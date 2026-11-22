#!/usr/bin/env python3
"""Apply the Escher/Fern runtime integration fixes to an extracted datapack or ZIP.

The helper is intentionally standard-library-only so it can be used by the
migration tool and to update an already-built compatible pack.
"""
from __future__ import annotations

import argparse
import gzip
import os
from pathlib import Path
import re
import shutil
import struct
import tempfile
import zipfile
from dataclasses import dataclass
from typing import Any

PORTAL_TAG_PREFIX = "tid_portal_color_"
PORTAL_MARKER_TAG = "tid_portal_marker"
PORTAL_COLORS = tuple(range(16))
SAFE_ENTRY_FUNCTION = "general/prepare_layer_1_entry.mcfunction"
PORTAL_CONTACT_RESET = (
    "execute as @a[tag=tid_portal_contact] at @s unless block ~ ~-1 ~ sea_lantern "
    "run tag @s remove tid_portal_contact"
)
HELLION_RETURN_PAD_REPAIR = (
    "execute in tid:layer_hellion positioned -6 182 -6 if block ~ ~ ~ bedrock "
    "run setblock ~ ~ ~ sea_lantern"
)
OBSOLETE_HELLION_RETURN_PAD_REPAIR = (
    "execute in tid:layer_hellion positioned 2 182 0 if block ~ ~ ~ bedrock "
    "run setblock ~ ~ ~ sea_lantern replace bedrock"
)
OBSOLETE_HELLION_RETURN_PAD_REPAIR_INVALID_SYNTAX = (
    "execute in tid:layer_hellion positioned -6 182 -6 if block ~ ~ ~ bedrock "
    "run setblock ~ ~ ~ sea_lantern replace bedrock"
)


@dataclass
class NbtList:
    element_type: int
    values: list[Any]


@dataclass
class NbtTag:
    tag_type: int
    name: str
    value: Any


class _NbtReader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def take(self, count: int) -> bytes:
        end = self.offset + count
        if end > len(self.data):
            raise ValueError("truncated NBT payload")
        result = self.data[self.offset:end]
        self.offset = end
        return result

    def unpack(self, fmt: str) -> Any:
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self) -> str:
        length = self.unpack(">H")
        return self.take(length).decode("utf-8")

    def named_tag(self) -> NbtTag | None:
        tag_type = self.unpack(">B")
        if tag_type == 0:
            return None
        name = self.string()
        return NbtTag(tag_type, name, self.payload(tag_type))

    def payload(self, tag_type: int) -> Any:
        if tag_type == 1:
            return self.unpack(">b")
        if tag_type == 2:
            return self.unpack(">h")
        if tag_type == 3:
            return self.unpack(">i")
        if tag_type == 4:
            return self.unpack(">q")
        if tag_type == 5:
            return self.unpack(">f")
        if tag_type == 6:
            return self.unpack(">d")
        if tag_type == 7:
            length = self.unpack(">i")
            return self.take(length)
        if tag_type == 8:
            return self.string()
        if tag_type == 9:
            element_type = self.unpack(">B")
            length = self.unpack(">i")
            if length < 0:
                raise ValueError("negative NBT list length")
            return NbtList(element_type, [self.payload(element_type) for _ in range(length)])
        if tag_type == 10:
            children = []
            while True:
                child = self.named_tag()
                if child is None:
                    return children
                children.append(child)
        if tag_type == 11:
            length = self.unpack(">i")
            if length < 0:
                raise ValueError("negative NBT int-array length")
            return [self.unpack(">i") for _ in range(length)]
        if tag_type == 12:
            length = self.unpack(">i")
            if length < 0:
                raise ValueError("negative NBT long-array length")
            return [self.unpack(">q") for _ in range(length)]
        raise ValueError(f"unsupported NBT tag type {tag_type}")


def _write_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    if len(encoded) > 65535:
        raise ValueError("NBT string exceeds the format limit")
    return struct.pack(">H", len(encoded)) + encoded


def _write_payload(tag_type: int, value: Any) -> bytes:
    if tag_type == 1:
        return struct.pack(">b", value)
    if tag_type == 2:
        return struct.pack(">h", value)
    if tag_type == 3:
        return struct.pack(">i", value)
    if tag_type == 4:
        return struct.pack(">q", value)
    if tag_type == 5:
        return struct.pack(">f", value)
    if tag_type == 6:
        return struct.pack(">d", value)
    if tag_type == 7:
        return struct.pack(">i", len(value)) + value
    if tag_type == 8:
        return _write_string(value)
    if tag_type == 9:
        if not isinstance(value, NbtList):
            raise ValueError("invalid NBT list value")
        return (struct.pack(">B", value.element_type)
                + struct.pack(">i", len(value.values))
                + b"".join(_write_payload(value.element_type, item) for item in value.values))
    if tag_type == 10:
        return b"".join(_write_named_tag(child) for child in value) + b"\x00"
    if tag_type == 11:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", item) for item in value)
    if tag_type == 12:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">q", item) for item in value)
    raise ValueError(f"unsupported NBT tag type {tag_type}")


def _write_named_tag(tag: NbtTag) -> bytes:
    return struct.pack(">B", tag.tag_type) + _write_string(tag.name) + _write_payload(tag.tag_type, tag.value)


def _read_nbt(data: bytes) -> NbtTag:
    reader = _NbtReader(data)
    root = reader.named_tag()
    if root is None or root.tag_type != 10:
        raise ValueError("structure NBT root is not a compound")
    if reader.offset != len(data):
        raise ValueError("unexpected bytes after NBT root")
    return root


def _field(compound: list[NbtTag], name: str) -> NbtTag | None:
    return next((tag for tag in compound if tag.name == name), None)


def _has_portal_name(value: Any) -> bool:
    return isinstance(value, str) and "teleporter" in value.lower()


def _inject_structure_portal_tags(root: NbtTag) -> int:
    entities = _field(root.value, "entities")
    if entities is None or entities.tag_type != 9 or entities.value.element_type != 10:
        return 0
    changed = 0
    for entity_entry in entities.value.values:
        entity_nbt = _field(entity_entry, "nbt")
        if entity_nbt is None or entity_nbt.tag_type != 10:
            continue
        fields = entity_nbt.value
        entity_id = _field(fields, "id")
        custom_name = _field(fields, "CustomName")
        color = _field(fields, "Color")
        if (entity_id is None or entity_id.value != "minecraft:area_effect_cloud"
                or custom_name is None or not _has_portal_name(custom_name.value)
                or color is None or color.tag_type not in {1, 2, 3, 4, 5, 6}):
            continue
        color_value = int(color.value)
        if color_value not in PORTAL_COLORS:
            raise ValueError(f"unsupported teleporter Color value {color_value}")
        identity = f"{PORTAL_TAG_PREFIX}{color_value}"
        identities = (identity, PORTAL_MARKER_TAG)
        tags = _field(fields, "Tags")
        if tags is None:
            fields.append(NbtTag(9, "Tags", NbtList(8, list(identities))))
            changed += 1
        elif tags.tag_type == 9 and tags.value.element_type == 8:
            missing = [tag for tag in identities if tag not in tags.value.values]
            if missing:
                tags.value.values.extend(missing)
                changed += 1
        else:
            raise ValueError("teleporter entity has an incompatible Tags field")
    return changed


def _patch_structure_files(root: Path) -> int:
    changed_entities = 0
    for path in sorted((root / "data" / "tid" / "structure").rglob("*.nbt")):
        compressed = path.read_bytes()
        try:
            raw = gzip.decompress(compressed)
        except OSError as error:
            raise ValueError(f"unsupported structure compression: {path}") from error
        tree = _read_nbt(raw)
        changed = _inject_structure_portal_tags(tree)
        if changed:
            path.write_bytes(gzip.compress(_write_named_tag(tree), compresslevel=9, mtime=0))
            changed_entities += changed
    return changed_entities


def _tag_summoned_portal(line: str) -> tuple[str, bool]:
    if (line.lstrip().startswith("#") or "summon minecraft:area_effect_cloud" not in line
            or "teleporter" not in line.lower()):
        return line, False
    color_match = re.search(r"\bColor:(\d+)(?:b)?\b", line)
    if color_match is None:
        return line, False
    color = int(color_match.group(1))
    if color not in PORTAL_COLORS:
        raise ValueError(f"unsupported teleporter summon Color {color}: {line}")
    identity = f"{PORTAL_TAG_PREFIX}{color}"
    identities = (identity, PORTAL_MARKER_TAG)
    tags_match = re.search(r"\bTags:\[([^\]]*)\]", line)
    if tags_match:
        tags = tags_match.group(1)
        missing = [tag for tag in identities
                   if not re.search(rf'"{re.escape(tag)}"', tags)]
        if not missing:
            return line, False
        replacement = f'Tags:[{tags},' + ",".join(f'"{tag}"' for tag in missing) + "]"
        return line[:tags_match.start()] + replacement + line[tags_match.end():], True
    close = line.rfind("}")
    if close < 0:
        raise ValueError(f"malformed teleporter summon SNBT: {line}")
    encoded = ",".join(f'"{tag}"' for tag in identities)
    return line[:close] + f",Tags:[{encoded}]" + line[close:], True


def _replace_teleporter_name_selectors(line: str) -> tuple[str, int]:
    """Replace unreliable portal name filters with stable marker tags."""
    rewrites = 0

    def rewrite(match: re.Match[str]) -> str:
        nonlocal rewrites
        options = [option.strip() for option in match.group(1).split(",")]
        name_filter = re.compile(r'''name\s*=\s*["']?teleporter["']?''', re.IGNORECASE)
        if not any(name_filter.fullmatch(option) for option in options):
            return match.group(0)
        rewrites += 1
        has_color_identity = any(
            re.fullmatch(rf"tag={re.escape(PORTAL_TAG_PREFIX)}\d+", option)
            for option in options
        )
        options = [option for option in options if not name_filter.fullmatch(option)]
        if not has_color_identity and f"tag={PORTAL_MARKER_TAG}" not in options:
            options.append(f"tag={PORTAL_MARKER_TAG}")
        return "[" + ",".join(options) + "]"

    return re.sub(r"\[([^\]]*)\]", rewrite, line), rewrites


def _patch_function_tree(root: Path) -> tuple[int, int]:
    function_root = root / "data" / "tid" / "function"
    summon_changes = 0
    selector_changes = 0
    for path in sorted(function_root.rglob("*.mcfunction")):
        relative = path.relative_to(function_root).as_posix()
        original_lines = path.read_text(encoding="utf-8").splitlines()
        patched_lines = []
        portal_context = "teleporter" in relative or relative.endswith("advancement_checks_run.mcfunction")
        for line in original_lines:
            updated, changed = _tag_summoned_portal(line)
            summon_changes += int(changed)
            if (relative == "main.mcfunction"
                    and "run function tid:general/teleporter" in updated
                    and re.search(r'''name\s*=\s*["']?teleporter["']?''', updated, re.IGNORECASE)):
                patched_lines.extend(
                    f"execute as @e[type=minecraft:area_effect_cloud,tag={PORTAL_TAG_PREFIX}{color}] "
                    "at @s run function tid:general/teleporter"
                    for color in PORTAL_COLORS
                )
                selector_changes += 1
                continue
            line_portal_context = portal_context or "teleporter" in updated.lower()
            if line_portal_context and not updated.lstrip().startswith("#"):
                updated, count = re.subn(
                    r"nbt=\{Color:(\d+)(?:b)?\}",
                    lambda match: f"tag={PORTAL_TAG_PREFIX}{match.group(1)}",
                    updated,
                )
                selector_changes += count
                updated, count = re.subn(
                    r"if data entity @s \{Color:(\d+)(?:b)?\}",
                    lambda match: f"if entity @s[tag={PORTAL_TAG_PREFIX}{match.group(1)}]",
                    updated,
                )
                selector_changes += count
                updated, count = _replace_teleporter_name_selectors(updated)
                selector_changes += count
                if relative == "general/teleporter_active.mcfunction":
                    updated, count = re.subn(
                        r"@a\[distance=0\.\.3\]",
                        "@a[distance=0..3,tag=!tid_portal_contact]",
                        updated,
                    )
                    selector_changes += count
            if (relative == "general/teleporter_teleport_run.mcfunction"
                    and updated.strip() == "tp @s ~ ~ ~"
                    and (not patched_lines or patched_lines[-1].strip() != "tag @s add tid_portal_contact")):
                patched_lines.append("tag @s add tid_portal_contact")
            patched_lines.append(updated)
        if patched_lines != original_lines:
            path.write_text("\n".join(patched_lines) + "\n", encoding="utf-8")

    active = function_root / "general" / "teleporter_active.mcfunction"
    if active.exists():
        text = active.read_text(encoding="utf-8")
        old = "execute store result score temp value run data get entity @s Color"
        if old in text:
            dispatch = ["scoreboard players set temp value -1"]
            dispatch.extend(
                f"execute if entity @s[tag={PORTAL_TAG_PREFIX}{color}] run scoreboard players set temp value {color}"
                for color in PORTAL_COLORS
            )
            text = text.replace(old, "\n".join(dispatch))
            active.write_text(text, encoding="utf-8")
            selector_changes += 1

    main_path = function_root / "main.mcfunction"
    main_lines = main_path.read_text(encoding="utf-8").splitlines()
    original_main_lines = list(main_lines)
    main_lines = [
        line for line in main_lines
        if not ("tag=!tid_portal_identity_ready" in line
                and "run function tid:general/identify_teleporter" in line)
    ]
    contact_reset = PORTAL_CONTACT_RESET
    if contact_reset not in main_lines:
        marker = next((index for index, line in enumerate(main_lines)
                       if line.strip() == "#Runs the teleporters"), None)
        if marker is None:
            raise ValueError("could not locate the teleporter tick section for contact reset")
        main_lines.insert(marker + 1, contact_reset)

    obsolete_repairs = {
        OBSOLETE_HELLION_RETURN_PAD_REPAIR,
        OBSOLETE_HELLION_RETURN_PAD_REPAIR_INVALID_SYNTAX,
    }
    main_lines = [line for line in main_lines if line not in obsolete_repairs]
    if HELLION_RETURN_PAD_REPAIR not in main_lines:
        expected_dispatches = {
            f"execute as @e[type=minecraft:area_effect_cloud,tag={PORTAL_TAG_PREFIX}{color}] "
            "at @s run function tid:general/teleporter"
            for color in PORTAL_COLORS
        }
        dispatch_indices = [index for index, line in enumerate(main_lines) if line in expected_dispatches]
        if len(dispatch_indices) != len(PORTAL_COLORS):
            raise ValueError("could not locate every stable portal dispatch for return-pad repair ordering")
        main_lines.insert(max(dispatch_indices) + 1, HELLION_RETURN_PAD_REPAIR)

    first_dispatch = next((index for index, line in enumerate(main_lines)
                           if line.startswith(
                               f"execute as @e[type=minecraft:area_effect_cloud,tag={PORTAL_TAG_PREFIX}0]"
                           )), None)
    if first_dispatch is None:
        raise ValueError("could not locate the first stable-tag portal dispatcher")
    if main_lines != original_main_lines:
        main_path.write_text("\n".join(main_lines) + "\n", encoding="utf-8")

    teleporter = function_root / "general" / "teleporter.mcfunction"
    if teleporter.exists():
        text = teleporter.read_text(encoding="utf-8")
        identify = "execute unless entity @s[tag=tid_portal_identity_ready] run function tid:general/identify_teleporter"
        filtered = [line for line in text.splitlines() if line != identify]
        if filtered != text.splitlines():
            teleporter.write_text("\n".join(filtered) + "\n", encoding="utf-8")
    identify_path = function_root / "general" / "identify_teleporter.mcfunction"
    identify_path.unlink(missing_ok=True)
    return summon_changes, selector_changes


def _patch_init_and_entry(root: Path) -> None:
    function_root = root / "data" / "tid" / "function"
    init_path = function_root / "init.mcfunction"
    if not init_path.exists():
        raise FileNotFoundError(f"Escher init function missing: {init_path}")
    init_lines = init_path.read_text(encoding="utf-8").splitlines()
    score_line = "scoreboard objectives add portal_color dummy"
    init_lines = [line for line in init_lines if line.strip() != score_line]
    entry_call = "function tid:general/prepare_layer_1_entry"
    if entry_call not in init_lines:
        marker = next((index for index, line in enumerate(init_lines)
                       if line.strip() == "#Bossbars"), None)
        if marker is None:
            raise ValueError("could not find the init Bossbars section")
        init_lines[marker:marker] = [
            "# Pre-carve the Layer 1 landing chamber before waypoint or portal entry.",
            entry_call,
        ]
    init_path.write_text("\n".join(init_lines) + "\n", encoding="utf-8")
    (function_root / SAFE_ENTRY_FUNCTION).write_text(
        "# Create the same protected landing chamber used by the lazy generator, before any player arrives.\n"
        "execute in tid:layer_1 positioned 0 54 0 unless block ~ ~ ~ bedrock run function tid:general/bedrock_cage\n",
        encoding="utf-8",
    )

    # Escher originally bounced players out of all vanilla dimensions every tick.
    # That undid Fern's intentional return-to-Overworld teleports immediately.
    main_path = function_root / "main.mcfunction"
    main = main_path.read_text(encoding="utf-8")
    bounce_lines = (
        "execute as @a[predicate=tid:in_overworld] run function tid:general/respawn",
        "execute as @a[predicate=tid:in_the_nether] run function tid:general/respawn",
        "execute as @a[predicate=tid:in_the_end] run function tid:general/respawn",
    )
    for line in bounce_lines:
        main = main.replace(line + "\n", "")
    main = main.replace(
        "#If the player ever is in a normal dimension, take them to the start point of Layer 1",
        "# Fern controls returns to the Overworld; do not redirect players out of vanilla dimensions.",
    )
    main_path.write_text(main, encoding="utf-8")


def _patch_embedded_docs(root: Path) -> None:
    """Keep the user-facing notes inside an existing ZIP consistent with the patch."""
    replacements = {
        "FERN-COMPATIBILITY.md": (
            (
                "Portal routing uses stable `tid_portal_color_N` entity tags rather than the legacy `Color` field. "
                "Newly summoned and structure-loaded markers receive their identity tags at creation; a compatibility "
                "bridge can tag existing markers while their old `Color` value remains readable.",
                "Portal routing uses stable `tid_portal_color_N` identity tags and a generic `tid_portal_marker` tag "
                "rather than custom-name selectors. Newly summoned and structure-loaded markers receive both tags at "
                "creation. Before stable-tag dispatch, the main tick function sweeps for area-effect clouds with the "
                "exact legacy name `teleporter`, `Color` 0–15, and no `tid_portal_identity_ready` tag, then adds their "
                "stable identity tags. A per-player `tid_portal_contact` latch is set before transfer and cleared after "
                "the player leaves the sea-lantern floor, preventing repeated activation while standing on or "
                "immediately arriving at a portal.",
            ),
            (
                "Portal routing uses stable `tid_portal_color_N` identity tags and a generic `tid_portal_marker` tag "
                "rather than custom-name selectors. Newly summoned and structure-loaded markers receive both tags at "
                "creation; the compatibility bridge can tag existing color-tagged markers while their old `Color` "
                "value remains readable. A per-player `tid_portal_contact` latch is set before transfer and cleared "
                "after the player leaves the sea-lantern floor, preventing repeated activation while standing on or "
                "immediately arriving at a portal.",
                "Portal routing uses stable `tid_portal_color_N` identity tags and a generic `tid_portal_marker` tag "
                "rather than custom-name selectors. Newly summoned and structure-loaded markers receive both tags at "
                "creation. Before stable-tag dispatch, the main tick function sweeps for area-effect clouds with the "
                "exact legacy name `teleporter`, `Color` 0–15, and no `tid_portal_identity_ready` tag, then adds their "
                "stable identity tags. A per-player `tid_portal_contact` latch is set before transfer and cleared after "
                "the player leaves the sea-lantern floor, preventing repeated activation while standing on or "
                "immediately arriving at a portal.",
            ),
            (
                "active missing calls are repaired, portal identity no longer depends on `Color`, and the integrated "
                "Fabric/26.3 startup plus representative function smoke calls passed. Run a player-level "
                "generation/progression staging test before production.",
                "active missing calls are repaired, portal identity and dispatch use stable tags rather than `Color` "
                "or `name=teleporter`, and the integrated Fabric/26.3 startup plus representative function smoke "
                "calls passed. Run a player-driven portal walk-through before production; rerun the previously passed "
                "Layer 1 landing and `/fern return spawn` / `/fern return last` checks as regressions.",
            ),
        ),
        "PORT-VALIDATION-26.3.md": (
            (
                "tagged teleporter summon sites and structure entities, and no active portal selectors that depend on `Color`.",
                "tagged teleporter summon sites and structure entities, no active portal selectors that depend on "
                "`Color` or `name=teleporter`, and a per-player portal contact latch.",
            ),
            (
                "no active portal selectors that depend on `Color` or `name=teleporter`, and a per-player portal contact latch.",
                "a strict legacy-marker migration sweep before dispatch, no active custom-name portal selectors, "
                "and a per-player portal contact latch. Normal routing uses stable tags; legacy `Color` is read "
                "only during migration.",
            ),
        ),
        "RUNTIME-VALIDATION-26.3.md": (
            (
                "- The runtime patcher passed ZIP integrity and idempotence checks on a disposable archive: all 16 "
                "teleporter summon sites and 8 structure teleporter entities were tagged, 46 portal selectors/dispatch "
                "references were migrated, and validation found no active `Color`-based portal selectors or "
                "vanilla-dimension auto-bounce hooks.",
                "- The runtime patcher tags all 16 teleporter summon sites and 8 structure portal markers with stable "
                "color and generic marker tags, dispatches by `tid_portal_color_N`, removes active `name=teleporter` "
                "filters, and prevents repeat contact until the player leaves the portal floor. ZIP integrity, selector "
                "validation, regression tests, and content-level repeat-patch checks passed; no active Color-based "
                "portal selectors or vanilla-dimension auto-bounce hooks remain.",
            ),
            (
                "These automated checks include startup/parser and representative-call validation plus static "
                "integration checks; they are not a full player-driven dungeon run. Procedural room connectivity, "
                "progression, every random branch, balance, and long-run performance still need a staging playtest. "
                "Non-blocking unrelated notices observed were vanilla piglin-barter loot-table parameter warnings and "
                "Fabric untranslated item-tag warnings.",
                "These automated checks cover startup/parser, representative calls, and static integration checks; "
                "they are not a full player-driven dungeon run. A player-driven portal walk-through is still needed "
                "to confirm destination arrival, no immediate bounce-back, and walking back through a portal. "
                "The preceding staging pass succeeded for the Layer 1 landing and `/fern return spawn` / "
                "`/fern return last`; keep these as regression checks. Procedural room connectivity, progression, "
                "every random branch, balance, and long-run performance also need staging playtests. Non-blocking "
                "unrelated notices observed were vanilla piglin-barter loot-table parameter warnings and Fabric "
                "untranslated item-tag warnings.",
            ),
            (
                "- The runtime patcher tags all 16 teleporter summon sites and 8 structure portal markers with "
                "stable color and generic marker tags, dispatches by `tid_portal_color_N`, removes active "
                "`name=teleporter` filters, and prevents repeat contact until the player leaves the portal floor. "
                "ZIP integrity, selector validation, regression tests, and content-level repeat-patch checks passed; "
                "no active Color-based portal selectors or vanilla-dimension auto-bounce hooks remain.",
                "- The runtime patcher tags all 16 teleporter summon sites and 8 structure portal markers with "
                "stable color and generic marker tags. Before color-tag dispatch, it sweeps unready area-effect "
                "clouds with the exact legacy name `teleporter` and a valid `Color` (0–15), then tags them through "
                "the compatibility bridge. This is migration-only use of legacy `Color`; normal portal routing "
                "uses stable tags. Active custom-name selectors are removed, and a contact latch prevents repeat "
                "transfers. ZIP integrity, regression tests, and repeat-patch idempotence checks passed.",
            ),
        ),
    }
    for relative_path, pairs in replacements.items():
        path = root / relative_path
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for old, new in pairs:
            text = text.replace(old, new)
        path.write_text(text, encoding="utf-8")

    compatibility_path = root / "FERN-COMPATIBILITY.md"
    if compatibility_path.is_file():
        lines = compatibility_path.read_text(encoding="utf-8").splitlines()
        compatibility_paragraph = (
            "Portal routing uses stable `tid_portal_color_N` identity tags and a generic `tid_portal_marker` tag "
            "rather than custom-name selectors. Newly summoned and structure-loaded markers receive both tags at "
            "creation. Existing worlds with already-spawned untagged markers require affected markers to be rebuilt "
            "or manually retagged from a verified destination-color mapping before upgrade: the 26.3 live probe "
            "found the legacy `Color` field missing, so name-only automatic migration is unsafe. A per-player "
            "`tid_portal_contact` latch is set before transfer and cleared after the player leaves the sea-lantern "
            "floor, preventing repeated activation while standing on or immediately arriving at a portal."
        )
        lines = [
            compatibility_paragraph if line.startswith("Portal routing uses stable `tid_portal_color_N` identity tags")
            else line
            for line in lines
        ]
        compatibility_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    port_validation_path = root / "PORT-VALIDATION-26.3.md"
    if port_validation_path.is_file():
        lines = port_validation_path.read_text(encoding="utf-8").splitlines()
        port_validation_note = (
            "The runtime integration pass statically verifies a pre-carved Layer 1 landing chamber, tagged teleporter "
            "summon sites and structure entities, stable-tag dispatch, no active custom-name selectors, and a per-player "
            "portal contact latch. The live probe found that 26.3 does not retain legacy `Color` on an untagged marker; "
            "existing markers require rebuilding or manual retagging from a verified destination-color mapping."
        )
        lines = [
            port_validation_note if "strict legacy-marker migration sweep" in line else line
            for line in lines
        ]
        port_validation_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    compatibility_path = root / "FERN-COMPATIBILITY.md"
    if compatibility_path.is_file():
        lines = compatibility_path.read_text(encoding="utf-8").splitlines()
        install_note = (
            "4. Back up the world first. See `PORT-VALIDATION-26.3.md`, `FUNCTION-REPAIRS-26.3.md`, "
            "and `RUNTIME-VALIDATION-26.3.md`: active missing calls are repaired, portal dispatch uses stable "
            "tags, but already-spawned untagged portal markers must be rebuilt or manually retagged from a "
            "verified destination-color mapping because Minecraft 26.3 did not retain legacy `Color` in the "
            "live entity probe; no automatic migration is attempted. Active custom-name portal selectors are "
            "removed. The integrated Fabric/26.3 startup "
            "plus representative function smoke calls passed. Run a player-driven portal walk-through before "
            "production; rerun the previously passed Layer 1 landing and `/fern return spawn` / "
            "`/fern return last` checks as regressions."
        )
        for index, line in enumerate(lines):
            if line.startswith("4. Back up the world first."):
                lines[index] = install_note
                break
        compatibility_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    runtime_path = root / "RUNTIME-VALIDATION-26.3.md"
    if runtime_path.is_file():
        lines = runtime_path.read_text(encoding="utf-8").splitlines()
        migration_note = (
            "- A disposable live probe confirmed that an area-effect cloud summoned with the legacy portal name "
            "retains the exact `CustomName` in 26.3, but the old `Color` field is missing (`data get entity @s Color` "
            "fails); the marker therefore cannot be assigned a destination identity automatically. No unsafe "
            "name-only migration is shipped. Existing untagged markers must be rebuilt or manually retagged from "
            "a verified destination-color mapping before upgrade."
        )
        lines = [line for line in lines if not line.startswith("- Before stable-tag dispatch")]
        patcher_bullet = (
            "- The runtime patcher tags all 16 teleporter summon sites and 8 structure portal markers with stable "
            "color and generic marker tags. A disposable live probe found that a legacy-style entity retained its "
            "exact `CustomName` but not its old `Color` NBT in Minecraft 26.3, so automatic identity migration "
            "would not be reliable. Existing untagged markers must be rebuilt or manually retagged from a "
            "verified destination-color mapping before upgrade. Normal routing uses stable tags; active "
            "custom-name selectors are removed, and a contact latch prevents repeat transfers."
        )
        lines = [
            patcher_bullet if line.startswith("- The runtime patcher tags all 16") and (
                "strict" in line or "Before color-tag dispatch" in line or "sweeps unready" in line
            )
            else line
            for line in lines
        ]
        if migration_note not in lines:
            anchor = next((index for index, line in enumerate(lines)
                           if line.startswith("- The runtime patcher tags all 16")), None)
            if anchor is None:
                anchor = next((index for index, line in enumerate(lines)
                               if line.startswith("## Escher random-generation integration patch")),
                              len(lines) - 1)
            lines.insert(anchor + 1, migration_note)
        hellion_pad_note = (
            "- After all portal dispatch/build calls, the main tick repairs the reachable Hellion color-1 return "
            "approach at `(-6, 182, -6)`, directly under the marker at `(-6, 183, -6)` and within the active "
            "trigger's 3-block radius. It replaces the tile with sea lantern only when it is bedrock, so portal "
            "contact latching is unchanged and `teleporter_build` cannot overwrite the repair. Static regression "
            "coverage is added; rerun the player-driven two-way walk-through in a fresh disposable world."
        )
        lines = [line for line in lines if not line.startswith(
            "- The main tick repairs the tested Hellion return approach at `(2, 182, 0)`"
        )]
        if hellion_pad_note not in lines:
            migration_index = lines.index(migration_note)
            lines.insert(migration_index + 1, hellion_pad_note)
        geometry_note = (
            "- Static color-12/13 geometry regression confirms the Finale color-13 marker at `(3, 143, 3)` centers "
            "its radius-3 trigger at `(4.5, 142, 4.5)`. All 12 generated sea-lantern cross tiles are within 2.24 "
            "blocks; even the full 4×4 fill footprint is within 2.83. Color 12's nominal arrival target `(0, 143, 0)` "
            "is 6.44 blocks away, and Wan’er observed `(0.189, 143, 0.5)` at 5.97 blocks, so the bedrock spawn is "
            "outside the trigger by design and no spawn-floor repair is indicated. The contact latch clears on "
            "non-lantern ground. For the walk-up test, step onto the center lantern at `(4, 141, 4)` (feet at "
            "`(4.5, 142, 4.5)`); a world-coordinate lantern at x=12 is not this pad. The player-driven return "
            "check remains pending."
        )
        if geometry_note not in lines:
            anchor = lines.index(hellion_pad_note) if hellion_pad_note in lines else lines.index(migration_note)
            lines.insert(anchor + 1, geometry_note)
        regression_note = (
            "- Wan’er’s preceding staging pass succeeded for the Layer 1 landing and `/fern return spawn` / "
            "`/fern return last`; keep these as regression checks for the next playtest."
        )
        load_smoke_note = (
            "- A fresh disposable Fabric 26.3 / Fabric Loader 0.19.5 world auto-loaded the patched ZIP and loaded "
            "`tid:main` with no function parse errors. After setting `(-6, 182, -6)` to bedrock and invoking "
            "`function tid:main`, a block-state scoreboard check confirmed sea lantern. The server shut down "
            "cleanly. This is a command-level smoke test, not connected-player portal traversal."
        )
        lines = [line for line in lines if not line.startswith(
            "- A fresh disposable Fabric 26.3 `runServer` directory auto-loaded the patched ZIP"
        )]
        if load_smoke_note not in lines:
            anchor = next((index for index, line in enumerate(lines)
                           if "return-position map is currently in memory" in line
                           or "last-Overworld-position map is currently in memory" in line), None)
            if anchor is None:
                anchor = next((index for index, line in enumerate(lines)
                               if line.startswith("## Escher random-generation integration patch")),
                              len(lines) - 1)
            lines.insert(anchor + 1, load_smoke_note)
        if regression_note not in lines:
            anchor = next((index for index, line in enumerate(lines)
                           if "return-position map is currently in memory" in line
                           or "last-Overworld-position map is currently in memory" in line), None)
            if anchor is None:
                anchor = next((index for index, line in enumerate(lines)
                               if line.startswith("## Escher random-generation integration patch")),
                              len(lines) - 1)
            lines.insert(anchor + 1, regression_note)

        scope_note = (
            "These automated checks cover startup/parser, representative calls, and static integration checks; "
            "they are not a full player-driven dungeon run. A player-driven portal walk-through is still needed "
            "to confirm destination arrival, no immediate bounce-back, and walking back through a portal. The "
            "preceding staging pass succeeded for the Layer 1 landing and `/fern return spawn` / "
            "`/fern return last`; rerun these as regression checks. Procedural room connectivity, progression, "
            "every random branch, balance, and long-run performance also need disposable staging playtests. "
            "Non-blocking unrelated notices observed were vanilla piglin-barter loot-table parameter warnings "
            "and Fabric untranslated item-tag warnings."
        )
        lines = [line for line in lines if not line.startswith((
            "These automated checks include startup/parser",
            "These automated checks cover startup/parser",
            "These checks do not include connected-player testing.",
        ))]
        scope_header = next((index for index, line in enumerate(lines)
                             if line.strip() == "## Scope limit"), None)
        if scope_header is None:
            lines.extend(["", "## Scope limit"])
            scope_header = len(lines) - 1
        lines.insert(scope_header + 1, scope_note)
        runtime_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _validate(root: Path) -> dict[str, int]:
    function_root = root / "data" / "tid" / "function"
    active_color_selectors = []
    active_portal_name_selectors = []
    portal_summons = 0
    for path in function_root.rglob("*.mcfunction"):
        relative = path.relative_to(function_root).as_posix()
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("#"):
                continue
            if "summon minecraft:area_effect_cloud" in line and "teleporter" in line.lower():
                portal_summons += 1
                if (not re.search(rf"Tags:\[[^\]]*\"{PORTAL_TAG_PREFIX}\d+\"", line)
                        or f'"{PORTAL_MARKER_TAG}"' not in line):
                    raise ValueError(f"teleporter summon lacks stable identity tags at {relative}:{line_no}")
            if re.search(r'''\bname\s*=\s*["']?teleporter["']?''', line, re.IGNORECASE):
                active_portal_name_selectors.append(f"{relative}:{line_no}")
            if ("teleporter" in relative or relative.endswith("advancement_checks_run.mcfunction")
                    or "teleporter" in line.lower()):
                if "nbt={Color:" in line or re.search(r"if data entity @s \{Color:", line):
                    active_color_selectors.append(f"{relative}:{line_no}")
    if active_color_selectors:
        raise ValueError("Color-based portal selectors remain: " + ", ".join(active_color_selectors))
    if active_portal_name_selectors:
        raise ValueError("unreliable teleporter name selectors remain: " + ", ".join(active_portal_name_selectors))
    active_path = function_root / "general" / "teleporter_active.mcfunction"
    if "tag=!tid_portal_contact" not in active_path.read_text(encoding="utf-8"):
        raise ValueError("portal trigger is not guarded against repeat contact")
    transfer_run = (function_root / "general" / "teleporter_teleport_run.mcfunction").read_text(encoding="utf-8")
    latch_at = transfer_run.find("tag @s add tid_portal_contact")
    teleport_at = transfer_run.find("tp @s ~ ~ ~")
    if latch_at < 0 or teleport_at < 0 or latch_at > teleport_at:
        raise ValueError("portal contact latch is not set before player transfer")
    if not (function_root / SAFE_ENTRY_FUNCTION).is_file():
        raise ValueError("Layer 1 safe-entry function missing")
    structure_portal_entities = 0
    for path in sorted((root / "data" / "tid" / "structure").rglob("*.nbt")):
        try:
            tree = _read_nbt(gzip.decompress(path.read_bytes()))
        except OSError as error:
            raise ValueError(f"cannot read structure NBT: {path}") from error
        entities = _field(tree.value, "entities")
        if entities is None or entities.tag_type != 9 or entities.value.element_type != 10:
            continue
        for entity_entry in entities.value.values:
            entity_nbt = _field(entity_entry, "nbt")
            if entity_nbt is None or entity_nbt.tag_type != 10:
                continue
            fields = entity_nbt.value
            entity_id = _field(fields, "id")
            custom_name = _field(fields, "CustomName")
            if (entity_id is None or entity_id.value != "minecraft:area_effect_cloud"
                    or custom_name is None or not _has_portal_name(custom_name.value)):
                continue
            tags = _field(fields, "Tags")
            color = _field(fields, "Color")
            if (tags is None or tags.tag_type != 9 or tags.value.element_type != 8
                    or color is None or f"{PORTAL_TAG_PREFIX}{int(color.value)}" not in tags.value.values
                    or PORTAL_MARKER_TAG not in tags.value.values):
                raise ValueError(f"structure teleporter has no stable identity tag: {path}")
            structure_portal_entities += 1
    main = (function_root / "main.mcfunction").read_text(encoding="utf-8")
    if PORTAL_CONTACT_RESET not in main:
        raise ValueError("portal contact latch is not cleared after the player leaves the portal floor")
    main_lines = main.splitlines()
    if HELLION_RETURN_PAD_REPAIR not in main_lines:
        raise ValueError("Hellion return approach bedrock repair is missing")
    if any(obsolete in main_lines for obsolete in (
        OBSOLETE_HELLION_RETURN_PAD_REPAIR,
        OBSOLETE_HELLION_RETURN_PAD_REPAIR_INVALID_SYNTAX,
    )):
        raise ValueError("obsolete Hellion return repair command remains")
    repair_index = main_lines.index(HELLION_RETURN_PAD_REPAIR)
    last_dispatch_index = max(
        (index for index, line in enumerate(main_lines)
         if line.startswith(f"execute as @e[type=minecraft:area_effect_cloud,tag={PORTAL_TAG_PREFIX}")
         and line.endswith("at @s run function tid:general/teleporter")),
        default=-1,
    )
    if repair_index <= last_dispatch_index:
        raise ValueError("Hellion return approach repair must run after portal dispatch/build calls")
    if ("identify_teleporter" in main
            or "tid_portal_identity_ready" in main
            or (function_root / "general" / "identify_teleporter.mcfunction").exists()):
        raise ValueError("unsupported Color-based legacy portal migration must not be present")
    dispatch_lines = [
        f"execute as @e[type=minecraft:area_effect_cloud,tag={PORTAL_TAG_PREFIX}{color}] "
        "at @s run function tid:general/teleporter"
        for color in PORTAL_COLORS
    ]
    for color, dispatch in enumerate(dispatch_lines):
        if dispatch not in main_lines:
            raise ValueError(f"portal tick dispatch missing stable identity tag {PORTAL_TAG_PREFIX}{color}")
    if any(line in main for line in (
        "execute as @a[predicate=tid:in_overworld] run function tid:general/respawn",
        "execute as @a[predicate=tid:in_the_nether] run function tid:general/respawn",
        "execute as @a[predicate=tid:in_the_end] run function tid:general/respawn",
    )):
        raise ValueError("vanilla-dimension auto-respawn hook remains active")
    return {"portal_summons": portal_summons,
            "structure_portal_entities": structure_portal_entities}


def apply_escher_runtime_fixes(root: str | Path) -> dict[str, int]:
    """Patch an extracted datapack tree in place and return a small change report."""
    root = Path(root)
    if not root.is_dir():
        raise FileNotFoundError(root)
    structure_entities = _patch_structure_files(root)
    summon_changes, selector_changes = _patch_function_tree(root)
    _patch_init_and_entry(root)
    _patch_embedded_docs(root)
    report = _validate(root)
    report.update({
        "structure_portal_entities_tagged": structure_entities,
        "portal_summon_sites_tagged": summon_changes,
        "portal_selector_rewrites": selector_changes,
    })
    return report


def patch_archive(path: str | Path) -> dict[str, int]:
    """Patch a datapack ZIP atomically while preserving its root-relative paths."""
    archive = Path(path).resolve()
    if not zipfile.is_zipfile(archive):
        raise ValueError(f"not a datapack ZIP: {archive}")
    with tempfile.TemporaryDirectory(prefix="escher-runtime-fix-") as temp_name:
        temp = Path(temp_name)
        extracted = temp / "pack"
        extracted.mkdir()
        with zipfile.ZipFile(archive) as source:
            for info in source.infolist():
                destination = (extracted / info.filename).resolve()
                if extracted.resolve() not in destination.parents and destination != extracted.resolve():
                    raise ValueError(f"unsafe ZIP member path: {info.filename}")
                if info.is_dir():
                    destination.mkdir(parents=True, exist_ok=True)
                    continue
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(source.read(info.filename))
        report = apply_escher_runtime_fixes(extracted)
        rebuilt = temp / "patched.zip"
        with zipfile.ZipFile(rebuilt, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
            for file_path in sorted(p for p in extracted.rglob("*") if p.is_file()):
                target.write(file_path, file_path.relative_to(extracted).as_posix())
        os.replace(rebuilt, archive)
        return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("datapack", help="extracted datapack directory or datapack ZIP")
    args = parser.parse_args()
    path = Path(args.datapack)
    report = patch_archive(path) if path.is_file() else apply_escher_runtime_fixes(path)
    print("Escher runtime fixes applied:")
    for key, value in report.items():
        print(f"  {key}: {value}")


if __name__ == "__main__":
    main()

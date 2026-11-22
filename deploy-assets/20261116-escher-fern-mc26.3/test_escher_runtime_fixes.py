from __future__ import annotations

import hashlib
import re
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

from escher_runtime_fixes import (
    _replace_teleporter_name_selectors,
    _tag_summoned_portal,
    patch_archive,
)


HERE = Path(__file__).resolve().parent
SOURCE_ARCHIVE = HERE / "escher-fern-compatible-mc26.3.zip"
PORTAL_NAME = re.compile(r'''\bname\s*=\s*["']?teleporter["']?''', re.IGNORECASE)


def _content_digest(archive: Path) -> str:
    digest = hashlib.sha256()
    with zipfile.ZipFile(archive) as pack:
        for info in sorted(pack.infolist(), key=lambda entry: entry.filename):
            if info.is_dir():
                continue
            digest.update(info.filename.encode("utf-8"))
            digest.update(b"\0")
            digest.update(pack.read(info.filename))
            digest.update(b"\0")
    return digest.hexdigest()


class RuntimeFixesRegressionTest(unittest.TestCase):
    def test_helpers_use_stable_tags_instead_of_portal_names(self) -> None:
        summon = (
            'summon minecraft:area_effect_cloud ~ ~ ~ '
            '{Duration:99999999,CustomName:"\\"teleporter\\"",Color:8b}'
        )
        tagged, changed = _tag_summoned_portal(summon)
        self.assertTrue(changed)
        self.assertIn('"tid_portal_color_8"', tagged)
        self.assertIn('"tid_portal_marker"', tagged)
        self.assertEqual(_tag_summoned_portal(tagged), (tagged, False))

        specific, rewrites = _replace_teleporter_name_selectors(
            'execute at @e[type=area_effect_cloud,name="teleporter",tag=tid_portal_color_8,tag=open] run function tid:test'
        )
        self.assertEqual(rewrites, 1)
        self.assertNotIn('name="teleporter"', specific)
        self.assertIn("tag=tid_portal_color_8", specific)
        self.assertNotIn("tag=tid_portal_marker", specific)

        generic, rewrites = _replace_teleporter_name_selectors(
            'execute if entity @e[type=area_effect_cloud,name="teleporter",distance=0..20] run function tid:test'
        )
        self.assertEqual(rewrites, 1)
        self.assertIn("tag=tid_portal_marker", generic)

    def test_finale_return_pad_is_inside_trigger_but_spawn_is_outside(self) -> None:
        with zipfile.ZipFile(SOURCE_ARCHIVE) as pack:
            active = pack.read("data/tid/function/general/teleporter_active.mcfunction").decode("utf-8")
            builder = pack.read("data/tid/function/general/teleporter_build.mcfunction").decode("utf-8")
            teleporter = pack.read("data/tid/function/general/teleporter.mcfunction").decode("utf-8")
            routes = pack.read("data/tid/function/general/teleporter_teleport.mcfunction").decode("utf-8")
            finale = pack.read("data/tid/function/dungeon/finale/finale_handler_p40.mcfunction").decode("utf-8")

        trigger = re.search(
            r"execute positioned (\S+) (\S+) (\S+) as @a\[distance=0\.\.([0-9.]+),",
            active,
        )
        self.assertIsNotNone(trigger)
        assert trigger is not None

        def relative(token: str) -> float:
            self.assertTrue(token.startswith("~"), token)
            return 0.0 if token == "~" else float(token[1:])

        trigger_offset = tuple(relative(trigger.group(i)) for i in range(1, 4))
        radius = float(trigger.group(4))
        self.assertIn("if block ~ ~-1 ~ sea_lantern", active)

        marker_line = next(
            line for line in finale.splitlines()
            if line.startswith("summon minecraft:area_effect_cloud") and '"tid_portal_color_13"' in line
        )
        marker_tokens = marker_line.split()
        marker = tuple(float(value) for value in marker_tokens[2:5])

        arrival_line = next(
            line for line in routes.splitlines()
            if "score temp value matches 12 in tid:layer_finale positioned" in line
        )
        arrival_match = re.search(r"positioned ([\d.-]+) ([\d.-]+) ([\d.-]+) run function", arrival_line)
        self.assertIsNotNone(arrival_match)
        assert arrival_match is not None
        arrival = tuple(float(arrival_match.group(i)) for i in range(1, 4))

        def floor_tiles(text: str) -> set[tuple[int, int, int]]:
            tiles: set[tuple[int, int, int]] = set()
            for line in text.splitlines():
                if "sea_lantern" not in line or "fill " not in line:
                    continue
                command = line.split("run fill ", 1)[1] if "run fill " in line else line.split("fill ", 1)[1]
                values = command.split()
                if len(values) < 7 or values[0] != "~" and not values[0].startswith("~"):
                    continue
                coords = [relative(value) for value in values[:6]]
                x1, y1, z1, x2, y2, z2 = (int(value) for value in coords)
                self.assertEqual(y1, y2)
                for x in range(min(x1, x2), max(x1, x2) + 1):
                    for z in range(min(z1, z2), max(z1, z2) + 1):
                        tiles.add((x, y1, z))
            return tiles

        built_pad = floor_tiles(builder)
        opened_pad = floor_tiles(teleporter)
        self.assertEqual(len(built_pad), 12)
        self.assertEqual(len(opened_pad), 16)
        candidate_pad = built_pad | opened_pad

        trigger_center = tuple(marker[i] + trigger_offset[i] for i in range(3))

        def distance(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
            return sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5

        standing_points = [
            (marker[0] + x + 0.5, marker[1] + y + 1, marker[2] + z + 0.5)
            for x, y, z in candidate_pad
        ]
        self.assertTrue(all(distance(point, trigger_center) <= radius for point in standing_points))
        self.assertGreater(distance(arrival, trigger_center), radius)

    def test_upgrade_with_untagged_legacy_markers_fails_closed_and_requires_rebuild(self) -> None:
        with tempfile.TemporaryDirectory(prefix="escher-upgrade-test-") as temp_dir:
            archive = Path(temp_dir) / "compatible-pack.zip"
            shutil.copyfile(SOURCE_ARCHIVE, archive)
            patch_archive(archive)
            with zipfile.ZipFile(archive) as pack:
                main_lines = pack.read("data/tid/function/main.mcfunction").decode("utf-8").splitlines()
                self.assertNotIn("identify_teleporter", "\n".join(main_lines))
                self.assertNotIn("tid_portal_identity_ready", "\n".join(main_lines))
                self.assertFalse(any("nbt={CustomName:" in line for line in main_lines))
                self.assertNotIn("data/tid/function/general/identify_teleporter.mcfunction", pack.namelist())
                self.assertIn(
                    "26.3 live probe found the legacy `Color` field missing",
                    pack.read("FERN-COMPATIBILITY.md").decode("utf-8"),
                )
                compatibility = pack.read("FERN-COMPATIBILITY.md").decode("utf-8")
                self.assertIn("must be rebuilt or manually retagged", compatibility)
                runtime_notes = pack.read("RUNTIME-VALIDATION-26.3.md").decode("utf-8")
                self.assertIn("old `Color` field is missing", runtime_notes)
                self.assertIn("Static color-12/13 geometry regression", runtime_notes)
                self.assertIn("center lantern at `(4, 141, 4)`", runtime_notes)
                self.assertIn("x=12 is not this pad", runtime_notes)
                for color in range(16):
                    self.assertTrue(any(
                        f"tag=tid_portal_color_{color}]" in line
                        and "run function tid:general/teleporter" in line
                        for line in main_lines
                    ))

    def test_portal_dispatch_and_contact_guard_are_tagged_and_idempotent(self) -> None:
        with tempfile.TemporaryDirectory(prefix="escher-runtime-test-") as temp_dir:
            archive = Path(temp_dir) / "compatible-pack.zip"
            shutil.copyfile(SOURCE_ARCHIVE, archive)

            first_report = patch_archive(archive)
            self.assertEqual(first_report["portal_summons"], 16)
            self.assertEqual(first_report["structure_portal_entities"], 8)

            with zipfile.ZipFile(archive) as pack:
                main = pack.read("data/tid/function/main.mcfunction").decode("utf-8")
                active = pack.read("data/tid/function/general/teleporter_active.mcfunction").decode("utf-8")
                transfer = pack.read("data/tid/function/general/teleporter_teleport_run.mcfunction").decode("utf-8")
                compatibility = pack.read("FERN-COMPATIBILITY.md").decode("utf-8")
                runtime_notes = pack.read("RUNTIME-VALIDATION-26.3.md").decode("utf-8")
                dispatches = [line for line in main.splitlines()
                              if "run function tid:general/teleporter" in line]
                self.assertEqual(len(dispatches), 16)
                self.assertIn("tid_portal_contact", compatibility)
                self.assertIn("must be rebuilt or manually retagged", compatibility)
                install_note = next(line for line in compatibility.splitlines()
                                    if line.startswith("4. Back up the world first."))
                self.assertIn("rerun the previously passed Layer 1 landing", install_note)
                self.assertIn(
                    "old `Color` field is missing",
                    runtime_notes,
                )
                self.assertIn("player-driven portal walk-through is still needed", runtime_notes)
                self.assertIn("Layer 1 landing and `/fern return spawn` / `/fern return last`", runtime_notes)
                hellion_pad_repair = (
                    "execute in tid:layer_hellion positioned -6 182 -6 if block ~ ~ ~ bedrock "
                    "run setblock ~ ~ ~ sea_lantern"
                )
                main_lines = main.splitlines()
                self.assertIn(hellion_pad_repair, main_lines)
                self.assertNotIn(
                    "execute in tid:layer_hellion positioned -6 182 -6 if block ~ ~ ~ bedrock "
                    "run setblock ~ ~ ~ sea_lantern replace bedrock",
                    main_lines,
                )
                hellion_handler_line = next(
                    index for index, line in enumerate(main_lines)
                    if "run function tid:dungeon/hellion/hellion_handler" in line
                )
                self.assertGreater(main_lines.index(hellion_pad_repair), hellion_handler_line)
                self.assertGreater(main_lines.index(hellion_pad_repair), main_lines.index(dispatches[-1]))
                self.assertIn("directly under the marker at `(-6, 183, -6)`", runtime_notes)
                self.assertIn("within the active trigger's 3-block radius", runtime_notes)
                self.assertNotIn("`(2, 182, 0)`", runtime_notes)
                self.assertIn("A fresh disposable Fabric 26.3 / Fabric Loader 0.19.5 world auto-loaded", runtime_notes)
                self.assertIn(
                    "After setting `(-6, 182, -6)` to bedrock and invoking `function tid:main`, "
                    "a block-state scoreboard check confirmed sea lantern",
                    runtime_notes,
                )
                self.assertIn("not connected-player portal traversal", runtime_notes)
                self.assertNotIn("return commands still need", runtime_notes)
                self.assertEqual(runtime_notes.count("The preceding staging pass succeeded"), 1)
                for color in range(16):
                    self.assertTrue(any(f"tag=tid_portal_color_{color}]" in line for line in dispatches))

                contact_reset = (
                    "execute as @a[tag=tid_portal_contact] at @s unless block ~ ~-1 ~ sea_lantern "
                    "run tag @s remove tid_portal_contact"
                )
                self.assertIn(contact_reset, main)
                self.assertLess(main.index(contact_reset), main.index(dispatches[0]))
                self.assertIn("tag=!tid_portal_contact", active)
                self.assertLess(transfer.index("tag @s add tid_portal_contact"),
                                transfer.index("tp @s ~ ~ ~"))

                active_name_filters = []
                portal_summons = []
                for name in pack.namelist():
                    if not name.endswith(".mcfunction"):
                        continue
                    for line_no, line in enumerate(pack.read(name).decode("utf-8").splitlines(), 1):
                        if line.lstrip().startswith("#"):
                            continue
                        if PORTAL_NAME.search(line):
                            active_name_filters.append(f"{name}:{line_no}")
                        if "summon minecraft:area_effect_cloud" in line and "teleporter" in line.lower():
                            portal_summons.append(line)
                self.assertEqual(active_name_filters, [])
                self.assertEqual(len(portal_summons), 16)
                for summon in portal_summons:
                    color_tags = re.findall(r'"tid_portal_color_(\d+)"', summon)
                    self.assertEqual(len(color_tags), 1, summon)
                    self.assertIn('"tid_portal_marker"', summon)

            first_content = _content_digest(archive)
            second_report = patch_archive(archive)
            self.assertEqual(second_report["portal_summon_sites_tagged"], 0)
            self.assertEqual(second_report["structure_portal_entities_tagged"], 0)
            self.assertEqual(second_report["portal_selector_rewrites"], 0)
            self.assertEqual(_content_digest(archive), first_content)


if __name__ == "__main__":
    unittest.main()

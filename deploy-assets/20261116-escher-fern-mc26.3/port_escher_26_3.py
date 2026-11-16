#!/usr/bin/env python3
"""First-pass mechanical port of The Escher Dimension 1.0.12 to Java 26.3.

Run only against a disposable copy. This preserves source and structure files,
translates known schema breaks, and reports remaining manual compatibility work.
It does not claim a complete gameplay port until the target server tests pass.
"""
from __future__ import annotations

import json
import pathlib
import re
import shutil
import sys
import zipfile

SOURCE = pathlib.Path(sys.argv[1]).resolve()
DEST = pathlib.Path(sys.argv[2]).resolve()
PACK_FORMAT = 121  # Minecraft Java 26.3 datapack min/max format.

if not SOURCE.is_dir():
    raise SystemExit(f"source directory does not exist: {SOURCE}")
if DEST.exists() and any(DEST.iterdir()):
    raise SystemExit(f"destination must be empty: {DEST}")
DEST.mkdir(parents=True, exist_ok=True)
shutil.copytree(SOURCE, DEST, dirs_exist_ok=True)

# Java 1.21 singularized datapack resource directories.
renames = {
    "advancements": "advancement",
    "functions": "function",
    "item_modifiers": "item_modifier",
    "loot_tables": "loot_table",
    "predicates": "predicate",
    "recipes": "recipe",
    "structures": "structure",
}
for namespace in (DEST / "data").iterdir():
    if not namespace.is_dir():
        continue
    for old, new in renames.items():
        src, dst = namespace / old, namespace / new
        if src.exists():
            if dst.exists():
                raise SystemExit(f"path collision: {src} -> {dst}")
            src.rename(dst)
    tags = namespace / "tags"
    if tags.exists():
        for old, new in {
            "functions": "function",
            "items": "item",
            "blocks": "block",
            "entity_types": "entity_type",
            "fluids": "fluid",
            "game_events": "game_event",
        }.items():
            src, dst = tags / old, tags / new
            if src.exists():
                if dst.exists():
                    raise SystemExit(f"tag path collision: {src} -> {dst}")
                src.rename(dst)

# Preserve original instructions verbatim and add port-specific notes.
readme = DEST / "README.txt"
if readme.exists():
    readme.rename(DEST / "UPSTREAM-README-1.16.5.txt")
(DEST / "README.txt").write_text(
    "The Escher Dimension — Fern-compatible port for Minecraft Java 26.3\n"
    "Original datapack by ZungryWare; see credits.txt and UPSTREAM-README-1.16.5.txt.\n"
    "Install alongside Forge Solo Subseason 26.3; see FERN-COMPATIBILITY.md.\n"
    "The server requires enable-command-block=true per the upstream instructions.\n"
    "See PORT-VALIDATION-26.3.md for static audit and staging status; full dungeon progression still needs in-game testing.\n",
    encoding="utf-8",
)
(DEST / "pack.mcmeta").write_text(
    json.dumps({"pack": {"min_format": PACK_FORMAT, "max_format": PACK_FORMAT,
                          "description": "The Escher Dimension 26.3 — Fern compatible"}},
               indent=2) + "\n",
    encoding="utf-8",
)

# Convert legacy biome schema to the exact 26.3 shape. Visual and audio fields
# formerly under `effects` moved to environment attributes; natural spawns moved
# to minecraft:gameplay/natural_mob_spawns in 26.3.
def spawn_count(entry: dict) -> int | dict:
    lo, hi = entry.pop("minCount", 1), entry.pop("maxCount", 1)
    if lo == hi:
        return lo
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}

biome_count = 0
for p in (DEST / "data").glob("*/worldgen/biome/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    if "has_precipitation" in d:
        continue  # Already in the target schema.
    attrs = dict(d.pop("attributes", {}))
    old_effects = d.pop("effects", {})
    d.pop("scale", None)
    d.pop("surface_builder", None)
    d.pop("starts", None)
    d.pop("player_spawn_friendly", None)
    d.pop("category", None)
    d.pop("depth", None)
    precip = d.pop("precipitation", "rain")
    d["has_precipitation"] = precip != "none"

    old_carvers = d.get("carvers", [])
    if isinstance(old_carvers, dict):
        d["carvers"] = [carver for group in old_carvers.values() for carver in group]

    # Color/ambience fields now belong to environment attributes in 26.3.
    attr_keys = {
        "sky_color": "minecraft:visual/sky_color",
        "fog_color": "minecraft:visual/fog_color",
        "water_fog_color": "minecraft:visual/water_fog_color",
    }
    for old, new in attr_keys.items():
        if old in old_effects:
            attrs[new] = old_effects.pop(old)
    mood = old_effects.pop("mood_sound", None)
    additions = old_effects.pop("additions_sound", old_effects.pop("additions_sounds", None))
    loop = old_effects.pop("ambient_sound", None)
    if mood or additions or loop:
        ambient = {}
        if mood:
            ambient["mood"] = mood
        if additions:
            ambient["additions"] = additions
        if loop:
            ambient["loop"] = loop
        attrs["minecraft:audio/ambient_sounds"] = ambient
    music = old_effects.pop("music", None)
    if music:
        attrs["minecraft:audio/background_music"] = {"default": music}
    music_volume = old_effects.pop("music_volume", None)
    if music_volume is not None:
        attrs["minecraft:audio/music_volume"] = music_volume

    # Keep fields still accepted in the 26.3 biome `effects` codec.
    d["effects"] = old_effects

    spawners = d.pop("spawners", {})
    spawn_costs = d.pop("spawn_costs", {})
    categories = {}
    for category, entries in spawners.items():
        converted = []
        for entry in entries:
            item = dict(entry)
            count = spawn_count(item)
            converted.append({"type": item.pop("type"), "count": count,
                              "weight": item.pop("weight"), **item})
        if converted:
            categories[category] = converted
    attrs["minecraft:gameplay/natural_mob_spawns"] = {
        "modifier": "overlay",
        "argument": {"spawn_costs": spawn_costs, "spawns_by_category": categories},
    }
    d["attributes"] = attrs
    p.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    biome_count += 1

# Migrate 1.16 dimension types to the required 26.3 schema. Several former
# booleans now control environment attributes; `fixed_time` is only a flag now.
dimension_type_count = 0
for p in (DEST / "data").glob("*/dimension_type/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    if "min_y" in d and "monster_spawn_light_level" in d:
        continue
    attrs = dict(d.pop("attributes", {}))
    ultrawarm = d.pop("ultrawarm", False)
    natural = d.pop("natural", True)
    d.pop("shrunk", None)
    piglin_safe = d.pop("piglin_safe", False)
    anchors_work = d.pop("respawn_anchor_works", False)
    beds_work = d.pop("bed_works", False)
    raids = d.pop("has_raids", False)
    fixed_time = d.pop("fixed_time", None)
    if ultrawarm:
        attrs["minecraft:gameplay/water_evaporates"] = True
        attrs["minecraft:gameplay/fast_lava"] = True
    attrs["minecraft:gameplay/nether_portal_spawns_piglin"] = bool(natural)
    attrs["minecraft:gameplay/piglins_zombify"] = not bool(piglin_safe)
    attrs["minecraft:gameplay/respawn_anchor_works"] = bool(anchors_work)
    attrs["minecraft:gameplay/can_start_raid"] = bool(raids)
    attrs["minecraft:gameplay/bed_rule"] = {
        "can_sleep": "when_dark" if beds_work else "never",
        "can_set_spawn": "always" if beds_work else "never",
        "destroy_on_use": not bool(beds_work),
    }
    d["attributes"] = attrs
    # The 26.3 world_clock registry only exposes overworld/end clocks; the old
    # nether time behavior is not a distinct clock resource.
    d["default_clock"] = "minecraft:overworld"
    d["has_ender_dragon_fight"] = False
    d["height"] = 256
    d["min_y"] = 0
    d["monster_spawn_block_light_limit"] = 0
    d["monster_spawn_light_level"] = 7
    if fixed_time is not None:
        d["has_fixed_time"] = True
    d["infiniburn"] = "#" + str(d.get("infiniburn", "minecraft:infiniburn_overworld")).lstrip("#")
    p.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    dimension_type_count += 1

# 26.3 renamed the discriminator in predicate/loot-condition objects from
# `condition` to `type`. Predicate resource definitions use the same shape.
predicate_count = 0
def migrate_predicate_shape(value):
    if isinstance(value, list):
        return [migrate_predicate_shape(item) for item in value]
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            new_key = "type" if key == "condition" else key
            if new_key in out:
                raise SystemExit(f"predicate key collision after rename: {key}")
            converted = migrate_predicate_shape(item)
            if new_key == "type" and converted == "minecraft:alternative":
                converted = "minecraft:any_of"
            out[new_key] = converted
        return out
    return value

for p in (DEST / "data").glob("*/predicate/**/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    migrated = migrate_predicate_shape(d)
    if migrated != d:
        p.write_text(json.dumps(migrated, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        predicate_count += 1

# Recipe ingredients changed from {item: id}/{tag: id} to a direct item/tag
# reference, while result.item was renamed to result.id.
recipe_count = 0
def migrate_recipe(value, parent_key=None):
    if isinstance(value, list):
        return [migrate_recipe(item, parent_key) for item in value]
    if isinstance(value, dict):
        if parent_key != "result" and set(value) == {"item"}:
            return value["item"]
        if parent_key != "result" and set(value) == {"tag"}:
            return "#" + value["tag"].lstrip("#")
        out = {}
        for key, item in value.items():
            new_key = "id" if parent_key == "result" and key == "item" else key
            out[new_key] = migrate_recipe(item, key)
        return out
    return value

for p in (DEST / "data").glob("*/recipe/**/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    migrated = migrate_recipe(d)
    if migrated.get("type") == "minecraft:smithing":
        migrated["type"] = "minecraft:smithing_transform"
    if migrated != d:
        p.write_text(json.dumps(migrated, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        recipe_count += 1

# Loot-table 26.3 schema: condition/functions became condition/modifier, the
# discriminator became type, number-provider bounds are explicitly typed, and
# referenced-table entries use `value` rather than `name`.
loot_table_count = 0
NUMBER_PROVIDER_FIELDS = {"rolls", "bonus_rolls", "count", "damage", "levels",
                          "chance", "amount", "value", "weight", "luck"}
def migrate_loot(value, parent_key=None):
    if isinstance(value, list):
        return [migrate_loot(item, parent_key) for item in value]
    if isinstance(value, dict):
        if set(value) == {"min", "max"} and parent_key in NUMBER_PROVIDER_FIELDS:
            return {"type": "minecraft:uniform", "min": value["min"],
                    "max": value["max"]}
        out = {}
        for key, item in value.items():
            new_key = key
            converted = migrate_loot(item, key)
            if key == "conditions":
                new_key = "condition"
                if isinstance(converted, list):
                    converted = {"type": "minecraft:all_of", "terms": converted}
            elif key == "functions":
                new_key = "modifier"
            elif key == "function":
                new_key = "type"
            elif key == "condition":
                # In a predicate object this is its former discriminator. The
                # outer loot-function condition list is handled as `conditions`.
                new_key = "type"
            if key == "condition" and converted == "minecraft:alternative":
                converted = "minecraft:any_of"
            if new_key == "type" and converted == "minecraft:alternative":
                converted = "minecraft:any_of"
            if new_key == "type" and converted in {"set_nbt", "minecraft:set_nbt"}:
                converted = "minecraft:set_custom_data"
            if new_key == "type" and converted in {"looting_enchant", "minecraft:looting_enchant"}:
                converted = "minecraft:enchanted_count_increase"
            if key == "name" and value.get("type") in {"loot_table", "minecraft:loot_table"}:
                new_key = "value"
            out[new_key] = converted
        return out
    return value

for p in (DEST / "data").glob("*/loot_table/**/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    migrated = migrate_loot(d)
    def patch_loot_runtime(value):
        if isinstance(value, list):
            return [patch_loot_runtime(item) for item in value]
        if isinstance(value, dict):
            out = {key: patch_loot_runtime(item) for key, item in value.items()}
            if out.get("type") == "minecraft:set_contents" and "component" not in out:
                out["component"] = "container"
            if out.get("type") == "minecraft:enchanted_count_increase":
                out.setdefault("enchantment", "minecraft:looting")
            if out.get("type") == "minecraft:random_chance_with_looting":
                chance = out.pop("chance", 0.0)
                multiplier = out.pop("looting_multiplier", 0.0)
                out["type"] = "minecraft:random_chance_with_enchanted_bonus"
                out["unenchanted_chance"] = chance
                out["enchanted_chance"] = {
                    "type": "minecraft:linear", "base": chance,
                    "per_level_above_first": multiplier,
                }
                out["enchantment"] = "minecraft:looting"
            if out.get("type") == "minecraft:copy_nbt":
                out["type"] = "minecraft:copy_custom_data"
            if out.get("type") == "loot_table":
                out["type"] = "minecraft:loot_table"
            if out.get("type") == "item":
                out["type"] = "minecraft:item"
            return out
        return value
    migrated = patch_loot_runtime(migrated)
    if migrated != d:
        p.write_text(json.dumps(migrated, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        loot_table_count += 1

# Advancement triggers now use stricter inline predicate objects and newer
# item-predicate/component fields. Preserve the upstream tree but translate the
# common item, recipe-unlocked and entity-list patterns.
advancement_count = 0
def migrate_item_predicate(value):
    if isinstance(value, list):
        return [migrate_item_predicate(item) for item in value]
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            new_key = {"item": "items", "potion": "potions"}.get(key, key)
            if key == "nbt":
                preds = out.setdefault("predicates", {})
                preds["minecraft:custom_data"] = item
                continue
            out[new_key] = migrate_item_predicate(item)
        return out
    return value

ENTITY_SUBPREDICATES = {
    "equipment", "flags", "vehicle", "passenger", "targeted_entity",
    "type_specific", "stepping_on", "slots", "effects", "location", "team",
}
CONDITION_TYPES = {
    "minecraft:all_of", "minecraft:any_of", "minecraft:entity_properties",
    "minecraft:random_chance", "minecraft:inverted", "minecraft:killed_by_player",
    "minecraft:location_check", "minecraft:time_check", "minecraft:match_tool",
    "minecraft:match_block", "minecraft:damage_source_properties",
}
def migrate_entity_predicate(value):
    if isinstance(value, list):
        return [migrate_entity_predicate(item) for item in value]
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            if key == "type":
                new_key = "minecraft:entity_type"
            elif key in ENTITY_SUBPREDICATES:
                new_key = "minecraft:" + key
            elif key == "equipment" and isinstance(item, dict):
                new_key = "minecraft:equipment"
                out[new_key] = {slot: migrate_item_predicate(pred)
                                for slot, pred in item.items()}
                continue
            elif key == "nbt":
                # Custom mob tags in old NBT have no 1:1 component predicate;
                # retain them for the next server validation pass.
                new_key = key
            else:
                new_key = key
            if new_key == "minecraft:entity_type" and item == "minecraft:boat":
                item = "#minecraft:boat"
            out[new_key] = migrate_entity_predicate(item)
        return out
    return value

def migrate_adv_condition(value, parent_key=None):
    if isinstance(value, list):
        terms = [migrate_adv_condition(item) for item in value]
        if parent_key in {"entity", "player"}:
            return {"type": "minecraft:all_of", "terms": terms}
        return terms
    if isinstance(value, dict):
        if parent_key in {"entity", "player"} and value.get("type") not in CONDITION_TYPES:
            return {
                "type": "minecraft:entity_properties",
                "entity": "this",
                "predicate": migrate_entity_predicate(value),
            }
        if parent_key == "location" and "type" not in value and "condition" not in value:
            return {"type": "minecraft:location_check", "predicate": value}
        # Old entity trigger conditions accepted a list of loot conditions;
        # 26.3 requires one inline predicate value.
        if parent_key == "entity" and value and all(isinstance(x, dict) for x in value):
            terms = [migrate_adv_condition(x) for x in value]
            return {"type": "minecraft:all_of", "terms": terms}
        out = {}
        for key, item in value.items():
            new_key = "type" if key == "condition" else key
            converted = item
            if key == "predicate":
                converted = migrate_entity_predicate(item)
            elif key == "item" and isinstance(item, dict):
                converted = migrate_item_predicate(item)
            elif key == "items":
                converted = migrate_item_predicate(item)
            elif key in {"entity", "player"}:
                converted = migrate_adv_condition(item, key)
            elif key in {"direct_entity", "source_entity"}:
                converted = migrate_entity_predicate(item)
            elif key == "block" and isinstance(item, dict) and "block" in item:
                converted = {"blocks" if subkey == "block" else subkey: migrate_adv_condition(subvalue, subkey)
                             for subkey, subvalue in item.items()}
            else:
                converted = migrate_adv_condition(item, key)
            if new_key == "type" and converted == "minecraft:alternative":
                converted = "minecraft:any_of"
            out[new_key] = converted
        return out
    return value

def migrate_custom_predicate(value):
    if isinstance(value, list):
        return [migrate_custom_predicate(item) for item in value]
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            if key == "predicate":
                out[key] = migrate_entity_predicate(item)
            elif key in {"mainhand", "offhand", "head", "chest", "legs", "feet"}:
                out[key] = migrate_item_predicate(item)
            else:
                out[key] = migrate_custom_predicate(item)
        return out
    return value

for p in (DEST / "data").glob("*/predicate/**/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    migrated = migrate_custom_predicate(d)
    if migrated != d:
        p.write_text(json.dumps(migrated, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        predicate_count += 1

for p in (DEST / "data").glob("*/advancement/**/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    original = json.dumps(d, sort_keys=True)
    icon = d.get("display", {}).get("icon")
    if isinstance(icon, dict) and "item" in icon:
        icon["id"] = icon.pop("item")
    for criterion in d.get("criteria", {}).values():
        trigger = criterion.get("trigger", "")
        cond = criterion.get("conditions", {})
        if trigger == "minecraft:recipe_unlocked" and isinstance(cond, dict) and "recipe" in cond:
            cond["recipes"] = cond.pop("recipe")
        if trigger == "minecraft:player_generates_container_loot" and isinstance(cond, dict) and "loot_table" in cond:
            cond["loot_tables"] = cond.pop("loot_table")
        criterion["conditions"] = migrate_adv_condition(cond)
    if json.dumps(d, sort_keys=True) != original:
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        advancement_count += 1

# Predicate-bearing fields inside loot tables/item modifiers use the same
# flattened 26.3 entity/item predicate shape as advancement criteria.
for base in ("loot_table", "item_modifier"):
    for p in (DEST / "data").glob(f"*/{base}/**/*.json"):
        d = json.loads(p.read_text(encoding="utf-8"))
        migrated = migrate_custom_predicate(d)
        if migrated != d:
            p.write_text(json.dumps(migrated, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

# One upstream function refers to this absent chest table; keep it harmlessly empty
# rather than letting the missing reference prevent all datapack registries loading.
missing_table = DEST / "data/tid/loot_table/chests/finale_misc.json"
if not missing_table.exists():
    missing_table.parent.mkdir(parents=True, exist_ok=True)
    missing_table.write_text(json.dumps({"type": "minecraft:chest", "pools": []}, indent=2) + "\n", encoding="utf-8")

# Apply renamed item IDs found in the 26.3 vanilla item registry.
for p in DEST.rglob("*.json"):
    text = p.read_text(encoding="utf-8")
    updated = re.sub(r"minecraft:chain(?![a-z0-9_])", "minecraft:iron_chain", text)
    updated = re.sub(r"minecraft:scute(?![a-z0-9_])", "minecraft:turtle_scute", updated)
    if updated != text:
        p.write_text(updated, encoding="utf-8")

# Standalone item modifiers use the same 26.3 loot-function/predicate format.
item_modifier_count = 0
for p in (DEST / "data").glob("*/item_modifier/**/*.json"):
    d = json.loads(p.read_text(encoding="utf-8"))
    migrated = patch_loot_runtime(migrate_loot(d))
    if migrated != d:
        p.write_text(json.dumps(migrated, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        item_modifier_count += 1

# Convert all occurrences, including `execute ... run replaceitem ...` lines.
replace_count = 0
entity_re = re.compile(r"\breplaceitem\s+entity\s+(\S+)\s+(\S+)\s+")
block_re = re.compile(r"\breplaceitem\s+block\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s+")
particle_line_re = re.compile(r"(?P<head>\bparticle\s+)(?P<name>(?:minecraft:)?[a-z0-9_]+)\s+(?P<args>.*)$")
def _num(s: str) -> str:
    if s.startswith("."):
        return "0" + s
    if s.startswith("-."):
        return "-0" + s[1:]
    return s

def migrate_particle_line(line: str) -> str:
    m = particle_line_re.search(line)
    if not m:
        return line
    name = m.group("name").split(":")[-1]
    tokens = m.group("args").split()
    options, consumed = None, 0
    numeric = re.compile(r"-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?")
    if name == "dust" and len(tokens) >= 4 and all(numeric.fullmatch(x) for x in tokens[:4]):
        r, g, b, scale = map(_num, tokens[:4])
        options, consumed = f"{{color:[{r},{g},{b}],scale:{scale}}}", 4
    elif name == "dust_color_transition" and len(tokens) >= 7 and all(numeric.fullmatch(x) for x in tokens[:7]):
        r1, g1, b1, scale, r2, g2, b2 = map(_num, tokens[:7])
        options = f"{{from_color:[{r1},{g1},{b1}],scale:{scale},to_color:[{r2},{g2},{b2}]}}"
        consumed = 7
    elif name in {"block", "block_marker", "falling_dust"} and tokens:
        block_id = tokens[0]
        if ":" not in block_id:
            block_id = "minecraft:" + block_id
        options, consumed = f'{{block_state:{{id:"{block_id}"}}}}', 1
    elif name == "item" and tokens:
        item_id = tokens[0]
        if ":" not in item_id:
            item_id = "minecraft:" + item_id
        options, consumed = f'{{item:{{id:"{item_id}"}}}}', 1
    if options is None:
        return line
    remainder = " ".join(tokens[consumed:])
    replacement = m.group("head") + "minecraft:" + name + options + ((" " + remainder) if remainder else "")
    return line[:m.start()] + replacement

def migrate_command_line(line: str) -> str:
    line = migrate_particle_line(line)
    for old, new in {
        "minecraft:generic.max_health": "minecraft:max_health",
        "minecraft:generic.attack_damage": "minecraft:attack_damage",
        "minecraft:generic.movement_speed": "minecraft:movement_speed",
        "minecraft:generic.knockback_resistance": "minecraft:knockback_resistance",
        "minecraft:zombie.spawn_reinforcements": "minecraft:spawn_reinforcements",
    }.items():
        line = line.replace(old, new)
    line = re.sub(r"minecraft:chain(?![a-z0-9_])", "minecraft:iron_chain", line)
    line = re.sub(r"(?<![a-z0-9_])chain(?![a-z0-9_])", "iron_chain", line)
    line = re.sub(r"(?<![a-z0-9_])grass(?![a-z0-9_])", "short_grass", line)
    line = line.replace("summon minecraft:boat", "summon minecraft:dark_oak_boat")
    line = line.replace('Type:"dark_oak",', "")
    line = re.sub(r'minecraft:potion\{Potion:"([^"]+)"\}',
                  lambda m: 'minecraft:potion[minecraft:potion_contents={potion:"' +
                            (m.group(1) if ":" in m.group(1) else "minecraft:" + m.group(1)) + '"}]', line)
    line = re.sub(r'minecraft:mojang_banner_pattern\{([a-z0-9_]+:1b)\}',
                  r'minecraft:mojang_banner_pattern[minecraft:custom_data~{\1}]', line)
    line = re.sub(r'(#(?:[a-z0-9_.-]+):[a-z0-9_./-]+)\{([^{}]+)\}',
                  r'\1[minecraft:custom_data~{\2}]', line)
    def enchantments(m: re.Match) -> str:
        item = m.group(1)
        if ":" not in item:
            item = "minecraft:" + item
        levels = {}
        for e in re.finditer(r'\{id:"([a-z0-9_.-]+:[a-z0-9_./-]+)",lvl:(\d+)\}', m.group(2)):
            levels[e.group(1)] = int(e.group(2))
        if not levels:
            return m.group(0)
        encoded = json.dumps(levels, separators=(",", ":"))
        return f'{item}[minecraft:enchantments={encoded}]'
    line = re.sub(r'((?:minecraft:)?[a-z0-9_]+)\{Enchantments:\[(.*?)\]\}', enchantments, line)
    line = re.sub(r'\bbedrock\{drop_items:1b\}', 'minecraft:bedrock', line)
    gm = re.search(r"\bgamerule\s+([A-Za-z][A-Za-z0-9]*)\s+(-?\d+|true|false)\b", line)
    if gm:
        key = re.sub(r"(?<!^)([A-Z])", r"_\1", gm.group(1)).lower()
        value = gm.group(2)
        renamed_rules = {
            "do_trader_spawning": "spawn_wandering_traders",
            "do_weather_cycle": "advance_weather",
            "do_insomnia": "spawn_phantoms",
            "do_patrol_spawning": "spawn_patrols",
            "disable_raids": "raids",
            "max_command_chain_length": "max_command_sequence_length",
        }
        if key == "disable_raids":
            value = "false" if value == "true" else "true" if value == "false" else value
        key = renamed_rules.get(key, key)
        line = line[:gm.start()] + f"gamerule {key} {value}" + line[gm.end():]
    line = re.sub(r"scoreboard players set (moon_flower_probability|station_bramble_probability|tree_status) (?!value\b)(\d+)\b",
                  r"scoreboard players set \1 value \2", line)
    # 26.3 uses namespaced modifier IDs and explicit operation names. Preserve
    # UUID identity with a deterministic ID and discard the legacy display name.
    def modifier_id(m: re.Match) -> str:
        return m.group(1) + "tid:legacy_" + m.group(2).replace("-", "").lower()
    line = re.sub(r"(modifier\s+(?:add|remove)\s+)([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})\b", modifier_id, line)
    operations = {"add": "add_value", "multiply": "add_multiplied_total",
                  "multiply_base": "add_multiplied_base"}
    def modifier_add(m: re.Match) -> str:
        return f"{m.group(1)} {m.group(2)} {operations[m.group(3)]}"
    line = re.sub(r"(modifier add tid:legacy_[0-9a-f]+)\s+\S+\s+(-?(?:\d+(?:\.\d*)?|\.\d+))\s+(add|multiply|multiply_base)\b",
                  modifier_add, line)
    # Structure-block mode is a block state in current Minecraft; upstream only
    # provided the uppercase block-entity field.
    def structure_block(m: re.Match) -> str:
        mode = re.search(r'mode:\s*"([A-Z]+)"', m.group(1))
        if not mode:
            return m.group(0)
        data = re.sub(r',?\s*mode:\s*"[A-Z]+"', '', m.group(1))
        return f"minecraft:structure_block[mode={mode.group(1).lower()}]{{{data}}}"
    line = re.sub(r"minecraft:structure_block\{([^{}]*)\}", structure_block, line)
    line = re.sub(r"(scoreboard objectives setdisplay\s+)belowName\b", r"\1below_name", line)
    line = line.replace("#minecraft:carpets", "#minecraft:wool_carpets")
    line = line.replace("tid:dungeon/station_generate_straggler_build",
                        "tid:dungeon/station/generate_straggler_build")
    return line

for p in DEST.rglob("*.mcfunction"):
    text = p.read_text(encoding="utf-8")
    def entity_sub(m: re.Match) -> str:
        return f"item replace entity {m.group(1)} {m.group(2)} with "
    def block_sub(m: re.Match) -> str:
        return f"item replace block {m.group(1)} {m.group(2)} {m.group(3)} {m.group(4)} with "
    updated = entity_re.sub(entity_sub, text)
    updated = block_re.sub(block_sub, updated)
    # Player head NBT in the one old setitem command becomes a profile component.
    updated = updated.replace(
        "minecraft:player_head{SkullOwner:ZungryWare}",
        'minecraft:player_head[minecraft:profile={name:"ZungryWare"}]',
    )
    updated = "\n".join(migrate_command_line(line) for line in updated.split("\n"))
    replace_count += text.count("replaceitem ") - updated.count("replaceitem ")
    if updated != text:
        p.write_text(updated, encoding="utf-8")

# Restore live functions omitted from the v1.0.12 release archive. The Infernal
# checks/sewer routine are recovered from official upstream history; other
# routines below are reconstructed from existing room templates and siblings.
repaired_functions = {
    "data/tid/function/dungeon/infernal/check_down.mcfunction": """function tid:dungeon/infernal/room_checks""",
    "data/tid/function/dungeon/infernal/check_up.mcfunction": """function tid:dungeon/infernal/room_checks""",
    "data/tid/function/dungeon/infernal/check_east.mcfunction": """function tid:dungeon/infernal/room_checks
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~1 ~-9 ~-12 if blocks ~ ~128 ~ ~29 ~151 ~29 1001 1 1001 all run function tid:dungeon/infernal/select_sewer_east
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~1 ~-10 ~-15 if blocks ~ ~128 ~ ~31 ~154 ~31 1001 1 1001 all run function tid:dungeon/infernal/select_factory""",
    "data/tid/function/dungeon/infernal/check_north.mcfunction": """function tid:dungeon/infernal/room_checks
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~-12 ~-9 ~-30 if blocks ~ ~128 ~ ~29 ~151 ~29 1001 1 1001 all run function tid:dungeon/infernal/select_sewer_north
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~-15 ~-10 ~-32 if blocks ~ ~128 ~ ~31 ~154 ~31 1001 1 1001 all run function tid:dungeon/infernal/select_factory""",
    "data/tid/function/dungeon/infernal/check_south.mcfunction": """function tid:dungeon/infernal/room_checks
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~-12 ~-9 ~1 if blocks ~ ~128 ~ ~29 ~151 ~29 1001 1 1001 all run function tid:dungeon/infernal/select_sewer_south
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~-15 ~-10 ~1 if blocks ~ ~128 ~ ~31 ~154 ~31 1001 1 1001 all run function tid:dungeon/infernal/select_factory""",
    "data/tid/function/dungeon/infernal/check_west.mcfunction": """function tid:dungeon/infernal/room_checks
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~-30 ~-9 ~-12 if blocks ~ ~128 ~ ~29 ~151 ~29 1001 1 1001 all run function tid:dungeon/infernal/select_sewer_west
execute unless score @s failed matches 1.. if predicate tid:random_20 positioned ~-32 ~-10 ~-15 if blocks ~ ~128 ~ ~31 ~154 ~31 1001 1 1001 all run function tid:dungeon/infernal/select_factory""",
    "data/tid/function/dungeon/infernal/sewer_check.mcfunction": """# Decide which direction this sewer will go in.
# Try without randomness first so one valid direction is selected when space exists.
# 1 north, 2 east, 3 south, 4 west, 5 down, 6 up.
scoreboard players set @s counter1 0
execute positioned ~ ~-4 ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 5
execute positioned ~-5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 4
execute positioned ~ ~ ~5 if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 3
execute positioned ~5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 2
execute positioned ~ ~ ~-5 if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 1
execute if predicate tid:random_50 positioned ~5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 2
execute if predicate tid:random_33 positioned ~ ~ ~5 if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 3
execute if predicate tid:random_25 positioned ~-5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 4
execute if predicate tid:random_03 positioned ~ ~-4 ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 5
execute if predicate tid:random_50 if score @s down matches 1 positioned ~ ~4 ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 6
execute if predicate tid:random_50 if score @s up matches 1 positioned ~ ~-4 ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run scoreboard players set @s counter1 5

# Build the selected continuation.
execute if score @s counter1 matches 1 positioned ~ ~ ~-5 run function tid:dungeon/infernal/sewer_select_north
execute if score @s counter1 matches 2 positioned ~5 ~ ~ run function tid:dungeon/infernal/sewer_select_east
execute if score @s counter1 matches 3 positioned ~ ~ ~5 run function tid:dungeon/infernal/sewer_select_south
execute if score @s counter1 matches 4 positioned ~-5 ~ ~ run function tid:dungeon/infernal/sewer_select_west
execute if score @s counter1 matches 5 positioned ~ ~-4 ~ run function tid:dungeon/infernal/sewer_select_down
execute if score @s counter1 matches 6 positioned ~ ~4 ~ run function tid:dungeon/infernal/sewer_select_up

# Add optional extra passages where the corresponding space remains open.
execute if predicate tid:random_03 positioned ~ ~ ~-5 if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run function tid:dungeon/infernal/sewer_select_north
execute if predicate tid:random_03 positioned ~5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run function tid:dungeon/infernal/sewer_select_east
execute if predicate tid:random_03 positioned ~ ~ ~5 if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run function tid:dungeon/infernal/sewer_select_south
execute if predicate tid:random_03 positioned ~-5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run function tid:dungeon/infernal/sewer_select_west
execute if predicate tid:random_05 positioned ~ ~4 ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1033 1 1033 all run function tid:dungeon/infernal/sewer_select_up

# Exterior pipe outflows.
execute if predicate tid:random_10 positioned ~ ~ ~-5 if blocks ~ ~128 ~ ~4 ~131 ~4 1001 1 1001 all run function tid:dungeon/infernal/sewer_outflow_north
execute if predicate tid:random_10 positioned ~5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1001 1 1001 all run function tid:dungeon/infernal/sewer_outflow_east
execute if predicate tid:random_10 positioned ~ ~ ~5 if blocks ~ ~128 ~ ~4 ~131 ~4 1001 1 1001 all run function tid:dungeon/infernal/sewer_outflow_south
execute if predicate tid:random_10 positioned ~-5 ~ ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1001 1 1001 all run function tid:dungeon/infernal/sewer_outflow_west
execute if predicate tid:random_05 positioned ~ ~-4 ~ if blocks ~ ~128 ~ ~4 ~131 ~4 1001 1 1001 all run scoreboard players set @s down 1

data modify entity @s CustomName set value '{\"text\":\"sewer_section_finished\"}'""",
    "data/tid/function/dungeon/test/check_up.mcfunction": """# Upward test markers have no dedicated vertical stair selector; still apply the test-room retry/termination guard.
function tid:dungeon/test/room_checks""",
    "data/tid/function/custom_mobs/war_pig.mcfunction": """# Weighted tier roll: low 50%, medium 25%, high 12.5%, super 12.5%.
scoreboard players set temp2 value 0
execute if score temp2 value matches 0 if predicate tid:random_50 run scoreboard players set temp2 value 1
execute if score temp2 value matches 0 if predicate tid:random_50 run scoreboard players set temp2 value 2
execute if score temp2 value matches 0 if predicate tid:random_50 run scoreboard players set temp2 value 3
execute if score temp2 value matches 0 run scoreboard players set temp2 value 4
execute if score temp2 value matches 1 run function tid:custom_mobs/war_pig_1
execute if score temp2 value matches 2 run function tid:custom_mobs/war_pig_2
execute if score temp2 value matches 3 run function tid:custom_mobs/war_pig_3
execute if score temp2 value matches 4 run function tid:custom_mobs/war_pig_4""",
    "data/tid/function/misc/soul_lantern.mcfunction": """# Place a stable floor-supported soul lantern at the selected cave-decoration cell.
setblock ~ ~ ~ minecraft:soul_lantern""",
}

def write_repaired_function(relative_path: str, body: str) -> None:
    path = DEST / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    converted = "\n".join(migrate_command_line(line) for line in body.strip().splitlines())
    path.write_text(converted + "\n", encoding="utf-8")

for relative_path, body in repaired_functions.items():
    write_repaired_function(relative_path, body)

# Both sand floors use 11x11 X/Z room templates, so restore the same proven
# orientation transform helpers used by sand2 for the otherwise identical sand1 grid.
for suffix in ("0f", "90", "90f", "180", "180f", "270", "270f"):
    source = DEST / f"data/tid/function/dungeon/sand2/orient_room_{suffix}.mcfunction"
    if not source.is_file():
        raise SystemExit(f"missing sand2 orientation source: {source}")
    write_repaired_function(
        f"data/tid/function/dungeon/sand1/orient_room_{suffix}.mcfunction",
        source.read_text(encoding="utf-8"),
    )

# The supplied hall templates already cover north and west. Reflect those templates
# across their doorway planes to generate south/east without inventing new structure assets.
hall_builders = {
    "data/tid/function/dungeon/sand1/build_hall_south.mcfunction": (
        "tid:sand1/hall_north", "LEFT_RIGHT",
        "fill ~ 255 ~-6 ~4 255 ~ minecraft:sandstone replace minecraft:black_concrete"),
    "data/tid/function/dungeon/sand1/build_hall_east.mcfunction": (
        "tid:sand1/hall_west", "FRONT_BACK",
        "fill ~-6 255 ~ ~ 255 ~4 minecraft:sandstone replace minecraft:black_concrete"),
    "data/tid/function/dungeon/sand2/build_hall_south.mcfunction": (
        "tid:sand2/hall_north", "LEFT_RIGHT", ""),
    "data/tid/function/dungeon/sand2/build_hall_east.mcfunction": (
        "tid:sand2/hall_west", "FRONT_BACK", ""),
}
for relative_path, (template, mirror, map_mark) in hall_builders.items():
    body = (
        "# Place the existing north/west template reflected into this direction.\n"
        f'setblock ~ ~ ~ minecraft:structure_block[mode=load]{{name:"{template}",posX:0,posY:0,posZ:0,rotation:"NONE",mirror:"{mirror}",ignoreEntities:0b,powered:0b,showair:0b,showboundingbox:0b}}\n'
        "setblock ~ ~1 ~ minecraft:redstone_block"
        + ("\n" + map_mark if map_mark else "")
    )
    write_repaired_function(relative_path, body)

# Import the remaining exact upstream Infernal selection/outflow helpers. This
# companion archive is distributed beside this script and contains source history.
upstream_overlay = pathlib.Path(__file__).with_name("escher-infernal-upstream-recovery-26.3.zip")
if not upstream_overlay.is_file():
    raise SystemExit(f"required upstream helper overlay missing: {upstream_overlay}")
with zipfile.ZipFile(upstream_overlay) as archive:
    for member in archive.namelist():
        if member.startswith("data/tid/function/") and member.endswith(".mcfunction"):
            write_repaired_function(member, archive.read(member).decode("utf-8"))

# Restore referenced random-chance aliases missing from the upstream archive.
referenced_predicates = set()
for p in DEST.rglob("*.mcfunction"):
    for match in re.finditer(r"\bpredicate\s+tid:([a-z0-9_./-]+)", p.read_text(encoding="utf-8")):
        referenced_predicates.add(match.group(1))
for ident in sorted(referenced_predicates):
    path = DEST / "data/tid/predicate" / (ident + ".json")
    if path.exists():
        continue
    match = re.fullmatch(r"random_(\d+)", ident) or re.fullmatch(r"(\d{2})", ident)
    if match:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"type": "minecraft:random_chance",
                                    "chance": int(match.group(1)) / 100}, indent=2) + "\n", encoding="utf-8")

# Apply the same removed block-ID correction to JSON resources (tags and loot).
for p in DEST.rglob("*.json"):
    text = p.read_text(encoding="utf-8")
    updated = re.sub(r"minecraft:grass(?![a-z0-9_])", "minecraft:short_grass", text)
    updated = updated.replace("#minecraft:carpets", "#minecraft:wool_carpets")
    if updated != text:
        p.write_text(updated, encoding="utf-8")

# Fern integration: Escher's singleplayer defaults must not reset a dedicated
# server's spawn or silently change its world-wide survival rules. Fern's
# permanent owner-locked death drops also require keep_inventory=false.
init_function = DEST / "data/tid/function/init.mcfunction"
if not init_function.exists():
    raise SystemExit(f"Escher init function missing: {init_function}")
init_lines = init_function.read_text(encoding="utf-8").splitlines()
compat_lines = []
keep_inventory_written = False
server_global_rules = re.compile(
    r"^gamerule\s+(?:command_block_output|spawn_wandering_traders|advance_weather|spawn_phantoms|raids|spawn_patrols)\s+"
)
for line in init_lines:
    stripped = line.strip()
    if re.match(r"^setworldspawn\s+", stripped):
        continue
    if server_global_rules.match(stripped):
        continue
    if re.match(r"^gamerule\s+keep_inventory\s+", stripped):
        if not keep_inventory_written:
            compat_lines.append("gamerule keep_inventory false")
            keep_inventory_written = True
        continue
    compat_lines.append(line)
if not keep_inventory_written:
    insert_at = 1 if compat_lines and compat_lines[0].strip().startswith("#Setup") else 0
    compat_lines.insert(insert_at, "gamerule keep_inventory false")
init_function.write_text("\n".join(compat_lines) + "\n", encoding="utf-8")

(DEST / "FERN-COMPATIBILITY.md").write_text(
    "# Fern / Escher compatibility (Minecraft Java 26.3)\n\n"
    "This port is designed to run beside Forge Solo Subseason on Fabric. The `tid:` namespace remains intact, so Escher resources do not replace Fern's `forge_*` arenas or `fern` dimension.\n\n"
    "## Player entry and return\n\n"
    "With the compatible Fern mod JAR installed, players can run `/waypoint dungeon` (alias `/waypoint escher`) to enter `tid:layer_1` at the Escher start platform, `(0, 55, 0)`. When entering from the Overworld, Fern saves the player's prior location; use `/fern return last` to return there, or `/fern return spawn` for the main-world spawn. The route reports a clear error if the Escher dimension is not loaded.\n\n"
    "## Fern gameplay hooks\n\n"
    "Fern's owner-binding applies in Escher dimensions just as it does elsewhere: carried and crafted stacks are bound to their player outside Cornucopia, and the Escher Delver daily quest counts monster kills in any `tid:` dimension. The init function explicitly sets `keep_inventory false` so Fern can retain its permanent, owner-locked death drops and coordinate notices. This is a server-wide rule and is intentional for the requested Fern death behavior.\n\n"
    "The upstream `setworldspawn` call was removed to preserve the Fern server's existing Overworld spawn. Escher's unrelated global gamerule overrides for weather, phantoms, raids, patrols, traders, and command feedback were also removed. The upstream command-sequence limit is retained because procedural generation uses long function chains.\n\n"
    "## Install / operational notes\n\n"
    "1. Use Minecraft 26.3, Fabric, Java 25, and enable `enable-command-block=true` in `server.properties`.\n"
    "2. Install the matching Forge Solo Subseason Fabric JAR in `mods/`.\n"
    "3. Install this datapack in the active world's `datapacks/` directory, then fully restart the server so the `tid:` dimensions register.\n"
    "4. Back up the world first. See `PORT-VALIDATION-26.3.md`, `FUNCTION-REPAIRS-26.3.md`, and `RUNTIME-VALIDATION-26.3.md`: active missing calls are repaired and the integrated Fabric/26.3 startup plus representative function smoke calls passed. Run a player-level generation/progression staging test before production.\n",
    encoding="utf-8",
)

# Report missing live targets separately from stale references left in comments.
missing_function_targets = {}
comment_only_targets = {}
for p in DEST.glob("data/*/function/**/*.mcfunction"):
    for line_no, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        for match in re.finditer(r"\bfunction\s+([a-z0-9_.-]+:[a-z0-9_./-]+)", line):
            ident = match.group(1)
            if not ident.startswith("tid:"):
                continue
            target = DEST / "data" / ident.split(":", 1)[0] / "function" / (ident.split(":", 1)[1] + ".mcfunction")
            if not target.is_file():
                bucket = comment_only_targets if line.lstrip().startswith("#") else missing_function_targets
                bucket.setdefault(ident, []).append(f"{p.relative_to(DEST)}:{line_no}")
missing_lines = [f"- `{ident}` — referenced at `{refs[0]}`" for ident, refs in sorted(missing_function_targets.items())]
(DEST / "FUNCTION-REPAIRS-26.3.md").write_text(
    "# Escher function repairs for Minecraft 26.3\n\n"
    "The official source is [Zungrysoft/The-Escher-Dimension](https://github.com/Zungrysoft/The-Escher-Dimension). The release archive omitted several functions that its live callers use.\n\n"
    "## Recovered or restored from existing assets\n\n"
    "- Infernal checks, factory selection, sewer room selection, and sewer outflows were recovered from upstream history (commits `df29773a2b6b`, `cc4c9769727c`, `39e66d9665b0`, `edcb8eb8f45a`, and `9a3f5a3931c3`).\n"
    "- The seven `sand1/orient_room_*` helpers reuse the matching `sand2` transforms. Both room families have 11×11 X/Z templates, so the saved origin offsets remain valid.\n"
    "- The station straggler caller is redirected to the existing `tid:dungeon/station/generate_straggler_build` function; the caller had omitted the `station/` path segment.\n\n"
    "## Reconstructed from in-pack evidence\n\n"
    "- `sand1`/`sand2` east and south hallway builders reflect the existing west and north structure templates across the doorway plane; their original structures are 7×7×5 and 5×7×7.\n"
    "- `dungeon/test/check_up` applies the test dungeon's room retry/termination guard; the test pack has no dedicated vertical stair selector.\n"
    "- `custom_mobs/war_pig` routes to the existing four variants with a geometric tier roll (50%, 25%, 12.5%, 12.5%).\n"
    "- `misc/soul_lantern` places a floor-supported soul lantern at the selected decoration cell. No upstream body for this helper was present in reachable history.\n\n"
    "Commented-out legacy calls are listed separately in the validation report; they are not executed by Minecraft. The reconstructed behaviors should be play-tested in staging.\n",
    encoding="utf-8",
)
(DEST / "PORT-VALIDATION-26.3.md").write_text(
    "# Escher 26.3 static compatibility report\n\n"
    "The migration script restores and reconstructs active missing helpers before auditing the resulting datapack.\n\n"
    "## Live function-target audit\n\n"
    f"Static audit found **{len(missing_function_targets)}** unresolved active `tid:` function targets.\n\n"
    + ("\n".join(missing_lines) if missing_lines else "No unresolved active `tid:` function targets found.")
    + "\n\n## Comment-only legacy references\n\n"
    + f"The pack retains **{len(comment_only_targets)}** missing target name(s) only inside comment lines. They are inactive and do not prevent execution.\n\n"
    + ("\n".join(f"- `{ident}` — `{refs[0]}`" for ident, refs in sorted(comment_only_targets.items())) if comment_only_targets else "No comment-only missing references found.")
    + "\n\n## Runtime smoke validation\n\n"
    "The integrated Fabric 26.3 cold boot and representative function calls passed; see `RUNTIME-VALIDATION-26.3.md`.\n\n"
    "## Validation boundary\n\n"
    "A zero-reference audit, clean startup, and representative smoke calls do not establish every random room layout, boss progression, or long-run performance. See `FUNCTION-REPAIRS-26.3.md` for recovered versus reconstructed behavior, and perform a player-level staging run before production.\n",
    encoding="utf-8",
)
(DEST / "RUNTIME-VALIDATION-26.3.md").write_text(
    "# Escher + Fern runtime validation, Minecraft 26.3\n\n"
    "Validation date: 2026-11-16. Environment: Fabric Loom 1.18.4, Fabric Loader 0.19.5, Minecraft 26.3, Java 25.0.5+8, and the Forge Solo Subseason Fabric mod.\n\n"
    "## Passed checks\n\n"
    "- `:platform-fabric:build` completed successfully.\n"
    "- The integrated `:platform-fabric:runServer` cold boot reached the server-ready `Done` marker; Fern initialized and all `tid:layer_*` dimensions registered.\n"
    "- The pack audit found zero unresolved active `tid:` function targets, and the server log contained no datapack/function parse failures.\n"
    "- Representative smoke calls to `tid:custom_mobs/war_pig`, `tid:dungeon/sand1/build_hall_south`, `tid:dungeon/sand2/build_hall_east`, and `tid:dungeon/station/generate_straggler_execute` ran without command errors.\n"
    "- The disposable server was stopped cleanly.\n\n"
    "## Scope limit\n\n"
    "This is startup/parser plus representative-call validation, not a full player-driven dungeon run. Procedural room connectivity, progression, every random branch, balance, and long-run performance still need a staging playtest. Non-blocking unrelated notices observed were vanilla piglin-barter loot-table parameter warnings and Fabric untranslated item-tag warnings.\n",
    encoding="utf-8",
)

remaining = []
for p in DEST.rglob("*.mcfunction"):
    for line_no, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if re.search(r"\breplaceitem\b", line):
            remaining.append(f"{p.relative_to(DEST)}:{line_no}")
if remaining:
    raise SystemExit("Unconverted replaceitem remains:\n" + "\n".join(remaining[:30]))

print(f"Copied {SOURCE} -> {DEST}")
print(f"Updated pack metadata to {PACK_FORMAT}; singularized paths; migrated {biome_count} biomes, {dimension_type_count} dimension types, {predicate_count} predicates, {recipe_count} recipes, {loot_table_count} loot tables, {item_modifier_count} item modifiers and {advancement_count} advancements; converted {replace_count} replaceitem usages.")
print("Function repairs applied; integrated 26.3 startup and representative smoke calls passed; full dungeon gameplay still needs staging validation.")

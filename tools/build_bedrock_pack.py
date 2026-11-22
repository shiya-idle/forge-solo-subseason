from pathlib import Path
import json
import shutil
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "resource-pack/assets/forge_solo"
OUT = ROOT / "bedrock-resource-pack"
ARCHIVE = ROOT / "forge-solo-bedrock-26.3.mcpack"
manifest_path = OUT / "manifest.json"
try:
    existing_manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
except (OSError, json.JSONDecodeError):
    existing_manifest = {}

if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True)

pack_uuid = existing_manifest.get("header", {}).get("uuid") or str(uuid.uuid4())
module_uuid = next((module.get("uuid") for module in existing_manifest.get("modules", [])
                    if isinstance(module, dict) and module.get("type") == "resources" and module.get("uuid")), None)
module_uuid = module_uuid or str(uuid.uuid4())
manifest = {
    "format_version": 2,
    "header": {
        "name": "Forge Solo Visuals — Fern",
        "description": "Bedrock visuals for Fern via Geyser/Floodgate. HUD values remain server-authoritative.",
        "uuid": pack_uuid,
        "pack_scope": "world",
        "version": [1, 0, 0],
        "min_engine_version": [1, 21, 0],
    },
    "modules": [{
        "description": "Fern Bedrock resource assets",
        "type": "resources",
        "uuid": module_uuid,
        "version": [1, 0, 0],
    }],
}
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
(OUT / "README.md").write_text("""# Forge Solo Bedrock pack

This resource pack is for Bedrock Edition players joining the Minecraft 26.3 Fabric server through Geyser/Floodgate. The manifest minimum is Bedrock 1.21.0; it is not a claim that every device or current client version has been runtime-tested.

## Open the class skill deck

Hold the matching class weapon, crouch, then tap/use it to open the native Bedrock skill form. You can also run `/skills panel`. Tap an ability to cast; the server checks the class weapon, world rules, mana, and cooldown. The class-selection form warns that the class choice is permanent.

Every ability button includes its text label; Cumulus adds a pack icon when the image API and texture path are available. The server does not detect a client-side image load failure. If the server cannot use the Cumulus image-button API, it falls back to text-only buttons. Bedrock players can turn the action-bar health/mana/thirst readout on or off with `/skills mana true` and `/skills mana false`.

## Pack contents and compatibility

Class/skill form icons are Bedrock texture paths under `textures/ui/forge_solo/icons/`; the server presents them through Geyser's Cumulus forms API. The other panel/overlay textures are assets only and do not, by themselves, create a Bedrock HUD. The server-authoritative action-bar display and Geyser-translated particles are the live Bedrock visuals; Java client HUD mixins do not run on Bedrock.

Build the `.mcpack` with `python3 tools/build_bedrock_pack.py`. Put `forge-solo-bedrock-26.3.mcpack` in Geyser's `packs` directory and restart/reload Geyser. Verify it on the actual target Bedrock version and device families before making the pack mandatory.
""")

textures = OUT / "textures/ui/forge_solo"
textures.mkdir(parents=True)
for source in sorted((JAVA / "textures/gui").glob("*.png")):
    shutil.copy2(source, textures / source.name)
source_icons = JAVA / "textures/gui/icons"
if source_icons.exists():
    bedrock_icons = textures / "icons"
    bedrock_icons.mkdir(parents=True)
    for source in sorted(source_icons.glob("*.png")):
        shutil.copy2(source, bedrock_icons / source.name)

font_source = JAVA / "font/forge_glyphs.png"
if font_source.exists():
    shutil.copy2(font_source, textures / "forge_glyphs.png")

sounds = OUT / "sounds"
sounds.mkdir(parents=True)
for source in sorted((JAVA / "sounds").glob("*.ogg")):
    shutil.copy2(source, sounds / source.name)


def sound_entry(name):
    return {"category": "player", "sounds": [{"name": f"sounds/{name}", "volume": 0.8}]}


sound_defs = {
    "format_version": "1.14.0",
    "sound_definitions": {
        "forge_solo.cast_arcane": sound_entry("cast_arcane"),
        "forge_solo.cast_nature": sound_entry("cast_nature"),
        "forge_solo.cast_radiant": sound_entry("cast_radiant"),
        "forge_solo.cast_shadow": sound_entry("cast_shadow"),
        "forge_solo.impact": sound_entry("impact"),
        "forge_solo.notice": sound_entry("notice"),
    },
}
(sounds / "sound_definitions.json").write_text(json.dumps(sound_defs, indent=2) + "\n")

icon_source = JAVA / "textures/gui/skill_panel.png"
if icon_source.exists():
    shutil.copy2(icon_source, OUT / "pack_icon.png")

if ARCHIVE.exists():
    ARCHIVE.unlink()
with zipfile.ZipFile(ARCHIVE, "w", zipfile.ZIP_DEFLATED) as zf:
    for path in sorted(OUT.rglob("*")):
        if path.is_file():
            zf.write(path, path.relative_to(OUT).as_posix())
print(f"created {ARCHIVE}")
print(f"pack uuid {pack_uuid}")

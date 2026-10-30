from pathlib import Path
import json
import shutil
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "resource-pack/assets/forge_solo"
OUT = ROOT / "bedrock-resource-pack"
ARCHIVE = ROOT / "forge-solo-bedrock-26.3.mcpack"

if OUT.exists():
    shutil.rmtree(OUT)
OUT.mkdir(parents=True)

pack_uuid = str(uuid.uuid4())
module_uuid = str(uuid.uuid4())
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
(OUT / "README.md").write_text("""# Fern Bedrock resource pack\n\nThis is a Bedrock Edition resource pack for Geyser/Floodgate. Put the `.mcpack` or `.zip` in the Geyser `packs` directory and restart or reload Geyser.\n\nThe server sends the live health, mana, and thirst values through the Bedrock action bar. This pack supplies the matching Fern colors, overlays, panel textures, font artwork, and sounds. Java Fabric HUD mixins cannot run inside Bedrock, so the pack does not attempt to install Java code or Java-only HUD logic.\n\nThe pack was generated from the Fern Java visual assets. Bedrock and Java resource packs use different schemas; the paths here are deliberately Bedrock paths. Test the pack on the target Bedrock version and device families before making it mandatory.\n""")

textures = OUT / "textures/ui/forge_solo"
textures.mkdir(parents=True)
for source in sorted((JAVA / "textures/gui").glob("*.png")):
    shutil.copy2(source, textures / source.name)
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
(OUT / "sounds/sound_definitions.json").write_text(json.dumps(sound_defs, indent=2) + "\n")

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

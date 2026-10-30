from pathlib import Path
import math, struct, wave, subprocess, json
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1] / 'resource-pack'
ASSETS = ROOT / 'assets' / 'forge_solo'
for folder in [ASSETS / 'textures/gui', ASSETS / 'textures/gui/icons', ASSETS / 'font', ASSETS / 'sounds']:
    folder.mkdir(parents=True, exist_ok=True)

# Minecraft 26.3 resource pack format.
(ROOT / 'pack.mcmeta').write_text(json.dumps({"pack": {"pack_format": 97.1, "description": "Forge Solo Subseason • Visual Profiles, Cast Trails & Arena UI"}}, indent=2) + '\n')

profiles = {
    'nature': ((74, 178, 107, 255), (193, 240, 145, 255)),
    'arcane': ((93, 95, 220, 255), (184, 147, 255, 255)),
    'radiant': ((255, 105, 150, 255), (255, 227, 120, 255)),
    'shadow': ((93, 53, 124, 255), (72, 218, 199, 255)),
    'impact': ((255, 151, 64, 255), (255, 225, 116, 255)),
}

def glow_icon(primary, accent):
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    glow = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((5, 5, 27, 27), fill=primary)
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    im.alpha_composite(glow)
    d = ImageDraw.Draw(im)
    d.ellipse((8, 8, 24, 24), outline=accent, width=2)
    d.polygon([(16, 5), (20, 13), (27, 16), (20, 19), (16, 27), (12, 19), (5, 16), (12, 13)], fill=primary, outline=accent)
    d.point((16, 16), fill=(255, 255, 255, 255))
    return im

for name, (primary, accent) in profiles.items():
    glow_icon(primary, accent).save(ASSETS / 'textures/gui/icons' / f'{name}.png')

# Panel and overlays are transparent, low-contrast compositing assets for a compatible pack UI.
panel = Image.new('RGBA', (256, 128), (11, 18, 28, 0))
pd = ImageDraw.Draw(panel)
pd.rounded_rectangle((2, 2, 253, 125), radius=12, fill=(10, 17, 28, 208), outline=(90, 220, 180, 210), width=2)
pd.line((12, 42, 244, 42), fill=(130, 220, 194, 150), width=1)
for y in (58, 89):
    for x in range(16, 244, 27): pd.rounded_rectangle((x, y, x + 22, y + 22), radius=4, fill=(30, 49, 60, 210), outline=(140, 230, 200, 130), width=1)
panel.save(ASSETS / 'textures/gui/skill_panel.png')

trail = Image.new('RGBA', (256, 64), (0, 0, 0, 0)); td = ImageDraw.Draw(trail)
for i in range(7):
    alpha = 150 - i * 17
    td.ellipse((12 + i * 34, 28 - i * 2, 35 + i * 34, 51 - i * 2), fill=(102, 220, 255, alpha))
    td.line((25 + i * 34, 39 - i * 2, 55 + i * 34, 39 - i * 2), fill=(178, 255, 230, alpha), width=2)
trail.save(ASSETS / 'textures/gui/cast_trail_overlay.png')

flash = Image.new('RGBA', (256, 64), (0, 0, 0, 0)); fd = ImageDraw.Draw(flash)
fd.rectangle((0, 0, 255, 63), outline=(255, 74, 91, 180), width=3)
fd.line((0, 31, 255, 31), fill=(255, 145, 120, 75), width=1)
fd.line((128, 0, 128, 63), fill=(255, 145, 120, 75), width=1)
flash.save(ASSETS / 'textures/gui/damage_flash_overlay.png')

notice = Image.new('RGBA', (256, 64), (0, 0, 0, 0)); nd = ImageDraw.Draw(notice)
nd.rounded_rectangle((2, 8, 253, 55), radius=8, fill=(15, 23, 37, 225), outline=(112, 239, 195, 220), width=2)
nd.line((16, 18, 240, 18), fill=(245, 202, 92, 210), width=2)
notice.save(ASSETS / 'textures/gui/notice_frame.png')

# Four private-use glyphs: cast identifier, trail, damage flash, announcement.
font_img = Image.new('RGBA', (64, 16), (0, 0, 0, 0)); g = ImageDraw.Draw(font_img)
for i, color in enumerate([(98, 220, 255, 255), (178, 255, 230, 255), (255, 86, 100, 255), (245, 202, 92, 255)]):
    x = i * 16
    g.rectangle((x + 2, 2, x + 13, 13), outline=color, width=2)
    g.line((x + 5, 8, x + 11, 8), fill=color, width=2)
font_img.save(ASSETS / 'font/forge_glyphs.png')
(ASSETS / 'font/forge.json').write_text(json.dumps({"providers": [{"type": "bitmap", "file": "forge_solo:font/forge_glyphs.png", "ascent": 8, "height": 8, "chars": ["\ue000\ue001\ue002\ue003"]}]}, indent=2) + '\n')

# A small deterministic synth creates placeholder SFX that can be replaced with authored audio later.
def tone(path, base, length=0.16, sweep=0):
    rate = 22050; n = int(rate * length)
    frames = bytearray()
    for i in range(n):
        t = i / rate
        freq = base + sweep * (i / max(1, n))
        env = min(1, i / (rate * .015)) * min(1, (n - i) / (rate * .045))
        val = int(13000 * env * math.sin(2 * math.pi * freq * t))
        frames += struct.pack('<h', val)
    wav = path.with_suffix('.wav')
    with wave.open(str(wav), 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(rate); f.writeframes(frames)
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '4', str(path)], check=True)
    wav.unlink()

tone(ASSETS / 'sounds/cast_nature.ogg', 520, .20, 180)
tone(ASSETS / 'sounds/cast_arcane.ogg', 740, .24, 420)
tone(ASSETS / 'sounds/cast_radiant.ogg', 660, .22, 300)
tone(ASSETS / 'sounds/cast_shadow.ogg', 180, .26, -80)
tone(ASSETS / 'sounds/impact.ogg', 110, .15, 220)
tone(ASSETS / 'sounds/notice.ogg', 440, .30, 90)

(ASSETS / 'sounds.json').write_text(json.dumps({
    'cast.nature': {'sounds': [{'name': 'forge_solo:cast_nature', 'volume': .65}]},
    'cast.arcane': {'sounds': [{'name': 'forge_solo:cast_arcane', 'volume': .65}]},
    'cast.radiant': {'sounds': [{'name': 'forge_solo:cast_radiant', 'volume': .65}]},
    'cast.shadow': {'sounds': [{'name': 'forge_solo:cast_shadow', 'volume': .65}]},
    'impact': {'sounds': [{'name': 'forge_solo:impact', 'volume': .75}]},
    'notice': {'sounds': [{'name': 'forge_solo:notice', 'volume': .75}]},
}, indent=2) + '\n')

(ROOT / 'README.md').write_text('''# Forge Solo Subseason Visual Pack\n\nTarget: Minecraft Java 26.3, resource pack format 97.1.\n\nThis pack contains profile icons, transparent panel/overlay assets, a custom bitmap font, and short placeholder cast/impact/notice sounds. The server sends cast identifiers and announcement text using the `forge_solo:forge` font. The image assets are intentionally subtle and can be replaced by an art pass without changing the server protocol.\n\nInstall by uploading `forge-solo-visuals-26.3.zip` to an HTTPS host, then set the server resource-pack URL and SHA-1 in `server.properties`. Keep `require-resource-pack=true` if the UI is required.\n''')
print(ROOT)

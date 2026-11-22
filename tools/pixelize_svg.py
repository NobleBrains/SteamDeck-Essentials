"""Turn a controller outline SVG into hard-edged pixel art (white on transparent).

usage: pixelize.py <in.svg> <out.png> <target_width> <stroke_width_svg_units> <threshold>
"""
import re, subprocess, sys, tempfile, os
from PIL import Image

src, out, tw, sw, thr = sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4]), float(sys.argv[5])
svg = open(src).read()
# all strokes white with one uniform width, body fills dark (only outlines survive)
svg = re.sub(r'stroke="#[0-9A-Fa-f]{6}"', 'stroke="white"', svg)
svg = re.sub(r'stroke-width="[0-9.]+"', f'stroke-width="{sw}"', svg)
svg = re.sub(r'fill="#0D131B"', 'fill="none"', svg, flags=re.I)
vb = re.search(r'viewBox="0 0 ([0-9.]+) ([0-9.]+)"', svg)
vw, vh = float(vb.group(1)), float(vb.group(2))
th = round(tw * vh / vw)
ss = 8  # supersampling
with tempfile.TemporaryDirectory() as d:
    p = os.path.join(d, 'a.svg'); open(p, 'w').write(svg)
    big = os.path.join(d, 'big.png')
    density = 96 * tw * ss / vw
    subprocess.run(['magick', '-background', 'black', '-density', str(density), p,
                    '-flatten', '-colorspace', 'Gray', big], check=True)
    im = Image.open(big).convert('L')
small = im.resize((tw, th), Image.BOX)
outim = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
px = small.load(); po = outim.load()
for y in range(th):
    for x in range(tw):
        if px[x, y] / 255.0 >= thr:
            po[x, y] = (255, 255, 255, 255)
outim.save(out)
print(out, tw, th)

# Used for the configurator art (run from ~/AGY/Minecraft/Deck):
#   python3 -I SteamDeck-Essentials/tools/pixelize_svg.py controller_config_controller_triton.svg \
#       SteamDeck-Essentials/src/main/resources/assets/steamdeck_essentials/textures/gui/controller_triton.png 200 4.6 0.33
#   python3 -I SteamDeck-Essentials/tools/pixelize_svg.py controller_config_controller_steam_deck.svg \
#       SteamDeck-Essentials/src/main/resources/assets/steamdeck_essentials/textures/gui/controller_deck.png 200 2.3 0.33
# Needs ImageMagick (magick) and Pillow. Hot spots in ControllerArt.java are in texture pixels.

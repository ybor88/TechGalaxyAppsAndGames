"""
Genera logo.png per HoopIQ — eseguire una volta sola.
Richiede: pip install Pillow
"""
import os
import math
from PIL import Image, ImageDraw, ImageFont

W, H = 600, 600
img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
draw = ImageDraw.Draw(img)

# ── sfondo degradato scuro ────────────────────────────────
for y in range(H):
    v = int(10 + (y / H) * 6)
    draw.line([(0, y), (W, y)], fill=(v, v, v, 255))

# ── cerchio basket (NBA silhouette style) ─────────────────
cx, cy, cr = 300, 180, 140
# cerchio esterno blu
draw.ellipse([cx-cr, cy-cr, cx+cr, cy+cr], outline=(29, 66, 138), width=10)
# cerchio interno rosso
draw.ellipse([cx-cr+14, cy-cr+14, cx+cr-14, cy+cr-14],
             outline=(200, 16, 46), width=6)

# linee basket (spicchi)
draw.arc([cx-cr+20, cy-cr+20, cx+cr-20, cy+cr-20],
         start=0, end=360, fill=(200, 16, 46), width=4)
# linea verticale
draw.line([cx, cy-cr+10, cx, cy+cr-10], fill=(200, 16, 46), width=5)
# linee curve orizzontali
for offset, angle_range in [(-45, (190, 350)), (45, (10, 170))]:
    draw.arc([cx-cr//2+offset, cy-cr+30, cx+cr//2+offset, cy+cr-30],
             start=angle_range[0], end=angle_range[1],
             fill=(200, 16, 46), width=4)

# pallone arancio
ball_r = 55
draw.ellipse([cx-ball_r, cy-ball_r, cx+ball_r, cy+ball_r],
             fill=(240, 110, 30), outline=(180, 70, 10), width=3)
# linee pallone
draw.arc([cx-ball_r, cy-ball_r, cx+ball_r, cy+ball_r],
         start=200, end=340, fill=(150, 50, 5), width=3)
draw.arc([cx-ball_r, cy-ball_r, cx+ball_r, cy+ball_r],
         start=20, end=160, fill=(150, 50, 5), width=3)
draw.line([cx-ball_r+4, cy, cx+ball_r-4, cy], fill=(150, 50, 5), width=3)
draw.line([cx, cy-ball_r+4, cx, cy+ball_r-4], fill=(150, 50, 5), width=3)

# ── HOOP ─────────────────────────────────────────────────
try:
    font_hoop = ImageFont.truetype("C:/Windows/Fonts/ariblk.ttf", 110)
    font_iq   = ImageFont.truetype("C:/Windows/Fonts/ariblk.ttf", 130)
    font_sub  = ImageFont.truetype("C:/Windows/Fonts/arial.ttf",  26)
except Exception:
    font_hoop = ImageFont.load_default()
    font_iq   = font_hoop
    font_sub  = font_hoop

# shadow HOOP
draw.text((299, 341), "HOOP", font=font_hoop,
          fill=(0, 0, 0, 180), anchor="mt")
draw.text((300, 340), "HOOP", font=font_hoop,
          fill=(255, 255, 255, 255), anchor="mt")

# IQ — "I" blu, "Q" rosso
bbox_i = draw.textbbox((0, 0), "I", font=font_iq)
iw = bbox_i[2] - bbox_i[0]
total_bbox = draw.textbbox((0, 0), "IQ", font=font_iq)
tw = total_bbox[2] - total_bbox[0]
x_start = W // 2 - tw // 2

draw.text((x_start - 2, 452), "I",  font=font_iq,
          fill=(29, 66, 138), anchor="lt")
draw.text((x_start + iw - 2, 452), "Q", font=font_iq,
          fill=(200, 16, 46), anchor="lt")

# ── tagline ───────────────────────────────────────────────
draw.text((W//2, 568), "SCOUT. ANALYZE. ELEVATE.",
          font=font_sub, fill=(180, 180, 180), anchor="mt")

# ── salva ─────────────────────────────────────────────────
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logo.png")
img.save(out, "PNG")
print(f"Logo salvato: {out}")

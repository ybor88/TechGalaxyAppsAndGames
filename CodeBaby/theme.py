"""Palette e font di CodeBaby, ispirati al logo: robot blu, scritte gialle/blu
con contorno rosso, sfondo rosso-arancio a raggiera, cubetti colorati sorridenti."""
import os
import pygame

# ---- Colori (dal logo) --------------------------------------------------
RED = (214, 40, 40)
RED_DARK = (150, 22, 22)
ORANGE = (255, 140, 46)
GOLD = (255, 199, 40)
GOLD_DARK = (222, 150, 10)
BLUE = (33, 118, 209)
BLUE_DARK = (18, 74, 140)
SKY = (110, 200, 245)
GREEN = (108, 191, 74)
GREEN_DARK = (70, 140, 45)
PURPLE = (150, 92, 196)
PURPLE_DARK = (104, 56, 145)
CREAM = (255, 250, 238)
WHITE = (255, 255, 255)
INK = (46, 40, 60)
GREY = (170, 170, 180)
GREY_LIGHT = (222, 222, 230)
TEAL = (20, 150, 140)
TEAL_DARK = (12, 105, 98)
PANEL = (255, 255, 255)
SHADOW = (0, 0, 0, 60)

CUBE_COLORS = [GREEN, BLUE, (240, 90, 60)]

FONT_DIR = r"C:\Windows\Fonts"
_FONT_REGULAR = os.path.join(FONT_DIR, "comic.ttf")
_FONT_BOLD = os.path.join(FONT_DIR, "comicbd.ttf")

_cache = {}


def font(size, bold=True):
    key = (size, bold)
    if key in _cache:
        return _cache[key]
    path = _FONT_BOLD if bold else _FONT_REGULAR
    try:
        f = pygame.font.Font(path, size)
    except Exception:
        f = pygame.font.SysFont("comicsansms,arial", size, bold=bold)
    _cache[key] = f
    return f


def draw_text(surface, text, size, color, center=None, topleft=None, bold=True,
              outline=None, outline_size=2):
    f = font(size, bold)
    if outline:
        base = f.render(text, True, outline)
        for dx in range(-outline_size, outline_size + 1):
            for dy in range(-outline_size, outline_size + 1):
                if dx == 0 and dy == 0:
                    continue
                r = base.get_rect()
                if center:
                    r.center = (center[0] + dx, center[1] + dy)
                else:
                    r.topleft = (topleft[0] + dx, topleft[1] + dy)
                surface.blit(base, r)
    img = f.render(text, True, color)
    rect = img.get_rect()
    if center:
        rect.center = center
    elif topleft:
        rect.topleft = topleft
    surface.blit(img, rect)
    return rect

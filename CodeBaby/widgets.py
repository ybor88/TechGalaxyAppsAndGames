"""Elementi grafici riutilizzabili: sfondi, pulsanti tondi, pannelli, cubetti mascotte."""
import math
import pygame
import theme


def draw_sunburst_background(surface, w, h, t=0.0):
    """Sfondo rosso-arancio a raggiera come nel logo, con qualche nuvoletta."""
    surface.fill(theme.RED_DARK)
    cx, cy = w // 2, int(h * 0.38)
    max_r = int(math.hypot(w, h))
    n_rays = 16
    for i in range(n_rays):
        a0 = (i / n_rays) * 2 * math.pi + t * 0.02
        a1 = ((i + 0.55) / n_rays) * 2 * math.pi + t * 0.02
        col = theme.ORANGE if i % 2 == 0 else theme.RED
        points = [(cx, cy)]
        for a in (a0, a1):
            points.append((cx + math.cos(a) * max_r, cy + math.sin(a) * max_r))
        pygame.draw.polygon(surface, col, points)
    # vignetta morbida in basso per leggibilità
    fade = pygame.Surface((w, h), pygame.SRCALPHA)
    pygame.draw.rect(fade, (100, 15, 15, 90), (0, int(h * 0.62), w, int(h * 0.38)))
    surface.blit(fade, (0, 0))
    # nuvolette bianche semi-trasparenti
    for (fx, fy, s) in [(0.12, 0.18, 1.0), (0.85, 0.14, 0.8), (0.7, 0.30, 0.6), (0.22, 0.32, 0.7)]:
        _cloud(surface, int(w * fx), int(h * fy + math.sin(t * 0.03 + fx * 10) * 4), s)


def _cloud(surface, x, y, scale):
    cloud = pygame.Surface((160, 70), pygame.SRCALPHA)
    for (dx, dy, r) in [(40, 35, 28), (75, 20, 34), (115, 35, 26), (25, 45, 20), (135, 45, 18)]:
        pygame.draw.circle(cloud, (255, 255, 255, 70), (dx, dy), r)
    cloud = pygame.transform.rotozoom(cloud, 0, scale)
    surface.blit(cloud, (x, y))


def rounded_rect(surface, rect, color, radius=18, border=0, border_color=None):
    pygame.draw.rect(surface, color, rect, border_radius=radius)
    if border:
        pygame.draw.rect(surface, border_color or theme.INK, rect, width=border, border_radius=radius)


def drop_shadow(surface, rect, radius=18, offset=(0, 6), alpha=70):
    shadow = pygame.Surface((rect.width + 20, rect.height + 20), pygame.SRCALPHA)
    r = pygame.Rect(10, 10, rect.width, rect.height)
    pygame.draw.rect(shadow, (0, 0, 0, alpha), r, border_radius=radius)
    surface.blit(shadow, (rect.x - 10 + offset[0], rect.y - 10 + offset[1]))


class Button:
    """Pulsante grande, tondo e colorato con effetto 'premuto' quando ci passi sopra."""

    def __init__(self, rect, text, color=None, text_color=theme.WHITE, size=34,
                 icon=None, enabled=True):
        self.rect = pygame.Rect(rect)
        self.text = text
        self.color = color or theme.BLUE
        self.text_color = text_color
        self.size = size
        self.icon = icon
        self.enabled = enabled
        self.hover = False

    def handle_hover(self, pos):
        self.hover = self.enabled and self.rect.collidepoint(pos)
        return self.hover

    def clicked(self, pos):
        return self.enabled and self.rect.collidepoint(pos)

    def draw(self, surface):
        r = self.rect.copy()
        col = self.color
        if not self.enabled:
            col = theme.GREY
        drop_shadow(surface, r, radius=r.height // 2, offset=(0, 5))
        press = 3 if self.hover else 0
        r2 = r.move(0, press)
        rounded_rect(surface, r2, col, radius=r2.height // 2)
        highlight = pygame.Rect(r2.x + 6, r2.y + 4, r2.width - 12, r2.height // 2 - 4)
        hl = pygame.Surface((highlight.width, highlight.height), pygame.SRCALPHA)
        pygame.draw.rect(hl, (255, 255, 255, 55), hl.get_rect(), border_radius=highlight.height)
        surface.blit(hl, highlight.topleft)
        pygame.draw.rect(surface, theme.INK, r2, width=3, border_radius=r2.height // 2)
        label = self.text
        if self.icon:
            label = f"{self.icon}  {self.text}"
        theme.draw_text(surface, label, self.size, self.text_color if self.enabled else (240, 240, 240),
                         center=r2.center, bold=True)


def draw_cube_face(surface, center, size, color, happy=True):
    """Cubetto sorridente colorato, come i personaggi del logo."""
    x, y = center
    r = pygame.Rect(0, 0, size, size)
    r.center = center
    rounded_rect(surface, r, color, radius=size // 5)
    dark = tuple(max(0, c - 45) for c in color)
    pygame.draw.rect(surface, dark, r, width=max(2, size // 22), border_radius=size // 5)
    eye_off = size * 0.18
    eye_r = max(3, size // 11)
    for sgn in (-1, 1):
        ex, ey = x + sgn * eye_off, y - size * 0.06
        pygame.draw.circle(surface, theme.WHITE, (ex, ey), eye_r + 2)
        pygame.draw.circle(surface, theme.INK, (ex, ey), eye_r)
    mouth_rect = pygame.Rect(0, 0, size * 0.4, size * 0.28)
    mouth_rect.center = (x, y + size * 0.16)
    if happy:
        pygame.draw.arc(surface, theme.INK, mouth_rect, math.pi * 1.05, math.pi * 1.95, max(2, size // 16))
    else:
        pygame.draw.arc(surface, theme.INK, mouth_rect, math.pi * 0.05, math.pi * 0.95, max(2, size // 16))


def draw_star(surface, center, r_outer, color, r_inner=None, border=theme.INK, width=3):
    r_inner = r_inner or r_outer * 0.45
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rr = r_outer if i % 2 == 0 else r_inner
        pts.append((center[0] + math.cos(ang) * rr, center[1] + math.sin(ang) * rr))
    pygame.draw.polygon(surface, color, pts)
    if width:
        pygame.draw.polygon(surface, border, pts, width=width)


def draw_padlock(surface, center, size, color=theme.WHITE):
    x, y = center
    body = pygame.Rect(0, 0, size * 0.62, size * 0.5)
    body.center = (x, y + size * 0.14)
    pygame.draw.rect(surface, color, body, border_radius=int(size * 0.08))
    shackle = pygame.Rect(0, 0, size * 0.4, size * 0.44)
    shackle.midbottom = (x, body.top + size * 0.06)
    pygame.draw.arc(surface, color, shackle, 0, math.pi, max(2, int(size * 0.09)))
    pygame.draw.circle(surface, tuple(max(0, c - 60) for c in color) if color != theme.WHITE else theme.GREY,
                        (x, y + size * 0.16), max(2, size * 0.06))


def wrap_text(text, size, max_width, bold=False):
    f = theme.font(size, bold)
    words = text.split(" ")
    lines = []
    cur = ""
    for w in words:
        trial = (cur + " " + w).strip()
        if f.size(trial)[0] > max_width and cur:
            lines.append(cur)
            cur = w
        else:
            cur = trial
    if cur:
        lines.append(cur)
    return lines

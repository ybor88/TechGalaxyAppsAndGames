"""Piccole icone disegnate a mano (frecce, giri, ripeti) per i blocchi comando,
cosi' non serve nessun font speciale con simboli."""
import math
import pygame


def arrow_up(surface, center, size, color):
    x, y = center
    w, h = size * 0.34, size * 0.5
    stem = pygame.Rect(0, 0, w * 0.5, h * 0.55)
    stem.center = (x, y + h * 0.28)
    pygame.draw.rect(surface, color, stem, border_radius=3)
    tip = (x, y - h * 0.5)
    p1 = (x - w * 0.65, y - h * 0.02)
    p2 = (x + w * 0.65, y - h * 0.02)
    pygame.draw.polygon(surface, color, [tip, p1, p2])


def turn_arrow(surface, center, size, color, clockwise=True):
    x, y = center
    r = size * 0.32
    start_ang, end_ang = (-40, 220) if clockwise else (220, -40)
    rect = pygame.Rect(0, 0, r * 2, r * 2)
    rect.center = center
    a0, a1 = sorted((math.radians(start_ang), math.radians(end_ang)))
    pygame.draw.arc(surface, color, rect, a0, a1, max(3, int(size * 0.09)))
    end_ang_rad = math.radians(end_ang if clockwise else start_ang)
    tip = (x + math.cos(end_ang_rad) * r, y + math.sin(end_ang_rad) * r)
    perp = end_ang_rad + (math.pi / 2 if clockwise else -math.pi / 2)
    back = (tip[0] - math.cos(end_ang_rad) * size * 0.16, tip[1] - math.sin(end_ang_rad) * size * 0.16)
    side = (tip[0] + math.cos(perp) * size * 0.13, tip[1] + math.sin(perp) * size * 0.13)
    pygame.draw.polygon(surface, color, [tip, back, side])


def repeat_icon(surface, center, size, color):
    x, y = center
    r = size * 0.30
    rect = pygame.Rect(0, 0, r * 2, r * 1.7)
    rect.center = center
    pygame.draw.arc(surface, color, rect, math.radians(-160), math.radians(140), max(3, int(size * 0.08)))
    ang = math.radians(140)
    tip = (rect.centerx + math.cos(ang) * r, rect.centery + math.sin(ang) * (r * 0.85))
    pygame.draw.polygon(surface, color, [
        (tip[0] - size * 0.08, tip[1] - size * 0.03),
        (tip[0] + size * 0.09, tip[1] + size * 0.02),
        (tip[0] - size * 0.02, tip[1] + size * 0.13),
    ])


def gem_icon(surface, center, size, color):
    """Piccolo rombo scintillante, per le gemme da raccogliere (variabili/array)."""
    x, y = center
    w, h = size * 0.30, size * 0.42
    pts = [(x, y - h), (x + w, y - h * 0.1), (x, y + h), (x - w, y - h * 0.1)]
    pygame.draw.polygon(surface, color, pts)
    pygame.draw.polygon(surface, (255, 255, 255), pts, width=max(1, int(size * 0.03)))


def key_icon(surface, center, size, color):
    """Chiave semplice: testa rotonda + asta + due dentini."""
    x, y = center
    head_r = size * 0.17
    head_c = (x - size * 0.14, y)
    pygame.draw.circle(surface, color, head_c, head_r, width=max(2, int(size * 0.06)))
    shaft_start = (head_c[0] + head_r * 0.7, y)
    shaft_end = (x + size * 0.34, y)
    pygame.draw.line(surface, color, shaft_start, shaft_end, max(3, int(size * 0.07)))
    tooth_w = max(2, int(size * 0.06))
    pygame.draw.line(surface, color, (shaft_end[0] - size * 0.05, y),
                      (shaft_end[0] - size * 0.05, y + size * 0.12), tooth_w)
    pygame.draw.line(surface, color, shaft_end, (shaft_end[0], y + size * 0.16), tooth_w)


def function_icon(surface, center, size, color):
    """Blocchetto con una piccola stella dentro: rappresenta una sequenza
    'salvata con un nome' da richiamare, come una mini-funzione."""
    x, y = center
    r = size * 0.30
    rect = pygame.Rect(0, 0, r * 1.9, r * 1.9)
    rect.center = center
    pygame.draw.rect(surface, color, rect, width=max(2, int(size * 0.07)), border_radius=int(r * 0.4))
    pts = []
    r_out, r_in = r * 0.5, r * 0.22
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rr = r_out if i % 2 == 0 else r_in
        pts.append((x + math.cos(ang) * rr, y + math.sin(ang) * rr))
    pygame.draw.polygon(surface, color, pts)

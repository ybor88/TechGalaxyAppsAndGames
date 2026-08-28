"""Disegna la mascotte CodeBaby: un robottino blu con antenna dorata, ispirato
al logo. Resta sempre 'dritto' (guarda il bambino) e mostra un piccolo
indicatore triangolare per far capire in che direzione e' rivolto sulla
griglia, cosi' il visetto non finisce mai a testa in giu'."""
import math
import pygame
import theme

DIR_ANGLES = {0: -90, 1: 0, 2: 90, 3: 180}  # gradi, 0 = verso destra in pygame


def draw_robot(surface, center, size, facing=None, mood="idle", t=0.0, bump=0.0):
    """mood: idle | happy | sad. facing: 0..3 oppure None per nasconderlo (menu)."""
    x, y = center
    bob = math.sin(t * 4.0) * size * 0.02 if mood != "sad" else 0
    y += bob
    if bump:
        x += math.sin(t * 40) * bump

    body_r = size * 0.36
    head_r = size * 0.30

    # ombra
    shadow = pygame.Surface((int(size * 1.1), int(size * 0.28)), pygame.SRCALPHA)
    pygame.draw.ellipse(shadow, (0, 0, 0, 70), shadow.get_rect())
    surface.blit(shadow, (x - shadow.get_width() / 2, y + size * 0.62))

    # corpo (cubo tondeggiante)
    body_rect = pygame.Rect(0, 0, size * 0.62, size * 0.5)
    body_rect.center = (x, y + size * 0.38)
    pygame.draw.rect(surface, theme.BLUE, body_rect, border_radius=int(size * 0.14))
    pygame.draw.rect(surface, theme.BLUE_DARK, body_rect, width=max(2, int(size * 0.02)),
                      border_radius=int(size * 0.14))
    # schermo sul petto con simbolo codice
    screen_rect = body_rect.inflate(-size * 0.30, -size * 0.26)
    pygame.draw.rect(surface, theme.SKY, screen_rect, border_radius=int(size * 0.06))
    theme.draw_text(surface, "</>", max(10, int(size * 0.16)), theme.BLUE_DARK,
                     center=screen_rect.center, bold=True)

    # braccia
    for sgn in (-1, 1):
        ax = x + sgn * (body_rect.width / 2 + size * 0.03)
        pygame.draw.line(surface, theme.BLUE_DARK, (ax, y + size * 0.30), (ax, y + size * 0.5),
                          max(3, int(size * 0.05)))

    # testa: cuffia blu (come nel logo) con il viso bianco sotto
    head_center = (x, y)
    ear_r = head_r * 0.24
    for sgn in (-1, 1):
        pygame.draw.circle(surface, theme.BLUE, (x + sgn * head_r * 0.92, y - head_r * 0.02), ear_r)
        pygame.draw.circle(surface, theme.BLUE_DARK, (x + sgn * head_r * 0.92, y - head_r * 0.02), ear_r, width=2)
    pygame.draw.circle(surface, theme.BLUE, head_center, head_r)
    pygame.draw.circle(surface, theme.BLUE_DARK, head_center, head_r, width=max(2, int(size * 0.02)))

    # antenna
    ant_top = (x, y - head_r - size * 0.16)
    pygame.draw.line(surface, theme.INK, (x, y - head_r * 0.7), ant_top, max(2, int(size * 0.03)))
    pygame.draw.circle(surface, theme.GOLD, ant_top, max(4, int(size * 0.07)))
    pygame.draw.circle(surface, theme.GOLD_DARK, ant_top, max(4, int(size * 0.07)), width=2)

    # viso bianco (sotto la cuffia blu)
    face_r = head_r * 0.86
    fx, fy = x, y + head_r * 0.24
    pygame.draw.circle(surface, theme.WHITE, (fx, fy), face_r)
    pygame.draw.circle(surface, theme.BLUE_DARK, (fx, fy), face_r, width=2)

    eye_dy = -head_r * 0.08
    eye_off = head_r * 0.40
    eye_r = max(3, head_r * 0.19)
    for sgn in (-1, 1):
        ex, ey = fx + sgn * eye_off, fy + eye_dy
        if mood == "sad":
            pygame.draw.circle(surface, theme.INK, (ex, ey), eye_r * 0.85)
        else:
            pygame.draw.circle(surface, theme.INK, (ex, ey), eye_r)
            pygame.draw.circle(surface, theme.WHITE, (ex - eye_r * 0.3, ey - eye_r * 0.3), eye_r * 0.3)

    mouth_rect = pygame.Rect(0, 0, head_r * 0.85, head_r * 0.55)
    mouth_rect.center = (fx, fy + head_r * 0.32)
    if mood == "happy":
        mouth_rect.centery -= head_r * 0.12
        pygame.draw.arc(surface, theme.INK, mouth_rect, math.radians(200), math.radians(340),
                         max(2, int(size * 0.03)))
    elif mood == "sad":
        mouth_rect.centery += head_r * 0.05
        pygame.draw.arc(surface, theme.INK, mouth_rect, math.radians(15), math.radians(165),
                         max(2, int(size * 0.03)))
    else:
        pygame.draw.line(surface, theme.INK, (fx - head_r * 0.26, fy + head_r * 0.32),
                          (fx + head_r * 0.26, fy + head_r * 0.32), max(2, int(size * 0.03)))

    # indicatore di direzione (usato solo sulla griglia)
    if facing is not None:
        ang = math.radians(DIR_ANGLES[facing])
        dist = size * 0.62
        tip = (x + math.cos(ang) * dist, y + math.sin(ang) * dist)
        perp = ang + math.pi / 2
        w = size * 0.11
        p1 = (tip[0] - math.cos(perp) * w, tip[1] - math.sin(perp) * w)
        p2 = (tip[0] + math.cos(perp) * w, tip[1] + math.sin(perp) * w)
        back = (x + math.cos(ang) * (dist - size * 0.18), y + math.sin(ang) * (dist - size * 0.18))
        pygame.draw.polygon(surface, theme.GOLD, [tip, p1, back, p2])
        pygame.draw.polygon(surface, theme.GOLD_DARK, [tip, p1, back, p2], width=2)

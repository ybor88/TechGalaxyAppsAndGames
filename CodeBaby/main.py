"""CodeBaby - un gioco per imparare le basi della programmazione a scuola,
guidati dalla maestra o dal maestro insieme al robottino CodeBaby.

Avvio: python main.py
Modalita' di test automatico (usata in sviluppo): impostare la variabile
d'ambiente CODEBABY_SCREENSHOTS su una cartella per salvare alcune schermate
e uscire subito, senza bisogno di un vero schermo.
"""
import os
import random
import sys

import pygame

import engine
import icons
import levels as levels_mod
import robot as robot_mod
import save as save_mod
import theme
import widgets

W, H = 1150, 760
FPS = 60

TOP_BAR = pygame.Rect(0, 0, W, 84)
TOOLBOX_RECT = pygame.Rect(16, 98, 210, 470)
CONTROLS_RECT = pygame.Rect(16, 580, 210, 160)
WORLD_RECT = pygame.Rect(240, 98, 660, 380)
PROGRAM_RECT = pygame.Rect(240, 490, 660, 250)
BACK_RECT = pygame.Rect(920, 98, 214, 60)

ACTION_COLOR = {"fwd": theme.GREEN, "left": theme.ORANGE, "right": theme.BLUE, "repeat": theme.PURPLE,
                "grab": theme.GOLD, "call": theme.TEAL}
ACTION_LABEL = {"fwd": "Avanti", "left": "Gira Sinistra", "right": "Gira Destra", "repeat": "Ripeti...",
                 "grab": "Raccogli"}
ACTION_ORDER = ["fwd", "left", "right", "grab", "repeat"]
MAX_BLOCKS = 16
FUNCTION_DEFINE_KEY = "define_fn"
KEY_COLOR = {"gold": theme.GOLD, "blue": theme.BLUE, "green": theme.GREEN, "red": theme.RED}


# --------------------------------------------------------------------------- #
# Particelle coriandoli per la vittoria
# --------------------------------------------------------------------------- #
class Confetti:
    def __init__(self, rect):
        self.parts = []
        colors = [theme.GOLD, theme.RED, theme.BLUE, theme.GREEN, theme.ORANGE, theme.PURPLE]
        for _ in range(70):
            self.parts.append({
                "x": random.uniform(rect.left, rect.right),
                "y": random.uniform(rect.top - 200, rect.top - 10),
                "vy": random.uniform(90, 200),
                "vx": random.uniform(-30, 30),
                "size": random.uniform(5, 10),
                "color": random.choice(colors),
                "rot": random.uniform(0, 360),
                "vrot": random.uniform(-200, 200),
            })
        self.rect = rect

    def update(self, dt):
        for p in self.parts:
            p["y"] += p["vy"] * dt
            p["x"] += p["vx"] * dt
            p["rot"] += p["vrot"] * dt
            if p["y"] > self.rect.bottom + 20:
                p["y"] = self.rect.top - 20
                p["x"] = random.uniform(self.rect.left, self.rect.right)

    def draw(self, surface):
        for p in self.parts:
            s = pygame.Surface((p["size"], p["size"] * 1.6), pygame.SRCALPHA)
            s.fill(p["color"])
            s = pygame.transform.rotate(s, p["rot"])
            surface.blit(s, (p["x"], p["y"]))


# --------------------------------------------------------------------------- #
# Sessione di gioco per il livello corrente
# --------------------------------------------------------------------------- #
class LevelSession:
    def __init__(self, level):
        self.level = level
        self.world = engine.World(level)
        self.blocks = []
        self.status = "intro"  # intro | build | running | crash | miss | win
        self.logical_pos = self.world.start
        self.logical_facing = self.world.start_facing
        self.anim_pos = self.logical_pos
        self.anim_facing = self.logical_facing
        self.run_steps = []
        self.run_i = 0
        self.anim_from_pos = self.logical_pos
        self.anim_to_pos = self.logical_pos
        self.anim_from_facing = self.logical_facing
        self.anim_to_facing = self.logical_facing
        self.anim_progress = 0.0
        self.anim_duration = 0.4
        self.pending_result = None
        self.status_timer = 0.0
        self.mood = "idle"
        self.confetti = None
        self.stars = 0
        self.message = ""
        self.bump = 0.0
        self.pulse = 0.0
        self.function_body = []
        self.defining_function = False

    def reset_robot(self):
        self.logical_pos = self.world.start
        self.logical_facing = self.world.start_facing
        self.anim_pos = self.logical_pos
        self.anim_facing = self.logical_facing
        self.mood = "idle"
        self.bump = 0.0

    def start_build(self):
        self.status = "build"

    def clear_blocks(self):
        if self.status == "build":
            self.blocks = []
            self.function_body = []
            self.defining_function = False

    def add_block(self, action, count=1):
        if self.status == "build" and len(self.blocks) < MAX_BLOCKS:
            self.blocks.append(engine.Block(action, count))

    def remove_block(self, idx):
        if self.status == "build" and 0 <= idx < len(self.blocks):
            del self.blocks[idx]

    def add_function_step(self, action):
        cap = self.level.get("function_capacity", 4)
        if self.status == "build" and self.defining_function and len(self.function_body) < cap:
            self.function_body.append(action)

    def remove_function_step(self, idx):
        if self.status == "build" and self.defining_function and 0 <= idx < len(self.function_body):
            del self.function_body[idx]

    def play(self):
        if self.status != "build" or not self.blocks:
            return
        self.world.collected_gems = set()
        self.world.collected_keys = set()
        self.world.next_gem_index = 0
        self.run_steps = engine.flatten(self.blocks, self.function_body)
        self.run_i = 0
        self.reset_robot()
        self.status = "running"
        self._start_step(0)

    def _start_step(self, i):
        block_idx, action = self.run_steps[i]
        old_pos, old_facing = self.logical_pos, self.logical_facing
        new_pos, new_facing, result = engine.simulate_step(self.world, old_pos, old_facing, action)
        self.current_block = block_idx
        self.anim_from_pos = old_pos
        self.anim_to_pos = new_pos
        self.anim_from_facing = old_facing
        self.anim_to_facing = new_facing
        self.anim_progress = 0.0
        self.anim_duration = 0.42 if action == "fwd" else 0.30
        self.pending_result = result
        self.logical_pos, self.logical_facing = new_pos, new_facing

    def update(self, dt, world_rect, save_data):
        if self.pulse > 0:
            self.pulse = max(0.0, self.pulse - dt)
        if self.status == "running":
            self.anim_progress += dt / self.anim_duration
            t = min(1.0, self.anim_progress)
            fx = self.anim_from_pos[0] + (self.anim_to_pos[0] - self.anim_from_pos[0]) * t
            fy = self.anim_from_pos[1] + (self.anim_to_pos[1] - self.anim_from_pos[1]) * t
            self.anim_pos = (fx, fy)
            self.anim_facing = _lerp_angle(self.anim_from_facing, self.anim_to_facing, t)
            if self.anim_progress >= 1.0:
                if self.pending_result == engine.RunResult.CRASH:
                    self.status = "crash"
                    self.mood = "sad"
                    self.status_timer = 0.0
                    self.message = "Ops! Ho sbattuto contro un ostacolo. Riprovo!"
                elif self.pending_result == engine.RunResult.ORDER_ERROR:
                    self.status = "crash"
                    self.mood = "sad"
                    self.status_timer = 0.0
                    self.message = "Attento all'ordine! Negli ARRAY conta la sequenza giusta."
                elif self.pending_result == engine.RunResult.WIN:
                    self.status = "win"
                    self.mood = "happy"
                    self.confetti = Confetti(pygame.Rect(0, 0, W, H))
                    self.stars = self._compute_stars()
                    save_mod.register_win(save_data, self.level["id"], self.stars)
                else:
                    if self.pending_result == engine.RunResult.COLLECT:
                        self.pulse = 0.4
                        self.mood = "happy"
                    self.run_i += 1
                    if self.run_i >= len(self.run_steps):
                        self.status = "miss"
                        self.mood = "sad"
                        self.status_timer = 0.0
                        self.message = "Non sono ancora arrivato alla stella. Riprova!"
                    else:
                        self._start_step(self.run_i)
        elif self.status in ("crash", "miss"):
            self.status_timer += dt
            self.bump = max(0, 0.35 - self.status_timer) * 2.2
            if self.status_timer > 1.0:
                self.reset_robot()
                self.status = "build"
        elif self.status == "win" and self.confetti:
            self.confetti.update(dt)

    def _compute_stars(self):
        used = sum(1 for b in self.blocks)
        ideal = self.level.get("ideal", used)
        if used <= ideal:
            return 3
        if used <= ideal + 3:
            return 2
        return 1


def _lerp_angle(a, b, t):
    da = (b - a) % 4
    if da > 2:
        da -= 4
    return a + da * t


# --------------------------------------------------------------------------- #
# App principale
# --------------------------------------------------------------------------- #
class App:
    def __init__(self, screenshot_dir=None):
        pygame.init()
        pygame.display.set_caption("CodeBaby - Impariamo a programmare!")
        try:
            icon = pygame.Surface((64, 64), pygame.SRCALPHA)
            robot_mod.draw_robot(icon, (32, 34), 46, facing=None, mood="happy")
            pygame.display.set_icon(icon)
        except Exception:
            pass
        if screenshot_dir:
            self.display = pygame.display.set_mode((W, H))
            self.screen = self.display
            self.render_scale = 1.0
            self.render_offset = (0, 0)
        else:
            info = pygame.display.Info()
            desktop_size = (info.current_w, info.current_h)
            self.display = pygame.display.set_mode(desktop_size, pygame.FULLSCREEN)
            self.screen = pygame.Surface((W, H))
            self.render_scale = min(desktop_size[0] / W, desktop_size[1] / H)
            scaled_size = (round(W * self.render_scale), round(H * self.render_scale))
            self.render_offset = ((desktop_size[0] - scaled_size[0]) // 2,
                                   (desktop_size[1] - scaled_size[1]) // 2)
        self.clock = pygame.time.Clock()
        self.t = 0.0
        self.state = "splash"
        self.splash_timer = 0.0
        self.save = save_mod.load()
        self.session = None
        self.map_nodes = _build_map_nodes()
        self.modal = None
        self.screenshot_dir = screenshot_dir
        self.buttons = {}

    # ---- gestione livelli --------------------------------------------- #
    def enter_level(self, level_id):
        level = levels_mod.LEVELS_BY_ID[level_id]
        self.session = LevelSession(level)
        self.state = "level"

    def go_next_level(self):
        nxt = self.session.level["id"] + 1
        if nxt in levels_mod.LEVELS_BY_ID and nxt <= self.save["unlocked"]:
            self.enter_level(nxt)
        else:
            self.state = "map"

    # ---- loop principale ------------------------------------------------ #
    def run(self):
        if self.screenshot_dir:
            self._run_screenshot_tour()
            return
        running = True
        while running:
            dt = self.clock.tick(FPS) / 1000.0
            self.t += dt
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False
                else:
                    if event.type == pygame.MOUSEBUTTONDOWN:
                        rx, ry = event.pos
                        ox, oy = self.render_offset
                        event.pos = ((rx - ox) / self.render_scale, (ry - oy) / self.render_scale)
                    self.handle_event(event)
            self.update(dt)
            self.draw()
            if self.screen is not self.display:
                scaled_size = (round(W * self.render_scale), round(H * self.render_scale))
                scaled = pygame.transform.smoothscale(self.screen, scaled_size)
                self.display.fill((0, 0, 0))
                self.display.blit(scaled, self.render_offset)
            pygame.display.flip()
        pygame.quit()

    def handle_event(self, event):
        if event.type == pygame.KEYDOWN and event.key == pygame.K_ESCAPE:
            if self.state == "level":
                self.state = "map"
            return
        if event.type not in (pygame.MOUSEBUTTONDOWN,):
            if event.type == pygame.KEYDOWN and self.state == "splash":
                self.state = "menu"
            return
        if event.button != 1:
            return
        pos = event.pos
        if self.state == "splash":
            self.state = "menu"
        elif self.state == "menu":
            self._click_menu(pos)
        elif self.state == "howto":
            self._click_howto(pos)
        elif self.state == "map":
            self._click_map(pos)
        elif self.state == "level":
            self._click_level(pos)

    def update(self, dt):
        if self.state == "splash":
            self.splash_timer += dt
            if self.splash_timer > 6.0:
                self.state = "menu"
        elif self.state == "level" and self.session:
            self.session.update(dt, WORLD_RECT, self.save)

    # ---- click handlers --------------------------------------------- #
    def _click_menu(self, pos):
        for name, rect in self.buttons.get("menu", []):
            if rect.collidepoint(pos):
                if name == "play":
                    self.state = "map"
                elif name == "howto":
                    self.state = "howto"
                elif name == "quit":
                    pygame.event.post(pygame.event.Event(pygame.QUIT))

    def _click_howto(self, pos):
        for name, rect in self.buttons.get("howto", []):
            if rect.collidepoint(pos) and name == "back":
                self.state = "menu"

    def _click_map(self, pos):
        for node in self.map_nodes:
            lid = node["id"]
            unlocked = lid <= self.save["unlocked"]
            if unlocked and node["rect"].collidepoint(pos):
                self.enter_level(lid)
                return
        for name, rect in self.buttons.get("map", []):
            if rect.collidepoint(pos) and name == "back":
                self.state = "menu"

    def _click_level(self, pos):
        s = self.session
        if s.status == "intro":
            s.start_build()
            return
        if self.modal is not None:
            if BACK_RECT.collidepoint(pos):
                self.modal = None
                self.state = "map"
            else:
                self._click_modal(pos)
            return
        if BACK_RECT.collidepoint(pos):
            self.state = "map"
            return
        if s.status == "win":
            for name, rect in self.buttons.get("win", []):
                if rect.collidepoint(pos):
                    if name == "next":
                        self.go_next_level()
                    elif name == "map":
                        self.state = "map"
                    elif name == "retry":
                        self.enter_level(s.level["id"])
            return
        if s.status != "build":
            return
        if s.defining_function:
            for action, rect in self.buttons.get("toolbox", []):
                if rect.collidepoint(pos):
                    s.add_function_step(action)
                    return
            for idx, rect in self.buttons.get("program", []):
                if rect.collidepoint(pos):
                    s.remove_function_step(idx)
                    return
            for name, rect in self.buttons.get("controls", []):
                if rect.collidepoint(pos):
                    if name == "play" and s.function_body:
                        s.defining_function = False
                    elif name == "clear":
                        s.function_body = []
            return
        # toolbox
        for action, rect in self.buttons.get("toolbox", []):
            if rect.collidepoint(pos):
                if action == "repeat":
                    self.modal = {"action": "fwd", "count": 3}
                elif action == FUNCTION_DEFINE_KEY:
                    s.defining_function = True
                else:
                    s.add_block(action)
                return
        # program chips
        for idx, rect in self.buttons.get("program", []):
            if rect.collidepoint(pos):
                s.remove_block(idx)
                return
        # controls
        for name, rect in self.buttons.get("controls", []):
            if rect.collidepoint(pos):
                if name == "play":
                    s.play()
                elif name == "clear":
                    s.clear_blocks()
        return

    def _click_modal(self, pos):
        m = self.modal
        for key, rect in self.buttons.get("modal", []):
            if not rect.collidepoint(pos):
                continue
            if key.startswith("act:"):
                m["action"] = key.split(":", 1)[1]
            elif key == "minus":
                m["count"] = max(2, m["count"] - 1)
            elif key == "plus":
                m["count"] = min(9, m["count"] + 1)
            elif key == "add":
                self.session.add_block(m["action"], m["count"])
                self.modal = None
            elif key == "cancel":
                self.modal = None
            return

    # ---- disegno ------------------------------------------------------- #
    def draw(self):
        if self.state == "splash":
            self._draw_splash()
        elif self.state == "menu":
            self._draw_menu()
        elif self.state == "howto":
            self._draw_howto()
        elif self.state == "map":
            self._draw_map()
        elif self.state == "level":
            self._draw_level()

    def _draw_splash(self):
        widgets.draw_sunburst_background(self.screen, W, H, self.t * 60)
        bounce = abs(pygame.math.Vector2(0, 1).y) * 0
        by = H * 0.42 + __import__("math").sin(self.t * 3) * 10
        robot_mod.draw_robot(self.screen, (W // 2, int(by)), 240, facing=None, mood="happy", t=self.t)
        theme.draw_text(self.screen, "Code", 96, theme.BLUE, center=(W // 2 - 95, H * 0.66),
                         outline=theme.RED, outline_size=4)
        theme.draw_text(self.screen, "Baby", 96, theme.GOLD, center=(W // 2 + 115, H * 0.66),
                         outline=theme.RED, outline_size=4)
        alpha = int(180 + 75 * __import__("math").sin(self.t * 3))
        prompt = theme.font(26, True).render("Tocca lo schermo o premi un tasto per iniziare", True, theme.WHITE)
        prompt.set_alpha(max(60, alpha))
        r = prompt.get_rect(center=(W // 2, H * 0.86))
        self.screen.blit(prompt, r)

    def _draw_menu(self):
        widgets.draw_sunburst_background(self.screen, W, H, self.t * 40)
        theme.draw_text(self.screen, "Code", 78, theme.BLUE, center=(W // 2 - 78, 130), outline=theme.RED,
                         outline_size=4)
        theme.draw_text(self.screen, "Baby", 78, theme.GOLD, center=(W // 2 + 95, 130), outline=theme.RED,
                         outline_size=4)
        robot_mod.draw_robot(self.screen, (W // 2, 300), 150, facing=None, mood="idle", t=self.t)
        for i, (cx, color) in enumerate([(W // 2 - 260, theme.GREEN), (W // 2, theme.BLUE), (W // 2 + 260, theme.ORANGE)]):
            widgets.draw_cube_face(self.screen, (cx, 430), 70, color)

        btns = []
        labels = [("play", "GIOCA!", theme.GREEN), ("howto", "Per la maestra/il maestro", theme.BLUE),
                  ("quit", "Esci", theme.RED)]
        y = 500
        for name, label, color in labels:
            rect = pygame.Rect(0, 0, 420, 68)
            rect.center = (W // 2, y)
            b = widgets.Button(rect, label, color=color, size=30)
            b.draw(self.screen)
            btns.append((name, rect))
            y += 90
        self.buttons["menu"] = btns
        theme.draw_text(self.screen, "© Roberto Di Flumeri", 16, theme.GREY, center=(W // 2, H - 20), bold=False)

    def _draw_howto(self):
        self.screen.fill(theme.CREAM)
        pygame.draw.rect(self.screen, theme.BLUE, TOP_BAR)
        theme.draw_text(self.screen, "Come funziona CodeBaby", 34, theme.WHITE, center=(W // 2, 42))
        panel = pygame.Rect(80, 120, W - 160, H - 220)
        widgets.drop_shadow(self.screen, panel)
        widgets.rounded_rect(self.screen, panel, theme.WHITE, radius=20, border=3, border_color=theme.BLUE)
        lines = [
            "CodeBaby e' pensato per l'ultimo anello della scuola dell'infanzia e per la",
            "scuola primaria, da usare in classe con la guida della maestra o del maestro.",
            "",
            "Il bambino costruisce un piccolo 'programma' scegliendo dei blocchi comando",
            "(Avanti, Gira, Ripeti) per far muovere il robottino CodeBaby fino alla stella.",
            "",
            "15 livelli introducono, uno alla volta, i concetti dell'informatica:",
            "SEQUENZA, ROTAZIONE, CICLO, VARIABILI, ARRAY, STRUTTURE e FUNZIONI.",
            "",
            "Se il robot sbatte contro un ostacolo torna semplicemente alla partenza:",
            "sbagliare non e' un problema, e' parte del gioco! Si puo' sempre riprovare.",
            "",
            "Consiglio per la classe: leggete insieme ad alta voce i blocchi scelti",
            "PRIMA di premere Gioca, provando ad indovinare cosa succedera'.",
        ]
        y = panel.top + 30
        for line in lines:
            if line:
                theme.draw_text(self.screen, line, 22, theme.INK, topleft=(panel.left + 30, y), bold=False)
            y += 30
        rect = pygame.Rect(0, 0, 220, 60)
        rect.center = (W // 2, panel.bottom - 40)
        b = widgets.Button(rect, "Indietro", color=theme.RED, size=26)
        b.draw(self.screen)
        self.buttons["howto"] = [("back", rect)]

    def _draw_map(self):
        self.screen.fill(theme.CREAM)
        pygame.draw.rect(self.screen, theme.RED, TOP_BAR)
        theme.draw_text(self.screen, "Scegli un livello", 34, theme.WHITE, center=(W // 2, 42))
        # path dotted line
        pts = [n["pos"] for n in self.map_nodes]
        for i in range(len(pts) - 1):
            _dotted_line(self.screen, pts[i], pts[i + 1], theme.GREY)
        for node in self.map_nodes:
            lid = node["id"]
            unlocked = lid <= self.save["unlocked"]
            stars = self.save["stars"].get(str(lid), 0)
            level = levels_mod.LEVELS_BY_ID[lid]
            r = 46
            rect = pygame.Rect(0, 0, r * 2, r * 2)
            rect.center = node["pos"]
            node["rect"] = rect
            widgets.drop_shadow(self.screen, rect, radius=r, offset=(0, 4))
            color = theme.BLUE if unlocked else theme.GREY
            if unlocked and stars > 0:
                color = theme.GOLD
            pygame.draw.circle(self.screen, color, rect.center, r)
            pygame.draw.circle(self.screen, theme.INK, rect.center, r, width=3)
            if unlocked:
                theme.draw_text(self.screen, str(lid), 30, theme.WHITE, center=rect.center)
                if stars > 0:
                    for i in range(3):
                        sc = (rect.centerx - 26 + i * 26, rect.centery + r + 16)
                        widgets.draw_star(self.screen, sc, 10, theme.GOLD if i < stars else theme.GREY_LIGHT, width=2)
            else:
                widgets.draw_padlock(self.screen, rect.center, r * 1.1, theme.WHITE)
            title_y = max(rect.top - 22, TOP_BAR.bottom + 18)
            theme.draw_text(self.screen, level["title"], 15, theme.INK, center=(rect.centerx, title_y), bold=True)
        rect = pygame.Rect(20, 20, 150, 44)
        b = widgets.Button(rect, "Menu", color=theme.INK, size=20)
        b.draw(self.screen)
        self.buttons["map"] = [("back", rect)]

    def _draw_level(self):
        s = self.session
        self.screen.fill(theme.CREAM)
        pygame.draw.rect(self.screen, theme.BLUE, TOP_BAR)
        theme.draw_text(self.screen, f"Livello {s.level['id']} - {s.level['title']}", 26, theme.WHITE,
                         topleft=(20, 12))
        theme.draw_text(self.screen, s.level["concept"], 16, theme.GOLD, topleft=(20, 48), bold=True)
        b = widgets.Button(BACK_RECT, "Mappa", color=theme.RED, size=22)
        b.draw(self.screen)

        self._draw_toolbox()
        self._draw_world()
        self._draw_program()
        self._draw_controls()

        if s.status == "intro":
            self._draw_intro_overlay()
        elif s.status == "win":
            self._draw_win_overlay()

        if self.modal is not None:
            self._draw_modal()

    def _draw_toolbox(self):
        s = self.session
        widgets.rounded_rect(self.screen, TOOLBOX_RECT, theme.WHITE, radius=16, border=3, border_color=theme.BLUE)
        enabled = s.status == "build"
        defining = s.defining_function
        fn_name = s.level.get("function_name", "FUNZIONE")
        if defining:
            header = f"Definisci {fn_name}"
            actions = ["fwd", "left", "right"]
            show_fn_slot = False
        else:
            header = "Blocchi"
            actions = [a for a in ACTION_ORDER if a in s.level["unlock"]]
            show_fn_slot = bool(s.level.get("allow_function"))
        theme.draw_text(self.screen, header, 20, theme.INK, center=(TOOLBOX_RECT.centerx, TOOLBOX_RECT.top + 24))

        n_rows = len(actions) + (1 if show_fn_slot else 0)
        avail_h = (TOOLBOX_RECT.bottom - 10) - (TOOLBOX_RECT.top + 50) - 3 * 19 - 10
        row_h = 92 if n_rows <= 4 else max(50, avail_h / max(1, n_rows))
        btn_h = row_h - 14
        icon_size = min(70, btn_h * 0.9)
        label_size = 18 if row_h >= 80 else (16 if row_h >= 64 else 14)

        btns = []
        y = TOOLBOX_RECT.top + 50
        for action in actions:
            rect = pygame.Rect(TOOLBOX_RECT.left + 12, y, TOOLBOX_RECT.width - 24, btn_h)
            col = ACTION_COLOR[action]
            drawcol = col if enabled else theme.GREY
            widgets.rounded_rect(self.screen, rect, drawcol, radius=14, border=3, border_color=theme.INK)
            icon_c = (rect.left + 34, rect.centery)
            if action == "fwd":
                icons.arrow_up(self.screen, icon_c, icon_size, theme.WHITE)
            elif action == "left":
                icons.turn_arrow(self.screen, icon_c, icon_size, theme.WHITE, clockwise=False)
            elif action == "right":
                icons.turn_arrow(self.screen, icon_c, icon_size, theme.WHITE, clockwise=True)
            elif action == "repeat":
                icons.repeat_icon(self.screen, icon_c, icon_size, theme.WHITE)
            elif action == "grab":
                icons.gem_icon(self.screen, icon_c, icon_size, theme.WHITE)
            theme.draw_text(self.screen, ACTION_LABEL[action], label_size, theme.WHITE,
                             topleft=(rect.left + 60, rect.centery - label_size // 2 - 2))
            btns.append((action, rect))
            y += row_h

        if show_fn_slot:
            defined = bool(s.function_body)
            label = f"Richiama {fn_name}" if defined else f"Definisci {fn_name}"
            rect = pygame.Rect(TOOLBOX_RECT.left + 12, y, TOOLBOX_RECT.width - 24, btn_h)
            col = theme.TEAL if enabled else theme.GREY
            widgets.rounded_rect(self.screen, rect, col, radius=14, border=3, border_color=theme.INK)
            icons.function_icon(self.screen, (rect.left + 34, rect.centery), icon_size, theme.WHITE)
            theme.draw_text(self.screen, label, label_size, theme.WHITE,
                             topleft=(rect.left + 60, rect.centery - label_size // 2 - 2))
            btns.append((FUNCTION_DEFINE_KEY if not defined else "call", rect))
            y += row_h

        self.buttons["toolbox"] = btns if enabled else []
        tip_y = y + 10
        if defining:
            tip_lines = ("Costruisci la funzione,", "poi tocca Fatto!", "qui sotto.")
        else:
            tip_lines = ("Tocca un blocco", "del programma", "per toglierlo.")
        for line in tip_lines:
            theme.draw_text(self.screen, line, 14, theme.GREY, topleft=(TOOLBOX_RECT.left + 12, tip_y), bold=False)
            tip_y += 19

    def _draw_world(self):
        s = self.session
        widgets.rounded_rect(self.screen, WORLD_RECT, theme.SKY, radius=16, border=3, border_color=theme.BLUE_DARK)
        world = s.world
        margin = 16
        avail_w = WORLD_RECT.width - margin * 2
        avail_h = WORLD_RECT.height - margin * 2
        cell = min(avail_w / world.cols, avail_h / world.rows)
        gw, gh = cell * world.cols, cell * world.rows
        ox = WORLD_RECT.left + (WORLD_RECT.width - gw) / 2
        oy = WORLD_RECT.top + (WORLD_RECT.height - gh) / 2

        def cell_center(cx, cy):
            return (ox + (cx + 0.5) * cell, oy + (cy + 0.5) * cell)

        for gx in range(world.cols):
            for gy in range(world.rows):
                r = pygame.Rect(ox + gx * cell, oy + gy * cell, cell, cell)
                pygame.draw.rect(self.screen, (255, 255, 255, 40), r, width=1)
                widgets.rounded_rect(self.screen, r.inflate(-4, -4), (255, 255, 255) if (gx + gy) % 2 == 0 else (240, 250, 255), radius=6)

        for (wx, wy) in world.walls:
            c = cell_center(wx, wy)
            for dx, dy, rr in [(-cell * 0.14, cell * 0.08, cell * 0.28), (cell * 0.14, cell * 0.05, cell * 0.26),
                                (0, -cell * 0.14, cell * 0.24)]:
                pygame.draw.circle(self.screen, theme.GREY, (c[0] + dx, c[1] + dy), rr)
            pygame.draw.circle(self.screen, theme.INK, c, cell * 0.34, width=2)

        for d in world.doors:
            c = cell_center(*d["pos"])
            col = KEY_COLOR.get(d["color"], theme.INK)
            locked = d["color"] not in world.collected_keys
            r = pygame.Rect(0, 0, cell * 0.74, cell * 0.74)
            r.center = c
            if locked:
                widgets.rounded_rect(self.screen, r, col, radius=8, border=3, border_color=theme.INK)
                widgets.draw_padlock(self.screen, c, cell * 0.4, theme.WHITE)
            else:
                widgets.rounded_rect(self.screen, r, theme.WHITE, radius=8, border=3, border_color=col)

        for k in world.keys:
            if k["color"] in world.collected_keys:
                continue
            c = cell_center(*k["pos"])
            icons.key_icon(self.screen, c, cell * 0.75, KEY_COLOR.get(k["color"], theme.INK))

        for i, gpos in enumerate(world.gems):
            if i in world.collected_gems:
                continue
            c = cell_center(*gpos)
            gem_col = theme.BLUE if world.gems_ordered else theme.GREEN
            icons.gem_icon(self.screen, c, cell * 0.55, gem_col)
            if world.gems_ordered:
                theme.draw_text(self.screen, str(i + 1), 15, theme.WHITE, center=c, bold=True)

        if world.gems:
            badge = pygame.Rect(0, 0, 96, 34)
            badge.topright = (WORLD_RECT.right - 10, WORLD_RECT.top + 10)
            widgets.rounded_rect(self.screen, badge, theme.WHITE, radius=16, border=2, border_color=theme.GOLD_DARK)
            icons.gem_icon(self.screen, (badge.left + 20, badge.centery), 24, theme.GOLD_DARK)
            count_text = f"{len(world.collected_gems)}/{len(world.gems)}"
            theme.draw_text(self.screen, count_text, 16, theme.INK, topleft=(badge.left + 34, badge.centery - 11),
                             bold=True)

        gcx, gcy = cell_center(*world.goal)
        pulse = 1 + 0.08 * __import__("math").sin(self.t * 3)
        widgets.draw_star(self.screen, (gcx, gcy), cell * 0.32 * pulse, theme.GOLD)

        px, py = s.anim_pos
        center = (ox + (px + 0.5) * cell, oy + (py + 0.5) * cell)
        robot_mod.draw_robot(self.screen, center, cell * 0.92, facing=round(s.anim_facing) % 4,
                              mood=s.mood, t=self.t, bump=s.bump)

        if s.status in ("crash", "miss"):
            bubble = pygame.Rect(0, 0, 340, 60)
            bubble.midbottom = (center[0], center[1] - cell * 0.7)
            bubble.clamp_ip(WORLD_RECT)
            widgets.rounded_rect(self.screen, bubble, theme.WHITE, radius=14, border=2, border_color=theme.RED)
            theme.draw_text(self.screen, s.message, 15, theme.RED, center=bubble.center, bold=True)

    def _draw_program(self):
        s = self.session
        widgets.rounded_rect(self.screen, PROGRAM_RECT, theme.WHITE, radius=16, border=3, border_color=theme.ORANGE)
        fn_name = s.level.get("function_name", "FUNZIONE")
        if s.defining_function:
            theme.draw_text(self.screen, f"La tua funzione: {fn_name}", 18, theme.INK,
                             topleft=(PROGRAM_RECT.left + 16, PROGRAM_RECT.top + 10))
            items = [(a, ACTION_LABEL[a]) for a in s.function_body]
            empty_msg = "Tocca i blocchi qui a sinistra per costruire la funzione!"
        else:
            theme.draw_text(self.screen, "Il tuo programma", 18, theme.INK,
                             topleft=(PROGRAM_RECT.left + 16, PROGRAM_RECT.top + 10))
            items = [(blk.action, blk.label(fn_name)) for blk in s.blocks]
            empty_msg = "Tocca i blocchi qui a sinistra per costruire il programma!"

        area = pygame.Rect(PROGRAM_RECT.left + 14, PROGRAM_RECT.top + 40, PROGRAM_RECT.width - 28,
                            PROGRAM_RECT.height - 54)
        x, y = area.left, area.top
        row_h = 46
        chips = []
        running_idx = getattr(s, "current_block", -1) if (s.status == "running" and not s.defining_function) else -1
        for idx, (action, label) in enumerate(items):
            f = theme.font(16, True)
            w = f.size(label)[0] + 46
            if x + w > area.right:
                x = area.left
                y += row_h + 8
            rect = pygame.Rect(x, y, w, row_h)
            col = ACTION_COLOR[action]
            widgets.rounded_rect(self.screen, rect, col, radius=12, border=2, border_color=theme.INK)
            if idx == running_idx:
                pygame.draw.rect(self.screen, theme.GOLD, rect.inflate(6, 6), width=3, border_radius=14)
            ic = (rect.left + 24, rect.centery)
            if action == "fwd":
                icons.arrow_up(self.screen, ic, 36, theme.WHITE)
            elif action == "left":
                icons.turn_arrow(self.screen, ic, 36, theme.WHITE, clockwise=False)
            elif action == "right":
                icons.turn_arrow(self.screen, ic, 36, theme.WHITE, clockwise=True)
            elif action == "grab":
                icons.gem_icon(self.screen, ic, 36, theme.WHITE)
            elif action == "call":
                icons.function_icon(self.screen, ic, 36, theme.WHITE)
            theme.draw_text(self.screen, label, 16, theme.WHITE, topleft=(rect.left + 42, rect.centery - 11))
            chips.append((idx, rect))
            x += w + 8
        if not items:
            theme.draw_text(self.screen, empty_msg, 16, theme.GREY, topleft=(area.left, area.top), bold=False)
        self.buttons["program"] = chips if s.status == "build" else []

    def _draw_controls(self):
        s = self.session
        widgets.rounded_rect(self.screen, CONTROLS_RECT, theme.WHITE, radius=16, border=3, border_color=theme.GREEN)
        if s.defining_function:
            can_done = s.status == "build" and len(s.function_body) > 0
            done_rect = pygame.Rect(CONTROLS_RECT.left + 14, CONTROLS_RECT.top + 14, CONTROLS_RECT.width - 28, 54)
            clear_rect = pygame.Rect(CONTROLS_RECT.left + 14, done_rect.bottom + 12, CONTROLS_RECT.width - 28, 54)
            widgets.Button(done_rect, "Fatto!", color=theme.TEAL, size=24, enabled=can_done).draw(self.screen)
            widgets.Button(clear_rect, "Pulisci", color=theme.ORANGE, size=22, enabled=s.status == "build").draw(self.screen)
            self.buttons["controls"] = [("play", done_rect), ("clear", clear_rect)] if s.status == "build" else []
            return
        can_play = s.status == "build" and len(s.blocks) > 0
        play_rect = pygame.Rect(CONTROLS_RECT.left + 14, CONTROLS_RECT.top + 14, CONTROLS_RECT.width - 28, 54)
        clear_rect = pygame.Rect(CONTROLS_RECT.left + 14, play_rect.bottom + 12, CONTROLS_RECT.width - 28, 54)
        bp = widgets.Button(play_rect, "Gioca!", color=theme.GREEN, size=24, enabled=can_play)
        bp.draw(self.screen)
        bc = widgets.Button(clear_rect, "Pulisci", color=theme.ORANGE, size=22, enabled=s.status == "build")
        bc.draw(self.screen)
        self.buttons["controls"] = [("play", play_rect), ("clear", clear_rect)] if s.status == "build" else []

    def _draw_intro_overlay(self):
        s = self.session
        overlay = pygame.Surface((W, H), pygame.SRCALPHA)
        overlay.fill((20, 15, 30, 190))
        self.screen.blit(overlay, (0, 0))
        panel = pygame.Rect(0, 0, 780, 420)
        panel.center = (W // 2, H // 2)
        widgets.drop_shadow(self.screen, panel)
        widgets.rounded_rect(self.screen, panel, theme.WHITE, radius=22, border=4, border_color=theme.BLUE)
        robot_mod.draw_robot(self.screen, (panel.left + 110, panel.centery - 30), 150, facing=None,
                              mood="happy", t=self.t)
        theme.draw_text(self.screen, s.level["concept"], 22, theme.RED, topleft=(panel.left + 210, panel.top + 26),
                         bold=True)
        theme.draw_text(self.screen, s.level["title"], 30, theme.BLUE, topleft=(panel.left + 210, panel.top + 54))
        lines = widgets.wrap_text(s.level["teach"], 19, panel.width - 240)
        y = panel.top + 110
        for line in lines:
            theme.draw_text(self.screen, line, 19, theme.INK, topleft=(panel.left + 210, y), bold=False)
            y += 26
        y += 14
        hint_lines = widgets.wrap_text("Indizio: " + s.level["hint"], 17, panel.width - 240)
        for line in hint_lines:
            theme.draw_text(self.screen, line, 17, theme.ORANGE, topleft=(panel.left + 210, y), bold=True)
            y += 23
        rect = pygame.Rect(0, 0, 200, 56)
        rect.center = (panel.centerx, panel.bottom - 40)
        b = widgets.Button(rect, "Inizia!", color=theme.GREEN, size=24)
        b.draw(self.screen)

    def _draw_win_overlay(self):
        s = self.session
        if s.confetti:
            s.confetti.draw(self.screen)
        panel = pygame.Rect(0, 0, 560, 380)
        panel.center = (W // 2, H // 2)
        widgets.drop_shadow(self.screen, panel)
        widgets.rounded_rect(self.screen, panel, theme.WHITE, radius=22, border=4, border_color=theme.GOLD)
        robot_mod.draw_robot(self.screen, (panel.centerx, panel.top + 90), 120, facing=None, mood="happy", t=self.t)
        theme.draw_text(self.screen, "Bravo/a!", 40, theme.RED, center=(panel.centerx, panel.top + 175),
                         outline=theme.GOLD, outline_size=2)
        for i in range(3):
            c = (panel.centerx - 60 + i * 60, panel.top + 220)
            widgets.draw_star(self.screen, c, 26, theme.GOLD if i < s.stars else theme.GREY_LIGHT)
        has_next = (s.level["id"] + 1) in levels_mod.LEVELS_BY_ID
        btns = []
        y = panel.top + 280
        if has_next:
            rect = pygame.Rect(0, 0, 230, 54)
            rect.center = (panel.centerx, y)
            widgets.Button(rect, "Prossimo Livello!", color=theme.GREEN, size=20).draw(self.screen)
            btns.append(("next", rect))
            y += 62
        rect2 = pygame.Rect(0, 0, 230, 50)
        rect2.center = (panel.centerx, y)
        widgets.Button(rect2, "Mappa dei Livelli", color=theme.BLUE, size=19).draw(self.screen)
        btns.append(("map", rect2))
        self.buttons["win"] = btns

    def _draw_modal(self):
        m = self.modal
        overlay = pygame.Surface((W, H), pygame.SRCALPHA)
        overlay.fill((20, 15, 30, 170))
        self.screen.blit(overlay, (0, 0))
        panel = pygame.Rect(0, 0, 520, 320)
        panel.center = (W // 2, H // 2)
        widgets.drop_shadow(self.screen, panel)
        widgets.rounded_rect(self.screen, panel, theme.WHITE, radius=20, border=4, border_color=theme.PURPLE)
        theme.draw_text(self.screen, "Blocco RIPETI", 26, theme.PURPLE, center=(panel.centerx, panel.top + 34))
        theme.draw_text(self.screen, "Quale comando vuoi ripetere?", 17, theme.INK,
                         center=(panel.centerx, panel.top + 68), bold=False)

        btns = []
        actions = [a for a in ("fwd", "left", "right") if a in self.session.level["unlock"]]
        bx = panel.left + 40
        for a in actions:
            rect = pygame.Rect(bx, panel.top + 90, 130, 66)
            selected = m["action"] == a
            col = ACTION_COLOR[a]
            widgets.rounded_rect(self.screen, rect, col, radius=14, border=4 if selected else 2,
                                  border_color=theme.INK if selected else theme.GREY)
            ic = (rect.centerx, rect.top + 22)
            if a == "fwd":
                icons.arrow_up(self.screen, ic, 44, theme.WHITE)
            elif a == "left":
                icons.turn_arrow(self.screen, ic, 44, theme.WHITE, clockwise=False)
            else:
                icons.turn_arrow(self.screen, ic, 44, theme.WHITE, clockwise=True)
            theme.draw_text(self.screen, ACTION_LABEL[a], 13, theme.WHITE, center=(rect.centerx, rect.bottom - 12))
            btns.append((f"act:{a}", rect))
            bx += 150

        theme.draw_text(self.screen, "Quante volte?", 17, theme.INK, center=(panel.centerx, panel.top + 190),
                         bold=False)
        minus = pygame.Rect(panel.centerx - 90, panel.top + 205, 50, 50)
        plus = pygame.Rect(panel.centerx + 40, panel.top + 205, 50, 50)
        widgets.Button(minus, "-", color=theme.RED, size=26).draw(self.screen)
        widgets.Button(plus, "+", color=theme.GREEN, size=26).draw(self.screen)
        theme.draw_text(self.screen, str(m["count"]), 30, theme.INK, center=(panel.centerx, panel.top + 230))
        btns.append(("minus", minus))
        btns.append(("plus", plus))

        add_rect = pygame.Rect(panel.left + 60, panel.bottom - 60, 170, 46)
        cancel_rect = pygame.Rect(panel.right - 230, panel.bottom - 60, 170, 46)
        widgets.Button(add_rect, "Aggiungi", color=theme.GREEN, size=20).draw(self.screen)
        widgets.Button(cancel_rect, "Annulla", color=theme.GREY, size=20).draw(self.screen)
        btns.append(("add", add_rect))
        btns.append(("cancel", cancel_rect))
        self.buttons["modal"] = btns

    # ---- tour screenshot (per collaudo senza schermo reale) ------------ #
    def _run_screenshot_tour(self):
        os.makedirs(self.screenshot_dir, exist_ok=True)

        def snap(name, ticks=1):
            for _ in range(ticks):
                self.update(1 / 60)
            self.draw()
            pygame.display.flip()
            pygame.image.save(self.screen, os.path.join(self.screenshot_dir, name))

        self.state = "splash"
        snap("01_splash.png")
        self.state = "menu"
        snap("02_menu.png")
        self.state = "howto"
        snap("03_howto.png")
        self.state = "map"
        self.save["unlocked"] = 6
        self.save["stars"] = {"1": 3, "2": 2, "3": 3, "4": 1, "5": 3}
        snap("04_map.png")

        self.enter_level(1)
        snap("05_level_intro.png")
        self.session.start_build()
        self.session.add_block("fwd")
        self.session.add_block("fwd")
        self.session.add_block("fwd")
        snap("06_level_build.png")
        self.session.play()
        snap("07_level_running.png", ticks=10)
        snap("08_level_running2.png", ticks=20)
        snap("09_level_win.png", ticks=60)

        self.enter_level(7)
        self.session.start_build()
        snap("10_level_obstacles.png")

        self.enter_level(5)
        self.session.start_build()
        self.modal = {"action": "fwd", "count": 6}
        snap("11_level_modal.png")

        self.modal = None
        self.enter_level(9)
        self.session.start_build()
        snap("12_level_maze.png")

        # livello 11 - variabile (gemme)
        self.enter_level(11)
        self.session.start_build()
        for action in ("fwd", "fwd", "grab", "fwd", "fwd", "grab", "fwd", "fwd"):
            self.session.add_block(action)
        snap("13_level_gems_build.png")
        self.session.play()
        snap("14_level_gems_running.png", ticks=40)
        snap("15_level_gems_win.png", ticks=200)

        # livello 12 - array (ordine sbagliato apposta, per mostrare l'errore didattico)
        self.enter_level(12)
        self.session.start_build()
        for action in ("fwd", "fwd", "fwd", "fwd", "grab"):
            self.session.add_block(action)
        snap("16_level_array_build.png")
        self.session.play()
        snap("17_level_array_order_error.png", ticks=60)

        # livello 13 - strutture dati (chiavi e porte)
        self.enter_level(13)
        self.session.start_build()
        snap("18_level_keys_build.png")
        for action in ("fwd", "grab", "fwd", "fwd", "fwd", "grab", "fwd", "fwd"):
            self.session.add_block(action)
        self.session.play()
        snap("19_level_keys_running.png", ticks=90)

        # livello 14 - funzioni (definizione + richiamo)
        self.enter_level(14)
        self.session.start_build()
        self.session.defining_function = True
        for action in ("fwd", "fwd", "right"):
            self.session.add_function_step(action)
        snap("20_level_function_define.png")
        self.session.defining_function = False
        for _ in range(3):
            self.session.add_block("call")
        snap("21_level_function_call.png")
        self.session.play()
        snap("22_level_function_win.png", ticks=200)

        # livello 15 - missione finale 2 (tutto insieme)
        self.enter_level(15)
        self.session.start_build()
        snap("23_level_capstone.png")

        print("Screenshot salvati in", self.screenshot_dir)


def _build_map_nodes():
    """Tre file di 5 nodi a serpentina, sempre con lo stesso spazio verticale
    tra una fila e l'altra: cosi' il titolo (sempre sopra al nodo) non rischia
    mai di sconfinare, qualunque sia il numero di livelli."""
    xs_lr = [130, 320, 510, 700, 890]
    xs_rl = list(reversed(xs_lr))
    rows = [(190, xs_lr), (430, xs_rl), (670, xs_lr)]
    nodes = []
    i = 0
    for y, xs in rows:
        for x in xs:
            if i >= len(levels_mod.LEVELS):
                return nodes
            lv = levels_mod.LEVELS[i]
            nodes.append({"id": lv["id"], "pos": (x, y), "rect": None})
            i += 1
    return nodes


def _dotted_line(surface, p1, p2, color, gap=14):
    import math as _m
    x1, y1 = p1
    x2, y2 = p2
    dist = _m.hypot(x2 - x1, y2 - y1)
    steps = max(1, int(dist // gap))
    for i in range(steps):
        t0 = i / steps
        t1 = (i + 0.5) / steps
        sx = x1 + (x2 - x1) * t0
        sy = y1 + (y2 - y1) * t0
        ex = x1 + (x2 - x1) * t1
        ey = y1 + (y2 - y1) * t1
        pygame.draw.line(surface, color, (sx, sy), (ex, ey), 4)


def main():
    screenshot_dir = os.environ.get("CODEBABY_SCREENSHOTS")
    if screenshot_dir:
        os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
    app = App(screenshot_dir=screenshot_dir)
    app.run()


if __name__ == "__main__":
    main()

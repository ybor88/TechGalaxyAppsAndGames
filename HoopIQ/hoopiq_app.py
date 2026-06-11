"""
HoopIQ - Scout. Analyze. Elevate.
Basketball Intelligence Desktop Application
"""

import tkinter as tk
from tkinter import ttk, messagebox
import json
import os
import math
from datetime import datetime
import matplotlib
matplotlib.use("TkAgg")
import matplotlib.pyplot as plt
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from matplotlib.figure import Figure
import numpy as np

try:
    from PIL import Image, ImageTk
    PIL_AVAILABLE = True
except ImportError:
    PIL_AVAILABLE = False

# ══════════════════════════════════════════════════════════════
#  COLORI — ispirato al logo NBA/FIBA: nero, rosso, blu, oro
# ══════════════════════════════════════════════════════════════
BG_DARK      = "#080808"
BG_CARD      = "#111111"
BG_PANEL     = "#181818"
BG_INPUT     = "#1c1c1c"
ACCENT_RED   = "#c8102e"   # NBA red
ACCENT_BLU   = "#1d428a"   # NBA blue
ACCENT_GLD   = "#f5a623"   # gold/FIBA
ACCENT_PINK  = "#d81b60"   # women
TEXT_WHITE   = "#ffffff"
TEXT_GRAY    = "#aaaaaa"
TEXT_DIM     = "#555555"
BORDER       = "#252525"
BTN_HOVER_R  = "#e8172f"
BTN_HOVER_B  = "#2554a8"
BTN_HOVER_G  = "#e0961f"
BTN_HOVER_P  = "#f02070"

# ══════════════════════════════════════════════════════════════
#  FILE DI DATI SEPARATI (nessun DB)
# ══════════════════════════════════════════════════════════════
BASE_DIR        = os.path.dirname(os.path.abspath(__file__))
FILE_MAN        = os.path.join(BASE_DIR, "players_man.json")
FILE_WOMEN      = os.path.join(BASE_DIR, "players_women.json")
FILE_SCOUTING   = os.path.join(BASE_DIR, "scouting.json")

def _read_json(path, default):
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return default

def _write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def load_man():      return _read_json(FILE_MAN,      [])
def load_women():    return _read_json(FILE_WOMEN,    [])
def load_scouting(): return _read_json(FILE_SCOUTING, [])

def save_man(data):      _write_json(FILE_MAN,      data)
def save_women(data):    _write_json(FILE_WOMEN,    data)
def save_scouting(data): _write_json(FILE_SCOUTING, data)

# ══════════════════════════════════════════════════════════════
#  TABELLE BONUS
# ══════════════════════════════════════════════════════════════
MAN_BONUSES = {
    "NBA PLAYOFF":        30,
    "NBA":                20,
    "EUROLEAGUE":         10,
    "FIBA NAZIONI":       10,
    "EUROCUP":             5,
    "FIBA EUROCUP":        3,
    "ALTRE":               2,
    "FIBA CLUB":           1,
    "FIBA CLUB 2":         0,
    "FIBA NAZIONI QUALIF": 0,
}
WOMEN_BONUSES = {
    "WNBA PLAYOFF":         30,
    "WNBA":                 20,
    "FIBA NAZIONI":         20,
    "EUROLEAGUE":           10,
    "FIBA NAZIONI QUALIF":  10,
    "EUROCUP":               5,
    "FIBA EUROCUP":          3,
    "ALTRE":                 2,
    "FIBA CLUB":             1,
    "FIBA CLUB 2":           0,
    "FIBA NAZIONI YOUNG":    2,
    "NCAA":                 20,
    "ANGXT":                10,
}

# Scouting reference values
SCOUT_REF = {
    "Minuti":        30,
    "P. Rubate":      1.0,
    "Stoppate":       1.0,
    "Rimbalzi":       6.0,
    "Punti":         15.0,
    "Tiro 2pt %":    50.0,
    "Tiro 3pt %":    37.0,
    "Tiro Libero %": 80.0,
}

# ══════════════════════════════════════════════════════════════
#  HELPERS
# ══════════════════════════════════════════════════════════════
def compute_score(player: dict) -> int:
    return (int(player.get("max_season", 0))
            + int(player.get("max_career", 0))
            + int(player.get("bonus", 0)))

def compute_scout_score(stats: dict) -> float:
    weights = {
        "Minuti":        0.10,
        "P. Rubate":     0.10,
        "Stoppate":      0.10,
        "Rimbalzi":      0.15,
        "Punti":         0.20,
        "Tiro 2pt %":    0.15,
        "Tiro 3pt %":    0.10,
        "Tiro Libero %": 0.10,
    }
    score = 0.0
    for key, ref in SCOUT_REF.items():
        try:
            val = float(stats.get(key, 0))
        except (ValueError, TypeError):
            val = 0.0
        ratio = (val / ref) if ref > 0 else 0
        score += min(ratio, 2.0) * weights[key] * 100
    return min(round(score, 1), 100)


# ══════════════════════════════════════════════════════════════
#  WIDGET HELPERS
# ══════════════════════════════════════════════════════════════
def sep(parent, color=BORDER, **kw):
    return tk.Frame(parent, bg=color, height=1, **kw)

def lbl(parent, text, size=10, color=TEXT_WHITE, bold=False, bg=BG_CARD, **kw):
    return tk.Label(parent, text=text, fg=color, bg=bg,
                    font=("Segoe UI", size, "bold" if bold else "normal"), **kw)

def entry(parent, var, width=28):
    e = tk.Entry(parent, textvariable=var, width=width,
                 bg=BG_INPUT, fg=TEXT_WHITE, insertbackground=TEXT_WHITE,
                 relief="flat", font=("Segoe UI", 10),
                 highlightbackground="#333", highlightcolor=ACCENT_RED,
                 highlightthickness=1)
    e.bind("<FocusIn>",  lambda ev: e.configure(highlightbackground=ACCENT_RED))
    e.bind("<FocusOut>", lambda ev: e.configure(highlightbackground="#333"))
    return e


class HoopButton(tk.Frame):
    """Pulsante custom con hover, bordo sinistro colorato e stile NBA."""
    def __init__(self, parent, text, command,
                 bg_color=ACCENT_RED, hover_color=BTN_HOVER_R,
                 fg=TEXT_WHITE, width=200, height=36,
                 icon="", font_size=10, radius=6, **kw):
        # larghezza in pixel → approssima con caratteri (non usata direttamente)
        super().__init__(parent, bg=bg_color, cursor="hand2",
                         highlightthickness=0, bd=0)
        self._bg    = bg_color
        self._hover = hover_color
        self._cmd   = command

        label_text = f"{icon}  {text}" if icon else text

        # bordo sinistro accent (bianco semi-trasparente simulato)
        self._left = tk.Frame(self, bg=self._lighten(bg_color), width=4)
        self._left.pack(side="left", fill="y")

        self._lbl = tk.Label(self, text=label_text,
                             bg=bg_color, fg=fg,
                             font=("Segoe UI", font_size, "bold"),
                             pady=max(4, height // 8),
                             padx=12, cursor="hand2")
        self._lbl.pack(side="left", fill="both", expand=True)

        for w in (self, self._lbl, self._left):
            w.bind("<Enter>",           self._on_enter)
            w.bind("<Leave>",           self._on_leave)
            w.bind("<ButtonPress-1>",   self._on_press)
            w.bind("<ButtonRelease-1>", self._on_release)

    @staticmethod
    def _lighten(hex_color):
        """Schiarisce leggermente un colore hex."""
        try:
            r = min(255, int(hex_color[1:3], 16) + 40)
            g = min(255, int(hex_color[3:5], 16) + 40)
            b = min(255, int(hex_color[5:7], 16) + 40)
            return f"#{r:02x}{g:02x}{b:02x}"
        except Exception:
            return hex_color

    def _set_color(self, color):
        self.configure(bg=color)
        self._lbl.configure(bg=color)

    def _on_enter(self, _):  self._set_color(self._hover)
    def _on_leave(self, _):  self._set_color(self._bg)
    def _on_press(self, _):  self._set_color("#000000")
    def _on_release(self, _):
        self._set_color(self._hover)
        if self._cmd:
            self._cmd()


# ══════════════════════════════════════════════════════════════
#  STILE SCROLLBAR & TREEVIEW
# ══════════════════════════════════════════════════════════════
def _apply_styles():
    s = ttk.Style()
    s.theme_use("default")
    # Scrollbar verticale rossa
    s.configure("Dark.Vertical.TScrollbar",
                background=ACCENT_RED, troughcolor=BG_PANEL,
                arrowcolor=TEXT_WHITE, bordercolor=BG_DARK,
                darkcolor=ACCENT_RED, lightcolor=BTN_HOVER_R,
                relief="flat", width=10)
    s.map("Dark.Vertical.TScrollbar",
          background=[("active", BTN_HOVER_R), ("pressed", "#900020")])
    # Treeview base
    s.configure("Treeview",
                background=BG_CARD, foreground=TEXT_WHITE,
                fieldbackground=BG_CARD, rowheight=30,
                font=("Segoe UI", 10))
    s.configure("Treeview.Heading",
                background=ACCENT_BLU, foreground=TEXT_WHITE,
                font=("Segoe UI", 10, "bold"), relief="flat")
    s.map("Treeview",
          background=[("selected", ACCENT_RED)],
          foreground=[("selected", TEXT_WHITE)])


# ══════════════════════════════════════════════════════════════
#  SCROLLABLE FRAME
# ══════════════════════════════════════════════════════════════
class ScrollableFrame(tk.Frame):
    def __init__(self, parent, bg=BG_CARD, fixed_width=None, **kw):
        super().__init__(parent, bg=bg, **kw)
        if fixed_width:
            self.configure(width=fixed_width)
            self.pack_propagate(False)

        self._canvas = tk.Canvas(self, bg=bg, highlightthickness=0, borderwidth=0)
        self._sb = ttk.Scrollbar(self, orient="vertical",
                                 command=self._canvas.yview,
                                 style="Dark.Vertical.TScrollbar")
        self.inner = tk.Frame(self._canvas, bg=bg)

        self.inner.bind("<Configure>", self._update_scroll)
        self._win_id = self._canvas.create_window((0, 0), window=self.inner, anchor="nw")
        self._canvas.configure(yscrollcommand=self._sb.set)

        self._sb.pack(side="right", fill="y")
        self._canvas.pack(side="left", fill="both", expand=True)
        self._canvas.bind("<Configure>", self._on_canvas_resize)

        # Mousewheel only when mouse inside
        self._canvas.bind("<Enter>", lambda _: self._canvas.bind_all("<MouseWheel>", self._on_wheel))
        self._canvas.bind("<Leave>", lambda _: self._canvas.unbind_all("<MouseWheel>"))

    def _update_scroll(self, _=None):
        self._canvas.configure(scrollregion=self._canvas.bbox("all"))

    def _on_canvas_resize(self, event):
        self._canvas.itemconfig(self._win_id, width=event.width)

    def _on_wheel(self, event):
        self._canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")


# ══════════════════════════════════════════════════════════════
#  MAIN APP
# ══════════════════════════════════════════════════════════════
class HoopIQApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("HoopIQ — Scout. Analyze. Elevate.")
        self.geometry("1320x820")
        self.state("zoomed")  # avvia a schermo intero/massimizzato
        self.minsize(1100, 720)
        self.configure(bg=BG_DARK)
        self.resizable(True, True)
        _apply_styles()
        self._load_logo()
        self._build_ui()

    def _load_logo(self):
        self._logo_side = None
        # supporta logo.jpeg, logo.jpg e logo.png
        logo_path = next(
            (os.path.join(BASE_DIR, n) for n in ("logo.jpeg", "logo.jpg", "logo.png")
             if os.path.exists(os.path.join(BASE_DIR, n))),
            None
        )
        if not logo_path:
            return
        if PIL_AVAILABLE:
            try:
                img = Image.open(logo_path).convert("RGBA")
                # icona finestra 64x64
                icon = img.resize((64, 64), Image.LANCZOS)
                self._icon_tk = ImageTk.PhotoImage(icon)
                self.iconphoto(True, self._icon_tk)
                # sidebar: mantieni aspect ratio, larghezza 210px
                ow, oh = img.size
                target_w = 210
                target_h = int(oh * target_w / ow)
                side = img.resize((target_w, target_h), Image.LANCZOS)
                self._logo_side = ImageTk.PhotoImage(side)
                self._logo_side_h = target_h
            except Exception:
                pass

    def _build_ui(self):
        # Status bar fissa in fondo — va creata PRIMA del contenuto principale
        self._statusbar = tk.Frame(self, bg="#111111", height=30)
        self._statusbar.pack(side="bottom", fill="x")
        self._statusbar.pack_propagate(False)
        # striscia rossa sopra la status bar
        tk.Frame(self._statusbar, bg=ACCENT_RED, height=2).pack(fill="x", side="top")
        self._status_lbl = tk.Label(
            self._statusbar,
            text="HoopIQ  ·  Scout. Analyze. Elevate.  ·  DATA DRIVEN  ·  TALENT FOCUSED  ·  FUTURE READY",
            fg="#888888", bg="#111111",
            font=("Segoe UI", 8),
            anchor="center"
        )
        self._status_lbl.pack(fill="x", expand=True, pady=5)

        self._build_sidebar()
        # bordo inferiore rosso tra content e status bar
        tk.Frame(self, bg=ACCENT_RED, height=1).pack(side="bottom", fill="x")
        self.content = tk.Frame(self, bg=BG_DARK)
        self.content.pack(side="right", fill="both", expand=True)
        self.pages = {}
        for Cls in (PageRatingMan, PageRatingWomen, PageScouting, PageGlobal):
            p = Cls(self.content, self)
            self.pages[Cls.__name__] = p
            p.place(relx=0, rely=0, relwidth=1, relheight=1)
        self.show_page("PageRatingMan")

    def _build_sidebar(self):
        side = tk.Frame(self, bg=BG_PANEL, width=240)
        side.pack(side="left", fill="y")
        side.pack_propagate(False)

        # ── Logo con aspect ratio corretto ────────────────
        logo_area = tk.Frame(side, bg=BG_PANEL)
        logo_area.pack(fill="x", pady=(10, 0))
        if self._logo_side:
            tk.Label(logo_area, image=self._logo_side,
                     bg=BG_PANEL).pack(padx=15, pady=(6, 4))
        else:
            tk.Label(logo_area, text="HOOP", fg=TEXT_WHITE, bg=BG_PANEL,
                     font=("Segoe UI", 28, "bold")).pack()
            tk.Label(logo_area, text="IQ", fg=ACCENT_RED, bg=BG_PANEL,
                     font=("Segoe UI", 28, "bold")).pack()
            tk.Label(logo_area, text="SCOUT · ANALYZE · ELEVATE",
                     fg=TEXT_DIM, bg=BG_PANEL,
                     font=("Segoe UI", 7, "bold")).pack(pady=(0, 6))

        # ── Striscia rossa sottile (unica, pulita) ────────
        tk.Frame(side, bg=ACCENT_RED, height=2).pack(fill="x")

        # ── Nav ───────────────────────────────────────────
        self.nav_buttons = {}
        nav_items = [
            ("Player Rating  ♂",  "PageRatingMan",   ACCENT_RED,  BTN_HOVER_R),
            ("Player Rating  ♀",  "PageRatingWomen", ACCENT_PINK, BTN_HOVER_P),
            ("Player Scouting",   "PageScouting",    ACCENT_GLD,  BTN_HOVER_G),
            ("Global Coverage",   "PageGlobal",      ACCENT_BLU,  BTN_HOVER_B),
        ]
        nav_frame = tk.Frame(side, bg=BG_PANEL)
        nav_frame.pack(fill="x", pady=(8, 4))
        for label, page, col, hov in nav_items:
            self._nav_btn(nav_frame, label, page, col, hov)

        # ── Mini stats ────────────────────────────────────
        tk.Frame(side, bg="#222", height=1).pack(fill="x", padx=16)
        self._side_stats = tk.Frame(side, bg=BG_PANEL)
        self._side_stats.pack(fill="x", padx=14, pady=10)
        self._refresh_side_stats()

        # ── Footer ────────────────────────────────────────
        tk.Label(side, text=f"HoopIQ  v2.0  ·  © {datetime.now().year}",
                 fg=TEXT_DIM, bg=BG_PANEL,
                 font=("Segoe UI", 7)).pack(side="bottom", pady=8)

    def _nav_btn(self, parent, label, page, col, hov):
        frame = tk.Frame(parent, bg=BG_PANEL)
        frame.pack(fill="x", pady=1, padx=0)
        # barra colorata sinistra
        bar = tk.Frame(frame, bg="#333", width=3)
        bar.pack(side="left", fill="y")
        btn = tk.Button(frame, text=f"  {label}", anchor="w",
                        bg=BG_PANEL, fg="#cccccc",
                        activebackground="#222", activeforeground=TEXT_WHITE,
                        relief="flat", font=("Segoe UI", 10),
                        cursor="hand2", padx=14, pady=10, bd=0,
                        command=lambda p=page: self.show_page(p))
        btn.pack(side="left", fill="x", expand=True)

        def on_enter(_):
            if getattr(self, "_active_page", None) != page:
                btn.configure(fg=TEXT_WHITE, bg="#212121")
        def on_leave(_):
            if getattr(self, "_active_page", None) != page:
                btn.configure(fg="#cccccc", bg=BG_PANEL)
        btn.bind("<Enter>", on_enter)
        btn.bind("<Leave>", on_leave)
        self.nav_buttons[page] = (frame, btn, bar, col, hov)

    def show_page(self, name):
        self._active_page = name
        self.pages[name].lift()
        labels = {
            "PageRatingMan":   "Player Rating — Maschile  ·  NBA · FIBA · EUROLEAGUE",
            "PageRatingWomen": "Player Rating — Femminile  ·  WNBA · FIBA · EUROLEAGUE",
            "PageScouting":    "Player Scouting  ·  Analisi statistica radar /100",
            "PageGlobal":      "Global Coverage  ·  Distribuzione campionati per rating",
        }
        self._status_lbl.configure(
            text=f"  ●  {labels.get(name, 'HoopIQ')}  ·  DATA DRIVEN  ·  FUTURE READY"
        )
        for k, (frame, btn, bar, col, hov) in self.nav_buttons.items():
            if k == name:
                btn.configure(bg="#1e1e1e", fg=TEXT_WHITE,
                              font=("Segoe UI", 10, "bold"))
                bar.configure(bg=col)
            else:
                btn.configure(bg=BG_PANEL, fg="#cccccc",
                              font=("Segoe UI", 10))
                bar.configure(bg="#333")

    def _refresh_side_stats(self):
        for w in self._side_stats.winfo_children(): w.destroy()
        m = load_man(); wm = load_women()
        items = [
            ("Players ♂",  str(len(m)),   ACCENT_RED),
            ("Players ♀",  str(len(wm)),  ACCENT_PINK),
            ("Top Score ♂", str(max((compute_score(p) for p in m),  default=0)), TEXT_WHITE),
            ("Top Score ♀", str(max((compute_score(p) for p in wm), default=0)), TEXT_WHITE),
        ]
        for i, (lab, val, col) in enumerate(items):
            r, c = divmod(i, 2)
            card = tk.Frame(self._side_stats, bg="#141414", padx=10, pady=6)
            card.grid(row=r, column=c, padx=3, pady=3, sticky="nsew")
            tk.Label(card, text=val, fg=col, bg="#141414",
                     font=("Segoe UI", 17, "bold")).pack()
            tk.Label(card, text=lab, fg=TEXT_DIM, bg="#141414",
                     font=("Segoe UI", 7)).pack()

    def refresh_side(self):
        self._refresh_side_stats()


# ══════════════════════════════════════════════════════════════
#  BASE PAGE
# ══════════════════════════════════════════════════════════════
class BasePage(tk.Frame):
    def __init__(self, parent, app):
        super().__init__(parent, bg=BG_DARK)
        self.app = app

    def _header(self, title, subtitle="", accent=ACCENT_RED):
        hdr = tk.Frame(self, bg=BG_DARK, pady=14)
        hdr.pack(fill="x", padx=30)
        bar = tk.Frame(hdr, bg=accent, width=5)
        bar.pack(side="left", fill="y", padx=(0, 12))
        txt = tk.Frame(hdr, bg=BG_DARK)
        txt.pack(side="left")
        tk.Label(txt, text=title, fg=TEXT_WHITE, bg=BG_DARK,
                 font=("Segoe UI", 20, "bold")).pack(anchor="w")
        if subtitle:
            tk.Label(txt, text=subtitle, fg=TEXT_GRAY, bg=BG_DARK,
                     font=("Segoe UI", 9)).pack(anchor="w")
        sep(self).pack(fill="x", padx=30)


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (MAN)
# ══════════════════════════════════════════════════════════════
class PageRatingMan(BasePage):
    GENDER   = "man"
    LABEL    = "♂ NBA · FIBA · EUROLEAGUE"
    BONUSES  = MAN_BONUSES
    COLOR    = ACCENT_RED
    HOVER    = BTN_HOVER_R

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def _load(self): return load_man()
    def _save(self, d): save_man(d)

    def _build(self):
        self._header("Player Rating — Maschile", self.LABEL, self.COLOR)
        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=8)

        # LEFT scrollable form
        sf = ScrollableFrame(body, bg=BG_CARD, fixed_width=285)
        sf.pack(side="left", fill="y", padx=(0, 12))
        fc = tk.Frame(sf.inner, bg=BG_CARD, padx=16, pady=14)
        fc.pack(fill="x")

        lbl(fc, "INSERT PLAYER FOR RATING", size=11, bold=True,
            color=self.COLOR).pack(anchor="w", pady=(0, 10))

        self.vars = {}
        for label, key in [("Nome","nome"),("Cognome","cognome"),
                            ("Data Nascita","nascita"),("Ruolo","ruolo"),
                            ("Max Season Pt","max_season"),("Max Career Pt","max_career")]:
            r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
            lbl(r, label, size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
            v = tk.StringVar(); self.vars[key] = v
            entry(r, v).pack(fill="x", pady=(0, 2))

        sep(fc).pack(fill="x", pady=8)
        lbl(fc, "Bonus Competizioni", size=9, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 4))

        self.comp_vars = {}
        for comp, pts in self.BONUSES.items():
            row = tk.Frame(fc, bg=BG_CARD); row.pack(fill="x", pady=1)
            v = tk.BooleanVar(); self.comp_vars[comp] = v
            pts_str = f"  +{pts}pt" if pts > 0 else "  N/D"
            tk.Checkbutton(row, text=comp, variable=v,
                           bg=BG_CARD, fg=TEXT_WHITE,
                           selectcolor=BG_DARK, activebackground=BG_CARD,
                           activeforeground=self.COLOR,
                           font=("Segoe UI", 9), anchor="w").pack(side="left")
            lbl(row, pts_str, size=8,
                color=self.COLOR if pts > 0 else TEXT_DIM,
                bg=BG_CARD).pack(side="left")

        sep(fc).pack(fill="x", pady=10)
        HoopButton(fc, "ADD PLAYER", self._add_player,
                   bg_color=self.COLOR, hover_color=self.HOVER,
                   width=240, height=38, icon="➕", font_size=10).pack(pady=4)

        # RIGHT ranking
        right = tk.Frame(body, bg=BG_DARK)
        right.pack(side="left", fill="both", expand=True)
        top_bar = tk.Frame(right, bg=BG_DARK)
        top_bar.pack(fill="x", pady=(0, 8))
        lbl(top_bar, "GENERATE RATING", size=13, bold=True,
            color=self.COLOR, bg=BG_DARK).pack(side="left")
        HoopButton(top_bar, "REFRESH", self.refresh,
                   bg_color=self.COLOR, hover_color=self.HOVER,
                   width=120, height=32, icon="🔄", font_size=9).pack(side="right")

        cols = ("Rank","Nome","Ruolo","Season","Career","Bonus","SCORE")
        self.tree = ttk.Treeview(right, columns=cols, show="headings", height=22)
        for c, w in zip(cols, [50, 180, 80, 70, 70, 70, 90]):
            self.tree.heading(c, text=c)
            self.tree.column(c, width=w, anchor="center")

        vsb = ttk.Scrollbar(right, orient="vertical",
                            command=self.tree.yview,
                            style="Dark.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.pack(side="left", fill="both", expand=True)
        vsb.pack(side="right", fill="y")

        db = tk.Frame(self, bg=BG_DARK)
        db.pack(fill="x", padx=30, pady=4)
        HoopButton(db, "ELIMINA SELEZIONATO", self._delete_player,
                   bg_color="#252525", hover_color="#3a0010",
                   width=220, height=30, icon="🗑", font_size=9).pack(side="right")
        self.refresh()

    def _add_player(self):
        try:
            nome    = self.vars["nome"].get().strip()
            cognome = self.vars["cognome"].get().strip()
            if not nome or not cognome:
                messagebox.showwarning("HoopIQ", "Inserisci Nome e Cognome.")
                return
            bonus = sum(pts for comp, pts in self.BONUSES.items()
                        if self.comp_vars[comp].get())
            player = {
                "nome":       nome,
                "cognome":    cognome,
                "nascita":    self.vars["nascita"].get().strip(),
                "ruolo":      self.vars["ruolo"].get().strip(),
                "max_season": int(self.vars["max_season"].get() or 0),
                "max_career": int(self.vars["max_career"].get() or 0),
                "bonus":      bonus,
                "comps":      [c for c, v in self.comp_vars.items() if v.get()],
                "added":      datetime.now().isoformat(timespec="seconds"),
            }
            data = self._load()
            data.append(player)
            self._save(data)
            for v in self.vars.values(): v.set("")
            for v in self.comp_vars.values(): v.set(False)
            self.refresh()
            self.app.refresh_side()
            messagebox.showinfo("HoopIQ", f"✅  {nome} {cognome} aggiunto!")
        except ValueError:
            messagebox.showerror("HoopIQ", "Max Season / Max Career devono essere numeri interi.")

    def _delete_player(self):
        sel = self.tree.selection()
        if not sel: return
        idx  = int(self.tree.item(sel[0], "values")[0]) - 1
        data = sorted(self._load(), key=compute_score, reverse=True)
        p    = data[idx]
        if messagebox.askyesno("HoopIQ", f"Eliminare {p['nome']} {p['cognome']}?"):
            data.remove(p)
            self._save(data)
            self.refresh()
            self.app.refresh_side()

    def refresh(self):
        for row in self.tree.get_children(): self.tree.delete(row)
        players = sorted(self._load(), key=compute_score, reverse=True)
        for i, p in enumerate(players, 1):
            score = compute_score(p)
            tag = "gold" if i == 1 else "silver" if i == 2 else "bronze" if i == 3 else ""
            self.tree.insert("", "end",
                values=(i, f"{p['nome']} {p['cognome']}", p.get("ruolo",""),
                        p["max_season"], p["max_career"], p["bonus"], score),
                tags=(tag,))
        self.tree.tag_configure("gold",   background="#1a1500", foreground="#f5c518")
        self.tree.tag_configure("silver", background="#141414", foreground="#c0c0c0")
        self.tree.tag_configure("bronze", background="#120800", foreground="#cd7f32")


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (WOMEN) — eredita da Man
# ══════════════════════════════════════════════════════════════
class PageRatingWomen(PageRatingMan):
    GENDER  = "women"
    LABEL   = "♀ WNBA · FIBA · EUROLEAGUE"
    BONUSES = WOMEN_BONUSES
    COLOR   = ACCENT_PINK
    HOVER   = BTN_HOVER_P

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()

    def _load(self): return load_women()
    def _save(self, d): save_women(d)

    def _header_title(self):
        return "Player Rating — Femminile"


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER SCOUTING
# ══════════════════════════════════════════════════════════════
class PageScouting(BasePage):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._last_stats = {}
        self._last_name  = ""
        self._last_score = 0
        self._build()

    def _build(self):
        self._header("Player Scouting",
                     "Analisi statistica con radar chart e score /100", ACCENT_GLD)
        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=8)

        # Left form — scrollabile per non tagliare campi
        sf = ScrollableFrame(body, bg=BG_CARD, fixed_width=300)
        sf.pack(side="left", fill="y", padx=(0, 16))
        fc = tk.Frame(sf.inner, bg=BG_CARD, padx=16, pady=14)
        fc.pack(fill="x")

        lbl(fc, "STATISTICHE GIOCATORE", size=11, bold=True,
            color=ACCENT_GLD).pack(anchor="w", pady=(0, 10))

        r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=3)
        lbl(r, "Nome Giocatore", size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self.v_name = tk.StringVar()
        entry(r, self.v_name, width=28).pack(fill="x")

        sep(fc).pack(fill="x", pady=8)

        self.stat_vars = {}
        stat_info = [
            ("Minuti",       "min/g","30"),   ("P. Rubate","x g","1.0"),
            ("Stoppate",     "x g","1.0"),    ("Rimbalzi","x g","6.0"),
            ("Punti",        "x g","15.0"),   ("Tiro 2pt %","%","50.0"),
            ("Tiro 3pt %",   "%","37.0"),     ("Tiro Libero %","%","80.0"),
        ]
        for label, unit, ref in stat_info:
            row = tk.Frame(fc, bg=BG_CARD); row.pack(fill="x", pady=3)
            hr = tk.Frame(row, bg=BG_CARD); hr.pack(fill="x")
            lbl(hr, label, size=9, color=TEXT_WHITE, bg=BG_CARD).pack(side="left")
            lbl(hr, f"  ref {ref}{unit}", size=7, color=TEXT_DIM, bg=BG_CARD).pack(side="left")
            v = tk.StringVar(); self.stat_vars[label] = v
            entry(row, v, width=28).pack(fill="x")

        sep(fc).pack(fill="x", pady=10)
        HoopButton(fc, "GENERA REPORT", self._generate,
                   bg_color=ACCENT_GLD, hover_color=BTN_HOVER_G,
                   fg="#000000", width=240, height=38,
                   icon="📊", font_size=10).pack(pady=4)
        HoopButton(fc, "SALVA REPORT", self._save_report,
                   bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                   width=240, height=32, icon="💾", font_size=9).pack(pady=3)
        HoopButton(fc, "RESET", self._reset,
                   bg_color="#252525", hover_color="#111",
                   width=240, height=28, icon="🔄", font_size=9).pack(pady=3)

        # Right
        rf = tk.Frame(body, bg=BG_DARK)
        rf.pack(side="left", fill="both", expand=True)
        self.score_label = tk.Label(rf, text="Score: —", fg=TEXT_WHITE,
                                    bg=BG_DARK, font=("Segoe UI", 30, "bold"))
        self.score_label.pack(pady=(8, 0))
        self.grade_label = tk.Label(rf, text="", fg=TEXT_GRAY,
                                    bg=BG_DARK, font=("Segoe UI", 13))
        self.grade_label.pack()
        self.fig = Figure(figsize=(6, 5), facecolor=BG_DARK)
        self.canvas = FigureCanvasTkAgg(self.fig, master=rf)
        self.canvas.get_tk_widget().pack(fill="both", expand=True)
        self._draw_empty_radar()

    def _draw_empty_radar(self):
        self.fig.clear()
        ax = self.fig.add_subplot(111, polar=True)
        ax.set_facecolor(BG_CARD); self.fig.patch.set_facecolor(BG_DARK)
        cats = list(SCOUT_REF.keys()); N = len(cats)
        angles = [n/N*2*math.pi for n in range(N)] + [0]
        ax.set_theta_offset(math.pi/2); ax.set_theta_direction(-1)
        ax.set_xticks(angles[:-1]); ax.set_xticklabels(cats, color=TEXT_GRAY, size=8)
        ax.set_ylim(0,100); ax.set_yticks([25,50,75,100])
        ax.set_yticklabels(["25","50","75","100"], color=TEXT_DIM, size=7)
        ax.grid(color=BORDER, linewidth=0.8); ax.spines["polar"].set_color(BORDER)
        self.canvas.draw()

    def _generate(self):
        stats = {k: v.get() for k, v in self.stat_vars.items()}
        score = compute_scout_score(stats)
        name  = self.v_name.get().strip() or "Giocatore"
        self._last_stats = stats; self._last_name = name; self._last_score = score

        if score >= 90:   grade, color = "ELITE ⭐⭐⭐",   "#f5c518"
        elif score >= 75: grade, color = "TOP PLAYER ⭐⭐", ACCENT_GLD
        elif score >= 60: grade, color = "GOOD PLAYER ⭐", "#4caf50"
        elif score >= 45: grade, color = "AVERAGE",         TEXT_GRAY
        else:             grade, color = "DEVELOPING",       ACCENT_RED

        self.score_label.configure(text=f"{name}  —  {score}/100", fg=color)
        self.grade_label.configure(text=grade, fg=color)

        self.fig.clear()
        ax = self.fig.add_subplot(111, polar=True)
        ax.set_facecolor(BG_CARD); self.fig.patch.set_facecolor(BG_DARK)
        cats = list(SCOUT_REF.keys()); N = len(cats)
        angles = [n/N*2*math.pi for n in range(N)] + [0]
        values = []
        for k, ref in SCOUT_REF.items():
            try: v = float(stats.get(k, 0))
            except: v = 0.0
            values.append(min((v/ref)*100 if ref else 0, 150))
        values += values[:1]
        ax.set_theta_offset(math.pi/2); ax.set_theta_direction(-1)
        ax.set_xticks(angles[:-1]); ax.set_xticklabels(cats, color=TEXT_WHITE, size=8)
        ax.set_ylim(0,150); ax.set_yticks([25,50,75,100,125])
        ax.set_yticklabels(["25%","50%","75%","100%","125%"], color=TEXT_DIM, size=7)
        ax.grid(color=BORDER, linewidth=0.8); ax.spines["polar"].set_color(BORDER)
        ax.plot(angles, [100]*N+[100], color=TEXT_DIM, linewidth=1,
                linestyle="--", alpha=0.5)
        ax.fill(angles, values, alpha=0.22, color=color)
        ax.plot(angles, values, color=color, linewidth=2.5)
        ax.scatter(angles[:-1], values[:-1], color=color, s=50, zorder=5)
        ax.set_title(name, color=TEXT_WHITE, size=11, pad=20, fontweight="bold")
        self.canvas.draw()

    def _save_report(self):
        if not self._last_name:
            messagebox.showwarning("HoopIQ", "Genera prima un report."); return
        reports = load_scouting()
        reports.append({
            "nome":  self._last_name,
            "score": self._last_score,
            "stats": self._last_stats,
            "date":  datetime.now().isoformat(timespec="seconds"),
        })
        save_scouting(reports)
        messagebox.showinfo("HoopIQ", f"💾  Report di {self._last_name} salvato in scouting.json")

    def _reset(self):
        self.v_name.set("")
        for v in self.stat_vars.values(): v.set("")
        self.score_label.configure(text="Score: —", fg=TEXT_WHITE)
        self.grade_label.configure(text="")
        self._draw_empty_radar()

    def refresh(self): pass


# ══════════════════════════════════════════════════════════════
#  PAGE: GLOBAL COVERAGE
# ══════════════════════════════════════════════════════════════
class PageGlobal(BasePage):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def _build(self):
        self._header("Global Coverage",
                     "Distribuzione rating per campionato — NBA · FIBA · WNBA · EUROLEAGUE",
                     ACCENT_BLU)
        ctrl = tk.Frame(self, bg=BG_DARK)
        ctrl.pack(fill="x", padx=30, pady=6)
        HoopButton(ctrl, "AGGIORNA GRAFICI", self.refresh,
                   bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                   width=200, height=34, icon="🔄", font_size=10).pack(side="left")

        self.chart_frame = tk.Frame(self, bg=BG_DARK)
        self.chart_frame.pack(fill="both", expand=True, padx=30, pady=8)
        self.stats_frame = tk.Frame(self, bg=BG_DARK)
        self.stats_frame.pack(fill="x", padx=30, pady=(0, 12))
        self.refresh()

    def refresh(self):
        for w in self.chart_frame.winfo_children(): w.destroy()
        for w in self.stats_frame.winfo_children(): w.destroy()
        man_pl   = load_man()
        women_pl = load_women()

        fig = Figure(figsize=(12, 5), facecolor=BG_DARK)
        ax1 = fig.add_subplot(121)
        self._draw_pie(ax1, man_pl, MAN_BONUSES, "Maschile ♂", ACCENT_BLU)
        ax2 = fig.add_subplot(122)
        self._draw_pie(ax2, women_pl, WOMEN_BONUSES, "Femminile ♀", ACCENT_PINK)
        fig.tight_layout(pad=3)
        canvas = FigureCanvasTkAgg(fig, master=self.chart_frame)
        canvas.get_tk_widget().pack(fill="both", expand=True)
        canvas.draw()

        for lab, val, col in [
            ("Giocatori ♂", str(len(man_pl)),   ACCENT_BLU),
            ("Giocatori ♀", str(len(women_pl)), ACCENT_PINK),
            ("Top Score ♂", str(max((compute_score(p) for p in man_pl),   default=0)), ACCENT_GLD),
            ("Top Score ♀", str(max((compute_score(p) for p in women_pl), default=0)), ACCENT_GLD),
            ("Scouting Reports", str(len(load_scouting())), "#4caf50"),
        ]:
            card = tk.Frame(self.stats_frame, bg=BG_CARD, padx=16, pady=10)
            card.pack(side="left", padx=6)
            tk.Label(card, text=val, fg=col, bg=BG_CARD,
                     font=("Segoe UI", 20, "bold")).pack()
            tk.Label(card, text=lab, fg=TEXT_GRAY, bg=BG_CARD,
                     font=("Segoe UI", 8)).pack()

    def _draw_pie(self, ax, players, bonus_table, title, color):
        ax.set_facecolor(BG_CARD)
        comp_totals = {}
        for p in players:
            for comp in p.get("comps", []):
                if comp in bonus_table and bonus_table[comp] > 0:
                    comp_totals[comp] = comp_totals.get(comp, 0) + bonus_table[comp]
        if not comp_totals:
            ax.text(0.5, 0.5, "Nessun dato", transform=ax.transAxes,
                    ha="center", va="center", color=TEXT_GRAY, fontsize=12)
            ax.set_title(title, color=TEXT_WHITE, fontsize=12, fontweight="bold")
            ax.axis("off"); return
        labels = list(comp_totals.keys())
        sizes  = list(comp_totals.values())
        palette = plt.cm.get_cmap("tab20", len(labels))
        colors  = [palette(i) for i in range(len(labels))]
        wedges, _, autotexts = ax.pie(
            sizes, labels=None, autopct="%1.0f%%", colors=colors,
            startangle=90, pctdistance=0.75,
            wedgeprops={"linewidth": 1.5, "edgecolor": BG_DARK})
        for at in autotexts:
            at.set_color(TEXT_WHITE); at.set_fontsize(8)
        ax.legend(wedges, [f"{l} ({s}pt)" for l, s in zip(labels, sizes)],
                  loc="lower center", bbox_to_anchor=(0.5, -0.28),
                  ncol=2, fontsize=7, framealpha=0, labelcolor=TEXT_WHITE)
        ax.set_title(title, color=TEXT_WHITE, fontsize=12, fontweight="bold", pad=12)


# ══════════════════════════════════════════════════════════════
#  ENTRY POINT
# ══════════════════════════════════════════════════════════════
if __name__ == "__main__":
    app = HoopIQApp()
    app.mainloop()

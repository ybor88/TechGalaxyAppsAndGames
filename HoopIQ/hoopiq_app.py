"""
HoopIQ - Scout. Analyze. Elevate.
Basketball Intelligence Desktop Application
"""

import tkinter as tk
from tkinter import ttk, messagebox
import json
import os
import math
import warnings
import webbrowser
from urllib.parse import quote_plus
from datetime import datetime
import matplotlib
matplotlib.use("TkAgg")
warnings.filterwarnings("ignore", message="Glyph.*missing from font")
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
BASE_DIR           = os.path.dirname(os.path.abspath(__file__))
FILE_MAN           = os.path.join(BASE_DIR, "players_man.json")
FILE_MAN_NAZIONI   = os.path.join(BASE_DIR, "players_man_nazioni.json")
FILE_WOMEN         = os.path.join(BASE_DIR, "players_women.json")
FILE_WOMEN_NAZIONI = os.path.join(BASE_DIR, "players_women_nazioni.json")
FILE_YOUTH         = os.path.join(BASE_DIR, "players_youth.json")
FILE_YOUTH_NAZIONI = os.path.join(BASE_DIR, "players_youth_nazioni.json")
FILE_SCOUTING      = os.path.join(BASE_DIR, "scouting.json")
FILE_MINOR         = os.path.join(BASE_DIR, "players_minor.json")

def _read_json(path, default):
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return default

def _write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def load_man():           return _read_json(FILE_MAN,           [])
def load_man_nazioni():   return _read_json(FILE_MAN_NAZIONI,   [])
def load_women():         return _read_json(FILE_WOMEN,         [])
def load_women_nazioni(): return _read_json(FILE_WOMEN_NAZIONI, [])
def load_youth():         return _read_json(FILE_YOUTH,         [])
def load_youth_nazioni(): return _read_json(FILE_YOUTH_NAZIONI, [])
def load_scouting():      return _read_json(FILE_SCOUTING,      [])
def load_minor():         return _read_json(FILE_MINOR,         [])

def save_man(data):           _write_json(FILE_MAN,           data)
def save_man_nazioni(data):   _write_json(FILE_MAN_NAZIONI,   data)
def save_women(data):         _write_json(FILE_WOMEN,         data)
def save_women_nazioni(data): _write_json(FILE_WOMEN_NAZIONI, data)
def save_youth(data):         _write_json(FILE_YOUTH,         data)
def save_youth_nazioni(data): _write_json(FILE_YOUTH_NAZIONI, data)
def save_scouting(data):      _write_json(FILE_SCOUTING,      data)
def save_minor(data):         _write_json(FILE_MINOR,         data)

# ══════════════════════════════════════════════════════════════
#  TABELLE BONUS
# ══════════════════════════════════════════════════════════════
# ── Maschile Club ────────────────────────────────────────────
MAN_CLUB_BONUSES = {
    "NBA PLAYOFF":  30,
    "NBA":          20,
    "EUROLEAGUE":   10,
    "EUROCUP":       5,
    "FIBA EUROCUP":  3,
    "G LEAGUE":      3,
    "ALTRE":         2,
    "FIBA CLUB":     1,
    "FIBA CLUB 2":   0,
}
# ── Maschile Nazioni ──────────────────────────────────────────
MAN_NAZIONI_BONUSES = {
    "FIBA NAZIONI":       20,
    "FIBA NAZIONI QUALIF": 10,
}
MAN_BONUSES = {**MAN_CLUB_BONUSES, **MAN_NAZIONI_BONUSES}

# ── Femminile Club ────────────────────────────────────────────
WOMEN_CLUB_BONUSES = {
    "WNBA PLAYOFF": 30,
    "WNBA":         20,
    "EUROLEAGUE":   10,
    "EUROCUP":       5,
    "FIBA EUROCUP":  3,
    "ALTRE":         2,
    "FIBA CLUB":     1,
    "FIBA CLUB 2":   0,
}
# ── Femminile Nazioni ─────────────────────────────────────────
WOMEN_NAZIONI_BONUSES = {
    "FIBA NAZIONI":        20,
    "FIBA NAZIONI QUALIF": 10,
}
WOMEN_BONUSES = {**WOMEN_CLUB_BONUSES, **WOMEN_NAZIONI_BONUSES}

# ── Giovanili Club ────────────────────────────────────────────
YOUTH_BONUSES = {
    "NCAA":  20,
    "ANGXT": 10,
}
# ── Giovanili Nazioni ─────────────────────────────────────────
YOUTH_NAZIONI_BONUSES = {
    "FIBA NAZIONI YOUNG": 2,
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
def compute_score(player: dict) -> float:
    return float(player.get("max_season", 0)) + float(player.get("bonus", 0))

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


def _hoop_dlg(parent, message, kind="info"):
    """Dialog scuro in stile HoopIQ. Ritorna True (OK/Sì) o False (No)."""
    _cfg = {
        "info":  ("#4caf50", "#388e3c", "✅"),
        "warn":  (ACCENT_GLD, BTN_HOVER_G, "⚠"),
        "error": (ACCENT_RED, BTN_HOVER_R, "✖"),
        "yesno": (ACCENT_BLU, BTN_HOVER_B, "?"),
    }
    color, hover, icon = _cfg.get(kind, (ACCENT_BLU, BTN_HOVER_B, "ℹ"))
    result = [False]
    root = parent.winfo_toplevel()
    dlg  = tk.Toplevel(root)
    dlg.configure(bg=BG_CARD)
    dlg.resizable(False, False)
    dlg.transient(root)
    dlg.grab_set()
    dlg.title("HoopIQ")
    # barra colorata in cima
    tk.Frame(dlg, bg=color, height=3).pack(fill="x")
    tk.Label(dlg, text=icon, fg=color, bg=BG_CARD,
             font=("Segoe UI", 22)).pack(pady=(14, 4))
    tk.Label(dlg, text=message, fg=TEXT_WHITE, bg=BG_CARD,
             font=("Segoe UI", 10), wraplength=320,
             justify="center").pack(padx=28, pady=(0, 16))
    tk.Frame(dlg, bg=BORDER, height=1).pack(fill="x")
    bf = tk.Frame(dlg, bg=BG_CARD, pady=10)
    bf.pack(fill="x", padx=20)
    if kind == "yesno":
        def _yes(): result[0] = True;  dlg.destroy()
        def _no():  result[0] = False; dlg.destroy()
        HoopButton(bf, "SÌ", _yes, bg_color=ACCENT_RED, hover_color=BTN_HOVER_R,
                   width=110, height=32, font_size=10).pack(side="left",  padx=4)
        HoopButton(bf, "NO", _no,  bg_color="#252525",  hover_color="#3a3a3a",
                   width=110, height=32, font_size=10).pack(side="right", padx=4)
    else:
        def _ok(): result[0] = True; dlg.destroy()
        HoopButton(bf, "OK", _ok, bg_color=color, hover_color=hover,
                   width=130, height=32, font_size=10).pack()
    dlg.update_idletasks()
    rw = root.winfo_x() + root.winfo_width()  // 2
    rh = root.winfo_y() + root.winfo_height() // 2
    dw = max(dlg.winfo_reqwidth(),  360)
    dh = dlg.winfo_reqheight()
    dlg.geometry(f"{dw}x{dh}+{rw - dw//2}+{rh - dh//2}")
    root.wait_window(dlg)
    return result[0]


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
    # Treeview con heading oro (pagina Da Aggiornare)
    s.configure("Gold.Treeview",
                background=BG_CARD, foreground=TEXT_WHITE,
                fieldbackground=BG_CARD, rowheight=30,
                font=("Segoe UI", 10))
    s.configure("Gold.Treeview.Heading",
                background="#1a1200", foreground=ACCENT_GLD,
                font=("Segoe UI", 10, "bold"), relief="flat")
    s.map("Gold.Treeview",
          background=[("selected", "#2d2000")],
          foreground=[("selected", ACCENT_GLD)])
    s.map("Treeview",
          background=[("selected", ACCENT_RED)],
          foreground=[("selected", TEXT_WHITE)])
    # Combobox — campo scuro
    s.configure("TCombobox",
                fieldbackground=BG_INPUT, background="#252525",
                foreground=TEXT_WHITE, selectbackground=ACCENT_RED,
                selectforeground=TEXT_WHITE, insertcolor=TEXT_WHITE,
                arrowcolor=TEXT_WHITE, bordercolor="#333",
                lightcolor=BG_INPUT, darkcolor=BG_INPUT,
                relief="flat")
    s.map("TCombobox",
          fieldbackground=[("readonly", BG_INPUT), ("disabled", BG_CARD)],
          foreground=[("readonly", TEXT_WHITE), ("disabled", TEXT_DIM)],
          background=[("readonly", "#252525"), ("active", "#333")],
          selectbackground=[("readonly", ACCENT_RED)],
          arrowcolor=[("readonly", TEXT_WHITE), ("disabled", TEXT_DIM)])


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
        # Listbox popup del Combobox (widget nativo — va impostato sul root)
        self.option_add("*TCombobox*Listbox.background",       BG_PANEL)
        self.option_add("*TCombobox*Listbox.foreground",       TEXT_WHITE)
        self.option_add("*TCombobox*Listbox.selectBackground", ACCENT_RED)
        self.option_add("*TCombobox*Listbox.selectForeground", TEXT_WHITE)
        self.option_add("*TCombobox*Listbox.font",             "Segoe\\ UI 10")
        self.option_add("*TCombobox*Listbox.relief",           "flat")
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
                # sidebar: mantieni aspect ratio, larghezza 150px
                ow, oh = img.size
                target_w = 150
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
        for Cls in (PageRatingMan, PageRatingManNazioni,
                    PageRatingWomen, PageRatingWomenNazioni,
                    PageRatingYouth, PageRatingYouthNazioni,
                    PageRatingMinor,
                    PageScouting, PageClubCoverage, PageAgeCoverage,
                    PageGlobal, PageOutdated):
            p = Cls(self.content, self)
            self.pages[Cls.__name__] = p
            p.place(relx=0, rely=0, relwidth=1, relheight=1)
        self.show_page("PageRatingMan")

    def _build_sidebar(self):
        side = tk.Frame(self, bg=BG_PANEL, width=240)
        side.pack(side="left", fill="y")
        side.pack_propagate(False)

        # ── Footer e stats: pack side="bottom" PRIMA degli altri ──
        tk.Label(side, text=f"HoopIQ  v2.0  ·  © {datetime.now().year}",
                 fg=TEXT_DIM, bg=BG_PANEL,
                 font=("Segoe UI", 7)).pack(side="bottom", pady=8)
        tk.Frame(side, bg="#222", height=1).pack(side="bottom", fill="x", padx=16)
        self._side_stats = tk.Frame(side, bg=BG_PANEL)
        self._side_stats.pack(side="bottom", fill="x", padx=8, pady=6)
        self._refresh_side_stats()
        tk.Frame(side, bg="#222", height=1).pack(side="bottom", fill="x", padx=16)

        # ── Logo ──────────────────────────────────────────
        logo_area = tk.Frame(side, bg=BG_PANEL)
        logo_area.pack(fill="x", pady=(4, 0))
        if self._logo_side:
            tk.Label(logo_area, image=self._logo_side,
                     bg=BG_PANEL).pack(padx=8, pady=(4, 2))
        else:
            tk.Label(logo_area, text="HOOP IQ", fg=TEXT_WHITE, bg=BG_PANEL,
                     font=("Segoe UI", 18, "bold")).pack()
            tk.Label(logo_area, text="SCOUT · ANALYZE · ELEVATE",
                     fg=TEXT_DIM, bg=BG_PANEL,
                     font=("Segoe UI", 7, "bold")).pack(pady=(0, 4))

        # ── Striscia rossa ────────────────────────────────
        tk.Frame(side, bg=ACCENT_RED, height=2).pack(fill="x")

        # ── Nav scrollabile (riempie lo spazio rimasto) ───
        self.nav_buttons = {}
        nav_items = [
            ("Rating ♂  Club",     "PageRatingMan",          ACCENT_RED,  BTN_HOVER_R),
            ("Rating ♂  Nazioni",  "PageRatingManNazioni",    ACCENT_RED,  BTN_HOVER_R),
            ("Rating ♀  Club",     "PageRatingWomen",         ACCENT_PINK, BTN_HOVER_P),
            ("Rating ♀  Nazioni",  "PageRatingWomenNazioni",  ACCENT_PINK, BTN_HOVER_P),
            ("Rating 🎓  Club",     "PageRatingYouth",         ACCENT_GRN,  BTN_HOVER_GRN),
            ("Rating 🎓  Nazioni",  "PageRatingYouthNazioni",  ACCENT_GRN,  BTN_HOVER_GRN),
            ("Rating Minori ♂♀",   "PageRatingMinor",         ACCENT_MINOR, BTN_HOVER_MINOR),
            ("Player Scouting",    "PageScouting",            ACCENT_GLD,   BTN_HOVER_G),
            ("Club Coverage",      "PageClubCoverage",        ACCENT_TEAL,  BTN_HOVER_TEAL),
            ("Age Coverage",      "PageAgeCoverage",         ACCENT_AGE,   BTN_HOVER_AGE),
            ("Global Coverage",   "PageGlobal",              ACCENT_BLU,   BTN_HOVER_B),
            ("Da Aggiornare",      "PageOutdated",            ACCENT_GLD,  BTN_HOVER_G),
        ]
        nav_sf = ScrollableFrame(side, bg=BG_PANEL)
        nav_sf.pack(fill="both", expand=True, pady=(2, 0))
        nav_frame = nav_sf.inner
        for label, page, col, hov in nav_items:
            self._nav_btn(nav_frame, label, page, col, hov)

    def _nav_btn(self, parent, label, page, col, hov):
        frame = tk.Frame(parent, bg=BG_PANEL)
        frame.pack(fill="x", pady=1, padx=0)
        # barra colorata sinistra
        bar = tk.Frame(frame, bg="#333", width=3)
        bar.pack(side="left", fill="y")
        btn = tk.Button(frame, text=f"  {label}", anchor="w",
                        bg=BG_PANEL, fg="#cccccc",
                        activebackground="#222", activeforeground=TEXT_WHITE,
                        relief="flat", font=("Segoe UI", 9),
                        cursor="hand2", padx=12, pady=6, bd=0,
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
            "PageRatingMan":          "Rating Maschile Club  ·  NBA · EUROLEAGUE · FIBA",
            "PageRatingManNazioni":   "Rating Maschile Nazioni  ·  FIBA NAZIONI",
            "PageRatingWomen":        "Rating Femminile Club  ·  WNBA · EUROLEAGUE · FIBA",
            "PageRatingWomenNazioni": "Rating Femminile Nazioni  ·  FIBA NAZIONI",
            "PageRatingYouth":        "Rating Giovanili Club  ·  NCAA · ANGXT",
            "PageRatingYouthNazioni": "Rating Giovanili Nazioni  ·  FIBA NAZIONI YOUNG",
            "PageRatingMinor":         "Rating Minori  ·  Campionati minori ♂♀ — bonus manuale",
            "PageScouting":           "Player Scouting  ·  Analisi statistica radar /100",
            "PageClubCoverage":       "Club Coverage  ·  Distribuzione giocatori per team/club",
            "PageAgeCoverage":        "Age Coverage  ·  Distribuzione anagrafica per fascia d'età",
            "PageGlobal":             "Global Coverage  ·  Distribuzione campionati per rating",
            "PageOutdated":           "Da Aggiornare  ·  Giocatori attivi non aggiornati da > 30 giorni",
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
        m   = load_man();           mn  = load_man_nazioni()
        wm  = load_women();         wn  = load_women_nazioni()
        y   = load_youth();         yn  = load_youth_nazioni()
        mi  = load_minor()
        items = [
            ("Club ♂",     str(len(m)),   ACCENT_RED),
            ("Naz ♂",      str(len(mn)),  ACCENT_RED),
            ("Club ♀",     str(len(wm)),  ACCENT_PINK),
            ("Naz ♀",      str(len(wn)),  ACCENT_PINK),
            ("Club 🎓",    str(len(y)),   ACCENT_GRN),
            ("Naz 🎓",     str(len(yn)),  ACCENT_GRN),
            ("Minori ♂♀",  str(len(mi)),  ACCENT_MINOR),
        ]
        for i, (lab, val, col) in enumerate(items):
            r, c = divmod(i, 3)
            card = tk.Frame(self._side_stats, bg="#141414", padx=4, pady=3)
            card.grid(row=r, column=c, padx=2, pady=2, sticky="nsew")
            tk.Label(card, text=val, fg=col, bg="#141414",
                     font=("Segoe UI", 13, "bold")).pack()
            tk.Label(card, text=lab, fg=TEXT_DIM, bg="#141414",
                     font=("Segoe UI", 6)).pack()

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
    GENDER        = "man"
    TITLE         = "Player Rating — Maschile  Club"
    LABEL         = "♂ Club — NBA · EUROLEAGUE · FIBA"
    BONUSES       = MAN_CLUB_BONUSES
    BONUS_SECTIONS = [
        ("🏀  CLUB",    MAN_CLUB_BONUSES),
    ]
    COLOR    = ACCENT_RED
    HOVER    = BTN_HOVER_R
    _FILE    = FILE_MAN

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def _load(self): return _read_json(self._FILE, [])
    def _save(self, d): _write_json(self._FILE, d)

    def _build(self):
        self._editing_player = None   # None = add mode, dict = edit mode
        self._header(self.TITLE, self.LABEL, self.COLOR)
        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=8)

        # LEFT scrollable form
        sf = ScrollableFrame(body, bg=BG_CARD, fixed_width=240)
        sf.pack(side="left", fill="y", padx=(0, 12))
        fc = tk.Frame(sf.inner, bg=BG_CARD, padx=16, pady=14)
        fc.pack(fill="x")

        lbl(fc, "INSERT PLAYER FOR RATING", size=11, bold=True,
            color=self.COLOR).pack(anchor="w", pady=(0, 10))

        # Mode indicator
        self._mode_lbl = tk.Label(fc, text="➕  MODALITA': INSERIMENTO",
                                   fg=self.COLOR, bg=BG_CARD,
                                   font=("Segoe UI", 8, "bold"))
        self._mode_lbl.pack(anchor="w", pady=(0, 6))

        self.vars = {}
        for label, key in [("Nome","nome"),("Cognome","cognome"),
                            ("Data Nascita","nascita"),("Ruolo","ruolo"),
                            ("Team","team"),
                            ("Max Season Pt","max_season")]:
            r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
            lbl(r, label, size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
            v = tk.StringVar(); self.vars[key] = v
            entry(r, v).pack(fill="x", pady=(0, 2))

        # Stato
        r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
        lbl(r, "Stato", size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self.stato_var = tk.StringVar(value="Attivo")
        stato_cb = ttk.Combobox(r, textvariable=self.stato_var,
                                values=["Attivo", "Inattivo", "Infortunato", "Ritirato"],
                                state="readonly", width=26,
                                font=("Segoe UI", 10))
        stato_cb.pack(fill="x", pady=(0, 2))

        sep(fc).pack(fill="x", pady=8)
        lbl(fc, "Bonus Competizioni", size=9, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 4))

        self.comp_var = tk.StringVar(value="")
        for section_title, section_dict in self.BONUS_SECTIONS:
            lbl(fc, section_title, size=8, bold=True,
                color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w", pady=(6, 2))
            for comp, pts in section_dict.items():
                row = tk.Frame(fc, bg=BG_CARD); row.pack(fill="x", pady=1)
                pts_str = f"  +{pts}pt" if pts > 0 else "  N/D"
                tk.Radiobutton(row, text=comp, variable=self.comp_var, value=comp,
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
        self._reset_btn = HoopButton(fc, "RESET FORM", self._clear_form,
                                     bg_color="#252525", hover_color="#333",
                                     width=240, height=28, icon="↩", font_size=9)
        self._reset_btn.pack(pady=2)

        # RIGHT ranking
        right = tk.Frame(body, bg=BG_DARK)
        right.pack(side="left", fill="both", expand=True)
        top_bar = tk.Frame(right, bg=BG_DARK)
        top_bar.pack(fill="x", pady=(0, 4))
        lbl(top_bar, "GENERATE RATING", size=13, bold=True,
            color=self.COLOR, bg=BG_DARK).pack(side="left")
        HoopButton(top_bar, "REFRESH", self.refresh,
                   bg_color=self.COLOR, hover_color=self.HOVER,
                   width=120, height=32, icon="🔄", font_size=9).pack(side="right")

        # ── Barra filtro ──────────────────────────────────
        fbar = tk.Frame(right, bg=BG_DARK)
        fbar.pack(fill="x", pady=(0, 6))
        tk.Label(fbar, text="Filtra per:", fg=TEXT_GRAY, bg=BG_DARK,
                 font=("Segoe UI", 8)).pack(side="left")
        self._filter_field = tk.StringVar(value="Nome")
        ttk.Combobox(fbar, textvariable=self._filter_field,
                     values=["Nome","Team","Ruolo","Stato","Comp"],
                     state="readonly", width=7,
                     font=("Segoe UI", 9)).pack(side="left", padx=(4, 6))
        self._filter_text = tk.StringVar()
        fe = entry(fbar, self._filter_text, width=22)
        fe.pack(side="left", fill="x", expand=True, padx=(0, 6))
        fe.bind("<KeyRelease>", lambda e: self.refresh())
        HoopButton(fbar, "AZZERA", self._reset_filter,
                   bg_color="#252525", hover_color="#3a3a3a",
                   width=80, height=26, icon="✖", font_size=8).pack(side="left")

        cols = ("Rank","Nome","Ruolo","Team","Stato","Comp","SCORE")
        self.tree = ttk.Treeview(right, columns=cols, show="headings", height=22)
        col_cfg = {
            "Rank":  (38,  "center", False),
            "Nome":  (162, "w",      False),
            "Ruolo": (52,  "center", False),
            "Team":  (192, "w",      True),
            "Stato": (78,  "center", False),
            "Comp":  (88,  "center", False),
            "SCORE": (60,  "center", False),
        }
        for c in cols:
            w, anc, stretch = col_cfg[c]
            self.tree.heading(c, text=c)
            self.tree.column(c, width=w, anchor=anc, stretch=stretch, minwidth=w)

        vsb = ttk.Scrollbar(right, orient="vertical",
                            command=self.tree.yview,
                            style="Dark.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.pack(side="left", fill="both", expand=True)
        vsb.pack(side="right", fill="y")
        self.tree.bind("<Double-1>", self._on_double_click)
        self.tree.bind("<Button-3>", self._show_context_menu)

        db = tk.Frame(self, bg=BG_DARK)
        db.pack(fill="x", padx=30, pady=4)
        HoopButton(db, "MODIFICA SELEZIONATO", self._edit_selected,
                   bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                   width=220, height=30, icon="✏", font_size=9).pack(side="left")
        HoopButton(db, "VIDEO", self._search_video,
                   bg_color="#1a1200", hover_color=BTN_HOVER_G,
                   width=100, height=30, icon="🎬", font_size=9).pack(side="left", padx=8)
        HoopButton(db, "STATS", self._search_stats,
                   bg_color="#001a2e", hover_color=BTN_HOVER_B,
                   width=100, height=30, icon="📊", font_size=9).pack(side="left", padx=(0, 8))
        HoopButton(db, "IMMAGINI", self._search_images,
                   bg_color="#1a001a", hover_color=BTN_HOVER_P,
                   width=110, height=30, icon="🖼", font_size=9).pack(side="left", padx=(0, 8))
        HoopButton(db, "ELIMINA SELEZIONATO", self._delete_player,
                   bg_color="#252525", hover_color="#3a0010",
                   width=220, height=30, icon="🗑", font_size=9).pack(side="right")
        self.refresh()

    def _selected_name(self):
        sel = self.tree.selection()
        if not sel:
            _hoop_dlg(self, "Seleziona un giocatore dalla lista.", "warn")
            return None
        return self.tree.item(sel[0], "values")[1]

    def _search_video(self):
        name = self._selected_name()
        if not name: return
        webbrowser.open(f"https://www.google.com/search?q={quote_plus(name + ' basketball highlights')}&tbm=vid")

    def _search_stats(self):
        name = self._selected_name()
        if not name: return
        webbrowser.open(f"https://www.google.com/search?q={quote_plus(name + ' basketball career stats')}")

    def _search_images(self):
        name = self._selected_name()
        if not name: return
        webbrowser.open(f"https://www.google.com/search?q={quote_plus(name + ' basketball')}&tbm=isch")

    def _show_context_menu(self, event):
        row = self.tree.identify_row(event.y)
        if not row:
            return
        self.tree.selection_set(row)
        menu = tk.Menu(self, tearoff=0,
                       bg=BG_PANEL, fg=TEXT_WHITE,
                       activebackground=ACCENT_GLD, activeforeground="#000000",
                       font=("Segoe UI", 9), bd=0, relief="flat")
        menu.add_command(label="🎬  Cerca video su Google",      command=self._search_video)
        menu.add_command(label="📊  Statistiche complete Google", command=self._search_stats)
        menu.add_command(label="🖼  Immagini Google",             command=self._search_images)
        menu.add_separator()
        menu.add_command(label="✏  Modifica",  command=self._edit_selected)
        menu.add_command(label="🗑  Elimina",   command=self._delete_player)
        menu.tk_popup(event.x_root, event.y_root)

    def _add_player(self):
        try:
            nome    = self.vars["nome"].get().strip()
            cognome = self.vars["cognome"].get().strip()
            if not nome or not cognome:
                _hoop_dlg(self, "Inserisci Nome e Cognome.", "warn")
                return
            selected_comp = self.comp_var.get()
            bonus = self.BONUSES.get(selected_comp, 0)
            data = self._load()

            if self._editing_player is not None:
                # Find and update existing player (match by 'added' timestamp)
                ep = self._editing_player
                for i, p in enumerate(data):
                    if p.get("added") == ep.get("added") and p.get("nome") == ep.get("nome"):
                        data[i] = {
                            "nome":       nome,
                            "cognome":    cognome,
                            "nascita":    self.vars["nascita"].get().strip(),
                            "ruolo":      self.vars["ruolo"].get().strip(),
                            "team":       self.vars["team"].get().strip(),
                            "stato":      self.stato_var.get(),
                            "max_season": float(self.vars["max_season"].get() or 0),
                            "comp":       selected_comp,
                            "bonus":      bonus,
                            "added":      ep.get("added"),
                            "modified":   datetime.now().isoformat(timespec="seconds"),
                        }
                        break
                self._save(data)
                self._clear_form()
                self.refresh()
                self.app.refresh_side()
                _hoop_dlg(self, f"{nome} {cognome} aggiornato!", "info")
            else:
                player = {
                    "nome":       nome,
                    "cognome":    cognome,
                    "nascita":    self.vars["nascita"].get().strip(),
                    "ruolo":      self.vars["ruolo"].get().strip(),
                    "team":       self.vars["team"].get().strip(),
                    "stato":      self.stato_var.get(),
                    "max_season": float(self.vars["max_season"].get() or 0),
                    "comp":       selected_comp,
                    "bonus":      bonus,
                    "added":      datetime.now().isoformat(timespec="seconds"),
                }
                check_keys = ("nome", "cognome", "nascita", "ruolo", "stato", "max_season", "comp")
                if any(all(p.get(k) == player.get(k) for k in check_keys) for p in data):
                    _hoop_dlg(self, f"{nome} {cognome} è già presente!", "warn")
                    return
                data.append(player)
                self._save(data)
                self._clear_form()
                self.refresh()
                self.app.refresh_side()
                _hoop_dlg(self, f"{nome} {cognome} aggiunto!", "info")
        except Exception as e:
            _hoop_dlg(self, f"Errore: {e}", "error")

    def _delete_player(self):
        sel = self.tree.selection()
        if not sel: return
        idx  = int(sel[0]) - 1
        data = sorted(self._load(), key=compute_score, reverse=True)
        p    = data[idx]
        if _hoop_dlg(self, f"Eliminare {p['nome']} {p['cognome']}?", "yesno"):
            data.remove(p)
            self._save(data)
            self._clear_form()
            self.refresh()
            self.app.refresh_side()

    def _on_double_click(self, event):
        sel = self.tree.selection()
        if not sel: return
        self._edit_selected()

    def _edit_selected(self):
        sel = self.tree.selection()
        if not sel:
            _hoop_dlg(self, "Seleziona un giocatore dalla classifica.", "warn")
            return
        idx  = int(sel[0]) - 1
        data = sorted(self._load(), key=compute_score, reverse=True)
        p    = data[idx]
        self._editing_player = p
        self.vars["nome"].set(p.get("nome", ""))
        self.vars["cognome"].set(p.get("cognome", ""))
        self.vars["nascita"].set(p.get("nascita", ""))
        self.vars["ruolo"].set(p.get("ruolo", ""))
        self.vars["max_season"].set(str(p.get("max_season", "")))
        self.vars["team"].set(p.get("team", ""))
        self.stato_var.set(p.get("stato", "Attivo"))
        self.comp_var.set(p.get("comp", ""))
        self._mode_lbl.configure(text=f"✏  MODALITA': MODIFICA  —  {p['nome']} {p['cognome']}")
        self._reset_btn._lbl.configure(text="✖  ANNULLA MODIFICA")
        self._reset_btn._bg    = "#3a0010"
        self._reset_btn._hover = "#5a0018"
        self._reset_btn._left.configure(bg="#5a0018")
        self._reset_btn._set_color("#3a0010")

    def _clear_form(self):
        self._editing_player = None
        for v in self.vars.values(): v.set("")
        self.stato_var.set("Attivo")
        self.comp_var.set("")
        self._mode_lbl.configure(text="➕  MODALITA': INSERIMENTO")
        self._reset_btn._lbl.configure(text="↩  RESET FORM")
        self._reset_btn._bg    = "#252525"
        self._reset_btn._hover = "#333333"
        self._reset_btn._left.configure(bg="#3a3a3a")
        self._reset_btn._set_color("#252525")

    def _get_filtered_players(self, players):
        ft = getattr(self, "_filter_text", None)
        ff = getattr(self, "_filter_field", None)
        if not (ft and ff): return players
        text = ft.get().strip().lower()
        if not text: return players
        field = ff.get()
        key_fn = {
            "Nome":  lambda p: f"{p.get('nome','')} {p.get('cognome','')}".lower(),
            "Team":  lambda p: p.get("team",  "").lower(),
            "Ruolo": lambda p: p.get("ruolo", "").lower(),
            "Stato": lambda p: p.get("stato", "").lower(),
            "Comp":  lambda p: p.get("comp",  "").lower(),
        }.get(field, lambda p: "")
        return [p for p in players if text in key_fn(p)]

    def _reset_filter(self):
        if hasattr(self, "_filter_text"):  self._filter_text.set("")
        if hasattr(self, "_filter_field"): self._filter_field.set("Nome")
        self.refresh()

    def refresh(self):
        for row in self.tree.get_children(): self.tree.delete(row)
        players = sorted(self._load(), key=compute_score, reverse=True)
        players = self._get_filtered_players(players)
        # stato color tags
        STATO_TAG = {
            "Attivo":     "stato_attivo",
            "Inattivo":   "stato_inattivo",
            "Infortunato":"stato_infort",
            "Ritirato":   "stato_ritirato",
        }
        MEDALS = {1: "🥇", 2: "🥈", 3: "🥉"}
        for i, p in enumerate(players, 1):
            score = compute_score(p)
            stato = p.get("stato", "Attivo")
            if i == 1:   tag = "gold"
            elif i == 2: tag = "silver"
            elif i == 3: tag = "bronze"
            else:        tag = STATO_TAG.get(stato, "")
            comps_str = p.get("comp", "") or "-"
            rank_lbl  = MEDALS.get(i, str(i))
            self.tree.insert("", "end", iid=str(i),
                values=(rank_lbl, f"{p.get('nome','')} {p.get('cognome','')}".strip(),
                        p.get("ruolo",""), p.get("team","") or "-", stato, comps_str, score),
                tags=(tag,))
        self.tree.tag_configure("gold",          background="#1a1500", foreground="#f5c518")
        self.tree.tag_configure("silver",        background="#141414", foreground="#c0c0c0")
        self.tree.tag_configure("bronze",        background="#120800", foreground="#cd7f32")
        self.tree.tag_configure("stato_attivo",  background=BG_CARD,   foreground="#4caf50")
        self.tree.tag_configure("stato_inattivo",background="#1a1a1a", foreground="#888888")
        self.tree.tag_configure("stato_infort",  background="#1a1000", foreground="#ff9800")
        self.tree.tag_configure("stato_ritirato",background="#0d0d1a", foreground="#5c6bc0")


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (WOMEN CLUB)
# ══════════════════════════════════════════════════════════════
class PageRatingWomen(PageRatingMan):
    GENDER         = "women"
    TITLE          = "Player Rating — Femminile  Club"
    LABEL          = "♀ Club — WNBA · EUROLEAGUE · FIBA"
    BONUSES        = WOMEN_CLUB_BONUSES
    BONUS_SECTIONS = [("🏀  CLUB", WOMEN_CLUB_BONUSES)]
    COLOR          = ACCENT_PINK
    HOVER          = BTN_HOVER_P
    _FILE          = FILE_WOMEN

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (MAN NAZIONI)
# ══════════════════════════════════════════════════════════════
class PageRatingManNazioni(PageRatingMan):
    GENDER         = "man_naz"
    TITLE          = "Player Rating — Maschile  Nazioni"
    LABEL          = "♂ Nazioni — FIBA NAZIONI · QUALIF"
    BONUSES        = MAN_NAZIONI_BONUSES
    BONUS_SECTIONS = [("🌍  NAZIONI", MAN_NAZIONI_BONUSES)]
    COLOR          = ACCENT_RED
    HOVER          = BTN_HOVER_R
    _FILE          = FILE_MAN_NAZIONI

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (WOMEN NAZIONI)
# ══════════════════════════════════════════════════════════════
class PageRatingWomenNazioni(PageRatingMan):
    GENDER         = "women_naz"
    TITLE          = "Player Rating — Femminile  Nazioni"
    LABEL          = "♀ Nazioni — FIBA NAZIONI · QUALIF"
    BONUSES        = WOMEN_NAZIONI_BONUSES
    BONUS_SECTIONS = [("🌍  NAZIONI", WOMEN_NAZIONI_BONUSES)]
    COLOR          = ACCENT_PINK
    HOVER          = BTN_HOVER_P
    _FILE          = FILE_WOMEN_NAZIONI

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (GIOVANILI CLUB)
# ══════════════════════════════════════════════════════════════
ACCENT_GRN    = "#2e7d32"
BTN_HOVER_GRN = "#388e3c"
ACCENT_MINOR  = "#6a1b9a"   # deep purple — campionati minori
BTN_HOVER_MINOR = "#7b1fa2"

class PageRatingYouth(PageRatingMan):
    GENDER         = "youth"
    TITLE          = "Player Rating — Giovanili  Club"
    LABEL          = "🎓 Club — NCAA · ANGXT"
    BONUSES        = YOUTH_BONUSES
    BONUS_SECTIONS = [("🎓  GIOVANILI", YOUTH_BONUSES)]
    COLOR          = ACCENT_GRN
    HOVER          = BTN_HOVER_GRN
    _FILE          = FILE_YOUTH

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (GIOVANILI NAZIONI)
# ══════════════════════════════════════════════════════════════
class PageRatingYouthNazioni(PageRatingMan):
    GENDER         = "youth_naz"
    TITLE          = "Player Rating — Giovanili  Nazioni"
    LABEL          = "🎓 Nazioni — FIBA NAZIONI YOUNG"
    BONUSES        = YOUTH_NAZIONI_BONUSES
    BONUS_SECTIONS = [("🌍  NAZIONI GIOVANILI", YOUTH_NAZIONI_BONUSES)]
    COLOR          = ACCENT_GRN
    HOVER          = BTN_HOVER_GRN
    _FILE          = FILE_YOUTH_NAZIONI

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING — CAMPIONATI MINORI (bonus manuale)
# ══════════════════════════════════════════════════════════════
class PageRatingMinor(PageRatingMan):
    TITLE  = "Player Rating — Campionati Minori"
    LABEL  = "♂♀ — Serie A2 · G League · LNB Pro B · BSL · Campionati Minori"
    BONUSES = {}
    BONUS_SECTIONS = []
    COLOR  = ACCENT_MINOR
    HOVER  = BTN_HOVER_MINOR
    _FILE  = FILE_MINOR

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()

    def _build(self):
        self._editing_player = None
        self._header(self.TITLE, self.LABEL, self.COLOR)
        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=8)

        sf = ScrollableFrame(body, bg=BG_CARD, fixed_width=240)
        sf.pack(side="left", fill="y", padx=(0, 12))
        fc = tk.Frame(sf.inner, bg=BG_CARD, padx=16, pady=14)
        fc.pack(fill="x")

        lbl(fc, "INSERT PLAYER FOR RATING", size=11, bold=True,
            color=self.COLOR).pack(anchor="w", pady=(0, 10))
        self._mode_lbl = tk.Label(fc, text="➕  MODALITA': INSERIMENTO",
                                   fg=self.COLOR, bg=BG_CARD,
                                   font=("Segoe UI", 8, "bold"))
        self._mode_lbl.pack(anchor="w", pady=(0, 6))

        self.vars = {}
        for label, key in [("Nome","nome"),("Cognome","cognome"),
                            ("Data Nascita","nascita"),("Ruolo","ruolo"),
                            ("Team","team"),
                            ("Max Season Pt","max_season")]:
            r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
            lbl(r, label, size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
            v = tk.StringVar(); self.vars[key] = v
            entry(r, v).pack(fill="x", pady=(0, 2))

        # Campionato libero
        r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
        lbl(r, "Campionato", size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self.comp_var = tk.StringVar()
        entry(r, self.comp_var).pack(fill="x", pady=(0, 2))

        # Bonus manuale
        r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
        lbl(r, "Bonus Manuale (+pt)", size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self.bonus_var = tk.StringVar(value="0")
        entry(r, self.bonus_var).pack(fill="x", pady=(0, 2))
        lbl(fc, "Imposta tu il bonus in base al campionato",
            size=7, color=TEXT_DIM, bg=BG_CARD).pack(anchor="w", pady=(0, 4))

        # Stato
        r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=2)
        lbl(r, "Stato", size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self.stato_var = tk.StringVar(value="Attivo")
        ttk.Combobox(r, textvariable=self.stato_var,
                     values=["Attivo","Attiva","Inattivo","Infortunato","Ritirato"],
                     state="readonly", width=26,
                     font=("Segoe UI", 10)).pack(fill="x", pady=(0, 2))

        sep(fc).pack(fill="x", pady=10)
        HoopButton(fc, "ADD PLAYER", self._add_player,
                   bg_color=self.COLOR, hover_color=self.HOVER,
                   width=240, height=38, icon="➕", font_size=10).pack(pady=4)
        self._reset_btn = HoopButton(fc, "RESET FORM", self._clear_form,
                                     bg_color="#252525", hover_color="#333",
                                     width=240, height=28, icon="↩", font_size=9)
        self._reset_btn.pack(pady=2)

        right = tk.Frame(body, bg=BG_DARK)
        right.pack(side="left", fill="both", expand=True)
        top_bar = tk.Frame(right, bg=BG_DARK)
        top_bar.pack(fill="x", pady=(0, 8))
        lbl(top_bar, "GENERATE RATING", size=13, bold=True,
            color=self.COLOR, bg=BG_DARK).pack(side="left")
        HoopButton(top_bar, "REFRESH", self.refresh,
                   bg_color=self.COLOR, hover_color=self.HOVER,
                   width=120, height=32, icon="\U0001f504", font_size=9).pack(side="right")

        # ── Barra filtro ──────────────────────────────────
        fbar = tk.Frame(right, bg=BG_DARK)
        fbar.pack(fill="x", pady=(0, 6))
        tk.Label(fbar, text="Filtra per:", fg=TEXT_GRAY, bg=BG_DARK,
                 font=("Segoe UI", 8)).pack(side="left")
        self._filter_field = tk.StringVar(value="Nome")
        ttk.Combobox(fbar, textvariable=self._filter_field,
                     values=["Nome","Team","Ruolo","Stato","Comp"],
                     state="readonly", width=7,
                     font=("Segoe UI", 9)).pack(side="left", padx=(4, 6))
        self._filter_text = tk.StringVar()
        fe = entry(fbar, self._filter_text, width=22)
        fe.pack(side="left", fill="x", expand=True, padx=(0, 6))
        fe.bind("<KeyRelease>", lambda e: self.refresh())
        HoopButton(fbar, "AZZERA", self._reset_filter,
                   bg_color="#252525", hover_color="#3a3a3a",
                   width=80, height=26, icon="✖", font_size=8).pack(side="left")

        cols = ("Rank","Nome","Ruolo","Team","Stato","Campionato","Bonus","SCORE")
        self.tree = ttk.Treeview(right, columns=cols, show="headings", height=22)
        for c, w in zip(cols, [50, 160, 70, 120, 80, 150, 55, 80]):
            self.tree.heading(c, text=c)
            self.tree.column(c, width=w, anchor="center")

        vsb = ttk.Scrollbar(right, orient="vertical", command=self.tree.yview,
                             style="Dark.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.pack(side="left", fill="both", expand=True)
        vsb.pack(side="right", fill="y")
        self.tree.bind("<Double-1>", self._on_double_click)
        self.tree.bind("<Button-3>", self._show_context_menu)

        db = tk.Frame(self, bg=BG_DARK)
        db.pack(fill="x", padx=30, pady=4)
        HoopButton(db, "MODIFICA SELEZIONATO", self._edit_selected,
                   bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                   width=220, height=30, icon="✏", font_size=9).pack(side="left")
        HoopButton(db, "VIDEO", self._search_video,
                   bg_color="#1a1200", hover_color=BTN_HOVER_G,
                   width=100, height=30, icon="\U0001f3ac", font_size=9).pack(side="left", padx=8)
        HoopButton(db, "STATS", self._search_stats,
                   bg_color="#001a2e", hover_color=BTN_HOVER_B,
                   width=100, height=30, icon="\U0001f4ca", font_size=9).pack(side="left", padx=(0, 8))
        HoopButton(db, "IMMAGINI", self._search_images,
                   bg_color="#1a001a", hover_color=BTN_HOVER_P,
                   width=110, height=30, icon="\U0001f5bc", font_size=9).pack(side="left", padx=(0, 8))
        HoopButton(db, "ELIMINA SELEZIONATO", self._delete_player,
                   bg_color="#252525", hover_color="#3a0010",
                   width=220, height=30, icon="\U0001f5d1", font_size=9).pack(side="right")
        self.refresh()

    def _add_player(self):
        try:
            nome    = self.vars["nome"].get().strip()
            cognome = self.vars["cognome"].get().strip()
            if not nome or not cognome:
                _hoop_dlg(self, "Inserisci Nome e Cognome.", "warn")
                return
            comp  = self.comp_var.get().strip()
            try:
                bonus = float(self.bonus_var.get() or 0)
            except ValueError:
                _hoop_dlg(self, "Bonus deve essere un numero.", "warn")
                return
            data = self._load()
            if self._editing_player is not None:
                ep = self._editing_player
                for i, p in enumerate(data):
                    if p.get("added") == ep.get("added") and p.get("nome") == ep.get("nome"):
                        data[i] = {
                            "nome":       nome,
                            "cognome":    cognome,
                            "nascita":    self.vars["nascita"].get().strip(),
                            "ruolo":      self.vars["ruolo"].get().strip(),
                            "team":       self.vars["team"].get().strip(),
                            "stato":      self.stato_var.get(),
                            "max_season": float(self.vars["max_season"].get() or 0),
                            "comp":       comp,
                            "bonus":      bonus,
                            "added":      ep.get("added"),
                            "modified":   datetime.now().isoformat(timespec="seconds"),
                        }
                        break
                self._save(data)
                self._clear_form()
                self.refresh()
                self.app.refresh_side()
                _hoop_dlg(self, f"{nome} {cognome} aggiornato!", "info")
            else:
                player = {
                    "nome":       nome,
                    "cognome":    cognome,
                    "nascita":    self.vars["nascita"].get().strip(),
                    "ruolo":      self.vars["ruolo"].get().strip(),
                    "team":       self.vars["team"].get().strip(),
                    "stato":      self.stato_var.get(),
                    "max_season": float(self.vars["max_season"].get() or 0),
                    "comp":       comp,
                    "bonus":      bonus,
                    "added":      datetime.now().isoformat(timespec="seconds"),
                }
                check_keys = ("nome", "cognome", "nascita", "ruolo", "stato", "max_season", "comp")
                if any(all(p.get(k) == player.get(k) for k in check_keys) for p in data):
                    _hoop_dlg(self, f"{nome} {cognome} è già presente!", "warn")
                    return
                data.append(player)
                self._save(data)
                self._clear_form()
                self.refresh()
                self.app.refresh_side()
                _hoop_dlg(self, f"{nome} {cognome} aggiunto!", "info")
        except Exception as e:
            _hoop_dlg(self, f"Errore: {e}", "error")

    def _edit_selected(self):
        sel = self.tree.selection()
        if not sel:
            _hoop_dlg(self, "Seleziona un giocatore dalla classifica.", "warn")
            return
        idx  = int(sel[0]) - 1
        data = sorted(self._load(), key=compute_score, reverse=True)
        p    = data[idx]
        self._editing_player = p
        self.vars["nome"].set(p.get("nome", ""))
        self.vars["cognome"].set(p.get("cognome", ""))
        self.vars["nascita"].set(p.get("nascita", ""))
        self.vars["ruolo"].set(p.get("ruolo", ""))
        self.vars["max_season"].set(str(p.get("max_season", "")))
        self.vars["team"].set(p.get("team", ""))
        self.stato_var.set(p.get("stato", "Attivo"))
        self.comp_var.set(p.get("comp", ""))
        self.bonus_var.set(str(p.get("bonus", 0)))
        self._mode_lbl.configure(text=f"✏  MODALITA': MODIFICA  —  {p['nome']} {p['cognome']}")
        self._reset_btn._lbl.configure(text="✖  ANNULLA MODIFICA")
        self._reset_btn._bg    = "#3a0010"
        self._reset_btn._hover = "#5a0018"
        self._reset_btn._left.configure(bg="#5a0018")
        self._reset_btn._set_color("#3a0010")

    def _clear_form(self):
        self._editing_player = None
        for v in self.vars.values(): v.set("")
        self.stato_var.set("Attivo")
        self.comp_var.set("")
        self.bonus_var.set("0")
        self._mode_lbl.configure(text="➕  MODALITA': INSERIMENTO")
        self._reset_btn._lbl.configure(text="↩  RESET FORM")
        self._reset_btn._bg    = "#252525"
        self._reset_btn._hover = "#333333"
        self._reset_btn._left.configure(bg="#3a3a3a")
        self._reset_btn._set_color("#252525")

    def refresh(self):
        for row in self.tree.get_children(): self.tree.delete(row)
        players = sorted(self._load(), key=compute_score, reverse=True)
        players = self._get_filtered_players(players)
        STATO_TAG = {
            "Attivo":     "stato_attivo",
            "Attiva":     "stato_attivo",
            "Inattivo":   "stato_inattivo",
            "Infortunato":"stato_infort",
            "Ritirato":   "stato_ritirato",
        }
        MEDALS = {1: "🥇", 2: "🥈", 3: "🥉"}
        for i, p in enumerate(players, 1):
            score = compute_score(p)
            stato = p.get("stato", "Attivo")
            if i == 1:   tag = "gold"
            elif i == 2: tag = "silver"
            elif i == 3: tag = "bronze"
            else:        tag = STATO_TAG.get(stato, "")
            rank_lbl = MEDALS.get(i, str(i))
            self.tree.insert("", "end", iid=str(i),
                values=(rank_lbl, f"{p.get('nome','')} {p.get('cognome','')}".strip(),
                        p.get("ruolo",""), p.get("team","") or "-", stato,
                        p.get("comp", "-"), p.get("bonus", 0), score),
                tags=(tag,))
        self.tree.tag_configure("gold",          background="#1a1500", foreground="#f5c518")
        self.tree.tag_configure("silver",        background="#141414", foreground="#c0c0c0")
        self.tree.tag_configure("bronze",        background="#120800", foreground="#cd7f32")
        self.tree.tag_configure("stato_attivo",  background=BG_CARD,   foreground="#4caf50")
        self.tree.tag_configure("stato_inattivo",background="#1a1a1a", foreground="#888888")
        self.tree.tag_configure("stato_infort",  background="#1a1000", foreground="#ff9800")
        self.tree.tag_configure("stato_ritirato",background="#0d0d1a", foreground="#5c6bc0")


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
            _hoop_dlg(self, "Genera prima un report.", "warn"); return
        reports = load_scouting()
        reports.append({
            "nome":  self._last_name,
            "score": self._last_score,
            "stats": self._last_stats,
            "date":  datetime.now().isoformat(timespec="seconds"),
        })
        save_scouting(reports)
        _hoop_dlg(self, f"Report di {self._last_name} salvato!", "info")

    def _reset(self):
        self.v_name.set("")
        for v in self.stat_vars.values(): v.set("")
        self.score_label.configure(text="Score: —", fg=TEXT_WHITE)
        self.grade_label.configure(text="")
        self._draw_empty_radar()

    def refresh(self): pass


# ══════════════════════════════════════════════════════════════
#  PAGE: CLUB COVERAGE  (distribuzione per team)
# ══════════════════════════════════════════════════════════════
ACCENT_TEAL   = "#00838f"
BTN_HOVER_TEAL = "#0097a7"

class PageClubCoverage(BasePage):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def lift(self, aboveThis=None):
        super().lift(aboveThis)
        self.refresh()

    def _build(self):
        self._header("Club Coverage",
                     "Distribuzione giocatori per team/club — tutti i campionati",
                     ACCENT_TEAL)
        ctrl = tk.Frame(self, bg=BG_DARK)
        ctrl.pack(fill="x", padx=30, pady=6)
        HoopButton(ctrl, "AGGIORNA", self.refresh,
                   bg_color=ACCENT_TEAL, hover_color=BTN_HOVER_TEAL,
                   width=140, height=32, icon="🔄", font_size=9).pack(side="left")

        # chip stats
        self._chips = tk.Frame(ctrl, bg=BG_DARK)
        self._chips.pack(side="left", padx=20)

        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=(0, 8))

        cols = ("#", "Team", "Categorie", "N.", "Score Medio", "Top Player")
        tv_frame = tk.Frame(body, bg=BG_DARK)
        tv_frame.pack(fill="both", expand=True)

        self.tree = ttk.Treeview(tv_frame, columns=cols, show="headings",
                                 style="Treeview", selectmode="browse")
        widths  = [40, 200, 130, 50, 110, 200]
        anchors = {"#": "center", "N.": "center", "Score Medio": "center"}
        for col, w in zip(cols, widths):
            self.tree.heading(col, text=col)
            self.tree.column(col, width=w, anchor=anchors.get(col, "w"), minwidth=30)

        vsb = ttk.Scrollbar(tv_frame, orient="vertical",
                            command=self.tree.yview,
                            style="Dark.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set)
        vsb.pack(side="right", fill="y")
        self.tree.pack(fill="both", expand=True)
        self.refresh()

    def _collect(self):
        sources = [
            (load_man(),           "♂ Club"),
            (load_man_nazioni(),   "♂ Naz"),
            (load_women(),         "♀ Club"),
            (load_women_nazioni(), "♀ Naz"),
            (load_youth(),         "🎓 Club"),
            (load_youth_nazioni(), "🎓 Naz"),
            (load_minor(),         "Minori"),
        ]
        team_data = {}
        for players, cat in sources:
            for p in players:
                team = p.get("team", "").strip() or "—"
                score = compute_score(p)
                nome  = f"{p.get('nome','')} {p.get('cognome','')}".strip()
                if team not in team_data:
                    team_data[team] = {
                        "count": 0, "total_score": 0.0,
                        "top_score": -1.0, "top_player": "", "cats": set()
                    }
                d = team_data[team]
                d["count"]       += 1
                d["total_score"] += score
                d["cats"].add(cat)
                if score > d["top_score"]:
                    d["top_score"]  = score
                    d["top_player"] = nome
        return team_data

    def refresh(self):
        team_data = self._collect()

        # chip stats
        for w in self._chips.winfo_children(): w.destroy()
        n_teams    = len(team_data)
        multi_cat  = sum(1 for d in team_data.values() if len(d["cats"]) > 1)
        top_team   = max(team_data, key=lambda t: team_data[t]["count"], default="—")
        top_count  = team_data[top_team]["count"] if top_team != "—" else 0
        for val, lab, col in [
            (str(n_teams),              "Team totali",      ACCENT_TEAL),
            (str(multi_cat),            "Multi-categoria",  ACCENT_GLD),
            (f"{top_count}",            f"Max ({top_team[:12]}…)" if len(top_team) > 12 else f"Max ({top_team})", ACCENT_RED),
        ]:
            c = tk.Frame(self._chips, bg="#141414", padx=10, pady=4)
            c.pack(side="left", padx=4)
            tk.Label(c, text=val, fg=col, bg="#141414",
                     font=("Segoe UI", 14, "bold")).pack()
            tk.Label(c, text=lab, fg=TEXT_DIM, bg="#141414",
                     font=("Segoe UI", 7)).pack()

        for row in self.tree.get_children():
            self.tree.delete(row)

        # sort: score medio desc, poi count desc
        rows = sorted(team_data.items(),
                      key=lambda kv: (-(kv[1]["total_score"] / kv[1]["count"]),
                                      -kv[1]["count"]))
        for i, (team, d) in enumerate(rows, 1):
            avg   = round(d["total_score"] / d["count"], 1)
            cats  = "  ".join(sorted(d["cats"]))
            count = d["count"]
            if count >= 3:   tag = "top"
            elif count == 2: tag = "mid"
            else:            tag = ""
            self.tree.insert("", "end",
                             values=(i, team, cats, count, avg, d["top_player"]),
                             tags=(tag,))

        self.tree.tag_configure("top", background="#1a1500", foreground="#f5c518")
        self.tree.tag_configure("mid", background="#141414", foreground="#c0c0c0")


# ══════════════════════════════════════════════════════════════
#  PAGE: AGE COVERAGE
# ══════════════════════════════════════════════════════════════
ACCENT_AGE     = "#e65100"
BTN_HOVER_AGE  = "#f4511e"

class PageAgeCoverage(BasePage):
    # Birth-year decade brackets (lo/hi are inclusive birth years)
    BRACKETS = [
        ("≤1949", 0,    1949, "#9e9e9e"),
        ("1950s",  1950, 1959, "#ff7043"),
        ("1960s",  1960, 1969, ACCENT_RED),
        ("1970s",  1970, 1979, ACCENT_GLD),
        ("1980s",  1980, 1989, ACCENT_BLU),
        ("1990s",  1990, 1999, "#26c6da"),
        ("2000s",  2000, 2009, "#4caf50"),
        ("2010+",  2010, 9999, ACCENT_MINOR),
    ]

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def lift(self, aboveThis=None):
        super().lift(aboveThis)
        self.refresh()

    def _build(self):
        self._header("Birth Coverage",
                     "Distribuzione per anno di nascita — decennio e lista giocatori",
                     ACCENT_AGE)

        ctrl = tk.Frame(self, bg=BG_DARK)
        ctrl.pack(fill="x", padx=30, pady=6)
        HoopButton(ctrl, "AGGIORNA", self.refresh,
                   bg_color=ACCENT_AGE, hover_color=BTN_HOVER_AGE,
                   width=140, height=32, icon="🔄", font_size=9).pack(side="left")
        self._chips = tk.Frame(ctrl, bg=BG_DARK)
        self._chips.pack(side="left", padx=20)

        self.chart_frame = tk.Frame(self, bg=BG_DARK)
        self.chart_frame.pack(fill="x", padx=30, pady=(0, 6))

        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=(0, 8))
        cols = ("#", "Nome", "Nascita", "Anno", "Categoria", "Team", "Score")
        tv = tk.Frame(body, bg=BG_DARK)
        tv.pack(fill="both", expand=True)
        self.tree = ttk.Treeview(tv, columns=cols, show="headings",
                                 style="Treeview", selectmode="browse")
        col_w = {"#": 35, "Nome": 180, "Nascita": 95, "Anno": 52,
                 "Categoria": 90, "Team": 170, "Score": 62}
        for c in cols:
            self.tree.heading(c, text=c)
            self.tree.column(c, width=col_w[c],
                             anchor="center" if c in ("#", "Anno", "Score") else "w",
                             minwidth=col_w[c], stretch=(c == "Team"))
        vsb = ttk.Scrollbar(tv, orient="vertical", command=self.tree.yview,
                            style="Dark.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set)
        vsb.pack(side="right", fill="y")
        self.tree.pack(fill="both", expand=True)
        self.refresh()

    def _parse_year(self, nascita):
        for fmt in ("%d-%m-%Y", "%Y-%m-%d", "%d/%m/%Y"):
            try:
                return datetime.strptime(nascita, fmt).year
            except (ValueError, TypeError):
                continue
        return None

    def _collect(self):
        sources = [
            (load_man(),           "♂ Club"),
            (load_man_nazioni(),   "♂ Naz"),
            (load_women(),         "♀ Club"),
            (load_women_nazioni(), "♀ Naz"),
            (load_youth(),         "🎓 Club"),
            (load_youth_nazioni(), "🎓 Naz"),
            (load_minor(),         "Minori"),
        ]
        out = []
        for pl_list, cat in sources:
            for p in pl_list:
                year = self._parse_year(p.get("nascita", ""))
                if year is None: continue
                out.append({
                    "nome":    f"{p.get('nome','')} {p.get('cognome','')}".strip(),
                    "year":    year,
                    "nascita": p.get("nascita", ""),
                    "cat":     cat,
                    "team":    p.get("team", "") or "—",
                    "score":   compute_score(p),
                })
        out.sort(key=lambda x: x["year"])
        return out

    def refresh(self):
        for w in self.chart_frame.winfo_children(): w.destroy()
        players = self._collect()

        # ── Aggrega per decennio ────────────────────────
        dec_scores_map = {}
        for label, lo, hi, col in self.BRACKETS:
            grp = [p for p in players if lo <= p["year"] <= hi]
            dec_scores_map[label] = (round(sum(p["score"] for p in grp), 1), col)

        # ── Chips ───────────────────────────────────────
        for w in self._chips.winfo_children(): w.destroy()
        if players:
            total_score = round(sum(p["score"] for p in players), 1)
            best_dec    = max(dec_scores_map, key=lambda k: dec_scores_map[k][0]) \
                          if dec_scores_map else None
            oldest  = min(players, key=lambda x: x["year"])
            newest  = max(players, key=lambda x: x["year"])
            chips = [
                (str(len(players)),  "Con data nascita",  ACCENT_BLU),
                (str(total_score),   "Score totale",      ACCENT_GLD),
            ]
            if best_dec:
                sc, col = dec_scores_map[best_dec]
                chips.append((best_dec, f"Decennio top ({sc} pt)", col))
            chips += [
                (str(oldest["year"]), oldest["nome"].split()[0], "#9e9e9e"),
                (str(newest["year"]), newest["nome"].split()[0], "#4caf50"),
            ]
            for val, lab, col in chips:
                c = tk.Frame(self._chips, bg="#141414", padx=10, pady=4)
                c.pack(side="left", padx=4)
                tk.Label(c, text=val, fg=col, bg="#141414",
                         font=("Segoe UI", 13, "bold")).pack()
                tk.Label(c, text=lab, fg=TEXT_DIM, bg="#141414",
                         font=("Segoe UI", 7)).pack()

        # ── Bar chart per decennio ──────────────────────
        dec_labels = [b[0] for b in self.BRACKETS]
        dec_scores = []
        dec_counts = []
        dec_colors = []
        for label, lo, hi, col in self.BRACKETS:
            grp = [p for p in players if lo <= p["year"] <= hi]
            dec_scores.append(round(sum(p["score"] for p in grp), 1))
            dec_counts.append(len(grp))
            dec_colors.append(col)

        # Barra col punteggio massimo evidenziata in bianco
        if dec_scores:
            mx = max(dec_scores)
            dec_colors = ["#ffffff" if s == mx else c
                          for s, c in zip(dec_scores, dec_colors)]

        fig = Figure(figsize=(14, 3.2), facecolor=BG_DARK)
        ax  = fig.add_subplot(111)
        ax.set_facecolor(BG_CARD)
        bars = ax.bar(dec_labels, dec_scores, color=dec_colors,
                      edgecolor=BG_DARK, linewidth=1.5, width=0.6)

        for bar, sc, cnt in zip(bars, dec_scores, dec_counts):
            if sc > 0:
                ax.text(bar.get_x() + bar.get_width() / 2,
                        bar.get_height() + max(dec_scores, default=1) * 0.01,
                        f"{sc}\n({cnt}p)",
                        ha="center", va="bottom",
                        color=TEXT_WHITE, fontsize=9, fontweight="bold",
                        linespacing=1.3)

        ax.set_ylabel("Score totale", color=TEXT_GRAY, fontsize=8)
        ax.tick_params(colors=TEXT_GRAY, labelsize=9)
        for sp in ("top", "right"):
            ax.spines[sp].set_visible(False)
        for sp in ("bottom", "left"):
            ax.spines[sp].set_color(BORDER)
        ax.set_ylim(0, max(dec_scores, default=1) * 1.25)
        fig.patch.set_facecolor(BG_DARK)
        fig.tight_layout(pad=1.2)
        FigureCanvasTkAgg(fig, master=self.chart_frame).get_tk_widget().pack(fill="x")
        fig.canvas.draw()

        # ── Treeview — per anno di nascita asc, poi score desc ──────
        for row in self.tree.get_children(): self.tree.delete(row)
        for label, lo, hi, color in self.BRACKETS:
            self.tree.tag_configure(f"b_{label}", foreground=color)

        players_sorted = sorted(players, key=lambda x: (x["year"], -x["score"]))
        for i, p in enumerate(players_sorted, 1):
            yr  = p["year"]
            tag = next(
                (f"b_{b[0]}" for b in self.BRACKETS if b[1] <= yr <= b[2]), ""
            )
            self.tree.insert("", "end",
                             values=(i, p["nome"], p["nascita"], yr,
                                     p["cat"], p["team"], p["score"]),
                             tags=(tag,))


# ══════════════════════════════════════════════════════════════
#  PAGE: GLOBAL COVERAGE
# ══════════════════════════════════════════════════════════════
class PageGlobal(BasePage):
    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def lift(self, aboveThis=None):
        super().lift(aboveThis)
        self.refresh()

    def _build(self):
        self._header("Global Coverage",
                     "Distribuzione rating per campionato — NBA · FIBA · WNBA · EUROLEAGUE",
                     ACCENT_BLU)
        ctrl = tk.Frame(self, bg=BG_DARK)
        ctrl.pack(fill="x", padx=30, pady=6)
        HoopButton(ctrl, "AGGIORNA GRAFICI", self.refresh,
                   bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                   width=200, height=34, icon="🔄", font_size=10).pack(side="left")

        # Contatori in fondo — pack bottom PRIMA
        self.stats_frame = tk.Frame(self, bg=BG_DARK)
        self.stats_frame.pack(side="bottom", fill="x", padx=30, pady=(0, 12))

        # Area grafici scrollabile
        outer = tk.Frame(self, bg=BG_DARK)
        outer.pack(fill="both", expand=True, padx=30, pady=8)

        vsb = ttk.Scrollbar(outer, orient="vertical", style="Dark.Vertical.TScrollbar")
        vsb.pack(side="right", fill="y")

        self._cv = tk.Canvas(outer, bg=BG_DARK, yscrollcommand=vsb.set,
                             highlightthickness=0)
        self._cv.pack(side="left", fill="both", expand=True)
        vsb.configure(command=self._cv.yview)

        self.chart_frame = tk.Frame(self._cv, bg=BG_DARK)
        self._cw = self._cv.create_window((0, 0), window=self.chart_frame, anchor="nw")

        self._cv.bind("<Configure>",
                      lambda e: self._cv.itemconfig(self._cw, width=e.width))
        self.chart_frame.bind("<Configure>",
                              lambda e: self._cv.configure(
                                  scrollregion=self._cv.bbox("all")))
        self._cv.bind("<MouseWheel>",
                      lambda e: self._cv.yview_scroll(-1 * (e.delta // 120), "units"))

        self.refresh()

    def refresh(self):
        for w in self.chart_frame.winfo_children(): w.destroy()
        for w in self.stats_frame.winfo_children(): w.destroy()
        man_pl    = load_man()
        man_naz   = load_man_nazioni()
        women_pl  = load_women()
        women_naz = load_women_nazioni()
        youth_pl  = load_youth()
        youth_naz = load_youth_nazioni()
        minor_pl  = load_minor()

        fig = Figure(figsize=(20, 10), facecolor=BG_DARK)
        # 3 rows × 3 cols — 7 charts + 2 empty slots
        datasets = [
            (man_pl,    MAN_CLUB_BONUSES,     "Club",              ACCENT_BLU),
            (man_naz,   MAN_NAZIONI_BONUSES,  "NBA",               ACCENT_RED),
            (women_pl,  WOMEN_CLUB_BONUSES,   "Club F",            ACCENT_PINK),
            (women_naz, WOMEN_NAZIONI_BONUSES,"WNBA",              "#c2185b"),
            (youth_pl,  YOUTH_BONUSES,        "Giovanili Club",    ACCENT_GRN),
            (youth_naz, YOUTH_NAZIONI_BONUSES,"Giovanili Nazioni", "#1b5e20"),
            (minor_pl,  None,                 "Minori ♂♀",        ACCENT_MINOR),
        ]
        for idx, (players, bonuses, title, color) in enumerate(datasets, 1):
            ax = fig.add_subplot(3, 3, idx)
            n  = len(players)
            self._draw_pie(ax, players, bonuses, f"{title}\n({n} giocatori)", color)
        fig.tight_layout(pad=2.5)
        canvas = FigureCanvasTkAgg(fig, master=self.chart_frame)
        canvas.get_tk_widget().pack(fill="both", expand=True)
        canvas.draw()

        all_groups = [
            ("Club ♂",    man_pl,    ACCENT_BLU),
            ("Naz ♂",     man_naz,   ACCENT_RED),
            ("Club ♀",    women_pl,  ACCENT_PINK),
            ("Naz ♀",     women_naz, "#c2185b"),
            ("Club 🎓",   youth_pl,  ACCENT_GRN),
            ("Naz 🎓",    youth_naz, "#1b5e20"),
            ("Minori ♂♀", minor_pl,  ACCENT_MINOR),
            ("Scouting",  None,      "#4caf50"),
        ]
        for lab, pl, col in all_groups:
            val = str(len(load_scouting())) if pl is None else str(len(pl))
            card = tk.Frame(self.stats_frame, bg=BG_CARD, padx=12, pady=8)
            card.pack(side="left", padx=4)
            tk.Label(card, text=val, fg=col, bg=BG_CARD,
                     font=("Segoe UI", 18, "bold")).pack()
            tk.Label(card, text=lab, fg=TEXT_GRAY, bg=BG_CARD,
                     font=("Segoe UI", 8)).pack()

    def _draw_pie(self, ax, players, bonus_table, title, color):
        ax.set_facecolor(BG_CARD)
        comp_totals = {}
        for p in players:
            comp = p.get("comp", "") or "Altro"
            if bonus_table is None:
                # campionati minori: usa il bonus manuale del giocatore
                val = float(p.get("bonus", 1)) or 1
                comp_totals[comp] = comp_totals.get(comp, 0) + val
            else:
                if comp and comp in bonus_table and bonus_table[comp] > 0:
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
#  PAGE: GIOCATORI DA AGGIORNARE
# ══════════════════════════════════════════════════════════════
class PageOutdated(BasePage):
    STALE_DAYS = 30

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self._build()

    def lift(self, aboveThis=None):
        super().lift(aboveThis)
        self.refresh()

    def _chip(self, parent, label, value, color):
        card = tk.Frame(parent, bg="#141414", padx=18, pady=10)
        card.pack(side="left", padx=(0, 12))
        tk.Label(card, text=value, fg=color, bg="#141414",
                 font=("Segoe UI", 22, "bold")).pack()
        tk.Label(card, text=label, fg=TEXT_DIM, bg="#141414",
                 font=("Segoe UI", 8)).pack()
        return card

    def _build(self):
        self._header("Da Aggiornare",
                     f"Giocatori attivi non modificati da oltre {self.STALE_DAYS} giorni",
                     ACCENT_GLD)
        body = tk.Frame(self, bg=BG_DARK)
        body.pack(fill="both", expand=True, padx=30, pady=(4, 8))

        # ── Strip statistiche ─────────────────────────────
        stats_row = tk.Frame(body, bg=BG_DARK)
        stats_row.pack(fill="x", pady=(0, 12))
        self._chip_attivi  = self._chip(stats_row, "ATTIVI TOTALI",   "—", TEXT_WHITE)
        self._chip_stale   = self._chip(stats_row, "DA AGGIORNARE",   "—", ACCENT_GLD)
        self._chip_old60   = self._chip(stats_row, "OLTRE 60 GG",     "—", "#ff6b35")
        self._chip_old90   = self._chip(stats_row, "OLTRE 90 GG",     "—", ACCENT_RED)

        HoopButton(stats_row, "AGGIORNA", self.refresh,
                   bg_color="#252525", hover_color="#333",
                   width=120, height=36, icon="🔄", font_size=9).pack(side="right", pady=4)

        sep(body).pack(fill="x", pady=(0, 8))

        # ── Tabella ───────────────────────────────────────
        cols = ("#", "Nome", "Cognome", "Competizione", "Rating", "Ult. Modifica", "Giorni fa")
        tv_frame = tk.Frame(body, bg=BG_CARD)
        tv_frame.pack(fill="both", expand=True)

        self.tree = ttk.Treeview(tv_frame, columns=cols, show="headings",
                                 style="Gold.Treeview", selectmode="browse")
        widths  = [35, 130, 150, 130, 65, 150, 85]
        anchors = {"#": "center", "Rating": "center", "Giorni fa": "center"}
        for col, w in zip(cols, widths):
            self.tree.heading(col, text=col)
            self.tree.column(col, width=w, anchor=anchors.get(col, "w"), minwidth=30)

        vsb = ttk.Scrollbar(tv_frame, orient="vertical",
                            command=self.tree.yview,
                            style="Dark.Vertical.TScrollbar")
        self.tree.configure(yscrollcommand=vsb.set)
        vsb.pack(side="right", fill="y")
        self.tree.pack(fill="both", expand=True)

        self.refresh()

    def _collect(self):
        sources = [
            load_man(), load_man_nazioni(),
            load_women(), load_women_nazioni(),
            load_youth(), load_youth_nazioni(),
            load_minor(),
        ]
        now = datetime.now()
        total_attivi, stale = 0, []
        for players in sources:
            for p in players:
                if p.get("stato") not in ("Attivo", "Attiva"):
                    continue
                total_attivi += 1
                ts_str = p.get("modified") or p.get("added", "")
                try:
                    ts = datetime.fromisoformat(ts_str)
                except (ValueError, TypeError):
                    continue
                delta = now - ts
                days  = delta.days
                hours = delta.seconds // 3600
                if days >= self.STALE_DAYS:
                    comp  = p.get("comp", "—")
                    score = p.get("max_season", 0)
                    dt_fmt = ts.strftime("%d/%m/%Y %H:%M")
                    stale.append((days, hours, p.get("nome", ""), p.get("cognome", ""), comp, score, dt_fmt))
        stale.sort(key=lambda x: (-x[0], x[3], x[2]))
        return total_attivi, stale

    def refresh(self):
        total_attivi, stale = self._collect()

        cnt60 = sum(1 for d, *_ in stale if d >= 60)
        cnt90 = sum(1 for d, *_ in stale if d >= 90)

        def _upd(chip, val, color):
            for w in chip.winfo_children():
                if w.cget("font") and "22" in str(w.cget("font")):
                    w.configure(text=str(val), fg=color)

        _upd(self._chip_attivi, total_attivi, TEXT_WHITE)
        _upd(self._chip_stale,  len(stale),   ACCENT_GLD if stale else "#4caf50")
        _upd(self._chip_old60,  cnt60,         "#ff6b35" if cnt60 else TEXT_DIM)
        _upd(self._chip_old90,  cnt90,         ACCENT_RED if cnt90 else TEXT_DIM)

        for row in self.tree.get_children():
            self.tree.delete(row)

        for i, (days, hours, nome, cognome, comp, score, dt_fmt) in enumerate(stale, 1):
            if days >= 90:
                tag = "crit"
            elif days >= 60:
                tag = "warn"
            else:
                tag = "mild"
            self.tree.insert("", "end",
                             values=(i, nome, cognome, comp, score, dt_fmt, f"{days}g {hours}h"),
                             tags=(tag,))

        self.tree.tag_configure("mild", foreground=ACCENT_GLD)
        self.tree.tag_configure("warn", foreground="#ff6b35")
        self.tree.tag_configure("crit", foreground=ACCENT_RED)


# ══════════════════════════════════════════════════════════════
#  ENTRY POINT
# ══════════════════════════════════════════════════════════════
if __name__ == "__main__":
    app = HoopIQApp()
    app.mainloop()

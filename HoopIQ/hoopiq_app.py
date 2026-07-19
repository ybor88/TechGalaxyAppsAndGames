"""
HoopIQ - Scout. Analyze. Elevate.
Basketball Intelligence Desktop Application
"""

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import json
import os
import re
import math
import ssl
import shutil
import threading
import warnings
import webbrowser
import urllib.request
from urllib.parse import quote_plus, quote as _url_quote
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
ACCENT_GRN   = "#2e7d32"   # green
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

ASSETS_DIR        = os.path.join(BASE_DIR, "assets")
COMP_LOGOS_DIR    = os.path.join(ASSETS_DIR, "comp_logos")
PLAYER_PHOTOS_DIR = os.path.join(ASSETS_DIR, "players")
TEAM_LOGOS_DIR    = os.path.join(ASSETS_DIR, "teams")
FILE_AI_CONFIG    = os.path.join(ASSETS_DIR, "ai_config.json")

def _read_json(path, default):
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8-sig") as f:
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
#  CLASSIFICA UNICA PRESTIGIO 2027 — solo partecipazione
# ══════════════════════════════════════════════════════════════
PRESTIGE_2027 = {
    "NBA Playoffs":                    100,
    "NBA Europe League Playoffs":       95,
    "Olimpiadi / Mondiali FIBA":        90,
    "EuroLeague Final Four":            85,
    "NBA Regular Season":               80,
    "NBA Europe League RS":             78,
    "EuroLeague":                       75,
    "SuperCup EuroLeague":              60,
    "Playoff Campionato Nazionale Top": 55,
    "NBA Cup":                          50,
    "Campionato Nazionale Top":         45,
    "EuroCup":                          40,
    "Coppa Intercontinentale FIBA":     35,
    "Coppa Nazionale":                  25,
    "BCL / FIBA Europe Cup":            20,
    "Supercoppa Nazionale":             15,
    "NBA Summer League":                10,
    "NBA Europe Games":                  8,
    "NBA Preseason":                     5,
}

# Competizioni per squadre NAZIONALI (no NBA/club)
PRESTIGE_NAZIONI = {
    "Olimpiadi":                          100,
    "Mondiali FIBA":                       95,
    "Pre-Olimpico FIBA":                   80,
    "EuroBasket":                          85,
    "AmeriCup FIBA":                       75,
    "AfroBasket FIBA":                     75,
    "FIBA Asia Cup":                       75,
    "Coppa Intercontinentale FIBA":        70,
    "Qualificazioni Olimpiche FIBA":       60,
    "Qualificazioni Mondiali FIBA":        55,
    "Qualificazioni EuroBasket":           45,
    "FIBA Europe Cup (Nazionale)":         40,
    "Torneo Nazionale Invitational":       25,
    "Preparazione / Amichevole Nazioni":   10,
}

# Competizioni per GIOVANILI (club e nazioni)
PRESTIGE_YOUTH = {
    "Mondiali FIBA U19":                  100,
    "Mondiali FIBA U17":                   90,
    "EuroBasket U20":                      85,
    "EuroBasket U18":                      80,
    "EuroBasket U16":                      70,
    "Torneo Internazionale Giovanile":     60,
    "Playoff Campionato Naz. Giovanile":   55,
    "Campionato Nazionale U20":            45,
    "Campionato Nazionale U18":            40,
    "Campionato Nazionale U16":            35,
    "Lega Giovanile Club":                 30,
    "Coppa Nazionale Giovanile":           20,
    "Preparazione Giovanile":              10,
}

# Competizioni per GIOVANILI CLUB (NCAA, NGT, leghe giovanili club)
PRESTIGE_YOUTH_CLUB = {
    "adidas Next Generation Tournament":  100,
    "NCAA Division I":                     95,
    "NBA Academy Games":                   85,
    "EYBL (European Youth Basketball)":    80,
    "Lega Basket U19 Top":                 75,
    "Liga ACB Junior":                     70,
    "Basketball Champions League U20":     65,
    "JBA / Junior Basketball Association": 60,
    "Playoff Campionato Naz. U20 Club":    55,
    "Campionato Nazionale U20 Club":       45,
    "Campionato Nazionale U18 Club":       38,
    "Campionato Nazionale U16 Club":       30,
    "Lega Giovanile Club":                 20,
    "Coppa Club Giovanile":                12,
    "Torneo Internazionale Club Youth":     6,
}

# Competizioni per CLUB FEMMINILI
PRESTIGE_WOMEN_CLUB = {
    "WNBA Playoffs":                        100,
    "EuroLeague Women Final Four":           95,
    "Olimpiadi / Mondiali FIBA Women":       90,
    "WNBA Regular Season":                   85,
    "EuroLeague Women":                      80,
    "WNBA Commissioner's Cup":               72,
    "FIBA Champions League Women":           68,
    "EuroCup Women":                         62,
    "SuperCup EuroLeague Women":             55,
    "Playoff Campionato Femm. Top":          50,
    "Campionato Femminile Top (A1)":         42,
    "Liga Femenina / WBBL / Ligue Féminine": 35,
    "Coppa Intercontinentale FIBA Women":    30,
    "Coppa Nazionale Femminile":             25,
    "Supercoppa Femminile":                  18,
    "WNBA Summer League":                     8,
    "Preparazione / Amichevole Club Femm.":   4,
}

# Alias per compatibilità con tutti i moduli
MAN_CLUB_BONUSES      = PRESTIGE_2027
MAN_NAZIONI_BONUSES   = PRESTIGE_NAZIONI
MAN_BONUSES           = PRESTIGE_2027
WOMEN_CLUB_BONUSES    = PRESTIGE_WOMEN_CLUB
WOMEN_NAZIONI_BONUSES = PRESTIGE_NAZIONI
WOMEN_BONUSES         = PRESTIGE_WOMEN_CLUB
YOUTH_BONUSES         = PRESTIGE_YOUTH_CLUB
YOUTH_NAZIONI_BONUSES = PRESTIGE_YOUTH

# ══════════════════════════════════════════════════════════════
#  COLORI & ARTICOLI WIKIPEDIA PER ICONE COMPETIZIONI
# ══════════════════════════════════════════════════════════════
COMP_COLORS = {
    "NBA Playoffs":                    ("#17408B", "#C9082A"),
    "NBA Europe League Playoffs":       ("#1d428a", "#FF6B35"),
    "Olimpiadi / Mondiali FIBA":        ("#003087", "#EE1C25"),
    "EuroLeague Final Four":            ("#C8102E", "#FFFFFF"),
    "NBA Regular Season":               ("#17408B", "#C9082A"),
    "NBA Europe League RS":             ("#1d428a", "#FF6B35"),
    "EuroLeague":                       ("#C8102E", "#FFFFFF"),
    "SuperCup EuroLeague":              ("#C8102E", "#FFD700"),
    "Playoff Campionato Nazionale Top": ("#1A7F37", "#FFD700"),
    "NBA Cup":                          ("#17408B", "#FFD700"),
    "Campionato Nazionale Top":         ("#1A7F37", "#FFFFFF"),
    "EuroCup":                          ("#E8751A", "#FFFFFF"),
    "Coppa Intercontinentale FIBA":     ("#003087", "#FFD700"),
    "Coppa Nazionale":                  ("#CC0000", "#FFD700"),
    "BCL / FIBA Europe Cup":            ("#1A3E6F", "#FFFFFF"),
    "Supercoppa Nazionale":             ("#CC6600", "#FFFFFF"),
    "NBA Summer League":                ("#17408B", "#4CAF50"),
    "NBA Europe Games":                 ("#17408B", "#FFD700"),
    "NBA Preseason":                    ("#444444", "#AAAAAA"),
    # ── Nazioni ──
    "Olimpiadi":                          ("#003087", "#FFD700"),
    "Mondiali FIBA":                       ("#003087", "#EE1C25"),
    "Pre-Olimpico FIBA":                   ("#003087", "#FFFFFF"),
    "EuroBasket":                          ("#003399", "#FFD700"),
    "AmeriCup FIBA":                       ("#B22222", "#FFD700"),
    "AfroBasket FIBA":                     ("#006400", "#FFD700"),
    "FIBA Asia Cup":                       ("#CC0000", "#FFD700"),
    "Qualificazioni Olimpiche FIBA":       ("#003087", "#AAAAAA"),
    "Qualificazioni Mondiali FIBA":        ("#003087", "#CCCCCC"),
    "Qualificazioni EuroBasket":           ("#003399", "#AAAAAA"),
    "FIBA Europe Cup (Nazionale)":         ("#1A3E6F", "#FFFFFF"),
    "Torneo Nazionale Invitational":       ("#555555", "#CCCCCC"),
    "Preparazione / Amichevole Nazioni":   ("#333333", "#AAAAAA"),
    # ── Giovanili ──
    "Mondiali FIBA U19":                   ("#003087", "#FFD700"),
    "Mondiali FIBA U17":                   ("#003087", "#FFA500"),
    "EuroBasket U20":                      ("#003399", "#FFD700"),
    "EuroBasket U18":                      ("#003399", "#FFA500"),
    "EuroBasket U16":                      ("#003399", "#AADDFF"),
    "Torneo Internazionale Giovanile":     ("#2E7D32", "#FFD700"),
    "Playoff Campionato Naz. Giovanile":   ("#1A7F37", "#FFD700"),
    "Campionato Nazionale U20":            ("#1A7F37", "#FFFFFF"),
    "Campionato Nazionale U18":            ("#2E7D32", "#FFFFFF"),
    "Campionato Nazionale U16":            ("#388E3C", "#FFFFFF"),
    "Lega Giovanile Club":                 ("#4CAF50", "#FFFFFF"),
    "Coppa Nazionale Giovanile":           ("#CC6600", "#FFFFFF"),
    "Preparazione Giovanile":              ("#444444", "#AAAAAA"),
    # ── Club Femminili ──
    "WNBA Playoffs":                        ("#FF6B9D", "#FFFFFF"),
    "EuroLeague Women Final Four":          ("#C8102E", "#FFFFFF"),
    "Olimpiadi / Mondiali FIBA Women":      ("#003087", "#EE1C25"),
    "WNBA Regular Season":                  ("#FF6B9D", "#FFFFFF"),
    "EuroLeague Women":                     ("#C8102E", "#FFAACC"),
    "WNBA Commissioner's Cup":              ("#CC3377", "#FFD700"),
    "FIBA Champions League Women":          ("#1A3E6F", "#FF9EC4"),
    "EuroCup Women":                        ("#E8751A", "#FFAACC"),
    "SuperCup EuroLeague Women":            ("#C8102E", "#FFD700"),
    "Playoff Campionato Femm. Top":         ("#1A7F37", "#FFD700"),
    "Campionato Femminile Top (A1)":        ("#1A7F37", "#FFAACC"),
    "Liga Femenina / WBBL / Ligue Féminine":("#8B4513", "#FFAACC"),
    "Coppa Intercontinentale FIBA Women":   ("#003087", "#FF9EC4"),
    "Coppa Nazionale Femminile":            ("#CC0000", "#FFD700"),
    "Supercoppa Femminile":                 ("#CC6600", "#FFAACC"),
    "WNBA Summer League":                   ("#FF6B9D", "#4CAF50"),
    "Preparazione / Amichevole Club Femm.": ("#444444", "#AAAAAA"),
}

COMP_WIKI_ARTICLES = {
    "NBA Playoffs":                    "National Basketball Association",
    "NBA Europe League Playoffs":       None,
    "Olimpiadi / Mondiali FIBA":        "FIBA Basketball World Cup",
    "EuroLeague Final Four":            "EuroLeague Basketball",
    "NBA Regular Season":               "National Basketball Association",
    "NBA Europe League RS":             None,
    "EuroLeague":                       "EuroLeague Basketball",
    "SuperCup EuroLeague":              "EuroLeague Basketball",
    "Playoff Campionato Nazionale Top": None,
    "NBA Cup":                          "NBA Cup",
    "Campionato Nazionale Top":         None,
    "EuroCup":                          "EuroCup Basketball",
    "Coppa Intercontinentale FIBA":     "FIBA Intercontinental Cup",
    "Coppa Nazionale":                  None,
    "BCL / FIBA Europe Cup":            "Basketball Champions League",
    "Supercoppa Nazionale":             None,
    "NBA Summer League":                "NBA Summer League",
    "NBA Europe Games":                 "NBA Global Games",
    "NBA Preseason":                    "NBA preseason",
    # ── Nazioni ──
    "Olimpiadi":                          "Basketball at the Summer Olympics",
    "Mondiali FIBA":                       "FIBA Basketball World Cup",
    "Pre-Olimpico FIBA":                   None,
    "EuroBasket":                          "EuroBasket",
    "AmeriCup FIBA":                       "FIBA AmeriCup",
    "AfroBasket FIBA":                     "FIBA AfroBasket",
    "FIBA Asia Cup":                       "FIBA Asia Cup",
    "Qualificazioni Olimpiche FIBA":       None,
    "Qualificazioni Mondiali FIBA":        None,
    "Qualificazioni EuroBasket":           None,
    "FIBA Europe Cup (Nazionale)":         "FIBA Europe Cup",
    "Torneo Nazionale Invitational":       None,
    "Preparazione / Amichevole Nazioni":   None,
    # ── Giovanili ──
    "Mondiali FIBA U19":                   "FIBA Under-19 Basketball World Cup",
    "Mondiali FIBA U17":                   "FIBA Under-17 Basketball World Cup",
    "EuroBasket U20":                      "FIBA U20 European Basketball Championship",
    "EuroBasket U18":                      "FIBA U18 European Basketball Championship",
    "EuroBasket U16":                      "FIBA U16 European Basketball Championship",
    "Torneo Internazionale Giovanile":     None,
    "Playoff Campionato Naz. Giovanile":   None,
    "Campionato Nazionale U20":            None,
    "Campionato Nazionale U18":            None,
    "Campionato Nazionale U16":            None,
    "Lega Giovanile Club":                 None,
    "Coppa Nazionale Giovanile":           None,
    "Preparazione Giovanile":              None,
    # ── Club Femminili ──
    "WNBA Playoffs":                        "Women's National Basketball Association",
    "EuroLeague Women Final Four":          "EuroLeague Women",
    "Olimpiadi / Mondiali FIBA Women":      "Basketball at the Summer Olympics",
    "WNBA Regular Season":                  "Women's National Basketball Association",
    "EuroLeague Women":                     "EuroLeague Women",
    "WNBA Commissioner's Cup":              "WNBA Commissioner's Cup",
    "FIBA Champions League Women":          "FIBA Women's Basketball Champions League",
    "EuroCup Women":                        "EuroCup Women",
    "SuperCup EuroLeague Women":            "EuroLeague Women",
    "Playoff Campionato Femm. Top":         None,
    "Campionato Femminile Top (A1)":        None,
    "Liga Femenina / WBBL / Ligue Féminine":None,
    "Coppa Intercontinentale FIBA Women":   None,
    "Coppa Nazionale Femminile":            None,
    "Supercoppa Femminile":                 None,
    "WNBA Summer League":                   "Women's National Basketball Association",
    "Preparazione / Amichevole Club Femm.": None,
}

_comp_icon_cache: dict = {}

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
    """Punteggio = solo punti della competizione (Prestigio 2027)."""
    return float(player.get("bonus", 0))


# ══════════════════════════════════════════════════════════════
#  ICONE COMPETIZIONI  +  FOTO GIOCATORI / TEAM
# ══════════════════════════════════════════════════════════════
def _sanitize_name(name: str) -> str:
    """Nome sicuro per filesystem."""
    return re.sub(r"[^a-z0-9_\-]", "", name.strip().lower().replace(" ", "_"))[:50] or "unnamed"


def _make_comp_badge(comp: str, size: int = 26):
    """Crea un badge PIL circolare colorato con sigla competizione."""
    if not PIL_AVAILABLE:
        return None
    try:
        from PIL import ImageDraw, ImageFont
        def h2rgb(h):
            h = h.lstrip("#")
            return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
        bg_hex, fg_hex = COMP_COLORS.get(comp, ("#333333", "#FFFFFF"))
        bg_rgb, fg_rgb = h2rgb(bg_hex), h2rgb(fg_hex)
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        draw.ellipse([0, 0, size - 1, size - 1], fill=(*bg_rgb, 255))
        words = comp.split()
        abbr = "".join(w[0].upper() for w in words if w and w[0].isalpha())[:3]
        fs = max(6, size // 3)
        font = None
        for fp in ("C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/arial.ttf",
                   "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                   "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
            if os.path.exists(fp):
                try:
                    font = ImageFont.truetype(fp, fs)
                    break
                except Exception:
                    pass
        if font is None:
            font = ImageFont.load_default()
        bbox = draw.textbbox((0, 0), abbr, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        draw.text(((size - tw) // 2, (size - th) // 2 - 1),
                  abbr, fill=(*fg_rgb, 255), font=font)
        return img
    except Exception:
        return None


def _load_comp_logo_file(comp: str, size: int = 26):
    """Carica logo competizione da file locale (se già scaricato)."""
    if not PIL_AVAILABLE:
        return None
    safe = _sanitize_name(comp)
    for ext in (".png", ".jpg", ".jpeg", ".webp"):
        path = os.path.join(COMP_LOGOS_DIR, safe + ext)
        if os.path.exists(path):
            try:
                img = Image.open(path).convert("RGBA")
                img.thumbnail((size, size), Image.LANCZOS)
                sq = Image.new("RGBA", (size, size), (0, 0, 0, 0))
                sq.paste(img, ((size - img.width) // 2, (size - img.height) // 2), img)
                return sq
            except Exception:
                pass
    return None


def get_comp_icon(comp: str, size: int = 26):
    """Restituisce PhotoImage icon per la competizione (badge o logo scaricato)."""
    key = (comp, size)
    if key in _comp_icon_cache:
        return _comp_icon_cache.get(key)
    if not PIL_AVAILABLE:
        return None
    img = _load_comp_logo_file(comp, size) or _make_comp_badge(comp, size)
    if img is None:
        return None
    try:
        tk_img = ImageTk.PhotoImage(img)
        _comp_icon_cache[key] = tk_img
        return tk_img
    except Exception:
        return None


def _download_comp_logos_bg():
    """Scarica loghi competizioni: prima prova URL diretto, poi Wikipedia REST API."""
    os.makedirs(COMP_LOGOS_DIR, exist_ok=True)
    ctx = ssl.create_default_context()
    ua  = "HoopIQ/2.0 (basketball scouting desktop app)"

    DIRECT_LOGOS = {
        "NBA Playoffs":        "https://upload.wikimedia.org/wikipedia/en/0/03/National_Basketball_Association_logo.svg",
        "NBA Regular Season":  "https://upload.wikimedia.org/wikipedia/en/0/03/National_Basketball_Association_logo.svg",
        "NBA Cup":             "https://upload.wikimedia.org/wikipedia/en/thumb/6/6a/NBA_In-Season_Tournament_trophy.png/120px-NBA_In-Season_Tournament_trophy.png",
        "EuroLeague":          "https://upload.wikimedia.org/wikipedia/en/thumb/e/ec/EuroLeague_Basketball.png/120px-EuroLeague_Basketball.png",
        "EuroLeague Final Four":"https://upload.wikimedia.org/wikipedia/en/thumb/e/ec/EuroLeague_Basketball.png/120px-EuroLeague_Basketball.png",
        "EuroCup":             "https://upload.wikimedia.org/wikipedia/en/thumb/d/d7/EuroCup_Basketball_logo.png/120px-EuroCup_Basketball_logo.png",
        "WNBA Playoffs":       "https://upload.wikimedia.org/wikipedia/en/thumb/4/49/WNBA.png/120px-WNBA.png",
        "WNBA Regular Season": "https://upload.wikimedia.org/wikipedia/en/thumb/4/49/WNBA.png/120px-WNBA.png",
        "EuroLeague Women":    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/EuroLeague_Women_logo.png/120px-EuroLeague_Women_logo.png",
        "EuroLeague Women Final Four":"https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/EuroLeague_Women_logo.png/120px-EuroLeague_Women_logo.png",
        "Mondiali FIBA":       "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/FIBA_Basketball_World_Cup_logo.png/120px-FIBA_Basketball_World_Cup_logo.png",
        "EuroBasket":          "https://upload.wikimedia.org/wikipedia/commons/thumb/3/34/EuroBasket_logo.png/120px-EuroBasket_logo.png",
        "BCL / FIBA Europe Cup":"https://upload.wikimedia.org/wikipedia/en/thumb/3/3e/Basketball_Champions_League_logo.png/120px-Basketball_Champions_League_logo.png",
        "FIBA Champions League Women":"https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Basketball_Champions_League_logo.png/120px-Basketball_Champions_League_logo.png",
    }

    for comp, article in COMP_WIKI_ARTICLES.items():
        safe = _sanitize_name(comp)
        if any(os.path.exists(os.path.join(COMP_LOGOS_DIR, safe + ext))
               for ext in (".png", ".jpg", ".jpeg")):
            continue

        # 1. URL diretto (loghi ufficiali stabili)
        direct_url = DIRECT_LOGOS.get(comp)
        if direct_url:
            try:
                req = urllib.request.Request(direct_url, headers={"User-Agent": ua})
                with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
                    img_bytes = r.read()
                dest = os.path.join(COMP_LOGOS_DIR, safe + ".png")
                with open(dest, "wb") as f:
                    f.write(img_bytes)
                for sz in (22, 24, 26, 28, 32):
                    _comp_icon_cache.pop((comp, sz), None)
                continue
            except Exception:
                pass

        if not article:
            continue

        # 2. Wikipedia REST API (thumbnail 200px)
        try:
            title = _url_quote(article.replace(" ", "_"))
            sm_url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{title}"
            req = urllib.request.Request(sm_url, headers={"User-Agent": ua})
            with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
                data = json.loads(r.read().decode("utf-8"))
            thumb = (data.get("originalimage") or data.get("thumbnail") or {}).get("source")
            if not thumb:
                api_url = (
                    "https://en.wikipedia.org/w/api.php?"
                    f"action=query&titles={_url_quote(article)}"
                    "&prop=pageimages&format=json&pithumbsize=200"
                )
                req2 = urllib.request.Request(api_url, headers={"User-Agent": ua})
                with urllib.request.urlopen(req2, timeout=10, context=ctx) as r2:
                    d2 = json.loads(r2.read().decode("utf-8"))
                pages = d2.get("query", {}).get("pages", {})
                thumb = next(iter(pages.values()), {}).get("thumbnail", {}).get("source")
            if not thumb:
                continue
            req3 = urllib.request.Request(thumb, headers={"User-Agent": ua})
            with urllib.request.urlopen(req3, timeout=10, context=ctx) as r3:
                img_bytes = r3.read()
            dest = os.path.join(COMP_LOGOS_DIR, safe + ".png")
            with open(dest, "wb") as f:
                f.write(img_bytes)
            for sz in (22, 24, 26, 28, 32):
                _comp_icon_cache.pop((comp, sz), None)
        except Exception:
            pass  # Fallback badge utilizzato


threading.Thread(target=_download_comp_logos_bg, daemon=True).start()


def _load_photo_preview(path: str, size: int = 150):
    """Carica un'immagine come PhotoImage thumbnail proporzionato (per anteprima form)."""
    if not PIL_AVAILABLE or not path or not os.path.exists(path):
        return None
    try:
        img = Image.open(path).convert("RGBA")
        img.thumbnail((size, size), Image.LANCZOS)
        # Centro su sfondo trasparente/scuro proporzionato
        bg = Image.new("RGBA", (size, size), (26, 26, 26, 0))
        bg.paste(img, ((size - img.width) // 2, (size - img.height) // 2),
                 img if img.mode == "RGBA" else None)
        return ImageTk.PhotoImage(bg)
    except Exception:
        return None


# ══════════════════════════════════════════════════════════════
#  RICERCA IMMAGINI (DuckDuckGo) + CONFIG AI
# ══════════════════════════════════════════════════════════════

def _ddg_image_urls(query: str, count: int = 8) -> list:
    """
    Cerca loghi/foto team via ESPN CDN (NBA) + Wikipedia REST API + Commons.
    """
    ctx = ssl.create_default_context()
    ua  = "HoopIQ/2.0 (basketball scouting desktop app)"

    def _get(url: str) -> dict:
        req = urllib.request.Request(url, headers={"User-Agent": ua})
        with urllib.request.urlopen(req, timeout=12, context=ctx) as r:
            return json.loads(r.read().decode("utf-8"))

    def _head_ok(url: str) -> bool:
        """Verifica rapidamente che un URL immagine risponda 200."""
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": ua}, method="HEAD")
            with urllib.request.urlopen(req, timeout=6, context=ctx) as r:
                return r.status == 200
        except Exception:
            return False

    short_q = re.sub(
        r'\s*\b(basketball player|basketball|player|team|nba|club)\b\s*',
        ' ', query, flags=re.I).strip()

    urls: list = []
    SKIP = ("flag", "map", "icon", "seal", "coat", "badge", "banner",
            "stub", "silhouette", "signature", "placeholder",
            "default", "question", "award", "trophy")

    # ══ 0. ESPN CDN – loghi NBA diretti (nessuna chiamata API) ══════════
    # Mappa nomi team → abbreviazione ESPN
    NBA_ESPN = {
        "atlanta hawks": "atl", "boston celtics": "bos",
        "brooklyn nets": "bkn", "charlotte hornets": "cha",
        "chicago bulls": "chi", "cleveland cavaliers": "cle",
        "dallas mavericks": "dal", "denver nuggets": "den",
        "detroit pistons": "det", "golden state warriors": "gsw",
        "houston rockets": "hou", "indiana pacers": "ind",
        "los angeles clippers": "lac", "los angeles lakers": "lal",
        "la clippers": "lac", "la lakers": "lal",
        "memphis grizzlies": "mem", "miami heat": "mia",
        "milwaukee bucks": "mil", "minnesota timberwolves": "min",
        "new orleans pelicans": "no", "new york knicks": "ny",
        "oklahoma city thunder": "okc", "orlando magic": "orl",
        "philadelphia 76ers": "phi", "phoenix suns": "phx",
        "portland trail blazers": "por", "sacramento kings": "sac",
        "san antonio spurs": "sas", "toronto raptors": "tor",
        "utah jazz": "utah", "washington wizards": "wsh",
    }
    sq_lower = short_q.lower()
    espn_abbr = None
    for name, abbr in NBA_ESPN.items():
        if name in sq_lower or sq_lower in name:
            espn_abbr = abbr
            break
    if espn_abbr:
        espn_url = f"https://a.espncdn.com/i/teamlogos/nba/500/{espn_abbr}.png"
        if _head_ok(espn_url):
            urls.append(espn_url)
            # Also try dark/alternate version
            alt = f"https://a.espncdn.com/i/teamlogos/nba/500-dark/{espn_abbr}.png"
            if _head_ok(alt):
                urls.append(alt)

    if len(urls) >= count:
        return urls[:count]

    # ══ 1. Wikipedia REST API – foto/logo principale (un solo call) ═══════
    try:
        # 1a. Trova l'articolo
        sr = _get("https://en.wikipedia.org/w/api.php?"
                  f"action=opensearch&search={_url_quote(short_q)}"
                  "&limit=1&format=json")
        if sr and len(sr) > 1 and sr[1]:
            title = sr[1][0].replace(" ", "_")
            # 1b. REST summary → immagine principale diretta
            sm = _get(f"https://en.wikipedia.org/api/rest_v1/page/summary/{_url_quote(title)}")
            orig = sm.get("originalimage", {}).get("source", "")
            thumb = sm.get("thumbnail", {}).get("source", "")
            for u in (orig, thumb):
                if u and u not in urls:
                    urls.append(u)
            # 1c. Media list → immagini aggiuntive dalla pagina
            try:
                ml = _get(f"https://en.wikipedia.org/api/rest_v1/page/media-list/{_url_quote(title)}")
                for item in ml.get("items", []):
                    if item.get("type") != "image":
                        continue
                    srcset = item.get("srcset", [])
                    src = (srcset[-1].get("src", "") if srcset
                           else item.get("src", ""))
                    if src.startswith("//"):
                        src = "https:" + src
                    t_lower = src.lower()
                    if (src and src.startswith("http")
                            and src not in urls
                            and any(t_lower.endswith(e) for e in
                                    (".jpg", ".jpeg", ".png", ".svg"))
                            and not any(k in t_lower for k in SKIP)):
                        urls.append(src)
                    if len(urls) >= count:
                        break
            except Exception:
                pass
    except Exception:
        pass

    if len(urls) >= 2:
        return urls[:count]

    # ══ 2. Wikimedia Commons – categoria diretta ══════════════════════════
    try:
        cat = _get("https://commons.wikimedia.org/w/api.php?"
                   f"action=query&list=categorymembers"
                   f"&cmtitle=Category:{_url_quote(short_q)}"
                   "&cmnamespace=6&cmlimit=20&format=json")
        titles = [m["title"] for m in cat.get("query", {})
                  .get("categorymembers", [])
                  if m["title"].lower().endswith((".jpg", ".jpeg", ".png"))
                  and not any(k in m["title"].lower() for k in SKIP)]
        for chunk in [titles[i:i+5] for i in range(0, len(titles), 5)]:
            ii = _get("https://commons.wikimedia.org/w/api.php?"
                      f"action=query&titles={_url_quote('|'.join(chunk))}"
                      "&prop=imageinfo&iiprop=url&format=json")
            for pg in ii.get("query", {}).get("pages", {}).values():
                inf = pg.get("imageinfo", [])
                if inf:
                    u = inf[0].get("url", "")
                    if u and u not in urls:
                        urls.append(u)
            if len(urls) >= count:
                break
    except Exception:
        pass

    # ══ 3. Commons – ricerca per titolo file ══════════════════════════════
    if len(urls) < 2:
        try:
            ts = _get("https://commons.wikimedia.org/w/api.php?"
                      f"action=query&list=search&srsearch={_url_quote(short_q)}"
                      "&srnamespace=6&srwhat=title&srlimit=15&srprop=title&format=json")
            hits = [r["title"] for r in ts.get("query", {}).get("search", [])
                    if r["title"].lower().endswith((".jpg", ".jpeg", ".png"))
                    and not any(k in r["title"].lower() for k in SKIP)]
            for chunk in [hits[i:i+5] for i in range(0, len(hits), 5)]:
                ii = _get("https://commons.wikimedia.org/w/api.php?"
                          f"action=query&titles={_url_quote('|'.join(chunk))}"
                          "&prop=imageinfo&iiprop=url&format=json")
                for pg in ii.get("query", {}).get("pages", {}).values():
                    inf = pg.get("imageinfo", [])
                    if inf:
                        u = inf[0].get("url", "")
                        if u and u not in urls:
                            urls.append(u)
                if len(urls) >= count:
                    break
        except Exception:
            pass

    return urls[:count]
    ua = "HoopIQ/2.0 (basketball scouting desktop app)"
    urls: list = []

    # Rimuovi parole generiche dalla query per la ricerca su Wikipedia/Commons
    short_q = re.sub(
        r'\s*\b(basketball player|basketball|player|team|nba|club)\b\s*',
        ' ', query, flags=re.I).strip()

    def _call(base: str, params: str) -> dict:
        req = urllib.request.Request(
            f"{base}?{params}", headers={"User-Agent": ua})
        with urllib.request.urlopen(req, timeout=8) as r:
            return json.loads(r.read().decode("utf-8"))

    def _imageinfo_urls(base: str, titles: list) -> list:
        """Risolve una lista di File: titles in URL immagine reali."""
        result = []
        for chunk in [titles[i:i+5] for i in range(0, len(titles), 5)]:
            try:
                d = _call(base,
                          f"action=query&titles={_url_quote('|'.join(chunk))}"
                          "&prop=imageinfo&iiprop=url&format=json")
                for page in d.get("query", {}).get("pages", {}).values():
                    info = page.get("imageinfo", [])
                    if info:
                        u = info[0].get("url", "")
                        if u and u not in urls and u not in result:
                            result.append(u)
            except Exception:
                pass
        return result

    base_wp  = "https://en.wikipedia.org/w/api.php"
    base_com = "https://commons.wikimedia.org/w/api.php"

    SKIP = ("flag", "map", "icon", "seal", "coat", "badge",
            "banner", "stub", "commons", "silhouette", "signature",
            "placeholder", "default", "question", "award", "trophy")

    def _is_photo(title: str) -> bool:
        t = title.lower()
        return (t.endswith((".jpg", ".jpeg", ".png"))
                and not any(kw in t for kw in SKIP))

    # ══ 1. Wikipedia pageimages – SOLO foto principale articolo ══════════
    try:
        sr = _call(base_wp,
                   f"action=opensearch&search={_url_quote(short_q)}"
                   "&limit=1&format=json")
        if sr and len(sr) > 1 and sr[1]:
            title = sr[1][0]
            pi = _call(base_wp,
                       f"action=query&titles={_url_quote(title)}"
                       "&prop=pageimages&pithumbsize=600&piprop=thumbnail&format=json")
            pages = pi.get("query", {}).get("pages", {})
            thumb = next(iter(pages.values()), {}).get("thumbnail", {}).get("source")
            if thumb:
                urls.append(thumb)
    except Exception:
        pass

    # ══ 2. Commons – categoria diretta del giocatore ═════════════════════
    try:
        cat = _call(base_com,
                    f"action=query&list=categorymembers"
                    f"&cmtitle=Category:{_url_quote(short_q)}"
                    "&cmnamespace=6&cmlimit=20&format=json")
        members = cat.get("query", {}).get("categorymembers", [])
        cat_titles = [m["title"] for m in members if _is_photo(m["title"])]
        urls += [u for u in _imageinfo_urls(base_com, cat_titles) if u not in urls]
    except Exception:
        pass

    # ══ 3. Commons – ricerca per TITOLO file (non full-text) ═════════════
    if len(urls) < count:
        try:
            ts = _call(base_com,
                       f"action=query&list=search&srsearch={_url_quote(short_q)}"
                       "&srnamespace=6&srwhat=title&srlimit=20&srprop=title&format=json")
            title_hits = [
                r["title"] for r in ts.get("query", {}).get("search", [])
                if _is_photo(r["title"])
            ]
            urls += [u for u in _imageinfo_urls(base_com, title_hits) if u not in urls]
        except Exception:
            pass

    return urls[:count] if urls else []
    ua = "HoopIQ/2.0 (basketball scouting desktop app)"
    urls: list = []

    # ══ Helper ════════════════════════════════════════════════════════════
    def _wiki_api(base: str, params: str) -> dict:
        r = urllib.request.Request(f"{base}?{params}", headers={"User-Agent": ua})
        with urllib.request.urlopen(r, timeout=8) as resp:
            return json.loads(resp.read().decode("utf-8"))

    # Nome breve: rimuove suffissi generici per migliorare la ricerca
    short_q = re.sub(r'\b(basketball player|basketball|player|team)\b', '',
                     query, flags=re.I).strip()

    # ══ 1. Wikipedia pageimages (SOLO foto principale) ═══════════════════
    try:
        base_wiki = "https://en.wikipedia.org/w/api.php"
        sr = _wiki_api(base_wiki,
                       f"action=opensearch&search={_url_quote(short_q)}&limit=1&format=json")
        if sr and len(sr) > 1 and sr[1]:
            title = sr[1][0]
            pi = _wiki_api(base_wiki,
                           f"action=query&titles={_url_quote(title)}"
                           "&prop=pageimages&pithumbsize=600&piprop=thumbnail&format=json")
            pages = pi.get("query", {}).get("pages", {})
            thumb = next(iter(pages.values()), {}).get("thumbnail", {}).get("source")
            if thumb:
                urls.append(thumb)
    except Exception:
        pass

    # ══ 2. Wikimedia Commons (cerca file per nome atleta) ════════════════
    try:
        base_com = "https://commons.wikimedia.org/w/api.php"
        com = _wiki_api(base_com,
                        f"action=query&list=search&srsearch={_url_quote(short_q)}"
                        "&srnamespace=6&srlimit=20&srprop=title&format=json")
        candidates = [
            r["title"] for r in com.get("query", {}).get("search", [])
            if r["title"].lower().endswith((".jpg", ".jpeg", ".png"))
            and not any(kw in r["title"].lower()
                        for kw in ("logo", "flag", "map", "icon", "seal",
                                   "coat", "badge", "banner", "stub", "commons"))
        ]
        if candidates:
            for chunk in [candidates[i:i+5] for i in range(0, min(len(candidates), 15), 5)]:
                tp = "|".join(chunk)
                ii = _wiki_api(base_com,
                               f"action=query&titles={_url_quote(tp)}"
                               "&prop=imageinfo&iiprop=url&format=json")
                for page in ii.get("query", {}).get("pages", {}).values():
                    info = page.get("imageinfo", [])
                    if info:
                        u = info[0].get("url", "")
                        if u and u not in urls:
                            urls.append(u)
                if len(urls) >= count:
                    break
    except Exception:
        pass

    if urls:
        return urls[:count]

    # ══ 3. Bing fallback ══════════════════════════════════════════════════
    try:
        bing_ua = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) "
                   "Chrome/124.0.0.0 Safari/537.36")
        bing_url = (f"https://www.bing.com/images/search?"
                    f"q={_url_quote(query)}&form=HDRSC2&first=1")
        req = urllib.request.Request(
            bing_url, headers={"User-Agent": bing_ua, "Accept-Language": "en-US,en;q=0.9"})
        with urllib.request.urlopen(req, timeout=12) as r:
            html = r.read().decode("utf-8", errors="replace")
        bing_urls: list = []
        for raw_m in re.findall(r'class="iusc"[^>]+?m="([^"]+)"', html):
            try:
                u = json.loads(raw_m).get("murl", "")
                if u.startswith("http"):
                    bing_urls.append(u)
            except Exception:
                pass
        if not bing_urls:
            bing_urls = re.findall(
                r'murl&quot;:&quot;(https?://[^&"<>]+?)&quot;', html)
        if bing_urls:
            return bing_urls[:count]
    except Exception:
        pass

    return []
    ua = "HoopIQ/2.0 (basketball scouting desktop app)"
    base_wiki = "https://en.wikipedia.org/w/api.php"

    # ══ 1. Wikipedia ══════════════════════════════════════════════════════
    try:
        # a) Trova l'articolo con opensearch
        search_url = (f"{base_wiki}?action=opensearch"
                      f"&search={_url_quote(query)}&limit=1&format=json")
        req = urllib.request.Request(search_url, headers={"User-Agent": ua})
        with urllib.request.urlopen(req, timeout=8) as r:
            results = json.loads(r.read().decode("utf-8"))

        urls: list = []
        if results and len(results) > 1 and results[1]:
            title = results[1][0]

            # b) Foto principale (pageimages, alta risoluzione)
            thumb_url = (f"{base_wiki}?action=query"
                         f"&titles={_url_quote(title)}"
                         "&prop=pageimages&pithumbsize=600&format=json")
            req2 = urllib.request.Request(thumb_url, headers={"User-Agent": ua})
            with urllib.request.urlopen(req2, timeout=8) as r2:
                data = json.loads(r2.read().decode("utf-8"))
            pages = data.get("query", {}).get("pages", {})
            thumb = next(iter(pages.values()), {}).get("thumbnail", {}).get("source")
            if thumb:
                urls.append(thumb)

            # c) Tutte le immagini dell'articolo (filtrate: solo jpg/png foto)
            imgs_url = (f"{base_wiki}?action=query"
                        f"&titles={_url_quote(title)}"
                        "&prop=images&imlimit=30&format=json")
            req3 = urllib.request.Request(imgs_url, headers={"User-Agent": ua})
            with urllib.request.urlopen(req3, timeout=8) as r3:
                data3 = json.loads(r3.read().decode("utf-8"))
            pages3 = data3.get("query", {}).get("pages", {})
            all_imgs = next(iter(pages3.values()), {}).get("images", [])

            SKIP_KW = ("flag", "logo", "icon", "map", "seal", "coat", "badge",
                       "banner", "award", "trophy", "ribbon", "stub", "wiki",
                       "commons", "portal", "question", "star", "globe")
            photo_titles = [
                img["title"] for img in all_imgs
                if img["title"].lower().endswith((".jpg", ".jpeg", ".png"))
                and not any(kw in img["title"].lower() for kw in SKIP_KW)
            ][:8]

            if photo_titles:
                # Recupera URL reali con imageinfo
                for chunk in [photo_titles[i:i+5] for i in range(0, len(photo_titles), 5)]:
                    tp = "|".join(chunk)
                    info_url = (f"{base_wiki}?action=query"
                                f"&titles={_url_quote(tp)}"
                                "&prop=imageinfo&iiprop=url&format=json")
                    req4 = urllib.request.Request(info_url, headers={"User-Agent": ua})
                    with urllib.request.urlopen(req4, timeout=8) as r4:
                        data4 = json.loads(r4.read().decode("utf-8"))
                    for page in data4.get("query", {}).get("pages", {}).values():
                        info = page.get("imageinfo", [])
                        if info:
                            u = info[0].get("url", "")
                            if u and u not in urls:
                                urls.append(u)

        if urls:
            return urls[:count]
    except Exception:
        pass

    # ══ 2. Bing fallback ══════════════════════════════════════════════════
    try:
        bing_ua = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) "
                   "Chrome/124.0.0.0 Safari/537.36")
        bing_url = (f"https://www.bing.com/images/search?"
                    f"q={_url_quote(query)}&form=HDRSC2&first=1")
        req = urllib.request.Request(
            bing_url,
            headers={"User-Agent": bing_ua,
                     "Accept-Language": "en-US,en;q=0.9"})
        with urllib.request.urlopen(req, timeout=12) as r:
            html = r.read().decode("utf-8", errors="replace")

        # Estrai URL da attributo m (JSON) dei tag <a class="iusc">
        bing_urls: list = []
        for raw_m in re.findall(r'class="iusc"[^>]+?m="([^"]+)"', html):
            try:
                m_data = json.loads(raw_m)
                u = m_data.get("murl") or m_data.get("turl")
                if u and u.startswith("http"):
                    bing_urls.append(u)
            except Exception:
                pass
        # Fallback regex diretto
        if not bing_urls:
            raw_list = re.findall(
                r'&quot;murl&quot;:&quot;(https?://[^&"<>]+?)&quot;', html)
            bing_urls = [u for u in raw_list if u.startswith("http")]

        if bing_urls:
            return bing_urls[:count]
    except Exception:
        pass

    return []


def _fetch_bytes(url: str, timeout: int = 10) -> bytes | None:
    """Scarica bytes da URL; None in caso di errore."""
    try:
        ua = "Mozilla/5.0 HoopIQ/2.0"
        req = urllib.request.Request(url, headers={"User-Agent": ua})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read()
    except Exception:
        return None


# ── AI Config (OpenAI-compatible) ──────────────────────────────────────────

def _ai_cfg_load() -> dict:
    """Legge la configurazione AI dal file locale."""
    if os.path.exists(FILE_AI_CONFIG):
        try:
            with open(FILE_AI_CONFIG, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"api_key": "", "base_url": "https://api.openai.com/v1",
            "model": "gpt-4o-mini"}


def _ai_cfg_save(cfg: dict) -> None:
    os.makedirs(ASSETS_DIR, exist_ok=True)
    with open(FILE_AI_CONFIG, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)


def _ai_free_chat_completion(messages: list) -> str:
    """Chiama Pollinations AI — gratuito, nessuna API key richiesta."""
    ctx = ssl.create_default_context()
    payload = json.dumps({
        "messages": messages,
        "model":    "openai",
        "seed":     -1,
        "private":  True,
    }).encode("utf-8")
    req = urllib.request.Request(
        "https://text.pollinations.ai/",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "User-Agent":   "HoopIQ/2.0 basketball-scouting",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
        return r.read().decode("utf-8").strip()


def _ai_chat_completion(messages: list, cfg: dict) -> str:
    """Chiama l'API OpenAI-compatible; restituisce il testo della risposta."""
    payload = json.dumps({
        "model": cfg.get("model", "gpt-4o-mini"),
        "messages": messages,
        "max_tokens": 600,
        "temperature": 0.7,
    }).encode("utf-8")
    base = cfg.get("base_url", "https://api.openai.com/v1").rstrip("/")
    req = urllib.request.Request(
        f"{base}/chat/completions",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {cfg['api_key']}",
        },
        method="POST",
    )
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
        resp = json.loads(r.read().decode("utf-8"))
    return resp["choices"][0]["message"]["content"].strip()

def _make_ai_robot_logo(size: int = 64) -> "ImageTk.PhotoImage | None":
    """Logo HoopIQ AI: pallone basket stilizzato con elementi circuito AI."""
    if not PIL_AVAILABLE:
        return None
    try:
        from PIL import ImageDraw, ImageFont
        S = size
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)

        # ── Sfondo cerchio scuro con bordo luminoso ───────────────
        d.ellipse([0, 0, S - 1, S - 1], fill=(8, 16, 36, 255))
        bw = max(2, S // 20)
        d.ellipse([bw // 2, bw // 2, S - 1 - bw // 2, S - 1 - bw // 2],
                  fill=(0, 0, 0, 0), outline=(50, 120, 240, 200), width=bw)

        # ── Pallone basket (centro, 68% dell'icona) ───────────────
        margin = int(S * 0.16)
        br0, br1 = margin, S - margin
        cx, cy = S // 2, S // 2
        r = (br1 - br0) // 2
        d.ellipse([br0, br0, br1, br1],
                  fill=(215, 78, 14, 255),
                  outline=(255, 148, 60, 255), width=max(1, S // 26))
        # Linea orizzontale
        lw = max(1, S // 19)
        d.line([(br0 + 2, cy), (br1 - 2, cy)], fill=(20, 5, 0, 210), width=lw)
        # Arco verticale centrale
        d.arc([cx - r, br0, cx + r, br1], 80, 100, fill=(20, 5, 0, 210), width=lw)
        # Arco sinistro e destro
        off = int(r * 0.52)
        d.arc([cx - off, br0, cx + off, br1], 258, 78, fill=(20, 5, 0, 210), width=lw)
        d.arc([cx - off, br0, cx + off, br1], 102, 258, fill=(20, 5, 0, 210), width=lw)

        # ── Nodi circuito AI (4 angoli) ───────────────────────────
        node_r  = max(2, S // 13)
        nd_off  = int(S * 0.115)
        node_col = (75, 168, 255, 225)
        line_col = (45, 110, 210, 140)
        nodes = [(nd_off, nd_off), (S - nd_off, nd_off),
                 (nd_off, S - nd_off), (S - nd_off, S - nd_off)]
        for nx, ny in nodes:
            d.line([(cx, cy), (nx, ny)], fill=line_col, width=max(1, S // 30))
            d.ellipse([nx - node_r, ny - node_r, nx + node_r, ny + node_r],
                      fill=node_col, outline=(160, 220, 255, 255), width=1)

        # ── Badge "IQ" in basso a destra ─────────────────────────
        badge_cx = int(S * 0.77)
        badge_cy = int(S * 0.77)
        badge_r  = max(5, int(S * 0.135))
        d.ellipse([badge_cx - badge_r, badge_cy - badge_r,
                   badge_cx + badge_r, badge_cy + badge_r],
                  fill=(18, 65, 190, 245), outline=(110, 175, 255, 255), width=1)
        fs = max(5, badge_r - 1)
        font = None
        for fp in ("C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/arial.ttf",
                   "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                   "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
            if os.path.exists(fp):
                try:
                    font = ImageFont.truetype(fp, fs)
                    break
                except Exception:
                    pass
        if font is None:
            font = ImageFont.load_default()
        try:
            bbox = d.textbbox((0, 0), "IQ", font=font)
            tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        except Exception:
            tw, th = fs * 2, fs
        d.text((badge_cx - tw // 2, badge_cy - th // 2 - 1),
               "IQ", fill=(220, 240, 255, 255), font=font)

        return ImageTk.PhotoImage(img)
    except Exception:
        return None


_AI_ROBOT_LOGO_CACHE: dict = {}


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
#  ESPORTAZIONE PDF
# ══════════════════════════════════════════════════════════════
def _export_table_pdf(title: str, subtitle: str,
                      col_headers: list, rows: list,
                      col_widths: list | None = None,
                      parent_win=None) -> None:
    """Esporta una tabella dati come PDF ben formattato (layout A4 landscape)."""
    from matplotlib.backends.backend_pdf import PdfPages

    path = filedialog.asksaveasfilename(
        parent=parent_win,
        title="Salva classifica PDF",
        defaultextension=".pdf",
        filetypes=[("PDF", "*.pdf"), ("Tutti i file", "*.*")],
        initialfile=(f"HoopIQ_{re.sub(chr(32), '_', title)}_"
                     f"{datetime.now().strftime('%Y%m%d_%H%M')}.pdf"))
    if not path:
        return

    n = len(rows)
    # Altezza pagina dinamica (A4 landscape = 11.69 × 8.27 in, max ~60 righe/pag)
    rows_per_page = 45
    n_pages = max(1, math.ceil(n / rows_per_page)) if n else 1

    cw = col_widths or [1.0 / len(col_headers)] * len(col_headers)

    with PdfPages(path) as pdf:
        for page in range(n_pages):
            chunk = rows[page * rows_per_page: (page + 1) * rows_per_page]
            fig = Figure(figsize=(16.53, 11.69), facecolor="#0d0d0d")
            ax  = fig.add_subplot(111)
            ax.set_facecolor("#0d0d0d")
            ax.axis("off")

            # Intestazione
            ax.text(0.5, 0.98,
                    f"HoopIQ  ·  {title}",
                    transform=ax.transAxes, ha="center", va="top",
                    color="#c8102e", fontsize=18, fontweight="bold",
                    fontfamily="DejaVu Sans")
            ax.text(0.5, 0.935,
                    subtitle,
                    transform=ax.transAxes, ha="center", va="top",
                    color="#aaaaaa", fontsize=11)
            ax.text(0.99, 0.935,
                    datetime.now().strftime("%d/%m/%Y %H:%M"),
                    transform=ax.transAxes, ha="right", va="top",
                    color="#555555", fontsize=9)
            if n_pages > 1:
                ax.text(0.01, 0.935,
                        f"Pag. {page + 1} / {n_pages}",
                        transform=ax.transAxes, ha="left", va="top",
                        color="#555555", fontsize=9)

            if not chunk:
                ax.text(0.5, 0.5, "Nessun dato",
                        transform=ax.transAxes, ha="center", va="center",
                        color="#555555", fontsize=14)
            else:
                tbl = ax.table(
                    cellText=[[str(c) for c in r] for r in chunk],
                    colLabels=col_headers,
                    colWidths=cw,
                    loc="center",
                    bbox=[0.0, 0.02, 1.0, 0.88])
                tbl.auto_set_font_size(False)
                tbl.set_fontsize(9)
                # Header
                for j in range(len(col_headers)):
                    cell = tbl[0, j]
                    cell.set_facecolor("#c8102e")
                    cell.set_text_props(color="white", fontweight="bold")
                    cell.set_edgecolor("#333333")
                # Righe alternate
                for i in range(len(chunk)):
                    bg = "#1a1a1a" if i % 2 == 0 else "#111111"
                    for j in range(len(col_headers)):
                        cell = tbl[i + 1, j]
                        cell.set_facecolor(bg)
                        cell.set_text_props(color="#eeeeee")
                        cell.set_edgecolor("#222222")

            pdf.savefig(fig, bbox_inches="tight", facecolor=fig.get_facecolor())
            plt.close("all")

    messagebox.showinfo("PDF Esportato ✓",
                        f"File salvato in:\n{path}", parent=parent_win)


def _export_figure_pdf(fig, title: str, parent_win=None) -> None:
    """Salva una Figure matplotlib come PDF (per radar chart e pie chart)."""
    from matplotlib.backends.backend_pdf import PdfPages

    path = filedialog.asksaveasfilename(
        parent=parent_win,
        title="Salva grafico PDF",
        defaultextension=".pdf",
        filetypes=[("PDF", "*.pdf"), ("Tutti i file", "*.*")],
        initialfile=(f"HoopIQ_{re.sub(chr(32), '_', title)}_"
                     f"{datetime.now().strftime('%Y%m%d_%H%M')}.pdf"))
    if not path:
        return
    with PdfPages(path) as pdf:
        pdf.savefig(fig, bbox_inches="tight", facecolor=fig.get_facecolor())
    messagebox.showinfo("PDF Esportato ✓",
                        f"File salvato in:\n{path}", parent=parent_win)


# ══════════════════════════════════════════════════════════════
#  WIDGET HELPERS
# ══════════════════════════════════════════════════════════════
def sep(parent, color=BORDER, **kw):
    return tk.Frame(parent, bg=color, height=1, **kw)

def lbl(parent, text, size=10, color=TEXT_WHITE, bold=False, bg=BG_CARD, **kw):
    return tk.Label(parent, text=text, fg=color, bg=bg,
                    font=("Segoe UI", size, "bold" if bold else "normal"), **kw)

def entry(parent, var, width=26):
    e = tk.Entry(parent, textvariable=var, width=width,
                 bg=BG_INPUT, fg=TEXT_WHITE, insertbackground=TEXT_WHITE,
                 relief="flat", font=("Segoe UI", 11),
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
                background="#1e1e1e", foreground="#e8e8e8",
                fieldbackground="#1e1e1e", rowheight=34,
                font=("Segoe UI", 11))
    s.configure("Treeview.Heading",
                background="#1d428a", foreground="#ffffff",
                font=("Segoe UI", 10, "bold"), relief="flat")
    # Treeview con heading oro (pagina Da Aggiornare)
    s.configure("Gold.Treeview",
                background="#1e1e1e", foreground="#e8e8e8",
                fieldbackground="#1e1e1e", rowheight=34,
                font=("Segoe UI", 11))
    s.configure("Gold.Treeview.Heading",
                background="#2a1e00", foreground=ACCENT_GLD,
                font=("Segoe UI", 10, "bold"), relief="flat")
    s.map("Gold.Treeview",
          background=[("selected", "#3d2c00")],
          foreground=[("selected", ACCENT_GLD)])
    s.map("Treeview",
          background=[("selected", "#c8102e")],
          foreground=[("selected", "#ffffff")])
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
            "PageRatingYouth":        "Rating Giovanili Club  ·  NCAA · NCAA 2 · NCAA 3 · ANGXT",
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
    LABEL         = "♂ Club — Classifica Unica Prestigio 2027"
    BONUSES       = PRESTIGE_2027
    BONUS_SECTIONS = [
        ("🏆  CLASSIFICA UNICA PRESTIGIO 2027",    PRESTIGE_2027),
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
        sf = ScrollableFrame(body, bg=BG_CARD, fixed_width=310)
        sf.pack(side="left", fill="y", padx=(0, 12))
        fc = tk.Frame(sf.inner, bg=BG_CARD, padx=16, pady=14)
        fc.pack(fill="x")

        lbl(fc, "INSERT PLAYER", size=13, bold=True,
            color=self.COLOR).pack(anchor="w", pady=(0, 10))

        # Mode indicator
        self._mode_lbl = tk.Label(fc, text="➕  MODALITA': INSERIMENTO",
                                   fg=self.COLOR, bg=BG_CARD,
                                   font=("Segoe UI", 9, "bold"))
        self._mode_lbl.pack(anchor="w", pady=(0, 6))

        self.vars = {}
        for label, key in [("Nome","nome"),("Cognome","cognome"),
                            ("Data Nascita","nascita"),("Ruolo","ruolo"),
                            ("Team","team")]:
            r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=3)
            lbl(r, label, size=10, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
            v = tk.StringVar(); self.vars[key] = v
            entry(r, v).pack(fill="x", pady=(0, 3))

        # Stato
        r = tk.Frame(fc, bg=BG_CARD); r.pack(fill="x", pady=3)
        lbl(r, "Stato", size=10, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self.stato_var = tk.StringVar(value="Attivo")
        stato_cb = ttk.Combobox(r, textvariable=self.stato_var,
                                values=["Attivo", "Inattivo", "Infortunato", "Ritirato"],
                                state="readonly", width=34,
                                font=("Segoe UI", 11))
        stato_cb.pack(fill="x", pady=(0, 3))

        sep(fc).pack(fill="x", pady=8)
        lbl(fc, "📸  Foto Giocatore", size=11, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 6))
        player_photo_row = tk.Frame(fc, bg=BG_CARD)
        player_photo_row.pack(fill="x", pady=2)
        pf = tk.Frame(player_photo_row, bg="#121212", padx=6, pady=6)
        pf.pack(side="left")
        player_photo_container = tk.Frame(pf, bg="#1a1a1a", width=120, height=120)
        player_photo_container.pack_propagate(False)
        player_photo_container.pack()
        self._player_photo_lbl = tk.Label(
            player_photo_container, text="👤", bg="#1a1a1a", fg=TEXT_DIM,
            font=("Segoe UI", 28), cursor="hand2")
        self._player_photo_lbl.place(relx=0.5, rely=0.5, anchor="center")
        lbl(pf, "Giocatore", size=7, color=TEXT_DIM, bg="#121212").pack()
        HoopButton(pf, "CERCA FOTO", self._pick_player_photo,
                   bg_color="#0a0020", hover_color=ACCENT_BLU,
                   width=120, height=28, icon="🔍", font_size=8).pack(pady=(4, 0))
        HoopButton(pf, "CARICA", self._pick_player_photo_manual,
                   bg_color="#252525", hover_color="#404040",
                   width=120, height=28, icon="📂", font_size=8).pack(pady=(2, 0))
        self._player_foto_path = ""

        sep(fc).pack(fill="x", pady=6)
        lbl(fc, "🏀  Logo Team", size=11, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 6))
        photo_row = tk.Frame(fc, bg=BG_CARD)
        photo_row.pack(fill="x", pady=2)
        tf = tk.Frame(photo_row, bg="#121212", padx=6, pady=6)
        tf.pack(side="left")
        # Contenitore a dimensione fissa 120x120 px
        logo_container = tk.Frame(tf, bg="#1a1a1a", width=120, height=120)
        logo_container.pack_propagate(False)
        logo_container.pack()
        self._team_logo_lbl = tk.Label(
            logo_container, text="🏀", bg="#1a1a1a", fg=TEXT_DIM,
            font=("Segoe UI", 28), cursor="hand2")
        self._team_logo_lbl.place(relx=0.5, rely=0.5, anchor="center")
        lbl(tf, "Team", size=7, color=TEXT_DIM, bg="#121212").pack()
        HoopButton(tf, "CERCA LOGO", self._pick_team_logo,
                   bg_color="#0a1500", hover_color=ACCENT_GRN,
                   width=120, height=28, icon="🔍", font_size=8).pack(pady=(4, 0))
        HoopButton(tf, "CARICA", self._pick_team_logo_manual,
                   bg_color="#252525", hover_color="#404040",
                   width=120, height=28, icon="📂", font_size=8).pack(pady=(2, 0))
        self._team_foto_path = ""

        sep(fc).pack(fill="x", pady=6)
        lbl(fc, "Completezza Dati", size=11, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 4))
        self._incompleto_var = tk.BooleanVar(value=False)
        tk.Checkbutton(fc, text="⚠  Segna come INCOMPLETO",
                       variable=self._incompleto_var,
                       command=self._toggle_nota,
                       bg=BG_CARD, fg="#ff9800",
                       selectcolor=BG_DARK,
                       activebackground=BG_CARD, activeforeground="#ff9800",
                       font=("Segoe UI", 10, "bold")).pack(anchor="w")
        r_nota = tk.Frame(fc, bg=BG_CARD); r_nota.pack(fill="x", pady=(4, 0))
        lbl(r_nota, "Nota (cosa manca?)", size=10, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self._nota_text = tk.Text(r_nota, width=36, height=4,
                                   bg="#1a1a1a", fg=TEXT_DIM,
                                   insertbackground=TEXT_WHITE,
                                   font=("Segoe UI", 10), relief="flat", bd=4,
                                   state="disabled")
        self._nota_text.pack(fill="x", pady=(0, 2))

        sep(fc).pack(fill="x", pady=8)
        lbl(fc, "Competizione", size=11, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 4))

        self.comp_var = tk.StringVar(value="")
        for section_title, section_dict in self.BONUS_SECTIONS:
            lbl(fc, section_title, size=10, bold=True,
                color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w", pady=(8, 3))
            for comp, pts in section_dict.items():
                row = tk.Frame(fc, bg=BG_CARD); row.pack(fill="x", pady=2)
                # Icona competizione
                cimg = get_comp_icon(comp, 24)
                if cimg:
                    il = tk.Label(row, image=cimg, bg=BG_CARD)
                    il.pack(side="left", padx=(0, 4))
                    il._img = cimg   # prevent GC
                pts_str = f"  +{pts}pt" if pts > 0 else "  N/D"
                tk.Radiobutton(row, text=comp, variable=self.comp_var, value=comp,
                               bg=BG_CARD, fg=TEXT_WHITE,
                               selectcolor=BG_DARK, activebackground=BG_CARD,
                               activeforeground=self.COLOR,
                               font=("Segoe UI", 10), anchor="w").pack(side="left")
                lbl(row, pts_str, size=9,
                    color=self.COLOR if pts > 0 else TEXT_DIM,
                    bg=BG_CARD).pack(side="left")

        sep(fc).pack(fill="x", pady=10)
        HoopButton(fc, "ADD PLAYER", self._add_player,
                   bg_color=self.COLOR, hover_color=self.HOVER,
                   width=270, height=44, icon="➕", font_size=12).pack(pady=6)
        self._reset_btn = HoopButton(fc, "RESET FORM", self._clear_form,
                                     bg_color="#252525", hover_color="#333",
                                     width=270, height=34, icon="↩", font_size=10)
        self._reset_btn.pack(pady=3)
        HoopButton(fc, "CHIEDI ALL'AI", self._open_ai_chat,
                   bg_color="#0d1b2a", hover_color="#1a2e48",
                   width=270, height=34, icon="🏀", font_size=10).pack(pady=3)

        # RIGHT ranking
        right = tk.Frame(body, bg=BG_DARK)
        right.pack(side="left", fill="both", expand=True)
        top_bar = tk.Frame(right, bg=BG_DARK)
        top_bar.pack(fill="x", pady=(0, 4))
        lbl(top_bar, "GENERATE RATING", size=13, bold=True,
            color=self.COLOR, bg=BG_DARK).pack(side="left")
        HoopButton(top_bar, "PDF", self._export_pdf,
                   bg_color="#3a0010", hover_color="#5a0018",
                   width=80, height=32, icon="📥", font_size=9).pack(side="right", padx=(4, 0))
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
        col_cfg = {
            "Rank":  (42,  "center", False),
            "Nome":  (170, "w",      False),
            "Ruolo": (56,  "center", False),
            "Team":  (160, "w",      True),
            "Stato": (72,  "center", False),
            "Comp":  (130, "w",      True),
            "SCORE": (64,  "center", False),
        }

        tree_wrap = tk.Frame(right, bg=BG_DARK)
        tree_wrap.pack(fill="both", expand=True)
        vsb = ttk.Scrollbar(tree_wrap, orient="vertical",
                            command=lambda *a: self.tree.yview(*a),
                            style="Dark.Vertical.TScrollbar")
        vsb.pack(side="right", fill="y")
        hsb = ttk.Scrollbar(tree_wrap, orient="horizontal",
                            command=lambda *a: self.tree.xview(*a),
                            style="Dark.Vertical.TScrollbar")
        hsb.pack(side="bottom", fill="x")
        self.tree = ttk.Treeview(tree_wrap, columns=cols, show="tree headings", height=22)
        self.tree.heading("#0", text="Logo")
        self.tree.column("#0", width=46, stretch=False, anchor="center", minwidth=46)
        for c in cols:
            w, anc, stretch = col_cfg[c]
            self.tree.heading(c, text=c)
            self.tree.column(c, width=w, anchor=anc, stretch=stretch, minwidth=w)
        self.tree.configure(yscrollcommand=vsb.set, xscrollcommand=hsb.set)
        self.tree.pack(side="left", fill="both", expand=True)
        self._tree_logo_cache: dict = {}   # team_foto_path → PhotoImage 36px
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
        HoopButton(db, "⚠ INCOMPLETI", self._show_incompleti,
                   bg_color="#3a2000", hover_color="#5a3800",
                   width=130, height=30, icon="⚠", font_size=9).pack(side="left", padx=(0, 8))
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
        idx = int(row) - 1
        data = sorted(self._load(), key=compute_score, reverse=True)
        p = data[idx] if idx < len(data) else {}
        is_incomplete = p.get("incompleto", False)
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
        menu.add_separator()
        lbl_inc = "✅  Segna come Completo" if is_incomplete else "⚠  Segna come Incompleto"
        menu.add_command(label=lbl_inc, command=self._toggle_selected_incomplete)
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
                            "nome":            nome,
                            "cognome":         cognome,
                            "nascita":         self.vars["nascita"].get().strip(),
                            "ruolo":           self.vars["ruolo"].get().strip(),
                            "team":            self.vars["team"].get().strip(),
                            "stato":           self.stato_var.get(),
                            "comp":            selected_comp,
                            "bonus":           bonus,
                            "foto":            getattr(self, "_player_foto_path", ""),
                            "team_foto":       getattr(self, "_team_foto_path", ""),
                            "incompleto":      self._incompleto_var.get(),
                            "nota_incompleto": self._nota_text.get("1.0", "end").strip() if self._incompleto_var.get() else "",
                            "added":           ep.get("added"),
                            "modified":        datetime.now().isoformat(timespec="seconds"),
                        }
                        break
                self._save(data)
                self._clear_form()
                self.refresh()
                self.app.refresh_side()
                _hoop_dlg(self, f"{nome} {cognome} aggiornato!", "info")
            else:
                player = {
                    "nome":            nome,
                    "cognome":         cognome,
                    "nascita":         self.vars["nascita"].get().strip(),
                    "ruolo":           self.vars["ruolo"].get().strip(),
                    "team":            self.vars["team"].get().strip(),
                    "stato":           self.stato_var.get(),
                    "comp":            selected_comp,
                    "bonus":           bonus,
                    "team_foto":       getattr(self, "_team_foto_path", ""),
                    "incompleto":      self._incompleto_var.get(),
                    "nota_incompleto": self._nota_text.get("1.0", "end").strip() if self._incompleto_var.get() else "",
                    "added":           datetime.now().isoformat(timespec="seconds"),
                }
                check_keys = ("nome", "cognome", "nascita", "ruolo", "stato", "comp")
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
        self.vars["team"].set(p.get("team", ""))
        self.stato_var.set(p.get("stato", "Attivo"))
        self.comp_var.set(p.get("comp", ""))
        # Foto giocatore
        self._player_foto_path = p.get("foto", "")
        if hasattr(self, "_player_photo_lbl"):
            self._update_photo_preview(self._player_photo_lbl, self._player_foto_path, "👤")
        # Logo team
        self._team_foto_path = p.get("team_foto", "")
        if hasattr(self, "_team_logo_lbl"):
            self._update_photo_preview(self._team_logo_lbl, self._team_foto_path, "🏀")
        inc = p.get("incompleto", False)
        self._incompleto_var.set(inc)
        self._nota_text.configure(state="normal")
        self._nota_text.delete("1.0", "end")
        if inc:
            self._nota_text.insert("1.0", p.get("nota_incompleto", ""))
            self._nota_text.configure(fg=TEXT_WHITE)
        else:
            self._nota_text.configure(state="disabled", fg=TEXT_DIM)
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
        self._incompleto_var.set(False)
        self._nota_text.configure(state="normal")
        self._nota_text.delete("1.0", "end")
        self._nota_text.configure(state="disabled", fg=TEXT_DIM)
        self._player_foto_path = ""
        if hasattr(self, "_player_photo_lbl"):
            self._player_photo_lbl.configure(image="", text="👤", fg=TEXT_DIM)
            if hasattr(self._player_photo_lbl, "_photo"): del self._player_photo_lbl._photo
        self._team_foto_path = ""
        if hasattr(self, "_team_logo_lbl"):
            self._team_logo_lbl.configure(image="", text="🏀", fg=TEXT_DIM)
            if hasattr(self._team_logo_lbl, "_photo"): del self._team_logo_lbl._photo
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

    def _toggle_nota(self):
        if self._incompleto_var.get():
            self._nota_text.configure(state="normal", fg=TEXT_WHITE)
        else:
            self._nota_text.configure(state="normal")
            self._nota_text.delete("1.0", "end")
            self._nota_text.configure(state="disabled", fg=TEXT_DIM)

    def _export_pdf(self):
        """Esporta la classifica corrente (filtrata) come PDF."""
        players = sorted(self._load(), key=compute_score, reverse=True)
        players = self._get_filtered_players(players)
        MEDALS  = {1: "🥇", 2: "🥈", 3: "🥉"}
        rows = [
            [MEDALS.get(i, str(i)),
             p.get("nome", ""),
             p.get("cognome", ""),
             p.get("ruolo", ""),
             p.get("team", ""),
             p.get("stato", ""),
             p.get("comp", ""),
             str(int(compute_score(p)))]
            for i, p in enumerate(players, 1)
        ]
        _export_table_pdf(
            title=getattr(self, "LABEL", "Rating"),
            subtitle=(f"Classifica Unica Prestigio 2027  ·  "
                      f"{len(players)} giocatori"),
            col_headers=["#", "Nome", "Cognome", "Ruolo",
                         "Team", "Stato", "Competizione", "Score"],
            rows=rows,
            col_widths=[0.04, 0.10, 0.11, 0.06, 0.20, 0.07, 0.35, 0.07],
            parent_win=self)

    def _toggle_selected_incomplete(self):
        sel = self.tree.selection()
        if not sel: return
        idx  = int(sel[0]) - 1
        data_sorted = sorted(self._load(), key=compute_score, reverse=True)
        all_data    = self._load()
        p = data_sorted[idx]
        new_state = not p.get("incompleto", False)
        for i, pp in enumerate(all_data):
            if pp.get("added") == p.get("added") and pp.get("nome") == p.get("nome"):
                all_data[i]["incompleto"] = new_state
                if not new_state:
                    all_data[i]["nota_incompleto"] = ""
                all_data[i]["modified"] = datetime.now().isoformat(timespec="seconds")
                break
        self._save(all_data)
        self.refresh()

    def _show_incompleti(self):
        data = [p for p in self._load() if p.get("incompleto")]
        win = tk.Toplevel(self)
        win.title("Giocatori Incompleti")
        win.configure(bg=BG_DARK)
        win.geometry("760x460")
        win.grab_set()
        tk.Label(win, text="⚠  GIOCATORI INCOMPLETI",
                 fg="#ff9800", bg=BG_DARK,
                 font=("Segoe UI", 13, "bold")).pack(pady=(14, 2))
        tk.Label(win, text=f"{len(data)} giocatori con dati mancanti  —  doppio clic per aprire in modifica",
                 fg=TEXT_GRAY, bg=BG_DARK, font=("Segoe UI", 9)).pack(pady=(0, 8))
        frame = tk.Frame(win, bg=BG_DARK)
        frame.pack(fill="both", expand=True, padx=16, pady=(0, 4))
        cols = ("Nome", "Team", "Comp", "Nota mancante")
        tree = ttk.Treeview(frame, columns=cols, show="headings", height=15)
        for c, w in zip(cols, [190, 160, 90, 270]):
            tree.heading(c, text=c)
            tree.column(c, width=w, anchor="w", minwidth=w)
        vsb = ttk.Scrollbar(frame, orient="vertical", command=tree.yview,
                             style="Dark.Vertical.TScrollbar")
        tree.configure(yscrollcommand=vsb.set)
        tree.pack(side="left", fill="both", expand=True)
        vsb.pack(side="right", fill="y")
        tree.tag_configure("inc", background="#1a0d00", foreground="#ff9800")
        for p in data:
            nome = f"{p.get('nome','')} {p.get('cognome','')}".strip()
            tree.insert("", "end",
                        values=(nome,
                                p.get("team","") or "—",
                                p.get("comp","") or "—",
                                p.get("nota_incompleto","") or "—"),
                        tags=("inc",))

        def on_double(event):
            sel = tree.selection()
            if not sel: return
            idx = tree.index(sel[0])
            p = data[idx]
            win.destroy()
            self._editing_player = p
            self.vars["nome"].set(p.get("nome", ""))
            self.vars["cognome"].set(p.get("cognome", ""))
            self.vars["nascita"].set(p.get("nascita", ""))
            self.vars["ruolo"].set(p.get("ruolo", ""))
            self.vars["team"].set(p.get("team", ""))
            self.stato_var.set(p.get("stato", "Attivo"))
            self.comp_var.set(p.get("comp", ""))
            if hasattr(self, "bonus_var"):
                self.bonus_var.set(str(p.get("bonus", 0)))
            self._player_foto_path = p.get("foto", "")
            if hasattr(self, "_player_photo_lbl"):
                self._update_photo_preview(self._player_photo_lbl, self._player_foto_path, "👤")
            self._team_foto_path = p.get("team_foto", "")
            if hasattr(self, "_team_logo_lbl"):
                self._update_photo_preview(self._team_logo_lbl, self._team_foto_path, "🏀")
            self._incompleto_var.set(True)
            self._toggle_nota()
            self._nota_text.delete("1.0", "end")
            self._nota_text.insert("1.0", p.get("nota_incompleto", ""))
            self._mode_lbl.configure(
                text=f"✏  MODALITA': MODIFICA  —  {p.get('nome','')} {p.get('cognome','')}")
            self._reset_btn._lbl.configure(text="✖  ANNULLA MODIFICA")
            self._reset_btn._bg    = "#3a0010"
            self._reset_btn._hover = "#5a0018"
            self._reset_btn._left.configure(bg="#5a0018")
            self._reset_btn._set_color("#3a0010")

        tree.bind("<Double-1>", on_double)
        btn_frame = tk.Frame(win, bg=BG_DARK)
        btn_frame.pack(fill="x", padx=16, pady=8)
        HoopButton(btn_frame, "CHIUDI", win.destroy,
                   bg_color="#252525", hover_color="#333",
                   width=120, height=30, icon="✖", font_size=9).pack(side="right")

    # ── Logo Team helpers ────────────────────────────────────────────────────

    def _pick_team_logo(self):
        team = self.vars.get("team", tk.StringVar()).get().strip()
        if not team:
            _hoop_dlg(self, "Inserisci prima il nome del Team.", "warn")
            return
        # Rimuove suffissi come "- USA", "- ITA" ecc. per una ricerca più pulita
        clean_team = re.sub(r'\s*[-–]\s*[A-Z]{2,3}\s*$', '', team).strip()
        self._fetch_web_photo(clean_team, target="team")

    def _fetch_web_photo(self, query: str, target: str):
        """Cerca immagini su DuckDuckGo, gallery PREV/NEXT, conferma, fallback manuale."""
        from io import BytesIO

        dlg = tk.Toplevel(self)
        dlg.title("Ricerca foto online")
        dlg.configure(bg=BG_DARK)
        dlg.geometry("520x490")
        dlg.grab_set()
        dlg.resizable(False, False)

        lbl(dlg, f"🔍  {query}", size=11, bold=True,
            color=TEXT_WHITE).pack(pady=(14, 2), padx=16, anchor="w")
        status_lbl = lbl(dlg, "Ricerca in corso…", size=9, color=TEXT_DIM)
        status_lbl.pack(pady=(0, 4))
        counter_lbl = lbl(dlg, "", size=8, color=TEXT_DIM)
        counter_lbl.pack(pady=(0, 6))

        preview_outer = tk.Frame(dlg, bg="#121212", width=240, height=240)
        preview_outer.pack_propagate(False)
        preview_outer.pack()
        preview_lbl = tk.Label(preview_outer, text="⏳", bg="#121212", fg=TEXT_DIM,
                               font=("Segoe UI", 36))
        preview_lbl.pack(expand=True)

        nav_frame = tk.Frame(dlg, bg=BG_DARK)
        nav_frame.pack(pady=6)
        btn_frame = tk.Frame(dlg, bg=BG_DARK)
        btn_frame.pack(fill="x", padx=16, pady=(4, 10))

        state = {"urls": [], "idx": 0, "img_bytes": None}

        def _show_idx(i):
            urls = state["urls"]
            if not urls:
                return
            i = max(0, min(i, len(urls) - 1))
            state["idx"] = i
            counter_lbl.configure(text=f"Immagine {i + 1} / {len(urls)}")
            status_lbl.configure(text="Caricamento immagine…", fg=TEXT_DIM)
            preview_lbl.configure(image="", text="⏳")
            if hasattr(preview_lbl, "_img"):
                del preview_lbl._img
            confirm_btn._lbl.configure(state="disabled")

            def _load():
                raw = _fetch_bytes(urls[i])
                if not raw:
                    if i + 1 < len(urls):
                        dlg.after(0, lambda: _show_idx(i + 1))
                    else:
                        dlg.after(0, lambda: status_lbl.configure(
                            text="❌ Nessuna immagine caricabile. Usa Manuale.", fg="#FF9800"))
                    return
                state["img_bytes"] = raw

                def _update():
                    if not dlg.winfo_exists():
                        return
                    try:
                        if PIL_AVAILABLE:
                            img = Image.open(BytesIO(raw)).convert("RGBA")
                            img.thumbnail((230, 230), Image.LANCZOS)
                            sq = Image.new("RGBA", (240, 240), (18, 18, 18, 255))
                            sq.paste(img, ((240 - img.width) // 2,
                                           (240 - img.height) // 2), img)
                            tk_img = ImageTk.PhotoImage(sq)
                            preview_lbl.configure(image=tk_img, text="")
                            preview_lbl._img = tk_img
                        status_lbl.configure(text="✅ Conferma per salvare.", fg="#4CAF50")
                        confirm_btn._lbl.configure(state="normal")
                    except Exception:
                        dlg.after(0, lambda: _show_idx(state["idx"] + 1))
                dlg.after(0, _update)
            threading.Thread(target=_load, daemon=True).start()

        def _save_and_close():
            if not state["img_bytes"]:
                return
            dest_dir = PLAYER_PHOTOS_DIR if target == "player" else TEAM_LOGOS_DIR
            os.makedirs(dest_dir, exist_ok=True)
            nome    = self.vars.get("nome",    tk.StringVar()).get().strip()
            cognome = self.vars.get("cognome", tk.StringVar()).get().strip()
            team    = self.vars.get("team",    tk.StringVar()).get().strip()
            base = (_sanitize_name(f"{nome}_{cognome}") if target == "player"
                    else _sanitize_name(team)) or ("player" if target == "player" else "team")
            dest = os.path.join(dest_dir, base + ".png")
            try:
                with open(dest, "wb") as f:
                    f.write(state["img_bytes"])
            except Exception as e:
                _hoop_dlg(self, f"Errore salvataggio: {e}", "error")
                return
            dlg.destroy()
            if target == "player":
                self._player_foto_path = dest
                self._update_photo_preview(self._player_photo_lbl, dest, "👤")
            else:
                self._team_foto_path = dest
                self._update_photo_preview(self._team_logo_lbl, dest, "🏀")

        def _manual():
            dlg.destroy()
            if target == "player":
                self._pick_player_photo_manual()
            else:
                self._pick_team_logo_manual()

        HoopButton(nav_frame, "◀ PREC", lambda: _show_idx(state["idx"] - 1),
                   bg_color="#252525", hover_color="#404040",
                   width=110, height=28, font_size=9).pack(side="left", padx=4)
        HoopButton(nav_frame, "SUCC ▶", lambda: _show_idx(state["idx"] + 1),
                   bg_color="#252525", hover_color="#404040",
                   width=110, height=28, font_size=9).pack(side="left", padx=4)

        confirm_btn = HoopButton(btn_frame, "✅  USA QUESTA", _save_and_close,
                                 bg_color="#0a3300", hover_color=ACCENT_GRN,
                                 width=155, height=34, font_size=10)
        confirm_btn.pack(side="left", padx=(0, 8))
        confirm_btn._lbl.configure(state="disabled")
        HoopButton(btn_frame, "📂  MANUALE", _manual,
                   bg_color="#252525", hover_color="#404040",
                   width=130, height=34, font_size=10).pack(side="left", padx=(0, 8))
        HoopButton(btn_frame, "✖  ANNULLA", dlg.destroy,
                   bg_color="#3a0010", hover_color="#5a0018",
                   width=100, height=34, font_size=10).pack(side="right")

        def _do_search():
            err_msg = [""]
            try:
                found = _ddg_image_urls(query, count=8)
            except Exception as e:
                found = []
                err_msg[0] = str(e)
            def _on_result():
                if not dlg.winfo_exists():
                    return
                if found:
                    state["urls"] = found
                    _show_idx(0)
                else:
                    detail = f"  ({err_msg[0]})" if err_msg[0] else ""
                    status_lbl.configure(
                        text=f"❌ Nessun risultato{detail}. Prova con \"Manuale\".",
                        fg="#FF9800")
            dlg.after(0, _on_result)
        threading.Thread(target=_do_search, daemon=True).start()

    # ── Upload manuale ───────────────────────────────────────────────────────

    def _pick_player_photo(self):
        nome    = self.vars.get("nome",    tk.StringVar()).get().strip()
        cognome = self.vars.get("cognome", tk.StringVar()).get().strip()
        if not nome or not cognome:
            _hoop_dlg(self, "Inserisci prima Nome e Cognome.", "warn")
            return
        team  = self.vars.get("team", tk.StringVar()).get().strip()
        query = f"{nome} {cognome} basketball"
        if team:
            query += f" {team}"
        self._fetch_web_photo(query, target="player")

    def _pick_player_photo_manual(self):
        path = filedialog.askopenfilename(
            parent=self, title="Seleziona foto giocatore",
            filetypes=[("Immagini", "*.png *.jpg *.jpeg *.webp *.bmp"),
                       ("Tutti i file", "*.*")])
        if not path:
            return
        os.makedirs(PLAYER_PHOTOS_DIR, exist_ok=True)
        nome    = self.vars.get("nome",    tk.StringVar()).get().strip()
        cognome = self.vars.get("cognome", tk.StringVar()).get().strip()
        base    = _sanitize_name(f"{nome}_{cognome}") or "player"
        dest    = self._copy_to_assets(path, PLAYER_PHOTOS_DIR, base)
        self._player_foto_path = dest
        self._update_photo_preview(self._player_photo_lbl, dest, "👤")

    def _pick_team_logo_manual(self):
        path = filedialog.askopenfilename(
            parent=self, title="Seleziona logo team",
            filetypes=[("Immagini", "*.png *.jpg *.jpeg *.webp *.bmp"),
                       ("Tutti i file", "*.*")])
        if not path:
            return
        os.makedirs(TEAM_LOGOS_DIR, exist_ok=True)
        team = self.vars.get("team", tk.StringVar()).get().strip()
        base = _sanitize_name(team) or "team"
        dest = self._copy_to_assets(path, TEAM_LOGOS_DIR, base)
        self._team_foto_path = dest
        self._update_photo_preview(self._team_logo_lbl, dest, "🏀")

    @staticmethod
    def _copy_to_assets(src: str, dest_dir: str, base_name: str) -> str:
        """Copia file in dest_dir/base_name.ext, restituisce path dest."""
        ext  = os.path.splitext(src)[1].lower() or ".png"
        dest = os.path.join(dest_dir, base_name + ext)
        try:
            if os.path.abspath(src) != os.path.abspath(dest):
                shutil.copy2(src, dest)
        except Exception:
            dest = src
        return dest

    def _update_photo_preview(self, label: tk.Label, path: str, placeholder: str = "🖼", size: int = 120):
        """Aggiorna un Label con anteprima proporzionata o placeholder."""
        photo = _load_photo_preview(path, size)
        if photo:
            label.configure(image=photo, text="", bg="#1a1a1a")
            label._photo = photo
        else:
            label.configure(image="", text=placeholder, fg=TEXT_DIM, bg="#1a1a1a")
            if hasattr(label, "_photo"):
                del label._photo

    # ── AI Chat ──────────────────────────────────────────────────────────────

    def _open_ai_chat(self):
        """Apre la finestra chat AI contestuale al giocatore in modifica."""
        nome    = self.vars.get("nome",    tk.StringVar()).get().strip()
        cognome = self.vars.get("cognome", tk.StringVar()).get().strip()
        team    = self.vars.get("team",    tk.StringVar()).get().strip()
        ruolo   = self.vars.get("ruolo",   tk.StringVar()).get().strip()
        comp    = self.comp_var.get() if hasattr(self, "comp_var") else ""
        player_ctx = (
            f"Giocatore: {nome} {cognome} | Ruolo: {ruolo} | "
            f"Team: {team} | Competizione: {comp}"
        ).strip(" |")

        cfg = _ai_cfg_load()

        dlg = tk.Toplevel(self)
        dlg.title("🤖  HoopIQ AI Assistant")
        dlg.configure(bg=BG_DARK)
        dlg.geometry("640x680")
        dlg.resizable(True, True)

        # ── Header ──────────────────────────────────────────────────────────
        hdr = tk.Frame(dlg, bg="#0d1b2a")
        hdr.pack(fill="x")

        # Robot logo
        logo_img = _AI_ROBOT_LOGO_CACHE.get(52) or _make_ai_robot_logo(52)
        if logo_img:
            _AI_ROBOT_LOGO_CACHE[52] = logo_img
            tk.Label(hdr, image=logo_img, bg="#0d1b2a").pack(
                side="left", padx=(10, 4), pady=6)
            hdr._logo = logo_img   # prevent GC

        lbl(hdr, "HoopIQ AI Assistant", size=13, bold=True,
            color="#82b4ff", bg="#0d1b2a").pack(side="left", padx=(4, 14), pady=10)
        settings_btn = tk.Label(hdr, text="⚙", bg="#0d1b2a", fg=TEXT_GRAY,
                                font=("Segoe UI", 14), cursor="hand2")
        settings_btn.pack(side="right", padx=14)

        if player_ctx:
            lbl(dlg, f"📋  {player_ctx}", size=8, color=TEXT_GRAY,
                bg=BG_DARK).pack(anchor="w", padx=12, pady=(6, 0))

        # ── Storico chat ────────────────────────────────────────────────────
        chat_frame = tk.Frame(dlg, bg=BG_DARK)
        chat_frame.pack(fill="both", expand=True, padx=10, pady=6)

        chat_vsb = ttk.Scrollbar(chat_frame, orient="vertical",
                                 style="Dark.Vertical.TScrollbar")
        chat_vsb.pack(side="right", fill="y")
        chat_box = tk.Text(
            chat_frame, bg="#0b0f14", fg=TEXT_WHITE, wrap="word",
            font=("Segoe UI", 10), relief="flat", bd=6,
            state="disabled", cursor="arrow",
            yscrollcommand=chat_vsb.set)
        chat_box.pack(side="left", fill="both", expand=True)
        chat_vsb.configure(command=chat_box.yview)

        # Tag colori messaggi
        chat_box.tag_configure("user",  foreground="#82b4ff",
                               font=("Segoe UI", 10, "bold"))
        chat_box.tag_configure("ai",    foreground="#cccccc",
                               font=("Segoe UI", 10))
        chat_box.tag_configure("error", foreground="#FF5555",
                               font=("Segoe UI", 10, "italic"))
        chat_box.tag_configure("label", foreground="#555555",
                               font=("Segoe UI", 8))

        # ── Input area ──────────────────────────────────────────────────────
        inp_frame = tk.Frame(dlg, bg="#111111")
        inp_frame.pack(fill="x", padx=10, pady=(0, 10))

        inp_var = tk.StringVar()
        inp_entry = entry(inp_frame, inp_var, width=50)
        inp_entry.pack(side="left", fill="x", expand=True, padx=(0, 8), pady=8)
        send_btn = HoopButton(inp_frame, "INVIA", lambda: _send(),
                              bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                              width=90, height=34, icon="➤", font_size=10)
        send_btn.pack(side="left", pady=8)

        # ── Stato config AI ─────────────────────────────────────────────────
        if cfg.get("api_key"):
            api_warn = lbl(dlg, "✅  Modalità API personalizzata attiva.",
                           size=8, color="#4CAF50", bg=BG_DARK)
        else:
            api_warn = lbl(dlg, "🆓  AI gratuita attiva — nessuna API key richiesta.",
                           size=8, color="#82b4ff", bg=BG_DARK)
        api_warn.pack(pady=(0, 4))

        history: list[dict] = []  # messages for API
        system_msg = (
            "Sei HoopIQ AI, assistente esperto di basket. "
            "Rispondi in italiano. Sii conciso e preciso. "
            + (f"Il contesto attuale è: {player_ctx}." if player_ctx else "")
        )

        def _append(text: str, tag: str, prefix: str = ""):
            chat_box.configure(state="normal")
            if prefix:
                chat_box.insert("end", prefix + "\n", "label")
            chat_box.insert("end", text + "\n\n", tag)
            chat_box.configure(state="disabled")
            chat_box.see("end")

        def _send(event=None):
            text = inp_var.get().strip()
            if not text:
                return
            inp_var.set("")
            _append(text, "user", "👤 Tu")
            history.append({"role": "user", "content": text})
            send_btn._lbl.configure(state="disabled")
            inp_entry.configure(state="disabled")

            def _call():
                current_cfg = _ai_cfg_load()
                msgs = [{"role": "system", "content": system_msg}] + history[-12:]
                try:
                    if current_cfg.get("api_key"):
                        reply = _ai_chat_completion(msgs, current_cfg)
                    else:
                        reply = _ai_free_chat_completion(msgs)
                    history.append({"role": "assistant", "content": reply})
                    dlg.after(0, lambda: _append(reply, "ai", "🤖 AI"))
                except Exception as e:
                    dlg.after(0, lambda: _append(str(e), "error", "⚠ Errore"))
                finally:
                    dlg.after(0, lambda: send_btn._lbl.configure(state="normal"))
                    dlg.after(0, lambda: inp_entry.configure(state="normal"))
                    dlg.after(0, inp_entry.focus_set)
            threading.Thread(target=_call, daemon=True).start()

        inp_entry.bind("<Return>", _send)

        def _open_settings():
            sw = tk.Toplevel(dlg)
            sw.title("Impostazioni AI")
            sw.configure(bg=BG_DARK)
            sw.geometry("480x300")
            sw.grab_set()
            sw.resizable(False, False)

            lbl(sw, "⚙  Configurazione API Personalizzata (opzionale)", size=12, bold=True,
                color=TEXT_WHITE).pack(pady=(16, 12))

            def _row(parent, label_text, default=""):
                f = tk.Frame(parent, bg=BG_DARK)
                f.pack(fill="x", padx=20, pady=4)
                tk.Label(f, text=label_text, fg=TEXT_GRAY, bg=BG_DARK,
                         font=("Segoe UI", 9), width=14, anchor="w").pack(side="left")
                v = tk.StringVar(value=default)
                e = entry(f, v, width=40)
                e.pack(side="left", fill="x", expand=True)
                return v

            v_key   = _row(sw, "API Key",   cfg.get("api_key", ""))
            v_url   = _row(sw, "Base URL",  cfg.get("base_url", "https://api.openai.com/v1"))
            v_model = _row(sw, "Modello",   cfg.get("model", "gpt-4o-mini"))

            lbl(sw, "Lascia API Key vuota per usare l'AI gratuita (Pollinations) senza nessuna configurazione.",
                size=7, color=TEXT_DIM, bg=BG_DARK).pack(pady=(2, 0))
            lbl(sw, "Compatibile con OpenAI, Ollama, Groq, DeepSeek e qualsiasi API OpenAI-compatible.",
                size=7, color=TEXT_DIM, bg=BG_DARK).pack(pady=(2, 0))

            def _save():
                new_cfg = {
                    "api_key":  v_key.get().strip(),
                    "base_url": v_url.get().strip() or "https://api.openai.com/v1",
                    "model":    v_model.get().strip() or "gpt-4o-mini",
                }
                _ai_cfg_save(new_cfg)
                if api_warn and dlg.winfo_exists():
                    if new_cfg.get("api_key"):
                        api_warn.configure(text="✅  Modalità API personalizzata attiva.", fg="#4CAF50")
                    else:
                        api_warn.configure(text="🆓  AI gratuita attiva — nessuna API key richiesta.", fg="#82b4ff")
                sw.destroy()

            HoopButton(sw, "SALVA", _save,
                       bg_color=ACCENT_BLU, hover_color=BTN_HOVER_B,
                       width=160, height=34, icon="💾", font_size=10).pack(pady=14)

        settings_btn.bind("<Button-1>", lambda e: _open_settings())

        # Messaggio di benvenuto
        welcome = (f"Ciao! Sono HoopIQ AI, il tuo assistente basket. "
                   + (f"Sto guardando il profilo di {nome} {cognome}. " if nome else "")
                   + "Chiedimi statistiche, storia, confronti o qualsiasi cosa sul basket! "
                   + "Funziono GRATIS — nessuna API key necessaria.")
        _append(welcome, "ai", "🤖 AI")
        inp_entry.focus_set()

    def refresh(self):
        for row in self.tree.get_children(): self.tree.delete(row)
        self._tree_logo_cache.clear()
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
            nome_str  = f"{p.get('nome','')} {p.get('cognome','')}".strip()
            if p.get("incompleto"):
                nome_str = f"⚠ {nome_str}"
                tag = "incompleto"
            # Logo miniatura per colonna tree
            team_foto = p.get("team_foto", "")
            logo_img  = None
            if team_foto and team_foto not in self._tree_logo_cache:
                self._tree_logo_cache[team_foto] = _load_photo_preview(team_foto, 36)
            if team_foto:
                logo_img = self._tree_logo_cache.get(team_foto)
            self.tree.insert("", "end", iid=str(i),
                image=logo_img or "",
                values=(rank_lbl, nome_str,
                        p.get("ruolo",""), p.get("team","") or "-", stato, comps_str, score),
                tags=(tag,))
        self.tree.tag_configure("gold",          background="#2a2000", foreground="#f5c518")
        self.tree.tag_configure("silver",        background="#252525", foreground="#d8d8d8")
        self.tree.tag_configure("bronze",        background="#221200", foreground="#e08c40")
        self.tree.tag_configure("stato_attivo",  background="#162416", foreground="#66dd66")
        self.tree.tag_configure("stato_inattivo",background="#1e1e1e", foreground="#aaaaaa")
        self.tree.tag_configure("stato_infort",  background="#251a00", foreground="#ffb347")
        self.tree.tag_configure("stato_ritirato",background="#131326", foreground="#8899ee")
        self.tree.tag_configure("incompleto",    background="#281800", foreground="#ffb347")


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING (WOMEN CLUB)
# ══════════════════════════════════════════════════════════════
class PageRatingWomen(PageRatingMan):
    GENDER         = "women"
    TITLE          = "Player Rating — Femminile  Club"
    LABEL          = "♀ Club — Classifica Unica Prestigio 2027"
    BONUSES        = PRESTIGE_WOMEN_CLUB
    BONUS_SECTIONS = [("🏆  CLASSIFICA UNICA PRESTIGIO 2027", PRESTIGE_WOMEN_CLUB)]
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
    LABEL          = "♂ Nazioni — Classifica Unica Prestigio 2027"
    BONUSES        = PRESTIGE_NAZIONI
    BONUS_SECTIONS = [("🏆  CLASSIFICA UNICA PRESTIGIO 2027", PRESTIGE_NAZIONI)]
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
    LABEL          = "♀ Nazioni — Classifica Unica Prestigio 2027"
    BONUSES        = PRESTIGE_NAZIONI
    BONUS_SECTIONS = [("🏆  CLASSIFICA UNICA PRESTIGIO 2027", PRESTIGE_NAZIONI)]
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
    LABEL          = "🎓 Club — Classifica Unica Prestigio 2027"
    BONUSES        = PRESTIGE_YOUTH_CLUB
    BONUS_SECTIONS = [("🏆  CLASSIFICA UNICA PRESTIGIO 2027", PRESTIGE_YOUTH_CLUB)]
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
    LABEL          = "🎓 Nazioni — Classifica Unica Prestigio 2027"
    BONUSES        = PRESTIGE_YOUTH
    BONUS_SECTIONS = [("🏆  CLASSIFICA UNICA PRESTIGIO 2027", PRESTIGE_YOUTH)]
    COLOR          = ACCENT_GRN
    HOVER          = BTN_HOVER_GRN
    _FILE          = FILE_YOUTH_NAZIONI

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()


# ══════════════════════════════════════════════════════════════
#  PAGE: PLAYER RATING — CAMPIONATI MINORI
# ══════════════════════════════════════════════════════════════
class PageRatingMinor(PageRatingMan):
    TITLE  = "Player Rating — Campionati Minori"
    LABEL  = "♂♀ — Campionati Minori · Classifica Unica Prestigio 2027"
    BONUSES = PRESTIGE_2027
    BONUS_SECTIONS = [("🏆  CLASSIFICA UNICA PRESTIGIO 2027", PRESTIGE_2027)]
    COLOR  = ACCENT_MINOR
    HOVER  = BTN_HOVER_MINOR
    _FILE  = FILE_MINOR

    def __init__(self, parent, app):
        BasePage.__init__(self, parent, app)
        self._build()

    # All behaviour (build, add, edit, refresh) inherited from PageRatingMan.


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

        sep(fc).pack(fill="x", pady=6)
        lbl(fc, "Completezza Dati", size=9, bold=True,
            color=TEXT_WHITE).pack(anchor="w", pady=(0, 4))
        self._incompleto_var = tk.BooleanVar(value=False)
        tk.Checkbutton(fc, text="⚠  Segna come INCOMPLETO",
                       variable=self._incompleto_var,
                       command=self._toggle_nota,
                       bg=BG_CARD, fg="#ff9800",
                       selectcolor=BG_DARK,
                       activebackground=BG_CARD, activeforeground="#ff9800",
                       font=("Segoe UI", 9, "bold")).pack(anchor="w")
        r_nota = tk.Frame(fc, bg=BG_CARD); r_nota.pack(fill="x", pady=(4, 0))
        lbl(r_nota, "Nota (cosa manca?)", size=8, color=TEXT_GRAY, bg=BG_CARD).pack(anchor="w")
        self._nota_text = tk.Text(r_nota, width=28, height=3,
                                   bg="#1a1a1a", fg=TEXT_DIM,
                                   insertbackground=TEXT_WHITE,
                                   font=("Segoe UI", 9), relief="flat", bd=4,
                                   state="disabled")
        self._nota_text.pack(fill="x", pady=(0, 2))

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
        HoopButton(db, "⚠ INCOMPLETI", self._show_incompleti,
                   bg_color="#3a2000", hover_color="#5a3800",
                   width=130, height=30, icon="⚠", font_size=9).pack(side="left", padx=(0, 8))
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
                            "nome":            nome,
                            "cognome":         cognome,
                            "nascita":         self.vars["nascita"].get().strip(),
                            "ruolo":           self.vars["ruolo"].get().strip(),
                            "team":            self.vars["team"].get().strip(),
                            "stato":           self.stato_var.get(),
                            "max_season":      float(self.vars["max_season"].get() or 0),
                            "comp":            comp,
                            "bonus":           bonus,
                            "incompleto":      self._incompleto_var.get(),
                            "nota_incompleto": self._nota_text.get("1.0", "end").strip() if self._incompleto_var.get() else "",
                            "added":           ep.get("added"),
                            "modified":        datetime.now().isoformat(timespec="seconds"),
                        }
                        break
                self._save(data)
                self._clear_form()
                self.refresh()
                self.app.refresh_side()
                _hoop_dlg(self, f"{nome} {cognome} aggiornato!", "info")
            else:
                player = {
                    "nome":            nome,
                    "cognome":         cognome,
                    "nascita":         self.vars["nascita"].get().strip(),
                    "ruolo":           self.vars["ruolo"].get().strip(),
                    "team":            self.vars["team"].get().strip(),
                    "stato":           self.stato_var.get(),
                    "max_season":      float(self.vars["max_season"].get() or 0),
                    "comp":            comp,
                    "bonus":           bonus,
                    "incompleto":      self._incompleto_var.get(),
                    "nota_incompleto": self._nota_text.get("1.0", "end").strip() if self._incompleto_var.get() else "",
                    "added":           datetime.now().isoformat(timespec="seconds"),
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
        inc = p.get("incompleto", False)
        self._incompleto_var.set(inc)
        self._nota_text.configure(state="normal")
        self._nota_text.delete("1.0", "end")
        if inc:
            self._nota_text.insert("1.0", p.get("nota_incompleto", ""))
            self._nota_text.configure(fg=TEXT_WHITE)
        else:
            self._nota_text.configure(state="disabled", fg=TEXT_DIM)
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
        self._incompleto_var.set(False)
        self._nota_text.configure(state="normal")
        self._nota_text.delete("1.0", "end")
        self._nota_text.configure(state="disabled", fg=TEXT_DIM)
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
            nome_str = f"{p.get('nome','')} {p.get('cognome','')}".strip()
            if p.get("incompleto"):
                nome_str = f"⚠ {nome_str}"
                tag = "incompleto"
            self.tree.insert("", "end", iid=str(i),
                values=(rank_lbl, nome_str,
                        p.get("ruolo",""), p.get("team","") or "-", stato,
                        p.get("comp", "-"), p.get("bonus", 0), score),
                tags=(tag,))
        self.tree.tag_configure("gold",          background="#2a2000", foreground="#f5c518")
        self.tree.tag_configure("silver",        background="#252525", foreground="#d8d8d8")
        self.tree.tag_configure("bronze",        background="#221200", foreground="#e08c40")
        self.tree.tag_configure("stato_attivo",  background="#162416", foreground="#66dd66")
        self.tree.tag_configure("stato_inattivo",background="#1e1e1e", foreground="#aaaaaa")
        self.tree.tag_configure("stato_infort",  background="#251a00", foreground="#ffb347")
        self.tree.tag_configure("stato_ritirato",background="#131326", foreground="#8899ee")
        self.tree.tag_configure("incompleto",    background="#281800", foreground="#ffb347")


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
        HoopButton(fc, "ESPORTA PDF", self._export_pdf,
                   bg_color="#3a0010", hover_color="#5a0018",
                   width=240, height=32, icon="📥", font_size=9).pack(pady=3)
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

    def _export_pdf(self):
        if not self._last_name:
            _hoop_dlg(self, "Genera prima un report radar.", "warn")
            return
        _export_figure_pdf(
            self.fig,
            title=f"Scouting_{self._last_name}",
            parent_win=self)

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
                score = compute_score(p)
                if score < 15: continue
                out.append({
                    "nome":    f"{p.get('nome','')} {p.get('cognome','')}".strip(),
                    "year":    year,
                    "nascita": p.get("nascita", ""),
                    "cat":     cat,
                    "team":    p.get("team", "") or "—",
                    "score":   score,
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
        HoopButton(ctrl, "ESPORTA PDF", self._export_pdf,
                   bg_color="#3a0010", hover_color="#5a0018",
                   width=170, height=34, icon="📥", font_size=10).pack(side="left", padx=8)

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
        self._global_fig = fig  # per export PDF
        # 3 rows × 3 cols — 7 charts + 2 empty slots
        datasets = [
            (man_pl,    MAN_CLUB_BONUSES,     "Club ♂",            ACCENT_BLU),
            (man_naz,   MAN_NAZIONI_BONUSES,  "Nazioni ♂",         ACCENT_RED),
            (women_pl,  WOMEN_CLUB_BONUSES,   "Club ♀",            ACCENT_PINK),
            (women_naz, WOMEN_NAZIONI_BONUSES,"Nazioni ♀",         "#c2185b"),
            (youth_pl,  YOUTH_BONUSES,        "Giovanili Club",    ACCENT_GRN),
            (youth_naz, YOUTH_NAZIONI_BONUSES,"Giovanili Nazioni", "#1b5e20"),
            (minor_pl,  PRESTIGE_2027,             "Minori ♂♀",        ACCENT_MINOR),
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
        palette = plt.colormaps["tab20"]
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

    def _export_pdf(self):
        if not hasattr(self, "_global_fig") or self._global_fig is None:
            _hoop_dlg(self, "Aggiorna prima i grafici.", "warn")
            return
        _export_figure_pdf(
            self._global_fig,
            title="Global_Coverage",
            parent_win=self)


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
                    score = compute_score(p)
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

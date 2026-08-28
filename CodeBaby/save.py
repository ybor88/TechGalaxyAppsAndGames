"""Salvataggio dei progressi (livelli sbloccati e stelle) in un piccolo file
JSON accanto al programma, cosi' il bambino ritrova i progressi la volta dopo.

Nota: quando l'app e' impacchettata con PyInstaller (--onefile), __file__ punta
a una cartella temporanea diversa ad ogni avvio: bisogna invece usare la
cartella del vero .exe (sys.executable), altrimenti i progressi si perderebbero
ogni volta."""
import json
import os
import sys

if getattr(sys, "frozen", False):
    _APP_DIR = os.path.dirname(os.path.abspath(sys.executable))
else:
    _APP_DIR = os.path.dirname(os.path.abspath(__file__))

SAVE_PATH = os.path.join(_APP_DIR, "salvataggio.json")
_FALLBACK_PATH = os.path.join(os.path.expanduser("~"), ".codebaby_salvataggio.json")

DEFAULT = {"unlocked": 1, "stars": {}}


def load():
    for path in (SAVE_PATH, _FALLBACK_PATH):
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            data.setdefault("unlocked", 1)
            data.setdefault("stars", {})
            return data
        except Exception:
            continue
    return dict(DEFAULT)


def save(data):
    for path in (SAVE_PATH, _FALLBACK_PATH):
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f)
            return
        except Exception:
            continue


def register_win(data, level_id, stars):
    key = str(level_id)
    prev = data["stars"].get(key, 0)
    data["stars"][key] = max(prev, stars)
    if level_id + 1 > data["unlocked"]:
        data["unlocked"] = level_id + 1
    save(data)

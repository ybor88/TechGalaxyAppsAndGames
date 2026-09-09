# Copyright (c) Roberto Di Flumeri
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent.parent
DATA_DIR = ROOT_DIR / "data"
DB_PATH = DATA_DIR / "sentinel.db"
SIGNATURES_PATH = DATA_DIR / "signatures.json"
QUARANTINE_DIR = DATA_DIR / "quarantine"

DATA_DIR.mkdir(parents=True, exist_ok=True)
QUARANTINE_DIR.mkdir(parents=True, exist_ok=True)

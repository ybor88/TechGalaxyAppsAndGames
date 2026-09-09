# Copyright (c) Roberto Di Flumeri
"""Local offline signature database (known-malicious file hashes).

This is the "offline" half of Cloud & Offline Protection: SentinelAI never
depends on a network connection to recognize a known threat. See cloud.py
for the optional online lookup that complements it.
"""
import json
from datetime import datetime, timezone

from .paths import SIGNATURES_PATH

_cache = None


def _load():
    global _cache
    if _cache is None:
        with open(SIGNATURES_PATH, "r", encoding="utf-8") as f:
            _cache = json.load(f)
    return _cache


def reload():
    global _cache
    _cache = None
    return _load()


def lookup_hash(sha256: str):
    data = _load()
    return data.get("hashes", {}).get(sha256.lower())


def signature_count():
    return len(_load().get("hashes", {}))


def database_version():
    return _load().get("version", "sconosciuta")


def database_file_mtime():
    ts = SIGNATURES_PATH.stat().st_mtime
    return datetime.fromtimestamp(ts, tz=timezone.utc)

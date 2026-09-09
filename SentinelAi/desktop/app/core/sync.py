# Copyright (c) Roberto Di Flumeri
"""Sicurezza Multipiattaforma: exports/imports SentinelAI settings as a
portable JSON profile, so the same protection preferences can be carried
to another SentinelAI installation (desktop or, in future, Android)."""
import json

from . import database

_EXPORTED_KEYS = [
    "auto_quarantine_threshold",
    "watched_folders",
    "start_on_login",
    "cloud_lookup_enabled",
]


def export_profile() -> dict:
    return {
        "app": "SentinelAI",
        "profile_version": 1,
        "settings": {key: database.get_setting(key, "") for key in _EXPORTED_KEYS},
    }


def export_profile_json() -> str:
    return json.dumps(export_profile(), indent=2, ensure_ascii=False)


def import_profile_json(raw: str):
    data = json.loads(raw)
    settings = data.get("settings", {})
    for key in _EXPORTED_KEYS:
        if key in settings:
            database.set_setting(key, settings[key])

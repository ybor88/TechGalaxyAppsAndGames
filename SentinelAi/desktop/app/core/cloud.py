# Copyright (c) Roberto Di Flumeri
"""Cloud & Offline Protection.

Offline detection (signatures.py + scanner.py heuristics) always runs and
never requires network access. Cloud lookup is an optional, explicit
enhancement: when the user supplies their own VirusTotal API key in
Settings, a suspicious file's hash can be checked against VirusTotal's
public database. Without a key, SentinelAI stays fully offline and says so
honestly in the UI rather than faking a connection.
"""
import json
import urllib.request
import urllib.error

from . import database

VT_HASH_URL = "https://www.virustotal.com/api/v3/files/{}"


class CloudLookupError(Exception):
    pass


def is_cloud_configured() -> bool:
    return bool(database.get_setting("vt_api_key", "")) and database.get_setting(
        "cloud_lookup_enabled", "0"
    ) == "1"


def lookup_hash_virustotal(sha256: str, api_key: str, timeout=10) -> dict:
    """Real lookup against the VirusTotal public API. Requires the user's
    own API key. Returns a summary dict, or raises CloudLookupError."""
    req = urllib.request.Request(
        VT_HASH_URL.format(sha256),
        headers={"x-apikey": api_key, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return {"known": False, "malicious": 0, "suspicious": 0, "harmless": 0}
        raise CloudLookupError(f"Errore HTTP {exc.code} da VirusTotal") from exc
    except urllib.error.URLError as exc:
        raise CloudLookupError(f"Connessione al cloud non riuscita: {exc.reason}") from exc

    attrs = payload.get("data", {}).get("attributes", {})
    stats = attrs.get("last_analysis_stats", {})
    return {
        "known": True,
        "malicious": stats.get("malicious", 0),
        "suspicious": stats.get("suspicious", 0),
        "harmless": stats.get("harmless", 0),
        "name": attrs.get("meaningful_name", ""),
    }

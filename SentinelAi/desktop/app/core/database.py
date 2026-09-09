# Copyright (c) Roberto Di Flumeri
import sqlite3
from datetime import datetime, timezone

from .paths import DB_PATH

_SCHEMA = """
CREATE TABLE IF NOT EXISTS scans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_path TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    files_scanned INTEGER DEFAULT 0,
    threats_found INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS detections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id INTEGER,
    file_path TEXT NOT NULL,
    sha256 TEXT,
    threat_name TEXT,
    risk_score INTEGER NOT NULL,
    verdict TEXT NOT NULL,
    reasons TEXT,
    source TEXT NOT NULL,
    detected_at TEXT NOT NULL,
    action TEXT DEFAULT 'none'
);

CREATE TABLE IF NOT EXISTS quarantine (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    original_path TEXT NOT NULL,
    quarantine_path TEXT NOT NULL,
    threat_name TEXT,
    risk_score INTEGER,
    quarantined_at TEXT NOT NULL,
    restored INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    level TEXT NOT NULL,
    message TEXT NOT NULL
);
"""

_DEFAULT_SETTINGS = {
    "realtime_enabled": "0",
    "watched_folders": "",
    "auto_quarantine_threshold": "70",
    "start_on_login": "0",
    "vt_api_key": "",
    "cloud_lookup_enabled": "0",
}


def _connect():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = _connect()
    try:
        conn.executescript(_SCHEMA)
        for key, value in _DEFAULT_SETTINGS.items():
            conn.execute(
                "INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)",
                (key, value),
            )
        conn.commit()
    finally:
        conn.close()


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def get_setting(key, default=None):
    conn = _connect()
    try:
        row = conn.execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
        return row["value"] if row else default
    finally:
        conn.close()


def set_setting(key, value):
    conn = _connect()
    try:
        conn.execute(
            "INSERT INTO settings (key, value) VALUES (?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, str(value)),
        )
        conn.commit()
    finally:
        conn.close()


def log_event(level, message):
    conn = _connect()
    try:
        conn.execute(
            "INSERT INTO events (timestamp, level, message) VALUES (?, ?, ?)",
            (now_iso(), level, message),
        )
        conn.commit()
    finally:
        conn.close()


def recent_events(limit=100):
    conn = _connect()
    try:
        rows = conn.execute(
            "SELECT * FROM events ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def start_scan(target_path):
    conn = _connect()
    try:
        cur = conn.execute(
            "INSERT INTO scans (target_path, started_at) VALUES (?, ?)",
            (target_path, now_iso()),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def finish_scan(scan_id, files_scanned, threats_found):
    conn = _connect()
    try:
        conn.execute(
            "UPDATE scans SET finished_at = ?, files_scanned = ?, threats_found = ? WHERE id = ?",
            (now_iso(), files_scanned, threats_found, scan_id),
        )
        conn.commit()
    finally:
        conn.close()


def add_detection(scan_id, result, source, action="none"):
    conn = _connect()
    try:
        cur = conn.execute(
            "INSERT INTO detections "
            "(scan_id, file_path, sha256, threat_name, risk_score, verdict, reasons, source, detected_at, action) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                scan_id,
                result.path,
                result.sha256,
                result.threat_name,
                result.risk_score,
                result.verdict,
                "; ".join(result.reasons),
                source,
                now_iso(),
                action,
            ),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def set_detection_action(detection_id, action):
    conn = _connect()
    try:
        conn.execute("UPDATE detections SET action = ? WHERE id = ?", (action, detection_id))
        conn.commit()
    finally:
        conn.close()


def recent_detections(limit=200):
    conn = _connect()
    try:
        rows = conn.execute(
            "SELECT * FROM detections ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def stats_today():
    conn = _connect()
    try:
        today = datetime.now(timezone.utc).date().isoformat()
        scanned = conn.execute(
            "SELECT COALESCE(SUM(files_scanned), 0) AS c FROM scans WHERE started_at LIKE ?",
            (f"{today}%",),
        ).fetchone()["c"]
        threats = conn.execute(
            "SELECT COUNT(*) AS c FROM detections WHERE detected_at LIKE ? AND risk_score >= 30",
            (f"{today}%",),
        ).fetchone()["c"]
        last_scan = conn.execute(
            "SELECT finished_at FROM scans WHERE finished_at IS NOT NULL ORDER BY id DESC LIMIT 1"
        ).fetchone()
        return {
            "files_scanned_today": scanned,
            "threats_today": threats,
            "last_scan": last_scan["finished_at"] if last_scan else None,
        }
    finally:
        conn.close()


def add_quarantine(original_path, quarantine_path, threat_name, risk_score):
    conn = _connect()
    try:
        cur = conn.execute(
            "INSERT INTO quarantine (original_path, quarantine_path, threat_name, risk_score, quarantined_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (original_path, quarantine_path, threat_name, risk_score, now_iso()),
        )
        conn.commit()
        return cur.lastrowid
    finally:
        conn.close()


def list_quarantine(include_restored=False):
    conn = _connect()
    try:
        if include_restored:
            rows = conn.execute("SELECT * FROM quarantine ORDER BY id DESC").fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM quarantine WHERE restored = 0 ORDER BY id DESC"
            ).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def get_quarantine_item(item_id):
    conn = _connect()
    try:
        row = conn.execute("SELECT * FROM quarantine WHERE id = ?", (item_id,)).fetchone()
        return dict(row) if row else None
    finally:
        conn.close()


def mark_quarantine_restored(item_id):
    conn = _connect()
    try:
        conn.execute("UPDATE quarantine SET restored = 1 WHERE id = ?", (item_id,))
        conn.commit()
    finally:
        conn.close()


def delete_quarantine_record(item_id):
    conn = _connect()
    try:
        conn.execute("DELETE FROM quarantine WHERE id = ?", (item_id,))
        conn.commit()
    finally:
        conn.close()


def quarantine_count():
    conn = _connect()
    try:
        row = conn.execute("SELECT COUNT(*) AS c FROM quarantine WHERE restored = 0").fetchone()
        return row["c"]
    finally:
        conn.close()

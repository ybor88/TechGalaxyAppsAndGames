# Copyright (c) Roberto Di Flumeri
"""Quarantine: neutralizes a flagged file by moving it out of place and
XOR-obfuscating its bytes so it can no longer be double-clicked or executed
by accident, while keeping it recoverable via restore().
"""
import os
import uuid
from pathlib import Path

from . import database
from .paths import QUARANTINE_DIR

_XOR_KEY = 0xA5


def _xor_transform(data: bytes) -> bytes:
    return bytes(b ^ _XOR_KEY for b in data)


def quarantine_file(original_path: str, threat_name: str, risk_score: int) -> int:
    src = Path(original_path)
    token = uuid.uuid4().hex
    dest = QUARANTINE_DIR / f"{token}.quar"

    with open(src, "rb") as f:
        data = f.read()
    with open(dest, "wb") as f:
        f.write(_xor_transform(data))
    os.remove(src)

    record_id = database.add_quarantine(str(src), str(dest), threat_name, risk_score)
    database.log_event("warning", f"Messo in quarantena: {src.name} ({threat_name})")
    return record_id


def restore_file(item_id: int) -> str:
    item = database.get_quarantine_item(item_id)
    if not item:
        raise ValueError("Elemento in quarantena non trovato")

    quarantine_path = Path(item["quarantine_path"])
    original_path = Path(item["original_path"])
    original_path.parent.mkdir(parents=True, exist_ok=True)

    target = original_path
    if target.exists():
        target = target.with_name(f"{target.stem}_ripristinato{target.suffix}")

    with open(quarantine_path, "rb") as f:
        data = f.read()
    with open(target, "wb") as f:
        f.write(_xor_transform(data))
    os.remove(quarantine_path)

    database.mark_quarantine_restored(item_id)
    database.log_event("info", f"Ripristinato dalla quarantena: {target.name}")
    return str(target)


def delete_permanently(item_id: int):
    item = database.get_quarantine_item(item_id)
    if not item:
        raise ValueError("Elemento in quarantena non trovato")

    quarantine_path = Path(item["quarantine_path"])
    if quarantine_path.exists():
        os.remove(quarantine_path)
    database.delete_quarantine_record(item_id)
    database.log_event("info", f"Eliminato definitivamente: {Path(item['original_path']).name}")

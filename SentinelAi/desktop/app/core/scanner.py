# Copyright (c) Roberto Di Flumeri
"""Detection engine: hash-based signature matching + heuristic risk scoring.

The heuristic scorer is the "AI Advanced Detection" / "Predictive Defense"
feature: a transparent, weighted rule model (not a black-box neural network)
that flags suspicious traits before a file is known-bad in any database.
"""
import hashlib
import math
import os
from dataclasses import dataclass, field
from pathlib import Path

from . import signatures

EXECUTABLE_EXTENSIONS = {
    ".exe", ".dll", ".scr", ".bat", ".cmd", ".vbs", ".js", ".ps1",
    ".msi", ".com", ".jar", ".hta",
}

DOCUMENT_EXTENSIONS = {".pdf", ".doc", ".docx", ".xls", ".xlsx", ".jpg", ".jpeg", ".png", ".txt", ".mp3", ".mp4"}

SUSPICIOUS_KEYWORDS = [
    "crack", "keygen", "loader", "activator", "patcher", "hack",
]

AUTORUN_NAMES = {"autorun.inf"}

CHUNK_SIZE = 1024 * 1024


@dataclass
class DetectionResult:
    path: str
    sha256: str = ""
    risk_score: int = 0
    reasons: list = field(default_factory=list)
    threat_name: str = ""
    verdict: str = "clean"  # clean | suspicious | malicious
    error: str = ""

    @property
    def is_flagged(self):
        return self.verdict in ("suspicious", "malicious")


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(CHUNK_SIZE)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def _shannon_entropy(data: bytes) -> float:
    if not data:
        return 0.0
    freq = [0] * 256
    for b in data:
        freq[b] += 1
    length = len(data)
    entropy = 0.0
    for count in freq:
        if count:
            p = count / length
            entropy -= p * math.log2(p)
    return entropy


def _has_double_extension(name: str) -> bool:
    parts = name.lower().split(".")
    if len(parts) < 3:
        return False
    last_ext = f".{parts[-1]}"
    second_ext = f".{parts[-2]}"
    return last_ext in EXECUTABLE_EXTENSIONS and second_ext in DOCUMENT_EXTENSIONS


def _verdict_for_score(score: int) -> str:
    if score >= 70:
        return "malicious"
    if score >= 30:
        return "suspicious"
    return "clean"


def scan_file(file_path) -> DetectionResult:
    path = Path(file_path)
    result = DetectionResult(path=str(path))

    try:
        if not path.is_file():
            result.error = "File non trovato"
            return result

        stat = path.stat()
        name = path.name
        ext = path.suffix.lower()

        try:
            result.sha256 = sha256_of(path)
        except (PermissionError, OSError) as exc:
            result.error = f"Impossibile leggere il file: {exc}"
            return result

        known = signatures.lookup_hash(result.sha256)
        if known:
            result.risk_score = 100
            result.threat_name = known
            result.reasons.append("Hash corrispondente a firma nota nel database offline")
            result.verdict = "malicious"
            return result

        score = 0
        reasons = []

        if name.lower() in AUTORUN_NAMES:
            score += 20
            reasons.append("File autorun.inf rilevato")

        if _has_double_extension(name):
            score += 35
            reasons.append("Doppia estensione sospetta (es. .pdf.exe)")

        lowered = name.lower()
        if any(k in lowered for k in SUSPICIOUS_KEYWORDS) and ext in EXECUTABLE_EXTENSIONS:
            score += 15
            reasons.append("Nome file contiene termini associati a software pirata/malevolo")

        if ext in EXECUTABLE_EXTENSIONS and stat.st_size > 0:
            try:
                with open(path, "rb") as f:
                    sample = f.read(min(stat.st_size, 4 * 1024 * 1024))
                entropy = _shannon_entropy(sample)
                if entropy >= 7.5:
                    score += 25
                    reasons.append(f"Entropia elevata ({entropy:.2f}/8.0): possibile file impacchettato/offuscato")
            except (PermissionError, OSError):
                pass

        if ext in EXECUTABLE_EXTENSIONS and stat.st_size == 0:
            score += 10
            reasons.append("Eseguibile di dimensione zero (anomalo)")

        parent_parts = {p.lower() for p in path.parts}
        if ext in EXECUTABLE_EXTENSIONS and ({"temp", "tmp"} & parent_parts):
            score += 10
            reasons.append("Eseguibile in esecuzione da una cartella temporanea")

        score = min(score, 99)  # 100 reserved for confirmed signature matches
        result.risk_score = score
        result.reasons = reasons
        result.verdict = _verdict_for_score(score)
        if result.verdict == "malicious":
            result.threat_name = "Heuristic.Suspicious.Generic"
        elif result.verdict == "suspicious":
            result.threat_name = "Heuristic.PotentiallyUnwanted"

        return result
    except Exception as exc:  # defensive: never crash the scan loop
        result.error = str(exc)
        return result


def iter_files(target_path, recursive=True):
    path = Path(target_path)
    if path.is_file():
        yield path
        return
    if not path.is_dir():
        return
    if recursive:
        for root, _dirs, files in os.walk(path):
            for fname in files:
                yield Path(root) / fname
    else:
        for entry in path.iterdir():
            if entry.is_file():
                yield entry

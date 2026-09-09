# Copyright (c) Roberto Di Flumeri
"""Real-Time Protection: watches chosen folders and scans new/modified files
as they appear, emitting Qt signals (thread-safe, queued to the GUI thread)."""
import time

from PySide6.QtCore import QObject, Signal
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer

from . import database, quarantine, scanner

SETTLE_DELAY_SECONDS = 1.5


class _Handler(FileSystemEventHandler):
    def __init__(self, on_file_ready):
        super().__init__()
        self._on_file_ready = on_file_ready

    def on_created(self, event):
        if not event.is_directory:
            self._on_file_ready(event.src_path)

    def on_modified(self, event):
        if not event.is_directory:
            self._on_file_ready(event.src_path)


class RealtimeMonitor(QObject):
    file_scanned = Signal(object)  # DetectionResult
    threat_blocked = Signal(object, str)  # DetectionResult, quarantine_path
    error = Signal(str)

    def __init__(self, auto_quarantine_threshold=70, parent=None):
        super().__init__(parent)
        self.auto_quarantine_threshold = auto_quarantine_threshold
        self._observer = None
        self._watched_paths = []

    @property
    def is_running(self):
        return self._observer is not None and self._observer.is_alive()

    def start(self, folders):
        self.stop()
        self._observer = Observer()
        handler = _Handler(self._handle_file_event)
        started_any = False
        for folder in folders:
            try:
                self._observer.schedule(handler, folder, recursive=False)
                started_any = True
            except (FileNotFoundError, OSError) as exc:
                self.error.emit(f"Impossibile monitorare {folder}: {exc}")
        if started_any:
            self._observer.start()
            self._watched_paths = list(folders)
            database.log_event("info", f"Protezione in tempo reale avviata su {len(folders)} cartelle")

    def stop(self):
        if self._observer is not None:
            self._observer.stop()
            self._observer.join(timeout=3)
            self._observer = None
            database.log_event("info", "Protezione in tempo reale disattivata")

    def _handle_file_event(self, path):
        time.sleep(SETTLE_DELAY_SECONDS)  # let the writer finish before reading
        try:
            result = scanner.scan_file(path)
        except Exception as exc:  # defensive: a watcher must never crash
            self.error.emit(str(exc))
            return

        if result.error:
            return

        self.file_scanned.emit(result)

        if result.is_flagged:
            database.add_detection(None, result, source="realtime")
            if result.risk_score >= self.auto_quarantine_threshold:
                try:
                    quarantine.quarantine_file(result.path, result.threat_name, result.risk_score)
                    self.threat_blocked.emit(result, result.path)
                except (OSError, PermissionError) as exc:
                    self.error.emit(f"Quarantena non riuscita per {path}: {exc}")

# Copyright (c) Roberto Di Flumeri
from PySide6.QtCore import QThread, Signal

from . import database, scanner


class ScanWorker(QThread):
    progress = Signal(int, str)  # files scanned so far, current file name
    file_result = Signal(object)  # DetectionResult
    finished_scan = Signal(int, int, int)  # scan_id, files_scanned, threats_found

    def __init__(self, target_path, recursive=True, parent=None):
        super().__init__(parent)
        self.target_path = target_path
        self.recursive = recursive
        self._stop_requested = False

    def stop(self):
        self._stop_requested = True

    def run(self):
        scan_id = database.start_scan(self.target_path)
        files_scanned = 0
        threats_found = 0

        for file_path in scanner.iter_files(self.target_path, recursive=self.recursive):
            if self._stop_requested:
                break
            result = scanner.scan_file(file_path)
            files_scanned += 1
            self.progress.emit(files_scanned, str(file_path))

            if not result.error:
                if result.is_flagged:
                    threats_found += 1
                    database.add_detection(scan_id, result, source="manual")
                self.file_result.emit(result)

        database.finish_scan(scan_id, files_scanned, threats_found)
        self.finished_scan.emit(scan_id, files_scanned, threats_found)

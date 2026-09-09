# Copyright (c) Roberto Di Flumeri
from pathlib import Path

from PySide6.QtWidgets import (
    QFileDialog, QHBoxLayout, QHeaderView, QLabel, QMessageBox, QProgressBar,
    QPushButton, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget,
)

from ..core import quarantine
from ..core.scan_worker import ScanWorker
from ..widgets import verdict_color, verdict_label


class ScanPage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window
        self.worker = None
        self._results = {}  # row -> DetectionResult

        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(16)

        title = QLabel("Scansione")
        title.setObjectName("pageTitle")
        subtitle = QLabel("Rilevamento AI Avanzato: analisi delle firme e delle euristiche di rischio")
        subtitle.setObjectName("pageSubtitle")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        controls_row = QHBoxLayout()
        self.target_label = QLabel(str(Path.home()))
        self.target_label.setObjectName("muted")
        pick_file_btn = QPushButton("Scegli file")
        pick_file_btn.setObjectName("secondary")
        pick_file_btn.clicked.connect(self._pick_file)
        pick_folder_btn = QPushButton("Scegli cartella")
        pick_folder_btn.setObjectName("secondary")
        pick_folder_btn.clicked.connect(self._pick_folder)
        self.scan_btn = QPushButton("Avvia scansione")
        self.scan_btn.clicked.connect(self._start_scan)
        self.stop_btn = QPushButton("Interrompi")
        self.stop_btn.setObjectName("secondary")
        self.stop_btn.clicked.connect(self._stop_scan)
        self.stop_btn.setEnabled(False)

        controls_row.addWidget(pick_file_btn)
        controls_row.addWidget(pick_folder_btn)
        controls_row.addStretch()
        controls_row.addWidget(self.stop_btn)
        controls_row.addWidget(self.scan_btn)
        layout.addLayout(controls_row)
        layout.addWidget(self.target_label)

        self.progress_bar = QProgressBar()
        self.progress_bar.setRange(0, 0)
        self.progress_bar.setVisible(False)
        layout.addWidget(self.progress_bar)

        self.status_label = QLabel("")
        self.status_label.setObjectName("muted")
        layout.addWidget(self.status_label)

        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(["File", "Verdetto", "Rischio", "Azione"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeToContents)
        self.table.verticalHeader().setVisible(False)
        self.table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.table.setWordWrap(False)
        layout.addWidget(self.table)

        self._target_path = str(Path.home())

    def _pick_file(self):
        path, _ = QFileDialog.getOpenFileName(self, "Scegli un file da scansionare")
        if path:
            self._target_path = path
            self.target_label.setText(path)

    def _pick_folder(self):
        path = QFileDialog.getExistingDirectory(self, "Scegli una cartella da scansionare")
        if path:
            self._target_path = path
            self.target_label.setText(path)

    def run_quick_scan(self, target_path):
        self._target_path = target_path
        self.target_label.setText(target_path)
        self._start_scan()

    def _start_scan(self):
        if self.worker is not None and self.worker.isRunning():
            return
        self.table.setRowCount(0)
        self._results.clear()
        self.progress_bar.setVisible(True)
        self.status_label.setText("Scansione in corso…")
        self.scan_btn.setEnabled(False)
        self.stop_btn.setEnabled(True)

        self.worker = ScanWorker(self._target_path)
        self.worker.progress.connect(self._on_progress)
        self.worker.file_result.connect(self._on_result)
        self.worker.finished_scan.connect(self._on_finished)
        self.worker.start()

    def _stop_scan(self):
        if self.worker is not None:
            self.worker.stop()
            self.status_label.setText("Interruzione in corso…")

    def _on_progress(self, count, current_file):
        self.status_label.setText(f"File analizzati: {count} — {Path(current_file).name}")

    def _on_result(self, result):
        if not result.is_flagged:
            return
        row = self.table.rowCount()
        self.table.insertRow(row)
        self._results[row] = result

        self.table.setItem(row, 0, QTableWidgetItem(result.path))
        verdict_item = QTableWidgetItem(verdict_label(result.verdict))
        verdict_item.setForeground(_qcolor(verdict_color(result.verdict)))
        self.table.setItem(row, 1, verdict_item)
        self.table.setItem(row, 2, QTableWidgetItem(f"{result.risk_score}/100"))

        action_btn = QPushButton("Metti in quarantena")
        action_btn.setObjectName("secondary")
        action_btn.clicked.connect(lambda _=None, r=row: self._quarantine_row(r))
        self.table.setCellWidget(row, 3, action_btn)

    def _quarantine_row(self, row):
        result = self._results.get(row)
        if not result:
            return
        try:
            quarantine.quarantine_file(result.path, result.threat_name or "Sospetto", result.risk_score)
            self.table.setCellWidget(row, 3, QLabel("In quarantena"))
        except (OSError, PermissionError) as exc:
            QMessageBox.warning(self, "Quarantena non riuscita", str(exc))

    def _on_finished(self, scan_id, files_scanned, threats_found):
        self.progress_bar.setVisible(False)
        self.scan_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)
        self.status_label.setText(
            f"Scansione completata: {files_scanned} file analizzati, {threats_found} minacce rilevate"
        )
        self.main_window.refresh_dashboard()


def _qcolor(hex_str):
    from PySide6.QtGui import QColor
    return QColor(hex_str)

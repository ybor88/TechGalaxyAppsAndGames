# Copyright (c) Roberto Di Flumeri
from pathlib import Path

from PySide6.QtWidgets import (
    QCheckBox, QFileDialog, QHBoxLayout, QLabel, QListWidget, QListWidgetItem,
    QPushButton, QVBoxLayout, QWidget,
)

from ..core import database
from ..widgets import verdict_color, verdict_label


class RealtimePage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window

        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(16)

        title = QLabel("Protezione in Tempo Reale")
        title.setObjectName("pageTitle")
        subtitle = QLabel("Monitora le cartelle scelte e analizza automaticamente ogni nuovo file")
        subtitle.setObjectName("pageSubtitle")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        toggle_row = QHBoxLayout()
        self.toggle_checkbox = QCheckBox("Attiva protezione in tempo reale")
        self.toggle_checkbox.stateChanged.connect(self._on_toggle)
        toggle_row.addWidget(self.toggle_checkbox)
        toggle_row.addStretch()
        layout.addLayout(toggle_row)

        folders_title = QLabel("Cartelle monitorate")
        folders_title.setObjectName("sectionTitle")
        layout.addWidget(folders_title)

        self.folders_list = QListWidget()
        self.folders_list.setMaximumHeight(120)
        layout.addWidget(self.folders_list)

        folder_actions = QHBoxLayout()
        add_btn = QPushButton("Aggiungi cartella")
        add_btn.setObjectName("secondary")
        add_btn.clicked.connect(self._add_folder)
        remove_btn = QPushButton("Rimuovi selezionata")
        remove_btn.setObjectName("secondary")
        remove_btn.clicked.connect(self._remove_folder)
        folder_actions.addWidget(add_btn)
        folder_actions.addWidget(remove_btn)
        folder_actions.addStretch()
        layout.addLayout(folder_actions)

        feed_title = QLabel("Attività recente")
        feed_title.setObjectName("sectionTitle")
        layout.addWidget(feed_title)

        self.feed_list = QListWidget()
        layout.addWidget(self.feed_list)

        self._load_folders()
        self._load_state()

    def _load_folders(self):
        raw = database.get_setting("watched_folders", "")
        folders = [f for f in raw.split("|") if f]
        if not folders:
            folders = [str(Path.home() / "Downloads")]
        self.folders_list.clear()
        for f in folders:
            self.folders_list.addItem(f)

    def _save_folders(self):
        folders = [self.folders_list.item(i).text() for i in range(self.folders_list.count())]
        database.set_setting("watched_folders", "|".join(folders))
        return folders

    def _add_folder(self):
        path = QFileDialog.getExistingDirectory(self, "Scegli una cartella da monitorare")
        if path:
            self.folders_list.addItem(path)
            self._save_folders()
            if self.toggle_checkbox.isChecked():
                self._restart_monitor()

    def _remove_folder(self):
        row = self.folders_list.currentRow()
        if row >= 0:
            self.folders_list.takeItem(row)
            self._save_folders()
            if self.toggle_checkbox.isChecked():
                self._restart_monitor()

    def _load_state(self):
        enabled = database.get_setting("realtime_enabled", "0") == "1"
        self.toggle_checkbox.blockSignals(True)
        self.toggle_checkbox.setChecked(enabled)
        self.toggle_checkbox.blockSignals(False)
        if enabled:
            self._restart_monitor()

    def _on_toggle(self, state):
        enabled = bool(state)
        database.set_setting("realtime_enabled", "1" if enabled else "0")
        if enabled:
            self._restart_monitor()
        else:
            self.main_window.realtime_monitor.stop()
        self.main_window.refresh_dashboard()

    def _restart_monitor(self):
        folders = self._save_folders()
        threshold = int(database.get_setting("auto_quarantine_threshold", "70"))
        self.main_window.realtime_monitor.auto_quarantine_threshold = threshold
        self.main_window.realtime_monitor.start(folders)
        self.main_window.refresh_dashboard()

    def on_file_scanned(self, result):
        color = verdict_color(result.verdict)
        item = QListWidgetItem(f"{Path(result.path).name} — {verdict_label(result.verdict)} ({result.risk_score}/100)")
        item.setForeground(_qcolor(color))
        self.feed_list.insertItem(0, item)
        if self.feed_list.count() > 200:
            self.feed_list.takeItem(self.feed_list.count() - 1)

    def on_threat_blocked(self, result, quarantine_path):
        item = QListWidgetItem(f"🛑 Bloccato e messo in quarantena: {Path(result.path).name}")
        item.setForeground(_qcolor("#ff4d6d"))
        self.feed_list.insertItem(0, item)
        self.main_window.refresh_dashboard()


def _qcolor(hex_str):
    from PySide6.QtGui import QColor
    return QColor(hex_str)

# Copyright (c) Roberto Di Flumeri
from pathlib import Path

from PySide6.QtCore import QThread, Signal
from PySide6.QtWidgets import (
    QCheckBox, QFileDialog, QHBoxLayout, QLabel, QLineEdit, QMessageBox,
    QPushButton, QScrollArea, QVBoxLayout, QWidget,
)

from ..core import cloud, database, scanner, signatures
from ..widgets import StatCard


class _LookupWorker(QThread):
    result = Signal(dict)
    failed = Signal(str)

    def __init__(self, sha256, api_key, parent=None):
        super().__init__(parent)
        self.sha256 = sha256
        self.api_key = api_key

    def run(self):
        try:
            data = cloud.lookup_hash_virustotal(self.sha256, self.api_key)
            self.result.emit(data)
        except cloud.CloudLookupError as exc:
            self.failed.emit(str(exc))


class CloudPage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window
        self._lookup_worker = None

        outer_layout = QVBoxLayout(self)
        outer_layout.setContentsMargins(0, 0, 0, 0)

        scroll_area = QScrollArea()
        scroll_area.setWidgetResizable(True)
        scroll_area.setFrameShape(QScrollArea.NoFrame)
        outer_layout.addWidget(scroll_area)

        content = QWidget()
        scroll_area.setWidget(content)

        layout = QVBoxLayout(content)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(16)

        title = QLabel("Protezione Cloud e Offline")
        title.setObjectName("pageTitle")
        subtitle = QLabel("Il database delle firme locali è sempre attivo. La verifica cloud è opzionale.")
        subtitle.setObjectName("pageSubtitle")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        stats_row = QHBoxLayout()
        self.sig_count_card = StatCard("Firme offline", signatures.signature_count())
        self.sig_version_card = StatCard("Versione database", signatures.database_version())
        stats_row.addWidget(self.sig_count_card)
        stats_row.addWidget(self.sig_version_card)
        layout.addLayout(stats_row)

        offline_note = QLabel(
            "La scansione offline confronta l'hash SHA-256 di ogni file con il database locale "
            "delle firme note e applica euristiche di rischio, senza mai richiedere una connessione."
        )
        offline_note.setObjectName("muted")
        offline_note.setWordWrap(True)
        layout.addWidget(offline_note)

        cloud_title = QLabel("Verifica cloud (VirusTotal)")
        cloud_title.setObjectName("sectionTitle")
        layout.addWidget(cloud_title)

        cloud_note = QLabel(
            "Inserisci la tua chiave API personale VirusTotal per verificare l'hash di un file "
            "sospetto contro un database cloud di terze parti. Senza chiave, SentinelAI resta "
            "esclusivamente offline."
        )
        cloud_note.setObjectName("muted")
        cloud_note.setWordWrap(True)
        layout.addWidget(cloud_note)

        key_row = QHBoxLayout()
        self.key_input = QLineEdit()
        self.key_input.setPlaceholderText("Chiave API VirusTotal")
        self.key_input.setEchoMode(QLineEdit.Password)
        self.key_input.setText(database.get_setting("vt_api_key", ""))
        save_key_btn = QPushButton("Salva")
        save_key_btn.setObjectName("secondary")
        save_key_btn.clicked.connect(self._save_key)
        key_row.addWidget(self.key_input)
        key_row.addWidget(save_key_btn)
        layout.addLayout(key_row)

        self.enable_checkbox = QCheckBox("Abilita verifica cloud per i file sospetti")
        self.enable_checkbox.setChecked(database.get_setting("cloud_lookup_enabled", "0") == "1")
        self.enable_checkbox.stateChanged.connect(self._toggle_enabled)
        layout.addWidget(self.enable_checkbox)

        self.status_label = QLabel()
        self.status_label.setObjectName("muted")
        layout.addWidget(self.status_label)
        self._update_status()

        test_row = QHBoxLayout()
        test_btn = QPushButton("Verifica un file nel cloud")
        test_btn.clicked.connect(self._test_lookup)
        test_row.addWidget(test_btn)
        test_row.addStretch()
        layout.addLayout(test_row)

        self.result_label = QLabel("")
        self.result_label.setWordWrap(True)
        layout.addWidget(self.result_label)

        layout.addStretch()

    def _save_key(self):
        database.set_setting("vt_api_key", self.key_input.text().strip())
        self._update_status()
        QMessageBox.information(self, "Salvato", "Chiave API salvata.")

    def _toggle_enabled(self, state):
        database.set_setting("cloud_lookup_enabled", "1" if bool(state) else "0")
        self._update_status()

    def _update_status(self):
        if cloud.is_cloud_configured():
            self.status_label.setText("☁ Cloud: configurato e attivo")
            self.status_label.setStyleSheet("color: #22e6a0;")
        else:
            self.status_label.setText("○ Cloud: non configurato — SentinelAI opera in modalità offline")
            self.status_label.setStyleSheet("")

    def _test_lookup(self):
        api_key = database.get_setting("vt_api_key", "")
        if not api_key:
            QMessageBox.warning(self, "Chiave mancante", "Inserisci e salva una chiave API VirusTotal prima di continuare.")
            return
        path, _ = QFileDialog.getOpenFileName(self, "Scegli un file da verificare nel cloud")
        if not path:
            return
        sha256 = scanner.sha256_of(Path(path))
        self.result_label.setText("Verifica in corso…")
        self._lookup_worker = _LookupWorker(sha256, api_key)
        self._lookup_worker.result.connect(self._on_lookup_result)
        self._lookup_worker.failed.connect(self._on_lookup_failed)
        self._lookup_worker.start()

    def _on_lookup_result(self, data):
        if not data.get("known"):
            self.result_label.setText("Nessun risultato: file sconosciuto a VirusTotal.")
            return
        self.result_label.setText(
            f"Rilevamenti: {data['malicious']} motori malevoli, "
            f"{data['suspicious']} sospetti, {data['harmless']} puliti."
        )

    def _on_lookup_failed(self, message):
        self.result_label.setText(f"Errore: {message}")

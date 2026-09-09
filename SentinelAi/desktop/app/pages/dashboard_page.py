# Copyright (c) Roberto Di Flumeri
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QGridLayout, QHBoxLayout, QLabel, QScrollArea, QVBoxLayout, QWidget,
)

from ..core import database, signatures
from ..widgets import FeatureTile, StatCard, ShieldStatusCard


class DashboardPage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window

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
        layout.setSpacing(20)

        header = QVBoxLayout()
        title = QLabel("Dashboard")
        title.setObjectName("pageTitle")
        subtitle = QLabel("Panoramica dello stato di protezione del sistema")
        subtitle.setObjectName("pageSubtitle")
        header.addWidget(title)
        header.addWidget(subtitle)
        layout.addLayout(header)

        self.shield_card = ShieldStatusCard()
        layout.addWidget(self.shield_card)

        stats_row = QHBoxLayout()
        stats_row.setSpacing(16)
        self.stat_scanned = StatCard("File scansionati oggi")
        self.stat_threats = StatCard("Minacce oggi")
        self.stat_quarantine = StatCard("In quarantena")
        self.stat_db = StatCard("Versione database")
        for card in (self.stat_scanned, self.stat_threats, self.stat_quarantine, self.stat_db):
            stats_row.addWidget(card)
        layout.addLayout(stats_row)

        features_title = QLabel("Funzionalità di protezione")
        features_title.setObjectName("sectionTitle")
        layout.addWidget(features_title)

        grid = QGridLayout()
        grid.setSpacing(16)
        tiles = [
            ("🧠", "Rilevamento AI Avanzato", "Analisi euristica dei file con punteggio di rischio", "scan"),
            ("⚡", "Protezione in Tempo Reale", "Monitoraggio live delle cartelle a rischio", "realtime"),
            ("🎯", "Difesa Predittiva", "Blocco automatico oltre la soglia di rischio", "settings"),
            ("👁", "Privacy e Controllo", "Processi attivi e stato della sicurezza di sistema", "privacy"),
            ("☁", "Protezione Cloud e Offline", "Firme locali sempre attive, verifica cloud opzionale", "cloud"),
            ("📱", "Sicurezza Multipiattaforma", "Sincronizza impostazioni tra i tuoi dispositivi", "settings"),
        ]
        for i, (icon, title_text, subtitle_text, page) in enumerate(tiles):
            tile = FeatureTile(icon, title_text, subtitle_text)
            tile.clicked.connect(lambda p=page: self.main_window.show_page(p))
            grid.addWidget(tile, i // 3, i % 3)
        layout.addLayout(grid)

        actions_row = QHBoxLayout()
        from PySide6.QtWidgets import QPushButton
        quick_scan_btn = QPushButton("Scansione rapida")
        quick_scan_btn.clicked.connect(self._quick_scan)
        actions_row.addWidget(quick_scan_btn)
        actions_row.addStretch()
        layout.addLayout(actions_row)

        layout.addStretch()

        copyright_label = QLabel("© Roberto Di Flumeri")
        copyright_label.setObjectName("muted")
        copyright_label.setAlignment(Qt.AlignRight)
        layout.addWidget(copyright_label)

        self.refresh()

    def _quick_scan(self):
        target = str(Path.home() / "Downloads")
        self.main_window.show_page("scan")
        self.main_window.scan_page.run_quick_scan(target)

    def refresh(self):
        stats = database.stats_today()
        self.stat_scanned.set_value(stats["files_scanned_today"])
        self.stat_threats.set_value(stats["threats_today"])
        self.stat_quarantine.set_value(database.quarantine_count())
        self.stat_db.set_value(signatures.database_version())

        realtime_on = self.main_window.realtime_monitor.is_running
        if realtime_on:
            self.shield_card.set_state(True, "Protezione in tempo reale attiva")
        else:
            self.shield_card.set_state(False, "Attiva la protezione in tempo reale nella sezione dedicata")

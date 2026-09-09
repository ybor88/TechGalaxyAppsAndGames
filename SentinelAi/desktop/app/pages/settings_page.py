# Copyright (c) Roberto Di Flumeri
from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QCheckBox, QFileDialog, QHBoxLayout, QLabel, QMessageBox, QSlider,
    QVBoxLayout, QWidget,
)

from ..core import database, startup, sync
from ..widgets import make_pill_button


class SettingsPage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window

        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(18)

        title = QLabel("Impostazioni")
        title.setObjectName("pageTitle")
        subtitle = QLabel("Difesa Predittiva, avvio automatico e sincronizzazione multipiattaforma")
        subtitle.setObjectName("pageSubtitle")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        predictive_title = QLabel("Difesa Predittiva")
        predictive_title.setObjectName("sectionTitle")
        layout.addWidget(predictive_title)

        predictive_note = QLabel(
            "I file con un punteggio di rischio pari o superiore alla soglia scelta vengono "
            "messi in quarantena automaticamente dalla protezione in tempo reale, prima ancora "
            "di una conferma cloud."
        )
        predictive_note.setObjectName("muted")
        predictive_note.setWordWrap(True)
        layout.addWidget(predictive_note)

        slider_row = QHBoxLayout()
        self.threshold_slider = QSlider(Qt.Horizontal)
        self.threshold_slider.setRange(30, 100)
        self.threshold_slider.setValue(int(database.get_setting("auto_quarantine_threshold", "70")))
        self.threshold_value_label = QLabel(str(self.threshold_slider.value()))
        self.threshold_slider.valueChanged.connect(self._on_threshold_changed)
        slider_row.addWidget(self.threshold_slider)
        slider_row.addWidget(self.threshold_value_label)
        layout.addLayout(slider_row)

        startup_title = QLabel("Avvio")
        startup_title.setObjectName("sectionTitle")
        layout.addWidget(startup_title)

        self.startup_checkbox = QCheckBox("Avvia SentinelAI all'accesso a Windows")
        self.startup_checkbox.setChecked(startup.is_enabled())
        self.startup_checkbox.stateChanged.connect(self._on_startup_changed)
        layout.addWidget(self.startup_checkbox)

        sync_title = QLabel("📱 Sicurezza Multipiattaforma")
        sync_title.setObjectName("sectionTitle")
        layout.addWidget(sync_title)

        sync_note = QLabel(
            "Esporta il tuo profilo di protezione per riutilizzarlo su un'altra installazione "
            "di SentinelAI (desktop o, in futuro, l'app Android)."
        )
        sync_note.setObjectName("muted")
        sync_note.setWordWrap(True)
        layout.addWidget(sync_note)

        sync_row = QHBoxLayout()
        export_btn = make_pill_button("Esporta profilo", "secondary")
        export_btn.clicked.connect(self._export_profile)
        import_btn = make_pill_button("Importa profilo", "secondary")
        import_btn.clicked.connect(self._import_profile)
        sync_row.addWidget(export_btn)
        sync_row.addWidget(import_btn)
        sync_row.addStretch()
        layout.addLayout(sync_row)

        about_title = QLabel("Informazioni")
        about_title.setObjectName("sectionTitle")
        layout.addWidget(about_title)

        about_label = QLabel("SentinelAI Desktop — Intelligent. Proactive. Protected.\n© Roberto Di Flumeri")
        about_label.setObjectName("muted")
        layout.addWidget(about_label)

        layout.addStretch()

    def _on_threshold_changed(self, value):
        self.threshold_value_label.setText(str(value))
        database.set_setting("auto_quarantine_threshold", value)
        self.main_window.realtime_monitor.auto_quarantine_threshold = value

    def _on_startup_changed(self, state):
        enabled = bool(state)
        try:
            startup.set_enabled(enabled)
            database.set_setting("start_on_login", "1" if enabled else "0")
        except OSError as exc:
            QMessageBox.warning(self, "Operazione non riuscita", str(exc))

    def _export_profile(self):
        path, _ = QFileDialog.getSaveFileName(self, "Esporta profilo", "sentinelai_profile.json", "JSON (*.json)")
        if not path:
            return
        with open(path, "w", encoding="utf-8") as f:
            f.write(sync.export_profile_json())
        QMessageBox.information(self, "Esportato", "Profilo esportato correttamente.")

    def _import_profile(self):
        path, _ = QFileDialog.getOpenFileName(self, "Importa profilo", "", "JSON (*.json)")
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                sync.import_profile_json(f.read())
            self.threshold_slider.setValue(int(database.get_setting("auto_quarantine_threshold", "70")))
            QMessageBox.information(self, "Importato", "Profilo importato correttamente.")
        except (OSError, ValueError) as exc:
            QMessageBox.warning(self, "Importazione non riuscita", str(exc))

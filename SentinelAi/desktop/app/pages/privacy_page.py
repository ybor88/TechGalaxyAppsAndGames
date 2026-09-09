# Copyright (c) Roberto Di Flumeri
from PySide6.QtCore import QThread, Signal
from PySide6.QtWidgets import (
    QFrame, QHBoxLayout, QHeaderView, QLabel, QMessageBox, QPushButton,
    QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget,
)

from ..core import process_monitor
from ..theme import DANGER, OK, TEXT_DIM, WARNING


class _PostureWorker(QThread):
    result = Signal(str, str)

    def run(self):
        self.result.emit(process_monitor.firewall_status(), process_monitor.defender_status())


class _ProcessWorker(QThread):
    result = Signal(list)

    def run(self):
        self.result.emit(process_monitor.list_processes())


def _status_color(status):
    if status == "attivo":
        return OK
    if status in ("disattivato", "parzialmente attivo"):
        return DANGER if status == "disattivato" else WARNING
    return TEXT_DIM


class PrivacyPage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window
        self._posture_worker = None
        self._process_worker = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(16)

        title = QLabel("Privacy e Controllo")
        title.setObjectName("pageTitle")
        subtitle = QLabel("Stato di sicurezza del sistema e visibilità sui processi attivi")
        subtitle.setObjectName("pageSubtitle")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        posture_row = QHBoxLayout()
        self.firewall_card = self._posture_card("Firewall di Windows")
        self.defender_card = self._posture_card("Windows Defender")
        posture_row.addWidget(self.firewall_card)
        posture_row.addWidget(self.defender_card)
        layout.addLayout(posture_row)

        table_header = QHBoxLayout()
        processes_title = QLabel("Processi attivi")
        processes_title.setObjectName("sectionTitle")
        refresh_btn = QPushButton("Aggiorna")
        refresh_btn.setObjectName("secondary")
        refresh_btn.clicked.connect(self.refresh)
        table_header.addWidget(processes_title)
        table_header.addStretch()
        table_header.addWidget(refresh_btn)
        layout.addLayout(table_header)

        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["PID", "Processo", "CPU %", "Memoria (MB)", "Connessioni rete", "Azione"])
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.Stretch)
        self.table.verticalHeader().setVisible(False)
        self.table.setEditTriggers(QTableWidget.NoEditTriggers)
        layout.addWidget(self.table)

        self.refresh()

    def _posture_card(self, title_text):
        card = QFrame()
        card.setObjectName("card")
        v = QVBoxLayout(card)
        v.setContentsMargins(18, 14, 18, 14)
        label = QLabel(title_text)
        label.setObjectName("muted")
        value = QLabel("verifica…")
        value.setObjectName("statValue")
        value.setStyleSheet("font-size: 16px;")
        v.addWidget(label)
        v.addWidget(value)
        card.value_label = value
        return card

    def refresh(self):
        if self._posture_worker is None or not self._posture_worker.isRunning():
            self._posture_worker = _PostureWorker()
            self._posture_worker.result.connect(self._on_posture)
            self._posture_worker.start()

        if self._process_worker is None or not self._process_worker.isRunning():
            self._process_worker = _ProcessWorker()
            self._process_worker.result.connect(self._on_processes)
            self._process_worker.start()

    def _on_posture(self, firewall, defender):
        self.firewall_card.value_label.setText(firewall.capitalize())
        self.firewall_card.value_label.setStyleSheet(f"font-size: 16px; color: {_status_color(firewall)};")
        self.defender_card.value_label.setText(defender.capitalize())
        self.defender_card.value_label.setStyleSheet(f"font-size: 16px; color: {_status_color(defender)};")

    def _on_processes(self, processes):
        self.table.setRowCount(0)
        for proc in processes[:150]:
            row = self.table.rowCount()
            self.table.insertRow(row)
            self.table.setItem(row, 0, QTableWidgetItem(str(proc["pid"])))
            self.table.setItem(row, 1, QTableWidgetItem(proc["name"]))
            self.table.setItem(row, 2, QTableWidgetItem(f"{proc['cpu']:.1f}"))
            self.table.setItem(row, 3, QTableWidgetItem(f"{proc['mem_mb']:.1f}"))
            self.table.setItem(row, 4, QTableWidgetItem(str(proc["connections"])))

            kill_btn = QPushButton("Termina")
            kill_btn.setObjectName("danger")
            kill_btn.clicked.connect(lambda _=None, pid=proc["pid"], name=proc["name"]: self._kill(pid, name))
            self.table.setCellWidget(row, 5, kill_btn)

    def _kill(self, pid, name):
        confirm = QMessageBox.question(self, "Termina processo", f"Terminare il processo '{name}' (PID {pid})?")
        if confirm == QMessageBox.Yes:
            try:
                process_monitor.kill_process(pid)
                self.refresh()
            except Exception as exc:
                QMessageBox.warning(self, "Operazione non riuscita", str(exc))

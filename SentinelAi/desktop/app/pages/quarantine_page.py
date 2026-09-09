# Copyright (c) Roberto Di Flumeri
from PySide6.QtWidgets import (
    QHeaderView, QLabel, QMessageBox, QPushButton,
    QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget,
)

from ..core import database, quarantine


class QuarantinePage(QWidget):
    def __init__(self, main_window, parent=None):
        super().__init__(parent)
        self.main_window = main_window
        self._items = {}

        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(16)

        title = QLabel("Quarantena")
        title.setObjectName("pageTitle")
        subtitle = QLabel("File isolati e neutralizzati: possono essere ripristinati o eliminati definitivamente")
        subtitle.setObjectName("pageSubtitle")
        layout.addWidget(title)
        layout.addWidget(subtitle)

        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(
            ["File originale", "Minaccia", "Rischio", "Data", "", ""]
        )
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.Stretch)
        for col in (1, 2, 3, 4, 5):
            self.table.horizontalHeader().setSectionResizeMode(col, QHeaderView.ResizeToContents)
        self.table.verticalHeader().setVisible(False)
        self.table.setWordWrap(False)
        self.table.setEditTriggers(QTableWidget.NoEditTriggers)
        layout.addWidget(self.table)

        self.empty_label = QLabel("Nessun elemento in quarantena. Il sistema è pulito.")
        self.empty_label.setObjectName("muted")
        layout.addWidget(self.empty_label)

        self.refresh()

    def refresh(self):
        items = database.list_quarantine()
        self._items = {item["id"]: item for item in items}
        self.table.setRowCount(0)
        self.empty_label.setVisible(len(items) == 0)
        self.table.setVisible(len(items) > 0)

        for item in items:
            row = self.table.rowCount()
            self.table.insertRow(row)
            self.table.setItem(row, 0, QTableWidgetItem(item["original_path"]))
            self.table.setItem(row, 1, QTableWidgetItem(item["threat_name"] or ""))
            self.table.setItem(row, 2, QTableWidgetItem(f"{item['risk_score']}/100"))
            self.table.setItem(row, 3, QTableWidgetItem(item["quarantined_at"]))

            restore_btn = QPushButton("Ripristina")
            restore_btn.setObjectName("secondary")
            restore_btn.clicked.connect(lambda _=None, i=item["id"]: self._restore(i))
            self.table.setCellWidget(row, 4, restore_btn)

            delete_btn = QPushButton("Elimina")
            delete_btn.setObjectName("danger")
            delete_btn.clicked.connect(lambda _=None, i=item["id"]: self._delete(i))
            self.table.setCellWidget(row, 5, delete_btn)

        self.main_window.refresh_dashboard()

    def _restore(self, item_id):
        try:
            quarantine.restore_file(item_id)
            self.refresh()
        except (OSError, ValueError) as exc:
            QMessageBox.warning(self, "Ripristino non riuscito", str(exc))

    def _delete(self, item_id):
        confirm = QMessageBox.question(
            self, "Conferma eliminazione",
            "Eliminare definitivamente questo file? L'operazione non è reversibile.",
        )
        if confirm == QMessageBox.Yes:
            try:
                quarantine.delete_permanently(item_id)
                self.refresh()
            except (OSError, ValueError) as exc:
                QMessageBox.warning(self, "Eliminazione non riuscita", str(exc))

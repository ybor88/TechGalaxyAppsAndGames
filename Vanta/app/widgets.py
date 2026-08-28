# Copyright (c) Roberto Di Flumeri
from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QFrame, QLabel, QPushButton, QSpacerItem, QSizePolicy,
    QScrollArea
)


class ChoiceButton(QPushButton):
    """Pulsante selezionabile usato per le domande a risposta multipla."""

    def __init__(self, text):
        super().__init__(text)
        self.setObjectName("choice")
        self.setCheckable(True)
        self.setMinimumHeight(46)


class StepWidget(QWidget):
    """Base per ogni schermata del percorso guidato: una card centrata su sfondo scuro."""

    def __init__(self, controller):
        super().__init__()
        self.controller = controller

        content = QWidget()
        outer = QVBoxLayout(content)
        outer.addSpacerItem(QSpacerItem(1, 1, QSizePolicy.Minimum, QSizePolicy.Expanding))

        self.card = QFrame()
        self.card.setObjectName("panel")
        self.card.setMaximumWidth(640)
        self.card_layout = QVBoxLayout(self.card)
        self.card_layout.setSpacing(14)
        self.card_layout.setContentsMargins(36, 32, 36, 32)

        row = QHBoxLayout()
        row.addStretch(1)
        row.addWidget(self.card)
        row.addStretch(1)
        outer.addLayout(row)

        outer.addSpacerItem(QSpacerItem(1, 1, QSizePolicy.Minimum, QSizePolicy.Expanding))

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        scroll.setWidget(content)

        page_layout = QVBoxLayout(self)
        page_layout.setContentsMargins(0, 0, 0, 0)
        page_layout.addWidget(scroll)

        self.error_label = None

    def add_progress(self, text):
        label = QLabel(text.upper())
        label.setObjectName("progress")
        self.card_layout.addWidget(label)
        return label

    def add_question(self, text):
        label = QLabel(text)
        label.setObjectName("question")
        label.setWordWrap(True)
        self.card_layout.addWidget(label)
        return label

    def add_subtitle(self, text):
        label = QLabel(text)
        label.setObjectName("subtitle")
        label.setWordWrap(True)
        self.card_layout.addWidget(label)
        return label

    def add_error(self):
        self.error_label = QLabel("")
        self.error_label.setObjectName("error")
        self.error_label.setWordWrap(True)
        self.card_layout.addWidget(self.error_label)
        return self.error_label

    def show_error(self, message):
        if self.error_label is not None:
            self.error_label.setText(message)

    def add_nav(self, on_back=None, on_next=None, next_label="Avanti", next_enabled=True):
        row = QHBoxLayout()
        if on_back is not None:
            back_btn = QPushButton("Indietro")
            back_btn.setObjectName("secondary")
            back_btn.clicked.connect(on_back)
            row.addWidget(back_btn)
        row.addStretch(1)
        if on_next is not None:
            next_btn = QPushButton(next_label)
            next_btn.setEnabled(next_enabled)
            next_btn.clicked.connect(on_next)
            row.addWidget(next_btn)
            self.next_btn = next_btn
        self.card_layout.addLayout(row)
        return row

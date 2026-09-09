# Copyright (c) Roberto Di Flumeri
from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QFrame, QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget,
)

from .theme import DANGER, OK, TEAL, TEXT_DIM, WARNING


class StatCard(QFrame):
    def __init__(self, label, value="—", accent=None, parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(6)

        self.value_label = QLabel(str(value))
        self.value_label.setObjectName("statValue")
        if accent:
            self.value_label.setStyleSheet(f"color: {accent};")

        label_widget = QLabel(label.upper())
        label_widget.setObjectName("statLabel")

        layout.addWidget(self.value_label)
        layout.addWidget(label_widget)

    def set_value(self, value):
        self.value_label.setText(str(value))


class FeatureTile(QFrame):
    clicked = Signal()

    def __init__(self, icon, title, subtitle, parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        self.setCursor(Qt.PointingHandCursor)
        self.setMinimumHeight(140)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(6)

        icon_label = QLabel(icon)
        icon_label.setStyleSheet(f"font-size: 22px; color: {TEAL};")

        title_label = QLabel(title)
        title_label.setStyleSheet("font-size: 13px; font-weight: 700;")
        title_label.setWordWrap(True)

        subtitle_label = QLabel(subtitle)
        subtitle_label.setObjectName("muted")
        subtitle_label.setWordWrap(True)

        layout.addWidget(icon_label)
        layout.addWidget(title_label)
        layout.addWidget(subtitle_label)
        layout.addStretch()

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            self.clicked.emit()
        super().mousePressEvent(event)


class ShieldStatusCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        layout = QHBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 20)
        layout.setSpacing(20)

        self.icon_label = QLabel("🛡")
        self.icon_label.setStyleSheet(f"font-size: 40px; color: {OK};")

        text_col = QVBoxLayout()
        text_col.setSpacing(2)
        self.title_label = QLabel("Sistema protetto")
        self.title_label.setStyleSheet("font-size: 18px; font-weight: 700;")
        self.subtitle_label = QLabel("Protezione in tempo reale attiva")
        self.subtitle_label.setObjectName("muted")
        text_col.addWidget(self.title_label)
        text_col.addWidget(self.subtitle_label)

        layout.addWidget(self.icon_label)
        layout.addLayout(text_col)
        layout.addStretch()

    def set_state(self, protected: bool, subtitle: str):
        if protected:
            self.icon_label.setStyleSheet(f"font-size: 40px; color: {OK};")
            self.icon_label.setText("🛡")
            self.title_label.setText("Sistema protetto")
        else:
            self.icon_label.setStyleSheet(f"font-size: 40px; color: {WARNING};")
            self.icon_label.setText("⚠")
            self.title_label.setText("Protezione in tempo reale disattivata")
        self.subtitle_label.setText(subtitle)


def verdict_color(verdict: str) -> str:
    return {"malicious": DANGER, "suspicious": WARNING, "clean": OK}.get(verdict, TEXT_DIM)


def verdict_label(verdict: str) -> str:
    return {
        "malicious": "Minaccia rilevata",
        "suspicious": "Sospetto",
        "clean": "Pulito",
    }.get(verdict, verdict)


def make_pill_button(text, object_name=None):
    btn = QPushButton(text)
    if object_name:
        btn.setObjectName(object_name)
    return btn

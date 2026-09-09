# Copyright (c) Roberto Di Flumeri
BG = "#080b14"
BG_SIDEBAR = "#0a0e1c"
PANEL = "#0f1526"
PANEL_2 = "#131a30"
PANEL_BORDER = "#1f2946"
BLUE = "#2e7bff"
TEAL = "#22e6c0"
PURPLE = "#6c3ce9"
TEXT = "#e8ecfb"
TEXT_DIM = "#7c8aae"
DANGER = "#ff4d6d"
WARNING = "#ffb020"
OK = "#22e6a0"

GRADIENT = f"qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {BLUE}, stop:1 {TEAL})"

STYLESHEET = f"""
QMainWindow, QWidget {{
    background-color: {BG};
    color: {TEXT};
    font-family: "Segoe UI", sans-serif;
    font-size: 13px;
}}

QWidget#sidebar {{
    background-color: {BG_SIDEBAR};
    border-right: 1px solid {PANEL_BORDER};
}}

QLabel#brand {{
    color: {TEXT};
    font-size: 19px;
    font-weight: 700;
    letter-spacing: 2px;
}}

QLabel#brandAccent {{
    color: {TEAL};
}}

QLabel#tagline {{
    color: {TEXT_DIM};
    font-size: 9px;
    letter-spacing: 2px;
}}

QPushButton#navItem {{
    background: transparent;
    border: none;
    border-radius: 8px;
    color: {TEXT_DIM};
    text-align: left;
    padding: 10px 14px;
    font-size: 13px;
    font-weight: 600;
}}

QPushButton#navItem:hover {{
    background: {PANEL};
    color: {TEXT};
}}

QPushButton#navItem:checked {{
    background: {PANEL_2};
    color: {TEAL};
    border-left: 3px solid {TEAL};
}}

QLabel#pageTitle {{
    color: {TEXT};
    font-size: 22px;
    font-weight: 700;
}}

QLabel#pageSubtitle {{
    color: {TEXT_DIM};
    font-size: 12px;
}}

QFrame#card {{
    background-color: {PANEL};
    border: 1px solid {PANEL_BORDER};
    border-radius: 12px;
}}

QFrame#card:hover {{
    border: 1px solid {BLUE};
}}

QLabel#statValue {{
    color: {TEXT};
    font-size: 24px;
    font-weight: 700;
}}

QLabel#statLabel {{
    color: {TEXT_DIM};
    font-size: 11px;
    letter-spacing: 1px;
}}

QLabel#sectionTitle {{
    color: {TEXT};
    font-size: 15px;
    font-weight: 700;
}}

QLabel#muted {{
    color: {TEXT_DIM};
    font-size: 12px;
}}

QLabel#ok {{ color: {OK}; font-weight: 700; }}
QLabel#warning {{ color: {WARNING}; font-weight: 700; }}
QLabel#danger {{ color: {DANGER}; font-weight: 700; }}

QPushButton {{
    background: {GRADIENT};
    color: white;
    border: none;
    border-radius: 8px;
    padding: 9px 20px;
    font-weight: 600;
    font-size: 13px;
}}

QPushButton:hover {{
    background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #4a8fff, stop:1 #45f0d0);
}}

QPushButton:disabled {{
    background: #232b42;
    color: {TEXT_DIM};
}}

QPushButton#secondary {{
    background: transparent;
    border: 1px solid {PANEL_BORDER};
    color: {TEXT};
}}

QPushButton#secondary:hover {{
    border: 1px solid {TEAL};
    color: {TEAL};
    background: transparent;
}}

QPushButton#danger {{
    background: {DANGER};
}}

QPushButton#danger:hover {{
    background: #ff6b85;
}}

QPushButton#icon {{
    background: {PANEL_2};
    border: 1px solid {PANEL_BORDER};
    border-radius: 8px;
    padding: 8px 10px;
}}

QPushButton#icon:hover {{
    border: 1px solid {TEAL};
}}

QLineEdit, QComboBox, QSpinBox {{
    background-color: {PANEL_2};
    border: 1px solid {PANEL_BORDER};
    border-radius: 8px;
    padding: 8px 10px;
    color: {TEXT};
    font-size: 13px;
}}

QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{
    border: 1px solid {TEAL};
}}

QCheckBox {{
    spacing: 8px;
    padding: 4px;
    color: {TEXT};
}}

QSlider::groove:horizontal {{
    height: 6px;
    background: {PANEL_2};
    border-radius: 3px;
}}

QSlider::handle:horizontal {{
    background: {TEAL};
    width: 16px;
    height: 16px;
    margin: -5px 0;
    border-radius: 8px;
}}

QSlider::sub-page:horizontal {{
    background: {GRADIENT};
    border-radius: 3px;
}}

QProgressBar {{
    background: {PANEL_2};
    border: 1px solid {PANEL_BORDER};
    border-radius: 6px;
    text-align: center;
    color: {TEXT};
    height: 16px;
}}

QProgressBar::chunk {{
    background: {GRADIENT};
    border-radius: 6px;
}}

QTableWidget {{
    background-color: {PANEL};
    border: 1px solid {PANEL_BORDER};
    border-radius: 10px;
    gridline-color: {PANEL_BORDER};
    color: {TEXT};
    selection-background-color: {PANEL_2};
    selection-color: {TEAL};
}}

QHeaderView::section {{
    background-color: {PANEL_2};
    color: {TEXT_DIM};
    padding: 8px;
    border: none;
    font-weight: 600;
    font-size: 11px;
}}

QListWidget {{
    background-color: {PANEL};
    border: 1px solid {PANEL_BORDER};
    border-radius: 10px;
    color: {TEXT};
    padding: 4px;
}}

QListWidget::item {{
    padding: 6px;
    border-radius: 6px;
}}

QListWidget::item:selected {{
    background-color: {PANEL_2};
    color: {TEAL};
}}

QScrollArea {{
    border: none;
    background: transparent;
}}

QScrollBar:vertical {{
    background: {BG};
    width: 10px;
}}

QScrollBar::handle:vertical {{
    background: {PANEL_BORDER};
    border-radius: 5px;
    min-height: 30px;
}}

QToolTip {{
    background-color: {PANEL_2};
    color: {TEXT};
    border: 1px solid {PANEL_BORDER};
    padding: 4px;
}}
"""

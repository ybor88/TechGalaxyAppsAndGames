# Copyright (c) Roberto Di Flumeri
BG = "#07070d"
PANEL = "#0f0f1a"
PANEL_BORDER = "#22223a"
PURPLE = "#8a3ffc"
BLUE = "#2fc7ff"
TEXT = "#eef0ff"
TEXT_DIM = "#8d8da8"
ERROR = "#ff5470"
OK = "#2fe6a0"

STYLESHEET = f"""
QMainWindow, QWidget {{
    background-color: {BG};
    color: {TEXT};
    font-family: "Segoe UI", sans-serif;
    font-size: 14px;
}}

QLabel#title {{
    color: {BLUE};
    font-size: 26px;
    font-weight: 700;
}}

QLabel#subtitle {{
    color: {TEXT_DIM};
    font-size: 13px;
}}

QLabel#question {{
    color: {TEXT};
    font-size: 19px;
    font-weight: 600;
}}

QLabel#progress {{
    color: {PURPLE};
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 1px;
}}

QLabel#error {{
    color: {ERROR};
    font-size: 12px;
}}

QFrame#panel {{
    background-color: {PANEL};
    border: 1px solid {PANEL_BORDER};
    border-radius: 12px;
}}

QLabel#code, QPlainTextEdit#code {{
    background-color: #0a0a14;
    color: #c9c9f2;
    border: 1px solid {PANEL_BORDER};
    border-radius: 8px;
    padding: 14px;
    font-family: Consolas, "Courier New", monospace;
    font-size: 13px;
}}

QPushButton {{
    background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
        stop:0 {PURPLE}, stop:1 {BLUE});
    color: white;
    border: none;
    border-radius: 8px;
    padding: 10px 22px;
    font-weight: 600;
    font-size: 14px;
}}

QPushButton:hover {{
    background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
        stop:0 #9a54ff, stop:1 #52d3ff);
}}

QPushButton:disabled {{
    background: #2a2a3c;
    color: {TEXT_DIM};
}}

QPushButton#secondary {{
    background: transparent;
    border: 1px solid {PANEL_BORDER};
    color: {TEXT_DIM};
}}

QPushButton#secondary:hover {{
    border: 1px solid {BLUE};
    color: {TEXT};
    background: transparent;
}}

QPushButton#choice {{
    background: {PANEL};
    border: 1px solid {PANEL_BORDER};
    color: {TEXT};
    text-align: left;
    padding: 14px 18px;
    font-weight: 500;
    font-size: 14px;
    border-radius: 10px;
}}

QPushButton#choice:hover {{
    border: 1px solid {BLUE};
    background: #14142a;
}}

QPushButton#choice:checked {{
    border: 2px solid {BLUE};
    background: #17172f;
    color: {BLUE};
}}

QLineEdit, QComboBox, QSpinBox {{
    background-color: #14142a;
    border: 1px solid {PANEL_BORDER};
    border-radius: 8px;
    padding: 8px 10px;
    color: {TEXT};
    font-size: 14px;
}}

QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{
    border: 1px solid {BLUE};
}}

QCheckBox {{
    spacing: 8px;
    padding: 4px;
}}

QScrollArea {{
    border: none;
}}
"""

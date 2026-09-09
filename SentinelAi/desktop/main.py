# SentinelAI - Copyright (c) Roberto Di Flumeri
import sys

from PySide6.QtWidgets import QApplication

from app.main_window import MainWindow
from app.theme import STYLESHEET


def main():
    app = QApplication(sys.argv)
    app.setStyleSheet(STYLESHEET)
    app.setApplicationName("SentinelAI")

    window = MainWindow()
    window.showMaximized()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()

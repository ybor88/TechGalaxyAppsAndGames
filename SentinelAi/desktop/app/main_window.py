# Copyright (c) Roberto Di Flumeri
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtGui import QIcon
from PySide6.QtWidgets import (
    QButtonGroup, QHBoxLayout, QLabel, QMainWindow, QPushButton,
    QStackedWidget, QVBoxLayout, QWidget,
)

from .core import database
from .core.realtime_monitor import RealtimeMonitor
from .pages.cloud_page import CloudPage
from .pages.dashboard_page import DashboardPage
from .pages.privacy_page import PrivacyPage
from .pages.quarantine_page import QuarantinePage
from .pages.realtime_page import RealtimePage
from .pages.scan_page import ScanPage
from .pages.settings_page import SettingsPage

NAV_ITEMS = [
    ("dashboard", "🛡  Dashboard"),
    ("scan", "🧠  Scansione"),
    ("realtime", "⚡  Tempo Reale"),
    ("quarantine", "🗄  Quarantena"),
    ("privacy", "👁  Privacy"),
    ("cloud", "☁  Cloud & Offline"),
    ("settings", "⚙  Impostazioni"),
]


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        database.init_db()

        self.setWindowTitle("SentinelAI")
        self.resize(1180, 780)
        self.setMinimumSize(980, 640)

        logo_path = Path(__file__).resolve().parent.parent / "assets" / "logo.jpeg"
        if logo_path.exists():
            self.setWindowIcon(QIcon(str(logo_path)))

        self.realtime_monitor = RealtimeMonitor()

        central = QWidget()
        self.setCentralWidget(central)
        root_layout = QHBoxLayout(central)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        root_layout.addWidget(self._build_sidebar())

        self.stack = QStackedWidget()
        root_layout.addWidget(self.stack, 1)

        self.dashboard_page = DashboardPage(self)
        self.scan_page = ScanPage(self)
        self.realtime_page = RealtimePage(self)
        self.quarantine_page = QuarantinePage(self)
        self.privacy_page = PrivacyPage(self)
        self.cloud_page = CloudPage(self)
        self.settings_page = SettingsPage(self)

        self.pages = {
            "dashboard": self.dashboard_page,
            "scan": self.scan_page,
            "realtime": self.realtime_page,
            "quarantine": self.quarantine_page,
            "privacy": self.privacy_page,
            "cloud": self.cloud_page,
            "settings": self.settings_page,
        }
        for page in self.pages.values():
            self.stack.addWidget(page)

        self.realtime_monitor.file_scanned.connect(self.realtime_page.on_file_scanned)
        self.realtime_monitor.threat_blocked.connect(self.realtime_page.on_threat_blocked)

        self.show_page("dashboard")

    def _build_sidebar(self):
        sidebar = QWidget()
        sidebar.setObjectName("sidebar")
        sidebar.setFixedWidth(230)
        layout = QVBoxLayout(sidebar)
        layout.setContentsMargins(20, 24, 16, 20)
        layout.setSpacing(4)

        brand_row = QHBoxLayout()
        shield = QLabel("🛡")
        shield.setStyleSheet("font-size: 22px;")
        brand_text = QLabel()
        brand_text.setText('<span style="color:#e8ecfb;">SENTINEL</span> <span style="color:#22e6c0;">AI</span>')
        brand_text.setObjectName("brand")
        brand_row.addWidget(shield)
        brand_row.addWidget(brand_text)
        brand_row.addStretch()
        layout.addLayout(brand_row)

        tagline = QLabel("INTELLIGENT · PROACTIVE · PROTECTED")
        tagline.setObjectName("tagline")
        tagline.setWordWrap(True)
        layout.addWidget(tagline)

        layout.addSpacing(24)

        self.nav_group = QButtonGroup(self)
        self.nav_group.setExclusive(True)
        self.nav_buttons = {}
        for key, label in NAV_ITEMS:
            btn = QPushButton(label)
            btn.setObjectName("navItem")
            btn.setCheckable(True)
            btn.setCursor(Qt.PointingHandCursor)
            btn.clicked.connect(lambda _=None, k=key: self.show_page(k))
            self.nav_group.addButton(btn)
            self.nav_buttons[key] = btn
            layout.addWidget(btn)

        layout.addStretch()
        return sidebar

    def show_page(self, key):
        page = self.pages.get(key)
        if page is None:
            return
        self.stack.setCurrentWidget(page)
        if key in self.nav_buttons:
            self.nav_buttons[key].setChecked(True)
        if hasattr(page, "refresh"):
            page.refresh()

    def refresh_dashboard(self):
        self.dashboard_page.refresh()

    def closeEvent(self, event):
        self.realtime_monitor.stop()
        super().closeEvent(event)

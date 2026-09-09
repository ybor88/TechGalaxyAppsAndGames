# Copyright (c) Roberto Di Flumeri
"""Start-on-login toggle via the current user's HKCU Run registry key
(no admin privileges required, affects only the current Windows user)."""
import sys
import winreg

_RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
_VALUE_NAME = "SentinelAI"


def _main_script_path():
    from ..core.paths import ROOT_DIR
    return str(ROOT_DIR / "main.py")


def is_enabled() -> bool:
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_KEY) as key:
            winreg.QueryValueEx(key, _VALUE_NAME)
            return True
    except FileNotFoundError:
        return False


def set_enabled(enabled: bool):
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, _RUN_KEY) as key:
        if enabled:
            command = f'"{sys.executable}" "{_main_script_path()}"'
            winreg.SetValueEx(key, _VALUE_NAME, 0, winreg.REG_SZ, command)
        else:
            try:
                winreg.DeleteValue(key, _VALUE_NAME)
            except FileNotFoundError:
                pass

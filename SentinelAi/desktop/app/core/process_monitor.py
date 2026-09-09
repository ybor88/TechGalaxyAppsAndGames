# Copyright (c) Roberto Di Flumeri
"""Privacy & Control: running-process visibility and system security posture
(Windows Firewall / Defender status) using psutil and native Windows tools."""
import subprocess

import psutil


def list_processes():
    net_by_pid = {}
    try:
        for conn in psutil.net_connections(kind="inet"):
            if conn.pid:
                net_by_pid[conn.pid] = net_by_pid.get(conn.pid, 0) + 1
    except (psutil.AccessDenied, PermissionError, OSError):
        net_by_pid = {}

    processes = []
    for proc in psutil.process_iter(["pid", "name", "exe", "username"]):
        try:
            info = proc.info
            processes.append(
                {
                    "pid": info["pid"],
                    "name": info["name"] or "?",
                    "exe": info.get("exe") or "",
                    "cpu": proc.cpu_percent(interval=None),
                    "mem_mb": round(proc.memory_info().rss / (1024 * 1024), 1),
                    "connections": net_by_pid.get(info["pid"], 0),
                }
            )
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            continue
    processes.sort(key=lambda p: p["mem_mb"], reverse=True)
    return processes


def kill_process(pid: int):
    proc = psutil.Process(pid)
    proc.terminate()


def _run(cmd, timeout=6):
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            creationflags=subprocess.CREATE_NO_WINDOW if hasattr(subprocess, "CREATE_NO_WINDOW") else 0,
        )
        return result.stdout
    except (subprocess.SubprocessError, OSError, FileNotFoundError):
        return None


def firewall_status():
    output = _run(["netsh", "advfirewall", "show", "allprofiles", "state"])
    if output is None:
        return "non disponibile"
    if "ON" in output.upper():
        return "attivo" if "OFF" not in output.upper() else "parzialmente attivo"
    if "OFF" in output.upper():
        return "disattivato"
    return "sconosciuto"


def defender_status():
    output = _run(
        [
            "powershell",
            "-NoProfile",
            "-Command",
            "(Get-MpComputerStatus).RealTimeProtectionEnabled",
        ],
        timeout=10,
    )
    if output is None:
        return "non disponibile"
    value = output.strip().lower()
    if value == "true":
        return "attivo"
    if value == "false":
        return "disattivato"
    return "sconosciuto"

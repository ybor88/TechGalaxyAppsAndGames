# Copyright (c) Roberto Di Flumeri
import subprocess
import sys
import tempfile
import os
from dataclasses import dataclass


@dataclass
class ExecutionResult:
    success: bool
    output: str
    error: str


def _write_temp_script(source: str) -> str:
    fd, path = tempfile.mkstemp(suffix=".py", prefix="vanta_")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(source)
    return path


def run_source(source: str, timeout: float = 5.0) -> ExecutionResult:
    """Esegue un programma senza richieste di input e ne cattura l'output."""
    path = _write_temp_script(source)
    try:
        proc = subprocess.run(
            [sys.executable, path],
            capture_output=True,
            text=True,
            timeout=timeout,
            stdin=subprocess.DEVNULL,
        )
        if proc.returncode == 0:
            return ExecutionResult(True, proc.stdout, "")
        return ExecutionResult(False, proc.stdout, proc.stderr)
    except subprocess.TimeoutExpired:
        return ExecutionResult(False, "", "Il programma ha impiegato troppo tempo ed è stato interrotto.")
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def launch_interactive(source: str, new_console: bool = False):
    """Avvia il programma in un processo indipendente senza attendere il risultato.

    Usato per i programmi con finestra grafica o che chiedono un valore da terminale,
    dove non ha senso catturare l'output in modo sincrono.
    """
    path = _write_temp_script(source)
    creationflags = subprocess.CREATE_NEW_CONSOLE if (new_console and sys.platform == "win32") else 0
    return subprocess.Popen([sys.executable, path], creationflags=creationflags)

"""
Assegna (o toglie) il ruolo di moderatore a un account esistente.

    cd backend
    .venv\\Scripts\\python scripts\\moderatore.py email@esempio.it          # promuove
    .venv\\Scripts\\python scripts\\moderatore.py email@esempio.it --togli  # rimuove

© Roberto Di Flumeri
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from sqlalchemy import select  # noqa: E402

from app.crypto import hash_email  # noqa: E402
from app.database import SessionLocal, init_db  # noqa: E402
from app.models import AuditLog, User  # noqa: E402


async def main(email: str, togli: bool) -> int:
    await init_db()
    async with SessionLocal() as db:
        u = (await db.execute(select(User).where(User.email_hash == hash_email(email)))).scalar_one_or_none()
        if u is None:
            print("Nessun account con questa email.")
            return 1
        u.ruolo = "utente" if togli else "moderatore"
        db.add(AuditLog(attore_id=None, azione="ruolo_" + u.ruolo, oggetto=u.id, dettagli="da riga di comando"))
        await db.commit()
        print(f"Ruolo aggiornato: {u.ruolo}")
        return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(asyncio.run(main(sys.argv[1], "--togli" in sys.argv)))

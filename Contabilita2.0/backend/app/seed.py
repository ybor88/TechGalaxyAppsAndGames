"""
Seed: popola il DB con dati demo realistici al primo avvio.
Se i movimenti esistono già non fa nulla.
"""

from datetime import date, timedelta
from decimal import Decimal
import random

from sqlalchemy import select, func

from app.database import AsyncSessionLocal
from app.models.financial import Movimento, TipoMovimento, Conto

# ── Dati demo ─────────────────────────────────────────────────────────────

CONTI_DEMO = [
    {"codice": "1001", "descrizione": "Cassa", "tipo": "attivo"},
    {"codice": "1002", "descrizione": "Banca c/c", "tipo": "attivo"},
    {"codice": "4001", "descrizione": "Vendite prodotti", "tipo": "ricavo"},
    {"codice": "4002", "descrizione": "Prestazioni servizi", "tipo": "ricavo"},
    {"codice": "5001", "descrizione": "Acquisto merci", "tipo": "costo"},
    {"codice": "5002", "descrizione": "Affitto ufficio", "tipo": "costo"},
    {"codice": "5003", "descrizione": "Stipendi", "tipo": "costo"},
    {"codice": "5004", "descrizione": "Utenze", "tipo": "costo"},
]

ENTRATE_DEMO = [
    ("Vendita prodotti cliente A", "Vendite", 8500),
    ("Vendita prodotti cliente B", "Vendite", 12300),
    ("Prestazione consulenza", "Servizi", 4200),
    ("Fattura cliente C", "Vendite", 6800),
    ("Anticipo commessa", "Servizi", 9000),
    ("Incasso fattura arretrata", "Vendite", 3400),
    ("Vendita stock", "Vendite", 5100),
    ("Servizio mensile abbonamento", "Servizi", 2800),
]

USCITE_DEMO = [
    ("Affitto ufficio", "Affitti", 1800),
    ("Stipendi personale", "Personale", 7500),
    ("Acquisto materie prime", "Fornitori", 4300),
    ("Bollette utenze", "Utenze", 620),
    ("Software e licenze", "IT", 380),
    ("Spese commerciali", "Marketing", 950),
    ("Assicurazioni", "Assicurazioni", 1200),
    ("Fornitore logistica", "Fornitori", 2100),
]


async def run_seed() -> None:
    async with AsyncSessionLocal() as db:
        # Controlla se ci sono già movimenti
        count = await db.execute(select(func.count()).select_from(Movimento))
        if count.scalar() > 0:
            return  # DB già popolato

        # Crea conti
        conti = []
        for c in CONTI_DEMO:
            conto = Conto(**c, saldo=Decimal("0.00"))
            db.add(conto)
            conti.append(conto)
        await db.flush()

        # Genera movimenti negli ultimi 12 mesi
        oggi = date.today()
        random.seed(42)

        for settimane_fa in range(52):
            data_mov = oggi - timedelta(weeks=settimane_fa)

            # 1-2 entrate per settimana
            for _ in range(random.randint(1, 2)):
                template = random.choice(ENTRATE_DEMO)
                importo = Decimal(str(template[2] + random.randint(-500, 1500)))
                db.add(Movimento(
                    data=data_mov,
                    tipo=TipoMovimento.ENTRATA,
                    importo=importo,
                    descrizione=template[0],
                    categoria=template[1],
                ))

            # 1-3 uscite per settimana
            for _ in range(random.randint(1, 3)):
                template = random.choice(USCITE_DEMO)
                importo = Decimal(str(template[2] + random.randint(-200, 400)))
                db.add(Movimento(
                    data=data_mov,
                    tipo=TipoMovimento.USCITA,
                    importo=importo,
                    descrizione=template[0],
                    categoria=template[1],
                ))

        await db.commit()

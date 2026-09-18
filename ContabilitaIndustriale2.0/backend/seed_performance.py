"""
seed_performance.py
Popola il DB con migliaia di record coerenti per testare le performance dell'app.

Esecuzione (dal container o con venv attivo):
    cd backend
    python seed_performance.py

Aggiunta flag --reset per svuotare prima il DB:
    python seed_performance.py --reset
"""

import asyncio
import random
import sys
from datetime import date, timedelta
from decimal import Decimal

from sqlalchemy import text, select, func

from app.database import AsyncSessionLocal
from app.models.financial import Conto, Movimento
from app.models.fatturazione import Anagrafica, Documento, RigaDocumento
from app.models.crm import StoricoPagamento, Scadenza, OpportunitaPipeline
from app.models.contabilita import RegistrazioneContabile, RigaRegistrazione
from app.models.workflow import Task, PassoApprovazione

# importa i modelli OCR e AI assistant così sono registrati in metadata
import app.models.ocr          # noqa: F401
import app.models.ai_assistant  # noqa: F401

R = random.Random(42)  # seed fisso → risultati riproducibili

# ---------------------------------------------------------------------------
# Parametri di volume
# ---------------------------------------------------------------------------
TOT_ANAGRAFICHE  = 300   # 180 clienti, 80 fornitori, 40 entrambi
TOT_DOCUMENTI    = 3_000
TOT_MOVIMENTI    = 5_000
TOT_REGISTRAZIONI = 2_000
TOT_SCADENZE     = 1_500
TOT_OPPORTUNITA  = 600
TOT_TASK         = 700

# ---------------------------------------------------------------------------
# Dati di supporto
# ---------------------------------------------------------------------------
CITTA = [
    ("Milano","MI"), ("Roma","RM"), ("Torino","TO"), ("Napoli","NA"),
    ("Bologna","BO"), ("Firenze","FI"), ("Venezia","VE"), ("Genova","GE"),
    ("Palermo","PA"), ("Bari","BA"), ("Verona","VR"), ("Padova","PD"),
    ("Brescia","BS"), ("Modena","MO"), ("Parma","PR"), ("Pisa","PI"),
    ("Bergamo","BG"), ("Trento","TN"), ("Udine","UD"), ("Salerno","SA"),
]

NOMI_AZIENDA = [
    "Alfa", "Beta", "Gamma", "Delta", "Sigma", "Nova", "Ital", "Med",
    "Euro", "Tech", "Agri", "Edil", "Logis", "Fin", "Smart", "Green",
    "Fast", "Pro", "Mega", "Plus",
]
SUFFISSI = ["Srl", "SpA", "Snc", "Sas", "Srls"]
SETTORI  = ["Consulting", "Trade", "Solutions", "Services", "Group",
            "Industries", "Systems", "Partners", "Lab", "Net"]

PRODOTTI = [
    ("Consulenza strategica",    Decimal("1500.00"), Decimal("22.00")),
    ("Sviluppo software",        Decimal("850.00"),  Decimal("22.00")),
    ("Manutenzione impianti",    Decimal("320.00"),  Decimal("22.00")),
    ("Fornitura materiali",      Decimal("180.00"),  Decimal("22.00")),
    ("Trasporto e logistica",    Decimal("240.00"),  Decimal("22.00")),
    ("Formazione aziendale",     Decimal("600.00"),  Decimal("22.00")),
    ("Servizi IT gestiti",       Decimal("420.00"),  Decimal("22.00")),
    ("Progettazione",            Decimal("1200.00"), Decimal("22.00")),
    ("Licenza software",         Decimal("350.00"),  Decimal("22.00")),
    ("Supporto tecnico",         Decimal("90.00"),   Decimal("22.00")),
    ("Acquisto hardware",        Decimal("560.00"),  Decimal("22.00")),
    ("Materiale di consumo",     Decimal("45.00"),   Decimal("22.00")),
    ("Servizi cloud",            Decimal("280.00"),  Decimal("22.00")),
    ("Marketing digitale",       Decimal("800.00"),  Decimal("22.00")),
    ("Pulizia uffici",           Decimal("500.00"),  Decimal("4.00")),
    ("Energia e riscaldamento",  Decimal("1800.00"), Decimal("22.00")),
    ("Nolo attrezzature",        Decimal("650.00"),  Decimal("22.00")),
    ("Spese di spedizione",      Decimal("38.00"),   Decimal("22.00")),
    ("Analisi e ricerca",        Decimal("2200.00"), Decimal("22.00")),
    ("Servizi legali",           Decimal("950.00"),  Decimal("22.00")),
]

CONTI_PIANO = [
    ("1001", "Cassa",                      "attivo"),
    ("1002", "Banca c/c principale",       "attivo"),
    ("1003", "Banca c/c secondario",       "attivo"),
    ("1101", "Crediti vs clienti",         "attivo"),
    ("1201", "Rimanenze merci",            "attivo"),
    ("1301", "Risconti attivi",            "attivo"),
    ("2001", "Debiti vs fornitori",        "passivo"),
    ("2101", "IVA a debito",               "passivo"),
    ("2102", "IVA a credito",              "passivo"),
    ("2201", "Ratei passivi",              "passivo"),
    ("2401", "Debiti tributari",           "passivo"),
    ("2501", "Debiti previdenziali",       "passivo"),
    ("4001", "Vendite prodotti",           "ricavo"),
    ("4002", "Prestazioni servizi",        "ricavo"),
    ("4003", "Altri ricavi",               "ricavo"),
    ("5001", "Acquisto merci",             "costo"),
    ("5002", "Affitto ufficio",            "costo"),
    ("5003", "Stipendi e oneri",           "costo"),
    ("5004", "Utenze",                     "costo"),
    ("5005", "Ammortamenti",               "costo"),
    ("5006", "Spese commerciali",          "costo"),
    ("5007", "Spese IT",                   "costo"),
    ("5008", "Assicurazioni",              "costo"),
    ("5009", "Consulenze esterne",         "costo"),
    ("5010", "Spese bancarie",             "costo"),
]

SCHEMI_CONTABILI = [
    # (conto_dare, conto_avere, tipo_causale, descrizione)
    ("1101", "4001", "fattura_attiva",  "Fattura attiva – vendita prodotti"),
    ("1101", "4002", "fattura_attiva",  "Fattura attiva – servizi"),
    ("1002", "1101", "incasso",         "Incasso da cliente"),
    ("1001", "1101", "incasso",         "Incasso contante da cliente"),
    ("5001", "2001", "fattura_passiva", "Fattura passiva – acquisto merci"),
    ("5009", "2001", "fattura_passiva", "Fattura passiva – consulenza"),
    ("5007", "2001", "fattura_passiva", "Fattura passiva – IT"),
    ("2001", "1002", "pagamento",       "Pagamento fornitore"),
    ("5003", "2501", "pagamento",       "Pagamento stipendi"),
    ("5002", "1002", "manuale",         "Affitto mensile"),
    ("5004", "1002", "manuale",         "Utenze"),
    ("2101", "1002", "pagamento",       "Versamento IVA"),
    ("5005", "1201", "manuale",         "Ammortamento"),
    ("5006", "1001", "manuale",         "Spese commerciali"),
    ("1002", "1003", "manuale",         "Giroconto bancario"),
]

UTENTI = [
    "mario.rossi", "giulia.bianchi", "luca.ferrari",
    "anna.conti", "marco.esposito", "sara.marino",
]

FASI_PIPELINE = ["prospecting", "qualifica", "proposta", "trattativa", "chiusa_vinta", "chiusa_persa"]
PROB_FASE = {"prospecting": 10, "qualifica": 25, "proposta": 50,
             "trattativa": 70, "chiusa_vinta": 100, "chiusa_persa": 0}

DATA_INIZIO = date(2023, 1, 1)
DATA_FINE   = date.today()


def rdate(da: date = DATA_INIZIO, a: date = DATA_FINE) -> date:
    return da + timedelta(days=R.randint(0, (a - da).days))


def nome_azienda(i: int) -> str:
    return f"{R.choice(NOMI_AZIENDA)} {R.choice(SETTORI)} {R.choice(SUFFISSI)} {i}"


def piva() -> str:
    return "IT" + "".join(str(R.randint(0, 9)) for _ in range(11))


# ---------------------------------------------------------------------------
# Blocchi di inserimento
# ---------------------------------------------------------------------------

async def inserisci_conti(db) -> dict[str, int]:
    """Piano dei conti. Ritorna {codice: id}."""
    codici_esistenti = set()
    rows = await db.execute(text("SELECT codice FROM conti"))
    for row in rows:
        codici_esistenti.add(row[0])

    result = {}
    for codice, desc, tipo in CONTI_PIANO:
        if codice not in codici_esistenti:
            c = Conto(codice=codice, descrizione=desc, tipo=tipo, saldo=Decimal("0.00"))
            db.add(c)
            await db.flush()
            result[codice] = c.id
        else:
            row = await db.execute(text(f"SELECT id FROM conti WHERE codice='{codice}'"))
            result[codice] = row.scalar()
    return result


async def inserisci_anagrafiche(db) -> tuple[list[int], list[int], list[int]]:
    """300 anagrafiche. Ritorna (tutti_ids, clienti_ids, fornitori_ids)."""
    print("  → Anagrafiche...")
    tipi = (["cliente"] * 180) + (["fornitore"] * 80) + (["entrambi"] * 40)
    tutti, clienti, fornitori = [], [], []
    for i, tipo in enumerate(tipi, 1):
        citta, prov = R.choice(CITTA)
        a = Anagrafica(
            nome=nome_azienda(i),
            tipo=tipo,
            piva=piva(),
            cf="".join(R.choices("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", k=16)),
            indirizzo=f"Via {R.choice(['Roma','Verdi','Mazzini','Garibaldi'])} {R.randint(1,200)}",
            cap=f"{R.randint(10000,99999)}",
            citta=citta,
            provincia=prov,
            paese="Italia",
            email=f"info{i}@esempio.it",
            telefono=f"+39 0{R.randint(2,9)}{R.randint(1000000,9999999)}",
        )
        db.add(a)
        await db.flush()
        tutti.append(a.id)
        if tipo == "cliente":
            clienti.append(a.id)
        elif tipo == "fornitore":
            fornitori.append(a.id)
        else:
            clienti.append(a.id)
            fornitori.append(a.id)
    return tutti, clienti, fornitori


async def inserisci_documenti(db, clienti_ids, fornitori_ids, tutti_ids) -> list[tuple[int, str, Decimal]]:
    """3 000 documenti con righe. Ritorna [(doc_id, stato, totale)]."""
    print("  → Documenti + righe documento...")
    tipi_peso = (["fattura_attiva"] * 45 + ["fattura_passiva"] * 30
                 + ["preventivo"] * 15 + ["ordine"] * 10)
    progressivi: dict[str, int] = {}
    risultati = []

    for _ in range(TOT_DOCUMENTI):
        tipo = R.choice(tipi_peso)
        data_doc = rdate()

        if tipo == "fattura_attiva":
            ana_id = R.choice(clienti_ids)
            stati = ["emesso"] * 45 + ["pagato"] * 45 + ["annullato"] * 5 + ["bozza"] * 5
        elif tipo == "fattura_passiva":
            ana_id = R.choice(fornitori_ids)
            stati = ["emesso"] * 40 + ["pagato"] * 50 + ["annullato"] * 5 + ["bozza"] * 5
        else:
            ana_id = R.choice(tutti_ids)
            stati = ["bozza"] * 30 + ["emesso"] * 65 + ["annullato"] * 5

        stato = R.choice(stati)
        key = f"{tipo}_{data_doc.year}"
        progressivi[key] = progressivi.get(key, 0) + 1
        numero = f"{data_doc.year}/{progressivi[key]:04d}"
        scadenza = (data_doc + timedelta(days=R.choice([30, 60, 90]))
                    if tipo in ("fattura_attiva", "fattura_passiva") else None)

        # righe
        subtotale = Decimal("0.00")
        tot_iva   = Decimal("0.00")
        righe_tmp = []
        for _ in range(R.randint(1, 5)):
            desc, prezzo_base, iva_pct = R.choice(PRODOTTI)
            prezzo = (prezzo_base * Decimal(str(round(R.uniform(0.8, 1.2), 4)))).quantize(Decimal("0.01"))
            qty    = Decimal(str(round(R.uniform(1.0, 10.0), 2)))
            importo = (qty * prezzo).quantize(Decimal("0.01"))
            imp_iva  = (importo * iva_pct / 100).quantize(Decimal("0.01"))
            subtotale += importo
            tot_iva   += imp_iva
            righe_tmp.append((desc, qty, prezzo, iva_pct, importo))

        totale = subtotale + tot_iva
        doc = Documento(
            tipo=tipo, numero=numero, data=data_doc,
            data_scadenza=scadenza, anagrafica_id=ana_id,
            stato=stato, oggetto=f"{tipo.replace('_',' ').title()} n.{numero}",
            subtotale=subtotale, totale_iva=tot_iva, totale=totale,
        )
        db.add(doc)
        await db.flush()

        for desc, qty, prezzo, iva_pct, importo in righe_tmp:
            db.add(RigaDocumento(
                documento_id=doc.id, descrizione=desc, quantita=qty,
                prezzo_unitario=prezzo, iva_percentuale=iva_pct, importo=importo,
            ))
        risultati.append((doc.id, stato, totale))

    await db.flush()
    return risultati


async def inserisci_movimenti(db, conti_map: dict[str, int]) -> None:
    """5 000 movimenti finanziari."""
    print("  → Movimenti...")
    conti_banca = [conti_map["1001"], conti_map["1002"], conti_map["1003"]]
    for _ in range(TOT_MOVIMENTI):
        tipo = R.choice(["entrata"] * 55 + ["uscita"] * 45)
        if tipo == "entrata":
            importo = Decimal(str(round(R.uniform(300, 30_000), 2)))
            descrizione = R.choice([
                "Incasso fattura cliente", "Acconto commessa", "Saldo fattura",
                "Vendita prodotti", "Prestazione servizi", "Anticipo ordine",
            ])
            categoria = R.choice(["Vendite", "Servizi", "Incassi"])
        else:
            importo = Decimal(str(round(R.uniform(100, 18_000), 2)))
            descrizione = R.choice([
                "Pagamento fornitore", "Stipendi", "Affitto mensile",
                "Bollette utenze", "Acquisto materiali", "F24 IVA",
                "Spese marketing", "Software e licenze", "Assicurazioni",
            ])
            categoria = R.choice(["Fornitori", "Stipendi", "Affitti", "Utenze", "IT", "Marketing"])
        db.add(Movimento(
            data=rdate(),
            tipo=tipo,
            importo=importo,
            descrizione=descrizione,
            categoria=categoria,
            conto_id=R.choice(conti_banca),
        ))
    await db.flush()


async def inserisci_registrazioni(db, conti_map: dict[str, int]) -> None:
    """2 000 registrazioni contabili in partita doppia."""
    print("  → Registrazioni contabili (partita doppia)...")
    causali = [
        "Incasso fattura cliente", "Pagamento fornitore", "Giroconto bancario",
        "Pagamento stipendi", "Versamento IVA", "Rimborso spese",
        "Affitto mensile", "Utenze", "Ammortamento", "Rettifica contabile",
    ]
    for i in range(TOT_REGISTRAZIONI):
        schema = R.choice(SCHEMI_CONTABILI)
        cod_d, cod_a, tipo_causale, desc_schema = schema
        importo = Decimal(str(round(R.uniform(200, 40_000), 2)))
        reg = RegistrazioneContabile(
            numero=i + 1,
            data=rdate(),
            causale=R.choice(causali),
            tipo_causale=tipo_causale,
            chiusa=R.random() < 0.7,
        )
        db.add(reg)
        await db.flush()
        db.add(RigaRegistrazione(
            registrazione_id=reg.id,
            conto_id=conti_map[cod_d],
            descrizione=desc_schema,
            dare=importo,
            avere=Decimal("0.00"),
        ))
        db.add(RigaRegistrazione(
            registrazione_id=reg.id,
            conto_id=conti_map[cod_a],
            descrizione=desc_schema,
            dare=Decimal("0.00"),
            avere=importo,
        ))
    await db.flush()


async def inserisci_scadenze(db, tutti_ids, doc_risultati) -> None:
    """1 500 scadenze CRM collegate a documenti e anagrafiche."""
    print("  → Scadenze CRM...")
    doc_emessi = [did for did, stato, _ in doc_risultati if stato in ("emesso", "pagato")]
    tipi  = ["incasso"] * 60 + ["pagamento"] * 35 + ["altro"] * 5
    stati = ["aperta"] * 50 + ["pagata"] * 30 + ["scaduta"] * 15 + ["annullata"] * 5
    for i in range(TOT_SCADENZE):
        stato = R.choice(stati)
        doc_id = R.choice(doc_emessi) if doc_emessi and R.random() < 0.6 else None
        data_sc = (rdate(DATA_INIZIO, date.today() - timedelta(days=1))
                   if stato == "scaduta" else rdate())
        db.add(Scadenza(
            anagrafica_id=R.choice(tutti_ids) if R.random() < 0.9 else None,
            documento_id=doc_id,
            titolo=f"Scadenza {R.choice(tipi)} #{i+1}",
            data_scadenza=data_sc,
            importo=Decimal(str(round(R.uniform(500, 25_000), 2))),
            tipo=R.choice(tipi),
            stato=stato,
        ))
    await db.flush()


async def inserisci_storico_pagamenti(db, tutti_ids, doc_risultati) -> None:
    """800 pagamenti storici collegati a fatture pagate."""
    print("  → Storico pagamenti...")
    fatture_pagate = [(did, tot) for did, stato, tot in doc_risultati if stato == "pagato"]
    if not fatture_pagate:
        fatture_pagate = [(None, Decimal("1000.00"))]
    metodi = ["bonifico"] * 60 + ["contanti"] * 15 + ["assegno"] * 10 + ["carta"] * 10 + ["rid"] * 5
    for _ in range(800):
        doc_id, totale = R.choice(fatture_pagate)
        giorni = R.choice([0] * 50 + list(range(1, 31)) * 35 + list(range(-5, 0)) * 15)
        db.add(StoricoPagamento(
            anagrafica_id=R.choice(tutti_ids),
            documento_id=doc_id,
            data_pagamento=rdate(),
            importo=(totale * Decimal(str(round(R.uniform(0.9, 1.0), 4)))).quantize(Decimal("0.01")),
            metodo_pagamento=R.choice(metodi),
            giorni_ritardo=giorni,
        ))
    await db.flush()


async def inserisci_opportunita(db, clienti_ids) -> None:
    """600 opportunità nel pipeline commerciale."""
    print("  → Opportunità pipeline commerciale...")
    for i in range(TOT_OPPORTUNITA):
        fase = R.choice(FASI_PIPELINE)
        prob = max(0, min(100, PROB_FASE[fase] + R.randint(-5, 5)))
        chiusura = (date.today() + timedelta(days=R.randint(30, 540))
                    if fase not in ("chiusa_vinta", "chiusa_persa") else rdate())
        db.add(OpportunitaPipeline(
            anagrafica_id=R.choice(clienti_ids) if R.random() < 0.85 else None,
            titolo=f"Opportunità #{i+1} – {R.choice(PRODOTTI)[0]}",
            valore_stimato=Decimal(str(round(R.uniform(2_000, 150_000), 2))),
            fase=fase,
            probabilita=prob,
            data_chiusura_prevista=chiusura,
        ))
    await db.flush()


async def inserisci_task(db, tutti_ids) -> None:
    """700 task workflow con passi di approvazione."""
    print("  → Task workflow...")
    tipi_t  = ["task"] * 50 + ["approvazione"] * 25 + ["reminder"] * 15 + ["acquisto"] * 10
    stati_t = ["aperto"] * 35 + ["in_corso"] * 25 + ["completato"] * 30 + ["annullato"] * 10
    priorita = ["bassa"] * 20 + ["media"] * 45 + ["alta"] * 25 + ["urgente"] * 10
    for i in range(TOT_TASK):
        tipo  = R.choice(tipi_t)
        stato = R.choice(stati_t)
        scad  = rdate(date.today() - timedelta(days=60), date.today() + timedelta(days=90))
        t = Task(
            titolo=f"Task #{i+1} – {R.choice(PRODOTTI)[0]}",
            descrizione=f"Descrizione del task #{i+1}.",
            tipo=tipo,
            stato=stato,
            priorita=R.choice(priorita),
            assegnato_a=R.choice(UTENTI),
            creato_da=R.choice(UTENTI),
            data_scadenza=scad,
            data_completamento=(rdate(DATA_INIZIO, scad) if stato == "completato" else None),
            anagrafica_id=(R.choice(tutti_ids) if tipo == "acquisto" and R.random() < 0.7 else None),
            importo_stimato=(Decimal(str(round(R.uniform(500, 50_000), 2))) if tipo == "acquisto" else None),
            reminder_inviato=R.random() < 0.3,
        )
        db.add(t)
        await db.flush()
        if tipo in ("approvazione", "acquisto"):
            for ordine in range(1, R.randint(2, 4)):
                db.add(PassoApprovazione(
                    task_id=t.id,
                    ordine=ordine,
                    approvatore=R.choice(UTENTI),
                    stato=R.choice(["in_attesa", "approvato", "rifiutato"]),
                ))
    await db.flush()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

async def main() -> None:
    reset = "--reset" in sys.argv

    async with AsyncSessionLocal() as db:
        if reset:
            print("⚠  Reset: svuoto le tabelle...")
            for tabella in [
                "workflow_passi_approvazione", "workflow_tasks",
                "scadenze_crm", "pipeline_commerciale", "storico_pagamenti",
                "righe_registrazione", "registrazioni_contabili",
                "righe_documento", "documenti", "anagrafiche",
                "movimenti", "conti", "ocr_risultati",
            ]:
                await db.execute(text(f"DELETE FROM {tabella}"))
            await db.commit()
            print("   Tabelle svuotate.\n")

        print("Avvio seed performance...\n")

        print("  → Piano dei conti...")
        conti_map = await inserisci_conti(db)
        await db.commit()

        tutti_ids, clienti_ids, fornitori_ids = await inserisci_anagrafiche(db)
        await db.commit()

        doc_risultati = await inserisci_documenti(db, clienti_ids, fornitori_ids, tutti_ids)
        await db.commit()

        await inserisci_movimenti(db, conti_map)
        await db.commit()

        await inserisci_registrazioni(db, conti_map)
        await db.commit()

        await inserisci_scadenze(db, tutti_ids, doc_risultati)
        await db.commit()

        await inserisci_storico_pagamenti(db, tutti_ids, doc_risultati)
        await db.commit()

        await inserisci_opportunita(db, clienti_ids)
        await db.commit()

        await inserisci_task(db, tutti_ids)
        await db.commit()

    print("\n✓ Seed completato! Riepilogo:")
    print(f"   Anagrafiche       : {TOT_ANAGRAFICHE}")
    print(f"   Documenti         : {TOT_DOCUMENTI}  (+righe)")
    print(f"   Movimenti         : {TOT_MOVIMENTI}")
    print(f"   Registrazioni     : {TOT_REGISTRAZIONI}  (partita doppia)")
    print(f"   Scadenze CRM      : {TOT_SCADENZE}")
    print(f"   Storico pagamenti : 800")
    print(f"   Opportunità       : {TOT_OPPORTUNITA}")
    print(f"   Task workflow     : {TOT_TASK}  (+passi approvazione)")


if __name__ == "__main__":
    asyncio.run(main())

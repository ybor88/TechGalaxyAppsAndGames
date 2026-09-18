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
from app.models.centri_costo import CentroCosto, BaseRiparto, ValoreBaseRiparto, RipartoCostoIndiretto
from app.models.prodotti import Prodotto, DistintaBaseRiga, MovimentoMagazzino
from app.models.commesse import Commessa, RigaCostoCommessa
from app.models.controllo_gestione import ClassificazioneCosto
from app.schemas.commesse import RigaCostoCommessaCreate
from app.services.commesse import CommesseService
from app.schemas.contabilita import RegistrazioneCreate, RigaRegistrazioneIn
from app.services.contabilita import ContabilitaService

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
        conti_map = {c.codice: c for c in conti}

        # Registrazioni di prima nota (partita doppia) che valorizzano il saldo dei conti:
        # i Movimenti di cassa sotto sono un registro parallelo e NON aggiornano il saldo dei conti
        # (solo la Contabilità Generale lo fa) — senza queste scritture Bilancio e Controllo di
        # Gestione mostrerebbero sempre saldi a zero su un DB appena seedato.
        oggi_pn = date.today()
        contabilita_service = ContabilitaService(db)
        REGISTRAZIONI_DEMO = [
            ("4001", "avere", Decimal("8500.00"), "Vendita prodotti cliente A", 80),
            ("4001", "avere", Decimal("12300.00"), "Vendita prodotti cliente B", 55),
            ("4001", "avere", Decimal("6800.00"), "Fattura cliente C", 20),
            ("4002", "avere", Decimal("4200.00"), "Prestazione consulenza", 65),
            ("4002", "avere", Decimal("9000.00"), "Anticipo commessa", 15),
            ("5001", "dare", Decimal("4300.00"), "Acquisto materie prime", 75),
            ("5001", "dare", Decimal("2100.00"), "Fornitore logistica", 40),
            ("5001", "dare", Decimal("3800.00"), "Acquisto materiali imballaggio", 10),
            ("5002", "dare", Decimal("1800.00"), "Affitto ufficio", 85),
            ("5002", "dare", Decimal("1800.00"), "Affitto ufficio", 55),
            ("5002", "dare", Decimal("1800.00"), "Affitto ufficio", 25),
            ("5003", "dare", Decimal("7500.00"), "Stipendi personale", 85),
            ("5003", "dare", Decimal("7500.00"), "Stipendi personale", 55),
            ("5003", "dare", Decimal("7500.00"), "Stipendi personale", 25),
            ("5004", "dare", Decimal("620.00"), "Bollette utenze", 70),
            ("5004", "dare", Decimal("580.00"), "Bollette utenze", 40),
            ("5004", "dare", Decimal("650.00"), "Bollette utenze", 12),
        ]
        for codice_conto, verso, importo, causale, giorni_fa in REGISTRAZIONI_DEMO:
            conto = conti_map[codice_conto]
            banca = conti_map["1002"]
            if verso == "avere":
                righe = [
                    RigaRegistrazioneIn(conto_id=banca.id, dare=importo, avere=Decimal("0.00")),
                    RigaRegistrazioneIn(conto_id=conto.id, dare=Decimal("0.00"), avere=importo),
                ]
            else:
                righe = [
                    RigaRegistrazioneIn(conto_id=conto.id, dare=importo, avere=Decimal("0.00")),
                    RigaRegistrazioneIn(conto_id=banca.id, dare=Decimal("0.00"), avere=importo),
                ]
            await contabilita_service.crea_registrazione(RegistrazioneCreate(
                data=oggi_pn - timedelta(days=giorni_fa), causale=causale, tipo_causale="manuale", righe=righe,
            ))

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

    await _seed_industriale()


async def _seed_industriale() -> None:
    """Popola centri di costo, prodotti/distinta base, magazzino e commesse demo."""
    async with AsyncSessionLocal() as db:
        count = await db.execute(select(func.count()).select_from(CentroCosto))
        if count.scalar() > 0:
            return  # già popolato

        oggi = date.today()
        periodo_corrente = oggi.strftime("%Y-%m")

        # ── Centri di costo ──────────────────────────────────────────────────
        cc_taglio = CentroCosto(codice="CC-01", descrizione="Reparto Taglio", tipo="produttivo")
        cc_assemblaggio = CentroCosto(codice="CC-02", descrizione="Reparto Assemblaggio", tipo="produttivo")
        cc_manutenzione = CentroCosto(codice="CC-03", descrizione="Manutenzione", tipo="ausiliario")
        cc_amministrazione = CentroCosto(codice="CC-04", descrizione="Amministrazione e Direzione", tipo="comune")
        db.add_all([cc_taglio, cc_assemblaggio, cc_manutenzione, cc_amministrazione])
        await db.flush()

        # ── Basi di riparto ──────────────────────────────────────────────────
        base_ore_macchina = BaseRiparto(descrizione="Ore macchina", unita_misura="ore")
        base_mq = BaseRiparto(descrizione="Superficie occupata", unita_misura="mq")
        db.add_all([base_ore_macchina, base_mq])
        await db.flush()

        db.add_all([
            ValoreBaseRiparto(base_riparto_id=base_ore_macchina.id, centro_costo_id=cc_taglio.id, periodo=periodo_corrente, quantita=Decimal("620")),
            ValoreBaseRiparto(base_riparto_id=base_ore_macchina.id, centro_costo_id=cc_assemblaggio.id, periodo=periodo_corrente, quantita=Decimal("380")),
            ValoreBaseRiparto(base_riparto_id=base_mq.id, centro_costo_id=cc_taglio.id, periodo=periodo_corrente, quantita=Decimal("140")),
            ValoreBaseRiparto(base_riparto_id=base_mq.id, centro_costo_id=cc_assemblaggio.id, periodo=periodo_corrente, quantita=Decimal("210")),
        ])

        # Esempio di riparto: il costo di manutenzione (centro ausiliario) viene ribaltato
        # sui due centri produttivi in proporzione alle ore macchina consumate.
        db.add(RipartoCostoIndiretto(
            descrizione="Manutenzione impianti — mese corrente", centro_costo_origine_id=cc_manutenzione.id,
            importo=Decimal("1450.00"), base_riparto_id=base_ore_macchina.id, periodo=periodo_corrente,
        ))

        # ── Prodotti: materie prime ──────────────────────────────────────────
        legno = Prodotto(
            codice="MP-001", descrizione="Pannello legno multistrato", tipo="materia_prima", unita_misura="pz",
            prezzo_standard=Decimal("18.5000"), scorta_minima=Decimal("50"),
        )
        tessuto = Prodotto(
            codice="MP-002", descrizione="Tessuto imbottitura (metro)", tipo="materia_prima", unita_misura="mt",
            prezzo_standard=Decimal("7.2000"), scorta_minima=Decimal("100"),
        )
        ruote = Prodotto(
            codice="MP-003", descrizione="Kit ruote piroettanti", tipo="materia_prima", unita_misura="pz",
            prezzo_standard=Decimal("4.8000"), scorta_minima=Decimal("80"),
        )
        meccanismo = Prodotto(
            codice="MP-004", descrizione="Meccanismo basculante", tipo="materia_prima", unita_misura="pz",
            prezzo_standard=Decimal("12.0000"), scorta_minima=Decimal("40"),
        )
        db.add_all([legno, tessuto, ruote, meccanismo])
        await db.flush()

        # ── Semilavorato ──────────────────────────────────────────────────────
        struttura = Prodotto(
            codice="SL-001", descrizione="Struttura sedia assemblata", tipo="semilavorato", unita_misura="pz",
            ore_manodopera_standard=Decimal("0.400"), costo_orario_manodopera_standard=Decimal("16.00"),
            costo_indiretto_standard_unitario=Decimal("2.5000"), scorta_minima=Decimal("10"),
        )
        db.add(struttura)
        await db.flush()
        db.add_all([
            DistintaBaseRiga(prodotto_padre_id=struttura.id, componente_id=legno.id, quantita=Decimal("2.0000")),
            DistintaBaseRiga(prodotto_padre_id=struttura.id, componente_id=meccanismo.id, quantita=Decimal("1.0000")),
        ])

        # ── Prodotto finito ───────────────────────────────────────────────────
        sedia = Prodotto(
            codice="PF-001", descrizione="Sedia da ufficio Mod. Comfort", tipo="prodotto_finito", unita_misura="pz",
            ore_manodopera_standard=Decimal("0.600"), costo_orario_manodopera_standard=Decimal("16.00"),
            costo_indiretto_standard_unitario=Decimal("3.0000"), prezzo_vendita=Decimal("129.00"),
            scorta_minima=Decimal("15"),
        )
        db.add(sedia)
        await db.flush()
        db.add_all([
            DistintaBaseRiga(prodotto_padre_id=sedia.id, componente_id=struttura.id, quantita=Decimal("1.0000")),
            DistintaBaseRiga(prodotto_padre_id=sedia.id, componente_id=tessuto.id, quantita=Decimal("1.5000")),
            DistintaBaseRiga(prodotto_padre_id=sedia.id, componente_id=ruote.id, quantita=Decimal("5.0000")),
        ])
        await db.flush()

        # ── Movimenti di magazzino: carichi iniziali (valorizzano il costo medio ponderato) ──
        carichi = [
            (legno, Decimal("300"), Decimal("18.20")),
            (tessuto, Decimal("400"), Decimal("7.35")),
            (ruote, Decimal("600"), Decimal("4.70")),
            (meccanismo, Decimal("200"), Decimal("12.10")),
            # Struttura: carico da produzione interna (reparto Taglio) a un costo reale leggermente
            # superiore allo standard (57.90) — genera uno scostamento di prezzo sfavorevole realistico.
            (struttura, Decimal("25"), Decimal("60.00")),
        ]
        for prodotto, quantita, costo in carichi:
            prodotto.giacenza_attuale = quantita
            prodotto.costo_medio_ponderato = costo
            db.add(MovimentoMagazzino(
                prodotto_id=prodotto.id, data=oggi - timedelta(days=20), tipo="carico",
                quantita=quantita, costo_unitario=costo,
                causale="Carico da produzione interna" if prodotto is struttura else "Carico iniziale da fornitore",
            ))

        # ── Commesse demo ────────────────────────────────────────────────────
        commessa_aperta = Commessa(
            codice="COM-2026-001", descrizione="Fornitura 50 sedie — Cliente Ufficio Moderno Srl",
            prodotto_id=sedia.id, centro_costo_id=cc_assemblaggio.id, quantita_pianificata=Decimal("50"),
            data_apertura=oggi - timedelta(days=10), stato="aperta",
        )
        commessa_chiusa = Commessa(
            codice="COM-2026-000", descrizione="Lotto pilota 20 sedie — collaudo linea",
            prodotto_id=sedia.id, centro_costo_id=cc_assemblaggio.id, quantita_pianificata=Decimal("20"),
            data_apertura=oggi - timedelta(days=45), stato="aperta",
        )
        db.add_all([commessa_aperta, commessa_chiusa])
        await db.flush()

        # Righe di costo della commessa chiusa (genera scostamenti realistici in Analisi Scostamenti).
        # Le righe materiale passano dal service: scaricano il magazzino e si valorizzano al costo medio ponderato.
        data_consumo = oggi - timedelta(days=33)
        for prodotto, quantita in [(struttura, Decimal("21")), (tessuto, Decimal("32")), (ruote, Decimal("108"))]:
            await CommesseService(db).aggiungi_riga_costo(commessa_chiusa.id, RigaCostoCommessaCreate(
                tipo="materiale", descrizione=f"Consumo {prodotto.descrizione}", data=data_consumo,
                prodotto_id=prodotto.id, quantita=quantita,
            ))
        db.add_all([
            RigaCostoCommessa(
                commessa_id=commessa_chiusa.id, tipo="manodopera", descrizione="Ore assemblaggio linea",
                data=oggi - timedelta(days=32), centro_costo_id=cc_assemblaggio.id,
                quantita=Decimal("14.0"), costo_unitario=Decimal("17.50"), importo=Decimal("245.00"),
            ),
            RigaCostoCommessa(
                commessa_id=commessa_chiusa.id, tipo="indiretto", descrizione="Quota energia e ammortamenti reparto",
                data=oggi - timedelta(days=30), centro_costo_id=cc_assemblaggio.id,
                importo=Decimal("68.00"),
            ),
        ])

        # Chiude la commessa pilota ora che i costi sono stati registrati.
        commessa_chiusa.stato = "chiusa"
        commessa_chiusa.data_chiusura = oggi - timedelta(days=30)
        commessa_chiusa.quantita_prodotta = Decimal("20")

        # ── Classificazione costi fissi/variabili (per Break-even / direct costing) ──
        conti = (await db.execute(select(Conto))).scalars().all()
        mappa_conti = {c.codice: c for c in conti}
        classificazioni = {
            "5001": "variabile",  # Acquisto merci
            "5002": "fisso",      # Affitto ufficio
            "5003": "fisso",      # Stipendi
            "5004": "variabile",  # Utenze
        }
        for codice, classificazione in classificazioni.items():
            conto = mappa_conti.get(codice)
            if conto:
                db.add(ClassificazioneCosto(conto_id=conto.id, classificazione=classificazione))

        await db.commit()

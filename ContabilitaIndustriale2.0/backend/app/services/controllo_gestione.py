from decimal import Decimal, ROUND_HALF_UP
from datetime import date

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.financial import Conto
from app.models.controllo_gestione import ClassificazioneCosto
from app.models.prodotti import Prodotto
from app.models.commesse import Commessa
from app.schemas.controllo_gestione import (
    ClassificazioneCostoSet, ClassificazioneCostoOut,
    BreakEvenAziendale, BreakEvenProdotto, KPIIndustriale,
)
from app.services.prodotti import ProdottiService
from app.services.commesse import CommesseService

TWO_DP = Decimal("0.01")


def _q(val: Decimal) -> Decimal:
    return val.quantize(TWO_DP, rounding=ROUND_HALF_UP)


class ControlloGestioneService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Classificazione costi fissi/variabili ────────────────────────────────

    async def list_classificazioni(self) -> list[ClassificazioneCostoOut]:
        conti = (await self.db.execute(
            select(Conto).where(Conto.tipo.in_(["costo", "ricavo"])).order_by(Conto.codice)
        )).scalars().all()
        classificazioni = (await self.db.execute(select(ClassificazioneCosto))).scalars().all()
        mappa = {c.conto_id: c.classificazione for c in classificazioni}
        return [
            ClassificazioneCostoOut(
                conto_id=c.id, conto_codice=c.codice, conto_descrizione=c.descrizione,
                tipo_conto=c.tipo, classificazione=mappa.get(c.id),
            )
            for c in conti
        ]

    async def imposta_classificazione(self, payload: ClassificazioneCostoSet) -> ClassificazioneCostoOut:
        conto = await self.db.get(Conto, payload.conto_id)
        if conto is None:
            raise ValueError("Conto non trovato")
        existing = (await self.db.execute(
            select(ClassificazioneCosto).where(ClassificazioneCosto.conto_id == payload.conto_id)
        )).scalar_one_or_none()
        if existing is None:
            existing = ClassificazioneCosto(conto_id=payload.conto_id, classificazione=payload.classificazione)
            self.db.add(existing)
        else:
            existing.classificazione = payload.classificazione
        await self.db.commit()
        return ClassificazioneCostoOut(
            conto_id=conto.id, conto_codice=conto.codice, conto_descrizione=conto.descrizione,
            tipo_conto=conto.tipo, classificazione=payload.classificazione,
        )

    # ── Break-even aziendale (direct costing) ────────────────────────────────

    async def break_even_aziendale(self) -> BreakEvenAziendale:
        conti = (await self.db.execute(
            select(Conto).where(Conto.tipo.in_(["costo", "ricavo"]))
        )).scalars().all()
        classificazioni = (await self.db.execute(select(ClassificazioneCosto))).scalars().all()
        mappa = {c.conto_id: c.classificazione for c in classificazioni}

        totale_ricavi = Decimal("0")
        totale_fissi = Decimal("0")
        totale_variabili = Decimal("0")
        non_classificati = 0

        for conto in conti:
            saldo = abs(conto.saldo)
            if conto.tipo == "ricavo":
                totale_ricavi += saldo
                continue
            classificazione = mappa.get(conto.id)
            if classificazione == "fisso":
                totale_fissi += saldo
            elif classificazione == "variabile":
                totale_variabili += saldo
            else:
                non_classificati += 1

        margine = totale_ricavi - totale_variabili
        percentuale = _q(margine / totale_ricavi * 100) if totale_ricavi > 0 else Decimal("0.00")
        bep = _q(totale_fissi / (percentuale / 100)) if percentuale > 0 else None

        return BreakEvenAziendale(
            totale_ricavi=_q(totale_ricavi), totale_costi_fissi=_q(totale_fissi),
            totale_costi_variabili=_q(totale_variabili), margine_di_contribuzione=_q(margine),
            percentuale_margine_di_contribuzione=percentuale, punto_di_pareggio_valore=bep,
            conti_non_classificati=non_classificati,
        )

    # ── Break-even per prodotto (margine di contribuzione unitario) ─────────

    async def break_even_prodotti(self) -> list[BreakEvenProdotto]:
        aziendale = await self.break_even_aziendale()
        prodotti_service = ProdottiService(self.db)
        prodotti = (await self.db.execute(
            select(Prodotto).where(Prodotto.tipo == "prodotto_finito", Prodotto.attivo.is_(True))
        )).scalars().all()

        risultati = []
        for p in prodotti:
            if p.prezzo_vendita is None or p.prezzo_vendita <= 0:
                continue
            dettaglio = await prodotti_service.get_prodotto(p.id)
            costo_variabile_unitario = _q(
                dettaglio.costo_standard_materiale_unitario
                + dettaglio.ore_manodopera_standard * dettaglio.costo_orario_manodopera_standard
            )
            margine_unitario = _q(p.prezzo_vendita - costo_variabile_unitario)
            percentuale = _q(margine_unitario / p.prezzo_vendita * 100) if p.prezzo_vendita > 0 else Decimal("0.00")
            bep_quantita = (
                _q(aziendale.totale_costi_fissi / margine_unitario) if margine_unitario > 0 else None
            )
            risultati.append(BreakEvenProdotto(
                prodotto_id=p.id, codice=p.codice, descrizione=p.descrizione,
                prezzo_vendita=p.prezzo_vendita, costo_variabile_unitario=costo_variabile_unitario,
                margine_di_contribuzione_unitario=margine_unitario,
                percentuale_margine_di_contribuzione=percentuale,
                quota_costi_fissi_aziendali=aziendale.totale_costi_fissi,
                punto_di_pareggio_quantita=bep_quantita,
            ))
        return risultati

    # ── Dashboard KPI industriale ─────────────────────────────────────────────

    async def kpi_industriale(self, anno: int | None = None) -> KPIIndustriale:
        anno = anno or date.today().year
        commesse = (await self.db.execute(select(Commessa))).scalars().all()
        aperte = [c for c in commesse if c.stato == "aperta"]
        chiuse_periodo = [
            c for c in commesse
            if c.stato == "chiusa" and c.data_chiusura and c.data_chiusura.year == anno
        ]

        commesse_service = CommesseService(self.db)
        valore_aperte = Decimal("0")
        for c in aperte:
            riepilogo = await commesse_service.riepilogo_costi(c.id)
            valore_aperte += riepilogo.totale

        scostamento_periodo = Decimal("0")
        for c in chiuse_periodo:
            sc = await commesse_service.scostamenti(c.id)
            scostamento_periodo += sc.scostamento_totale

        giacenze = await ProdottiService(self.db).get_giacenze()
        valore_magazzino = sum((g.valore_giacenza for g in giacenze), Decimal("0"))
        sotto_scorta = sum(1 for g in giacenze if g.sotto_scorta)

        try:
            aziendale = await self.break_even_aziendale()
            margine_pct = aziendale.percentuale_margine_di_contribuzione if aziendale.totale_ricavi > 0 else None
        except Exception:
            margine_pct = None

        return KPIIndustriale(
            numero_commesse_aperte=len(aperte), valore_commesse_aperte=_q(valore_aperte),
            numero_commesse_chiuse_periodo=len(chiuse_periodo), scostamento_totale_periodo=_q(scostamento_periodo),
            valore_magazzino=_q(valore_magazzino), numero_prodotti_sotto_scorta=sotto_scorta,
            margine_di_contribuzione_percentuale=margine_pct,
        )

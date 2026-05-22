from decimal import Decimal
from datetime import date
from collections import defaultdict

from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.financial import Movimento
from app.schemas.dashboard import (
    DashboardResponse,
    KPIFinanziario,
    SaldoMensile,
    PuntoGrafico,
)

ENTRATA = "entrata"
USCITA = "uscita"


class DashboardService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_kpi(self) -> KPIFinanziario:
        """Calcola i KPI finanziari principali."""
        result_entrate = await self.db.execute(
            select(func.sum(Movimento.importo)).where(
                Movimento.tipo == ENTRATA
            )
        )
        result_uscite = await self.db.execute(
            select(func.sum(Movimento.importo)).where(
                Movimento.tipo == USCITA
            )
        )

        totale_entrate = result_entrate.scalar() or Decimal("0.00")
        totale_uscite = result_uscite.scalar() or Decimal("0.00")
        cashflow_netto = totale_entrate - totale_uscite
        saldo_operativo = cashflow_netto
        indice_liquidita = (
            totale_entrate / totale_uscite if totale_uscite > 0 else Decimal("0.00")
        )

        return KPIFinanziario(
            saldo_operativo=saldo_operativo,
            totale_entrate=totale_entrate,
            totale_uscite=totale_uscite,
            cashflow_netto=cashflow_netto,
            indice_liquidita=indice_liquidita,
        )

    async def get_andamento_mensile(self, mesi: int = 12) -> list[SaldoMensile]:
        """Restituisce l'andamento mensile degli ultimi N mesi."""
        data_inizio = date.today().replace(day=1)
        # Calcola mesi indietro
        anno = data_inizio.year
        mese = data_inizio.month - mesi
        while mese <= 0:
            mese += 12
            anno -= 1
        data_inizio = date(anno, mese, 1)

        result = await self.db.execute(
            select(
                func.strftime("%Y-%m", Movimento.data).label("mese"),
                Movimento.tipo,
                func.sum(Movimento.importo).label("totale"),
            )
            .where(Movimento.data >= data_inizio)
            .group_by("mese", Movimento.tipo)
            .order_by("mese")
        )

        mesi_data: dict[str, dict] = defaultdict(lambda: {"entrate": Decimal("0"), "uscite": Decimal("0")})
        for row in result.all():
            if row.tipo == ENTRATA:
                mesi_data[row.mese]["entrate"] = row.totale
            else:
                mesi_data[row.mese]["uscite"] = row.totale

        return [
            SaldoMensile(
                mese=mese,
                entrate=dati["entrate"],
                uscite=dati["uscite"],
                saldo=dati["entrate"] - dati["uscite"],
            )
            for mese, dati in sorted(mesi_data.items())
        ]

    async def get_cashflow_settimanale(self, mesi: int = 24) -> list[PuntoGrafico]:
        """Restituisce il cashflow cumulativo aggregato per mese negli ultimi N mesi.
        Finestra ampia (default 24 mesi) per coprire dati storici."""
        oggi = date.today()
        anno = oggi.year
        mese = oggi.month - mesi
        while mese <= 0:
            mese += 12
            anno -= 1
        data_inizio = date(anno, mese, 1)

        result = await self.db.execute(
            select(
                func.strftime("%Y-%m", Movimento.data).label("mese"),
                Movimento.tipo,
                func.sum(Movimento.importo).label("totale"),
            )
            .where(Movimento.data >= data_inizio)
            .group_by("mese", Movimento.tipo)
            .order_by("mese")
        )

        mesi_data: dict[str, dict] = defaultdict(lambda: {"entrate": Decimal("0"), "uscite": Decimal("0")})
        for row in result.all():
            if row.tipo == ENTRATA:
                mesi_data[row.mese]["entrate"] = row.totale
            else:
                mesi_data[row.mese]["uscite"] = row.totale

        punti = []
        cashflow_cumulativo = Decimal("0")
        for mese_key, dati in sorted(mesi_data.items()):
            cashflow_cumulativo += dati["entrate"] - dati["uscite"]
            punti.append(
                PuntoGrafico(
                    data=mese_key,
                    entrate=dati["entrate"],
                    uscite=dati["uscite"],
                    cashflow=cashflow_cumulativo,
                )
            )
        return punti

    async def get_dashboard(self) -> DashboardResponse:
        """Assembla il payload completo per la dashboard."""
        kpi = await self.get_kpi()
        andamento = await self.get_andamento_mensile()
        cashflow = await self.get_cashflow_settimanale()

        return DashboardResponse(
            kpi=kpi,
            andamento_mensile=andamento,
            cashflow_settimanale=cashflow,
            aggiornato_al=date.today(),
        )

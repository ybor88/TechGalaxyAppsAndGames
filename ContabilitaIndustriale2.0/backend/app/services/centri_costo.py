from decimal import Decimal, ROUND_HALF_UP

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.centri_costo import CentroCosto, BaseRiparto, ValoreBaseRiparto, RipartoCostoIndiretto
from app.schemas.centri_costo import (
    CentroCostoCreate, CentroCostoUpdate, CentroCostoOut,
    BaseRipartoCreate, BaseRipartoOut,
    ValoreBaseRipartoCreate, ValoreBaseRipartoOut,
    RipartoCostoIndirettoCreate, RipartoCostoIndirettoOut,
    AllocazioneRiga, RipartoCalcolato,
)

TWO_DP = Decimal("0.01")
FOUR_DP = Decimal("0.0001")


def _q(val: Decimal, dp: Decimal = TWO_DP) -> Decimal:
    return val.quantize(dp, rounding=ROUND_HALF_UP)


class CentriCostoService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Centri di costo ──────────────────────────────────────────────────────

    async def crea_centro(self, payload: CentroCostoCreate) -> CentroCostoOut:
        if payload.centro_padre_id is not None:
            padre = await self.db.get(CentroCosto, payload.centro_padre_id)
            if padre is None:
                raise ValueError("Centro di costo padre non trovato")
        centro = CentroCosto(**payload.model_dump())
        self.db.add(centro)
        await self.db.commit()
        await self.db.refresh(centro)
        return await self._centro_to_out(centro)

    async def list_centri(self, solo_attivi: bool = False) -> list[CentroCostoOut]:
        stmt = select(CentroCosto).options(selectinload(CentroCosto.centro_padre))
        if solo_attivi:
            stmt = stmt.where(CentroCosto.attivo.is_(True))
        stmt = stmt.order_by(CentroCosto.codice)
        centri = (await self.db.execute(stmt)).scalars().all()
        return [await self._centro_to_out(c) for c in centri]

    async def aggiorna_centro(self, centro_id: int, payload: CentroCostoUpdate) -> CentroCostoOut | None:
        centro = await self.db.get(CentroCosto, centro_id)
        if centro is None:
            return None
        for campo, valore in payload.model_dump(exclude_unset=True).items():
            setattr(centro, campo, valore)
        await self.db.commit()
        await self.db.refresh(centro)
        return await self._centro_to_out(centro)

    async def elimina_centro(self, centro_id: int) -> bool:
        centro = await self.db.get(CentroCosto, centro_id)
        if centro is None:
            return False
        await self.db.delete(centro)
        await self.db.commit()
        return True

    async def _centro_to_out(self, centro: CentroCosto) -> CentroCostoOut:
        padre_desc = None
        if centro.centro_padre_id is not None:
            padre = await self.db.get(CentroCosto, centro.centro_padre_id)
            padre_desc = padre.descrizione if padre else None
        return CentroCostoOut(
            id=centro.id, codice=centro.codice, descrizione=centro.descrizione, tipo=centro.tipo,
            centro_padre_id=centro.centro_padre_id, centro_padre_descrizione=padre_desc,
            attivo=centro.attivo, note=centro.note, created_at=centro.created_at,
        )

    # ── Basi di riparto ──────────────────────────────────────────────────────

    async def crea_base_riparto(self, payload: BaseRipartoCreate) -> BaseRipartoOut:
        base = BaseRiparto(**payload.model_dump())
        self.db.add(base)
        await self.db.commit()
        await self.db.refresh(base)
        return BaseRipartoOut.model_validate(base)

    async def list_basi_riparto(self) -> list[BaseRipartoOut]:
        basi = (await self.db.execute(select(BaseRiparto).order_by(BaseRiparto.descrizione))).scalars().all()
        return [BaseRipartoOut.model_validate(b) for b in basi]

    async def crea_valore_base(self, payload: ValoreBaseRipartoCreate) -> ValoreBaseRipartoOut:
        centro = await self.db.get(CentroCosto, payload.centro_costo_id)
        if centro is None:
            raise ValueError("Centro di costo non trovato")
        if await self.db.get(BaseRiparto, payload.base_riparto_id) is None:
            raise ValueError("Base di riparto non trovata")
        existing = await self.db.execute(
            select(ValoreBaseRiparto).where(
                ValoreBaseRiparto.base_riparto_id == payload.base_riparto_id,
                ValoreBaseRiparto.centro_costo_id == payload.centro_costo_id,
                ValoreBaseRiparto.periodo == payload.periodo,
            )
        )
        if existing.scalar_one_or_none() is not None:
            raise ValueError("Valore già inserito per questo centro/base/periodo: eliminalo prima di reinserirlo.")
        valore = ValoreBaseRiparto(**payload.model_dump())
        self.db.add(valore)
        await self.db.commit()
        await self.db.refresh(valore)
        return ValoreBaseRipartoOut(
            id=valore.id, base_riparto_id=valore.base_riparto_id, centro_costo_id=valore.centro_costo_id,
            centro_costo_descrizione=centro.descrizione, periodo=valore.periodo, quantita=valore.quantita,
        )

    async def list_valori_base(self, base_riparto_id: int | None = None, periodo: str | None = None) -> list[ValoreBaseRipartoOut]:
        stmt = select(ValoreBaseRiparto).options(selectinload(ValoreBaseRiparto.centro_costo))
        if base_riparto_id is not None:
            stmt = stmt.where(ValoreBaseRiparto.base_riparto_id == base_riparto_id)
        if periodo is not None:
            stmt = stmt.where(ValoreBaseRiparto.periodo == periodo)
        valori = (await self.db.execute(stmt)).scalars().all()
        return [
            ValoreBaseRipartoOut(
                id=v.id, base_riparto_id=v.base_riparto_id, centro_costo_id=v.centro_costo_id,
                centro_costo_descrizione=v.centro_costo.descrizione, periodo=v.periodo, quantita=v.quantita,
            )
            for v in valori
        ]

    async def elimina_valore_base(self, valore_id: int) -> bool:
        valore = await self.db.get(ValoreBaseRiparto, valore_id)
        if valore is None:
            return False
        await self.db.delete(valore)
        await self.db.commit()
        return True

    # ── Riparto costi indiretti ──────────────────────────────────────────────

    async def crea_riparto(self, payload: RipartoCostoIndirettoCreate) -> RipartoCostoIndirettoOut:
        if await self.db.get(CentroCosto, payload.centro_costo_origine_id) is None:
            raise ValueError("Centro di costo origine non trovato")
        if await self.db.get(BaseRiparto, payload.base_riparto_id) is None:
            raise ValueError("Base di riparto non trovata")
        riparto = RipartoCostoIndiretto(**payload.model_dump())
        self.db.add(riparto)
        await self.db.commit()
        await self.db.refresh(riparto)
        return await self._riparto_to_out(riparto)

    async def list_riparti(self, periodo: str | None = None) -> list[RipartoCostoIndirettoOut]:
        stmt = select(RipartoCostoIndiretto).options(
            selectinload(RipartoCostoIndiretto.centro_costo_origine),
            selectinload(RipartoCostoIndiretto.base_riparto),
        )
        if periodo is not None:
            stmt = stmt.where(RipartoCostoIndiretto.periodo == periodo)
        stmt = stmt.order_by(RipartoCostoIndiretto.periodo.desc())
        riparti = (await self.db.execute(stmt)).scalars().all()
        return [await self._riparto_to_out(r) for r in riparti]

    async def elimina_riparto(self, riparto_id: int) -> bool:
        riparto = await self.db.get(RipartoCostoIndiretto, riparto_id)
        if riparto is None:
            return False
        await self.db.delete(riparto)
        await self.db.commit()
        return True

    async def _riparto_to_out(self, riparto: RipartoCostoIndiretto) -> RipartoCostoIndirettoOut:
        origine = await self.db.get(CentroCosto, riparto.centro_costo_origine_id)
        base = await self.db.get(BaseRiparto, riparto.base_riparto_id)
        return RipartoCostoIndirettoOut(
            id=riparto.id, descrizione=riparto.descrizione,
            centro_costo_origine_id=riparto.centro_costo_origine_id,
            centro_costo_origine_descrizione=origine.descrizione,
            importo=riparto.importo, base_riparto_id=riparto.base_riparto_id,
            base_riparto_descrizione=base.descrizione, periodo=riparto.periodo,
            note=riparto.note, created_at=riparto.created_at,
        )

    async def calcola_riparto(self, riparto_id: int) -> RipartoCalcolato:
        riparto = await self.db.get(RipartoCostoIndiretto, riparto_id)
        if riparto is None:
            raise ValueError("Riparto non trovato")
        base = await self.db.get(BaseRiparto, riparto.base_riparto_id)

        stmt = select(ValoreBaseRiparto).options(selectinload(ValoreBaseRiparto.centro_costo)).where(
            ValoreBaseRiparto.base_riparto_id == riparto.base_riparto_id,
            ValoreBaseRiparto.periodo == riparto.periodo,
            ValoreBaseRiparto.centro_costo_id != riparto.centro_costo_origine_id,
        )
        valori = (await self.db.execute(stmt)).scalars().all()
        totale_base = sum((v.quantita for v in valori), Decimal("0"))
        if totale_base <= 0:
            raise ValueError(
                "Nessun valore della base di riparto disponibile per il periodo: "
                "inserisci prima i consumi (es. ore macchina) dei centri destinatari."
            )

        allocazioni = []
        for v in valori:
            percentuale = _q(v.quantita / totale_base * 100, FOUR_DP)
            importo_allocato = _q(riparto.importo * v.quantita / totale_base)
            allocazioni.append(AllocazioneRiga(
                centro_costo_id=v.centro_costo_id,
                centro_costo_descrizione=v.centro_costo.descrizione,
                quantita_base=v.quantita,
                percentuale=percentuale,
                importo_allocato=importo_allocato,
            ))

        return RipartoCalcolato(
            riparto_id=riparto.id, descrizione=riparto.descrizione, importo_totale=riparto.importo,
            base_riparto_descrizione=base.descrizione, periodo=riparto.periodo, allocazioni=allocazioni,
        )

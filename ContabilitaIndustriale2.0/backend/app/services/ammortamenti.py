from decimal import Decimal, ROUND_HALF_UP
from datetime import date

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.financial import Conto
from app.models.ammortamenti import Cespite, QuotaAmmortamentoContabilizzata
from app.schemas.ammortamenti import (
    CespiteCreate,
    CespiteUpdate,
    CespiteOut,
    QuotaPiano,
    PianoAmmortamentoResponse,
    RiepilogoAmmortamenti,
    ContabilizzaQuotaResponse,
)
from app.schemas.contabilita import RegistrazioneCreate, RigaRegistrazioneIn
from app.services.contabilita import ContabilitaService

TWO_DP = Decimal("0.01")


def _q(val: Decimal) -> Decimal:
    return val.quantize(TWO_DP, rounding=ROUND_HALF_UP)


# ── Tabella ministeriale (DM 31/12/1988) — sottoinsieme delle categorie più
# comuni per una PMI. Le aliquote sono indicative: vanno sempre verificate con
# il proprio commercialista in base al settore ATECO specifico, che nella
# tabella ufficiale prevede centinaia di voci per gruppo/specie industriale. ──
TABELLA_MINISTERIALE: dict[str, dict] = {
    "fabbricati_industriali": {"label": "Fabbricati destinati all'industria", "aliquota": Decimal("3.00")},
    "costruzioni_leggere": {"label": "Costruzioni leggere, baracche, tettoie", "aliquota": Decimal("10.00")},
    "impianti_generici": {"label": "Impianti generici", "aliquota": Decimal("10.00")},
    "macchinari_generici": {"label": "Macchinari e apparecchi (generico)", "aliquota": Decimal("10.00")},
    "attrezzatura_varia": {"label": "Attrezzatura varia e minuta", "aliquota": Decimal("15.00")},
    "mobili_arredi_ufficio": {"label": "Mobili e macchine ordinarie d'ufficio", "aliquota": Decimal("12.00")},
    "macchine_ufficio_elettroniche": {"label": "Macchine d'ufficio elettroniche e computer", "aliquota": Decimal("20.00")},
    "autovetture": {"label": "Autovetture, motoveicoli e simili", "aliquota": Decimal("25.00")},
    "autocarri": {"label": "Automezzi da trasporto merci (autocarri)", "aliquota": Decimal("20.00")},
    "software": {"label": "Software e diritti di utilizzazione di opere dell'ingegno", "aliquota": Decimal("33.33")},
    "brevetti": {"label": "Brevetti industriali", "aliquota": Decimal("33.33")},
    "marchi": {"label": "Marchi d'impresa (1/18 annuo)", "aliquota": Decimal("5.56")},
    "altro": {"label": "Altro (aliquota libera, da impostare manualmente)", "aliquota": Decimal("10.00")},
}


def get_tabella_ministeriale() -> list[dict]:
    return [
        {"categoria": k, "label": v["label"], "aliquota_fiscale": v["aliquota"]}
        for k, v in TABELLA_MINISTERIALE.items()
    ]


def aliquota_fiscale_categoria(categoria: str) -> Decimal:
    entry = TABELLA_MINISTERIALE.get(categoria)
    if entry is None:
        raise ValueError(f"Categoria cespite sconosciuta: {categoria}")
    return entry["aliquota"]


# ── Calcolo piano di ammortamento ──────────────────────────────────────────────

def calcola_piano(cespite: Cespite, fino_ad_anno: int | None = None) -> list[QuotaPiano]:
    """
    Calcola il piano di ammortamento a quote costanti, sia civilistico che fiscale.

    - Civilistico (OIC 16): la quota del primo esercizio è ridotta pro-rata in base
      ai mesi di effettivo utilizzo (il mese di acquisto si considera intero).
    - Fiscale (art. 102 co. 2 TUIR): la quota del primo esercizio è sempre ridotta
      al 50% dell'aliquota ministeriale, indipendentemente dal mese di acquisto.
    - In nessun caso il fondo ammortamento accumulato supera il costo storico.
    - Se il cespite è dismesso, il piano si interrompe all'anno di dismissione.
    """
    costo = cespite.costo_storico
    anno_acquisto = cespite.data_acquisto.year
    mese_acquisto = cespite.data_acquisto.month

    quota_civ_annua = _q(costo * cespite.aliquota_civilistica / 100)
    quota_fis_annua = _q(costo * cespite.aliquota_fiscale / 100)

    anno_dismissione = (
        cespite.data_dismissione.year
        if cespite.dismesso and cespite.data_dismissione
        else None
    )

    quote: list[QuotaPiano] = []
    fondo_civ = Decimal("0.00")
    fondo_fis = Decimal("0.00")
    anno = anno_acquisto
    iterazioni = 0

    while fondo_civ < costo or fondo_fis < costo:
        iterazioni += 1
        if iterazioni > 200:  # protezione contro aliquote a 0 o dati incoerenti
            break
        if anno_dismissione is not None and anno > anno_dismissione:
            break
        if fino_ad_anno is not None and anno > fino_ad_anno:
            break

        if anno == anno_acquisto:
            mesi_utilizzo = 13 - mese_acquisto  # es. acquisto a marzo -> 10 mesi residui
            quota_civ = _q(quota_civ_annua * mesi_utilizzo / 12)
            quota_fis = _q(quota_fis_annua / 2)  # riduzione al 50% ex art. 102 TUIR
        else:
            quota_civ = quota_civ_annua
            quota_fis = quota_fis_annua

        quota_civ = min(quota_civ, costo - fondo_civ)
        quota_fis = min(quota_fis, costo - fondo_fis)
        quota_civ = max(quota_civ, Decimal("0.00"))
        quota_fis = max(quota_fis, Decimal("0.00"))

        fondo_civ = _q(fondo_civ + quota_civ)
        fondo_fis = _q(fondo_fis + quota_fis)

        quote.append(
            QuotaPiano(
                anno=anno,
                quota_civilistica=quota_civ,
                fondo_civilistico=fondo_civ,
                valore_residuo_civilistico=_q(costo - fondo_civ),
                quota_fiscale=quota_fis,
                fondo_fiscale=fondo_fis,
                valore_residuo_fiscale=_q(costo - fondo_fis),
            )
        )
        anno += 1

    return quote


class AmmortamentiService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── CRUD cespiti ─────────────────────────────────────────────────────────

    async def crea_cespite(self, payload: CespiteCreate) -> CespiteOut:
        conto_ids = [payload.conto_costo_id, payload.conto_fondo_id]
        res = await self.db.execute(select(Conto).where(Conto.id.in_(conto_ids)))
        conti_map = {c.id: c for c in res.scalars().all()}
        missing = set(conto_ids) - conti_map.keys()
        if missing:
            raise ValueError(f"Conti non trovati: {sorted(missing)}")

        aliquota_fiscale = payload.aliquota_fiscale
        if aliquota_fiscale is None:
            aliquota_fiscale = aliquota_fiscale_categoria(payload.categoria)

        aliquota_civilistica = payload.aliquota_civilistica
        if aliquota_civilistica is None:
            aliquota_civilistica = aliquota_fiscale

        cespite = Cespite(
            descrizione=payload.descrizione,
            categoria=payload.categoria,
            data_acquisto=payload.data_acquisto,
            costo_storico=payload.costo_storico,
            aliquota_civilistica=aliquota_civilistica,
            aliquota_fiscale=aliquota_fiscale,
            conto_costo_id=payload.conto_costo_id,
            conto_fondo_id=payload.conto_fondo_id,
            note=payload.note,
        )
        self.db.add(cespite)
        await self.db.commit()
        await self.db.refresh(cespite)
        return await self._to_out(cespite)

    async def list_cespiti(self, includi_dismessi: bool = True) -> list[CespiteOut]:
        stmt = select(Cespite).options(
            selectinload(Cespite.conto_costo), selectinload(Cespite.conto_fondo)
        )
        if not includi_dismessi:
            stmt = stmt.where(Cespite.dismesso.is_(False))
        stmt = stmt.order_by(Cespite.data_acquisto.desc())
        cespiti = (await self.db.execute(stmt)).scalars().all()
        return [await self._to_out(c) for c in cespiti]

    async def get_cespite(self, cespite_id: int) -> CespiteOut | None:
        cespite = await self._load(cespite_id)
        if cespite is None:
            return None
        return await self._to_out(cespite)

    async def _load(self, cespite_id: int) -> Cespite | None:
        stmt = (
            select(Cespite)
            .options(selectinload(Cespite.conto_costo), selectinload(Cespite.conto_fondo))
            .where(Cespite.id == cespite_id)
        )
        return (await self.db.execute(stmt)).scalar_one_or_none()

    async def aggiorna_cespite(self, cespite_id: int, payload: CespiteUpdate) -> CespiteOut | None:
        cespite = await self._load(cespite_id)
        if cespite is None:
            return None

        ha_quote = await self._ha_quote_contabilizzate(cespite_id)
        campi_bloccati = {"costo_storico", "data_acquisto", "categoria"}
        dati = payload.model_dump(exclude_unset=True)
        if ha_quote and campi_bloccati & dati.keys():
            raise ValueError(
                "Impossibile modificare costo storico, data acquisto o categoria: "
                "esistono già quote di ammortamento contabilizzate per questo cespite."
            )

        for campo, valore in dati.items():
            setattr(cespite, campo, valore)

        await self.db.commit()
        await self.db.refresh(cespite)
        return await self._to_out(cespite)

    async def elimina_cespite(self, cespite_id: int) -> bool:
        cespite = await self._load(cespite_id)
        if cespite is None:
            return False
        if await self._ha_quote_contabilizzate(cespite_id):
            raise ValueError(
                "Impossibile eliminare: esistono quote di ammortamento già contabilizzate."
            )
        await self.db.delete(cespite)
        await self.db.commit()
        return True

    async def dismetti_cespite(self, cespite_id: int, data_dismissione: date) -> CespiteOut | None:
        cespite = await self._load(cespite_id)
        if cespite is None:
            return None
        if data_dismissione < cespite.data_acquisto:
            raise ValueError("La data di dismissione non può precedere la data di acquisto.")
        cespite.dismesso = True
        cespite.data_dismissione = data_dismissione
        await self.db.commit()
        await self.db.refresh(cespite)
        return await self._to_out(cespite)

    async def _ha_quote_contabilizzate(self, cespite_id: int) -> bool:
        res = await self.db.execute(
            select(QuotaAmmortamentoContabilizzata.id).where(
                QuotaAmmortamentoContabilizzata.cespite_id == cespite_id
            )
        )
        return res.first() is not None

    async def _to_out(self, cespite: Cespite) -> CespiteOut:
        return CespiteOut(
            id=cespite.id,
            descrizione=cespite.descrizione,
            categoria=cespite.categoria,
            categoria_label=TABELLA_MINISTERIALE.get(cespite.categoria, {}).get("label", cespite.categoria),
            data_acquisto=cespite.data_acquisto,
            costo_storico=cespite.costo_storico,
            aliquota_civilistica=cespite.aliquota_civilistica,
            aliquota_fiscale=cespite.aliquota_fiscale,
            conto_costo_id=cespite.conto_costo_id,
            conto_costo_descrizione=cespite.conto_costo.descrizione if cespite.conto_costo else "",
            conto_fondo_id=cespite.conto_fondo_id,
            conto_fondo_descrizione=cespite.conto_fondo.descrizione if cespite.conto_fondo else "",
            note=cespite.note,
            dismesso=cespite.dismesso,
            data_dismissione=cespite.data_dismissione,
            created_at=cespite.created_at,
        )

    # ── Piano di ammortamento ────────────────────────────────────────────────

    async def get_piano(self, cespite_id: int) -> PianoAmmortamentoResponse | None:
        cespite = await self._load(cespite_id)
        if cespite is None:
            return None
        quote = calcola_piano(cespite)
        anni_contabilizzati = await self._anni_contabilizzati(cespite_id)
        for q in quote:
            q.contabilizzato = q.anno in anni_contabilizzati
        return PianoAmmortamentoResponse(
            cespite_id=cespite.id,
            descrizione=cespite.descrizione,
            costo_storico=cespite.costo_storico,
            quote=quote,
        )

    async def _anni_contabilizzati(self, cespite_id: int) -> set[int]:
        res = await self.db.execute(
            select(QuotaAmmortamentoContabilizzata.anno).where(
                QuotaAmmortamentoContabilizzata.cespite_id == cespite_id
            )
        )
        return set(res.scalars().all())

    # ── Riepilogo aggregato (per dashboard/UI) ───────────────────────────────

    async def get_riepilogo(self, anno: int) -> RiepilogoAmmortamenti:
        cespiti = (await self.db.execute(select(Cespite))).scalars().all()
        totale_costo_storico = Decimal("0.00")
        totale_fondo_civilistico = Decimal("0.00")
        totale_quota_anno = Decimal("0.00")
        totale_valore_residuo = Decimal("0.00")
        numero_cespiti = 0

        for c in cespiti:
            if c.dismesso and c.data_dismissione and c.data_dismissione.year < anno:
                continue
            numero_cespiti += 1
            totale_costo_storico += c.costo_storico
            quote = calcola_piano(c, fino_ad_anno=anno)
            if quote:
                ultima = quote[-1]
                totale_fondo_civilistico += ultima.fondo_civilistico
                totale_valore_residuo += ultima.valore_residuo_civilistico
                quota_anno_corrente = next((q for q in quote if q.anno == anno), None)
                if quota_anno_corrente:
                    totale_quota_anno += quota_anno_corrente.quota_civilistica

        return RiepilogoAmmortamenti(
            anno=anno,
            numero_cespiti=numero_cespiti,
            totale_costo_storico=_q(totale_costo_storico),
            totale_fondo_civilistico=_q(totale_fondo_civilistico),
            totale_quota_anno=_q(totale_quota_anno),
            totale_valore_residuo=_q(totale_valore_residuo),
        )

    # ── Contabilizzazione in prima nota ──────────────────────────────────────

    async def contabilizza_quota(self, cespite_id: int, anno: int) -> ContabilizzaQuotaResponse:
        cespite = await self._load(cespite_id)
        if cespite is None:
            raise ValueError("Cespite non trovato")

        anni_gia_fatti = await self._anni_contabilizzati(cespite_id)
        if anno in anni_gia_fatti:
            raise ValueError(f"La quota di ammortamento per l'anno {anno} è già stata contabilizzata.")

        quote = calcola_piano(cespite)
        quota = next((q for q in quote if q.anno == anno), None)
        if quota is None:
            raise ValueError(f"Nessuna quota di ammortamento prevista per l'anno {anno} per questo cespite.")
        if quota.quota_civilistica <= Decimal("0.00"):
            raise ValueError(f"La quota di ammortamento civilistica per l'anno {anno} è pari a zero.")

        registrazione = await ContabilitaService(self.db).crea_registrazione(
            RegistrazioneCreate(
                data=date(anno, 12, 31),
                causale=f"Ammortamento {anno} — {cespite.descrizione}",
                tipo_causale="altro",
                righe=[
                    RigaRegistrazioneIn(
                        conto_id=cespite.conto_costo_id,
                        descrizione=f"Quota ammortamento {anno}",
                        dare=quota.quota_civilistica,
                        avere=Decimal("0.00"),
                    ),
                    RigaRegistrazioneIn(
                        conto_id=cespite.conto_fondo_id,
                        descrizione=f"Fondo ammortamento {anno}",
                        dare=Decimal("0.00"),
                        avere=quota.quota_civilistica,
                    ),
                ],
            )
        )

        self.db.add(
            QuotaAmmortamentoContabilizzata(
                cespite_id=cespite.id,
                anno=anno,
                importo=quota.quota_civilistica,
                registrazione_id=registrazione.id,
            )
        )
        await self.db.commit()

        return ContabilizzaQuotaResponse(
            cespite_id=cespite.id,
            anno=anno,
            importo=quota.quota_civilistica,
            registrazione_id=registrazione.id,
        )

from decimal import Decimal, ROUND_HALF_UP
from datetime import date

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.financial import Conto
from app.models.paghe import Dipendente, CedolinoPaga
from app.schemas.paghe import DipendenteCreate, DipendenteUpdate, CedolinoOut
from app.schemas.contabilita import RegistrazioneCreate, RigaRegistrazioneIn
from app.services.contabilita import ContabilitaService

TWO_DP = Decimal("0.01")


def _q(val: Decimal) -> Decimal:
    return val.quantize(TWO_DP, rounding=ROUND_HALF_UP)


# ── Motore di calcolo (modello semplificato — NON sostituisce un consulente
# del lavoro: non include addizionali regionali/comunali, conguaglio di fine
# anno, ANF, altre detrazioni per carichi di famiglia, o CCNL specifici) ──────

# Scaglioni IRPEF nazionali 2024 (D.Lgs. 216/2023): soglia superiore, aliquota %.
# L'ultimo scaglione (soglia=None) si applica a tutto il reddito eccedente.
SCAGLIONI_IRPEF: list[tuple[Decimal | None, Decimal]] = [
    (Decimal("28000"), Decimal("23.00")),
    (Decimal("50000"), Decimal("35.00")),
    (None, Decimal("43.00")),
]


def calcola_irpef_lorda_annua(imponibile_annuo: Decimal) -> Decimal:
    """Imposta lorda annua calcolata a scaglioni progressivi sull'imponibile fiscale annuo."""
    if imponibile_annuo <= 0:
        return Decimal("0.00")

    imposta = Decimal("0.00")
    soglia_precedente = Decimal("0")
    for soglia, aliquota in SCAGLIONI_IRPEF:
        if soglia is None or imponibile_annuo <= soglia:
            imposta += (imponibile_annuo - soglia_precedente) * aliquota / 100
            break
        imposta += (soglia - soglia_precedente) * aliquota / 100
        soglia_precedente = soglia
    return _q(imposta)


def calcola_detrazione_lavoro_dipendente_annua(reddito_annuo: Decimal) -> Decimal:
    """
    Detrazione per lavoro dipendente (art. 13 co. 1 TUIR), formula semplificata
    per rapporto a tempo indeterminato per l'intero anno (nessun ragguaglio a
    giorni di lavoro effettivi, nessuna distinzione per contratti a termine).
    """
    if reddito_annuo <= 0:
        return Decimal("0.00")
    if reddito_annuo <= Decimal("15000"):
        detrazione = Decimal("1955.00")
    elif reddito_annuo <= Decimal("28000"):
        detrazione = Decimal("1910.00") + Decimal("1190.00") * (Decimal("28000") - reddito_annuo) / Decimal("13000")
    elif reddito_annuo <= Decimal("50000"):
        detrazione = Decimal("1190.00") * (Decimal("50000") - reddito_annuo) / Decimal("22000")
    else:
        detrazione = Decimal("0.00")
    return _q(max(detrazione, Decimal("0.00")))


def calcola_cedolino(dipendente: Dipendente, anno: int, mese: int) -> CedolinoOut:
    lordo = dipendente.retribuzione_lorda_mensile
    mensilita = dipendente.numero_mensilita

    contributi_dip = _q(lordo * dipendente.aliquota_inps_dipendente / 100)
    imponibile_fiscale_mensile = lordo - contributi_dip

    # Il reddito annuo viene stimato come imponibile mensile x numero di mensilità
    # e l'imposta netta annua viene ripartita in quote uguali su tutte le mensilità
    # (semplificazione: nessun conguaglio progressivo mese per mese).
    reddito_annuo_presunto = imponibile_fiscale_mensile * mensilita
    irpef_lorda_annua = calcola_irpef_lorda_annua(reddito_annuo_presunto)
    detrazione_annua = (
        calcola_detrazione_lavoro_dipendente_annua(reddito_annuo_presunto)
        if dipendente.detrazioni_attive
        else Decimal("0.00")
    )
    irpef_netta_annua = max(Decimal("0.00"), irpef_lorda_annua - detrazione_annua)

    irpef_lorda_mensile = _q(irpef_lorda_annua / mensilita)
    detrazione_mensile = _q(detrazione_annua / mensilita)
    irpef_netta_mensile = _q(irpef_netta_annua / mensilita)

    netto = _q(lordo - contributi_dip - irpef_netta_mensile)

    contributi_azienda = _q(lordo * dipendente.aliquota_inps_azienda / 100)
    # TFR ex art. 2120 c.c.: retribuzione utile annua / 13,5, riproporzionata al mese.
    quota_tfr = _q(lordo / Decimal("13.5"))
    costo_azienda = _q(lordo + contributi_azienda + quota_tfr)

    return CedolinoOut(
        dipendente_id=dipendente.id,
        anno=anno,
        mese=mese,
        retribuzione_lorda=lordo,
        contributi_inps_dipendente=contributi_dip,
        imponibile_fiscale=_q(imponibile_fiscale_mensile),
        irpef_lorda=irpef_lorda_mensile,
        detrazioni_irpef=detrazione_mensile,
        irpef_netta=irpef_netta_mensile,
        netto_busta=netto,
        contributi_inps_azienda=contributi_azienda,
        quota_tfr=quota_tfr,
        costo_azienda=costo_azienda,
    )


class PagheService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── CRUD dipendenti ──────────────────────────────────────────────────────

    async def crea_dipendente(self, payload: DipendenteCreate) -> Dipendente:
        dip = Dipendente(**payload.model_dump())
        self.db.add(dip)
        await self.db.commit()
        await self.db.refresh(dip)
        return dip

    async def list_dipendenti(self, includi_cessati: bool = True) -> list[Dipendente]:
        stmt = select(Dipendente)
        if not includi_cessati:
            stmt = stmt.where(Dipendente.data_cessazione.is_(None))
        stmt = stmt.order_by(Dipendente.cognome, Dipendente.nome)
        return list((await self.db.execute(stmt)).scalars().all())

    async def get_dipendente(self, dipendente_id: int) -> Dipendente | None:
        return (
            await self.db.execute(select(Dipendente).where(Dipendente.id == dipendente_id))
        ).scalar_one_or_none()

    async def aggiorna_dipendente(self, dipendente_id: int, payload: DipendenteUpdate) -> Dipendente | None:
        dip = await self.get_dipendente(dipendente_id)
        if dip is None:
            return None
        for campo, valore in payload.model_dump(exclude_unset=True).items():
            setattr(dip, campo, valore)
        await self.db.commit()
        await self.db.refresh(dip)
        return dip

    async def elimina_dipendente(self, dipendente_id: int) -> bool:
        dip = await self.get_dipendente(dipendente_id)
        if dip is None:
            return False
        await self.db.delete(dip)
        await self.db.commit()
        return True

    # ── Cedolini ─────────────────────────────────────────────────────────────

    async def calcola_anteprima(self, dipendente_id: int, anno: int, mese: int) -> CedolinoOut | None:
        dip = await self.get_dipendente(dipendente_id)
        if dip is None:
            return None
        return calcola_cedolino(dip, anno, mese)

    async def salva_cedolino(self, dipendente_id: int, anno: int, mese: int) -> CedolinoOut:
        dip = await self.get_dipendente(dipendente_id)
        if dip is None:
            raise ValueError("Dipendente non trovato")

        esistente = await self._trova_cedolino(dipendente_id, anno, mese)
        if esistente is not None:
            raise ValueError(f"Cedolino {mese:02d}/{anno} già presente per questo dipendente.")

        calcolo = calcola_cedolino(dip, anno, mese)
        cedolino = CedolinoPaga(
            dipendente_id=dipendente_id,
            anno=anno,
            mese=mese,
            retribuzione_lorda=calcolo.retribuzione_lorda,
            contributi_inps_dipendente=calcolo.contributi_inps_dipendente,
            imponibile_fiscale=calcolo.imponibile_fiscale,
            irpef_lorda=calcolo.irpef_lorda,
            detrazioni_irpef=calcolo.detrazioni_irpef,
            irpef_netta=calcolo.irpef_netta,
            netto_busta=calcolo.netto_busta,
            contributi_inps_azienda=calcolo.contributi_inps_azienda,
            quota_tfr=calcolo.quota_tfr,
            costo_azienda=calcolo.costo_azienda,
        )
        self.db.add(cedolino)
        await self.db.commit()
        await self.db.refresh(cedolino)
        return CedolinoOut.model_validate(cedolino)

    async def list_cedolini(self, dipendente_id: int) -> list[CedolinoOut]:
        stmt = (
            select(CedolinoPaga)
            .where(CedolinoPaga.dipendente_id == dipendente_id)
            .order_by(CedolinoPaga.anno.desc(), CedolinoPaga.mese.desc())
        )
        rows = (await self.db.execute(stmt)).scalars().all()
        return [CedolinoOut.model_validate(r) for r in rows]

    async def get_cedolino(self, cedolino_id: int) -> CedolinoPaga | None:
        return (
            await self.db.execute(select(CedolinoPaga).where(CedolinoPaga.id == cedolino_id))
        ).scalar_one_or_none()

    async def elimina_cedolino(self, cedolino_id: int) -> bool:
        ced = await self.get_cedolino(cedolino_id)
        if ced is None:
            return False
        if ced.registrazione_id is not None:
            raise ValueError("Impossibile eliminare: il cedolino è già stato contabilizzato in prima nota.")
        await self.db.delete(ced)
        await self.db.commit()
        return True

    async def _trova_cedolino(self, dipendente_id: int, anno: int, mese: int) -> CedolinoPaga | None:
        stmt = select(CedolinoPaga).where(
            CedolinoPaga.dipendente_id == dipendente_id,
            CedolinoPaga.anno == anno,
            CedolinoPaga.mese == mese,
        )
        return (await self.db.execute(stmt)).scalar_one_or_none()

    # ── Contabilizzazione in prima nota ──────────────────────────────────────

    async def _conto_per_codice(self, codice: str) -> Conto:
        conto = (
            await self.db.execute(select(Conto).where(Conto.codice == codice))
        ).scalar_one_or_none()
        if conto is None:
            raise ValueError(
                f"Conto {codice} non trovato: inizializza il piano dei conti standard "
                "dalla sezione Contabilità Generale prima di contabilizzare i cedolini."
            )
        return conto

    async def contabilizza_cedolino(self, cedolino_id: int) -> tuple[int, int]:
        ced = await self.get_cedolino(cedolino_id)
        if ced is None:
            raise ValueError("Cedolino non trovato")
        if ced.registrazione_id is not None:
            raise ValueError("Questo cedolino è già stato contabilizzato.")

        dip = await self.get_dipendente(ced.dipendente_id)
        assert dip is not None

        conto_salari = await self._conto_per_codice("41100")
        conto_contributi = await self._conto_per_codice("41200")
        conto_tfr_costo = await self._conto_per_codice("41300")
        conto_debiti_dipendenti = await self._conto_per_codice("22400")
        conto_debiti_previdenziali = await self._conto_per_codice("22300")
        conto_debiti_tributari = await self._conto_per_codice("22200")
        conto_fondo_tfr = await self._conto_per_codice("22450")

        totale_contributi_previdenziali = _q(ced.contributi_inps_dipendente + ced.contributi_inps_azienda)

        registrazione = await ContabilitaService(self.db).crea_registrazione(
            RegistrazioneCreate(
                data=date(ced.anno, ced.mese, 1),
                causale=f"Cedolino paga {ced.mese:02d}/{ced.anno} — {dip.cognome} {dip.nome}",
                tipo_causale="altro",
                righe=[
                    RigaRegistrazioneIn(
                        conto_id=conto_salari.id,
                        descrizione="Retribuzione lorda",
                        dare=ced.retribuzione_lorda,
                        avere=Decimal("0.00"),
                    ),
                    RigaRegistrazioneIn(
                        conto_id=conto_contributi.id,
                        descrizione="Contributi INPS a carico azienda",
                        dare=ced.contributi_inps_azienda,
                        avere=Decimal("0.00"),
                    ),
                    RigaRegistrazioneIn(
                        conto_id=conto_tfr_costo.id,
                        descrizione="Accantonamento TFR",
                        dare=ced.quota_tfr,
                        avere=Decimal("0.00"),
                    ),
                    RigaRegistrazioneIn(
                        conto_id=conto_debiti_dipendenti.id,
                        descrizione="Netto da pagare al dipendente",
                        dare=Decimal("0.00"),
                        avere=ced.netto_busta,
                    ),
                    RigaRegistrazioneIn(
                        conto_id=conto_debiti_previdenziali.id,
                        descrizione="Contributi INPS da versare (dipendente + azienda)",
                        dare=Decimal("0.00"),
                        avere=totale_contributi_previdenziali,
                    ),
                    RigaRegistrazioneIn(
                        conto_id=conto_debiti_tributari.id,
                        descrizione="IRPEF trattenuta da versare",
                        dare=Decimal("0.00"),
                        avere=ced.irpef_netta,
                    ),
                    RigaRegistrazioneIn(
                        conto_id=conto_fondo_tfr.id,
                        descrizione="Accantonamento fondo TFR",
                        dare=Decimal("0.00"),
                        avere=ced.quota_tfr,
                    ),
                ],
            )
        )

        ced.registrazione_id = registrazione.id
        await self.db.commit()
        return ced.id, registrazione.id

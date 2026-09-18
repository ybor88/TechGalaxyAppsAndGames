from decimal import Decimal, ROUND_HALF_UP

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.prodotti import Prodotto, DistintaBaseRiga, MovimentoMagazzino
from app.schemas.prodotti import (
    ProdottoCreate, ProdottoUpdate, ProdottoOut,
    DistintaBaseRigaCreate, DistintaBaseRigaOut, DistintaBaseResponse,
    MovimentoMagazzinoCreate, MovimentoMagazzinoOut, GiacenzaMagazzino,
)

TWO_DP = Decimal("0.01")
FOUR_DP = Decimal("0.0001")
THREE_DP = Decimal("0.001")


def _q(val: Decimal, dp: Decimal = FOUR_DP) -> Decimal:
    return val.quantize(dp, rounding=ROUND_HALF_UP)


class ProdottiService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Prodotti ─────────────────────────────────────────────────────────────

    async def crea_prodotto(self, payload: ProdottoCreate) -> ProdottoOut:
        prodotto = Prodotto(**payload.model_dump())
        self.db.add(prodotto)
        await self.db.commit()
        await self.db.refresh(prodotto)
        return await self._to_out(prodotto)

    async def list_prodotti(self, tipo: str | None = None, solo_attivi: bool = False) -> list[ProdottoOut]:
        stmt = select(Prodotto)
        if tipo is not None:
            stmt = stmt.where(Prodotto.tipo == tipo)
        if solo_attivi:
            stmt = stmt.where(Prodotto.attivo.is_(True))
        stmt = stmt.order_by(Prodotto.codice)
        prodotti = (await self.db.execute(stmt)).scalars().all()
        return [await self._to_out(p) for p in prodotti]

    async def get_prodotto(self, prodotto_id: int) -> ProdottoOut | None:
        prodotto = await self.db.get(Prodotto, prodotto_id)
        if prodotto is None:
            return None
        return await self._to_out(prodotto)

    async def aggiorna_prodotto(self, prodotto_id: int, payload: ProdottoUpdate) -> ProdottoOut | None:
        prodotto = await self.db.get(Prodotto, prodotto_id)
        if prodotto is None:
            return None
        for campo, valore in payload.model_dump(exclude_unset=True).items():
            setattr(prodotto, campo, valore)
        await self.db.commit()
        await self.db.refresh(prodotto)
        return await self._to_out(prodotto)

    async def elimina_prodotto(self, prodotto_id: int) -> bool:
        prodotto = await self.db.get(Prodotto, prodotto_id)
        if prodotto is None:
            return False
        await self.db.delete(prodotto)
        await self.db.commit()
        return True

    async def _costo_materiale_unitario(self, prodotto_id: int) -> Decimal:
        """Costo standard dei materiali per 1 unità: somma ricorsiva sui componenti di distinta base.
        Se un componente è a sua volta un semilavorato, si usa il suo costo standard pieno
        (materiale + manodopera + indiretti), non il solo prezzo_standard."""
        stmt = select(DistintaBaseRiga).where(DistintaBaseRiga.prodotto_padre_id == prodotto_id)
        righe = (await self.db.execute(stmt)).scalars().all()
        totale = Decimal("0")
        for r in righe:
            totale += r.quantita * await self.costo_standard_totale(r.componente_id)
        return _q(totale)

    async def costo_standard_totale(self, prodotto_id: int, _visitati: frozenset[int] = frozenset()) -> Decimal:
        """Costo standard unitario pieno di un prodotto: per le materie prime è il prezzo_standard,
        per semilavorati/prodotti finiti è materiale (ricorsivo su BOM) + manodopera + indiretti standard."""
        if prodotto_id in _visitati:
            raise ValueError("Distinta base ciclica rilevata: un componente richiama sé stesso indirettamente")
        prodotto = await self.db.get(Prodotto, prodotto_id)
        if prodotto is None:
            raise ValueError("Prodotto non trovato in distinta base")
        if prodotto.tipo == "materia_prima":
            return prodotto.prezzo_standard
        if len(_visitati) > 20:
            raise ValueError("Distinta base troppo profonda (possibile ciclo)")
        stmt = select(DistintaBaseRiga).where(DistintaBaseRiga.prodotto_padre_id == prodotto_id)
        righe = (await self.db.execute(stmt)).scalars().all()
        nuovi_visitati = _visitati | {prodotto_id}
        costo_materiale = Decimal("0")
        for r in righe:
            costo_materiale += r.quantita * await self.costo_standard_totale(r.componente_id, nuovi_visitati)
        totale = (
            costo_materiale
            + prodotto.ore_manodopera_standard * prodotto.costo_orario_manodopera_standard
            + prodotto.costo_indiretto_standard_unitario
        )
        return _q(totale)

    async def _to_out(self, prodotto: Prodotto) -> ProdottoOut:
        if prodotto.tipo == "materia_prima":
            costo_materiale = Decimal("0.0000")
            costo_totale = prodotto.prezzo_standard
        else:
            costo_materiale = await self._costo_materiale_unitario(prodotto.id)
            costo_totale = _q(
                costo_materiale
                + prodotto.ore_manodopera_standard * prodotto.costo_orario_manodopera_standard
                + prodotto.costo_indiretto_standard_unitario
            )
        return ProdottoOut(
            id=prodotto.id, codice=prodotto.codice, descrizione=prodotto.descrizione, tipo=prodotto.tipo,
            unita_misura=prodotto.unita_misura, giacenza_attuale=prodotto.giacenza_attuale,
            costo_medio_ponderato=prodotto.costo_medio_ponderato, scorta_minima=prodotto.scorta_minima,
            prezzo_standard=prodotto.prezzo_standard, ore_manodopera_standard=prodotto.ore_manodopera_standard,
            costo_orario_manodopera_standard=prodotto.costo_orario_manodopera_standard,
            costo_indiretto_standard_unitario=prodotto.costo_indiretto_standard_unitario,
            costo_standard_materiale_unitario=costo_materiale, costo_standard_unitario_totale=costo_totale,
            prezzo_vendita=prodotto.prezzo_vendita,
            sotto_scorta=prodotto.giacenza_attuale < prodotto.scorta_minima,
            attivo=prodotto.attivo, note=prodotto.note, created_at=prodotto.created_at,
        )

    # ── Distinta base ────────────────────────────────────────────────────────

    async def aggiungi_componente(self, prodotto_padre_id: int, payload: DistintaBaseRigaCreate) -> DistintaBaseRigaOut:
        if prodotto_padre_id == payload.componente_id:
            raise ValueError("Un prodotto non può essere componente di sé stesso")
        padre = await self.db.get(Prodotto, prodotto_padre_id)
        if padre is None:
            raise ValueError("Prodotto padre non trovato")
        if padre.tipo == "materia_prima":
            raise ValueError("Una materia prima non può avere una distinta base")
        componente = await self.db.get(Prodotto, payload.componente_id)
        if componente is None:
            raise ValueError("Componente non trovato")
        existing = await self.db.execute(
            select(DistintaBaseRiga).where(
                DistintaBaseRiga.prodotto_padre_id == prodotto_padre_id,
                DistintaBaseRiga.componente_id == payload.componente_id,
            )
        )
        if existing.scalar_one_or_none() is not None:
            raise ValueError("Componente già presente in distinta base: modifica la quantità della riga esistente.")
        if componente.tipo != "materia_prima" and await self._contiene(payload.componente_id, prodotto_padre_id):
            raise ValueError("Distinta base ciclica: il componente scelto contiene già il prodotto padre al suo interno.")

        riga = DistintaBaseRiga(prodotto_padre_id=prodotto_padre_id, **payload.model_dump())
        self.db.add(riga)
        await self.db.commit()
        await self.db.refresh(riga)
        return DistintaBaseRigaOut(
            id=riga.id, prodotto_padre_id=riga.prodotto_padre_id, componente_id=riga.componente_id,
            componente_codice=componente.codice, componente_descrizione=componente.descrizione,
            componente_tipo=componente.tipo, quantita=riga.quantita,
            costo_standard_componente=await self.costo_standard_totale(componente.id), note=riga.note,
        )

    async def _contiene(self, prodotto_id: int, cercato_id: int, _profondita: int = 0) -> bool:
        """True se `prodotto_id` contiene (direttamente o indirettamente) `cercato_id` nella sua distinta base."""
        if _profondita > 20:
            return True  # possibile ciclo già esistente: tratta come contenuto per bloccare l'inserimento
        stmt = select(DistintaBaseRiga.componente_id).where(DistintaBaseRiga.prodotto_padre_id == prodotto_id)
        componenti_id = (await self.db.execute(stmt)).scalars().all()
        if cercato_id in componenti_id:
            return True
        for cid in componenti_id:
            if await self._contiene(cid, cercato_id, _profondita + 1):
                return True
        return False

    async def get_distinta_base(self, prodotto_id: int) -> DistintaBaseResponse | None:
        prodotto = await self.db.get(Prodotto, prodotto_id)
        if prodotto is None:
            return None
        stmt = (
            select(DistintaBaseRiga)
            .options(selectinload(DistintaBaseRiga.componente))
            .where(DistintaBaseRiga.prodotto_padre_id == prodotto_id)
            .order_by(DistintaBaseRiga.id)
        )
        righe = (await self.db.execute(stmt)).scalars().all()
        righe_out = []
        costo_materiale = Decimal("0")
        for r in righe:
            costo_componente = await self.costo_standard_totale(r.componente_id)
            costo_materiale += r.quantita * costo_componente
            righe_out.append(DistintaBaseRigaOut(
                id=r.id, prodotto_padre_id=r.prodotto_padre_id, componente_id=r.componente_id,
                componente_codice=r.componente.codice, componente_descrizione=r.componente.descrizione,
                componente_tipo=r.componente.tipo, quantita=r.quantita,
                costo_standard_componente=costo_componente, note=r.note,
            ))
        costo_materiale = _q(costo_materiale)
        return DistintaBaseResponse(
            prodotto_id=prodotto.id, descrizione=prodotto.descrizione, righe=righe_out,
            costo_materiale_unitario=costo_materiale,
        )

    async def rimuovi_componente(self, riga_id: int) -> bool:
        riga = await self.db.get(DistintaBaseRiga, riga_id)
        if riga is None:
            return False
        await self.db.delete(riga)
        await self.db.commit()
        return True

    # ── Magazzino ────────────────────────────────────────────────────────────

    async def registra_movimento(self, payload: MovimentoMagazzinoCreate) -> MovimentoMagazzinoOut:
        prodotto = await self.db.get(Prodotto, payload.prodotto_id)
        if prodotto is None:
            raise ValueError("Prodotto non trovato")

        if payload.tipo == "carico":
            if payload.costo_unitario is None or payload.costo_unitario <= 0:
                raise ValueError("Il costo unitario è obbligatorio per un carico di magazzino")
            costo_unitario = payload.costo_unitario
            nuova_giacenza = prodotto.giacenza_attuale + payload.quantita
            valore_precedente = prodotto.giacenza_attuale * prodotto.costo_medio_ponderato
            nuovo_valore = valore_precedente + payload.quantita * costo_unitario
            prodotto.costo_medio_ponderato = _q(nuovo_valore / nuova_giacenza) if nuova_giacenza > 0 else costo_unitario
            prodotto.giacenza_attuale = nuova_giacenza.quantize(THREE_DP)
        else:  # scarico
            if payload.quantita > prodotto.giacenza_attuale:
                raise ValueError(
                    f"Giacenza insufficiente: disponibili {prodotto.giacenza_attuale} {prodotto.unita_misura}, "
                    f"richiesti {payload.quantita}."
                )
            costo_unitario = prodotto.costo_medio_ponderato
            prodotto.giacenza_attuale = (prodotto.giacenza_attuale - payload.quantita).quantize(THREE_DP)
            # il costo medio ponderato non cambia in uno scarico

        movimento = MovimentoMagazzino(
            prodotto_id=payload.prodotto_id, data=payload.data, tipo=payload.tipo,
            quantita=payload.quantita, costo_unitario=costo_unitario, causale=payload.causale,
            commessa_id=payload.commessa_id,
        )
        self.db.add(movimento)
        await self.db.commit()
        await self.db.refresh(movimento)
        return self._movimento_to_out(movimento, prodotto)

    def _movimento_to_out(self, movimento: MovimentoMagazzino, prodotto: Prodotto) -> MovimentoMagazzinoOut:
        return MovimentoMagazzinoOut(
            id=movimento.id, prodotto_id=movimento.prodotto_id, prodotto_codice=prodotto.codice,
            prodotto_descrizione=prodotto.descrizione, data=movimento.data, tipo=movimento.tipo,
            quantita=movimento.quantita, costo_unitario=movimento.costo_unitario,
            importo=_q(movimento.quantita * movimento.costo_unitario, TWO_DP),
            causale=movimento.causale, commessa_id=movimento.commessa_id, created_at=movimento.created_at,
        )

    async def list_movimenti(self, prodotto_id: int | None = None, skip: int = 0, limit: int = 200) -> list[MovimentoMagazzinoOut]:
        stmt = select(MovimentoMagazzino).options(selectinload(MovimentoMagazzino.prodotto))
        if prodotto_id is not None:
            stmt = stmt.where(MovimentoMagazzino.prodotto_id == prodotto_id)
        stmt = stmt.order_by(MovimentoMagazzino.data.desc(), MovimentoMagazzino.id.desc()).offset(skip).limit(limit)
        movimenti = (await self.db.execute(stmt)).scalars().all()
        return [self._movimento_to_out(m, m.prodotto) for m in movimenti]

    async def get_giacenze(self, solo_sotto_scorta: bool = False) -> list[GiacenzaMagazzino]:
        prodotti = (await self.db.execute(select(Prodotto).where(Prodotto.attivo.is_(True)).order_by(Prodotto.codice))).scalars().all()
        risultato = []
        for p in prodotti:
            sotto_scorta = p.giacenza_attuale < p.scorta_minima
            if solo_sotto_scorta and not sotto_scorta:
                continue
            risultato.append(GiacenzaMagazzino(
                prodotto_id=p.id, codice=p.codice, descrizione=p.descrizione, tipo=p.tipo,
                unita_misura=p.unita_misura, giacenza_attuale=p.giacenza_attuale,
                costo_medio_ponderato=p.costo_medio_ponderato,
                valore_giacenza=_q(p.giacenza_attuale * p.costo_medio_ponderato, TWO_DP),
                scorta_minima=p.scorta_minima, sotto_scorta=sotto_scorta,
            ))
        return risultato

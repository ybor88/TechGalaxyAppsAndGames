from decimal import Decimal, ROUND_HALF_UP
from datetime import date

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.commesse import Commessa, RigaCostoCommessa
from app.models.prodotti import Prodotto, DistintaBaseRiga
from app.models.centri_costo import CentroCosto
from app.models.fatturazione import Anagrafica
from app.schemas.commesse import (
    CommessaCreate, CommessaUpdate, CommessaOut, ChiudiCommessaRequest,
    RigaCostoCommessaCreate, RigaCostoCommessaOut,
    RiepilogoCostiCommessa, ScostamentoVoce, ScostamentiCommessaResponse,
)
from app.schemas.prodotti import MovimentoMagazzinoCreate
from app.services.prodotti import ProdottiService

TWO_DP = Decimal("0.01")


def _q(val: Decimal) -> Decimal:
    return val.quantize(TWO_DP, rounding=ROUND_HALF_UP)


class CommesseService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Commesse ─────────────────────────────────────────────────────────────

    async def crea_commessa(self, payload: CommessaCreate) -> CommessaOut:
        if await self.db.get(Prodotto, payload.prodotto_id) is None:
            raise ValueError("Prodotto non trovato")
        if await self.db.get(CentroCosto, payload.centro_costo_id) is None:
            raise ValueError("Centro di costo non trovato")
        if payload.anagrafica_id is not None and await self.db.get(Anagrafica, payload.anagrafica_id) is None:
            raise ValueError("Cliente non trovato")
        commessa = Commessa(stato="aperta", **payload.model_dump())
        self.db.add(commessa)
        await self.db.commit()
        await self.db.refresh(commessa)
        return await self._to_out(commessa)

    async def list_commesse(self, stato: str | None = None) -> list[CommessaOut]:
        stmt = select(Commessa)
        if stato is not None:
            stmt = stmt.where(Commessa.stato == stato)
        stmt = stmt.order_by(Commessa.data_apertura.desc())
        commesse = (await self.db.execute(stmt)).scalars().all()
        return [await self._to_out(c) for c in commesse]

    async def get_commessa(self, commessa_id: int) -> CommessaOut | None:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            return None
        return await self._to_out(commessa)

    async def aggiorna_commessa(self, commessa_id: int, payload: CommessaUpdate) -> CommessaOut | None:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            return None
        for campo, valore in payload.model_dump(exclude_unset=True).items():
            setattr(commessa, campo, valore)
        await self.db.commit()
        await self.db.refresh(commessa)
        return await self._to_out(commessa)

    async def chiudi_commessa(self, commessa_id: int, payload: ChiudiCommessaRequest) -> CommessaOut | None:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            return None
        if commessa.stato == "chiusa":
            raise ValueError("La commessa è già chiusa")
        if payload.data_chiusura < commessa.data_apertura:
            raise ValueError("La data di chiusura non può precedere la data di apertura")
        commessa.stato = "chiusa"
        commessa.data_chiusura = payload.data_chiusura
        commessa.quantita_prodotta = payload.quantita_prodotta
        await self.db.commit()
        await self.db.refresh(commessa)
        return await self._to_out(commessa)

    async def elimina_commessa(self, commessa_id: int) -> bool:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            return False
        await self.db.delete(commessa)
        await self.db.commit()
        return True

    async def _costo_totale(self, commessa_id: int) -> Decimal:
        righe = (await self.db.execute(
            select(RigaCostoCommessa).where(RigaCostoCommessa.commessa_id == commessa_id)
        )).scalars().all()
        return _q(sum((r.importo for r in righe), Decimal("0")))

    async def _to_out(self, commessa: Commessa) -> CommessaOut:
        prodotto = await self.db.get(Prodotto, commessa.prodotto_id)
        centro = await self.db.get(CentroCosto, commessa.centro_costo_id)
        anagrafica = await self.db.get(Anagrafica, commessa.anagrafica_id) if commessa.anagrafica_id else None
        return CommessaOut(
            id=commessa.id, codice=commessa.codice, descrizione=commessa.descrizione,
            prodotto_id=commessa.prodotto_id, prodotto_codice=prodotto.codice, prodotto_descrizione=prodotto.descrizione,
            centro_costo_id=commessa.centro_costo_id, centro_costo_descrizione=centro.descrizione,
            anagrafica_id=commessa.anagrafica_id, anagrafica_nome=anagrafica.nome if anagrafica else None,
            quantita_pianificata=commessa.quantita_pianificata, quantita_prodotta=commessa.quantita_prodotta,
            data_apertura=commessa.data_apertura, data_chiusura=commessa.data_chiusura, stato=commessa.stato,
            costo_totale_consuntivo=await self._costo_totale(commessa.id),
            note=commessa.note, created_at=commessa.created_at,
        )

    # ── Righe di costo ───────────────────────────────────────────────────────

    async def aggiungi_riga_costo(self, commessa_id: int, payload: RigaCostoCommessaCreate) -> RigaCostoCommessaOut:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            raise ValueError("Commessa non trovata")
        if commessa.stato == "chiusa":
            raise ValueError("Impossibile aggiungere costi a una commessa chiusa")

        prodotto = None
        costo_unitario = payload.costo_unitario
        importo = payload.importo

        if payload.tipo == "materiale":
            if payload.prodotto_id is None or payload.quantita is None or payload.quantita <= 0:
                raise ValueError("Per un costo materiale sono obbligatori prodotto e quantità")
            prodotto = await self.db.get(Prodotto, payload.prodotto_id)
            if prodotto is None:
                raise ValueError("Prodotto/materiale non trovato")
            # Scarica il magazzino e valorizza al costo medio ponderato corrente (garantisce coerenza col modulo Magazzino).
            movimento = await ProdottiService(self.db).registra_movimento(MovimentoMagazzinoCreate(
                prodotto_id=payload.prodotto_id, data=payload.data, tipo="scarico",
                quantita=payload.quantita, causale=f"Consumo commessa {commessa.codice}: {payload.descrizione}",
                commessa_id=commessa_id,
            ))
            costo_unitario = movimento.costo_unitario
            importo = movimento.importo
        elif payload.tipo == "manodopera":
            if payload.quantita is None or payload.quantita <= 0 or payload.costo_unitario is None:
                raise ValueError("Per un costo manodopera sono obbligatori ore lavorate e costo orario")
            importo = _q(payload.quantita * payload.costo_unitario)
        else:  # indiretto
            if payload.importo is None or payload.importo <= 0:
                raise ValueError("Per un costo indiretto è obbligatorio l'importo")
            importo = payload.importo

        riga = RigaCostoCommessa(
            commessa_id=commessa_id, tipo=payload.tipo, descrizione=payload.descrizione, data=payload.data,
            prodotto_id=payload.prodotto_id, centro_costo_id=payload.centro_costo_id,
            quantita=payload.quantita, costo_unitario=costo_unitario, importo=importo,
        )
        self.db.add(riga)
        await self.db.commit()
        await self.db.refresh(riga)
        return await self._riga_to_out(riga)

    async def _riga_to_out(self, riga: RigaCostoCommessa) -> RigaCostoCommessaOut:
        prodotto = await self.db.get(Prodotto, riga.prodotto_id) if riga.prodotto_id else None
        centro = await self.db.get(CentroCosto, riga.centro_costo_id) if riga.centro_costo_id else None
        return RigaCostoCommessaOut(
            id=riga.id, commessa_id=riga.commessa_id, tipo=riga.tipo, descrizione=riga.descrizione, data=riga.data,
            prodotto_id=riga.prodotto_id, prodotto_descrizione=prodotto.descrizione if prodotto else None,
            centro_costo_id=riga.centro_costo_id, centro_costo_descrizione=centro.descrizione if centro else None,
            quantita=riga.quantita, costo_unitario=riga.costo_unitario, importo=riga.importo,
            created_at=riga.created_at,
        )

    async def list_righe_costo(self, commessa_id: int) -> list[RigaCostoCommessaOut]:
        stmt = select(RigaCostoCommessa).where(RigaCostoCommessa.commessa_id == commessa_id).order_by(RigaCostoCommessa.data)
        righe = (await self.db.execute(stmt)).scalars().all()
        return [await self._riga_to_out(r) for r in righe]

    async def elimina_riga_costo(self, riga_id: int) -> bool:
        riga = await self.db.get(RigaCostoCommessa, riga_id)
        if riga is None:
            return False
        if riga.tipo == "materiale":
            raise ValueError(
                "Impossibile eliminare un costo materiale: ha già generato uno scarico di magazzino. "
                "Registra un carico di rettifica se necessario."
            )
        await self.db.delete(riga)
        await self.db.commit()
        return True

    async def riepilogo_costi(self, commessa_id: int) -> RiepilogoCostiCommessa:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            raise ValueError("Commessa non trovata")
        righe = (await self.db.execute(
            select(RigaCostoCommessa).where(RigaCostoCommessa.commessa_id == commessa_id)
        )).scalars().all()
        totale_materiale = _q(sum((r.importo for r in righe if r.tipo == "materiale"), Decimal("0")))
        totale_manodopera = _q(sum((r.importo for r in righe if r.tipo == "manodopera"), Decimal("0")))
        totale_indiretti = _q(sum((r.importo for r in righe if r.tipo == "indiretto"), Decimal("0")))
        totale = _q(totale_materiale + totale_manodopera + totale_indiretti)
        quantita_rif = commessa.quantita_prodotta or commessa.quantita_pianificata
        costo_unitario = _q(totale / quantita_rif) if quantita_rif else None
        return RiepilogoCostiCommessa(
            commessa_id=commessa_id, totale_materiale=totale_materiale, totale_manodopera=totale_manodopera,
            totale_indiretti=totale_indiretti, totale=totale, costo_unitario=costo_unitario,
        )

    # ── Analisi degli scostamenti ────────────────────────────────────────────

    async def scostamenti(self, commessa_id: int) -> ScostamentiCommessaResponse:
        commessa = await self.db.get(Commessa, commessa_id)
        if commessa is None:
            raise ValueError("Commessa non trovata")
        prodotto = await self.db.get(Prodotto, commessa.prodotto_id)
        righe = (await self.db.execute(
            select(RigaCostoCommessa).where(RigaCostoCommessa.commessa_id == commessa_id)
        )).scalars().all()

        if commessa.quantita_prodotta:
            quantita_rif = commessa.quantita_prodotta
            tipo_rif = "prodotta"
        else:
            quantita_rif = commessa.quantita_pianificata
            tipo_rif = "pianificata"

        voci: list[ScostamentoVoce] = []

        # ── Materiale: scostamento prezzo + quantità, aggregato su tutti i componenti di distinta base ──
        bom = (await self.db.execute(
            select(DistintaBaseRiga).options(selectinload(DistintaBaseRiga.componente))
            .where(DistintaBaseRiga.prodotto_padre_id == prodotto.id)
        )).scalars().all()
        righe_materiale = [r for r in righe if r.tipo == "materiale"]
        standard_materiale = Decimal("0")
        scostamento_prezzo_mat = Decimal("0")
        scostamento_quantita_mat = Decimal("0")
        prodotti_service = ProdottiService(self.db)
        for bom_riga in bom:
            standard_qty = bom_riga.quantita * quantita_rif
            standard_prezzo = await prodotti_service.costo_standard_totale(bom_riga.componente_id)
            standard_materiale += standard_qty * standard_prezzo
            righe_componente = [r for r in righe_materiale if r.prodotto_id == bom_riga.componente_id]
            actual_qty = sum((r.quantita or Decimal("0") for r in righe_componente), Decimal("0"))
            actual_costo = sum((r.importo for r in righe_componente), Decimal("0"))
            actual_prezzo = (actual_costo / actual_qty) if actual_qty > 0 else standard_prezzo
            # Convenzione: positivo = sfavorevole (costo maggiore dello standard), come per `scostamento`.
            scostamento_prezzo_mat += (actual_prezzo - standard_prezzo) * actual_qty
            scostamento_quantita_mat += (actual_qty - standard_qty) * standard_prezzo
        consuntivo_materiale = _q(sum((r.importo for r in righe_materiale), Decimal("0")))
        standard_materiale = _q(standard_materiale)
        voci.append(ScostamentoVoce(
            categoria="materiale", standard=standard_materiale, consuntivo=consuntivo_materiale,
            scostamento=_q(consuntivo_materiale - standard_materiale),
            scostamento_prezzo=_q(scostamento_prezzo_mat), scostamento_quantita=_q(scostamento_quantita_mat),
        ))

        # ── Manodopera: scostamento tariffa (prezzo) + efficienza (quantità) ──
        righe_manodopera = [r for r in righe if r.tipo == "manodopera"]
        standard_ore = prodotto.ore_manodopera_standard * quantita_rif
        standard_tariffa = prodotto.costo_orario_manodopera_standard
        standard_manodopera = _q(standard_ore * standard_tariffa)
        actual_ore = sum((r.quantita or Decimal("0") for r in righe_manodopera), Decimal("0"))
        consuntivo_manodopera = _q(sum((r.importo for r in righe_manodopera), Decimal("0")))
        actual_tariffa = (consuntivo_manodopera / actual_ore) if actual_ore > 0 else standard_tariffa
        scostamento_tariffa = _q((actual_tariffa - standard_tariffa) * actual_ore)
        scostamento_efficienza = _q((actual_ore - standard_ore) * standard_tariffa)
        voci.append(ScostamentoVoce(
            categoria="manodopera", standard=standard_manodopera, consuntivo=consuntivo_manodopera,
            scostamento=_q(consuntivo_manodopera - standard_manodopera),
            scostamento_prezzo=scostamento_tariffa, scostamento_quantita=scostamento_efficienza,
        ))

        # ── Indiretti: scostamento complessivo (spesa + volume, non scomposto) ──
        standard_indiretti = _q(prodotto.costo_indiretto_standard_unitario * quantita_rif)
        consuntivo_indiretti = _q(sum((r.importo for r in righe if r.tipo == "indiretto"), Decimal("0")))
        voci.append(ScostamentoVoce(
            categoria="indiretti", standard=standard_indiretti, consuntivo=consuntivo_indiretti,
            scostamento=_q(consuntivo_indiretti - standard_indiretti),
        ))

        scostamento_totale = _q(sum((v.scostamento for v in voci), Decimal("0")))
        return ScostamentiCommessaResponse(
            commessa_id=commessa_id, codice=commessa.codice, quantita_riferimento=quantita_rif,
            quantita_riferimento_tipo=tipo_rif, voci=voci, scostamento_totale=scostamento_totale,
        )

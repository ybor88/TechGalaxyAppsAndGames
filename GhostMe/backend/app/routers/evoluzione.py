"""
Versioni del profilo (F04), timeline della personalità (F10), diario di evoluzione (F06)
e sfide di auto-riflessione (F09).

© Roberto Di Flumeri
"""

from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import (
    ChallengeResponse, DecisionSession, JournalEntry, ProfileSnapshot, TraitChange, TraitStatement, User,
)
from app.routers.comune import dichiarazione_dict, registra_modifica, risorsa_utente, traccia
from app.schemas import (
    DiarioIn, DiarioOut, RispostaSfidaIn, SimulazioneIn, SnapshotDettaglio, SnapshotIn, SnapshotOut,
)
from app.security import richiede_consenso
from app.services.catalogo import CATEGORIE, SFIDE
from app.services.dominio import applica_ipotesi, confronta_versioni, impronta, riepilogo_profilo

router = APIRouter(prefix="/v1", tags=["Evoluzione"])
profilo_ok = richiede_consenso("trattamento_profilo")


async def _dati_attuali(db: AsyncSession, user_id: str) -> list[dict]:
    rows = (await db.execute(select(TraitStatement).where(TraitStatement.user_id == user_id))).scalars()
    return [dichiarazione_dict(s) | {"usa_in_ai": s.usa_in_ai} for s in rows]


async def _prossima_versione(db: AsyncSession, user_id: str) -> int:
    n = (await db.execute(select(func.max(ProfileSnapshot.versione)).where(ProfileSnapshot.user_id == user_id))).scalar()
    return (n or 0) + 1


# ── Versioni ──────────────────────────────────────────────────────────────────

@router.post("/snapshots", response_model=SnapshotOut, status_code=status.HTTP_201_CREATED)
async def crea_snapshot(dati: SnapshotIn, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    attuali = await _dati_attuali(db, user.id)
    if not attuali:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Il profilo è vuoto: aggiungi qualche dichiarazione prima")
    oggi = date.today()
    s = ProfileSnapshot(
        user_id=user.id, versione=await _prossima_versione(db, user.id), tipo="reale",
        etichetta=dati.etichetta or f"Il mio profilo al {oggi:%d/%m/%Y}", data_riferimento=oggi,
        riepilogo=riepilogo_profilo(attuali), dati=attuali, impronta=impronta(attuali),
    )
    db.add(s)
    await traccia(db, user, "snapshot_creato")
    await db.commit()
    return s


@router.post("/snapshots/simulazione", response_model=SnapshotOut, status_code=status.HTTP_201_CREATED)
async def crea_simulazione(dati: SimulazioneIn, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    attuali = await _dati_attuali(db, user.id)
    ids = {d["id"] for d in attuali}
    for ip in dati.ipotesi:
        if ip.azione in ("modifica", "rimuovi") and ip.id not in ids:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Un'ipotesi fa riferimento a una dichiarazione inesistente")
    simulati = applica_ipotesi(attuali, [ip.model_dump() for ip in dati.ipotesi])
    futuro = date.today() + timedelta(days=round(dati.mesi * 30.44))
    s = ProfileSnapshot(
        user_id=user.id, versione=await _prossima_versione(db, user.id), tipo="simulazione_futura",
        etichetta=f"SIMULAZIONE · {dati.etichetta or f'Io tra {dati.mesi} mesi'}", data_riferimento=futuro,
        riepilogo="Versione ipotetica costruita dalle tue ipotesi, non una previsione. " + riepilogo_profilo(simulati),
        dati=simulati, impronta=impronta(simulati),
    )
    db.add(s)
    await traccia(db, user, "simulazione_futura")
    await db.commit()
    return s


@router.get("/snapshots", response_model=list[SnapshotOut])
async def elenco_snapshot(user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    return (await db.execute(
        select(ProfileSnapshot).where(ProfileSnapshot.user_id == user.id).order_by(ProfileSnapshot.versione.desc())
    )).scalars().all()


@router.get("/snapshots/confronto")
async def confronta(da: str = Query(...), a: str = Query("attuale"), user: User = Depends(profilo_ok),
                    db: AsyncSession = Depends(get_db)):
    async def carica(rif: str) -> tuple[list[dict], str]:
        if rif == "attuale":
            return await _dati_attuali(db, user.id), "Profilo attuale"
        s = await risorsa_utente(db, ProfileSnapshot, rif, user)
        return s.dati, s.etichetta

    dati_a, etichetta_a = await carica(da)
    dati_b, etichetta_b = await carica(a)
    return {"da": etichetta_a, "a": etichetta_b, **confronta_versioni(dati_a, dati_b)}


@router.get("/snapshots/{snapshot_id}", response_model=SnapshotDettaglio)
async def dettaglio_snapshot(snapshot_id: str, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, ProfileSnapshot, snapshot_id, user)
    out = SnapshotDettaglio.model_validate(s)
    out.integro = impronta(s.dati) == s.impronta  # verifica d'integrità della versione immutabile
    return out


@router.delete("/snapshots/{snapshot_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_snapshot(snapshot_id: str, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, ProfileSnapshot, snapshot_id, user)
    await db.delete(s)
    await db.commit()


# ── Timeline ──────────────────────────────────────────────────────────────────

@router.get("/timeline")
async def timeline(user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    eventi = []
    for c in (await db.execute(select(TraitChange).where(TraitChange.user_id == user.id))).scalars():
        rif = c.dopo or c.prima or {}
        eventi.append({"tipo": "dichiarazione", "azione": c.azione, "data": c.timestamp,
                       "categoria": c.categoria, "testo": rif.get("testo"), "fonte": rif.get("fonte"),
                       "prima": (c.prima or {}).get("testo") if c.azione == "modificata" else None})
    snapshots = (await db.execute(
        select(ProfileSnapshot).where(ProfileSnapshot.user_id == user.id).order_by(ProfileSnapshot.data_riferimento)
    )).scalars().all()
    for s in snapshots:
        eventi.append({"tipo": "versione", "azione": s.tipo, "data": s.created_at, "testo": s.etichetta,
                       "snapshot_id": s.id})
    for j in (await db.execute(select(JournalEntry).where(JournalEntry.user_id == user.id))).scalars():
        eventi.append({"tipo": "diario", "azione": j.tipo, "data": j.created_at, "testo": j.testo[:140]})
    for d in (await db.execute(select(DecisionSession).where(DecisionSession.user_id == user.id))).scalars():
        if d.scelta_finale:
            eventi.append({"tipo": "decisione", "azione": d.stato, "data": d.updated_at,
                           "testo": f"{d.domanda} → {d.scelta_finale}", "esito": d.esito})
    eventi.sort(key=lambda e: e["data"], reverse=True)

    # numero di dichiarazioni per categoria in ogni versione: mostra variazioni, non punteggi
    serie = []
    for s in snapshots:
        punto = {"etichetta": s.etichetta, "data": s.data_riferimento, "tipo": s.tipo}
        for cat in CATEGORIE:
            punto[cat] = sum(1 for d in s.dati if d["categoria"] == cat)
        serie.append(punto)
    attuali = await _dati_attuali(db, user.id)
    serie.append({"etichetta": "Oggi", "data": date.today(), "tipo": "attuale",
                  **{cat: sum(1 for d in attuali if d["categoria"] == cat) for cat in CATEGORIE}})
    return {"eventi": eventi[:300], "serie": serie, "categorie": CATEGORIE,
            "nota": "La timeline mostra cosa hai dichiarato e quando: non attribuisce diagnosi né punteggi psicologici."}


# ── Diario ────────────────────────────────────────────────────────────────────

@router.get("/journal", response_model=list[DiarioOut])
async def elenco_diario(user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    return (await db.execute(
        select(JournalEntry).where(JournalEntry.user_id == user.id).order_by(JournalEntry.created_at.desc())
    )).scalars().all()


@router.post("/journal", response_model=DiarioOut, status_code=status.HTTP_201_CREATED)
async def nuova_voce(dati: DiarioIn, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    voce = JournalEntry(user_id=user.id, **dati.model_dump())
    db.add(voce)
    await traccia(db, user, "diario_" + dati.tipo)
    await db.commit()
    return voce


@router.delete("/journal/{voce_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_voce(voce_id: str, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    voce = await risorsa_utente(db, JournalEntry, voce_id, user)
    await db.delete(voce)
    await db.commit()


# ── Sfide ─────────────────────────────────────────────────────────────────────

@router.get("/challenges")
async def elenco_sfide(user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    risposte = (await db.execute(
        select(ChallengeResponse).where(ChallengeResponse.user_id == user.id).order_by(ChallengeResponse.created_at.desc())
    )).scalars().all()
    per_sfida: dict[str, list] = {}
    for r in risposte:
        per_sfida.setdefault(r.sfida_id, []).append(
            {"id": r.id, "risposta": r.risposta, "statement_id": r.statement_id, "created_at": r.created_at})
    return [{**s, "risposte": per_sfida.get(s["id"], [])} for s in SFIDE]


@router.post("/challenges/{sfida_id}/risposte", status_code=status.HTTP_201_CREATED)
async def rispondi_sfida(sfida_id: str, dati: RispostaSfidaIn, user: User = Depends(profilo_ok),
                         db: AsyncSession = Depends(get_db)):
    sfida = next((s for s in SFIDE if s["id"] == sfida_id), None)
    if sfida is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Sfida non trovata")
    risposta = ChallengeResponse(user_id=user.id, sfida_id=sfida_id, risposta=dati.risposta.strip())
    if dati.salva_come_dichiarazione:
        # il titolo della sfida resta come contesto: "Il no più facile: a un lavoro sempre reperibile"
        st = TraitStatement(user_id=user.id, testo=f"{sfida['titolo']}: {dati.risposta.strip()}", categoria=dati.categoria or sfida["categoria"],
                            peso=3, fonte="sfida", valido_da=date.today())
        db.add(st)
        await db.flush()
        registra_modifica(db, user.id, st, "creata", None)
        risposta.statement_id = st.id
    db.add(risposta)
    await traccia(db, user, "sfida_completata")
    await db.commit()
    return {"id": risposta.id, "statement_id": risposta.statement_id}

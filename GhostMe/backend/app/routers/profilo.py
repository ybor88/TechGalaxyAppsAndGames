"""Consensi (§8), profilo dichiarato (F01) e knowledge base manuale. © Roberto Di Flumeri"""

import hashlib
from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import delete, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import AnalyticsEvent, CompatibilityInvite, Consent, KnowledgeItem, ShareGrant, TraitStatement, User, adesso
from app.routers.comune import dichiarazione_dict, registra_modifica, risorsa_utente, traccia
from app.schemas import (
    ConoscenzaIn, ConoscenzaOut, ConoscenzaUpdate, ConsensoIn, DichiarazioneIn, DichiarazioneOut,
    DichiarazioneUpdate, QuestionarioIn,
)
from app.security import FINALITA, audit, consenso_attivo, registra_consenso, richiede_consenso, utente_corrente
from app.services.catalogo import CATEGORIE, QUESTIONARIO
from app.services.sicurezza_contenuti import sanifica_fonte

router = APIRouter(prefix="/v1", tags=["Profilo"])
profilo_ok = richiede_consenso("trattamento_profilo")


# ── Consensi ──────────────────────────────────────────────────────────────────

@router.get("/consents")
async def elenco_consensi(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    storico = (await db.execute(
        select(Consent).where(Consent.user_id == user.id).order_by(Consent.timestamp.desc())
    )).scalars().all()
    stato = []
    for finalita, descrizione in FINALITA.items():
        stato.append({
            "finalita": finalita, "descrizione": descrizione,
            "obbligatorio": finalita == "trattamento_profilo",
            "concesso": await consenso_attivo(db, user.id, finalita),
        })
    return {
        "stato": stato,
        "registro": [{"finalita": c.finalita, "concesso": c.concesso, "versione": c.versione,
                      "timestamp": c.timestamp, "revocato_at": c.revocato_at} for c in storico],
    }


@router.put("/consents/{finalita}")
async def imposta_consenso(finalita: str, dati: ConsensoIn, user: User = Depends(utente_corrente),
                           db: AsyncSession = Depends(get_db)):
    if finalita not in FINALITA:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Finalità sconosciuta")
    if finalita == "trattamento_profilo" and not dati.concesso:
        raise HTTPException(status.HTTP_409_CONFLICT,
                            "Questo consenso è necessario per usare l'app: per revocarlo cancella l'account.")
    await registra_consenso(db, user.id, finalita, dati.concesso)
    if not dati.concesso and finalita == "condivisione":
        # revocare la condivisione disattiva subito link e inviti attivi
        await db.execute(update(ShareGrant).where(ShareGrant.user_id == user.id, ShareGrant.revocato_at.is_(None))
                         .values(revocato_at=adesso()))
        await db.execute(delete(CompatibilityInvite).where(CompatibilityInvite.proprietario_id == user.id,
                                                           CompatibilityInvite.accettato_da.is_(None)))
    if not dati.concesso and finalita == "analytics":
        # revoca analytics: si cancellano anche gli eventi già raccolti
        pseudo = hashlib.sha256(f"ghostme:{user.id}".encode()).hexdigest()
        await db.execute(delete(AnalyticsEvent).where(AnalyticsEvent.utente_pseudo == pseudo))
    audit(db, user.id, "consenso_" + ("concesso" if dati.concesso else "revocato"), finalita)
    await db.commit()
    return {"finalita": finalita, "concesso": dati.concesso}


# ── Questionario ──────────────────────────────────────────────────────────────

@router.get("/profile/questionnaire")
async def questionario(user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    risposte = {s.chiave_domanda: s for s in (await db.execute(
        select(TraitStatement).where(TraitStatement.user_id == user.id, TraitStatement.chiave_domanda.is_not(None))
    )).scalars()}
    return {
        "categorie": CATEGORIE,
        "domande": [
            {**q, "risposta": dichiarazione_dict(risposte[q["chiave"]]) if q["chiave"] in risposte else None}
            for q in QUESTIONARIO
        ],
    }


@router.post("/profile/questionnaire")
async def salva_questionario(dati: QuestionarioIn, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    domande = {q["chiave"]: q for q in QUESTIONARIO}
    esistenti = {s.chiave_domanda: s for s in (await db.execute(
        select(TraitStatement).where(TraitStatement.user_id == user.id, TraitStatement.chiave_domanda.is_not(None))
    )).scalars()}
    salvate = 0
    for r in dati.risposte:
        q = domande.get(r.chiave)
        if q is None:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, f"Domanda sconosciuta: {r.chiave}")
        s = esistenti.get(r.chiave)
        if s is None:
            s = TraitStatement(user_id=user.id, testo=r.testo.strip(), categoria=q["categoria"], peso=r.peso,
                               fonte="questionario", chiave_domanda=r.chiave, visibilita=r.visibilita,
                               valido_da=date.today())
            db.add(s)
            await db.flush()
            registra_modifica(db, user.id, s, "creata", None)
        elif (s.testo, s.peso, s.visibilita) != (r.testo.strip(), r.peso, r.visibilita):
            prima = dichiarazione_dict(s)
            s.testo, s.peso, s.visibilita, s.updated_at = r.testo.strip(), r.peso, r.visibilita, adesso()
            registra_modifica(db, user.id, s, "modificata", prima)
        salvate += 1
    user.onboarding_completato = True
    await traccia(db, user, "questionario_salvato")
    await db.commit()
    return {"salvate": salvate}


# ── Dichiarazioni ────────────────────────────────────────────────────────────

@router.get("/profile/statements", response_model=list[DichiarazioneOut])
async def elenco_dichiarazioni(categoria: str | None = Query(None), user: User = Depends(profilo_ok),
                               db: AsyncSession = Depends(get_db)):
    q = select(TraitStatement).where(TraitStatement.user_id == user.id)
    if categoria:
        q = q.where(TraitStatement.categoria == categoria)
    return (await db.execute(q.order_by(TraitStatement.categoria, TraitStatement.peso.desc()))).scalars().all()


@router.post("/profile/statements", response_model=DichiarazioneOut, status_code=status.HTTP_201_CREATED)
async def nuova_dichiarazione(dati: DichiarazioneIn, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    s = TraitStatement(user_id=user.id, fonte="manuale", **dati.model_dump())
    s.testo = s.testo.strip()
    if s.valido_da is None:
        s.valido_da = date.today()
    db.add(s)
    await db.flush()
    registra_modifica(db, user.id, s, "creata", None)
    await traccia(db, user, "dichiarazione_creata")
    await db.commit()
    return s


@router.patch("/profile/statements/{statement_id}", response_model=DichiarazioneOut)
async def modifica_dichiarazione(statement_id: str, dati: DichiarazioneUpdate, user: User = Depends(profilo_ok),
                                 db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, TraitStatement, statement_id, user)
    prima = dichiarazione_dict(s)
    for campo, valore in dati.model_dump(exclude_unset=True).items():
        if valore is not None or campo in ("valido_a",):
            setattr(s, campo, valore.strip() if isinstance(valore, str) else valore)
    if s.valido_da and s.valido_a and s.valido_a < s.valido_da:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "La data di fine validità precede quella di inizio")
    s.updated_at = adesso()
    registra_modifica(db, user.id, s, "modificata", prima)
    await db.commit()
    return s


@router.delete("/profile/statements/{statement_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_dichiarazione(statement_id: str, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, TraitStatement, statement_id, user)
    registra_modifica(db, user.id, s, "eliminata", dichiarazione_dict(s))
    await db.delete(s)
    await db.commit()


# ── Knowledge base ───────────────────────────────────────────────────────────

@router.get("/knowledge", response_model=list[ConoscenzaOut])
async def elenco_conoscenze(user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    return (await db.execute(
        select(KnowledgeItem).where(KnowledgeItem.user_id == user.id).order_by(KnowledgeItem.created_at.desc())
    )).scalars().all()


@router.post("/knowledge", response_model=ConoscenzaOut, status_code=status.HTTP_201_CREATED)
async def nuova_conoscenza(dati: ConoscenzaIn, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    _, sospetta = sanifica_fonte(dati.contenuto + "\n" + dati.titolo)
    k = KnowledgeItem(user_id=user.id, sospetta_injection=sospetta, indice_ref="lessicale-v1", **dati.model_dump())
    db.add(k)
    await traccia(db, user, "conoscenza_creata")
    await db.commit()
    return k


@router.patch("/knowledge/{item_id}", response_model=ConoscenzaOut)
async def modifica_conoscenza(item_id: str, dati: ConoscenzaUpdate, user: User = Depends(profilo_ok),
                              db: AsyncSession = Depends(get_db)):
    k = await risorsa_utente(db, KnowledgeItem, item_id, user)
    for campo, valore in dati.model_dump(exclude_unset=True, exclude_none=True).items():
        setattr(k, campo, valore)
    _, k.sospetta_injection = sanifica_fonte(k.contenuto + "\n" + k.titolo)
    await db.commit()
    return k


@router.delete("/knowledge/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_conoscenza(item_id: str, user: User = Depends(profilo_ok), db: AsyncSession = Depends(get_db)):
    k = await risorsa_utente(db, KnowledgeItem, item_id, user)
    await db.delete(k)
    await db.commit()

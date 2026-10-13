"""Simulatore di decisioni (F03) e valutazione delle scelte (F07). © Roberto Di Flumeri"""

from datetime import date

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import DecisionSession, TraitStatement, User, adesso
from app.routers.comune import dichiarazione_dict, registra_modifica, risorsa_utente, traccia
from app.schemas import DecisioneIn, DecisioneOut, EsitoIn, ValutazioniIn
from app.security import richiede_consenso
from app.services.dominio import analizza_decisione, suggerisci_criteri

router = APIRouter(prefix="/v1/decisions", tags=["Decisioni"])
ai_ok = richiede_consenso("elaborazione_ai")


async def _dichiarazioni(db: AsyncSession, user_id: str) -> dict[str, dict]:
    rows = (await db.execute(
        select(TraitStatement).where(TraitStatement.user_id == user_id, TraitStatement.usa_in_ai.is_(True))
    )).scalars()
    return {s.id: dichiarazione_dict(s) for s in rows}


@router.get("/criteri-suggeriti")
async def criteri_suggeriti(user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    return suggerisci_criteri(list((await _dichiarazioni(db, user.id)).values()))


@router.get("", response_model=list[DecisioneOut])
async def elenco(user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    return (await db.execute(
        select(DecisionSession).where(DecisionSession.user_id == user.id).order_by(DecisionSession.created_at.desc())
    )).scalars().all()


@router.post("", response_model=DecisioneOut, status_code=status.HTTP_201_CREATED)
async def nuova(dati: DecisioneIn, user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    dich = await _dichiarazioni(db, user.id)
    criteri = [c.model_dump() for c in dati.criteri]
    for c in criteri:
        if c["statement_id"] and c["statement_id"] not in dich:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Un criterio fa riferimento a una dichiarazione non disponibile")
    opzioni = [o.model_dump() for o in dati.opzioni]
    _verifica_indici(dati.valutazioni, len(opzioni), len(criteri))
    risultato, complete = analizza_decisione(opzioni, criteri, dati.valutazioni, dich)
    sessione = DecisionSession(user_id=user.id, domanda=dati.domanda, opzioni=opzioni, criteri=criteri,
                               valutazioni=complete, risultato=risultato)
    db.add(sessione)
    await traccia(db, user, "decisione_simulata")
    await db.commit()
    return sessione


def _verifica_indici(valutazioni: dict, n_opzioni: int, n_criteri: int) -> None:
    for i, riga in valutazioni.items():
        if not i.isdigit() or int(i) >= n_opzioni or any(not j.isdigit() or int(j) >= n_criteri for j in riga):
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Valutazione riferita a opzione o criterio inesistente")


@router.get("/{decisione_id}", response_model=DecisioneOut)
async def dettaglio(decisione_id: str, user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    return await risorsa_utente(db, DecisionSession, decisione_id, user)


@router.put("/{decisione_id}/valutazioni", response_model=DecisioneOut)
async def aggiorna_valutazioni(decisione_id: str, dati: ValutazioniIn, user: User = Depends(ai_ok),
                               db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, DecisionSession, decisione_id, user)
    _verifica_indici(dati.valutazioni, len(s.opzioni), len(s.criteri))
    unite = {k: dict(v) for k, v in s.valutazioni.items()}
    stimate = {(c["opzione"], c["criterio"]) for c in s.risultato.get("celle_stimate", [])}
    # le celle stimate non ancora confermate vengono ricalcolate; quelle indicate dall'utente sostituiscono le stime
    for i, j in stimate:
        unite.get(str(i), {}).pop(str(j), None)
    for i, riga in dati.valutazioni.items():
        unite.setdefault(i, {}).update(riga)
    risultato, complete = analizza_decisione(s.opzioni, s.criteri, unite, await _dichiarazioni(db, user.id))
    s.valutazioni, s.risultato, s.updated_at = complete, risultato, adesso()
    await db.commit()
    return s


@router.put("/{decisione_id}/esito", response_model=DecisioneOut)
async def registra_esito(decisione_id: str, dati: EsitoIn, user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, DecisionSession, decisione_id, user)
    nomi = [o["nome"] for o in s.opzioni]
    if dati.scelta_finale not in nomi:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "La scelta finale deve essere una delle opzioni")
    s.scelta_finale = dati.scelta_finale
    s.esito = dati.esito
    s.apprendimento = dati.apprendimento
    s.stato = "valutata" if dati.esito else "decisa"
    s.updated_at = adesso()
    if dati.salva_come_dichiarazione and dati.apprendimento:
        # F07: l'apprendimento aggiorna il profilo, come dichiarazione esplicita e modificabile
        st = TraitStatement(user_id=user.id, testo=dati.apprendimento.strip(), categoria="valori", peso=3,
                            fonte="decisione", valido_da=date.today())
        db.add(st)
        await db.flush()
        registra_modifica(db, user.id, st, "creata", None)
    await traccia(db, user, "decisione_valutata")
    await db.commit()
    return s


@router.delete("/{decisione_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina(decisione_id: str, user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    s = await risorsa_utente(db, DecisionSession, decisione_id, user)
    await db.delete(s)
    await db.commit()

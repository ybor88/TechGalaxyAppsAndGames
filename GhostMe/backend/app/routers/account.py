"""Esportazione dati e cancellazione account self-service (§8, scenario 5). © Roberto Di Flumeri"""

import hashlib
import json
import secrets
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.models import (
    AnalyticsEvent, ChallengeResponse, CompatibilityInvite, Consent, Conversation, DecisionSession, DeletionReceipt,
    JournalEntry, KnowledgeItem, ProfileSnapshot, ShareGrant, TraitChange, TraitStatement, User, adesso, nuovo_id,
)
from app.schemas import CancellazioneIn
from app.security import audit, verifica_password, utente_corrente
from app.services.catalogo import CATEGORIE

router = APIRouter(prefix="/v1/account", tags=["Account"])


async def _tutti(db: AsyncSession, modello, user_id: str, campo: str = "user_id"):
    return (await db.execute(select(modello).where(getattr(modello, campo) == user_id))).scalars().all()


async def raccogli_dati(db: AsyncSession, user: User) -> dict:
    conv = (await db.execute(
        select(Conversation).options(selectinload(Conversation.messaggi)).where(Conversation.user_id == user.id)
    )).scalars().all()
    return {
        "formato": "ghostme-export/1",
        "esportato_il": datetime.now().isoformat(timespec="seconds"),
        "account": {"nome": user.nome_visualizzato, "creato_il": user.created_at.isoformat(), "lingua": user.locale,
                    "motore_ai": user.motore_ai, "conservazione_chat_giorni": user.conservazione_chat_giorni},
        "consensi": [{"finalita": c.finalita, "concesso": c.concesso, "versione": c.versione,
                      "timestamp": c.timestamp.isoformat(), "revocato_il": c.revocato_at.isoformat() if c.revocato_at else None}
                     for c in await _tutti(db, Consent, user.id)],
        "dichiarazioni": [{"testo": s.testo, "categoria": s.categoria, "peso": s.peso, "fonte": s.fonte,
                           "visibilita": s.visibilita, "usa_in_ai": s.usa_in_ai,
                           "valido_da": str(s.valido_da) if s.valido_da else None,
                           "valido_a": str(s.valido_a) if s.valido_a else None}
                          for s in await _tutti(db, TraitStatement, user.id)],
        "storico_modifiche": [{"azione": c.azione, "categoria": c.categoria, "prima": c.prima, "dopo": c.dopo,
                               "data": c.timestamp.isoformat()} for c in await _tutti(db, TraitChange, user.id)],
        "conoscenze": [{"titolo": k.titolo, "contenuto": k.contenuto, "tipo": k.tipo, "categoria": k.categoria,
                        "usa_in_ai": k.usa_in_ai} for k in await _tutti(db, KnowledgeItem, user.id)],
        "versioni": [{"versione": s.versione, "tipo": s.tipo, "etichetta": s.etichetta, "data": str(s.data_riferimento),
                      "riepilogo": s.riepilogo, "dati": s.dati, "impronta": s.impronta}
                     for s in await _tutti(db, ProfileSnapshot, user.id)],
        "conversazioni": [{"titolo": c.titolo, "creata_il": c.created_at.isoformat(),
                           "messaggi": [{"ruolo": m.ruolo, "testo": m.contenuto, "data": m.created_at.isoformat(),
                                         "motore": m.motore, "confidenza": m.confidenza} for m in c.messaggi]}
                          for c in conv],
        "decisioni": [{"domanda": d.domanda, "opzioni": d.opzioni, "criteri": d.criteri, "risultato": d.risultato,
                       "scelta_finale": d.scelta_finale, "esito": d.esito, "apprendimento": d.apprendimento}
                      for d in await _tutti(db, DecisionSession, user.id)],
        "diario": [{"tipo": j.tipo, "testo": j.testo, "energia": j.energia, "data": j.created_at.isoformat()}
                   for j in await _tutti(db, JournalEntry, user.id)],
        "sfide": [{"sfida": r.sfida_id, "risposta": r.risposta, "data": r.created_at.isoformat()}
                  for r in await _tutti(db, ChallengeResponse, user.id)],
        "condivisioni": [{"nome": g.nome, "categorie": g.categorie, "domande_usate": g.domande_usate,
                          "scadenza": g.scadenza.isoformat(), "revocata": g.revocato_at is not None}
                         for g in await _tutti(db, ShareGrant, user.id)],
    }


def in_markdown(dati: dict) -> str:
    righe = [f"# Esportazione Ghost Me — {dati['account']['nome'] or ''}", "",
             f"Esportato il {dati['esportato_il']}. Formato leggibile; il file JSON contiene gli stessi dati.", ""]
    righe += ["## Dichiarazioni", ""]
    for cat, etichetta in CATEGORIE.items():
        voci = [d for d in dati["dichiarazioni"] if d["categoria"] == cat]
        if voci:
            righe.append(f"### {etichetta}")
            righe += [f"- {d['testo']} _(importanza {d['peso']}/5, fonte: {d['fonte']})_" for d in voci]
            righe.append("")
    if dati["conoscenze"]:
        righe += ["## Conoscenze", ""] + [f"- **{k['titolo']}**: {k['contenuto']}" for k in dati["conoscenze"]] + [""]
    if dati["diario"]:
        righe += ["## Diario", ""] + [f"- {j['data'][:10]} ({j['tipo']}): {j['testo']}" for j in dati["diario"]] + [""]
    if dati["decisioni"]:
        righe += ["## Decisioni", ""]
        for d in dati["decisioni"]:
            righe.append(f"- {d['domanda']} → {d['scelta_finale'] or 'non ancora decisa'}"
                         + (f" (esito: {d['esito']})" if d["esito"] else ""))
        righe.append("")
    if dati["conversazioni"]:
        righe += ["## Conversazioni", ""]
        for c in dati["conversazioni"]:
            righe.append(f"### {c['titolo']}")
            righe += [f"- **{'Io' if m['ruolo'] == 'utente' else 'Alter ego'}**: {m['testo']}" for m in c["messaggi"]]
            righe.append("")
    righe += ["---", "Le risposte dell'alter ego sono contenuti generati, non una previsione né la volontà reale dell'utente.",
              "", "© Roberto Di Flumeri — Ghost Me"]
    return "\n".join(righe)


@router.get("/export")
async def esporta(formato: str = Query("json", pattern="^(json|markdown)$"), user: User = Depends(utente_corrente),
                  db: AsyncSession = Depends(get_db)):
    dati = await raccogli_dati(db, user)
    audit(db, user.id, "export_dati", formato)
    await db.commit()
    data = datetime.now().strftime("%Y%m%d")
    if formato == "markdown":
        return Response(in_markdown(dati), media_type="text/markdown; charset=utf-8",
                        headers={"Content-Disposition": f'attachment; filename="ghostme-{data}.md"'})
    return Response(json.dumps(dati, ensure_ascii=False, indent=2, default=str), media_type="application/json",
                    headers={"Content-Disposition": f'attachment; filename="ghostme-{data}.json"'})


@router.delete("")
async def cancella_account(dati: CancellazioneIn, response: Response, user: User = Depends(utente_corrente),
                           db: AsyncSession = Depends(get_db)):
    # ri-autenticazione obbligatoria
    if not verifica_password(dati.password, user.password_hash):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Password non corretta")
    user_id = user.id
    # inviti di compatibilità in cui l'utente è l'invitato: il risultato contiene suoi dati
    await db.execute(delete(CompatibilityInvite).where(CompatibilityInvite.accettato_da == user_id))
    pseudo = hashlib.sha256(f"ghostme:{user_id}".encode()).hexdigest()
    await db.execute(delete(AnalyticsEvent).where(AnalyticsEvent.utente_pseudo == pseudo))
    await db.delete(user)  # tutto il resto è eliminato a cascata dal database
    sale = secrets.token_hex(8)
    ricevuta = DeletionReceipt(id=nuovo_id(), timestamp=adesso(),
                               impronta=hashlib.sha256(f"{user_id}:{sale}".encode()).hexdigest())
    db.add(ricevuta)
    audit(db, None, "account_eliminato", ricevuta.id)
    await db.commit()
    response.delete_cookie("gm_refresh", path="/v1/auth")
    return {"ricevuta": ricevuta.id, "eliminato_il": ricevuta.timestamp,
            "messaggio": "Account e dati eliminati definitivamente. Conserva il numero di ricevuta."}

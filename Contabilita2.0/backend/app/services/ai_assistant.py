"""
Servizio AI Assistant Locale (F8).

Usa Ollama (Llama 3) tramite REST API per rispondere in linguaggio naturale.
Il contesto viene costruito direttamente da SQLite (entrate, uscite, conti, KPI).
La cronologia conversazione è persistita in SQLite (tabella ai_messaggi).

Dipendenze opzionali: langchain, langchain-community (per uso futuro di chain/agents).
"""
from __future__ import annotations

import uuid

from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.ai_assistant import MessaggioAI
from app.models.financial import Conto, Movimento
from app.schemas.ai_assistant import (
    CancellaResponse,
    ChatResponse,
    MessaggioResponse,
    StatusResponse,
)

# ── dipendenza opzionale LangChain ───────────────────────────────────────────
try:
    from langchain_community.llms import Ollama as _LangChainOllama  # noqa: F401
    _LANGCHAIN = True
except ImportError:
    _LANGCHAIN = False


# ════════════════════════════════════════════════════════════════════════════
class AIAssistantService:

    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Status ───────────────────────────────────────────────────────────────

    async def check_status(self) -> StatusResponse:
        """Verifica disponibilità e modello Ollama."""
        import httpx
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                r = await client.get(f"{settings.ollama_base_url}/api/tags")
            if r.status_code == 200:
                modelli = [m.get("name", "") for m in r.json().get("models", [])]
                has_model = any(settings.ollama_model in n for n in modelli)
                if has_model:
                    return StatusResponse(
                        ollama_disponibile=True,
                        modello=settings.ollama_model,
                        messaggio="Ollama operativo con modello disponibile.",
                    )
                return StatusResponse(
                    ollama_disponibile=True,
                    modello=settings.ollama_model,
                    messaggio=(
                        f"Ollama disponibile ma '{settings.ollama_model}' non trovato. "
                        f"Esegui: ollama pull {settings.ollama_model}"
                    ),
                )
        except Exception:
            pass
        return StatusResponse(
            ollama_disponibile=False,
            modello=settings.ollama_model,
            messaggio=(
                f"Ollama non raggiungibile. "
                f"Avvialo aprendo una finestra cmd ed eseguendo: ollama serve"
            ),
        )

    # ── Context building (RAG senza pgvector — query SQL dirette) ────────────

    async def _build_context(self, domanda: str) -> str:
        """Recupera dati pertinenti dal DB SQLite come contesto per il LLM."""
        domanda_lower = domanda.lower()
        parts: list[str] = []

        # KPI aggregati — query separate per compatibilità SQLite
        r_entrate = await self.db.execute(
            select(func.sum(Movimento.importo)).where(Movimento.tipo == "entrata")
        )
        r_uscite = await self.db.execute(
            select(func.sum(Movimento.importo)).where(Movimento.tipo == "uscita")
        )
        r_count = await self.db.execute(select(func.count(Movimento.id)))
        entrate = float(r_entrate.scalar() or 0)
        uscite = float(r_uscite.scalar() or 0)
        n_movimenti = r_count.scalar() or 0
        saldo = entrate - uscite
        liquidita = entrate / uscite if uscite > 0 else 0.0
        parts.append(
            "KPI FINANZIARI:\n"
            f"  Totale entrate : €{entrate:,.2f}\n"
            f"  Totale uscite  : €{uscite:,.2f}\n"
            f"  Saldo operativo: €{saldo:,.2f}\n"
            f"  Indice liquidità: {liquidita:.2f}\n"
            f"  Movimenti totali: {n_movimenti}"
        )

        # Ultimi 10 movimenti
        ultimi = (
            await self.db.execute(
                select(Movimento).order_by(Movimento.data.desc()).limit(10)
            )
        ).scalars().all()
        if ultimi:
            righe = "\n".join(
                f"  {m.data} | {m.tipo:7s} | €{float(m.importo):>10,.2f} | "
                f"{(m.categoria or 'N/A'):20s} | {m.descrizione}"
                for m in ultimi
            )
            parts.append(f"ULTIMI 10 MOVIMENTI:\n{righe}")

        # Piano dei conti (solo se la domanda riguarda conti/bilancio)
        if any(kw in domanda_lower for kw in ["conto", "conti", "piano", "bilancio", "saldo"]):
            conti = (
                await self.db.execute(select(Conto).order_by(Conto.codice))
            ).scalars().all()
            if conti:
                righe = "\n".join(
                    f"  {c.codice} | {c.descrizione:30s} | {c.tipo:10s} | €{float(c.saldo):,.2f}"
                    for c in conti
                )
                parts.append(f"PIANO DEI CONTI:\n{righe}")

        # Riepilogo per categoria (se la domanda riguarda categorie/spese/entrate)
        if any(kw in domanda_lower for kw in ["categoria", "spesa", "costo", "ricavo", "top", "più"]):
            cat_res = await self.db.execute(
                select(
                    Movimento.categoria,
                    Movimento.tipo,
                    func.sum(Movimento.importo).label("totale"),
                    func.count(Movimento.id).label("n"),
                )
                .where(Movimento.categoria.isnot(None))
                .group_by(Movimento.categoria, Movimento.tipo)
                .order_by(func.sum(Movimento.importo).desc())
                .limit(15)
            )
            cat_rows = cat_res.all()
            if cat_rows:
                righe = "\n".join(
                    f"  {r.categoria:25s} | {r.tipo:7s} | €{float(r.totale):>10,.2f} | {r.n} movimenti"
                    for r in cat_rows
                )
                parts.append(f"RIEPILOGO PER CATEGORIA:\n{righe}")

        return "\n\n".join(parts)

    # ── Chat ─────────────────────────────────────────────────────────────────

    async def chat(self, messaggio: str, sessione_id: str | None) -> ChatResponse:
        """Processa un messaggio e restituisce la risposta dell'assistente."""
        if not sessione_id:
            sessione_id = str(uuid.uuid4())

        try:
            # Salva messaggio utente
            self.db.add(MessaggioAI(sessione_id=sessione_id, ruolo="utente", contenuto=messaggio))
            await self.db.flush()

            # Cronologia sessione (ultimi 20 messaggi, escluso quello appena inserito)
            storia = (
                await self.db.execute(
                    select(MessaggioAI)
                    .where(MessaggioAI.sessione_id == sessione_id)
                    .order_by(MessaggioAI.created_at.desc())
                    .limit(21)
                )
            ).scalars().all()
            storia = list(reversed(storia))[:-1]  # ordine cronologico, escludi l'ultimo (utente corrente)

            # Recupera contesto DB
            contesto = await self._build_context(messaggio)

            # Prompt di sistema
            system_prompt = (
                "Sei un assistente amministrativo e contabile esperto per l'applicazione Contabilità 2.0. "
                "Rispondi SEMPRE in italiano, in modo preciso e conciso. "
                "Usa i dati finanziari nel contesto per rispondere con informazioni accurate e aggiornate. "
                "Se non hai dati sufficienti per rispondere, dichiaralo esplicitamente. "
                "Non inventare dati che non hai nel contesto.\n\n"
                f"=== DATI AZIENDALI ATTUALI ===\n{contesto}\n==========================="
            )

            # Costruisci conversazione precedente
            storia_txt = "\n".join(
                f"{'Utente' if m.ruolo == 'utente' else 'Assistente'}: {m.contenuto}"
                for m in storia
            )

            prompt = (
                f"{system_prompt}\n\n"
                f"{('Conversazione precedente:\n' + storia_txt + '\n\n') if storia_txt else ''}"
                f"Utente: {messaggio}\nAssistente:"
            )

            risposta = await self._chiedi_ollama(prompt)

            # Salva risposta
            self.db.add(MessaggioAI(sessione_id=sessione_id, ruolo="assistente", contenuto=risposta))
            await self.db.commit()

        except Exception as exc:
            await self.db.rollback()
            risposta = f"Errore interno durante l'elaborazione: {type(exc).__name__}: {exc}"

        return ChatResponse(risposta=risposta, sessione_id=sessione_id, model=settings.ollama_model)

    async def _chiedi_ollama(self, prompt: str) -> str:
        """Chiama Ollama REST API in modo asincrono."""
        import httpx
        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                r = await client.post(
                    f"{settings.ollama_base_url}/api/generate",
                    json={"model": settings.ollama_model, "prompt": prompt, "stream": False},
                )
            if r.status_code == 200:
                return r.json().get("response", "").strip()
            return f"Errore Ollama (HTTP {r.status_code}). Riprova o controlla il servizio."
        except Exception as exc:
            return (
                f"Ollama non è raggiungibile (avvialo con: ollama serve). "
                f"({type(exc).__name__})"
            )

    # ── Cronologia ───────────────────────────────────────────────────────────

    async def get_cronologia(self, sessione_id: str) -> list[MessaggioResponse]:
        risultati = (
            await self.db.execute(
                select(MessaggioAI)
                .where(MessaggioAI.sessione_id == sessione_id)
                .order_by(MessaggioAI.created_at)
            )
        ).scalars().all()
        return [MessaggioResponse.model_validate(m) for m in risultati]

    async def cancella_cronologia(self, sessione_id: str) -> CancellaResponse:
        result = await self.db.execute(
            delete(MessaggioAI).where(MessaggioAI.sessione_id == sessione_id)
        )
        await self.db.commit()
        return CancellaResponse(cancellati=result.rowcount)

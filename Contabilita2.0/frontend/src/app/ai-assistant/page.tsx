"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { aiAssistantApi, AIMessage, AIStatusResponse } from "@/lib/api";
import {
  Bot,
  ChevronLeft,
  SendHorizonal,
  Trash2,
  RefreshCw,
  CircleDot,
  CircleOff,
  User,
  Loader2,
} from "lucide-react";

// ── Helpers ───────────────────────────────────────────────────────────────────

function genSessionId(): string {
  return crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2);
}

const SUGGERIMENTI = [
  "Qual è il saldo operativo attuale?",
  "Mostrami le ultime 10 entrate.",
  "Qual è l'indice di liquidità?",
  "Quali sono le categorie di spesa principali?",
  "Dammi un riepilogo del bilancio.",
  "Quanti movimenti ci sono in totale?",
];

// ── Tipi ─────────────────────────────────────────────────────────────────────

interface Messaggio {
  ruolo: "utente" | "assistente";
  contenuto: string;
  loading?: boolean;
}

// ── Componente messaggio ──────────────────────────────────────────────────────

function BubbleMsg({ msg }: { msg: Messaggio }) {
  const isUtente = msg.ruolo === "utente";
  return (
    <div className={`flex gap-3 ${isUtente ? "flex-row-reverse" : "flex-row"} items-end`}>
      {/* Avatar */}
      <div
        className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-white text-sm ${
          isUtente ? "bg-indigo-500" : "bg-gray-700"
        }`}
      >
        {isUtente ? <User size={15} /> : <Bot size={15} />}
      </div>

      {/* Bubble */}
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm ${
          isUtente
            ? "bg-indigo-600 text-white rounded-br-sm"
            : "bg-white text-gray-800 border border-gray-100 rounded-bl-sm"
        }`}
      >
        {msg.loading ? (
          <span className="flex items-center gap-2 text-gray-400">
            <Loader2 size={14} className="animate-spin" />
            Sto elaborando…
          </span>
        ) : (
          <span className="whitespace-pre-wrap">{msg.contenuto}</span>
        )}
      </div>
    </div>
  );
}

// ── Pagina principale ─────────────────────────────────────────────────────────

export default function AIAssistantPage() {
  const [messaggi, setMessaggi] = useState<Messaggio[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [sessione, setSessione] = useState<string>(() => genSessionId());
  const [status, setStatus] = useState<AIStatusResponse | null>(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Scroll automatico verso il basso
  const scrollDown = useCallback(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollDown();
  }, [messaggi, scrollDown]);

  // Controlla status Ollama al mount
  useEffect(() => {
    aiAssistantApi
      .getStatus()
      .then((r) => setStatus(r.data))
      .catch(() =>
        setStatus({
          ollama_disponibile: false,
          modello: "llama3",
          messaggio: "Impossibile contattare il backend.",
        })
      )
      .finally(() => setStatusLoading(false));
  }, []);

  // Auto-resize textarea
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 140)}px`;
  }, [input]);

  const inviaMessaggio = useCallback(async () => {
    const testo = input.trim();
    if (!testo || loading) return;

    setInput("");
    setLoading(true);

    const nuoviMsg: Messaggio[] = [
      ...messaggi,
      { ruolo: "utente", contenuto: testo },
      { ruolo: "assistente", contenuto: "", loading: true },
    ];
    setMessaggi(nuoviMsg);

    try {
      const resp = await aiAssistantApi.chat(testo, sessione);
      const risposta = resp.data;
      // Aggiorna sessione se era la prima richiesta
      if (risposta.sessione_id !== sessione) setSessione(risposta.sessione_id);

      setMessaggi((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          ruolo: "assistente",
          contenuto: risposta.risposta,
        };
        return updated;
      });
    } catch {
      setMessaggi((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          ruolo: "assistente",
          contenuto: "Si è verificato un errore. Riprova.",
        };
        return updated;
      });
    } finally {
      setLoading(false);
      textareaRef.current?.focus();
    }
  }, [input, loading, messaggi, sessione]);

  const nuovaSessione = useCallback(() => {
    setMessaggi([]);
    setSessione(genSessionId());
    setInput("");
    textareaRef.current?.focus();
  }, []);

  const cancellaCronologia = useCallback(async () => {
    await aiAssistantApi.cancellaCronologia(sessione).catch(() => null);
    setMessaggi([]);
  }, [sessione]);

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      inviaMessaggio();
    }
  };

  const usaSuggerimento = (s: string) => {
    setInput(s);
    textareaRef.current?.focus();
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-[#f8fafc] flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-gray-100 shadow-sm sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 py-3 flex items-center gap-3">
          <Link href="/" className="text-gray-400 hover:text-indigo-600 transition-colors">
            <ChevronLeft size={22} />
          </Link>
          <div className="w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center">
            <Bot size={18} className="text-indigo-600" />
          </div>
          <div className="flex-1 min-w-0">
            <h1 className="text-sm font-semibold text-gray-800 leading-none">AI Assistant</h1>
            <p className="text-xs text-gray-400 mt-0.5">Interroga i dati in linguaggio naturale</p>
          </div>

          {/* Status badge */}
          {!statusLoading && status && (
            <span
              className={`flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full ${
                status.ollama_disponibile
                  ? "bg-green-50 text-green-700"
                  : "bg-red-50 text-red-600"
              }`}
              title={status.messaggio}
            >
              {status.ollama_disponibile ? (
                <CircleDot size={11} />
              ) : (
                <CircleOff size={11} />
              )}
              {status.ollama_disponibile ? status.modello : "Offline"}
            </span>
          )}

          {/* Azioni */}
          <button
            onClick={nuovaSessione}
            className="p-1.5 rounded-lg text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 transition-colors"
            title="Nuova conversazione"
          >
            <RefreshCw size={16} />
          </button>
          {messaggi.length > 0 && (
            <button
              onClick={cancellaCronologia}
              className="p-1.5 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 transition-colors"
              title="Cancella cronologia"
            >
              <Trash2 size={16} />
            </button>
          )}
        </div>
      </header>

      {/* Area messaggi */}
      <main className="flex-1 max-w-4xl w-full mx-auto px-4 py-6 flex flex-col gap-4 overflow-y-auto">
        {/* Banner Ollama offline */}
        {!statusLoading && status && !status.ollama_disponibile && (
          <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 text-sm text-amber-800">
            <strong>Ollama non disponibile.</strong> {status.messaggio}
          </div>
        )}

        {/* Schermata vuota */}
        {messaggi.length === 0 && (
          <div className="flex-1 flex flex-col items-center justify-center text-center gap-6 py-12">
            <div className="w-16 h-16 rounded-2xl bg-indigo-100 flex items-center justify-center">
              <Bot size={32} className="text-indigo-500" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-gray-700">Come posso aiutarti?</h2>
              <p className="text-sm text-gray-400 mt-1">
                Fai una domanda sui tuoi dati contabili in linguaggio naturale.
              </p>
            </div>
            {/* Suggerimenti rapidi */}
            <div className="flex flex-wrap gap-2 justify-center max-w-lg">
              {SUGGERIMENTI.map((s) => (
                <button
                  key={s}
                  onClick={() => usaSuggerimento(s)}
                  className="text-xs bg-white border border-gray-200 rounded-full px-3 py-1.5 text-gray-600 hover:border-indigo-400 hover:text-indigo-600 transition-colors shadow-sm"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Messaggi */}
        {messaggi.map((m, i) => (
          <BubbleMsg key={i} msg={m} />
        ))}
        <div ref={bottomRef} />
      </main>

      {/* Input */}
      <div className="bg-white border-t border-gray-100 shadow-sm sticky bottom-0">
        <div className="max-w-4xl mx-auto px-4 py-3">
          <div className="flex gap-2 items-end">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder="Scrivi una domanda… (Invio per inviare, Shift+Invio per andare a capo)"
              rows={1}
              className="flex-1 resize-none rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-800 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:border-transparent bg-gray-50 transition-all overflow-hidden"
              disabled={loading}
            />
            <button
              onClick={inviaMessaggio}
              disabled={!input.trim() || loading}
              className="flex-shrink-0 w-10 h-10 rounded-xl bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center text-white transition-colors shadow-sm"
            >
              {loading ? <Loader2 size={18} className="animate-spin" /> : <SendHorizonal size={18} />}
            </button>
          </div>
          <p className="text-[11px] text-gray-400 mt-1.5 text-center">
            Le risposte si basano sui dati del database locale. Il modello AI gira completamente in locale.
          </p>
        </div>
      </div>
    </div>
  );
}

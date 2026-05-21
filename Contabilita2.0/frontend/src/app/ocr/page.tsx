"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  ChevronLeft,
  ScanLine,
  Upload,
  Trash2,
  FileText,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Eye,
  X,
} from "lucide-react";
import { ocrApi, OcrRisultato, OcrElaboraResponse } from "@/lib/api";

// ── Helpers ──────────────────────────────────────────────────────────────────

const fmt = (n: number | string | null | undefined) =>
  n != null
    ? new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n))
    : "—";

const fmtData = (s: string | null | undefined) =>
  s ? new Date(s).toLocaleDateString("it-IT") : "—";

const STATO_STYLE: Record<string, { cls: string; label: string; icon: React.ReactNode }> = {
  elaborato: {
    cls: "bg-green-100 text-green-700",
    label: "Elaborato",
    icon: <CheckCircle2 className="w-3.5 h-3.5" />,
  },
  revisione: {
    cls: "bg-amber-100 text-amber-700",
    label: "Da rivedere",
    icon: <Clock className="w-3.5 h-3.5" />,
  },
  errore: {
    cls: "bg-red-100 text-red-700",
    label: "Errore",
    icon: <AlertTriangle className="w-3.5 h-3.5" />,
  },
};

// ── Componente badge stato ────────────────────────────────────────────────────

function StatoBadge({ stato }: { stato: string }) {
  const s = STATO_STYLE[stato] ?? STATO_STYLE.revisione;
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${s.cls}`}>
      {s.icon}
      {s.label}
    </span>
  );
}

// ── Card di dettaglio risultato ───────────────────────────────────────────────

function DettaglioOcr({
  r,
  onClose,
  onDelete,
}: {
  r: OcrRisultato;
  onClose: () => void;
  onDelete: (id: number) => void;
}) {
  const [showTesto, setShowTesto] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const handleDelete = async () => {
    if (!confirm("Eliminare questo risultato OCR?")) return;
    setDeleting(true);
    try {
      await ocrApi.delete(r.id);
      onDelete(r.id);
      onClose();
    } finally {
      setDeleting(false);
    }
  };

  const Field = ({ label, value }: { label: string; value: string }) => (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-slate-500">{label}</span>
      <span className="text-sm font-medium text-slate-800 break-all">{value}</span>
    </div>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <ScanLine className="w-5 h-5 text-teal-600" />
            <h2 className="font-semibold text-slate-800 truncate max-w-xs">{r.filename}</h2>
            <StatoBadge stato={r.stato} />
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-5">
          {/* Errore */}
          {r.errore && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-sm text-red-700">
              <span className="font-medium">Errore: </span>{r.errore}
            </div>
          )}

          {/* Dati estratti */}
          <div>
            <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">
              Dati estratti
            </h3>
            <div className="grid grid-cols-2 gap-4">
              <Field label="Fornitore" value={r.fornitore ?? "—"} />
              <Field label="Partita IVA" value={r.piva ?? "—"} />
              <Field label="Codice Fiscale" value={r.cf ?? "—"} />
              <Field label="Numero documento" value={r.numero_documento ?? "—"} />
              <Field label="Data documento" value={fmtData(r.data_documento)} />
              <Field label="Aliquota IVA" value={r.aliquota_iva != null ? `${r.aliquota_iva}%` : "—"} />
            </div>
          </div>

          {/* Importi */}
          <div>
            <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">
              Importi
            </h3>
            <div className="grid grid-cols-3 gap-3">
              {[
                { label: "Imponibile", value: fmt(r.importo_netto) },
                { label: "IVA", value: fmt(r.importo_iva) },
                { label: "Totale", value: fmt(r.importo_totale) },
              ].map(({ label, value }) => (
                <div key={label} className="bg-slate-50 rounded-xl p-3 text-center">
                  <div className="text-xs text-slate-500 mb-1">{label}</div>
                  <div className="font-semibold text-slate-800">{value}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Info */}
          <div className="text-xs text-slate-400 flex gap-4">
            <span>Elaborato: {fmtData(r.created_at)}</span>
            {r.documento_id && (
              <span className="text-teal-600 font-medium">Collegato al documento #{r.documento_id}</span>
            )}
          </div>

          {/* Testo grezzo */}
          {r.testo_estratto && (
            <div>
              <button
                onClick={() => setShowTesto((v) => !v)}
                className="text-xs text-teal-600 hover:underline font-medium"
              >
                {showTesto ? "Nascondi" : "Mostra"} testo estratto grezzo
              </button>
              {showTesto && (
                <pre className="mt-2 bg-slate-50 rounded-xl p-3 text-xs text-slate-700 whitespace-pre-wrap overflow-x-auto max-h-48 border border-slate-200">
                  {r.testo_estratto}
                </pre>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 p-5 border-t border-slate-100">
          <button
            onClick={handleDelete}
            disabled={deleting}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            <Trash2 className="w-4 h-4" />
            {deleting ? "Eliminazione…" : "Elimina"}
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm bg-slate-100 hover:bg-slate-200 text-slate-700"
          >
            Chiudi
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Zona di caricamento ───────────────────────────────────────────────────────

function DropZone({ onFile }: { onFile: (f: File) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const handle = (f: File | undefined) => { if (f) onFile(f); };

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    handle(e.dataTransfer.files[0]);
  }, []);

  return (
    <div
      onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
      onClick={() => inputRef.current?.click()}
      className={`cursor-pointer border-2 border-dashed rounded-2xl p-10 flex flex-col items-center gap-3 transition-colors
        ${dragging ? "border-teal-500 bg-teal-50" : "border-slate-200 hover:border-teal-400 hover:bg-slate-50"}`}
    >
      <Upload className={`w-10 h-10 ${dragging ? "text-teal-500" : "text-slate-400"}`} />
      <p className="text-sm font-medium text-slate-600">
        Trascina qui una fattura o <span className="text-teal-600">seleziona file</span>
      </p>
      <p className="text-xs text-slate-400">PDF, PNG, JPG, TIFF, BMP, WEBP — max 20 MB</p>
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept=".pdf,.png,.jpg,.jpeg,.tiff,.tif,.bmp,.webp"
        onChange={(e) => handle(e.target.files?.[0])}
      />
    </div>
  );
}

// ── Pagina principale ─────────────────────────────────────────────────────────

export default function OcrPage() {
  const [risultati, setRisultati] = useState<OcrRisultato[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [avvisi, setAvvisi] = useState<string[]>([]);
  const [erroreUpload, setErroreUpload] = useState<string | null>(null);
  const [selezionato, setSelezionato] = useState<OcrRisultato | null>(null);

  const caricaLista = useCallback(async () => {
    try {
      const { data } = await ocrApi.list();
      setRisultati(data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { caricaLista(); }, [caricaLista]);

  const handleFile = async (file: File) => {
    setUploading(true);
    setAvvisi([]);
    setErroreUpload(null);
    try {
      const { data }: { data: OcrElaboraResponse } = await ocrApi.elabora(file);
      setRisultati((prev) => [data.risultato, ...prev]);
      setAvvisi(data.avvisi);
      setSelezionato(data.risultato);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail ??
        "Errore durante l'elaborazione del file.";
      setErroreUpload(msg);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = (id: number) => {
    setRisultati((prev) => prev.filter((r) => r.id !== id));
  };

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      {/* Top bar */}
      <header className="bg-white border-b border-slate-200 px-6 py-4 flex items-center gap-3">
        <Link href="/" className="p-2 rounded-lg hover:bg-slate-100 text-slate-500">
          <ChevronLeft className="w-5 h-5" />
        </Link>
        <ScanLine className="w-5 h-5 text-teal-600" />
        <h1 className="text-lg font-semibold text-slate-800">OCR Contabile</h1>
        <span className="ml-2 px-2 py-0.5 rounded-full bg-teal-100 text-teal-700 text-xs font-medium">
          F3
        </span>
      </header>

      <main className="max-w-4xl mx-auto px-6 py-8 space-y-8">
        {/* Upload */}
        <section>
          <h2 className="text-sm font-semibold text-slate-700 mb-3">
            Carica fattura da analizzare
          </h2>

          {uploading ? (
            <div className="border-2 border-dashed border-teal-300 rounded-2xl p-10 flex flex-col items-center gap-3 bg-teal-50 animate-pulse">
              <ScanLine className="w-10 h-10 text-teal-500" />
              <p className="text-sm font-medium text-teal-700">Elaborazione OCR in corso…</p>
            </div>
          ) : (
            <DropZone onFile={handleFile} />
          )}

          {erroreUpload && (
            <div className="mt-3 bg-red-50 border border-red-200 rounded-xl p-3 flex items-start gap-2 text-sm text-red-700">
              <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
              {erroreUpload}
            </div>
          )}

          {avvisi.length > 0 && (
            <div className="mt-3 bg-amber-50 border border-amber-200 rounded-xl p-3 space-y-1">
              {avvisi.map((a, i) => (
                <p key={i} className="flex items-start gap-2 text-sm text-amber-700">
                  <Clock className="w-4 h-4 mt-0.5 shrink-0" />
                  {a}
                </p>
              ))}
            </div>
          )}
        </section>

        {/* Nota Tesseract */}
        <section className="bg-blue-50 border border-blue-200 rounded-xl p-4 text-sm text-blue-700">
          <p className="font-medium mb-1">Nota sull'installazione di Tesseract OCR</p>
          <p>
            Per l'OCR di immagini e PDF scansionati è necessario installare{" "}
            <strong>Tesseract</strong> sul sistema operativo.{" "}
            <a
              href="https://github.com/UB-Mannheim/tesseract/wiki"
              target="_blank"
              rel="noopener noreferrer"
              className="underline"
            >
              Scarica l'installer per Windows
            </a>{" "}
            e assicurati che sia nel PATH. I PDF digitali (non scansionati) funzionano senza
            Tesseract.
          </p>
        </section>

        {/* Storico risultati */}
        <section>
          <h2 className="text-sm font-semibold text-slate-700 mb-3">
            Risultati precedenti{" "}
            <span className="text-slate-400 font-normal">({risultati.length})</span>
          </h2>

          {loading ? (
            <div className="text-sm text-slate-400 text-center py-8">Caricamento…</div>
          ) : risultati.length === 0 ? (
            <div className="text-center py-12 text-slate-400">
              <FileText className="w-10 h-10 mx-auto mb-3 opacity-40" />
              <p className="text-sm">Nessuna fattura elaborata ancora.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {risultati.map((r) => (
                <div
                  key={r.id}
                  className="bg-white rounded-xl border border-slate-100 px-4 py-3 flex items-center gap-4 hover:shadow-sm transition-shadow"
                >
                  <FileText className="w-5 h-5 text-slate-400 shrink-0" />

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-medium text-slate-800 truncate max-w-[200px]">
                        {r.filename}
                      </span>
                      <StatoBadge stato={r.stato} />
                    </div>
                    <div className="text-xs text-slate-500 mt-0.5 flex gap-3 flex-wrap">
                      {r.fornitore && <span>Fornitore: <strong>{r.fornitore}</strong></span>}
                      {r.piva && <span>P.IVA: {r.piva}</span>}
                      {r.importo_totale != null && <span>Totale: {fmt(r.importo_totale)}</span>}
                      <span>{fmtData(r.created_at)}</span>
                    </div>
                  </div>

                  <button
                    onClick={() => setSelezionato(r)}
                    className="p-2 rounded-lg hover:bg-slate-100 text-slate-500 shrink-0"
                    title="Vedi dettagli"
                  >
                    <Eye className="w-4 h-4" />
                  </button>
                  <button
                    onClick={async () => {
                      if (!confirm("Eliminare questo risultato?")) return;
                      await ocrApi.delete(r.id);
                      handleDelete(r.id);
                    }}
                    className="p-2 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-500 shrink-0"
                    title="Elimina"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>

      {/* Modal dettaglio */}
      {selezionato && (
        <DettaglioOcr
          r={selezionato}
          onClose={() => setSelezionato(null)}
          onDelete={handleDelete}
        />
      )}
    </div>
  );
}

"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  ChevronLeft,
  Plus,
  Trash2,
  Wallet,
  AlertCircle,
  CheckCircle2,
  X,
  Calculator,
  Eye,
} from "lucide-react";
import { pagheApi, Dipendente, DipendenteCreate, Cedolino } from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const MESI = [
  "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
  "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
];

const emptyForm = (): DipendenteCreate => ({
  nome: "",
  cognome: "",
  data_assunzione: new Date().toISOString().split("T")[0],
  retribuzione_lorda_mensile: 0,
  numero_mensilita: 13,
  aliquota_inps_dipendente: 9.19,
  aliquota_inps_azienda: 30,
  detrazioni_attive: true,
});

export default function PaghePage() {
  const [dipendenti, setDipendenti] = useState<Dipendente[]>([]);
  const [selezionato, setSelezionato] = useState<Dipendente | null>(null);
  const [cedolini, setCedolini] = useState<Cedolino[]>([]);
  const [anteprima, setAnteprima] = useState<Cedolino | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DipendenteCreate>(emptyForm());
  const [annoSel, setAnnoSel] = useState(new Date().getFullYear());
  const [meseSel, setMeseSel] = useState(new Date().getMonth() + 1);
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);
  const [salvataggio, setSalvataggio] = useState(false);

  const carica = useCallback(async () => {
    try {
      const res = await pagheApi.listDipendenti();
      setDipendenti(res.data);
    } catch {
      setErrore("Impossibile caricare i dipendenti.");
    }
  }, []);

  useEffect(() => {
    carica();
  }, [carica]);

  const apriDettaglio = async (d: Dipendente) => {
    setSelezionato(d);
    setShowForm(false);
    setAnteprima(null);
    try {
      const res = await pagheApi.listCedolini(d.id);
      setCedolini(res.data);
    } catch {
      setErrore("Impossibile caricare i cedolini.");
    }
  };

  const submitDipendente = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.nome.trim() || !form.cognome.trim() || !form.retribuzione_lorda_mensile) {
      setErrore("Compila tutti i campi obbligatori.");
      return;
    }
    setSalvataggio(true);
    setErrore(null);
    try {
      await pagheApi.createDipendente(form);
      setSuccesso("Dipendente creato.");
      setShowForm(false);
      setForm(emptyForm());
      carica();
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Errore durante il salvataggio.");
    } finally {
      setSalvataggio(false);
    }
  };

  const eliminaDipendente = async (id: number) => {
    if (!confirm("Eliminare questo dipendente e tutti i suoi cedolini?")) return;
    try {
      await pagheApi.deleteDipendente(id);
      setSuccesso("Dipendente eliminato.");
      if (selezionato?.id === id) setSelezionato(null);
      carica();
    } catch {
      setErrore("Impossibile eliminare il dipendente.");
    }
  };

  const calcolaAnteprima = async () => {
    if (!selezionato) return;
    try {
      const res = await pagheApi.anteprimaCedolino(selezionato.id, annoSel, meseSel);
      setAnteprima(res.data);
      setErrore(null);
    } catch {
      setErrore("Impossibile calcolare l'anteprima.");
    }
  };

  const salvaCedolino = async () => {
    if (!selezionato) return;
    try {
      await pagheApi.salvaCedolino(selezionato.id, annoSel, meseSel);
      setSuccesso("Cedolino salvato.");
      setAnteprima(null);
      const res = await pagheApi.listCedolini(selezionato.id);
      setCedolini(res.data);
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Impossibile salvare il cedolino.");
    }
  };

  const eliminaCedolino = async (id: number) => {
    if (!confirm("Eliminare questo cedolino?")) return;
    try {
      await pagheApi.deleteCedolino(id);
      setSuccesso("Cedolino eliminato.");
      if (selezionato) {
        const res = await pagheApi.listCedolini(selezionato.id);
        setCedolini(res.data);
      }
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Impossibile eliminare: il cedolino è già contabilizzato.");
    }
  };

  const contabilizzaCedolino = async (id: number) => {
    if (!confirm("Contabilizzare questo cedolino in prima nota?")) return;
    try {
      await pagheApi.contabilizzaCedolino(id);
      setSuccesso("Cedolino contabilizzato in prima nota.");
      if (selezionato) {
        const res = await pagheApi.listCedolini(selezionato.id);
        setCedolini(res.data);
      }
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Impossibile contabilizzare il cedolino.");
    }
  };

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors">
            <ChevronLeft className="w-5 h-5" />
          </Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-orange-100 flex items-center justify-center">
              <Wallet className="w-5 h-5 text-orange-600" />
            </div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Gestione Paga Dipendenti</h1>
              <p className="text-xs text-gray-500">Anagrafica · Cedolini · Contabilizzazione in prima nota</p>
            </div>
          </div>
        </div>
      </header>

      {errore && (
        <div className="max-w-7xl mx-auto px-6 pt-4">
          <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span className="flex-1">{errore}</span>
            <button onClick={() => setErrore(null)}><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}
      {successo && (
        <div className="max-w-7xl mx-auto px-6 pt-4">
          <div className="flex items-center gap-2 bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 text-sm">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            <span className="flex-1">{successo}</span>
            <button onClick={() => setSuccesso(null)}><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-6 py-6 space-y-6">
        <p className="text-xs text-gray-400 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
          Motore di calcolo semplificato: IRPEF a scaglioni nazionali e detrazione lavoro
          dipendente (art. 13 TUIR), senza addizionali regionali/comunali, conguaglio di
          fine anno o carichi di famiglia. Non sostituisce un consulente del lavoro.
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
          {/* Lista dipendenti */}
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Dipendenti</h2>
              <button
                onClick={() => { setShowForm(!showForm); setSelezionato(null); setForm(emptyForm()); }}
                className="flex items-center gap-1.5 bg-orange-600 hover:bg-orange-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors"
              >
                <Plus className="w-4 h-4" /> Nuovo
              </button>
            </div>

            {dipendenti.length === 0 ? (
              <div className="text-center text-gray-400 py-12 bg-white rounded-xl border border-gray-100">
                Nessun dipendente registrato.
              </div>
            ) : (
              <div className="space-y-2">
                {dipendenti.map((d) => (
                  <button
                    key={d.id}
                    onClick={() => apriDettaglio(d)}
                    className={`w-full text-left bg-white border rounded-xl px-4 py-3 hover:border-orange-300 transition-colors ${
                      selezionato?.id === d.id ? "border-orange-400 ring-1 ring-orange-200" : "border-gray-100"
                    }`}
                  >
                    <p className="text-sm font-medium text-gray-800">{d.cognome} {d.nome}</p>
                    <div className="flex gap-4 mt-1 text-xs text-gray-500">
                      <span>{d.qualifica ?? "—"}</span>
                      <span className="font-medium text-gray-700">{EUR(d.retribuzione_lorda_mensile)}/mese</span>
                      <span>{d.numero_mensilita} mensilità</span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Form o dettaglio */}
          <div className="lg:col-span-3">
            {showForm ? (
              <div className="bg-white border border-gray-100 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-semibold text-gray-800">Nuovo dipendente</h3>
                  <button onClick={() => setShowForm(false)}>
                    <X className="w-4 h-4 text-gray-400 hover:text-gray-600" />
                  </button>
                </div>
                <form onSubmit={submitDipendente} className="space-y-4">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Nome *</label>
                      <input
                        type="text" value={form.nome}
                        onChange={(e) => setForm((f) => ({ ...f, nome: e.target.value }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Cognome *</label>
                      <input
                        type="text" value={form.cognome}
                        onChange={(e) => setForm((f) => ({ ...f, cognome: e.target.value }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Qualifica</label>
                      <input
                        type="text" value={form.qualifica ?? ""}
                        onChange={(e) => setForm((f) => ({ ...f, qualifica: e.target.value }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        placeholder="Es. Impiegato livello 3"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Data assunzione *</label>
                      <input
                        type="date" value={form.data_assunzione}
                        onChange={(e) => setForm((f) => ({ ...f, data_assunzione: e.target.value }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Retribuzione lorda mensile (€) *</label>
                      <input
                        type="number" min="0.01" step="0.01"
                        value={form.retribuzione_lorda_mensile || ""}
                        onChange={(e) => setForm((f) => ({ ...f, retribuzione_lorda_mensile: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Numero mensilità</label>
                      <select
                        value={form.numero_mensilita}
                        onChange={(e) => setForm((f) => ({ ...f, numero_mensilita: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-orange-300"
                      >
                        <option value={12}>12</option>
                        <option value={13}>13 (con tredicesima)</option>
                        <option value={14}>14 (con tredicesima e quattordicesima)</option>
                      </select>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Aliquota INPS dipendente %</label>
                      <input
                        type="number" min="0" max="100" step="0.01"
                        value={form.aliquota_inps_dipendente}
                        onChange={(e) => setForm((f) => ({ ...f, aliquota_inps_dipendente: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Aliquota INPS azienda %</label>
                      <input
                        type="number" min="0" max="100" step="0.01"
                        value={form.aliquota_inps_azienda}
                        onChange={(e) => setForm((f) => ({ ...f, aliquota_inps_azienda: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                      />
                    </div>
                  </div>
                  <label className="flex items-center gap-2 text-xs text-gray-600">
                    <input
                      type="checkbox" checked={form.detrazioni_attive}
                      onChange={(e) => setForm((f) => ({ ...f, detrazioni_attive: e.target.checked }))}
                    />
                    Applica detrazione lavoro dipendente (art. 13 TUIR)
                  </label>
                  <button
                    type="submit"
                    disabled={salvataggio}
                    className="w-full bg-orange-600 hover:bg-orange-700 disabled:bg-gray-300 text-white font-medium py-2.5 rounded-lg text-sm transition-colors"
                  >
                    {salvataggio ? "Salvataggio…" : "Crea dipendente"}
                  </button>
                </form>
              </div>
            ) : selezionato ? (
              <div className="space-y-4">
                <div className="bg-white border border-gray-100 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <h3 className="font-semibold text-gray-800">{selezionato.cognome} {selezionato.nome}</h3>
                      <p className="text-xs text-gray-400">
                        {EUR(selezionato.retribuzione_lorda_mensile)}/mese · {selezionato.numero_mensilita} mensilità ·
                        INPS dip. {selezionato.aliquota_inps_dipendente}% · INPS azienda {selezionato.aliquota_inps_azienda}%
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => eliminaDipendente(selezionato.id)}
                        className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                      <button onClick={() => setSelezionato(null)}>
                        <X className="w-4 h-4 text-gray-400 hover:text-gray-600" />
                      </button>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-end gap-3 border-t border-gray-100 pt-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Mese</label>
                      <select
                        value={meseSel}
                        onChange={(e) => setMeseSel(Number(e.target.value))}
                        className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white"
                      >
                        {MESI.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Anno</label>
                      <input
                        type="number" value={annoSel}
                        onChange={(e) => setAnnoSel(Number(e.target.value))}
                        className="w-24 border border-gray-200 rounded-lg px-3 py-2 text-sm"
                      />
                    </div>
                    <button
                      onClick={calcolaAnteprima}
                      className="flex items-center gap-1.5 text-sm text-gray-600 border border-gray-200 hover:bg-gray-50 px-3 py-2 rounded-lg transition-colors"
                    >
                      <Eye className="w-4 h-4" /> Anteprima
                    </button>
                    <button
                      onClick={salvaCedolino}
                      className="flex items-center gap-1.5 bg-orange-600 hover:bg-orange-700 text-white text-sm px-3 py-2 rounded-lg transition-colors"
                    >
                      <Plus className="w-4 h-4" /> Genera cedolino
                    </button>
                  </div>

                  {anteprima && (
                    <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                      <div className="bg-gray-50 rounded-lg p-2.5">
                        <p className="text-gray-500">Lordo</p>
                        <p className="font-semibold text-gray-800">{EUR(anteprima.retribuzione_lorda)}</p>
                      </div>
                      <div className="bg-gray-50 rounded-lg p-2.5">
                        <p className="text-gray-500">Contributi INPS</p>
                        <p className="font-semibold text-gray-800">{EUR(anteprima.contributi_inps_dipendente)}</p>
                      </div>
                      <div className="bg-gray-50 rounded-lg p-2.5">
                        <p className="text-gray-500">IRPEF netta</p>
                        <p className="font-semibold text-gray-800">{EUR(anteprima.irpef_netta)}</p>
                      </div>
                      <div className="bg-green-50 rounded-lg p-2.5">
                        <p className="text-green-700">Netto in busta</p>
                        <p className="font-bold text-green-700">{EUR(anteprima.netto_busta)}</p>
                      </div>
                      <div className="bg-gray-50 rounded-lg p-2.5">
                        <p className="text-gray-500">Contributi azienda</p>
                        <p className="font-semibold text-gray-800">{EUR(anteprima.contributi_inps_azienda)}</p>
                      </div>
                      <div className="bg-gray-50 rounded-lg p-2.5">
                        <p className="text-gray-500">Quota TFR</p>
                        <p className="font-semibold text-gray-800">{EUR(anteprima.quota_tfr)}</p>
                      </div>
                      <div className="bg-orange-50 rounded-lg p-2.5 col-span-2">
                        <p className="text-orange-700">Costo totale azienda</p>
                        <p className="font-bold text-orange-700">{EUR(anteprima.costo_azienda)}</p>
                      </div>
                    </div>
                  )}
                </div>

                <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
                  <div className="px-4 py-3 border-b border-gray-100">
                    <h3 className="text-sm font-semibold text-gray-700">Cedolini salvati</h3>
                  </div>
                  {cedolini.length === 0 ? (
                    <div className="text-center text-gray-400 py-8 text-sm">Nessun cedolino salvato.</div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs">
                        <thead className="bg-gray-50 border-b border-gray-100">
                          <tr>
                            <th className="text-left px-4 py-2 font-semibold text-gray-600">Periodo</th>
                            <th className="text-right px-3 py-2 font-semibold text-gray-600">Lordo</th>
                            <th className="text-right px-3 py-2 font-semibold text-gray-600">IRPEF</th>
                            <th className="text-right px-3 py-2 font-semibold text-gray-600">Netto</th>
                            <th className="text-right px-3 py-2 font-semibold text-gray-600">Costo azienda</th>
                            <th className="text-center px-3 py-2 font-semibold text-gray-600">Prima nota</th>
                            <th className="w-8"></th>
                          </tr>
                        </thead>
                        <tbody>
                          {cedolini.map((c) => (
                            <tr key={c.id} className="border-b border-gray-50 hover:bg-gray-50">
                              <td className="px-4 py-2 font-medium text-gray-700">{MESI[c.mese - 1]} {c.anno}</td>
                              <td className="px-3 py-2 text-right text-gray-600">{EUR(c.retribuzione_lorda)}</td>
                              <td className="px-3 py-2 text-right text-gray-600">{EUR(c.irpef_netta)}</td>
                              <td className="px-3 py-2 text-right font-semibold text-green-700">{EUR(c.netto_busta)}</td>
                              <td className="px-3 py-2 text-right text-orange-700">{EUR(c.costo_azienda)}</td>
                              <td className="px-3 py-2 text-center">
                                {c.registrazione_id ? (
                                  <span className="text-xs bg-green-50 text-green-700 px-1.5 py-0.5 rounded inline-flex items-center gap-1">
                                    <CheckCircle2 className="w-3 h-3" /> Fatta
                                  </span>
                                ) : (
                                  <button
                                    onClick={() => c.id && contabilizzaCedolino(c.id)}
                                    className="text-xs bg-orange-50 text-orange-700 hover:bg-orange-100 px-2 py-0.5 rounded inline-flex items-center gap-1"
                                  >
                                    <Calculator className="w-3 h-3" /> Contabilizza
                                  </button>
                                )}
                              </td>
                              <td className="px-2 py-2 text-center">
                                {!c.registrazione_id && (
                                  <button onClick={() => c.id && eliminaCedolino(c.id)} className="text-red-400 hover:text-red-600">
                                    <Trash2 className="w-3 h-3" />
                                  </button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-center h-48 text-gray-300 text-sm bg-white border border-gray-100 rounded-xl">
                Seleziona un dipendente o creane uno nuovo
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

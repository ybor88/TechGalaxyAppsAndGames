"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  ChevronLeft,
  Plus,
  Trash2,
  Building2,
  AlertCircle,
  CheckCircle2,
  X,
  Archive,
  Calculator,
} from "lucide-react";
import {
  ammortamentiApi,
  contiApi,
  Conto,
  Cespite,
  CespiteCreate,
  CategoriaMinisteriale,
  PianoAmmortamentoResponse,
  RiepilogoAmmortamenti,
} from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const today = () => new Date().toISOString().split("T")[0];
const annoCorrente = () => new Date().getFullYear();

const emptyForm = (): CespiteCreate => ({
  descrizione: "",
  categoria: "",
  data_acquisto: today(),
  costo_storico: 0,
  conto_costo_id: 0,
  conto_fondo_id: 0,
});

export default function AmmortamentiPage() {
  const [cespiti, setCespiti] = useState<Cespite[]>([]);
  const [categorie, setCategorie] = useState<CategoriaMinisteriale[]>([]);
  const [conti, setConti] = useState<Conto[]>([]);
  const [riepilogo, setRiepilogo] = useState<RiepilogoAmmortamenti | null>(null);
  const [selezionato, setSelezionato] = useState<Cespite | null>(null);
  const [piano, setPiano] = useState<PianoAmmortamentoResponse | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CespiteCreate>(emptyForm());
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);
  const [salvataggio, setSalvataggio] = useState(false);
  const [dismDate, setDismDate] = useState(today());

  const carica = useCallback(async () => {
    try {
      const [cRes, catRes, contiRes, riepRes] = await Promise.all([
        ammortamentiApi.listCespiti(),
        ammortamentiApi.listCategorie(),
        contiApi.list(),
        ammortamentiApi.getRiepilogo(annoCorrente()),
      ]);
      setCespiti(cRes.data);
      setCategorie(catRes.data);
      setConti(contiRes.data);
      setRiepilogo(riepRes.data);
    } catch {
      setErrore("Impossibile caricare i dati.");
    }
  }, []);

  useEffect(() => {
    carica();
  }, [carica]);

  const apriDettaglio = async (c: Cespite) => {
    setSelezionato(c);
    setShowForm(false);
    try {
      const res = await ammortamentiApi.getPiano(c.id);
      setPiano(res.data);
    } catch {
      setErrore("Impossibile caricare il piano di ammortamento.");
    }
  };

  const submitCespite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.descrizione.trim() || !form.categoria || !form.conto_costo_id || !form.conto_fondo_id) {
      setErrore("Compila tutti i campi obbligatori.");
      return;
    }
    setSalvataggio(true);
    setErrore(null);
    try {
      await ammortamentiApi.createCespite(form);
      setSuccesso("Cespite creato.");
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

  const eliminaCespite = async (id: number) => {
    if (!confirm("Eliminare questo cespite?")) return;
    try {
      await ammortamentiApi.deleteCespite(id);
      setSuccesso("Cespite eliminato.");
      if (selezionato?.id === id) setSelezionato(null);
      carica();
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Impossibile eliminare: verifica che non ci siano quote già contabilizzate.");
    }
  };

  const dismetti = async (id: number) => {
    try {
      await ammortamentiApi.dismettiCespite(id, dismDate);
      setSuccesso("Cespite dismesso.");
      carica();
      if (selezionato?.id === id) {
        const res = await ammortamentiApi.getPiano(id);
        setPiano(res.data);
      }
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Impossibile dismettere il cespite.");
    }
  };

  const contabilizza = async (id: number, anno: number) => {
    if (!confirm(`Contabilizzare la quota di ammortamento ${anno} in prima nota?`)) return;
    try {
      await ammortamentiApi.contabilizza(id, anno);
      setSuccesso(`Quota ${anno} contabilizzata in prima nota.`);
      const res = await ammortamentiApi.getPiano(id);
      setPiano(res.data);
      carica();
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(detail ?? "Impossibile contabilizzare la quota.");
    }
  };

  const contiCosto = conti.filter((c) => c.tipo === "costo");
  const contiAttivo = conti.filter((c) => c.tipo === "attivo");
  const categoriaSelezionata = categorie.find((c) => c.categoria === form.categoria);

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors">
            <ChevronLeft className="w-5 h-5" />
          </Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-orange-100 flex items-center justify-center">
              <Building2 className="w-5 h-5 text-orange-600" />
            </div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Ammortamenti</h1>
              <p className="text-xs text-gray-500">Cespiti · Piano civilistico e fiscale · Contabilizzazione</p>
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
        {riepilogo && (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[
              { label: "Cespiti attivi", value: riepilogo.numero_cespiti, isCount: true },
              { label: "Costo storico totale", value: riepilogo.totale_costo_storico },
              { label: `Quota ammortamento ${riepilogo.anno}`, value: riepilogo.totale_quota_anno },
              { label: "Valore residuo netto", value: riepilogo.totale_valore_residuo },
            ].map(({ label, value, isCount }) => (
              <div key={label} className="bg-white border border-gray-100 rounded-xl p-4">
                <p className="text-xs text-gray-500 mb-1">{label}</p>
                <p className="text-lg font-bold text-orange-600">{isCount ? value : EUR(value)}</p>
              </div>
            ))}
          </div>
        )}

        <p className="text-xs text-gray-400 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
          Le aliquote fiscali proposte sono un sottoinsieme indicativo della tabella ministeriale
          (DM 31/12/1988) per le categorie di beni più comuni. Verifica sempre con il tuo
          commercialista l&apos;aliquota corretta per il settore ATECO specifico.
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
          {/* Lista cespiti */}
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Cespiti</h2>
              <button
                onClick={() => { setShowForm(!showForm); setSelezionato(null); setForm(emptyForm()); }}
                className="flex items-center gap-1.5 bg-orange-600 hover:bg-orange-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors"
              >
                <Plus className="w-4 h-4" /> Nuovo
              </button>
            </div>

            {cespiti.length === 0 ? (
              <div className="text-center text-gray-400 py-12 bg-white rounded-xl border border-gray-100">
                Nessun cespite registrato.
              </div>
            ) : (
              <div className="space-y-2">
                {cespiti.map((c) => (
                  <button
                    key={c.id}
                    onClick={() => apriDettaglio(c)}
                    className={`w-full text-left bg-white border rounded-xl px-4 py-3 hover:border-orange-300 transition-colors ${
                      selezionato?.id === c.id ? "border-orange-400 ring-1 ring-orange-200" : "border-gray-100"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs text-gray-400">{c.data_acquisto}</span>
                      {c.dismesso && (
                        <span className="text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded flex items-center gap-1">
                          <Archive className="w-3 h-3" /> Dismesso
                        </span>
                      )}
                    </div>
                    <p className="text-sm font-medium text-gray-800 truncate">{c.descrizione}</p>
                    <div className="flex gap-4 mt-1 text-xs text-gray-500">
                      <span>{c.categoria_label}</span>
                      <span className="font-medium text-gray-700">{EUR(c.costo_storico)}</span>
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
                  <h3 className="font-semibold text-gray-800">Nuovo cespite</h3>
                  <button onClick={() => setShowForm(false)}>
                    <X className="w-4 h-4 text-gray-400 hover:text-gray-600" />
                  </button>
                </div>
                <form onSubmit={submitCespite} className="space-y-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Descrizione *</label>
                    <input
                      type="text"
                      value={form.descrizione}
                      onChange={(e) => setForm((f) => ({ ...f, descrizione: e.target.value }))}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                      placeholder="Es. Furgone Fiat Ducato"
                      required
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Categoria (tabella ministeriale) *</label>
                      <select
                        value={form.categoria}
                        onChange={(e) => {
                          const cat = categorie.find((c) => c.categoria === e.target.value);
                          const aliquota = cat ? Number(cat.aliquota_fiscale) : undefined;
                          setForm((f) => ({
                            ...f,
                            categoria: e.target.value,
                            aliquota_fiscale: aliquota,
                            aliquota_civilistica: aliquota,
                          }));
                        }}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      >
                        <option value="">— seleziona —</option>
                        {categorie.map((c) => (
                          <option key={c.categoria} value={c.categoria}>
                            {c.label} ({Number(c.aliquota_fiscale)}%)
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Data acquisto *</label>
                      <input
                        type="date"
                        value={form.data_acquisto}
                        onChange={(e) => setForm((f) => ({ ...f, data_acquisto: e.target.value }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-3 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Costo storico (€) *</label>
                      <input
                        type="number" min="0.01" step="0.01"
                        value={form.costo_storico || ""}
                        onChange={(e) => setForm((f) => ({ ...f, costo_storico: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Aliquota civilistica %</label>
                      <input
                        type="number" min="0.01" max="100" step="0.01"
                        value={form.aliquota_civilistica ?? (categoriaSelezionata ? Number(categoriaSelezionata.aliquota_fiscale) : "")}
                        onChange={(e) => setForm((f) => ({ ...f, aliquota_civilistica: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Aliquota fiscale %</label>
                      <input
                        type="number" min="0.01" max="100" step="0.01"
                        value={form.aliquota_fiscale ?? (categoriaSelezionata ? Number(categoriaSelezionata.aliquota_fiscale) : "")}
                        onChange={(e) => setForm((f) => ({ ...f, aliquota_fiscale: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                      />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Conto costo (dare) *</label>
                      <select
                        value={form.conto_costo_id || ""}
                        onChange={(e) => setForm((f) => ({ ...f, conto_costo_id: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      >
                        <option value="">— seleziona —</option>
                        {contiCosto.map((c) => (
                          <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Conto fondo amm.to (avere) *</label>
                      <select
                        value={form.conto_fondo_id || ""}
                        onChange={(e) => setForm((f) => ({ ...f, conto_fondo_id: Number(e.target.value) }))}
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      >
                        <option value="">— seleziona —</option>
                        {contiAttivo.map((c) => (
                          <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  {contiCosto.length === 0 && (
                    <p className="text-xs text-amber-600">
                      Nessun conto di costo/attivo trovato: vai in{" "}
                      <Link href="/contabilita" className="underline">Contabilità Generale</Link> e
                      inizializza il piano dei conti standard.
                    </p>
                  )}
                  <button
                    type="submit"
                    disabled={salvataggio}
                    className="w-full bg-orange-600 hover:bg-orange-700 disabled:bg-gray-300 text-white font-medium py-2.5 rounded-lg text-sm transition-colors"
                  >
                    {salvataggio ? "Salvataggio…" : "Crea cespite"}
                  </button>
                </form>
              </div>
            ) : selezionato && piano ? (
              <div className="bg-white border border-gray-100 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h3 className="font-semibold text-gray-800">{selezionato.descrizione}</h3>
                    <p className="text-xs text-gray-400">
                      {selezionato.categoria_label} · Acquisto {selezionato.data_acquisto} · {EUR(selezionato.costo_storico)}
                    </p>
                  </div>
                  <div className="flex gap-2 items-center">
                    {!selezionato.dismesso && (
                      <>
                        <input
                          type="date"
                          value={dismDate}
                          onChange={(e) => setDismDate(e.target.value)}
                          className="text-xs border border-gray-200 rounded-lg px-2 py-1.5"
                        />
                        <button
                          onClick={() => dismetti(selezionato.id)}
                          title="Dismetti cespite"
                          className="p-2 text-gray-400 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-colors"
                        >
                          <Archive className="w-4 h-4" />
                        </button>
                      </>
                    )}
                    <button
                      onClick={() => eliminaCespite(selezionato.id)}
                      title="Elimina cespite"
                      className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                    <button onClick={() => setSelezionato(null)}>
                      <X className="w-4 h-4 text-gray-400 hover:text-gray-600" />
                    </button>
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="text-gray-500 border-b border-gray-100">
                        <th className="text-left pb-2 font-medium">Anno</th>
                        <th className="text-right pb-2 font-medium">Quota civ.</th>
                        <th className="text-right pb-2 font-medium">Fondo civ.</th>
                        <th className="text-right pb-2 font-medium">Residuo civ.</th>
                        <th className="text-right pb-2 font-medium">Quota fisc.</th>
                        <th className="text-right pb-2 font-medium">Residuo fisc.</th>
                        <th className="text-center pb-2 font-medium">Prima nota</th>
                      </tr>
                    </thead>
                    <tbody>
                      {piano.quote.map((q) => (
                        <tr key={q.anno} className="border-b border-gray-50">
                          <td className="py-2 font-medium text-gray-700">{q.anno}</td>
                          <td className="py-2 text-right text-gray-700">{EUR(q.quota_civilistica)}</td>
                          <td className="py-2 text-right text-gray-500">{EUR(q.fondo_civilistico)}</td>
                          <td className="py-2 text-right text-gray-500">{EUR(q.valore_residuo_civilistico)}</td>
                          <td className="py-2 text-right text-gray-700">{EUR(q.quota_fiscale)}</td>
                          <td className="py-2 text-right text-gray-500">{EUR(q.valore_residuo_fiscale)}</td>
                          <td className="py-2 text-center">
                            {q.contabilizzato ? (
                              <span className="text-xs bg-green-50 text-green-700 px-1.5 py-0.5 rounded inline-flex items-center gap-1">
                                <CheckCircle2 className="w-3 h-3" /> Fatta
                              </span>
                            ) : q.quota_civilistica > 0 ? (
                              <button
                                onClick={() => contabilizza(selezionato.id, q.anno)}
                                className="text-xs bg-orange-50 text-orange-700 hover:bg-orange-100 px-2 py-0.5 rounded inline-flex items-center gap-1"
                              >
                                <Calculator className="w-3 h-3" /> Contabilizza
                              </button>
                            ) : (
                              "—"
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-center h-48 text-gray-300 text-sm bg-white border border-gray-100 rounded-xl">
                Seleziona un cespite o creane uno nuovo
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

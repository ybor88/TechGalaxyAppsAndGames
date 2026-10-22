// Motore AI di Nexora: previsione vendite (Holt-Winters), riordino automatico (punto di riordino con scorta
// di sicurezza), analisi clienti (RFM e rischio di abbandono) e alert intelligenti. Tutto calcolato localmente,
// senza servizi esterni a pagamento; ogni risultato è accompagnato dalla sua spiegazione. © Roberto Di Flumeri
import { db } from "../lib/db.js";
import { arrotonda3 } from "../lib/importi.js";
import { creaAcquisto } from "./acquisti.js";

const GIORNO = 86400_000;
const STATI_VENDITA = ["confermato", "in_preparazione", "spedito", "consegnato"];

const giornoDi = (d: Date) => d.toISOString().slice(0, 10);
const media = (v: number[]) => (v.length ? v.reduce((a, b) => a + b, 0) / v.length : 0);
const devStd = (v: number[]) => {
  const m = media(v);
  return v.length > 1 ? Math.sqrt(v.reduce((s, x) => s + (x - m) ** 2, 0) / (v.length - 1)) : 0;
};

// ── Serie storiche ──────────────────────────────────────────────────────────

export interface Punto { data: string; valore: number }

/** Serie giornaliera (giorni senza vendite = 0) delle quantità vendute o del fatturato.
 *  Termina ieri: la giornata in corso è incompleta e falserebbe previsioni e variabilità. */
export async function serieGiornaliera(aziendaId: string, opz: { varianteId?: string; misura: "quantita" | "fatturato"; giorni: number; fino?: Date }) {
  const fine = new Date(new Date(giornoDi(opz.fino ?? new Date()) + "T00:00:00.000Z").getTime() - (opz.fino ? 0 : GIORNO));
  const inizio = new Date(fine.getTime() - (opz.giorni - 1) * GIORNO);
  const valori = new Map<string, number>();
  if (opz.misura === "quantita") {
    const mov = await db.movimento.findMany({
      where: { aziendaId, tipo: "vendita", createdAt: { gte: inizio, lt: new Date(fine.getTime() + GIORNO) }, ...(opz.varianteId ? { varianteId: opz.varianteId } : {}) },
      select: { createdAt: true, quantita: true },
    });
    for (const m of mov) valori.set(giornoDi(m.createdAt), (valori.get(giornoDi(m.createdAt)) || 0) + m.quantita);
  } else {
    const ordini = await db.ordineVendita.findMany({
      where: { aziendaId, stato: { in: STATI_VENDITA }, data: { gte: inizio, lt: new Date(fine.getTime() + GIORNO) } },
      select: { data: true, imponibile: true },
    });
    for (const o of ordini) valori.set(giornoDi(o.data), (valori.get(giornoDi(o.data)) || 0) + o.imponibile);
  }
  const serie: Punto[] = [];
  for (let t = inizio.getTime(); t <= fine.getTime(); t += GIORNO) {
    const k = giornoDi(new Date(t));
    serie.push({ data: k, valore: valori.get(k) || 0 });
  }
  return serie;
}

// ── Previsione: Holt-Winters additivo con stagionalità settimanale ───────────

interface Modello { alpha: number; beta: number; gamma: number; sse: number }

function holtWinters(y: number[], periodo: number, a: number, b: number, g: number, orizzonte: number) {
  let livello = media(y.slice(0, periodo));
  let trend = (media(y.slice(periodo, 2 * periodo)) - livello) / periodo;
  const stag = y.slice(0, periodo).map((v) => v - livello);
  let sse = 0;
  const residui: number[] = [];
  for (let t = periodo; t < y.length; t++) {
    const prev = livello + trend + stag[t % periodo];
    const err = y[t] - prev;
    sse += err * err;
    residui.push(err);
    const lPrec = livello;
    livello = a * (y[t] - stag[t % periodo]) + (1 - a) * (livello + trend);
    trend = b * (livello - lPrec) + (1 - b) * trend;
    stag[t % periodo] = g * (y[t] - livello) + (1 - g) * stag[t % periodo];
  }
  const futuro = Array.from({ length: orizzonte }, (_, h) => livello + (h + 1) * trend + stag[(y.length + h) % periodo]);
  return { sse, futuro, residui };
}

function esponenzialeSemplice(y: number[], orizzonte: number) {
  const a = 0.3;
  let l = y[0] ?? 0;
  const residui: number[] = [];
  for (let t = 1; t < y.length; t++) {
    residui.push(y[t] - l);
    l = a * y[t] + (1 - a) * l;
  }
  return { futuro: Array(orizzonte).fill(l), residui };
}

export interface Previsione {
  storico: Punto[];
  previsione: { data: string; valore: number; minimo: number; massimo: number }[];
  metodo: string;
  accuratezza: { mape: number | null; descrizione: string };
  totalePrevisto: number;
  spiegazione: string;
}

export function prevedi(storicoCompleto: Punto[], orizzonte: number): Previsione {
  // i giorni prima della prima vendita (prodotto non ancora a catalogo) non sono domanda nulla
  const primo = storicoCompleto.findIndex((p) => p.valore > 0);
  const storico = primo > 0 ? storicoCompleto.slice(primo) : storicoCompleto;
  const y = storico.map((p) => p.valore);
  const periodo = 7;
  let futuro: number[];
  let residui: number[];
  let metodo: string;
  let mape: number | null = null;
  if (y.length >= periodo * 4 && y.some((v) => v > 0)) {
    // parametri scelti minimizzando l'errore di previsione a un passo (griglia)
    let migliore: Modello = { alpha: 0.3, beta: 0.05, gamma: 0.2, sse: Infinity };
    for (const alpha of [0.1, 0.2, 0.3, 0.5]) for (const beta of [0.01, 0.05, 0.1]) for (const gamma of [0.1, 0.2, 0.4]) {
      const { sse } = holtWinters(y, periodo, alpha, beta, gamma, 1);
      if (sse < migliore.sse) migliore = { alpha, beta, gamma, sse };
    }
    ({ futuro, residui } = holtWinters(y, periodo, migliore.alpha, migliore.beta, migliore.gamma, orizzonte));
    metodo = `Holt-Winters additivo, stagionalità settimanale (α=${migliore.alpha}, β=${migliore.beta}, γ=${migliore.gamma})`;
    // accuratezza misurata sulle ultime due settimane tenute fuori dall'addestramento
    if (y.length >= periodo * 6) {
      const test = y.slice(-14);
      const { futuro: pt } = holtWinters(y.slice(0, -14), periodo, migliore.alpha, migliore.beta, migliore.gamma, 14);
      const totReale = test.reduce((a, b) => a + b, 0);
      if (totReale > 0) mape = Math.round((Math.abs(pt.reduce((a, b) => a + Math.max(0, b), 0) - totReale) / totReale) * 1000) / 10;
    }
  } else {
    ({ futuro, residui } = esponenzialeSemplice(y, orizzonte));
    metodo = "Smorzamento esponenziale semplice (storico breve)";
  }
  const sigma = devStd(residui.slice(-56));
  const ultimo = storico.length ? new Date(storico[storico.length - 1].data + "T00:00:00Z") : new Date();
  const previsione = futuro.map((v, h) => {
    const val = Math.max(0, v);
    const ampiezza = 1.28 * sigma * Math.sqrt(1 + h / 7); // intervallo all'80%
    return { data: giornoDi(new Date(ultimo.getTime() + (h + 1) * GIORNO)), valore: arrotonda3(val),
      minimo: arrotonda3(Math.max(0, val - ampiezza)), massimo: arrotonda3(val + ampiezza) };
  });
  const totalePrevisto = arrotonda3(previsione.reduce((s, p) => s + p.valore, 0));
  const ultimi = y.slice(-orizzonte).reduce((a, b) => a + b, 0);
  const variazione = ultimi > 0 ? Math.round(((totalePrevisto - ultimi) / ultimi) * 100) : null;
  return {
    storico, previsione, metodo, totalePrevisto,
    accuratezza: { mape, descrizione: mape === null ? "Storico insufficiente per misurare l'errore" : `Errore medio sulle ultime 2 settimane: ${mape}%` },
    spiegazione: variazione === null ? "Previsione basata sull'andamento recente."
      : `Nei prossimi ${orizzonte} giorni è previsto un ${variazione >= 0 ? "aumento" : "calo"} del ${Math.abs(variazione)}% rispetto agli ultimi ${orizzonte}.`,
  };
}

// ── Riordino automatico ─────────────────────────────────────────────────────

export interface SuggerimentoRiordino {
  varianteId: string; sku: string; nome: string; fornitoreId: string | null; fornitore: string | null;
  disponibile: number; inArrivo: number; domandaGiornaliera: number; leadTime: number; scortaSicurezza: number;
  puntoRiordino: number; quantitaSuggerita: number; costoUnitario: number; urgenza: "alta" | "media"; motivazione: string;
}

const Z_95 = 1.65; // livello di servizio 95%
const PERIODO_REVISIONE = 14; // giorni coperti da ogni riordino

export async function suggerimentiRiordino(aziendaId: string): Promise<SuggerimentoRiordino[]> {
  const varianti = await db.variante.findMany({
    where: { aziendaId, attivo: true, prodotto: { attivo: true } },
    include: { prodotto: { include: { fornitore: true } }, giacenze: true },
  });
  const inArrivo = await db.rigaAcquisto.findMany({ where: { ordine: { aziendaId, stato: { in: ["bozza", "inviato", "parziale"] } } } });
  const arrivi = new Map<string, number>();
  for (const r of inArrivo) arrivi.set(r.varianteId, (arrivi.get(r.varianteId) || 0) + (r.quantita - r.quantitaRicevuta));
  const inizio = new Date(Date.now() - 90 * GIORNO);
  const vendite = await db.movimento.groupBy({ by: ["varianteId"], where: { aziendaId, tipo: "vendita", createdAt: { gte: inizio } }, _sum: { quantita: true } });
  const venduto = new Map(vendite.map((v) => [v.varianteId, v._sum.quantita || 0]));
  const out: SuggerimentoRiordino[] = [];
  for (const v of varianti) {
    const p = v.prodotto;
    const disponibile = arrotonda3(v.giacenze.reduce((s, g) => s + g.quantita - g.riservata, 0));
    const arrivo = arrotonda3(arrivi.get(v.id) || 0);
    const d = (venduto.get(v.id) || 0) / 90;
    if (d === 0 && p.scortaMinima === 0) continue;
    let sigma = 0;
    if (d > 0) sigma = devStd((await serieGiornaliera(aziendaId, { varianteId: v.id, misura: "quantita", giorni: 90 })).map((x) => x.valore));
    const lt = p.fornitore?.leadTimeGiorni ?? p.leadTimeGiorni;
    const ss = Z_95 * sigma * Math.sqrt(lt);
    const rop = Math.max(d * lt + ss, p.scortaMinima);
    if (disponibile + arrivo > rop) continue;
    let q = rop + d * PERIODO_REVISIONE - (disponibile + arrivo);
    q = p.unita === "pz" || p.unita === "conf" ? Math.ceil(q) : arrotonda3(q);
    if (q <= 0) continue;
    const giorniCopertura = d > 0 ? Math.floor(disponibile / d) : null;
    out.push({
      varianteId: v.id, sku: v.sku, nome: v.nome === "Standard" ? p.nome : `${p.nome} - ${v.nome}`, fornitoreId: p.fornitoreId,
      fornitore: p.fornitore?.ragioneSociale ?? null, disponibile, inArrivo: arrivo, domandaGiornaliera: arrotonda3(d), leadTime: lt,
      scortaSicurezza: arrotonda3(ss), puntoRiordino: arrotonda3(rop), quantitaSuggerita: q, costoUnitario: p.costoAcquisto,
      urgenza: disponibile <= d * lt / 2 || disponibile <= 0 ? "alta" : "media",
      motivazione: d > 0
        ? `Vendi in media ${arrotonda3(d)} ${p.unita}/giorno; il fornitore consegna in ${lt} giorni. Con ${disponibile} disponibili` +
          `${arrivo ? ` e ${arrivo} in arrivo` : ""} ${giorniCopertura !== null ? `copri circa ${giorniCopertura} giorni` : ""}: sotto il punto di riordino di ${arrotonda3(rop)}.`
        : `Giacenza disponibile (${disponibile}) sotto la scorta minima impostata (${p.scortaMinima}).`,
    });
  }
  return out.sort((a, b) => (a.urgenza === b.urgenza ? b.quantitaSuggerita * b.costoUnitario - a.quantitaSuggerita * a.costoUnitario : a.urgenza === "alta" ? -1 : 1));
}

/** Crea ordini di acquisto in bozza, uno per fornitore, dai suggerimenti selezionati. */
export async function generaRiordini(aziendaId: string, varianteIds?: string[]) {
  const sugg = (await suggerimentiRiordino(aziendaId)).filter((s) => s.fornitoreId && (!varianteIds || varianteIds.includes(s.varianteId)));
  const perFornitore = new Map<string, SuggerimentoRiordino[]>();
  for (const s of sugg) perFornitore.set(s.fornitoreId!, [...(perFornitore.get(s.fornitoreId!) || []), s]);
  const creati = [];
  for (const [fornitoreId, righe] of perFornitore) {
    creati.push(await db.$transaction((tx) => creaAcquisto(tx, aziendaId, {
      fornitoreId, generatoDaAi: true, note: "Generato dal motore AI di riordino",
      righe: righe.map((r) => ({ varianteId: r.varianteId, quantita: r.quantitaSuggerita, costoUnitario: r.costoUnitario })),
    })));
  }
  return { ordini: creati, senzaFornitore: (await suggerimentiRiordino(aziendaId)).filter((s) => !s.fornitoreId).length };
}

// ── Analisi clienti: RFM, rischio di abbandono, valore ──────────────────────

export interface ProfiloCliente {
  clienteId: string; nome: string; recenzaGiorni: number; frequenza: number; valore: number; r: number; f: number; m: number;
  segmento: string; rischioAbbandono: "basso" | "medio" | "alto"; valoreAnnuoStimato: number; intervalloMedioGiorni: number | null;
  consiglio: string;
}

const CONSIGLI: Record<string, string> = {
  Campioni: "Clienti migliori: premiali con anteprime e condizioni dedicate.",
  Fedeli: "Acquistano spesso: proponi prodotti complementari e programmi fedeltà.",
  Promettenti: "Recenti ma ancora poco frequenti: un'offerta sul secondo acquisto li fidelizza.",
  Nuovi: "Primo acquisto da poco: segui la consegna e chiedi un riscontro.",
  "A rischio": "Compravano spesso ma non ordinano da tempo: contattali con un'offerta personalizzata.",
  Dormienti: "Inattivi da molto: una campagna di riattivazione può riportarli.",
  "Da coltivare": "Comportamento nella media: tienili aggiornati sulle novità.",
};

function quintile(valori: number[], v: number, inverso = false) {
  const ordinati = [...valori].sort((a, b) => a - b);
  const pos = ordinati.filter((x) => x <= v).length / ordinati.length;
  const s = Math.min(5, Math.max(1, Math.ceil(pos * 5)));
  return inverso ? 6 - s : s;
}

export async function analisiClienti(aziendaId: string): Promise<ProfiloCliente[]> {
  const ora = Date.now();
  const ordini = await db.ordineVendita.findMany({
    where: { aziendaId, stato: { in: STATI_VENDITA }, data: { gte: new Date(ora - 365 * GIORNO) } },
    select: { clienteId: true, data: true, imponibile: true, cliente: { select: { ragioneSociale: true } } },
    orderBy: { data: "asc" },
  });
  const per = new Map<string, { nome: string; date: Date[]; valore: number }>();
  for (const o of ordini) {
    const c = per.get(o.clienteId) || { nome: o.cliente.ragioneSociale, date: [], valore: 0 };
    c.date.push(o.data);
    c.valore += o.imponibile;
    per.set(o.clienteId, c);
  }
  const righe = [...per.entries()].map(([id, c]) => ({
    id, nome: c.nome, recenza: Math.floor((ora - c.date[c.date.length - 1].getTime()) / GIORNO), frequenza: c.date.length, valore: c.valore, date: c.date,
  }));
  const R = righe.map((x) => x.recenza), F = righe.map((x) => x.frequenza), M = righe.map((x) => x.valore);
  return righe.map((x) => {
    const r = quintile(R, x.recenza, true), f = quintile(F, x.frequenza), m = quintile(M, x.valore);
    const intervalli = x.date.slice(1).map((d, i) => (d.getTime() - x.date[i].getTime()) / GIORNO);
    const intervallo = intervalli.length ? media(intervalli) : null;
    const rapporto = intervallo ? x.recenza / intervallo : x.recenza / 60;
    const rischio = rapporto > 2.5 ? "alto" : rapporto > 1.5 ? "medio" : "basso";
    // la recenza conta rispetto al ritmo abituale del cliente: chi ordina ogni mese non è "a rischio" dopo tre settimane
    const inRitardo = rischio !== "basso";
    let segmento = "Da coltivare";
    if (r >= 4 && f >= 4 && m >= 4) segmento = "Campioni";
    else if (f >= 4 && (r >= 3 || !inRitardo)) segmento = "Fedeli";
    else if (x.frequenza === 1 && r >= 4) segmento = "Nuovi";
    else if (r >= 4) segmento = "Promettenti";
    else if (r <= 2 && inRitardo && (f >= 3 || m >= 4)) segmento = "A rischio";
    else if (r <= 2 && inRitardo) segmento = "Dormienti";
    const valoreAnnuo = Math.round(x.frequenza > 1 && intervallo ? (x.valore / x.frequenza) * (365 / Math.max(intervallo, 7)) : x.valore);
    return {
      clienteId: x.id, nome: x.nome, recenzaGiorni: x.recenza, frequenza: x.frequenza, valore: x.valore, r, f, m, segmento,
      rischioAbbandono: rischio, valoreAnnuoStimato: valoreAnnuo, intervalloMedioGiorni: intervallo ? Math.round(intervallo) : null,
      consiglio: CONSIGLI[segmento],
    } as ProfiloCliente;
  }).sort((a, b) => b.valore - a.valore);
}

// ── Alert intelligenti ──────────────────────────────────────────────────────

interface NuovoAlert { tipo: string; gravita: "info" | "attenzione" | "critico"; titolo: string; messaggio: string; entita?: string; entitaId?: string; chiave: string }

export async function aggiornaAlert(aziendaId: string) {
  const ora = new Date();
  const nuovi: NuovoAlert[] = [];

  // scorte sotto il minimo
  const varianti = await db.variante.findMany({ where: { aziendaId, attivo: true, prodotto: { attivo: true, scortaMinima: { gt: 0 } } }, include: { prodotto: true, giacenze: true } });
  for (const v of varianti) {
    const disp = arrotonda3(v.giacenze.reduce((s, g) => s + g.quantita - g.riservata, 0));
    if (disp < v.prodotto.scortaMinima) nuovi.push({
      tipo: "scorta_bassa", gravita: disp <= 0 ? "critico" : "attenzione", entita: "prodotto", entitaId: v.prodottoId, chiave: `scorta:${v.id}`,
      titolo: `${disp <= 0 ? "Esaurito" : "Scorta bassa"}: ${v.prodotto.nome}${v.nome !== "Standard" ? ` (${v.nome})` : ""}`,
      messaggio: `Disponibili ${disp} ${v.prodotto.unita}, scorta minima ${v.prodotto.scortaMinima}.`,
    });
  }

  // riordino suggerito (un solo avviso riepilogativo)
  const sugg = await suggerimentiRiordino(aziendaId);
  if (sugg.length) nuovi.push({
    tipo: "riordino", gravita: sugg.some((s) => s.urgenza === "alta") ? "attenzione" : "info", chiave: `riordino:${giornoDi(ora)}`,
    titolo: `${sugg.length} ${sugg.length === 1 ? "articolo da riordinare" : "articoli da riordinare"}`,
    messaggio: `Il motore di riordino suggerisce acquisti per ${sugg.slice(0, 3).map((s) => s.sku).join(", ")}${sugg.length > 3 ? "…" : ""}.`,
  });

  // fatture scadute
  const scadute = await db.scadenza.findMany({
    where: { pagata: false, dataScadenza: { lt: ora }, documento: { aziendaId, tipo: "fattura", stato: { in: ["emessa"] } } },
    include: { documento: { include: { cliente: true } } },
  });
  for (const s of scadute) {
    const giorni = Math.floor((ora.getTime() - s.dataScadenza.getTime()) / GIORNO);
    nuovi.push({
      tipo: "fattura_scaduta", gravita: giorni > 30 ? "critico" : "attenzione", entita: "documento", entitaId: s.documentoId, chiave: `scaduta:${s.id}`,
      titolo: `Fattura ${s.documento.numero} scaduta da ${giorni} giorni`,
      messaggio: `${s.documento.cliente.ragioneSociale} deve ancora pagare ${(s.importo / 100).toLocaleString("it-IT", { style: "currency", currency: "EUR" })}.`,
    });
  }

  // lotti in scadenza entro 30 giorni ancora in magazzino
  const lotti = await db.lottoGiacenza.findMany({
    where: { quantita: { gt: 0 }, lotto: { scadenza: { lte: new Date(ora.getTime() + 30 * GIORNO) }, variante: { aziendaId } } },
    include: { lotto: { include: { variante: { include: { prodotto: true } } } }, magazzino: true },
  });
  for (const l of lotti) {
    const giorni = Math.ceil((l.lotto.scadenza!.getTime() - ora.getTime()) / GIORNO);
    nuovi.push({
      tipo: "lotto_in_scadenza", gravita: giorni <= 7 ? "critico" : "attenzione", entita: "prodotto", entitaId: l.lotto.variante.prodottoId,
      chiave: `lotto:${l.id}`, titolo: `Lotto ${l.lotto.codice} ${giorni < 0 ? "scaduto" : `in scadenza tra ${giorni} giorni`}`,
      messaggio: `${l.quantita} ${l.lotto.variante.prodotto.unita} di ${l.lotto.variante.prodotto.nome} a ${l.magazzino.nome}: valuta una promozione o un trasferimento.`,
    });
  }

  // anomalie: vendite dell'ultima settimana fuori dall'andamento delle 8 precedenti (|z| > 2,5)
  const top = await db.movimento.groupBy({
    by: ["varianteId"], where: { aziendaId, tipo: "vendita", createdAt: { gte: new Date(ora.getTime() - 63 * GIORNO) } },
    _sum: { quantita: true }, orderBy: { _sum: { quantita: "desc" } }, take: 15,
  });
  for (const t of top) {
    const serie = (await serieGiornaliera(aziendaId, { varianteId: t.varianteId, misura: "quantita", giorni: 63 })).map((p) => p.valore);
    const settimane = Array.from({ length: 9 }, (_, i) => serie.slice(i * 7, i * 7 + 7).reduce((a, b) => a + b, 0));
    const passate = settimane.slice(0, 8), ultima = settimane[8];
    const m = media(passate), sd = devStd(passate);
    if (m < 3 || sd === 0) continue;
    const z = (ultima - m) / sd;
    if (Math.abs(z) > 2.5) {
      const v = await db.variante.findUnique({ where: { id: t.varianteId }, include: { prodotto: true } });
      if (!v) continue;
      const pct = Math.round(((ultima - m) / m) * 100);
      nuovi.push({
        tipo: "anomalia_vendite", gravita: z < 0 ? "attenzione" : "info", entita: "prodotto", entitaId: v.prodottoId, chiave: `anomalia:${v.id}:${giornoDi(ora)}`,
        titolo: `${z > 0 ? "Picco" : "Calo"} anomalo di vendite: ${v.prodotto.nome}`,
        messaggio: `Ultimi 7 giorni: ${arrotonda3(ultima)} ${v.prodotto.unita} contro una media di ${arrotonda3(m)} (${pct > 0 ? "+" : ""}${pct}%).` +
          (z > 0 ? " Verifica le scorte per non restare senza merce." : " Verifica prezzi, disponibilità e visibilità del prodotto."),
      });
    }
  }

  // clienti importanti a rischio di abbandono
  const clienti = await analisiClienti(aziendaId);
  const soglia = [...clienti].sort((a, b) => b.valore - a.valore)[Math.floor(clienti.length * 0.25)]?.valore ?? Infinity;
  for (const c of clienti.filter((x) => x.segmento === "A rischio" && x.valore >= soglia)) nuovi.push({
    tipo: "cliente_a_rischio", gravita: "attenzione", entita: "cliente", entitaId: c.clienteId, chiave: `cliente:${c.clienteId}`,
    titolo: `Cliente importante a rischio: ${c.nome}`,
    messaggio: `Non ordina da ${c.recenzaGiorni} giorni (di solito ogni ${c.intervalloMedioGiorni ?? "?"}). ${c.consiglio}`,
  });

  // salvataggio con deduplica; gli alert la cui condizione è sparita si risolvono da soli
  const chiavi = new Set(nuovi.map((n) => n.chiave));
  for (const n of nuovi) {
    await db.alert.upsert({
      where: { aziendaId_chiave: { aziendaId, chiave: n.chiave } },
      create: { aziendaId, ...n },
      update: { titolo: n.titolo, messaggio: n.messaggio, gravita: n.gravita, risolto: null },
    });
  }
  const aperti = await db.alert.findMany({ where: { aziendaId, risolto: null } });
  const daRisolvere = aperti.filter((a) => !chiavi.has(a.chiave)).map((a) => a.id);
  if (daRisolvere.length) await db.alert.updateMany({ where: { id: { in: daRisolvere } }, data: { risolto: ora } });

  // riordino automatico: se attivo, crea le bozze d'ordine (senza duplicare quelle già aperte)
  const azienda = await db.azienda.findUnique({ where: { id: aziendaId } });
  let ordiniCreati = 0;
  if (azienda?.riordinoAutomatico && sugg.length) {
    const aperteAi = await db.ordineAcquisto.count({ where: { aziendaId, generatoDaAi: true, stato: "bozza" } });
    if (aperteAi === 0) ordiniCreati = (await generaRiordini(aziendaId)).ordini.length;
  }
  return { attivi: nuovi.length, risolti: daRisolvere.length, ordiniCreati };
}

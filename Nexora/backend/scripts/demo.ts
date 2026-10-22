// Azienda dimostrativa "Bottega Aurora S.r.l." con un anno di attività simulata giorno per giorno:
// vendite multicanale con stagionalità, spedizioni e DDT, fatture e incassi, acquisti con riordino
// e lotti, preventivi, note di credito, integrazioni e alert. Usa gli stessi servizi dell'app.
//   npm run demo            crea l'azienda se manca
//   npm run demo -- --reset la ricrea da zero
// © Roberto Di Flumeri
import { copyFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { BASE_DIR, config } from "../src/lib/config.js";
import { cifra, tokenCasuale } from "../src/lib/crypto.js";
import { db } from "../src/lib/db.js";
import { hashPassword } from "../src/lib/sicurezza.js";
import { creaAcquisto, inviaAcquisto, riceviAcquisto } from "../src/services/acquisti.js";
import { aggiornaAlert } from "../src/services/ai.js";
import { creaDocumento, emetti, registraPagamento } from "../src/services/documenti.js";
import { movimenta } from "../src/services/magazzino.js";
import { cambiaStato, creaOrdine, fatturaDaOrdine } from "../src/services/ordini.js";

const PASSWORD = "NexoraDemo2026";
const GIORNO = 86400_000;

// generatore pseudo-casuale riproducibile (mulberry32)
let seme = 20261021;
function caso() {
  seme |= 0; seme = (seme + 0x6d2b79f5) | 0;
  let t = Math.imul(seme ^ (seme >>> 15), 1 | seme);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const intero = (min: number, max: number) => min + Math.floor(caso() * (max - min + 1));
const scegli = <T>(v: T[]) => v[Math.floor(caso() * v.length)];
const pesato = <T>(v: { x: T; p: number }[]) => { let r = caso() * v.reduce((s, e) => s + e.p, 0); for (const e of v) { r -= e.p; if (r <= 0) return e.x; } return v[v.length - 1].x; };

function piva(): string {
  const b = Array.from({ length: 10 }, (_, i) => (i === 0 ? intero(0, 9) : intero(0, 9)));
  const s = b.reduce((acc, c, i) => acc + (i % 2 === 1 ? (c * 2 > 9 ? c * 2 - 9 : c * 2) : c), 0);
  return b.join("") + ((10 - (s % 10)) % 10);
}
function codiceFiscale(): string {
  const L = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  const mesi = "ABCDEHLMPRST";
  const base = Array.from({ length: 6 }, () => scegli([...L])).join("") + String(intero(50, 99)) + scegli([...mesi]) + String(intero(10, 28)) + "F205";
  const dispari: Record<string, number> = { 0: 1, 1: 0, 2: 5, 3: 7, 4: 9, 5: 13, 6: 15, 7: 17, 8: 19, 9: 21, A: 1, B: 0, C: 5, D: 7, E: 9, F: 13, G: 15, H: 17, I: 19,
    J: 21, K: 2, L: 4, M: 18, N: 20, O: 11, P: 3, Q: 6, R: 8, S: 12, T: 14, U: 16, V: 10, W: 22, X: 25, Y: 24, Z: 23 };
  let s = 0;
  for (let i = 0; i < 15; i++) s += i % 2 === 0 ? dispari[base[i]] : /\d/.test(base[i]) ? Number(base[i]) : base.charCodeAt(i) - 65;
  return base + String.fromCharCode(65 + (s % 26));
}
function ean13(prefisso: string): string {
  const d = prefisso.padEnd(12, "0").slice(0, 12).split("").map(Number);
  const s = d.reduce((acc, c, i) => acc + c * (i % 2 === 0 ? 1 : 3), 0);
  return d.join("") + ((10 - (s % 10)) % 10);
}

// ── Catalogo ────────────────────────────────────────────────────────────────

interface DefProdotto {
  sku: string; nome: string; cat: string; forn: string; prezzo: number; costo: number; iva: number; unita?: string; lotti?: boolean; seriali?: boolean;
  scorta: number; img: string; domanda: number; varianti?: { sku: string; nome: string; attributi: Record<string, string>; quota: number }[]; descrizione: string;
}
const PRODOTTI: DefProdotto[] = [
  { sku: "CAF-ORO-1KG", nome: "Caffè in grani Miscela Oro 1 kg", cat: "Caffè", forn: "vesuvio", prezzo: 2290, costo: 1150, iva: 22, unita: "conf", lotti: true, scorta: 40, img: "caffe-grani", domanda: 5.5,
    descrizione: "Miscela 80% arabica e 20% robusta, tostatura media, note di cioccolato e nocciola." },
  { sku: "CAF-MOKA-250", nome: "Caffè macinato per moka 250 g", cat: "Caffè", forn: "vesuvio", prezzo: 549, costo: 260, iva: 22, unita: "conf", lotti: true, scorta: 60, img: "caffe-macinato", domanda: 9,
    descrizione: "Macinatura media ideale per moka, confezione salvaroma con valvola." },
  { sku: "CAF-CAPS-50", nome: "Capsule compatibili Intenso x50", cat: "Caffè", forn: "vesuvio", prezzo: 1490, costo: 690, iva: 22, unita: "conf", lotti: true, scorta: 50, img: "capsule", domanda: 7,
    descrizione: "Capsule compostabili compatibili con le macchine più diffuse, intensità 10." },
  { sku: "CAF-DEK-500", nome: "Caffè decaffeinato in grani 500 g", cat: "Caffè", forn: "vesuvio", prezzo: 1190, costo: 580, iva: 22, unita: "conf", lotti: true, scorta: 15, img: "decaffeinato", domanda: 1.6,
    descrizione: "Decaffeinato con metodo ad acqua, gusto pieno senza caffeina." },
  { sku: "CAF-ETH-250", nome: "Specialty Etiopia Yirgacheffe 250 g", cat: "Caffè", forn: "vesuvio", prezzo: 1450, costo: 720, iva: 22, unita: "conf", lotti: true, scorta: 12, img: "specialty", domanda: 1.2,
    descrizione: "Monorigine lavato, note floreali e di agrumi. Tostatura chiara per filtro." },
  { sku: "MAC-PRO", nome: "Macchina espresso Aurora Pro", cat: "Macchine da caffè", forn: "elettro", prezzo: 34900, costo: 19500, iva: 22, seriali: true, scorta: 6, img: "macchina", domanda: 0.45,
    descrizione: "Pompa 15 bar, caldaia in acciaio, lancia vapore professionale. Garanzia 2 anni." },
  { sku: "MAC-MACINA", nome: "Macinacaffè elettrico a macine coniche", cat: "Macchine da caffè", forn: "elettro", prezzo: 8900, costo: 4600, iva: 22, seriali: true, scorta: 5, img: "macinacaffe", domanda: 0.35,
    descrizione: "40 gradi di macinatura, dall'espresso alla french press." },
  { sku: "MAC-LATTE", nome: "Montalatte elettrico", cat: "Macchine da caffè", forn: "elettro", prezzo: 5900, costo: 2900, iva: 22, scorta: 8, img: "montalatte", domanda: 0.6,
    descrizione: "Schiuma calda e fredda in 90 secondi, interno antiaderente." },
  { sku: "ACC-MOKA6", nome: "Moka in alluminio 6 tazze", cat: "Accessori", forn: "elettro", prezzo: 2900, costo: 1250, iva: 22, scorta: 15, img: "moka", domanda: 1.4,
    descrizione: "La classica caffettiera italiana, manico ergonomico." },
  { sku: "ACC-FILTRI", nome: "Filtri in carta V60 x100", cat: "Accessori", forn: "elettro", prezzo: 690, costo: 250, iva: 22, unita: "conf", scorta: 20, img: "filtri", domanda: 1.8,
    descrizione: "Filtri sbiancati senza cloro per metodo pour-over." },
  { sku: "ACC-BILANCIA", nome: "Bilancia da barista con timer", cat: "Accessori", forn: "elettro", prezzo: 3900, costo: 1700, iva: 22, scorta: 6, img: "bilancia", domanda: 0.5,
    descrizione: "Precisione 0,1 g e timer integrato per estrazioni perfette." },
  { sku: "ACC-PULIZIA", nome: "Kit pulizia macchina espresso", cat: "Accessori", forn: "elettro", prezzo: 1590, costo: 620, iva: 22, scorta: 12, img: "kit-pulizia", domanda: 1.1,
    descrizione: "Decalcificante, pastiglie detergenti e spazzolino per gruppo erogatore." },
  { sku: "TAZ-ESPRESSO6", nome: "Set 6 tazzine da espresso", cat: "Tazze e mug", forn: "deruta", prezzo: 2400, costo: 980, iva: 22, scorta: 10, img: "tazzine", domanda: 1.3,
    descrizione: "Porcellana dipinta a mano dalla ceramica di Deruta." },
  { sku: "TAZ-MUG", nome: "Mug Aurora 350 ml", cat: "Tazze e mug", forn: "deruta", prezzo: 1200, costo: 450, iva: 22, scorta: 8, img: "mug", domanda: 2.4,
    descrizione: "Mug in gres smaltato, lavabile in lavastoviglie.",
    varianti: [{ sku: "TAZ-MUG-BLU", nome: "Blu notte", attributi: { colore: "Blu notte" }, quota: 0.45 }, { sku: "TAZ-MUG-VIO", nome: "Viola", attributi: { colore: "Viola" }, quota: 0.35 },
      { sku: "TAZ-MUG-BIA", nome: "Bianco", attributi: { colore: "Bianco" }, quota: 0.2 }] },
  { sku: "ABB-TSHIRT", nome: "T-shirt Aurora in cotone bio", cat: "Abbigliamento", forn: "prato", prezzo: 1990, costo: 780, iva: 22, scorta: 6, img: "tshirt", domanda: 1.6,
    descrizione: "Cotone biologico certificato, stampa a base d'acqua.",
    varianti: ["S", "M", "L", "XL"].map((t, i) => ({ sku: `ABB-TSHIRT-${t}`, nome: `Taglia ${t}`, attributi: { taglia: t }, quota: [0.2, 0.35, 0.3, 0.15][i] })) },
  { sku: "ABB-GREMBIULE", nome: "Grembiule da barista in denim", cat: "Abbigliamento", forn: "prato", prezzo: 3490, costo: 1450, iva: 22, scorta: 5, img: "grembiule", domanda: 0.4,
    descrizione: "Denim resistente con tasche porta-attrezzi e lacci in cuoio." },
  { sku: "DOL-CANTUCCI", nome: "Cantucci alle mandorle 250 g", cat: "Dolci e snack", forn: "dolciaria", prezzo: 490, costo: 210, iva: 10, unita: "conf", lotti: true, scorta: 30, img: "cantucci", domanda: 4,
    descrizione: "Ricetta toscana tradizionale, perfetti con il caffè." },
  { sku: "DOL-CIOCCO", nome: "Cioccolatini fondenti al caffè 200 g", cat: "Dolci e snack", forn: "dolciaria", prezzo: 790, costo: 340, iva: 10, unita: "conf", lotti: true, scorta: 20, img: "cioccolatini", domanda: 2.6,
    descrizione: "Cioccolato fondente 70% con cuore di caffè macinato." },
  { sku: "DOL-BISCOTTI", nome: "Biscotti al caffè 300 g", cat: "Dolci e snack", forn: "dolciaria", prezzo: 450, costo: 190, iva: 10, unita: "conf", lotti: true, scorta: 25, img: "biscotti", domanda: 3,
    descrizione: "Frollini con farina di caffè, senza olio di palma." },
  { sku: "DOL-SCIROPPO", nome: "Sciroppo al caramello 700 ml", cat: "Dolci e snack", forn: "dolciaria", prezzo: 990, costo: 430, iva: 10, unita: "pz", lotti: true, scorta: 10, img: "sciroppo", domanda: 0.9,
    descrizione: "Per cappuccini e caffè aromatizzati, flacone con dosatore." },
  { sku: "ACC-BORRACCIA", nome: "Borraccia termica 500 ml", cat: "Accessori", forn: "elettro", prezzo: 2490, costo: 990, iva: 22, scorta: 10, img: "borraccia", domanda: 0.9,
    descrizione: "Acciaio inox a doppia parete, mantiene il caffè caldo per 12 ore." },
  { sku: "GIFT-BOX", nome: "Confezione regalo Degustazione", cat: "Caffè", forn: "vesuvio", prezzo: 3900, costo: 1800, iva: 22, unita: "conf", lotti: true, scorta: 10, img: "gift", domanda: 0.7,
    descrizione: "Tre caffè monorigine e una moka, in scatola regalo." },
];

const FORNITORI: Record<string, { ragioneSociale: string; citta: string; provincia: string; leadTimeGiorni: number; email: string }> = {
  vesuvio: { ragioneSociale: "Torrefazione Vesuvio S.r.l.", citta: "Napoli", provincia: "NA", leadTimeGiorni: 6, email: "ordini@torrefazionevesuvio.example" },
  elettro: { ragioneSociale: "ElettroCasa Distribuzione S.p.A.", citta: "Bologna", provincia: "BO", leadTimeGiorni: 12, email: "b2b@elettrocasa.example" },
  deruta: { ragioneSociale: "Ceramiche di Deruta S.n.c.", citta: "Deruta", provincia: "PG", leadTimeGiorni: 18, email: "vendite@ceramichederuta.example" },
  prato: { ragioneSociale: "Tessile Prato Bio S.r.l.", citta: "Prato", provincia: "PO", leadTimeGiorni: 10, email: "info@tessileprato.example" },
  dolciaria: { ragioneSociale: "Dolciaria Siciliana S.r.l.", citta: "Catania", provincia: "CT", leadTimeGiorni: 5, email: "ordini@dolciariasiciliana.example" },
};

const CLIENTI_B2B = [
  ["Bar Centrale di Rossi Luca", "Milano", "MI", "Via Dante 12"], ["Caffè degli Artisti S.n.c.", "Milano", "MI", "Corso Garibaldi 45"],
  ["Hotel Bellavista S.r.l.", "Como", "CO", "Lungolago Trento 3"], ["Pasticceria Dolce Vita S.r.l.", "Bergamo", "BG", "Via XX Settembre 8"],
  ["Ristorante Da Marco S.a.s.", "Torino", "TO", "Via Po 21"], ["Coworking Hub Navigli S.r.l.", "Milano", "MI", "Ripa di Porta Ticinese 55"],
  ["Studio Legale Bianchi & Associati", "Milano", "MI", "Via Montenapoleone 9"], ["Bar Sport Monza S.n.c.", "Monza", "MB", "Piazza Trento 1"],
  ["Gelateria Polare S.r.l.", "Varese", "VA", "Via Sacco 14"], ["Hotel Duomo Firenze S.p.A.", "Firenze", "FI", "Piazza del Duomo 4"],
  ["Enoteca Il Calice S.r.l.", "Verona", "VR", "Via Mazzini 30"], ["Agriturismo La Quercia", "Siena", "SI", "Strada di Pievescola 7"],
  ["Caffetteria Universitaria S.r.l.", "Padova", "PD", "Via del Santo 18"], ["Uffici Tecnoservice S.p.A.", "Brescia", "BS", "Via Triumplina 60"],
  ["Bistrot Napoletano S.r.l.", "Napoli", "NA", "Via Toledo 120"], ["Panificio Forno Antico", "Genova", "GE", "Via San Lorenzo 5"],
  ["Palestra FitLife S.S.D.", "Milano", "MI", "Viale Monza 88"], ["Hotel Riviera S.r.l.", "Rimini", "RN", "Viale Vespucci 40"],
];
const NOMI = ["Anna", "Marco", "Giulia", "Luca", "Sara", "Paolo", "Chiara", "Davide", "Elena", "Francesco", "Martina", "Alessandro", "Federica", "Simone", "Valentina", "Matteo", "Laura", "Andrea", "Silvia", "Stefano", "Irene", "Giorgio"];
const COGNOMI = ["Rossi", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "De Luca", "Mancini", "Costa", "Giordano", "Rizzo", "Lombardi", "Moretti", "Barbieri"];
const CITTA = [["Milano", "MI", "20121"], ["Roma", "RM", "00185"], ["Torino", "TO", "10121"], ["Napoli", "NA", "80132"], ["Bologna", "BO", "40121"], ["Firenze", "FI", "50122"],
  ["Genova", "GE", "16121"], ["Venezia", "VE", "30121"], ["Bari", "BA", "70121"], ["Palermo", "PA", "90133"], ["Verona", "VR", "37121"], ["Trento", "TN", "38122"]];
const VIE = ["Via Roma", "Via Garibaldi", "Corso Italia", "Via Mazzini", "Via Verdi", "Viale Europa", "Via Cavour", "Piazza della Repubblica"];

async function elimina() {
  const a = await db.azienda.findFirst({ where: { utenti: { some: { email: "admin@nexora.demo" } } } });
  if (a) await db.azienda.delete({ where: { id: a.id } }); // tutto il resto segue a cascata
}

async function main() {
  if (process.argv.includes("--reset")) await elimina();
  else if (await db.utente.findUnique({ where: { email: "admin@nexora.demo" } })) {
    console.log(`Azienda dimostrativa già presente (admin@nexora.demo / ${PASSWORD}): usa --reset per ricrearla`);
    return;
  }
  const inizioStorico = new Date(new Date().setHours(9, 0, 0, 0) - 365 * GIORNO);
  console.log("Creo l'azienda dimostrativa…");
  const azienda = await db.azienda.create({ data: {
    ragioneSociale: "Bottega Aurora S.r.l.", partitaIva: "12485670961", codiceFiscale: "12485670961", indirizzo: "Via Paolo Sarpi 18", cap: "20154", citta: "Milano",
    provincia: "MI", email: "amministrazione@bottegaaurora.example", pec: "bottegaaurora@pec.example", telefono: "02 1234 5678", iban: "IT60X0542811101000000123456",
    riordinoAutomatico: false, createdAt: inizioStorico,
  } });
  const az = azienda.id;
  const hash = await hashPassword(PASSWORD);
  for (const [email, nome, ruolo] of [["admin@nexora.demo", "Giulia Ferri", "admin"], ["vendite@nexora.demo", "Marco Conti", "vendite"],
    ["magazzino@nexora.demo", "Luca Bassi", "magazzino"], ["contabilita@nexora.demo", "Sara Neri", "contabilita"], ["direzione@nexora.demo", "Paolo Greco", "lettura"]])
    await db.utente.create({ data: { aziendaId: az, email, nome, ruolo, passwordHash: hash, createdAt: inizioStorico, ultimoAccesso: new Date() } });
  const admin = (await db.utente.findUniqueOrThrow({ where: { email: "admin@nexora.demo" } })).id;

  const [mi, na, neg] = await Promise.all([
    db.magazzino.create({ data: { aziendaId: az, codice: "MI", nome: "Milano - Centrale", indirizzo: "Via Paolo Sarpi 18", citta: "Milano", principale: true } }),
    db.magazzino.create({ data: { aziendaId: az, codice: "NA", nome: "Napoli - Hub Sud", indirizzo: "Via Ferrante Imparato 190", citta: "Napoli" } }),
    db.magazzino.create({ data: { aziendaId: az, codice: "BRE", nome: "Negozio Brera", indirizzo: "Via Solferino 7", citta: "Milano" } }),
  ]);
  const categorie: Record<string, string> = {};
  for (const c of [...new Set(PRODOTTI.map((p) => p.cat))]) categorie[c] = (await db.categoria.create({ data: { aziendaId: az, nome: c } })).id;
  const fornitori: Record<string, string> = {};
  for (const [k, f] of Object.entries(FORNITORI))
    fornitori[k] = (await db.fornitore.create({ data: { aziendaId: az, ...f, partitaIva: piva(), giorniPagamento: 60, indirizzo: `${scegli(VIE)} ${intero(1, 90)}`, cap: "00000", createdAt: inizioStorico } })).id;

  // prodotti, varianti e immagini
  mkdirSync(config.cartellaFile, { recursive: true });
  interface Articolo { varianteId: string; sku: string; def: DefProdotto; quota: number; prezzo: number }
  const articoli: Articolo[] = [];
  for (const [i, p] of PRODOTTI.entries()) {
    const varianti = p.varianti ?? [{ sku: p.sku, nome: "Standard", attributi: null as unknown as Record<string, string>, quota: 1 }];
    const prod = await db.prodotto.create({ data: {
      aziendaId: az, sku: p.sku, nome: p.nome, descrizione: p.descrizione, categoriaId: categorie[p.cat], unita: p.unita ?? "pz", prezzoVendita: p.prezzo, costoAcquisto: p.costo,
      aliquotaIva: p.iva, barcode: p.varianti ? null : ean13(`80412${String(i).padStart(4, "0")}`), gestioneLotti: !!p.lotti, gestioneSeriali: !!p.seriali,
      scortaMinima: p.scorta, leadTimeGiorni: FORNITORI[p.forn].leadTimeGiorni, fornitoreId: fornitori[p.forn], createdAt: inizioStorico,
      varianti: { create: varianti.map((v, j) => ({ aziendaId: az, sku: v.sku, nome: v.nome, attributi: v.attributi ? JSON.stringify(v.attributi) : null,
        barcode: p.varianti ? ean13(`80413${String(i).padStart(2, "0")}${j}`) : null })) },
    }, include: { varianti: true } });
    const file = `demo-${p.img}-${tokenCasuale(6)}.png`;
    copyFileSync(join(BASE_DIR, "assets", "demo", `${p.img}.png`), join(config.cartellaFile, file));
    await db.prodottoImmagine.create({ data: { prodottoId: prod.id, file } });
    for (const v of prod.varianti) articoli.push({ varianteId: v.id, sku: v.sku, def: p, quota: varianti.find((x) => x.sku === v.sku)!.quota, prezzo: p.prezzo });
  }

  // clienti: aziende (bar, hotel, uffici) e privati dell'e-commerce
  const b2b: Awaited<ReturnType<typeof db.cliente.create>>[] = [];
  for (const [nome, citta, prov, via] of CLIENTI_B2B) {
    b2b.push(await db.cliente.create({ data: {
      aziendaId: az, tipo: "azienda", ragioneSociale: nome, partitaIva: piva(), codiceDestinatario: scegli(["M5UXCR1", "KRRH6B9", "SUBM70N", "T9K4ZHO", "USAL8PV"]),
      email: `amministrazione@${nome.toLowerCase().replace(/[^a-z]+/g, "").slice(0, 16)}.example`, telefono: `0${intero(2, 9)} ${intero(1000, 9999)} ${intero(1000, 9999)}`,
      indirizzo: via, cap: String(intero(10000, 99999)), citta, provincia: prov, giorniPagamento: scegli([30, 30, 60, 60, 90]), scontoPercentuale: scegli([0, 0, 5, 5, 10]),
      createdAt: inizioStorico,
    } }));
  }
  const privati: typeof b2b = [];
  for (let i = 0; i < 70; i++) {
    const [citta, prov, cap] = scegli(CITTA);
    const nome = `${scegli(NOMI)} ${scegli(COGNOMI)}`;
    privati.push(await db.cliente.create({ data: {
      aziendaId: az, tipo: "privato", ragioneSociale: nome, codiceFiscale: codiceFiscale(), email: `${nome.toLowerCase().replace(/ /g, ".")}${i}@mail.example`,
      indirizzo: `${scegli(VIE)} ${intero(1, 150)}`, cap, citta, provincia: prov, giorniPagamento: 0, canale: scegli(["shopify", "shopify", "woocommerce"]), createdAt: inizioStorico,
    } }));
  }
  // un cliente importante che smette di ordinare da 5 mesi (per l'analisi del rischio di abbandono)
  const clienteRischio = b2b[2];
  const fedeli = b2b.slice(0, 6);

  // ── Giacenze iniziali (un anno fa) ─────────────────────────────────────────
  console.log("Carico le giacenze iniziali…");
  const tx0 = db;
  for (const a of articoli) {
    const quantita = Math.ceil(a.def.domanda * a.quota * 75 + a.def.scorta * a.quota);
    const lotto = a.def.lotti ? { codice: `L${inizioStorico.toISOString().slice(2, 7).replace("-", "")}-${a.def.sku.slice(4, 8)}`, scadenza: new Date(inizioStorico.getTime() + 300 * GIORNO) } : undefined;
    const seriali = a.def.seriali ? Array.from({ length: quantita }, (_, k) => `${a.def.sku.slice(4)}-${String(100000 + k)}`) : undefined;
    const mov = await movimenta(tx0 as never, { aziendaId: az, tipo: "carico", varianteId: a.varianteId, magazzinoId: mi.id, quantita, lotto, seriali, causale: "Giacenza iniziale", utenteId: admin });
    await db.movimento.updateMany({ where: { id: { in: mov.map((m) => m.id) } }, data: { createdAt: new Date(inizioStorico.getTime() - GIORNO) } });
    // il negozio e l'hub sud ricevono una parte della merce
    if (!a.def.seriali && !a.def.lotti && quantita > 10) {
      const t = await movimenta(tx0 as never, { aziendaId: az, tipo: "trasferimento", varianteId: a.varianteId, magazzinoId: mi.id, magazzinoDestId: scegli([na.id, neg.id]), quantita: Math.floor(quantita * 0.2), causale: "Rifornimento sede" });
      await db.movimento.updateMany({ where: { id: { in: t.map((m) => m.id) } }, data: { createdAt: inizioStorico } });
    }
  }

  // ── Simulazione giorno per giorno ──────────────────────────────────────────
  console.log("Simulo un anno di attività (può richiedere qualche minuto)…");
  const STAGIONE = [0.85, 0.8, 0.9, 0.95, 0.95, 0.9, 0.85, 0.55, 1.0, 1.05, 1.3, 1.6]; // gennaio … dicembre
  const SETTIMANA = [0.6, 0.95, 1.0, 1.0, 1.1, 1.35, 1.15]; // domenica … sabato
  const daRicevere: { id: string; quando: number }[] = [];
  const oggi = new Date(new Date().setHours(0, 0, 0, 0)).getTime();
  let ordiniCreati = 0;
  for (let t = inizioStorico.getTime(); t < oggi; t += GIORNO) {
    const giorno = new Date(t);
    const giorniAllaFine = Math.round((oggi - t) / GIORNO);
    const crescita = 0.85 + 0.35 * ((t - inizioStorico.getTime()) / (365 * GIORNO));
    const fattore = STAGIONE[giorno.getMonth()] * SETTIMANA[giorno.getDay()] * crescita;

    // ricevimenti degli ordini fornitore arrivati oggi
    for (const r of daRicevere.filter((x) => x.quando <= t)) {
      const oa = await db.ordineAcquisto.findUniqueOrThrow({ where: { id: r.id }, include: { righe: true } });
      if (oa.stato === "inviato") {
        const scad = new Date(t + 270 * GIORNO);
        await db.$transaction((tx) => riceviAcquisto(tx, az, oa.id, oa.righe.map((rg) => {
          const a = articoli.find((x) => x.varianteId === rg.varianteId)!;
          return { rigaId: rg.id, quantita: rg.quantita,
            lotto: a.def.lotti ? { codice: `L${giorno.toISOString().slice(2, 10).replace(/-/g, "")}-${a.def.sku.slice(4, 8)}`, scadenza: scad } : undefined,
            seriali: a.def.seriali ? Array.from({ length: rg.quantita }, (_, k) => `${a.def.sku.slice(4)}-${t.toString(36).slice(-4).toUpperCase()}${k}`) : undefined };
        }), admin, new Date(t + 10 * 3600_000)), { timeout: 60000 });
      }
    }
    daRicevere.splice(0, daRicevere.length, ...daRicevere.filter((x) => x.quando > t));

    // ordini del giorno
    const numero = Math.max(0, Math.round(fattore * 6.2 + (caso() - 0.5) * 3));
    for (let n = 0; n < numero; n++) {
      const b2bOrdine = caso() < 0.42;
      let c = b2bOrdine ? (caso() < 0.6 ? scegli(fedeli) : scegli(b2b)) : scegli(privati);
      if (c.id === clienteRischio.id && giorniAllaFine < 150) c = scegli(b2b.slice(3));
      const righe: { varianteId: string; quantita: number }[] = [];
      const nRighe = b2bOrdine ? intero(2, 5) : intero(1, 3);
      const usati = new Set<string>();
      for (let k = 0; k < nRighe; k++) {
        const a = pesato(articoli.map((x) => ({ x, p: x.def.domanda * x.quota * (x.def.cat === "Abbigliamento" && giorno.getMonth() === 11 ? 2 : 1) })));
        if (usati.has(a.varianteId)) continue;
        usati.add(a.varianteId);
        const q = a.def.seriali ? 1 : b2bOrdine ? intero(2, a.def.domanda > 3 ? 12 : 4) : intero(1, 2);
        const g = await db.giacenza.findUnique({ where: { magazzinoId_varianteId: { magazzinoId: mi.id, varianteId: a.varianteId } } });
        if (!g || g.quantita - g.riservata < q) continue; // fuori stock: la vendita si perde
        righe.push({ varianteId: a.varianteId, quantita: q });
      }
      if (!righe.length) continue;
      const canale = b2bOrdine ? "diretto" : c.canale;
      const ora = new Date(t + intero(8, 19) * 3600_000 + intero(0, 59) * 60_000);
      // gli ultimi giorni mostrano ordini ancora in lavorazione
      const fino = giorniAllaFine <= 1 ? scegli(["bozza", "confermato", "confermato"]) : giorniAllaFine <= 3 ? scegli(["confermato", "in_preparazione", "spedito"])
        : giorniAllaFine <= 6 ? scegli(["spedito", "consegnato"]) : "consegnato";
      await db.$transaction(async (tx) => {
        const o = await creaOrdine(tx, az, { clienteId: c.id, magazzinoId: mi.id, righe, canale, data: ora,
          riferimentoEsterno: canale === "diretto" ? null : `${canale === "shopify" ? "#" : "WC-"}${10000 + ordiniCreati}`,
          note: canale === "diretto" ? null : `Ordine importato da ${canale}` });
        ordiniCreati++;
        if (fino === "bozza") return;
        await cambiaStato(tx, az, o.id, "confermato", { data: ora });
        if (fino === "confermato") return;
        await cambiaStato(tx, az, o.id, "in_preparazione", { data: ora });
        if (fino === "in_preparazione") return;
        const spedito = new Date(ora.getTime() + intero(3, 26) * 3600_000);
        const { ddtId } = await cambiaStato(tx, az, o.id, "spedito", { data: spedito, utenteId: admin });
        await tx.movimento.updateMany({ where: { riferimentoId: o.id }, data: { createdAt: spedito } });
        if (ddtId) await tx.documento.update({ where: { id: ddtId }, data: { createdAt: spedito } });
        const corriere = canale === "diretto" && c.citta === "Milano" ? "ritiro" : scegli(["gls", "gls", "dhl"]);
        await tx.spedizione.create({ data: { ordineId: o.id, corriere, tracking: corriere === "ritiro" ? `RITIRO-${o.numero}` : corriere === "dhl" ? `JD0146${intero(10000000, 99999999)}${intero(100000, 999999)}` : String(intero(10000000000, 99999999999)),
          stato: fino === "consegnato" ? "consegnata" : "in_transito", modalita: "demo", createdAt: spedito,
          eventi: JSON.stringify([{ data: spedito, stato: "creata", descrizione: "Spedizione registrata" }]) } });
        await tx.ordineVendita.update({ where: { id: o.id }, data: { corriere } });
        if (fino === "consegnato") await cambiaStato(tx, az, o.id, "consegnato", { data: new Date(spedito.getTime() + intero(20, 60) * 3600_000) });
        // fattura all'atto della spedizione
        const f = await fatturaDaOrdine(tx, az, o.id, spedito);
        const fe = await emetti(tx, az, f.id, { rate: c.tipo === "azienda" && caso() < 0.2 ? 2 : 1 });
        await tx.documento.update({ where: { id: f.id }, data: { statoSdi: giorniAllaFine > 3 ? "consegnato" : "generato", nomeFileSdi: `IT${azienda.partitaIva}_${fe.progressivo!.toString(36).toUpperCase().padStart(5, "0")}.xml` } });
        // incassi: e-commerce pagato subito; aziende alla scadenza, con qualche ritardo
        for (const s of fe.scadenze) {
          const ritardo = c.tipo === "privato" ? 0 : intero(-5, 6) + (caso() < 0.02 ? 75 : 0);
          const pagamento = new Date(s.dataScadenza.getTime() + ritardo * GIORNO);
          if (pagamento.getTime() < oggi - GIORNO && !(c.id === b2b[7].id && giorniAllaFine < 120))
            await registraPagamento(tx, az, s.id, { metodo: c.tipo === "privato" ? (canale === "shopify" ? "stripe" : "paypal") : "bonifico", data: pagamento });
        }
      }, { timeout: 60000 });
    }

    // riordino: ogni lunedì e giovedì si ordina ai fornitori ciò che scende sotto 4 settimane di copertura
    if ([1, 4].includes(giorno.getDay()) && giorniAllaFine > 2) {
      const perFornitore = new Map<string, { varianteId: string; quantita: number }[]>();
      for (const a of articoli) {
        const g = await db.giacenza.findUnique({ where: { magazzinoId_varianteId: { magazzinoId: mi.id, varianteId: a.varianteId } } });
        const inArrivo = (await db.rigaAcquisto.findMany({ where: { varianteId: a.varianteId, ordine: { stato: { in: ["inviato", "parziale"] } } } })).reduce((s, r) => s + r.quantita - r.quantitaRicevuta, 0);
        const domanda = a.def.domanda * a.quota * 1.3;
        const copertura = ((g?.quantita ?? 0) - (g?.riservata ?? 0) + inArrivo) / Math.max(domanda, 0.01);
        // alcuni articoli restano volutamente scoperti alla fine, per mostrare il motore di riordino
        const trascurato = giorniAllaFine < 75 && ["CAF-DEK-500", "ACC-BILANCIA", "TAZ-MUG-VIO", "DOL-CIOCCO"].includes(a.sku);
        if (copertura < 28 && !trascurato) {
          const q = Math.ceil(domanda * 42);
          perFornitore.set(a.def.forn, [...(perFornitore.get(a.def.forn) || []), { varianteId: a.varianteId, quantita: q }]);
        }
      }
      for (const [forn, righe] of perFornitore) {
        const oa = await db.$transaction(async (tx) => {
          const o = await creaAcquisto(tx, az, { fornitoreId: fornitori[forn], magazzinoId: mi.id, righe, data: giorno });
          return inviaAcquisto(tx, az, o.id);
        });
        daRicevere.push({ id: oa.id, quando: t + FORNITORI[forn].leadTimeGiorni * GIORNO });
      }
    }
    if (giorno.getDate() === 1) console.log(`  ${giorno.toISOString().slice(0, 7)} · ordini finora: ${ordiniCreati}`);
  }

  // ── Situazioni particolari per la demo ─────────────────────────────────────
  console.log("Preparo preventivi, note di credito, lotti in scadenza e integrazioni…");
  const varianteDi = async (sku: string) => (await db.variante.findFirstOrThrow({ where: { aziendaId: az, sku } })).id;
  // preventivi: inviato, accettato (diventato ordine) e rifiutato
  for (const [i, stato] of ["inviato", "inviato", "rifiutato", "bozza"].entries()) {
    const c = b2b[10 + i];
    const v = await varianteDi(scegli(["MAC-PRO", "CAF-ORO-1KG", "TAZ-ESPRESSO6"]));
    const vr = await db.variante.findUniqueOrThrow({ where: { id: v }, include: { prodotto: true } });
    const d = await db.$transaction(async (tx) => {
      const doc = await creaDocumento(tx, az, { tipo: "preventivo", clienteId: c.id, data: new Date(oggi - intero(2, 20) * GIORNO), validoFino: new Date(oggi + 25 * GIORNO),
        righe: [{ varianteId: v, descrizione: vr.prodotto.nome, quantita: intero(2, 10), prezzoUnitario: vr.prodotto.prezzoVendita, sconto: 8, aliquotaIva: vr.prodotto.aliquotaIva }],
        note: "Prezzi riservati per fornitura annuale" });
      return stato === "bozza" ? doc : emetti(tx, az, doc.id);
    });
    if (stato === "rifiutato") await db.documento.update({ where: { id: d.id }, data: { stato } });
  }
  // note di credito per due resi
  const fatture = await db.documento.findMany({ where: { aziendaId: az, tipo: "fattura", clienteId: { in: privati.map((p) => p.id) } }, orderBy: { data: "desc" }, take: 30, include: { righe: true } });
  for (const f of [fatture[8], fatture[20]]) {
    await db.$transaction(async (tx) => {
      const r = f.righe[0];
      const nc = await creaDocumento(tx, az, { tipo: "nota_credito", clienteId: f.clienteId, documentoOrigineId: f.id, ordineId: f.ordineId, data: new Date(f.data.getTime() + 6 * GIORNO),
        note: `Reso merce della fattura ${f.numero}`, righe: [{ varianteId: r.varianteId, descrizione: r.descrizione, quantita: 1, unita: r.unita, prezzoUnitario: r.prezzoUnitario, sconto: r.sconto, aliquotaIva: r.aliquotaIva, naturaIva: r.naturaIva }] });
      await emetti(tx, az, nc.id, { resoInMagazzino: mi.id });
      await tx.documento.update({ where: { id: nc.id }, data: { statoSdi: "consegnato" } });
    });
  }
  // un lotto di cioccolatini in scadenza al negozio
  const ciocco = await varianteDi("DOL-CIOCCO");
  await movimenta(db as never, { aziendaId: az, tipo: "carico", varianteId: ciocco, magazzinoId: neg.id, quantita: 18, lotto: { codice: "L-PROMO-NATALE", scadenza: new Date(oggi + 12 * GIORNO) }, causale: "Rimanenze promozione" });
  // picco di vendite della borraccia nell'ultima settimana (per l'alert di anomalia)
  const borraccia = await varianteDi("ACC-BORRACCIA");
  await movimenta(db as never, { aziendaId: az, tipo: "carico", varianteId: borraccia, magazzinoId: mi.id, quantita: 60, causale: "Riassortimento" });
  for (let g = 1; g <= 6; g++) {
    const m = await movimenta(db as never, { aziendaId: az, tipo: "vendita", varianteId: borraccia, magazzinoId: mi.id, quantita: intero(3, 5), causale: "Vendita in negozio (picco)" });
    await db.movimento.updateMany({ where: { id: { in: m.map((x) => x.id) } }, data: { createdAt: new Date(oggi - g * GIORNO + 15 * 3600_000) } });
  }
  // integrazioni attive in modalità dimostrativa, con qualche evento registrato
  for (const tipo of ["shopify", "woocommerce", "stripe", "paypal", "gls", "dhl", "magento"]) {
    const i = await db.integrazione.create({ data: { aziendaId: az, tipo, attiva: tipo !== "magento", webhookSecret: cifra(tokenCasuale(24)), ultimaSincronizzazione: new Date(oggi - intero(1, 20) * 3600_000) } });
    if (["shopify", "woocommerce"].includes(tipo)) {
      const ordini = await db.ordineVendita.findMany({ where: { aziendaId: az, canale: tipo }, orderBy: { data: "desc" }, take: 8 });
      for (const o of ordini) await db.eventoIntegrazione.create({ data: { integrazioneId: i.id, direzione: "in", tipo: "orders/create", esito: "ok", dettagli: JSON.stringify({ ordine: o.numero, nuovo: true }), createdAt: o.data } });
      await db.eventoIntegrazione.create({ data: { integrazioneId: i.id, direzione: "out", tipo: "sincronizza_giacenze", esito: "ok", dettagli: JSON.stringify({ articoli: articoli.length, modalita: "demo" }), createdAt: new Date(oggi - 2 * 3600_000) } });
    }
    if (tipo === "stripe") await db.eventoIntegrazione.create({ data: { integrazioneId: i.id, direzione: "in", tipo: "checkout.session.completed", esito: "ok", createdAt: new Date(oggi - 5 * 3600_000) } });
  }
  // un client API per l'integrazione con un gestionale esterno
  await db.apiClient.create({ data: { aziendaId: az, nome: "Connettore contabilità esterna", clientId: `nx_${tokenCasuale(12)}`, secretHash: await hashPassword(tokenCasuale(32)),
    scopes: "documenti:lettura clienti:lettura", ultimoUso: new Date(oggi - 3 * 3600_000) } });
  await db.auditLog.create({ data: { aziendaId: az, utenteId: admin, azione: "dati_dimostrativi_creati", dettagli: `${ordiniCreati} ordini simulati` } });

  console.log("Calcolo alert e suggerimenti del motore AI…");
  await aggiornaAlert(az);
  const conteggi = {
    ordini: await db.ordineVendita.count({ where: { aziendaId: az } }), fatture: await db.documento.count({ where: { aziendaId: az, tipo: "fattura" } }),
    acquisti: await db.ordineAcquisto.count({ where: { aziendaId: az } }), movimenti: await db.movimento.count({ where: { aziendaId: az } }),
    alert: await db.alert.count({ where: { aziendaId: az, risolto: null } }),
  };
  console.log("Azienda dimostrativa pronta:", conteggi);
  for (const [email, ruolo] of [["admin@nexora.demo", "amministratore"], ["vendite@nexora.demo", "vendite"], ["magazzino@nexora.demo", "magazzino"],
    ["contabilita@nexora.demo", "contabilità"], ["direzione@nexora.demo", "sola lettura"]]) console.log(`  ${email.padEnd(26)} ${PASSWORD}  (${ruolo})`);
}

main().then(() => db.$disconnect()).catch(async (e) => { console.error(e); await db.$disconnect(); process.exit(1); });

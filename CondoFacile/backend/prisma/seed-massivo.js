// © Roberto Di Flumeri
/**
 * Seed massivo per test di carico e collaudo funzionale di CondoFacile.
 *
 * Aggiunge (senza cancellare nulla) 20 condomìni completi: ~750 condòmini con
 * account, 24 mesi di quote e pagamenti, ticket con note e interventi,
 * fornitori, scadenze, lavori, comunicazioni con letture, assemblee con
 * presenze/voti e documenti con file reali in uploads/documenti.
 *
 * Uso (dalla cartella backend):
 *   node prisma/seed-massivo.js          # esegue solo se i dati demo non ci sono già
 *   node prisma/seed-massivo.js --force  # aggiunge comunque un altro lotto
 *
 * Tutti gli account condòmino creati hanno password: password123
 */
const { PrismaClient } = require('@prisma/client');
const bcrypt = require('bcryptjs');
const fs = require('fs');
const path = require('path');

const prisma = new PrismaClient();

// ── Generatore pseudo-casuale riproducibile ──────────────────────────────────
let semeRng = 20261013;
const rnd = () => ((semeRng = (semeRng * 1103515245 + 12345) % 2147483648) / 2147483648);
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const chance = (p) => rnd() < p;
const giorni = (n) => n * 24 * 60 * 60 * 1000;

const N_CONDOMINI = 20;
const MESI_STORICO = 24;
const MARCATORE = 'Residenza Demo';

const NOMI = ['Mario', 'Giulia', 'Luca', 'Anna', 'Marco', 'Sara', 'Paolo', 'Elena', 'Andrea', 'Francesca',
  'Davide', 'Chiara', 'Matteo', 'Laura', 'Simone', 'Valentina', 'Giorgio', 'Martina', 'Stefano', 'Alessia'];
const COGNOMI = ['Rossi', 'Bianchi', 'Ferrari', 'Russo', 'Esposito', 'Romano', 'Colombo', 'Ricci', 'Marino',
  'Greco', 'Bruno', 'Gallo', 'Conti', 'De Luca', 'Costa', 'Giordano', 'Mancini', 'Rizzo', 'Lombardi', 'Moretti'];
const VIE = ['Via Roma', 'Corso Italia', 'Via Garibaldi', 'Via Mazzini', 'Viale dei Mille', 'Via Verdi',
  'Piazza Duomo', 'Via Manzoni', 'Via Cavour', 'Via Dante'];
const CITTA = ['Milano', 'Roma', 'Torino', 'Napoli', 'Bologna', 'Firenze', 'Genova', 'Bari', 'Verona', 'Padova'];
const NOMI_RESIDENZA = ['dei Fiori', 'del Sole', 'Le Querce', 'Belvedere', 'Il Parco', 'San Marco',
  'dei Tigli', 'Aurora', 'Le Magnolie', 'Bellavista'];

const CATEGORIE_TICKET = ['idraulica', 'elettrica', 'pulizie', 'sicurezza', 'strutturale', 'altro'];
const TITOLI_TICKET = {
  idraulica: ['Perdita d\'acqua in cantina', 'Infiltrazione dal soffitto', 'Scarico intasato', 'Autoclave rumorosa'],
  elettrica: ['Luce scale non funzionante', 'Citofono guasto', 'Cancello automatico bloccato', 'Blackout parziale'],
  pulizie: ['Androne sporco', 'Rifiuti abbandonati in cortile', 'Vetri portone da pulire'],
  sicurezza: ['Portone non si chiude', 'Telecamera spenta', 'Estintore scaduto'],
  strutturale: ['Crepa sul muro esterno', 'Distacco intonaco balcone', 'Gradino rotto'],
  altro: ['Rumori molesti', 'Parcheggio occupato abusivamente', 'Richiesta informazioni'],
};
const STATI_TICKET = ['Nuova', 'In lavorazione', 'Assegnata', 'Risolta', 'Chiusa'];
const PRIORITA = ['bassa', 'media', 'media', 'alta', 'urgente'];
const TIPI_FORNITORE = ['idraulico', 'elettricista', 'impresa pulizie', 'edile', 'ascensorista', 'giardiniere', 'generico'];
const TIPI_COMUNICAZIONE = ['avviso', 'assemblea', 'manutenzione', 'emergenza', 'circolare'];
const CATEGORIE_DOC = ['regolamento', 'verbali', 'fatture_pagamenti', 'contratti', 'certificazioni', 'polizze', 'planimetrie'];
const METODI = ['bonifico', 'contanti', 'assegno', 'pos', 'altro'];

/** Ripartizione al centesimo (metodo dei resti maggiori): la somma è esatta. */
function ripartisci(totale, pesi) {
  const cent = Math.round(totale * 100);
  const somma = pesi.reduce((a, b) => a + b, 0);
  const esatte = pesi.map((w) => (somma > 0 ? (cent * w) / somma : cent / pesi.length));
  const parti = esatte.map(Math.floor);
  let resto = cent - parti.reduce((a, b) => a + b, 0);
  const ordine = esatte.map((q, i) => ({ i, f: q - Math.floor(q) })).sort((a, b) => b.f - a.f);
  for (let k = 0; resto > 0; k = (k + 1) % ordine.length, resto--) parti[ordine[k].i]++;
  return parti.map((c) => c / 100);
}

/** PDF minimale valido (una pagina con una riga di testo). */
function pdfMinimo(testo) {
  const stream = `BT /F1 14 Tf 60 780 Td (${testo.replace(/[()\\]/g, '')}) Tj ET`;
  const oggetti = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let out = '%PDF-1.4\n';
  const offs = [];
  oggetti.forEach((o, i) => { offs.push(out.length); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = out.length;
  out += `xref\n0 ${oggetti.length + 1}\n0000000000 65535 f \n`;
  out += offs.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  out += `trailer\n<< /Size ${oggetti.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}

async function main() {
  const force = process.argv.includes('--force');
  const esistenti = await prisma.condominio.count({ where: { nome: { startsWith: MARCATORE } } });
  if (esistenti > 0 && !force) {
    console.log(`Dati demo già presenti (${esistenti} condomìni "${MARCATORE}"). Usa --force per aggiungerne altri.`);
    return;
  }

  const t0 = Date.now();
  const oggi = new Date();
  const passwordHash = await bcrypt.hash('password123', 10);
  const dirDoc = path.join(__dirname, '..', 'uploads', 'documenti');
  fs.mkdirSync(dirDoc, { recursive: true });
  const lotto = Date.now().toString(36);
  const conteggi = { condomini: 0, condomini_persone: 0, utenti: 0, quote: 0, pagamenti: 0, ticket: 0, note: 0,
    interventi: 0, fornitori: 0, scadenze: 0, lavori: 0, comunicazioni: 0, letture: 0, assemblee: 0,
    presenze: 0, punti: 0, voti: 0, documenti: 0 };
  const esempiLogin = [];

  // Fornitori globali (condivisi tra condomìni)
  for (let i = 0; i < 20; i++) {
    const tipo = pick(TIPI_FORNITORE);
    await prisma.fornitore.create({
      data: {
        nome: `${pick(COGNOMI)} ${tipo === 'generico' ? 'Servizi' : tipo.charAt(0).toUpperCase() + tipo.slice(1)} Srl`,
        tipo, email: `info${i}.${lotto}@fornitori-demo.it`, telefono: `+39 02 ${int(1000000, 9999999)}`,
        indirizzo: `${pick(VIE)} ${int(1, 120)}, ${pick(CITTA)}`,
      },
    });
    conteggi.fornitori++;
  }
  const fornitoriGlobali = await prisma.fornitore.findMany({ where: { condominioId: null } });

  for (let ci = 0; ci < N_CONDOMINI; ci++) {
    const condo = await prisma.condominio.create({
      data: {
        nome: `${MARCATORE} ${NOMI_RESIDENZA[ci % NOMI_RESIDENZA.length]} ${Math.floor(ci / NOMI_RESIDENZA.length) + 1}`,
        indirizzo: `${pick(VIE)} ${int(1, 200)}, ${pick(CITTA)}`,
      },
    });
    conteggi.condomini++;

    // ── Condòmini (millesimi che sommano a 1000) ──────────────────────────
    const nUnita = int(25, 50);
    const pesi = Array.from({ length: nUnita }, () => int(10, 60));
    const millesimi = ripartisci(1000, pesi);
    const persone = Array.from({ length: nUnita }, (_, u) => ({
      nome: pick(NOMI), cognome: pick(COGNOMI),
      unita: `Scala ${String.fromCharCode(65 + Math.floor(u / 12))} - Int. ${(u % 12) + 1}`,
      millesimi: millesimi[u],
      tipo: chance(0.75) ? 'proprietario' : 'inquilino',
      stato: chance(0.95) ? 'attivo' : 'disattivo',
      email: null, telefono: `+39 3${int(20, 99)} ${int(1000000, 9999999)}`,
      condominioId: condo.id,
    }));
    persone.forEach((p, u) => { p.email = `${p.nome}.${p.cognome}.${condo.id}.${u}@example.com`.toLowerCase().replace(/ /g, ''); });
    await prisma.condomino.createMany({ data: persone });
    const condomini = await prisma.condomino.findMany({ where: { condominioId: condo.id }, orderBy: { id: 'asc' } });
    conteggi.condomini_persone += condomini.length;

    // Account utente per ~70% dei condòmini
    const utenti = condomini.filter(() => chance(0.7)).map((c) => ({
      username: `${c.nome}.${c.cognome}.${c.id}`.toLowerCase().replace(/ /g, ''),
      passwordHash, role: 'CONDOMINO', condominoId: c.id, nome: c.nome, cognome: c.cognome, email: c.email,
    }));
    await prisma.user.createMany({ data: utenti });
    conteggi.utenti += utenti.length;
    if (esempiLogin.length < 5 && utenti[0]) esempiLogin.push(`${utenti[0].username} (${condo.nome})`);

    const attivi = condomini.filter((c) => c.stato === 'attivo');

    // ── Quote mensili + pagamenti ripartiti per millesimi ────────────────
    const importoMensile = int(30, 90) * 100;
    for (let m = MESI_STORICO - 1; m >= 0; m--) {
      const d = new Date(oggi.getFullYear(), oggi.getMonth() - m, 1);
      const quota = await prisma.quotaMensile.create({
        data: { mese: d.getMonth() + 1, anno: d.getFullYear(), importoTotale: importoMensile, tipo: 'collettiva', condominioId: condo.id },
      });
      conteggi.quote++;
      const importi = ripartisci(importoMensile, attivi.map((c) => c.millesimi));
      const pagamenti = attivi.map((c, i) => {
        // mesi vecchi quasi tutti pagati; gli ultimi due in parte in attesa; pochi morosi cronici
        const moroso = c.id % 17 === 0;
        let stato = 'pagato';
        if (moroso && m < 8) stato = 'in_mora';
        else if (m <= 1 && chance(0.45)) stato = 'in_attesa';
        else if (m <= 4 && chance(0.06)) stato = 'in_mora';
        return {
          condominoId: c.id, quotaId: quota.id, importo: importi[i], stato,
          dataPagamento: stato === 'pagato' ? new Date(d.getTime() + giorni(int(1, 25))) : null,
          metodoPagamento: stato === 'pagato' ? pick(METODI) : null,
        };
      });
      await prisma.pagamentoQuota.createMany({ data: pagamenti });
      conteggi.pagamenti += pagamenti.length;
    }
    // qualche quota personale (es. riparazione a carico del singolo)
    for (let k = 0; k < 5; k++) {
      const c = pick(attivi);
      const d = new Date(oggi.getFullYear(), oggi.getMonth() - int(0, 11), 1);
      const esiste = await prisma.quotaMensile.findFirst({
        where: { condominioId: condo.id, mese: d.getMonth() + 1, anno: d.getFullYear(), tipo: 'personale', destinatarioId: c.id },
      });
      if (esiste) continue;
      const importo = int(50, 600);
      const q = await prisma.quotaMensile.create({
        data: { mese: d.getMonth() + 1, anno: d.getFullYear(), importoTotale: importo, tipo: 'personale', destinatarioId: c.id, condominioId: condo.id },
      });
      await prisma.pagamentoQuota.create({ data: { condominoId: c.id, quotaId: q.id, importo, stato: pick(['pagato', 'in_attesa']) } });
      conteggi.quote++; conteggi.pagamenti++;
    }

    // ── Fornitori del condominio ──────────────────────────────────────────
    const fornLocali = [];
    for (let k = 0; k < 3; k++) {
      const tipo = pick(TIPI_FORNITORE);
      fornLocali.push(await prisma.fornitore.create({
        data: { nome: `${pick(COGNOMI)} & C. (${tipo})`, tipo, condominioId: condo.id, telefono: `+39 06 ${int(1000000, 9999999)}` },
      }));
      conteggi.fornitori++;
    }
    const fornitori = [...fornLocali, ...fornitoriGlobali];

    // ── Ticket con note e interventi ──────────────────────────────────────
    const nTicket = int(50, 100);
    for (let t = 0; t < nTicket; t++) {
      const categoria = pick(CATEGORIE_TICKET);
      const stato = pick(STATI_TICKET);
      const apertura = new Date(oggi.getTime() - giorni(int(0, 700)));
      const chiuso = stato === 'Risolta' || stato === 'Chiusa';
      const forn = stato !== 'Nuova' && chance(0.6) ? pick(fornitori) : null;
      const ticket = await prisma.ticket.create({
        data: {
          titolo: pick(TITOLI_TICKET[categoria]), descrizione: 'Segnalazione generata per test di carico.',
          categoria, priorita: pick(PRIORITA), stato, condominioId: condo.id,
          apertoCondominoId: chance(0.8) ? pick(attivi).id : null,
          dataApertura: apertura,
          dataChiusura: chiuso ? new Date(apertura.getTime() + giorni(int(1, 30))) : null,
          fornitoreId: forn?.id ?? null, assegnatoa: forn?.nome ?? null,
        },
      });
      conteggi.ticket++;
      const nNote = stato === 'Nuova' ? 0 : int(1, 3);
      for (let n = 0; n < nNote; n++) {
        await prisma.ticketNota.create({
          data: { ticketId: ticket.id, testo: pick(['Sopralluogo effettuato.', 'Richiesto preventivo.', 'Intervento programmato.', 'Problema risolto.']), autore: 'admin' },
        });
        conteggi.note++;
      }
      if (forn && chance(0.7)) {
        await prisma.intervento.create({
          data: { fornitoreId: forn.id, ticketId: ticket.id, descrizione: `Intervento: ${ticket.titolo}`,
            data: new Date(apertura.getTime() + giorni(int(1, 10))), costo: int(80, 2500) },
        });
        conteggi.interventi++;
      }
    }

    // ── Scadenze e lavori ─────────────────────────────────────────────────
    const scadenze = Array.from({ length: 10 }, () => ({
      descrizione: pick(['Rinnovo polizza fabbricato', 'Manutenzione ascensore', 'Verifica caldaia', 'Pagamento F24', 'Revisione estintori', 'Pulizia grondaie']),
      data: new Date(oggi.getTime() + giorni(int(-60, 120))),
      tipo: pick(['pagamento', 'manutenzione', 'assicurazione', 'scadenza']), condominioId: condo.id,
    }));
    await prisma.scadenza.createMany({ data: scadenze });
    conteggi.scadenze += scadenze.length;
    const lavori = Array.from({ length: 5 }, () => {
      const inizio = new Date(oggi.getTime() + giorni(int(-180, 30)));
      const progressione = pick([0, 15, 40, 65, 90, 100]);
      return {
        descrizione: pick(['Rifacimento facciata', 'Impermeabilizzazione tetto', 'Sostituzione caldaia', 'Tinteggiatura scale', 'Adeguamento ascensore']),
        fornitore: pick(fornitori).nome, dataInizio: inizio, dataFine: new Date(inizio.getTime() + giorni(int(20, 200))),
        stato: progressione === 100 ? 'Completato' : progressione === 0 ? 'Pianificato' : 'In corso', progressione,
        condominioId: condo.id,
      };
    });
    await prisma.lavoro.createMany({ data: lavori });
    conteggi.lavori += lavori.length;

    // ── Comunicazioni con letture ────────────────────────────────────────
    for (let k = 0; k < 15; k++) {
      const destinatariTipo = pick(['tutti', 'tutti', 'proprietari', 'inquilini']);
      const tipoCondomino = { proprietari: 'proprietario', inquilini: 'inquilino' }[destinatariTipo];
      const target = attivi.filter((c) => !tipoCondomino || c.tipo === tipoCondomino);
      const com = await prisma.comunicazione.create({
        data: {
          titolo: pick(['Interruzione acqua', 'Convocazione assemblea', 'Lavori in facciata', 'Nuovo regolamento', 'Raccolta differenziata']),
          corpo: 'Comunicazione generata per test di carico.', tipo: pick(TIPI_COMUNICAZIONE),
          data: new Date(oggi.getTime() - giorni(int(0, 365))), destinatari: target.length, destinatariTipo,
          condominioId: condo.id,
        },
      });
      conteggi.comunicazioni++;
      const letture = target.filter(() => chance(0.55)).map((c) => ({ comunicazioneId: com.id, condominoId: c.id }));
      await prisma.comunicazioneLettura.createMany({ data: letture });
      conteggi.letture += letture.length;
    }

    // ── Assemblee con presenze, punti OdG e voti ─────────────────────────
    for (let k = 0; k < 4; k++) {
      const passata = k < 3;
      const ass = await prisma.assemblea.create({
        data: {
          titolo: `Assemblea ${k === 3 ? 'straordinaria' : 'ordinaria'} ${2025 + Math.floor(k / 2)}`,
          data: new Date(oggi.getTime() + giorni(passata ? -int(30, 600) : int(10, 60))),
          luogo: 'Sala condominiale', tipo: k === 3 ? 'straordinaria' : 'ordinaria',
          stato: passata ? 'conclusa' : 'convocata', ordineDelGiorno: '1. Bilancio\n2. Lavori\n3. Varie ed eventuali',
          verbale: passata ? 'Verbale approvato all\'unanimità dei presenti.' : null, condominioId: condo.id,
        },
      });
      conteggi.assemblee++;
      const presenze = attivi.map((c) => ({ assembleaId: ass.id, condominoId: c.id, presente: passata && chance(0.6) }));
      await prisma.assembleaPresenza.createMany({ data: presenze });
      conteggi.presenze += presenze.length;
      for (let p = 0; p < int(3, 5); p++) {
        const votanti = passata ? attivi.filter(() => chance(0.6)) : [];
        const voti = votanti.map((c) => ({ condominoId: c.id, scelta: pick(['si', 'si', 'no', 'astenuto']) }));
        const somma = (s) => Math.round(voti.filter((v) => v.scelta === s)
          .reduce((t, v) => t + attivi.find((c) => c.id === v.condominoId).millesimi, 0) * 100) / 100;
        const votiSi = somma('si'); const votiNo = somma('no');
        const punto = await prisma.assembleaPuntoOdG.create({
          data: {
            assembleaId: ass.id, titolo: pick(['Approvazione bilancio consuntivo', 'Preventivo lavori facciata', 'Nomina amministratore', 'Regolamento parcheggi', 'Installazione colonnine ricarica']),
            ordine: p + 1, votiSi, votiNo, votiAstenuti: somma('astenuto'),
            esito: passata ? (votiSi > votiNo ? 'approvato' : 'respinto') : null,
          },
        });
        conteggi.punti++;
        await prisma.assembleaVoto.createMany({ data: voti.map((v) => ({ ...v, puntoOdGId: punto.id })) });
        conteggi.voti += voti.length;
      }
    }

    // ── Documenti con file reali ──────────────────────────────────────────
    for (let k = 0; k < 6; k++) {
      const categoria = CATEGORIE_DOC[k % CATEGORIE_DOC.length];
      const nomeFile = `seed-${lotto}-${condo.id}-${k}.pdf`;
      const contenuto = pdfMinimo(`${condo.nome} - ${categoria} - documento di prova ${k + 1}`);
      fs.writeFileSync(path.join(dirDoc, nomeFile), contenuto);
      const visibilita = pick(['pubblica', 'pubblica', 'privata', 'selettiva']);
      await prisma.documento.create({
        data: {
          nome: `${categoria.replace('_', ' ')} ${k + 1}`, categoria, tag: 'demo,test', visibilita,
          unitaAccesso: visibilita === 'selettiva' ? attivi.slice(0, 3).map((c) => c.unita).join(',') : '',
          filePath: path.join('uploads', 'documenti', nomeFile), fileSize: contenuto.length,
          mimeType: 'application/pdf', condominioId: condo.id,
        },
      });
      conteggi.documenti++;
    }
    process.stdout.write(`  condominio ${ci + 1}/${N_CONDOMINI} completato\r`);
  }

  console.log(`\nSeed massivo completato in ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  console.table(conteggi);
  console.log('Esempi di accesso condòmino (password: password123):');
  esempiLogin.forEach((e) => console.log(`  - ${e}`));
}

main()
  .catch((e) => { console.error(e); process.exitCode = 1; })
  .finally(() => prisma.$disconnect());

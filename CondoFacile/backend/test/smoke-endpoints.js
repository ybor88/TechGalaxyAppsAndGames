// © Roberto Di Flumeri
/**
 * Smoke test HTTP di tutti gli endpoint GET principali su un backend avviato
 * (default http://localhost:3001/api), come amministratore e come condòmino.
 * Segnala risposte non 2xx e tempi oltre la soglia.
 *
 *   node test/smoke-endpoints.js [baseUrl] [utenteCondomino] [password]
 */
const BASE = process.argv[2] ?? 'http://localhost:3001/api';
const SOGLIA_MS = 1500;

async function call(method, path, token, body) {
  const t0 = performance.now();
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const ms = performance.now() - t0;
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* risposta non JSON (es. file) */ }
  return { status: res.status, ms, json, size: text.length };
}

let problemi = 0;
async function check(label, method, path, token, atteso = 200) {
  const r = await call(method, path, token);
  const okStatus = Array.isArray(atteso) ? atteso.includes(r.status) : r.status === atteso;
  const lento = r.ms > SOGLIA_MS;
  if (!okStatus || lento) problemi++;
  const flag = !okStatus ? 'ERR ' : lento ? 'LENTO' : 'OK  ';
  const n = Array.isArray(r.json) ? `${r.json.length} elementi` : `${(r.size / 1024).toFixed(1)} KB`;
  console.log(`  ${flag} ${r.status} ${r.ms.toFixed(0).padStart(5)}ms  [${label}] ${method} ${path}  (${n})`);
  return r.json;
}

(async () => {
  const admin = await call('POST', '/auth/login', null, { username: 'admin', password: 'admin123' });
  if (admin.status !== 201) throw new Error(`Login admin fallito: ${admin.status}`);
  const A = admin.json.token;

  console.log('— Amministratore');
  const condomini = await check('admin', 'GET', '/condomini', A);
  const demo = condomini.find((c) => c.nome.startsWith('Residenza Demo')) ?? condomini[0];
  const cid = demo.id;
  await check('admin', 'GET', '/dashboard', A);
  await check('admin', 'GET', `/dashboard?condominioId=${cid}`, A);
  const dettaglio = await check('admin', 'GET', `/condomini/${cid}`, A);
  await check('admin', 'GET', '/condomini/users/non-associati', A);
  await check('admin', 'GET', '/condomini/users/richieste-reset-password', A);
  const quote = await check('admin', 'GET', `/quote?condominioId=${cid}`, A);
  await check('admin', 'GET', `/quote/${quote[0].id}/pagamenti`, A);
  await check('admin', 'GET', `/morosita?condominioId=${cid}`, A);
  const bil = await check('admin', 'GET', `/bilancio?condominioId=${cid}`, A);
  const ticket = await check('admin', 'GET', `/ticket?condominioId=${cid}`, A);
  await check('admin', 'GET', `/ticket/${ticket[0].id}`, A);
  await check('admin', 'GET', `/ticket/${ticket[0].id}/note`, A);
  const com = await check('admin', 'GET', `/comunicazioni?condominioId=${cid}`, A);
  await check('admin', 'GET', `/comunicazioni/${com[0].id}/letture`, A);
  const ass = await check('admin', 'GET', `/assemblee?condominioId=${cid}`, A);
  await check('admin', 'GET', `/assemblee/${ass[0].id}`, A);
  await check('admin', 'GET', `/assemblee/${ass[0].id}/quorum`, A);
  const docs = await check('admin', 'GET', `/documenti?condominioId=${cid}`, A);
  await check('admin', 'GET', `/documenti/${docs[0].id}/download`, A);
  await check('admin', 'GET', `/documenti/${docs[0].id}/versioni`, A);
  const forn = await check('admin', 'GET', '/fornitori', A);
  await check('admin', 'GET', '/fornitori/analytics', A);
  await check('admin', 'GET', `/fornitori/${forn[0].id}`, A);
  await check('admin', 'GET', `/fornitori/${forn[0].id}/interventi`, A);

  // Coerenza: la somma dei pagamenti di ogni quota collettiva = importo della quota
  let quoteIncoerenti = 0;
  for (const q of quote.filter((x) => x.tipo === 'collettiva')) {
    const somma = q.pagamenti.reduce((s, p) => s + Math.round(p.importo * 100), 0);
    if (somma !== Math.round(q.importoTotale * 100)) quoteIncoerenti++;
  }
  console.log(`  Quote collettive con ripartizione non esatta: ${quoteIncoerenti}/${quote.length}`);
  if (quoteIncoerenti) problemi++;
  console.log(`  Bilancio: emesso ${bil.totaleEmesso} – pagato ${bil.totalePagato} (${bil.percentualePagata}%)`);

  console.log('— Condòmino');
  const conUser = dettaglio.condomini.find((c) => c.user?.username);
  const login = await call('POST', '/auth/login', null, {
    username: process.argv[3] ?? conUser.user.username, password: process.argv[4] ?? 'password123',
  });
  if (login.status !== 201) throw new Error(`Login condòmino fallito: ${login.status}`);
  const C = login.json.token;
  await check('condòmino', 'GET', '/auth/me', C);
  await check('condòmino', 'GET', '/dashboard/condomino', C);
  await check('condòmino', 'GET', '/mie-quote', C);
  await check('condòmino', 'GET', '/ticket/miei', C);
  await check('condòmino', 'GET', '/comunicazioni/mie', C);
  await check('condòmino', 'GET', '/assemblee/mie', C);
  await check('condòmino', 'GET', '/documenti/miei', C);
  // accessi che devono essere negati
  await check('condòmino (negato)', 'GET', '/dashboard', C, 403);
  await check('condòmino (negato)', 'GET', `/quote?condominioId=${cid}`, C, 403);
  const altrui = ticket.find((t) => t.apertoCondominoId !== login.json.user.condominoId);
  if (altrui) await check('condòmino (negato)', 'GET', `/ticket/${altrui.id}`, C, 403);
  const privato = docs.find((d) => d.visibilita === 'privata');
  if (privato) await check('condòmino (negato)', 'GET', `/documenti/${privato.id}/download`, C, 403);
  await check('anonimo (negato)', 'GET', '/condomini', null, 401);

  console.log(`\nProblemi rilevati: ${problemi}`);
  process.exit(problemi ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });

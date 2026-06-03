import { CondominoDashboardData, DashboardData } from '@/types/dashboard';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:3001/api';

export async function fetchDashboard(token?: string): Promise<DashboardData> {
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE_URL}/dashboard`, {
    cache: 'no-store',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Errore nel caricamento dashboard: ${res.status}`);
  }

  return res.json() as Promise<DashboardData>;
}

export async function fetchDashboardCondomino(token: string): Promise<CondominoDashboardData> {
  const res = await fetch(`${API_BASE_URL}/dashboard/condomino`, {
    cache: 'no-store',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Errore dashboard condomino: ${res.status}`);
  return res.json() as Promise<CondominoDashboardData>;
}

// ─── Tipi Anagrafica ──────────────────────────────────────────────────────────

export interface CondominioListItem {
  id: number;
  nome: string;
  indirizzo: string;
  createdAt: string;
  _count: { condomini: number };
}

export interface CondominoItem {
  id: number;
  nome: string;
  cognome: string;
  email: string | null;
  telefono: string | null;
  unita: string;
  millesimi: number;
  tipo: string;
  stato: string;
  user: { username: string } | null;
}

export interface CondominioDetail extends CondominioListItem {
  condomini: CondominoItem[];
}

// ─── API Condomini ────────────────────────────────────────────────────────────

export async function fetchCondominii(token: string): Promise<CondominioListItem[]> {
  const res = await fetch(`${API_BASE_URL}/condomini`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento condominii');
  return res.json();
}

export async function createCondominio(
  token: string,
  nome: string,
  indirizzo: string,
): Promise<CondominioListItem> {
  const res = await fetch(`${API_BASE_URL}/condomini`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ nome, indirizzo }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore creazione condominio');
  }
  return res.json();
}

export async function fetchCondominioDetail(
  token: string,
  id: number,
): Promise<CondominioDetail> {
  const res = await fetch(`${API_BASE_URL}/condomini/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento condominio');
  return res.json();
}

export interface AddCondominoPayload {
  nome: string;
  cognome: string;
  email?: string;
  telefono?: string;
  unita: string;
  millesimi?: number;
  tipo?: string;
  username?: string;
  password?: string;
}

export async function addCondomino(
  token: string,
  condominioId: number,
  data: AddCondominoPayload,
): Promise<CondominoItem> {
  const res = await fetch(`${API_BASE_URL}/condomini/${condominioId}/condomini`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiunta condomino');
  }
  return res.json();
}

export async function updateCondomino(
  token: string,
  condominioId: number,
  condominoId: number,
  data: Partial<AddCondominoPayload>,
): Promise<CondominoItem> {
  const res = await fetch(`${API_BASE_URL}/condomini/${condominioId}/condomini/${condominoId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento condomino');
  }
  return res.json();
}

export async function toggleCondominoStato(
  token: string,
  condominioId: number,
  condominoId: number,
): Promise<CondominoItem> {
  const res = await fetch(`${API_BASE_URL}/condomini/${condominioId}/condomini/${condominoId}/toggle-stato`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento stato');
  }
  return res.json();
}

export async function updateCondominio(
  token: string,
  id: number,
  nome: string,
  indirizzo: string,
): Promise<CondominioListItem> {
  const res = await fetch(`${API_BASE_URL}/condomini/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ nome, indirizzo }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento condominio');
  }
  return res.json();
}

// ─── API Quote & Pagamenti ────────────────────────────────────────────────────

export interface QuotaMensileItem {
  id: number;
  mese: number;
  anno: number;
  importoTotale: number;
  condominioId: number;
  _count: { pagamenti: number };
  pagamenti: { stato: string; importo: number }[];
}

export interface PagamentoItem {
  id: number;
  importo: number;
  dataPagamento: string | null;
  stato: string;
  metodoPagamento: string | null;
  note: string | null;
  condominoId: number;
  quotaId: number;
  condomino: { nome: string; cognome: string; unita: string; millesimi?: number };
}

export interface BilancioData {
  totaleEmesso: number;
  totalePagato: number;
  totaleAttesa: number;
  totaleMora: number;
  percentualePagata: number;
  numeroPagamenti: number;
  numeroPagati: number;
  numeroAttesa: number;
  numeroMora: number;
}

export interface MorositaData {
  totaleInMora: number;
  condominoriMorosi: {
    nome: string;
    cognome: string;
    unita: string;
    importoTotale: number;
    quote: { mese: number; anno: number; importo: number }[];
  }[];
}

export async function fetchQuote(token: string, condominioId: number): Promise<QuotaMensileItem[]> {
  const res = await fetch(`${API_BASE_URL}/quote?condominioId=${condominioId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento quote');
  return res.json();
}

export async function createQuota(
  token: string,
  condominioId: number,
  mese: number,
  anno: number,
  importoTotale: number,
): Promise<QuotaMensileItem> {
  const res = await fetch(`${API_BASE_URL}/quote`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ condominioId, mese, anno, importoTotale }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore creazione quota');
  }
  return res.json();
}

export async function deleteQuota(token: string, quotaId: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/quote/${quotaId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore eliminazione quota');
  }
}

export async function generaPagamenti(token: string, quotaId: number): Promise<PagamentoItem[]> {
  const res = await fetch(`${API_BASE_URL}/quote/${quotaId}/genera`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore generazione pagamenti');
  }
  return res.json();
}

export async function fetchPagamenti(token: string, quotaId: number): Promise<PagamentoItem[]> {
  const res = await fetch(`${API_BASE_URL}/quote/${quotaId}/pagamenti`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento pagamenti');
  return res.json();
}

export async function updatePagamento(
  token: string,
  pagamentoId: number,
  data: {
    stato?: string;
    importo?: number;
    dataPagamento?: string | null;
    metodoPagamento?: string | null;
    note?: string | null;
  },
): Promise<PagamentoItem> {
  const res = await fetch(`${API_BASE_URL}/pagamenti/${pagamentoId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento pagamento');
  }
  return res.json();
}

export async function fetchBilancio(token: string, condominioId: number): Promise<BilancioData> {
  const res = await fetch(`${API_BASE_URL}/bilancio?condominioId=${condominioId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento bilancio');
  return res.json();
}

export async function fetchMorosita(token: string, condominioId: number): Promise<MorositaData> {
  const res = await fetch(`${API_BASE_URL}/morosita?condominioId=${condominioId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento morosità');
  return res.json();
}

// ─── API Mie Quote (Condomino) ────────────────────────────────────────────────

export interface MioPagamento {
  id: number;
  importo: number;
  stato: string;
  dataPagamento: string | null;
  metodoPagamento: string | null;
  note: string | null;
  quota: { mese: number; anno: number; importoTotale: number };
}

export interface MieQuoteData {
  condomino: { nome: string; cognome: string; unita: string; millesimi: number; tipo: string };
  condominio: { nome: string; indirizzo: string };
  pagamenti: MioPagamento[];
}

export async function fetchMieQuote(token: string): Promise<MieQuoteData> {
  const res = await fetch(`${API_BASE_URL}/mie-quote`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento quote personali');
  return res.json();
}

// ─── Tipi Ticket ──────────────────────────────────────────────────────────────

export interface TicketNota {
  id: number;
  testo: string;
  autore: string;
  createdAt: string;
  ticketId: number;
}

export interface TicketItem {
  id: number;
  titolo: string;
  descrizione: string;
  categoria: string;
  priorita: string;
  stato: string;
  foto: string | null;
  assegnatoa: string | null;
  dataApertura: string;
  dataChiusura: string | null;
  condominioId: number;
  apertoCondominoId: number | null;
  apertoCondomino: { nome: string; cognome: string; unita: string } | null;
  note: TicketNota[];
  _count: { note: number };
}

// ─── API Ticket ───────────────────────────────────────────────────────────────

export async function fetchTickets(
  token: string,
  condominioId: number,
  filters?: { stato?: string; priorita?: string; categoria?: string },
): Promise<TicketItem[]> {
  const params = new URLSearchParams({ condominioId: String(condominioId) });
  if (filters?.stato) params.set('stato', filters.stato);
  if (filters?.priorita) params.set('priorita', filters.priorita);
  if (filters?.categoria) params.set('categoria', filters.categoria);
  const res = await fetch(`${API_BASE_URL}/ticket?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento ticket');
  return res.json();
}

export async function fetchMieiTickets(token: string): Promise<TicketItem[]> {
  const res = await fetch(`${API_BASE_URL}/ticket/miei`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento segnalazioni');
  return res.json();
}

export async function createTicket(
  token: string,
  data: {
    titolo: string;
    descrizione?: string;
    categoria: string;
    priorita?: string;
    foto?: string | null;
    condominioId?: number;
  },
): Promise<TicketItem> {
  const res = await fetch(`${API_BASE_URL}/ticket`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore creazione segnalazione');
  }
  return res.json();
}

export async function updateTicket(
  token: string,
  id: number,
  data: { stato?: string; priorita?: string; assegnatoa?: string | null },
): Promise<TicketItem> {
  const res = await fetch(`${API_BASE_URL}/ticket/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento ticket');
  }
  return res.json();
}

export async function deleteTicket(token: string, id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/ticket/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore eliminazione ticket');
  }
}

export async function addTicketNota(
  token: string,
  ticketId: number,
  testo: string,
): Promise<TicketNota> {
  const res = await fetch(`${API_BASE_URL}/ticket/${ticketId}/note`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ testo }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiunta nota');
  }
  return res.json();
}

// ─── Tipi Comunicazioni ───────────────────────────────────────────────────────

export interface ComunicazioneLettura {
  condomino: { nome: string; cognome: string; unita: string };
  dataLettura: string;
}

export interface ComunicazioneItem {
  id: number;
  titolo: string;
  corpo: string;
  tipo: string;
  data: string;
  destinatari: number;
  destinatariTipo: string;
  condominioId: number;
  _count: { letture: number };
  letture: ComunicazioneLettura[];
  // Campi extra per la vista condomino
  presoVisione?: boolean;
  dataLettura?: string | null;
}

// ─── API Comunicazioni ────────────────────────────────────────────────────────

export async function fetchComunicazioni(
  token: string,
  condominioId: number,
  tipo?: string,
): Promise<ComunicazioneItem[]> {
  const params = new URLSearchParams({ condominioId: String(condominioId) });
  if (tipo && tipo !== 'tutti') params.set('tipo', tipo);
  const res = await fetch(`${API_BASE_URL}/comunicazioni?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento comunicazioni');
  return res.json();
}

export async function fetchMieComunicazioni(token: string): Promise<ComunicazioneItem[]> {
  const res = await fetch(`${API_BASE_URL}/comunicazioni/mie`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento bacheca');
  return res.json();
}

export async function createComunicazione(
  token: string,
  data: { titolo: string; corpo: string; tipo: string; destinatariTipo?: string; condominioId: number },
): Promise<ComunicazioneItem> {
  const res = await fetch(`${API_BASE_URL}/comunicazioni`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore creazione comunicazione');
  }
  return res.json();
}

export async function updateComunicazione(
  token: string,
  id: number,
  data: { titolo?: string; corpo?: string; tipo?: string; destinatariTipo?: string },
): Promise<ComunicazioneItem> {
  const res = await fetch(`${API_BASE_URL}/comunicazioni/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento comunicazione');
  }
  return res.json();
}

export async function deleteComunicazione(token: string, id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/comunicazioni/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore eliminazione comunicazione');
  }
}

export async function presoVisioneComunicazione(token: string, id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/comunicazioni/${id}/presa-visione`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore presa visione');
  }
}

export async function fetchLettureComunicazione(
  token: string,
  id: number,
): Promise<ComunicazioneLettura[]> {
  const res = await fetch(`${API_BASE_URL}/comunicazioni/${id}/letture`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento letture');
  return res.json();
}

// ─── Tipi Assemblee ───────────────────────────────────────────────────────────

export interface AssembleaCondominoInfo {
  id: number;
  nome: string;
  cognome: string;
  unita: string;
  millesimi: number;
}

export interface AssembleaPresenza {
  id: number;
  assembleaId: number;
  condominoId: number;
  presente: boolean;
  delegatoId: number | null;
  condomino: AssembleaCondominoInfo;
}

export interface AssembleaPuntoOdG {
  id: number;
  assembleaId: number;
  titolo: string;
  descrizione: string;
  ordine: number;
  esito: string | null;
  votiSi: number;
  votiNo: number;
  votiAstenuti: number;
}

export interface AssembleaItem {
  id: number;
  titolo: string;
  data: string;
  luogo: string;
  tipo: string;
  stato: string;
  ordineDelGiorno: string;
  verbale: string | null;
  condominioId: number;
  presenze: AssembleaPresenza[];
  puntiOdG: AssembleaPuntoOdG[];
  _count: { presenze: number };
  // Aggiunto dalla vista condomino
  miaPresenza?: { presente: boolean; delegatoId: number | null } | null;
}

export interface QuorumInfo {
  millTotali: number;
  millPresenti: number;
  percPresenti: number;
  quorumOrdinario: boolean;
  quorumStraordinario: boolean;
  presentiCount: number;
  delegatiCount: number;
}

// ─── API Assemblee ────────────────────────────────────────────────────────────

export async function fetchAssemblee(
  token: string,
  condominioId: number,
  filters?: { stato?: string; tipo?: string },
): Promise<AssembleaItem[]> {
  const params = new URLSearchParams({ condominioId: String(condominioId) });
  if (filters?.stato && filters.stato !== 'tutti') params.set('stato', filters.stato);
  if (filters?.tipo && filters.tipo !== 'tutti') params.set('tipo', filters.tipo);
  const res = await fetch(`${API_BASE_URL}/assemblee?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento assemblee');
  return res.json();
}

export async function fetchMieAssemblee(token: string): Promise<AssembleaItem[]> {
  const res = await fetch(`${API_BASE_URL}/assemblee/mie`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento assemblee');
  return res.json();
}

export async function createAssemblea(
  token: string,
  data: { titolo: string; data: string; luogo: string; tipo: string; ordineDelGiorno?: string; condominioId: number },
): Promise<AssembleaItem> {
  const res = await fetch(`${API_BASE_URL}/assemblee`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore creazione assemblea');
  }
  return res.json();
}

export async function updateAssemblea(
  token: string,
  id: number,
  data: Partial<{ titolo: string; data: string; luogo: string; tipo: string; stato: string; ordineDelGiorno: string; verbale: string }>,
): Promise<AssembleaItem> {
  const res = await fetch(`${API_BASE_URL}/assemblee/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento assemblea');
  }
  return res.json();
}

export async function deleteAssemblea(token: string, id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/assemblee/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore eliminazione assemblea');
  }
}

export async function upsertPresenza(
  token: string,
  assembleaId: number,
  condominoId: number,
  data: { presente: boolean; delegatoId?: number | null },
): Promise<AssembleaPresenza> {
  const res = await fetch(`${API_BASE_URL}/assemblee/${assembleaId}/presenze/${condominoId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento presenza');
  }
  return res.json();
}

export async function fetchQuorum(token: string, assembleaId: number): Promise<QuorumInfo> {
  const res = await fetch(`${API_BASE_URL}/assemblee/${assembleaId}/quorum`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore calcolo quorum');
  return res.json();
}

export async function createPuntoOdG(
  token: string,
  assembleaId: number,
  data: { titolo: string; descrizione?: string },
): Promise<AssembleaPuntoOdG> {
  const res = await fetch(`${API_BASE_URL}/assemblee/${assembleaId}/punti-odg`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore creazione punto OdG');
  }
  return res.json();
}

export async function updatePuntoOdG(
  token: string,
  id: number,
  data: Partial<{ titolo: string; descrizione: string; esito: string | null; votiSi: number; votiNo: number; votiAstenuti: number }>,
): Promise<AssembleaPuntoOdG> {
  const res = await fetch(`${API_BASE_URL}/assemblee/punti-odg/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento punto OdG');
  }
  return res.json();
}

export async function deletePuntoOdG(token: string, id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/assemblee/punti-odg/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore eliminazione punto OdG');
  }
}

export async function inviaDelega(
  token: string,
  assembleaId: number,
  delegatoId: number,
): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/assemblee/${assembleaId}/delega`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ delegatoId }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore invio delega');
  }
}

// ─── Tipi Documenti ───────────────────────────────────────────────────────────

export interface DocumentoItem {
  id: number;
  nome: string;
  categoria: string;
  tag: string;
  visibilita: string;
  unitaAccesso: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  versione: number;
  condominioId: number;
  createdAt: string;
  updatedAt: string;
}

// ─── API Documenti ────────────────────────────────────────────────────────────

export async function fetchDocumenti(
  token: string,
  condominioId: number,
  filters?: { categoria?: string; search?: string },
): Promise<DocumentoItem[]> {
  const params = new URLSearchParams({ condominioId: String(condominioId) });
  if (filters?.categoria && filters.categoria !== 'tutti') params.set('categoria', filters.categoria);
  if (filters?.search) params.set('search', filters.search);
  const res = await fetch(`${API_BASE_URL}/documenti?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento documenti');
  return res.json();
}

export async function fetchMieiDocumenti(token: string): Promise<DocumentoItem[]> {
  const res = await fetch(`${API_BASE_URL}/documenti/miei`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error('Errore caricamento documenti');
  return res.json();
}

export async function uploadDocumento(
  token: string,
  file: File,
  meta: { nome: string; categoria: string; tag?: string; visibilita?: string; unitaAccesso?: string; condominioId: number },
): Promise<DocumentoItem> {
  const form = new FormData();
  form.append('file', file);
  form.append('nome', meta.nome);
  form.append('categoria', meta.categoria);
  form.append('condominioId', String(meta.condominioId));
  if (meta.tag) form.append('tag', meta.tag);
  if (meta.visibilita) form.append('visibilita', meta.visibilita);
  if (meta.unitaAccesso) form.append('unitaAccesso', meta.unitaAccesso);

  const res = await fetch(`${API_BASE_URL}/documenti/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore upload documento');
  }
  return res.json();
}

export async function uploadNuovaVersione(
  token: string,
  id: number,
  file: File,
): Promise<DocumentoItem> {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${API_BASE_URL}/documenti/${id}/nuova-versione`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore caricamento nuova versione');
  }
  return res.json();
}

export async function updateDocumento(
  token: string,
  id: number,
  data: Partial<{ nome: string; categoria: string; tag: string; visibilita: string; unitaAccesso: string }>,
): Promise<DocumentoItem> {
  const res = await fetch(`${API_BASE_URL}/documenti/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore aggiornamento documento');
  }
  return res.json();
}

export async function deleteDocumento(token: string, id: number): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/documenti/${id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({})) as { message?: string };
    throw new Error(err.message ?? 'Errore eliminazione documento');
  }
}

export async function downloadDocumento(token: string, id: number, nome: string): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/documenti/${id}/download`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Errore download documento');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nome;
  a.click();
  URL.revokeObjectURL(url);
}


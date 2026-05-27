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


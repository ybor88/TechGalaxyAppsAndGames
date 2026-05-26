import { DashboardData } from '@/types/dashboard';

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

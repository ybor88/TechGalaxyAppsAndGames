const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:3001/api';

export interface AuthUser {
  id: number;
  username: string;
  role: 'AMMINISTRATORE' | 'CONDOMINO';
  condominoId: number | null;
  profilePhoto?: string | null;
}

export async function loginRequest(username: string, password: string): Promise<{ token: string; user: AuthUser }> {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as { message?: string }).message ?? 'Credenziali non valide');
  }
  return res.json();
}

export async function fetchMe(token: string): Promise<AuthUser> {
  const res = await fetch(`${API_URL}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Sessione scaduta');
  return res.json();
}

export async function uploadProfilePhoto(token: string, base64: string): Promise<string> {
  const res = await fetch(`${API_URL}/auth/profile-photo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ photo: base64 }),
  });
  if (!res.ok) throw new Error('Errore durante il caricamento della foto');
  const data: { profilePhoto: string } = await res.json();
  return data.profilePhoto;
}

export function saveToken(token: string): void {
  if (typeof document !== 'undefined') {
    document.cookie = `cf_token=${token}; path=/; max-age=28800; SameSite=Lax`;
  }
}

export function clearToken(): void {
  if (typeof document !== 'undefined') {
    document.cookie = 'cf_token=; path=/; max-age=0';
  }
}

export function getTokenFromCookie(): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(?:^|;\s*)cf_token=([^;]+)/);
  return match ? match[1] : null;
}

export function parseJwt(token: string): AuthUser | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return {
      id: payload.sub,
      username: payload.username,
      role: payload.role,
      condominoId: payload.condominoId ?? null,
    };
  } catch {
    return null;
  }
}

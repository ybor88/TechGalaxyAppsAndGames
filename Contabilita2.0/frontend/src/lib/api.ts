import axios from "axios";

const api = axios.create({
  baseURL: "/api/v1",
  headers: { "Content-Type": "application/json" },
});

export interface KPIFinanziario {
  saldo_operativo: number;
  totale_entrate: number;
  totale_uscite: number;
  cashflow_netto: number;
  indice_liquidita: number;
}

export interface SaldoMensile {
  mese: string;
  entrate: number;
  uscite: number;
  saldo: number;
}

export interface PuntoGrafico {
  data: string;
  entrate: number;
  uscite: number;
  cashflow: number;
}

export interface DashboardData {
  kpi: KPIFinanziario;
  andamento_mensile: SaldoMensile[];
  cashflow_settimanale: PuntoGrafico[];
  aggiornato_al: string;
}

export interface Movimento {
  id: number;
  data: string;
  tipo: "entrata" | "uscita";
  importo: number;
  descrizione: string;
  categoria: string | null;
  conto_id: number | null;
  note: string | null;
  created_at: string;
}

export interface MovimentoCreate {
  data: string;
  tipo: "entrata" | "uscita";
  importo: number;
  descrizione: string;
  categoria?: string;
  conto_id?: number;
  note?: string;
}

export interface Conto {
  id: number;
  codice: string;
  descrizione: string;
  tipo: string;
  saldo: number;
  created_at: string;
}

export interface ContoCreate {
  codice: string;
  descrizione: string;
  tipo: string;
  saldo?: number;
}

export const dashboardApi = {
  getDashboard: () => api.get<DashboardData>("/dashboard/"),
  getKPI: () => api.get<KPIFinanziario>("/dashboard/kpi"),
  getAndamentoMensile: (mesi = 12) =>
    api.get<SaldoMensile[]>(`/dashboard/andamento-mensile?mesi=${mesi}`),
  getCashflowSettimanale: (settimane = 8) =>
    api.get<PuntoGrafico[]>(`/dashboard/cashflow-settimanale?settimane=${settimane}`),
};

export const movimentiApi = {
  list: (skip = 0, limit = 500) =>
    api.get<Movimento[]>(`/movimenti/?skip=${skip}&limit=${limit}`),
  create: (payload: MovimentoCreate) =>
    api.post<Movimento>("/movimenti/", payload),
  update: (id: number, payload: Partial<MovimentoCreate>) =>
    api.put<Movimento>(`/movimenti/${id}`, payload),
  delete: (id: number) => api.delete(`/movimenti/${id}`),
};

export const contiApi = {
  list: () => api.get<Conto[]>("/conti/"),
  create: (payload: ContoCreate) => api.post<Conto>("/conti/", payload),
  update: (id: number, payload: Partial<ContoCreate>) =>
    api.put<Conto>(`/conti/${id}`, payload),
  delete: (id: number) => api.delete(`/conti/${id}`),
};

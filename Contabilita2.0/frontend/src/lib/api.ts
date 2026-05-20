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

export const dashboardApi = {
  getDashboard: () => api.get<DashboardData>("/dashboard/"),
  getKPI: () => api.get<KPIFinanziario>("/dashboard/kpi"),
  getAndamentoMensile: (mesi = 12) =>
    api.get<SaldoMensile[]>(`/dashboard/andamento-mensile?mesi=${mesi}`),
  getCashflowSettimanale: (settimane = 8) =>
    api.get<PuntoGrafico[]>(`/dashboard/cashflow-settimanale?settimane=${settimane}`),
};

export interface PaymentStatus {
  totaleCondomini: number;
  pagato: number;
  inAttesa: number;
  inMora: number;
  percentualePagato: number;
}

export interface MonthlyExpense {
  mese: string;
  importo: number;
}

export interface OpenTicket {
  id: number;
  titolo: string;
  categoria: string;
  priorita: 'alta' | 'media' | 'bassa';
  stato: string;
  dataApertura: string;
}

export interface UpcomingDeadline {
  id: number;
  descrizione: string;
  data: string;
  tipo: string;
  giorniMancanti: number;
}

export interface RecentCommunication {
  id: number;
  titolo: string;
  tipo: string;
  data: string;
  destinatari: number;
}

export interface OngoingWork {
  id: number;
  descrizione: string;
  fornitore: string;
  dataInizio: string;
  dataFine: string;
  stato: string;
  progressione: number;
}

export interface DashboardData {
  statoPagementi: PaymentStatus;
  speseUltimiMesi: MonthlyExpense[];
  segnalazioniAperte: OpenTicket[];
  scadenzeImminenti: UpcomingDeadline[];
  comunicazioniRecenti: RecentCommunication[];
  lavoriInCorso: OngoingWork[];
  nomeCondominio: string;
  aggiornato: string;
}

// ─── Vista Condomino ───────────────────────────────────────────────────────────────────

export interface QuotaCorrente {
  mese: number;
  anno: number;
  importo: number;
  stato: 'pagata' | 'in_attesa' | 'in_mora';
  dataPagamento: string | null;
}

export interface CondominoDashboardData {
  nomeCondomino: string;
  unita: string;
  nomeCondominio: string;
  aggiornato: string;
  quotaCorrente: QuotaCorrente | null;
  comunicazioniRecenti: RecentCommunication[];
  segnalazioniAperte: OpenTicket[];
  scadenzeImminenti: UpcomingDeadline[];
}

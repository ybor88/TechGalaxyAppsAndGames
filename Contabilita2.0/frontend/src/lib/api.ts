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

// ── Fatturazione ─────────────────────────────────────────────────────────────

export type TipoAnagrafica = "cliente" | "fornitore" | "entrambi";
export type TipoDocumento =
  | "preventivo"
  | "ordine"
  | "fattura_attiva"
  | "fattura_passiva"
  | "nota_credito";
export type StatoDocumento = "bozza" | "emesso" | "pagato" | "annullato";

export interface Anagrafica {
  id: number;
  nome: string;
  tipo: TipoAnagrafica;
  piva: string | null;
  cf: string | null;
  indirizzo: string | null;
  cap: string | null;
  citta: string | null;
  provincia: string | null;
  paese: string;
  email: string | null;
  telefono: string | null;
  note: string | null;
  created_at: string;
}

export interface AnagraficaCreate {
  nome: string;
  tipo: TipoAnagrafica;
  piva?: string;
  cf?: string;
  indirizzo?: string;
  cap?: string;
  citta?: string;
  provincia?: string;
  paese?: string;
  email?: string;
  telefono?: string;
  note?: string;
}

export interface RigaDocumento {
  id: number;
  documento_id: number;
  descrizione: string;
  quantita: number;
  prezzo_unitario: number;
  iva_percentuale: number;
  importo: number;
}

export interface RigaDocumentoCreate {
  descrizione: string;
  quantita: number;
  prezzo_unitario: number;
  iva_percentuale: number;
}

export interface Documento {
  id: number;
  tipo: TipoDocumento;
  numero: string;
  data: string;
  data_scadenza: string | null;
  anagrafica_id: number | null;
  stato: StatoDocumento;
  oggetto: string | null;
  subtotale: number;
  totale_iva: number;
  totale: number;
  note: string | null;
  created_at: string;
  updated_at: string;
  anagrafica: { id: number; nome: string; tipo: string } | null;
  righe: RigaDocumento[];
}

export interface DocumentoCreate {
  tipo: TipoDocumento;
  data: string;
  data_scadenza?: string;
  anagrafica_id?: number;
  oggetto?: string;
  note?: string;
  righe: RigaDocumentoCreate[];
}

export interface DocumentoUpdate {
  data?: string;
  data_scadenza?: string;
  anagrafica_id?: number;
  stato?: StatoDocumento;
  oggetto?: string;
  note?: string;
  righe?: RigaDocumentoCreate[];
}

export const anagraficheApi = {
  list: (tipo?: TipoAnagrafica) =>
    api.get<Anagrafica[]>(`/anagrafiche${tipo ? `?tipo=${tipo}` : ""}`),
  get: (id: number) => api.get<Anagrafica>(`/anagrafiche/${id}`),
  create: (payload: AnagraficaCreate) =>
    api.post<Anagrafica>("/anagrafiche", payload),
  update: (id: number, payload: Partial<AnagraficaCreate>) =>
    api.put<Anagrafica>(`/anagrafiche/${id}`, payload),
  delete: (id: number) => api.delete(`/anagrafiche/${id}`),
};

export const documentiApi = {
  list: (tipo?: TipoDocumento, stato?: StatoDocumento) => {
    const params = new URLSearchParams();
    if (tipo) params.set("tipo", tipo);
    if (stato) params.set("stato", stato);
    const qs = params.toString();
    return api.get<Documento[]>(`/documenti${qs ? `?${qs}` : ""}`);
  },
  get: (id: number) => api.get<Documento>(`/documenti/${id}`),
  create: (payload: DocumentoCreate) => api.post<Documento>("/documenti", payload),
  update: (id: number, payload: DocumentoUpdate) =>
    api.put<Documento>(`/documenti/${id}`, payload),
  delete: (id: number) => api.delete(`/documenti/${id}`),
  pdfUrl: (id: number) => `/api/v1/documenti/${id}/pdf`,
};

// ── OCR Contabile (F3) ───────────────────────────────────────────────────────

export interface OcrRisultato {
  id: number;
  filename: string;
  content_type: string;
  testo_estratto: string | null;
  fornitore: string | null;
  piva: string | null;
  cf: string | null;
  numero_documento: string | null;
  data_documento: string | null;
  importo_netto: number | null;
  importo_iva: number | null;
  importo_totale: number | null;
  aliquota_iva: number | null;
  stato: "elaborato" | "errore" | "revisione";
  errore: string | null;
  documento_id: number | null;
  created_at: string;
}

export interface OcrElaboraResponse {
  risultato: OcrRisultato;
  avvisi: string[];
}

export const ocrApi = {
  elabora: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return axios.post<OcrElaboraResponse>("/api/v1/ocr/elabora", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },
  list: () => api.get<OcrRisultato[]>("/ocr/risultati"),
  get: (id: number) => api.get<OcrRisultato>(`/ocr/risultati/${id}`),
  delete: (id: number) => api.delete(`/ocr/risultati/${id}`),
  collegaDocumento: (risultatoId: number, documentoId: number) =>
    api.patch<OcrRisultato>(`/ocr/risultati/${risultatoId}/collega-documento/${documentoId}`),
};

// ── Contabilità Generale (F4) ────────────────────────────────────────────────

export type TipoCausale =
  | "manuale"
  | "fattura_attiva"
  | "fattura_passiva"
  | "pagamento"
  | "incasso"
  | "altro";

export type TipoIVA = "imponibile" | "iva" | "esente";

export interface RigaRegistrazioneIn {
  conto_id: number;
  descrizione?: string;
  dare: number;
  avere: number;
  aliquota_iva?: number;
  tipo_iva?: TipoIVA;
}

export interface RegistrazioneCreate {
  data: string;
  causale: string;
  tipo_causale: TipoCausale;
  note?: string;
  righe: RigaRegistrazioneIn[];
}

export interface RigaRegistrazioneOut {
  id: number;
  registrazione_id: number;
  conto_id: number;
  conto_codice: string;
  conto_descrizione: string;
  descrizione: string | null;
  dare: number;
  avere: number;
  aliquota_iva: number | null;
  tipo_iva: string | null;
}

export interface RegistrazioneSummary {
  id: number;
  numero: number;
  data: string;
  causale: string;
  tipo_causale: string;
  chiusa: boolean;
  totale_dare: number;
  totale_avere: number;
  created_at: string;
}

export interface RegistrazioneDetail extends RegistrazioneSummary {
  note: string | null;
  righe: RigaRegistrazioneOut[];
}

export interface VoceBilancio {
  conto_id: number;
  codice: string;
  descrizione: string;
  tipo: string;
  totale_dare: number;
  totale_avere: number;
  saldo: number;
}

export interface BilancioResponse {
  conti: VoceBilancio[];
  totale_dare: number;
  totale_avere: number;
  totale_attivo: number;
  totale_passivo: number;
  totale_costi: number;
  totale_ricavi: number;
  utile_perdita: number;
}

export interface RigaIVA {
  aliquota_iva: number;
  imponibile_acquisti: number;
  iva_a_credito: number;
  imponibile_vendite: number;
  iva_a_debito: number;
}

export interface LiquidazioneIVA {
  data_da: string;
  data_a: string;
  iva_a_credito: number;
  iva_a_debito: number;
  saldo_iva: number;
  dettaglio: RigaIVA[];
}

export interface InizializzaResponse {
  conti_creati: number;
  conti_esistenti: number;
  message: string;
}

export const contabilitaApi = {
  listRegistrazioni: (
    skip = 0,
    limit = 200,
    data_da?: string,
    data_a?: string,
  ) => {
    const p = new URLSearchParams({ skip: String(skip), limit: String(limit) });
    if (data_da) p.append("data_da", data_da);
    if (data_a) p.append("data_a", data_a);
    return api.get<RegistrazioneSummary[]>(`/contabilita/registrazioni?${p}`);
  },
  createRegistrazione: (payload: RegistrazioneCreate) =>
    api.post<RegistrazioneDetail>("/contabilita/registrazioni", payload),
  getRegistrazione: (id: number) =>
    api.get<RegistrazioneDetail>(`/contabilita/registrazioni/${id}`),
  deleteRegistrazione: (id: number) =>
    api.delete(`/contabilita/registrazioni/${id}`),
  chiudiRegistrazione: (id: number) =>
    api.post(`/contabilita/registrazioni/${id}/chiudi`),
  getBilancio: () => api.get<BilancioResponse>("/contabilita/bilancio"),
  getLiquidazioneIVA: (data_da: string, data_a: string) =>
    api.get<LiquidazioneIVA>(`/contabilita/iva?data_da=${data_da}&data_a=${data_a}`),
  inizializzaPianoConti: () =>
    api.post<InizializzaResponse>("/contabilita/init-piano-conti"),
};

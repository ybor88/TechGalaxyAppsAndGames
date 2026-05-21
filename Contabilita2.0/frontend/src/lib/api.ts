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

// ── CRM Economico (F5) ───────────────────────────────────────────────────────

export type MetodoPagamento = "bonifico" | "contanti" | "assegno" | "carta" | "rid" | "altro";
export type TipoScadenza = "incasso" | "pagamento" | "altro";
export type StatoScadenza = "aperta" | "pagata" | "scaduta" | "annullata";
export type FasePipeline =
  | "prospecting"
  | "qualifica"
  | "proposta"
  | "trattativa"
  | "chiusa_vinta"
  | "chiusa_persa";

export interface StoricoPagamento {
  id: number;
  anagrafica_id: number;
  documento_id: number | null;
  data_pagamento: string;
  importo: number;
  metodo_pagamento: MetodoPagamento;
  giorni_ritardo: number;
  note: string | null;
  created_at: string;
  anagrafica_nome: string | null;
}

export interface StoricoPagamentoCreate {
  anagrafica_id: number;
  documento_id?: number;
  data_pagamento: string;
  importo: number;
  metodo_pagamento?: MetodoPagamento;
  giorni_ritardo?: number;
  note?: string;
}

export interface AffidabilitaCliente {
  anagrafica_id: number;
  nome: string;
  totale_pagamenti: number;
  pagamenti_puntuali: number;
  pagamenti_in_ritardo: number;
  media_giorni_ritardo: number;
  score: number;
  livello: "ottimo" | "buono" | "sufficiente" | "scarso";
}

export interface Scadenza {
  id: number;
  anagrafica_id: number | null;
  documento_id: number | null;
  titolo: string;
  descrizione: string | null;
  data_scadenza: string;
  importo: number | null;
  tipo: TipoScadenza;
  stato: StatoScadenza;
  note: string | null;
  created_at: string;
  anagrafica_nome: string | null;
  giorni_alla_scadenza: number | null;
}

export interface ScadenzaCreate {
  anagrafica_id?: number;
  documento_id?: number;
  titolo: string;
  descrizione?: string;
  data_scadenza: string;
  importo?: number;
  tipo?: TipoScadenza;
  stato?: StatoScadenza;
  note?: string;
}

export interface ScadenzaUpdate {
  titolo?: string;
  descrizione?: string;
  data_scadenza?: string;
  importo?: number;
  tipo?: TipoScadenza;
  stato?: StatoScadenza;
  note?: string;
}

export interface OpportunitaPipeline {
  id: number;
  anagrafica_id: number | null;
  titolo: string;
  valore_stimato: number | null;
  fase: FasePipeline;
  probabilita: number;
  data_chiusura_prevista: string | null;
  note: string | null;
  created_at: string;
  updated_at: string;
  anagrafica_nome: string | null;
}

export interface OpportunitaCreate {
  anagrafica_id?: number;
  titolo: string;
  valore_stimato?: number;
  fase?: FasePipeline;
  probabilita?: number;
  data_chiusura_prevista?: string;
  note?: string;
}

export interface OpportunitaUpdate {
  anagrafica_id?: number;
  titolo?: string;
  valore_stimato?: number;
  fase?: FasePipeline;
  probabilita?: number;
  data_chiusura_prevista?: string;
  note?: string;
}

export interface CrmSummary {
  totale_clienti: number;
  totale_fornitori: number;
  scadenze_aperte: number;
  scadenze_scadute: number;
  valore_pipeline_attivo: number;
  opportunita_aperte: number;
}

export const crmApi = {
  summary: () => api.get<CrmSummary>("/crm/summary"),
  listClienti: () => api.get<Anagrafica[]>("/crm/clienti"),
  listFornitori: () => api.get<Anagrafica[]>("/crm/fornitori"),
  getAffidabilita: (anagraficaId: number) =>
    api.get<AffidabilitaCliente>(`/crm/affidabilita/${anagraficaId}`),
  listStorico: (anagraficaId?: number) =>
    api.get<StoricoPagamento[]>(
      `/crm/storico-pagamenti${anagraficaId ? `?anagrafica_id=${anagraficaId}` : ""}`
    ),
  createStorico: (payload: StoricoPagamentoCreate) =>
    api.post<StoricoPagamento>("/crm/storico-pagamenti", payload),
  deleteStorico: (id: number) => api.delete(`/crm/storico-pagamenti/${id}`),
  listScadenze: (stato?: StatoScadenza, tipo?: TipoScadenza) => {
    const p = new URLSearchParams();
    if (stato) p.set("stato", stato);
    if (tipo) p.set("tipo", tipo);
    const qs = p.toString();
    return api.get<Scadenza[]>(`/crm/scadenze${qs ? `?${qs}` : ""}`);
  },
  createScadenza: (payload: ScadenzaCreate) =>
    api.post<Scadenza>("/crm/scadenze", payload),
  updateScadenza: (id: number, payload: ScadenzaUpdate) =>
    api.put<Scadenza>(`/crm/scadenze/${id}`, payload),
  deleteScadenza: (id: number) => api.delete(`/crm/scadenze/${id}`),
  listPipeline: (fase?: FasePipeline) =>
    api.get<OpportunitaPipeline[]>(`/crm/pipeline${fase ? `?fase=${fase}` : ""}`),
  createOpportunita: (payload: OpportunitaCreate) =>
    api.post<OpportunitaPipeline>("/crm/pipeline", payload),
  updateOpportunita: (id: number, payload: OpportunitaUpdate) =>
    api.put<OpportunitaPipeline>(`/crm/pipeline/${id}`, payload),
  deleteOpportunita: (id: number) => api.delete(`/crm/pipeline/${id}`),
};

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

// ── Workflow Aziendale (F6) ───────────────────────────────────────────────────

export type TipoTask = "task" | "approvazione" | "reminder" | "acquisto";
export type StatoTask =
  | "aperto"
  | "in_corso"
  | "completato"
  | "annullato"
  | "approvato"
  | "rifiutato";
export type PrioritaTask = "bassa" | "media" | "alta" | "urgente";
export type StatoPasso = "in_attesa" | "approvato" | "rifiutato";

export interface PassoApprovazione {
  id: number;
  task_id: number;
  ordine: number;
  approvatore: string;
  stato: StatoPasso;
  commento: string | null;
  aggiornato_at: string;
}

export interface WorkflowTask {
  id: number;
  titolo: string;
  descrizione: string | null;
  tipo: TipoTask;
  stato: StatoTask;
  priorita: PrioritaTask;
  assegnato_a: string | null;
  creato_da: string | null;
  data_scadenza: string | null;
  data_completamento: string | null;
  anagrafica_id: number | null;
  anagrafica_nome: string | null;
  importo_stimato: number | null;
  importo_approvato: number | null;
  reminder_inviato: boolean;
  note: string | null;
  created_at: string;
  updated_at: string;
  passi: PassoApprovazione[];
  giorni_alla_scadenza: number | null;
  passo_corrente: number | null;
}

export interface PassoCreate {
  approvatore: string;
  ordine: number;
}

export interface TaskCreate {
  titolo: string;
  descrizione?: string;
  tipo?: TipoTask;
  priorita?: PrioritaTask;
  assegnato_a?: string;
  creato_da?: string;
  data_scadenza?: string;
  anagrafica_id?: number;
  importo_stimato?: number;
  note?: string;
  passi?: PassoCreate[];
}

export interface TaskUpdate {
  titolo?: string;
  descrizione?: string;
  stato?: StatoTask;
  priorita?: PrioritaTask;
  assegnato_a?: string;
  data_scadenza?: string;
  data_completamento?: string;
  importo_stimato?: number;
  importo_approvato?: number;
  note?: string;
}

export interface ApprovaPasso {
  stato: StatoPasso;
  commento?: string;
}

export interface WorkflowSummary {
  task_aperti: number;
  task_in_corso: number;
  approvazioni_in_attesa: number;
  reminder_in_scadenza: number;
  acquisti_aperti: number;
  task_scaduti: number;
}

export const workflowApi = {
  summary: () => api.get<WorkflowSummary>("/workflow/summary"),
  listTasks: (params?: {
    tipo?: TipoTask;
    stato?: StatoTask;
    assegnato_a?: string;
    priorita?: PrioritaTask;
  }) => {
    const p = new URLSearchParams();
    if (params?.tipo) p.set("tipo", params.tipo);
    if (params?.stato) p.set("stato", params.stato);
    if (params?.assegnato_a) p.set("assegnato_a", params.assegnato_a);
    if (params?.priorita) p.set("priorita", params.priorita);
    const qs = p.toString();
    return api.get<WorkflowTask[]>(`/workflow/tasks${qs ? `?${qs}` : ""}`);
  },
  getTask: (id: number) => api.get<WorkflowTask>(`/workflow/tasks/${id}`),
  createTask: (payload: TaskCreate) =>
    api.post<WorkflowTask>("/workflow/tasks", payload),
  updateTask: (id: number, payload: TaskUpdate) =>
    api.put<WorkflowTask>(`/workflow/tasks/${id}`, payload),
  deleteTask: (id: number) => api.delete(`/workflow/tasks/${id}`),
  approvaPasso: (taskId: number, passoId: number, payload: ApprovaPasso) =>
    api.post<WorkflowTask>(
      `/workflow/tasks/${taskId}/passi/${passoId}/approva`,
      payload
    ),
};

// ── Forecasting Aziendale (F7) ────────────────────────────────────────────────

export interface PuntoPrevisione {
  periodo: string;
  valore: number;
  confidenza_min: number;
  confidenza_max: number;
}

export interface PrevisioneVenditeResponse {
  storico: PuntoPrevisione[];
  previsione: PuntoPrevisione[];
  trend: "crescente" | "stabile" | "decrescente";
  variazione_percentuale: number;
}

export interface PrevisioneLiquiditaResponse {
  saldo_attuale: number;
  previsione_giorni: PuntoPrevisione[];
  giorni_copertura: number;
  allerta: boolean;
}

export interface ScenarioItem {
  scenario: "ottimistico" | "base" | "pessimistico";
  entrate_previste: number;
  uscite_previste: number;
  cashflow: number;
  variazione_percentuale: number;
}

export interface SimulazioneScenariResponse {
  mese_riferimento: string;
  media_storica_entrate: number;
  media_storica_uscite: number;
  scenari: ScenarioItem[];
}

export interface FattoreRischio {
  fattore: string;
  impatto: "alto" | "medio" | "basso";
}

export interface RischioInsolvenzaResponse {
  punteggio: number;
  livello: "basso" | "medio" | "alto" | "critico";
  fattori: FattoreRischio[];
  raccomandazioni: string[];
}

export const forecastingApi = {
  previsioneVendite: (mesi = 3) =>
    api.get<PrevisioneVenditeResponse>(`/forecasting/previsione-vendite?mesi=${mesi}`),
  previsioneLiquidita: (giorni = 30) =>
    api.get<PrevisioneLiquiditaResponse>(`/forecasting/previsione-liquidita?giorni=${giorni}`),
  simulazioneScenari: () =>
    api.get<SimulazioneScenariResponse>("/forecasting/simulazione-scenari"),
  rischioInsolvenza: () =>
    api.get<RischioInsolvenzaResponse>("/forecasting/rischio-insolvenza"),
};

// ── AI Assistant Locale (F8) ─────────────────────────────────────────────────

export interface AIMessage {
  id: number;
  sessione_id: string;
  ruolo: "utente" | "assistente";
  contenuto: string;
  created_at: string;
}

export interface AIChatResponse {
  risposta: string;
  sessione_id: string;
  model: string | null;
}

export interface AIStatusResponse {
  ollama_disponibile: boolean;
  modello: string;
  messaggio: string;
}

export const aiAssistantApi = {
  getStatus: () => api.get<AIStatusResponse>("/ai/status"),
  chat: (messaggio: string, sessione_id?: string) =>
    api.post<AIChatResponse>("/ai/chat", { messaggio, sessione_id }),
  getCronologia: (sessione_id: string) =>
    api.get<AIMessage[]>(`/ai/cronologia?sessione_id=${encodeURIComponent(sessione_id)}`),
  cancellaCronologia: (sessione_id: string) =>
    api.delete(`/ai/cronologia?sessione_id=${encodeURIComponent(sessione_id)}`),
};

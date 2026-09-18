import axios from "axios";

const api = axios.create({
  baseURL: "/api/v1",
  headers: { "Content-Type": "application/json" },
});

// Istanza diretta al backend per chiamate lente (es. AI chat con Ollama).
// Bypassa il proxy Next.js che ha un timeout di ~30 s insufficiente per LLM.
const BACKEND_URL =
  (typeof window !== "undefined"
    ? (window as any).__ENV__?.NEXT_PUBLIC_BACKEND_URL
    : process.env.NEXT_PUBLIC_BACKEND_URL) ?? "http://localhost:8000";

const directApi = axios.create({
  baseURL: `${BACKEND_URL}/api/v1`,
  headers: { "Content-Type": "application/json" },
  timeout: 300_000, // 5 minuti — sufficiente per qualsiasi modello Ollama
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

// ── Ammortamenti ─────────────────────────────────────────────────────────────

export interface CategoriaMinisteriale {
  categoria: string;
  label: string;
  aliquota_fiscale: number;
}

export interface Cespite {
  id: number;
  descrizione: string;
  categoria: string;
  categoria_label: string;
  data_acquisto: string;
  costo_storico: number;
  aliquota_civilistica: number;
  aliquota_fiscale: number;
  conto_costo_id: number;
  conto_costo_descrizione: string;
  conto_fondo_id: number;
  conto_fondo_descrizione: string;
  note: string | null;
  dismesso: boolean;
  data_dismissione: string | null;
  created_at: string;
}

export interface CespiteCreate {
  descrizione: string;
  categoria: string;
  data_acquisto: string;
  costo_storico: number;
  aliquota_civilistica?: number;
  aliquota_fiscale?: number;
  conto_costo_id: number;
  conto_fondo_id: number;
  note?: string;
}

export interface QuotaPiano {
  anno: number;
  quota_civilistica: number;
  fondo_civilistico: number;
  valore_residuo_civilistico: number;
  quota_fiscale: number;
  fondo_fiscale: number;
  valore_residuo_fiscale: number;
  contabilizzato: boolean;
}

export interface PianoAmmortamentoResponse {
  cespite_id: number;
  descrizione: string;
  costo_storico: number;
  quote: QuotaPiano[];
}

export interface RiepilogoAmmortamenti {
  anno: number;
  numero_cespiti: number;
  totale_costo_storico: number;
  totale_fondo_civilistico: number;
  totale_quota_anno: number;
  totale_valore_residuo: number;
}

export const ammortamentiApi = {
  listCategorie: () => api.get<CategoriaMinisteriale[]>("/ammortamenti/categorie"),
  listCespiti: (includiDismessi = true) =>
    api.get<Cespite[]>(`/ammortamenti/cespiti?includi_dismessi=${includiDismessi}`),
  createCespite: (payload: CespiteCreate) =>
    api.post<Cespite>("/ammortamenti/cespiti", payload),
  deleteCespite: (id: number) => api.delete(`/ammortamenti/cespiti/${id}`),
  dismettiCespite: (id: number, data_dismissione: string) =>
    api.post<Cespite>(`/ammortamenti/cespiti/${id}/dismetti`, { data_dismissione }),
  getPiano: (id: number) => api.get<PianoAmmortamentoResponse>(`/ammortamenti/cespiti/${id}/piano`),
  contabilizza: (id: number, anno: number) =>
    api.post(`/ammortamenti/cespiti/${id}/contabilizza`, { anno }),
  getRiepilogo: (anno: number) =>
    api.get<RiepilogoAmmortamenti>(`/ammortamenti/riepilogo?anno=${anno}`),
};

// ── Gestione Paga Dipendenti ──────────────────────────────────────────────────

export interface Dipendente {
  id: number;
  nome: string;
  cognome: string;
  codice_fiscale: string | null;
  qualifica: string | null;
  data_assunzione: string;
  data_cessazione: string | null;
  retribuzione_lorda_mensile: number;
  numero_mensilita: number;
  aliquota_inps_dipendente: number;
  aliquota_inps_azienda: number;
  detrazioni_attive: boolean;
  note: string | null;
  created_at: string;
}

export interface DipendenteCreate {
  nome: string;
  cognome: string;
  codice_fiscale?: string;
  qualifica?: string;
  data_assunzione: string;
  data_cessazione?: string;
  retribuzione_lorda_mensile: number;
  numero_mensilita?: number;
  aliquota_inps_dipendente?: number;
  aliquota_inps_azienda?: number;
  detrazioni_attive?: boolean;
  note?: string;
}

export interface Cedolino {
  id: number | null;
  dipendente_id: number;
  anno: number;
  mese: number;
  retribuzione_lorda: number;
  contributi_inps_dipendente: number;
  imponibile_fiscale: number;
  irpef_lorda: number;
  detrazioni_irpef: number;
  irpef_netta: number;
  netto_busta: number;
  contributi_inps_azienda: number;
  quota_tfr: number;
  costo_azienda: number;
  registrazione_id: number | null;
}

export const pagheApi = {
  listDipendenti: (includiCessati = true) =>
    api.get<Dipendente[]>(`/paghe/dipendenti?includi_cessati=${includiCessati}`),
  createDipendente: (payload: DipendenteCreate) =>
    api.post<Dipendente>("/paghe/dipendenti", payload),
  updateDipendente: (id: number, payload: Partial<DipendenteCreate>) =>
    api.put<Dipendente>(`/paghe/dipendenti/${id}`, payload),
  deleteDipendente: (id: number) => api.delete(`/paghe/dipendenti/${id}`),
  anteprimaCedolino: (dipendenteId: number, anno: number, mese: number) =>
    api.post<Cedolino>(`/paghe/dipendenti/${dipendenteId}/cedolini/anteprima`, { anno, mese }),
  salvaCedolino: (dipendenteId: number, anno: number, mese: number) =>
    api.post<Cedolino>(`/paghe/dipendenti/${dipendenteId}/cedolini`, { anno, mese }),
  listCedolini: (dipendenteId: number) =>
    api.get<Cedolino[]>(`/paghe/dipendenti/${dipendenteId}/cedolini`),
  deleteCedolino: (id: number) => api.delete(`/paghe/cedolini/${id}`),
  contabilizzaCedolino: (id: number) =>
    api.post<{ cedolino_id: number; registrazione_id: number }>(`/paghe/cedolini/${id}/contabilizza`),
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
    directApi.post<AIChatResponse>("/ai/chat", { messaggio, sessione_id }),
  getCronologia: (sessione_id: string) =>
    directApi.get<AIMessage[]>(`/ai/cronologia?sessione_id=${encodeURIComponent(sessione_id)}`),
  cancellaCronologia: (sessione_id: string) =>
    directApi.delete(`/ai/cronologia?sessione_id=${encodeURIComponent(sessione_id)}`),
};

// ── Centri di Costo ────────────────────────────────────────────────────────────

export type TipoCentroCosto = "produttivo" | "ausiliario" | "comune";

export interface CentroCosto {
  id: number;
  codice: string;
  descrizione: string;
  tipo: TipoCentroCosto;
  centro_padre_id: number | null;
  centro_padre_descrizione: string | null;
  attivo: boolean;
  note: string | null;
  created_at: string;
}

export interface CentroCostoCreate {
  codice: string;
  descrizione: string;
  tipo: TipoCentroCosto;
  centro_padre_id?: number;
  note?: string;
}

export interface BaseRiparto {
  id: number;
  descrizione: string;
  unita_misura: string;
  created_at: string;
}

export interface BaseRipartoCreate {
  descrizione: string;
  unita_misura: string;
}

export interface ValoreBaseRiparto {
  id: number;
  base_riparto_id: number;
  centro_costo_id: number;
  centro_costo_descrizione: string;
  periodo: string;
  quantita: number;
}

export interface ValoreBaseRipartoCreate {
  base_riparto_id: number;
  centro_costo_id: number;
  periodo: string;
  quantita: number;
}

export interface RipartoCostoIndiretto {
  id: number;
  descrizione: string;
  centro_costo_origine_id: number;
  centro_costo_origine_descrizione: string;
  importo: number;
  base_riparto_id: number;
  base_riparto_descrizione: string;
  periodo: string;
  note: string | null;
  created_at: string;
}

export interface RipartoCostoIndirettoCreate {
  descrizione: string;
  centro_costo_origine_id: number;
  importo: number;
  base_riparto_id: number;
  periodo: string;
  note?: string;
}

export interface AllocazioneRiga {
  centro_costo_id: number;
  centro_costo_descrizione: string;
  quantita_base: number;
  percentuale: number;
  importo_allocato: number;
}

export interface RipartoCalcolato {
  riparto_id: number;
  descrizione: string;
  importo_totale: number;
  base_riparto_descrizione: string;
  periodo: string;
  allocazioni: AllocazioneRiga[];
}

export const centriCostoApi = {
  listCentri: (soloAttivi = false) =>
    api.get<CentroCosto[]>(`/centri-costo/centri?solo_attivi=${soloAttivi}`),
  createCentro: (payload: CentroCostoCreate) => api.post<CentroCosto>("/centri-costo/centri", payload),
  updateCentro: (id: number, payload: Partial<CentroCostoCreate> & { attivo?: boolean }) =>
    api.put<CentroCosto>(`/centri-costo/centri/${id}`, payload),
  deleteCentro: (id: number) => api.delete(`/centri-costo/centri/${id}`),

  listBasiRiparto: () => api.get<BaseRiparto[]>("/centri-costo/basi-riparto"),
  createBaseRiparto: (payload: BaseRipartoCreate) => api.post<BaseRiparto>("/centri-costo/basi-riparto", payload),

  listValoriBase: (base_riparto_id?: number, periodo?: string) => {
    const p = new URLSearchParams();
    if (base_riparto_id) p.set("base_riparto_id", String(base_riparto_id));
    if (periodo) p.set("periodo", periodo);
    const qs = p.toString();
    return api.get<ValoreBaseRiparto[]>(`/centri-costo/valori-base${qs ? `?${qs}` : ""}`);
  },
  createValoreBase: (payload: ValoreBaseRipartoCreate) =>
    api.post<ValoreBaseRiparto>("/centri-costo/valori-base", payload),
  deleteValoreBase: (id: number) => api.delete(`/centri-costo/valori-base/${id}`),

  listRiparti: (periodo?: string) =>
    api.get<RipartoCostoIndiretto[]>(`/centri-costo/riparti${periodo ? `?periodo=${periodo}` : ""}`),
  createRiparto: (payload: RipartoCostoIndirettoCreate) =>
    api.post<RipartoCostoIndiretto>("/centri-costo/riparti", payload),
  deleteRiparto: (id: number) => api.delete(`/centri-costo/riparti/${id}`),
  calcolaRiparto: (id: number) => api.get<RipartoCalcolato>(`/centri-costo/riparti/${id}/calcolo`),
};

// ── Prodotti, Distinta Base e Magazzino Industriale ─────────────────────────────

export type TipoProdotto = "materia_prima" | "semilavorato" | "prodotto_finito";

export interface Prodotto {
  id: number;
  codice: string;
  descrizione: string;
  tipo: TipoProdotto;
  unita_misura: string;
  giacenza_attuale: number;
  costo_medio_ponderato: number;
  scorta_minima: number;
  prezzo_standard: number;
  ore_manodopera_standard: number;
  costo_orario_manodopera_standard: number;
  costo_indiretto_standard_unitario: number;
  costo_standard_materiale_unitario: number;
  costo_standard_unitario_totale: number;
  prezzo_vendita: number | null;
  sotto_scorta: boolean;
  attivo: boolean;
  note: string | null;
  created_at: string;
}

export interface ProdottoCreate {
  codice: string;
  descrizione: string;
  tipo: TipoProdotto;
  unita_misura?: string;
  scorta_minima?: number;
  prezzo_standard?: number;
  ore_manodopera_standard?: number;
  costo_orario_manodopera_standard?: number;
  costo_indiretto_standard_unitario?: number;
  prezzo_vendita?: number;
  note?: string;
}

export interface DistintaBaseRiga {
  id: number;
  prodotto_padre_id: number;
  componente_id: number;
  componente_codice: string;
  componente_descrizione: string;
  componente_tipo: TipoProdotto;
  quantita: number;
  costo_standard_componente: number;
  note: string | null;
}

export interface DistintaBaseRigaCreate {
  componente_id: number;
  quantita: number;
  note?: string;
}

export interface DistintaBaseResponse {
  prodotto_id: number;
  descrizione: string;
  righe: DistintaBaseRiga[];
  costo_materiale_unitario: number;
}

export type TipoMovimentoMagazzino = "carico" | "scarico";

export interface MovimentoMagazzino {
  id: number;
  prodotto_id: number;
  prodotto_codice: string;
  prodotto_descrizione: string;
  data: string;
  tipo: TipoMovimentoMagazzino;
  quantita: number;
  costo_unitario: number;
  importo: number;
  causale: string;
  commessa_id: number | null;
  created_at: string;
}

export interface MovimentoMagazzinoCreate {
  prodotto_id: number;
  data: string;
  tipo: TipoMovimentoMagazzino;
  quantita: number;
  costo_unitario?: number;
  causale: string;
  commessa_id?: number;
}

export interface GiacenzaMagazzino {
  prodotto_id: number;
  codice: string;
  descrizione: string;
  tipo: TipoProdotto;
  unita_misura: string;
  giacenza_attuale: number;
  costo_medio_ponderato: number;
  valore_giacenza: number;
  scorta_minima: number;
  sotto_scorta: boolean;
}

export const prodottiApi = {
  list: (tipo?: TipoProdotto, soloAttivi = false) => {
    const p = new URLSearchParams();
    if (tipo) p.set("tipo", tipo);
    if (soloAttivi) p.set("solo_attivi", "true");
    const qs = p.toString();
    return api.get<Prodotto[]>(`/prodotti/${qs ? `?${qs}` : ""}`);
  },
  get: (id: number) => api.get<Prodotto>(`/prodotti/${id}`),
  create: (payload: ProdottoCreate) => api.post<Prodotto>("/prodotti/", payload),
  update: (id: number, payload: Partial<ProdottoCreate> & { attivo?: boolean }) =>
    api.put<Prodotto>(`/prodotti/${id}`, payload),
  delete: (id: number) => api.delete(`/prodotti/${id}`),

  getDistintaBase: (prodottoId: number) =>
    api.get<DistintaBaseResponse>(`/prodotti/${prodottoId}/distinta-base`),
  aggiungiComponente: (prodottoId: number, payload: DistintaBaseRigaCreate) =>
    api.post<DistintaBaseRiga>(`/prodotti/${prodottoId}/distinta-base`, payload),
  rimuoviComponente: (rigaId: number) => api.delete(`/prodotti/distinta-base/${rigaId}`),

  listMovimenti: (prodottoId?: number, skip = 0, limit = 200) => {
    const p = new URLSearchParams({ skip: String(skip), limit: String(limit) });
    if (prodottoId) p.set("prodotto_id", String(prodottoId));
    return api.get<MovimentoMagazzino[]>(`/prodotti/magazzino/movimenti?${p}`);
  },
  registraMovimento: (payload: MovimentoMagazzinoCreate) =>
    api.post<MovimentoMagazzino>("/prodotti/magazzino/movimenti", payload),
  getGiacenze: (soloSottoScorta = false) =>
    api.get<GiacenzaMagazzino[]>(`/prodotti/magazzino/giacenze?solo_sotto_scorta=${soloSottoScorta}`),
};

// ── Commesse (Job Costing) ───────────────────────────────────────────────────

export type StatoCommessa = "aperta" | "chiusa";
export type TipoCostoCommessa = "materiale" | "manodopera" | "indiretto";

export interface Commessa {
  id: number;
  codice: string;
  descrizione: string;
  prodotto_id: number;
  prodotto_codice: string;
  prodotto_descrizione: string;
  centro_costo_id: number;
  centro_costo_descrizione: string;
  anagrafica_id: number | null;
  anagrafica_nome: string | null;
  quantita_pianificata: number;
  quantita_prodotta: number | null;
  data_apertura: string;
  data_chiusura: string | null;
  stato: StatoCommessa;
  costo_totale_consuntivo: number;
  note: string | null;
  created_at: string;
}

export interface CommessaCreate {
  codice: string;
  descrizione: string;
  prodotto_id: number;
  centro_costo_id: number;
  anagrafica_id?: number;
  quantita_pianificata: number;
  data_apertura: string;
  note?: string;
}

export interface RigaCostoCommessa {
  id: number;
  commessa_id: number;
  tipo: TipoCostoCommessa;
  descrizione: string;
  data: string;
  prodotto_id: number | null;
  prodotto_descrizione: string | null;
  centro_costo_id: number | null;
  centro_costo_descrizione: string | null;
  quantita: number | null;
  costo_unitario: number | null;
  importo: number;
  created_at: string;
}

export interface RigaCostoCommessaCreate {
  tipo: TipoCostoCommessa;
  descrizione: string;
  data: string;
  prodotto_id?: number;
  centro_costo_id?: number;
  quantita?: number;
  costo_unitario?: number;
  importo?: number;
}

export interface RiepilogoCostiCommessa {
  commessa_id: number;
  totale_materiale: number;
  totale_manodopera: number;
  totale_indiretti: number;
  totale: number;
  costo_unitario: number | null;
}

export interface ScostamentoVoce {
  categoria: "materiale" | "manodopera" | "indiretti";
  standard: number;
  consuntivo: number;
  scostamento: number;
  scostamento_prezzo: number | null;
  scostamento_quantita: number | null;
}

export interface ScostamentiCommessaResponse {
  commessa_id: number;
  codice: string;
  quantita_riferimento: number;
  quantita_riferimento_tipo: "prodotta" | "pianificata";
  voci: ScostamentoVoce[];
  scostamento_totale: number;
}

export const commesseApi = {
  list: (stato?: StatoCommessa) => api.get<Commessa[]>(`/commesse/${stato ? `?stato=${stato}` : ""}`),
  get: (id: number) => api.get<Commessa>(`/commesse/${id}`),
  create: (payload: CommessaCreate) => api.post<Commessa>("/commesse/", payload),
  update: (id: number, payload: Partial<CommessaCreate>) => api.put<Commessa>(`/commesse/${id}`, payload),
  chiudi: (id: number, data_chiusura: string, quantita_prodotta: number) =>
    api.post<Commessa>(`/commesse/${id}/chiudi`, { data_chiusura, quantita_prodotta }),
  delete: (id: number) => api.delete(`/commesse/${id}`),

  listCosti: (commessaId: number) => api.get<RigaCostoCommessa[]>(`/commesse/${commessaId}/costi`),
  aggiungiCosto: (commessaId: number, payload: RigaCostoCommessaCreate) =>
    api.post<RigaCostoCommessa>(`/commesse/${commessaId}/costi`, payload),
  eliminaCosto: (rigaId: number) => api.delete(`/commesse/costi/${rigaId}`),

  riepilogo: (commessaId: number) => api.get<RiepilogoCostiCommessa>(`/commesse/${commessaId}/riepilogo`),
  scostamenti: (commessaId: number) => api.get<ScostamentiCommessaResponse>(`/commesse/${commessaId}/scostamenti`),
};

// ── Controllo di Gestione (Break-even / Direct Costing) ─────────────────────────

export interface ClassificazioneCosto {
  conto_id: number;
  conto_codice: string;
  conto_descrizione: string;
  tipo_conto: string;
  classificazione: "fisso" | "variabile" | null;
}

export interface BreakEvenAziendale {
  totale_ricavi: number;
  totale_costi_fissi: number;
  totale_costi_variabili: number;
  margine_di_contribuzione: number;
  percentuale_margine_di_contribuzione: number;
  punto_di_pareggio_valore: number | null;
  conti_non_classificati: number;
}

export interface BreakEvenProdotto {
  prodotto_id: number;
  codice: string;
  descrizione: string;
  prezzo_vendita: number;
  costo_variabile_unitario: number;
  margine_di_contribuzione_unitario: number;
  percentuale_margine_di_contribuzione: number;
  quota_costi_fissi_aziendali: number;
  punto_di_pareggio_quantita: number | null;
}

export interface KPIIndustriale {
  numero_commesse_aperte: number;
  valore_commesse_aperte: number;
  numero_commesse_chiuse_periodo: number;
  scostamento_totale_periodo: number;
  valore_magazzino: number;
  numero_prodotti_sotto_scorta: number;
  margine_di_contribuzione_percentuale: number | null;
}

export const controlloGestioneApi = {
  listClassificazioni: () => api.get<ClassificazioneCosto[]>("/controllo-gestione/classificazioni"),
  setClassificazione: (conto_id: number, classificazione: "fisso" | "variabile") =>
    api.post<ClassificazioneCosto>("/controllo-gestione/classificazioni", { conto_id, classificazione }),
  breakEven: () => api.get<BreakEvenAziendale>("/controllo-gestione/break-even"),
  breakEvenProdotti: () => api.get<BreakEvenProdotto[]>("/controllo-gestione/break-even/prodotti"),
  kpi: (anno?: number) => api.get<KPIIndustriale>(`/controllo-gestione/kpi${anno ? `?anno=${anno}` : ""}`),
};

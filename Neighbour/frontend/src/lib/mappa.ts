// Colori e nomi dei contenuti sulla mappa (senza dipendenze da Leaflet) — © Roberto Di Flumeri

export interface PuntoMappa {
  id: string; genere: string; tipo: string; titolo: string; link: string; lat: number; lon: number; distanza?: string;
  categoria?: string; inizio?: string;
}

export const COLORI: Record<string, string> = {
  offerta: "#15803d", richiesta: "#15803d", regalo: "#65a34a", scambio: "#65a34a", aiuto: "#ea7a1a",
  segnalazione: "#a16207", evento: "#0b6b3a", servizio: "#2563eb",
};

export const NOMI: Record<string, string> = {
  offerta: "Offro", richiesta: "Cerco", regalo: "Regalo", scambio: "Scambio", aiuto: "Aiuto",
  segnalazione: "Segnalazione", evento: "Evento", servizio: "Servizio",
};

// Città di partenza per scegliere il quartiere senza servizi di geocodifica esterni
export const CITTA: Record<string, [number, number]> = {
  Milano: [45.4642, 9.19], Roma: [41.9028, 12.4964], Napoli: [40.8518, 14.2681], Torino: [45.0703, 7.6869],
  Bologna: [44.4949, 11.3426], Firenze: [43.7696, 11.2558], Genova: [44.4056, 8.9463], Palermo: [38.1157, 13.3615],
  Bari: [41.1171, 16.8719], Venezia: [45.4408, 12.3155], Verona: [45.4384, 10.9916], Padova: [45.4064, 11.8768],
};

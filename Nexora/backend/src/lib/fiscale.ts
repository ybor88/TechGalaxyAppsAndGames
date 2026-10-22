// Validazioni fiscali italiane (gratuite, senza servizi esterni). © Roberto Di Flumeri

/** Partita IVA italiana: 11 cifre con cifra di controllo (algoritmo di Luhn modificato). */
export function partitaIvaValida(piva: string): boolean {
  const p = piva.replace(/^IT/i, "").trim();
  if (!/^\d{11}$/.test(p)) return false;
  let somma = 0;
  for (let i = 0; i < 10; i++) {
    let c = Number(p[i]);
    if (i % 2 === 1) {
      c *= 2;
      if (c > 9) c -= 9;
    }
    somma += c;
  }
  return (10 - (somma % 10)) % 10 === Number(p[10]);
}

/** Codice fiscale: 16 caratteri alfanumerici (persona) con carattere di controllo, oppure 11 cifre (azienda). */
export function codiceFiscaleValido(cf: string): boolean {
  const c = cf.toUpperCase().trim();
  if (/^\d{11}$/.test(c)) return partitaIvaValida(c);
  if (!/^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$/.test(c)) return false;
  const dispari: Record<string, number> = {
    0: 1, 1: 0, 2: 5, 3: 7, 4: 9, 5: 13, 6: 15, 7: 17, 8: 19, 9: 21, A: 1, B: 0, C: 5, D: 7, E: 9, F: 13, G: 15, H: 17, I: 19,
    J: 21, K: 2, L: 4, M: 18, N: 20, O: 11, P: 3, Q: 6, R: 8, S: 12, T: 14, U: 16, V: 10, W: 22, X: 25, Y: 24, Z: 23,
  };
  let somma = 0;
  for (let i = 0; i < 15; i++) {
    const ch = c[i];
    if (i % 2 === 0) somma += dispari[ch];
    else somma += /\d/.test(ch) ? Number(ch) : ch.charCodeAt(0) - 65;
  }
  return String.fromCharCode(65 + (somma % 26)) === c[15];
}

/** Codice destinatario SDI: 7 caratteri (6 per la PA). */
export const codiceDestinatarioValido = (c: string) => /^[A-Z0-9]{6,7}$/.test(c.toUpperCase());

export const ALIQUOTE_IVA = [22, 10, 5, 4, 0];
/** Nature IVA per le operazioni senza imposta (codici in vigore dal 2021). */
export const NATURE_IVA: Record<string, string> = {
  N1: "Escluse ex art. 15", "N2.1": "Non soggette (artt. 7-7septies)", "N2.2": "Non soggette, altri casi",
  "N3.1": "Non imponibili: esportazioni", "N3.2": "Non imponibili: cessioni intracomunitarie", "N3.5": "Non imponibili: dichiarazione d'intento",
  N4: "Esenti", N5: "Regime del margine", "N6.1": "Inversione contabile: rottami", "N6.9": "Inversione contabile: altri casi",
  N7: "IVA assolta in altro stato UE",
};

// © Roberto Di Flumeri
import { BadRequestException } from '@nestjs/common';

/** Copia solo i campi ammessi (evita che il client imposti campi non previsti, es. condominioId). */
export function soloCampi<T extends object, K extends keyof T>(obj: T | undefined | null, campi: readonly K[]): Partial<Pick<T, K>> {
  const out: Partial<Pick<T, K>> = {};
  if (!obj) return out;
  for (const c of campi) {
    if (obj[c] !== undefined) out[c] = obj[c];
  }
  return out;
}

/** Verifica che un valore (se presente) sia tra quelli ammessi. */
export function valoreAmmesso(nome: string, valore: unknown, ammessi: readonly string[]): void {
  if (valore === undefined || valore === null) return;
  if (typeof valore !== 'string' || !ammessi.includes(valore)) {
    throw new BadRequestException(`${nome} non valido. Valori ammessi: ${ammessi.join(', ')}`);
  }
}

/** Converte una data ISO in Date, rifiutando valori non interpretabili. */
export function dataValida(nome: string, valore: string | undefined | null): Date | undefined {
  if (valore === undefined || valore === null || valore === '') return undefined;
  const d = new Date(valore);
  if (Number.isNaN(d.getTime())) throw new BadRequestException(`${nome} non è una data valida`);
  return d;
}

export const TIPI_COMUNICAZIONE = ['avviso', 'assemblea', 'manutenzione', 'emergenza', 'circolare'] as const;
export const DESTINATARI_COMUNICAZIONE = ['tutti', 'proprietari', 'inquilini'] as const;
export const TIPI_ASSEMBLEA = ['ordinaria', 'straordinaria'] as const;
export const STATI_ASSEMBLEA = ['convocata', 'in_corso', 'conclusa', 'annullata'] as const;
export const CATEGORIE_DOCUMENTO = [
  'regolamento', 'verbali', 'fatture_pagamenti', 'contratti', 'certificazioni', 'polizze', 'planimetrie',
] as const;
export const VISIBILITA_DOCUMENTO = ['pubblica', 'privata', 'selettiva'] as const;
export const STATI_TICKET = ['Nuova', 'In lavorazione', 'Assegnata', 'Risolta', 'Chiusa'] as const;
export const PRIORITA_TICKET = ['bassa', 'media', 'alta', 'urgente'] as const;
export const CATEGORIE_TICKET = ['idraulica', 'elettrica', 'pulizie', 'sicurezza', 'strutturale', 'altro'] as const;

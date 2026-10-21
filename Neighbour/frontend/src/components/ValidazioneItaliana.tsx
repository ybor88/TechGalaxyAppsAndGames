"use client";
// Messaggi di validazione dei moduli in italiano, indipendenti dalla lingua del browser. © Roberto Di Flumeri
import { useEffect } from "react";

type Campo = HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;

function messaggio(el: Campo): string {
  const v = el.validity;
  if (v.valueMissing) {
    if (el instanceof HTMLInputElement && el.type === "checkbox") return "Seleziona questa casella per continuare.";
    if (el instanceof HTMLSelectElement) return "Seleziona un'opzione.";
    return "Compila questo campo.";
  }
  if (v.typeMismatch && el instanceof HTMLInputElement && el.type === "email") return "Inserisci un indirizzo email valido (es. nome@esempio.it).";
  if (v.typeMismatch && el instanceof HTMLInputElement && el.type === "url") return "Inserisci un indirizzo web valido.";
  if (v.tooShort && !(el instanceof HTMLSelectElement)) {
    const min = el.minLength;
    const att = el.value.length;
    return `Inserisci almeno ${min} caratteri (ora ${att === 1 ? "ne hai inserito 1" : `ne hai inseriti ${att}`}).`;
  }
  if (v.tooLong && !(el instanceof HTMLSelectElement)) return `Puoi inserire al massimo ${el.maxLength} caratteri.`;
  if (v.badInput) return "Inserisci un numero valido.";
  if (v.rangeUnderflow && el instanceof HTMLInputElement) return `Il valore minimo è ${el.min}.`;
  if (v.rangeOverflow && el instanceof HTMLInputElement) return `Il valore massimo è ${el.max}.`;
  if (v.stepMismatch) return "Inserisci un valore valido.";
  if (v.patternMismatch) return el.title || "Il formato non è valido.";
  return "";
}

function eCampo(t: EventTarget | null): t is Campo {
  return t instanceof HTMLInputElement || t instanceof HTMLTextAreaElement || t instanceof HTMLSelectElement;
}

export default function ValidazioneItaliana() {
  useEffect(() => {
    const suInvalido = (e: Event) => {
      if (!eCampo(e.target)) return;
      e.target.setCustomValidity("");
      const m = messaggio(e.target);
      if (m) e.target.setCustomValidity(m);
    };
    // appena l'utente modifica il campo il messaggio personalizzato va tolto, altrimenti resterebbe invalido
    const suModifica = (e: Event) => {
      if (eCampo(e.target)) e.target.setCustomValidity("");
    };
    document.addEventListener("invalid", suInvalido, true);
    document.addEventListener("input", suModifica, true);
    document.addEventListener("change", suModifica, true);
    return () => {
      document.removeEventListener("invalid", suInvalido, true);
      document.removeEventListener("input", suModifica, true);
      document.removeEventListener("change", suModifica, true);
    };
  }, []);
  return null;
}

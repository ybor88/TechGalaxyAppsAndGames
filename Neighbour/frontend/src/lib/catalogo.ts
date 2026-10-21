"use client";
// Vocabolari dal server (tipi, categorie, motivi): un'unica fonte per app e backend — © Roberto Di Flumeri
import { useEffect, useState } from "react";
import { api, type Catalogo } from "./api";

let cache: Catalogo | null = null;
let inCorso: Promise<Catalogo> | null = null;

export function useCatalogo(): Catalogo | null {
  const [c, setC] = useState<Catalogo | null>(cache);
  useEffect(() => {
    if (cache) return;
    inCorso = inCorso || api<Catalogo>("/v1/catalogo", { pubblica: true });
    inCorso.then((v) => { cache = v; setC(v); }).catch(() => { inCorso = null; });
  }, []);
  return c;
}

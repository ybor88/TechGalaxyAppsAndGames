"""
Elaborazione del testo e ricerca (BM25) sulle fonti autorizzate dall'utente.
Nessuna dipendenza esterna: l'indice è lessicale e locale.

© Roberto Di Flumeri
"""

import math
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass, field

STOPWORDS = set("""
a ad al allo ai agli all alla alle con col coi da dal dallo dai dagli dall dalla dalle di del dello dei degli
dell della delle in nel nello nei negli nell nella nelle su sul sullo sui sugli sull sulla sulle per tra fra
il lo la i gli le l un uno una un e ed o od ma se che chi cui non piu più quale quali quanto quanta quanti
quante questo questa questi queste quello quella quelli quelle io tu lui lei noi voi loro mi ti ci vi si
me te se ne mio mia miei mie tuo tua tuoi tue suo sua suoi sue nostro nostra vostro vostra sono sei e siamo
siete era ero eri erano essere ho hai ha abbiamo avete hanno avere fare faccio fai fa come anche molto poco
piu tanto tutto tutti tutta tutte cosa cose quando dove perche perché sempre mai gia già ancora solo poi
allora quindi cioe cioè ogni altro altra altri altre stesso stessa the and of to is
""".split())


def normalizza(testo: str) -> str:
    t = unicodedata.normalize("NFKD", testo.lower())
    return "".join(c for c in t if not unicodedata.combining(c))


def radice(parola: str) -> str:
    """Stemming leggero per l'italiano: rimuove le desinenze più comuni."""
    for suff in ("amento", "imento", "azione", "azioni", "mente", "abile", "ibile", "ando", "endo",
                 "are", "ere", "ire", "ato", "ata", "ati", "ate", "ito", "ita", "iti", "ite", "oso", "osa"):
        if len(parola) > len(suff) + 3 and parola.endswith(suff):
            return parola[: -len(suff)]
    if len(parola) > 4 and parola[-1] in "aeiou":
        return parola[:-1]
    return parola


# parole tipiche delle domande che non dicono nulla sul tema ("cosa ne penso dei debiti?")
PAROLE_DOMANDA = set("""
penso pensi pensa pensare dimmi dici dire secondo sai sapere senti sento sentire raccontami racconta
parlami spiegami opinione idea vorresti potresti dovresti riesci puoi posso devo fammi
""".split())


def tokenizza(testo: str, domanda: bool = False) -> list[str]:
    parole = re.findall(r"[a-z0-9]+", normalizza(testo))
    escluse = STOPWORDS | PAROLE_DOMANDA if domanda else STOPWORDS
    return [radice(p) for p in parole if len(p) > 2 and p not in escluse]


@dataclass
class Documento:
    id: str
    tipo: str  # dichiarazione | conoscenza
    testo: str
    categoria: str
    peso: int = 3
    titolo: str | None = None
    token: list[str] = field(default_factory=list)


@dataclass
class Risultato:
    doc: Documento
    punteggio: float
    copertura: float = 1.0  # quota dei termini della domanda trovati nel documento


class IndiceBM25:
    def __init__(self, documenti: list[Documento], k1: float = 1.4, b: float = 0.75):
        self.docs = documenti
        for d in self.docs:
            d.token = tokenizza(f"{d.titolo or ''} {d.testo} {d.categoria}")
        self.k1, self.b = k1, b
        self.n = len(self.docs)
        self.lung_media = (sum(len(d.token) for d in self.docs) / self.n) if self.n else 0
        df: Counter = Counter()
        for d in self.docs:
            df.update(set(d.token))
        self.idf = {t: math.log(1 + (self.n - f + 0.5) / (f + 0.5)) for t, f in df.items()}

    def _espandi(self, q: list[str]) -> list[tuple[str, float]]:
        """Termini della domanda con peso; se un termine non compare mai si cercano parole che lo
        contengono (es. "debit" → "indebitarm"), con peso ridotto."""
        termini: list[tuple[str, float]] = []
        for t in dict.fromkeys(q):
            if t in self.idf:
                termini.append((t, 1.0))
            elif len(t) >= 5:
                termini.extend((v, 0.7) for v in self.idf if t in v)
        return termini

    def cerca(self, domanda: str, k: int = 5, soglia: float = 0.0, relativa: float = 0.0) -> list[Risultato]:
        """`relativa`: scarta i risultati con punteggio inferiore a questa frazione del migliore."""
        q = tokenizza(domanda, domanda=True)
        if not q or not self.n:
            return []
        termini = self._espandi(q)
        risultati = []
        for d in self.docs:
            tf = Counter(d.token)
            s = 0.0
            coperti = set()
            for t, fattore in termini:
                if t not in tf:
                    continue
                f = tf[t]
                s += fattore * self.idf[t] * f * (self.k1 + 1) / (f + self.k1 * (1 - self.b + self.b * len(d.token) / (self.lung_media or 1)))
                coperti.add(t)
            if s > soglia:
                # le dichiarazioni più importanti per l'utente pesano un po' di più
                r = Risultato(d, s * (1 + (d.peso - 3) * 0.08))
                r.copertura = len(coperti) / len(set(q))
                risultati.append(r)
        risultati.sort(key=lambda r: r.punteggio, reverse=True)
        if risultati and relativa:
            minimo = risultati[0].punteggio * relativa
            risultati = [r for r in risultati if r.punteggio >= minimo]
        return risultati[:k]


def somiglianza(a: str, b: str) -> float:
    """Indice di Jaccard tra i termini significativi di due testi (0-1)."""
    ta, tb = set(tokenizza(a)), set(tokenizza(b))
    if not ta or not tb:
        return 0.0
    return len(ta & tb) / len(ta | tb)

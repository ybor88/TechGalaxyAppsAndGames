"""
Controlli etici e di sicurezza sui contenuti (specifica §2, §8, §11):
- separazione istruzioni/dati e neutralizzazione di prompt injection nelle fonti;
- niente imitazione di terzi, niente diagnosi o consulenze professionali;
- niente inferenza di attributi sensibili non dichiarati;
- indicazioni di aiuto in caso di frasi di crisi.

© Roberto Di Flumeri
"""

import re
from dataclasses import dataclass, field

from app.services.testo import normalizza

_INJECTION = [
    r"ignora (tutte )?(le )?(istruzioni|regole)( precedenti)?",
    r"dimentica (tutte )?(le )?(istruzioni|regole)",
    r"ignore (all |the )?(previous |above )?instructions",
    r"disregard (all |the )?(previous |above )?instructions",
    r"(nuove|new) istruzioni\s*:",
    r"system prompt",
    r"\bsei ora\b|\bda ora in poi sei\b|\byou are now\b",
    r"<\|?(system|im_start|im_end)\|?>",
    r"^\s*(system|assistant)\s*:",
    r"###\s*(istruzion|instruction)",
]
_RE_INJECTION = re.compile("|".join(_INJECTION), re.IGNORECASE | re.MULTILINE)


def sanifica_fonte(testo: str) -> tuple[str, bool]:
    """Neutralizza frasi che tentano di dare istruzioni al modello. Restituisce (testo, sospetto)."""
    sospetto = bool(_RE_INJECTION.search(testo))
    pulito = _RE_INJECTION.sub("[istruzione rimossa]", testo)
    # i delimitatori usati nel prompt non possono comparire nei dati
    pulito = pulito.replace("<dati>", "").replace("</dati>", "").replace("```", "'''")
    return pulito, sospetto


_NON_TERZI = r"(?!\s+(me|me stesso|me stessa|io|te stesso|te stessa|il mio alter ego|la mia versione|una versione di me))"
_TERZI = re.compile(
    # "fingi di essere mio padre", "rispondi come se fossi Elon Musk"
    r"\b(fingi|fai finta|immagina|rispondi|parla|comportati|agisci|scrivi)\b.{0,25}\b(di essere|come se fossi|come se tu fossi)\b" + _NON_TERZI +
    # "parla come mia madre", "scrivi come un famoso cantante"
    r"|\b(parla|rispondi|scrivi|ragiona)\s+come\s+(mio|mia|il mio|la mia|un|una|lo|il|la)\b(?!\s+(mio\s+|mia\s+)?(alter ego|versione))"
    r"|\bimpersona\b|\bimita\b",
    re.IGNORECASE,
)
_CLINICO = re.compile(
    r"diagnos|depress|disturbo|bipolar|ansia patolog|psichiatr|psicofarmac|farmac|terapia|sintom|malatti|"
    r"curare|guarire|psicolog|autismo|adhd|narcisis",
    re.IGNORECASE,
)
_PROFESSIONALE = re.compile(
    r"avvocat|causa legale|fare causa|denuncia|querela|ricorso|investir|investiment|azioni in borsa|"
    r"criptovalut|bitcoin|mutuo|prestito|tasse da pagare|dichiarazione dei redditi",
    re.IGNORECASE,
)
_SENSIBILI = re.compile(
    r"(sono|e|è|sarei|potrei essere)\s+(gay|lesbica|omosessuale|bisessuale|etero|eterosessuale|trans)\b|"
    r"orientamento sessuale|che religione|in cosa credo|quale religione|per chi (voto|voterei)|che partito|"
    r"orientamento politico|che etnia|di che razza|sono malat|ho una malattia|stato di salute",
    re.IGNORECASE,
)
_CRISI = re.compile(
    r"suicid|togliermi la vita|farla finita|farmi del male|non voglio piu vivere|uccidermi",
    re.IGNORECASE,
)

MESSAGGIO_CRISI = (
    "Mi dispiace che tu stia attraversando un momento così difficile. Io sono solo una simulazione "
    "e non posso aiutarti come serve: parlane subito con qualcuno di cui ti fidi o contatta "
    "Telefono Amico Italia (02 2327 2327) o il Telefono Azzurro (19696). "
    "In caso di pericolo immediato chiama il 112."
)
AVVISO_PROFESSIONALE = (
    "Nota: non sono un professionista e non sostituisco medici, psicologi, avvocati o consulenti "
    "finanziari. Posso solo riflettere su ciò che mi hai dichiarato."
)
RIFIUTO_TERZI = (
    "Non posso imitare altre persone: rappresento solo te, a partire dai dati che hai scelto "
    "di condividere con me."
)
RIFIUTO_SENSIBILI = (
    "Non deduco caratteristiche personali delicate (salute, orientamento, religione, idee politiche, "
    "origine) che non mi hai dichiarato esplicitamente."
)


@dataclass
class Valutazione:
    blocca: bool = False
    risposta_fissa: str | None = None
    avvisi: list[str] = field(default_factory=list)
    motivo: str | None = None


def valuta_domanda(domanda: str, testi_dichiarati: list[str]) -> Valutazione:
    d = normalizza(domanda)
    if _CRISI.search(d):
        return Valutazione(blocca=True, risposta_fissa=MESSAGGIO_CRISI, motivo="crisi")
    if _TERZI.search(d):
        return Valutazione(blocca=True, risposta_fissa=RIFIUTO_TERZI, motivo="imitazione_terzi")
    m = _SENSIBILI.search(d)
    if m:
        # consentito solo se l'utente ha dichiarato esplicitamente qualcosa sul tema
        tema = m.group(0)
        dichiarato = any(any(p in normalizza(t) for p in tema.split() if len(p) > 4) for t in testi_dichiarati)
        if not dichiarato:
            return Valutazione(blocca=True, risposta_fissa=RIFIUTO_SENSIBILI, motivo="attributi_sensibili")
    v = Valutazione()
    if _CLINICO.search(d) or _PROFESSIONALE.search(d):
        v.avvisi.append(AVVISO_PROFESSIONALE)
        v.motivo = "consulenza_professionale"
    return v

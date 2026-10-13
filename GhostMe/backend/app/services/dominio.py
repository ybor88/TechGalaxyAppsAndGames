"""
Logica di dominio deterministica e spiegabile:
- simulatore di decisioni (F03): punteggi pesati sui criteri dichiarati, pro/contro, fonti;
- versioni del profilo (F04/F10): snapshot immutabili, confronto, simulazioni future etichettate;
- compatibilità consensuale (F05): criteri visibili, nessun giudizio assoluto.

© Roberto Di Flumeri
"""

import hashlib
import json
from datetime import date

from app.services.catalogo import CATEGORIE
from app.services.testo import somiglianza

AVVISO_DECISIONE = (
    "Questa è una simulazione basata su ciò che hai dichiarato: evidenzia la coerenza delle opzioni "
    "con le tue priorità, ma non decide al posto tuo."
)

# ── Simulatore di decisioni ──────────────────────────────────────────────────


def suggerisci_criteri(dichiarazioni: list[dict], massimo: int = 5) -> list[dict]:
    candidate = [d for d in dichiarazioni if d["categoria"] in ("valori", "obiettivi")]
    candidate.sort(key=lambda d: d["peso"], reverse=True)
    return [
        {"nome": d["testo"] if len(d["testo"]) <= 80 else d["testo"][:77] + "…", "peso": d["peso"], "statement_id": d["id"]}
        for d in candidate[:massimo]
    ]


def stima_valutazione(opzione: dict, criterio: dict, testo_dichiarazione: str | None) -> int:
    """Stima neutra (3) corretta dall'affinità testuale tra opzione e criterio dichiarato."""
    testo_opzione = f"{opzione['nome']} {opzione.get('descrizione') or ''}"
    riferimento = f"{criterio['nome']} {testo_dichiarazione or ''}"
    sim = somiglianza(testo_opzione, riferimento)
    if sim >= 0.3:
        return 5
    if sim >= 0.12:
        return 4
    return 3


def analizza_decisione(opzioni: list[dict], criteri: list[dict], valutazioni: dict, dichiarazioni_per_id: dict) -> tuple[dict, dict]:
    """
    valutazioni: {"<indice opzione>": {"<indice criterio>": 1..5}} (anche parziali).
    Restituisce (risultato, valutazioni_complete) dove le celle mancanti sono stimate e marcate.
    """
    complete: dict = {}
    stimate: list[tuple[int, int]] = []
    for i, op in enumerate(opzioni):
        riga = {}
        for j, cr in enumerate(criteri):
            v = (valutazioni.get(str(i)) or {}).get(str(j))
            if v is None:
                testo = dichiarazioni_per_id.get(cr.get("statement_id") or "", {}).get("testo")
                v = stima_valutazione(op, cr, testo)
                stimate.append((i, j))
            riga[str(j)] = int(v)
        complete[str(i)] = riga

    peso_tot = sum(c["peso"] for c in criteri) or 1
    analisi = []
    for i, op in enumerate(opzioni):
        punteggio = sum(criteri[j]["peso"] * complete[str(i)][str(j)] for j in range(len(criteri)))
        percentuale = round(punteggio / (peso_tot * 5) * 100)
        pro = [criteri[j]["nome"] for j in range(len(criteri)) if complete[str(i)][str(j)] >= 4]
        contro = [criteri[j]["nome"] for j in range(len(criteri)) if complete[str(i)][str(j)] <= 2]
        coerenza = "alta" if percentuale >= 75 else "media" if percentuale >= 50 else "bassa"
        analisi.append({"indice": i, "opzione": op["nome"], "coerenza_percentuale": percentuale,
                        "coerenza": coerenza, "pro": pro, "contro": contro})
    ordinate = sorted(analisi, key=lambda a: a["coerenza_percentuale"], reverse=True)

    if len(ordinate) >= 2 and ordinate[0]["coerenza_percentuale"] - ordinate[1]["coerenza_percentuale"] < 5:
        sintesi = (f"«{ordinate[0]['opzione']}» e «{ordinate[1]['opzione']}» risultano quasi equivalenti "
                   "rispetto ai criteri indicati: conviene chiarire quale criterio pesa davvero di più.")
    else:
        sintesi = (f"«{ordinate[0]['opzione']}» è l'opzione più coerente con i criteri e i pesi che hai indicato "
                   f"({ordinate[0]['coerenza_percentuale']}%).")

    fonti = []
    for cr in criteri:
        d = dichiarazioni_per_id.get(cr.get("statement_id") or "")
        if d:
            fonti.append({"id": d["id"], "tipo": "dichiarazione", "categoria": d["categoria"], "estratto": d["testo"][:160]})

    risultato = {
        "classifica": ordinate,
        "sintesi": sintesi,
        "celle_stimate": [{"opzione": i, "criterio": j} for i, j in stimate],
        "nota_stime": ("Alcune valutazioni sono stime automatiche neutre (3) o basate sull'affinità del testo: "
                       "modificale per un risultato più fedele.") if stimate else None,
        "fonti": fonti,
        "avviso": AVVISO_DECISIONE,
    }
    return risultato, complete


# ── Versioni del profilo ─────────────────────────────────────────────────────


def impronta(dati: list[dict]) -> str:
    canonico = json.dumps(sorted(dati, key=lambda d: d["id"]), sort_keys=True, ensure_ascii=False, default=str)
    return hashlib.sha256(canonico.encode("utf-8")).hexdigest()


def riepilogo_profilo(dati: list[dict]) -> str:
    if not dati:
        return "Profilo vuoto."
    conteggi = {}
    for d in dati:
        conteggi[d["categoria"]] = conteggi.get(d["categoria"], 0) + 1
    parti = [f"{CATEGORIE.get(c, c)}: {n}" for c, n in sorted(conteggi.items(), key=lambda x: -x[1])]
    principali = sorted(dati, key=lambda d: d["peso"], reverse=True)[:3]
    return (f"{len(dati)} dichiarazioni ({', '.join(parti)}). "
            "Più importanti: " + "; ".join(d["testo"][:80] for d in principali) + ".")


def confronta_versioni(a: list[dict], b: list[dict]) -> dict:
    """Differenze tra due versioni: corrispondenza per id o, in mancanza, per chiave del questionario."""
    def chiave(d):
        return d.get("chiave_domanda") or d["id"]

    ia = {chiave(d): d for d in a}
    ib = {chiave(d): d for d in b}
    aggiunte = [ib[k] for k in ib if k not in ia]
    rimosse = [ia[k] for k in ia if k not in ib]
    modificate = []
    for k in ia.keys() & ib.keys():
        prima, dopo = ia[k], ib[k]
        campi = [c for c in ("testo", "peso", "categoria") if prima.get(c) != dopo.get(c)]
        if campi:
            modificate.append({"prima": prima, "dopo": dopo, "campi": campi})
    per_categoria = {}
    for cat in CATEGORIE:
        na = sum(1 for d in a if d["categoria"] == cat)
        nb = sum(1 for d in b if d["categoria"] == cat)
        if na or nb:
            per_categoria[cat] = {"prima": na, "dopo": nb}
    return {"aggiunte": aggiunte, "rimosse": rimosse, "modificate": modificate, "per_categoria": per_categoria,
            "nota": "Il confronto mostra cosa hai dichiarato in momenti diversi: non è una valutazione psicologica."}


def applica_ipotesi(base: list[dict], ipotesi: list[dict]) -> list[dict]:
    """Costruisce una versione simulata applicando ipotesi di cambiamento dichiarate dall'utente."""
    dati = {d["id"]: dict(d) for d in base}
    for n, ip in enumerate(ipotesi):
        azione = ip.get("azione")
        if azione == "aggiungi":
            nid = f"simulata-{n}"
            # i campi assenti arrivano come None dallo schema: si applicano i valori predefiniti
            dati[nid] = {"id": nid, "testo": ip["testo"], "categoria": ip.get("categoria") or "obiettivi",
                         "peso": ip.get("peso") or 3, "fonte": "simulazione", "chiave_domanda": None}
        elif azione == "modifica" and ip.get("id") in dati:
            d = dati[ip["id"]]
            for campo in ("testo", "categoria", "peso"):
                if ip.get(campo) not in (None, ""):
                    d[campo] = ip[campo]
            d["fonte"] = "simulazione"
        elif azione == "rimuovi" and ip.get("id") in dati:
            del dati[ip["id"]]
    return list(dati.values())


# ── Compatibilità ────────────────────────────────────────────────────────────


def calcola_compatibilita(categorie: list[str], a: list[dict], b: list[dict]) -> dict:
    """a e b: dichiarazioni condivisibili dei due utenti. Criteri visibili, nessun punteggio assoluto."""
    dettaglio = []
    for cat in categorie:
        da = [d for d in a if d["categoria"] == cat]
        db = [d for d in b if d["categoria"] == cat]
        comuni = []
        abbinate_b: set[str] = set()
        for x in da:
            migliore = max(db, key=lambda y: somiglianza(x["testo"], y["testo"]), default=None)
            if migliore is not None:
                s = somiglianza(x["testo"], migliore["testo"])
                if s >= 0.15 and migliore["id"] not in abbinate_b:
                    abbinate_b.add(migliore["id"])
                    comuni.append({"tuo": x["testo"], "altro": migliore["testo"]})
        base = max(len(da), len(db))
        quota = len(comuni) / base if base else 0
        descrizione = (
            "dati insufficienti" if not da or not db
            else "molti punti in comune" if quota >= 0.5
            else "alcuni punti in comune" if quota >= 0.2
            else "prospettive diverse"
        )
        dettaglio.append({
            "categoria": cat,
            "etichetta": CATEGORIE.get(cat, cat),
            "descrizione": descrizione,
            "punti_in_comune": comuni[:5],
            "dichiarazioni_confrontate": {"proprietario": len(da), "invitato": len(db)},
        })
    return {
        "criteri": ("Per ogni categoria scelta confrontiamo le dichiarazioni marcate come condivisibili da "
                    "entrambi e contiamo quelle che usano temi simili. Non è un giudizio sulla relazione."),
        "categorie": dettaglio,
        "calcolato_il": date.today().isoformat(),
    }

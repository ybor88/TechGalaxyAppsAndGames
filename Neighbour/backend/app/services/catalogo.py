"""Vocabolari condivisi: tipi di contenuto, categorie, stati e motivi di segnalazione. © Roberto Di Flumeri"""

TIPI_POST = {
    "offerta": "Offro / vendo",
    "richiesta": "Cerco",
    "regalo": "In regalo",
    "scambio": "Scambio",
    "aiuto": "Richiesta di aiuto",
    "segnalazione": "Segnalazione di quartiere",
}
TIPI_MERCATINO = ("offerta", "richiesta", "regalo", "scambio")

CATEGORIE_MERCATINO = {
    "casa": "Casa e arredo", "bambini": "Bambini", "libri": "Libri e musica", "elettronica": "Elettronica",
    "abbigliamento": "Abbigliamento", "sport": "Sport e tempo libero", "giardino": "Piante e giardino",
    "cibo": "Cibo (non deperibile)", "altro": "Altro",
}
CATEGORIE_AIUTO = {
    "spesa": "Spesa e commissioni", "trasporto": "Passaggi e trasporto", "animali": "Animali",
    "tecnologia": "Tecnologia", "compagnia": "Compagnia", "piccoli_lavori": "Piccoli lavori",
    "studio": "Ripetizioni e studio", "altro": "Altro",
}
CATEGORIE_SEGNALAZIONE = {
    "viabilita": "Strade e viabilità", "rifiuti": "Rifiuti e decoro", "smarrito": "Smarriti e ritrovati",
    "lavori": "Lavori in corso", "verde": "Verde pubblico", "sicurezza": "Sicurezza (non emergenze)",
    "avviso": "Avvisi utili", "altro": "Altro",
}
CATEGORIE_EVENTO = {
    "sport": "Sport", "cultura": "Cultura", "bambini": "Bambini e famiglie", "cibo": "Cibo e convivialità",
    "volontariato": "Volontariato", "mercatino": "Mercatini e baratto", "altro": "Altro",
}
CATEGORIE_GRUPPO = {
    "sport": "Sport", "animali": "Animali", "genitori": "Genitori", "hobby": "Hobby",
    "iniziative": "Iniziative di quartiere", "altro": "Altro",
}
CATEGORIE_SERVIZIO = {
    "artigiani": "Artigiani e riparazioni", "negozi": "Negozi", "salute_benessere": "Benessere",
    "lezioni": "Lezioni e corsi", "casa": "Pulizie e casa", "animali": "Servizi per animali",
    "ristorazione": "Bar e ristorazione", "altro": "Altro",
}


def categorie_per_tipo(tipo: str) -> dict[str, str]:
    if tipo in TIPI_MERCATINO:
        return CATEGORIE_MERCATINO
    return {"aiuto": CATEGORIE_AIUTO, "segnalazione": CATEGORIE_SEGNALAZIONE}.get(tipo, CATEGORIE_GRUPPO)


# Stati dei contenuti e transizioni ammesse (F02)
TRANSIZIONI = {
    "bozza": {"pubblicato"},
    "pubblicato": {"prenotato", "chiuso", "risolto"},
    "prenotato": {"pubblicato", "chiuso"},
    "chiuso": set(),
    "risolto": set(),
    "rimosso": set(),
}
STATO_FINALE_PER_TIPO = {"segnalazione": "risolto"}

MOTIVI_SEGNALAZIONE = {
    "spam": "Spam o pubblicità ripetitiva",
    "molestie": "Molestie o linguaggio offensivo",
    "truffa": "Truffa o tentativo di frode",
    "inappropriato": "Contenuto inappropriato",
    "discriminazione": "Discriminazione o incitamento all'odio",
    "privacy": "Dati personali di altre persone",
    "falso": "Informazioni false o fuorvianti",
    "pericolo": "Oggetto o attività pericolosa",
    "altro": "Altro",
}

DECISIONI_MODERAZIONE = {
    "nessuna_violazione": "Nessuna violazione: segnalazione archiviata",
    "rimuovi_contenuto": "Contenuto rimosso",
    "avviso": "Avviso inviato all'utente",
    "sospendi_7_giorni": "Account sospeso per 7 giorni",
    "blocca_utente": "Account bloccato",
}

TIPI_NOTIFICA_QUARTIERE = ("offerta", "richiesta", "regalo", "scambio", "aiuto", "segnalazione", "evento")

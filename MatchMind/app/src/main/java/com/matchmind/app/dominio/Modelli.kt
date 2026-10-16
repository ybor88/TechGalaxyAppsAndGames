package com.matchmind.app.dominio

// Modelli del dominio: sport, leghe, partite, quote, pronostici. © Roberto Di Flumeri

enum class Sport(val percorso: String, val etichetta: String) {
    CALCIO("soccer", "Calcio"),
    BASKET("basketball", "Basket"),
}

data class Lega(val sport: Sport, val codice: String, val nome: String)

/** Competizioni lette dal feed ESPN (risultati reali e quote DraftKings). */
object Leghe {
    val CALCIO = listOf(
        Lega(Sport.CALCIO, "ita.1", "Serie A"),
        Lega(Sport.CALCIO, "ita.2", "Serie B"),
        Lega(Sport.CALCIO, "eng.1", "Premier League"),
        Lega(Sport.CALCIO, "eng.2", "Championship"),
        Lega(Sport.CALCIO, "esp.1", "LaLiga"),
        Lega(Sport.CALCIO, "ger.1", "Bundesliga"),
        Lega(Sport.CALCIO, "fra.1", "Ligue 1"),
        Lega(Sport.CALCIO, "ned.1", "Eredivisie"),
        Lega(Sport.CALCIO, "por.1", "Primeira Liga"),
        Lega(Sport.CALCIO, "sco.1", "Scottish Premiership"),
        Lega(Sport.CALCIO, "tur.1", "Süper Lig"),
        Lega(Sport.CALCIO, "uefa.champions", "Champions League"),
        Lega(Sport.CALCIO, "uefa.europa", "Europa League"),
        Lega(Sport.CALCIO, "uefa.europa.conf", "Conference League"),
        Lega(Sport.CALCIO, "usa.1", "MLS"),
        Lega(Sport.CALCIO, "mex.1", "Liga MX"),
        Lega(Sport.CALCIO, "bra.1", "Brasileirão"),
        Lega(Sport.CALCIO, "arg.1", "Liga Argentina"),
    )
    val BASKET = listOf(
        Lega(Sport.BASKET, "nba", "NBA"),
        Lega(Sport.BASKET, "wnba", "WNBA"),
        Lega(Sport.BASKET, "mens-college-basketball", "NCAA"),
    )

    fun di(sport: Sport) = if (sport == Sport.CALCIO) CALCIO else BASKET
    fun nome(codice: String) = (CALCIO + BASKET).firstOrNull { it.codice == codice }?.nome ?: codice
}

/** Esito 1X2 della partita (nel basket il pareggio non esiste: si giocano i supplementari). */
enum class Esito(val simbolo: String) { CASA("1"), PAREGGIO("X"), OSPITE("2") }

/** Tipi di pronostico. VINCENTE è sempre attivo; gli altri si possono escludere dalle impostazioni. */
enum class Mercato(val etichetta: String, val descrizione: String) {
    VINCENTE("Vincente", "1 o 2: vince la squadra di casa o quella ospite"),
    PAREGGIO("Pareggio", "X: la partita finisce in parità"),
    DOPPIA_CHANCE("Doppia chance", "1X, X2 o 12: due risultati su tre"),
    GOAL("Goal / NoGoal", "GG: segnano entrambe · NG: almeno una non segna"),
    UNDER_OVER("Under / Over 2,5", "Over: almeno 3 gol · Under: al massimo 2 gol"),
}

/** Pronostico giocabile. Nel basket esistono solo 1 e 2. */
enum class Scommessa(val simbolo: String, val mercato: Mercato) {
    CASA("1", Mercato.VINCENTE),
    OSPITE("2", Mercato.VINCENTE),
    PAREGGIO("X", Mercato.PAREGGIO),
    CASA_O_PAREGGIO("1X", Mercato.DOPPIA_CHANCE),
    PAREGGIO_O_OSPITE("X2", Mercato.DOPPIA_CHANCE),
    CASA_O_OSPITE("12", Mercato.DOPPIA_CHANCE),
    GOAL("GG", Mercato.GOAL),
    NO_GOAL("NG", Mercato.GOAL),
    OVER("Over 2,5", Mercato.UNDER_OVER),
    UNDER("Under 2,5", Mercato.UNDER_OVER);

    /** Vincente sul risultato finale (nel calcio: dei 90 minuti). */
    fun vinta(casa: Int, ospite: Int): Boolean = when (this) {
        CASA -> casa > ospite
        OSPITE -> ospite > casa
        PAREGGIO -> casa == ospite
        CASA_O_PAREGGIO -> casa >= ospite
        PAREGGIO_O_OSPITE -> ospite >= casa
        CASA_O_OSPITE -> casa != ospite
        GOAL -> casa > 0 && ospite > 0
        NO_GOAL -> casa == 0 || ospite == 0
        OVER -> casa + ospite >= 3
        UNDER -> casa + ospite <= 2
    }

    fun descrivi(casaNome: String, ospiteNome: String): String = when (this) {
        CASA -> "1 · vince $casaNome"
        OSPITE -> "2 · vince $ospiteNome"
        PAREGGIO -> "X · pareggio"
        CASA_O_PAREGGIO -> "1X · $casaNome o pareggio"
        PAREGGIO_O_OSPITE -> "X2 · pareggio o $ospiteNome"
        CASA_O_OSPITE -> "12 · non finisce in parità"
        GOAL -> "GG · segnano entrambe"
        NO_GOAL -> "NG · almeno una non segna"
        OVER -> "Over 2,5 · almeno 3 gol"
        UNDER -> "Under 2,5 · al massimo 2 gol"
    }

    companion object {
        val BASKET = listOf(CASA, OSPITE)
        fun di(nome: String): Scommessa? = entries.firstOrNull { it.name == nome }
    }
}

enum class StatoEvento { PROGRAMMATO, IN_CORSO, CONCLUSO, RINVIATO, ANNULLATO }

/** Quote decimali (es. 1.85). Il pareggio è null nel basket; over/under solo se quotati (linea dei gol). */
data class Quote(
    val casa: Double,
    val pareggio: Double?,
    val ospite: Double,
    val lineaGol: Double? = null,
    val over: Double? = null,
    val under: Double? = null,
) {
    fun di(e: Esito): Double? = when (e) {
        Esito.CASA -> casa
        Esito.PAREGGIO -> pareggio
        Esito.OSPITE -> ospite
    }
}

data class Evento(
    val id: String,
    val sport: Sport,
    val lega: String,
    val inizioMillis: Long,
    val casaId: String,
    val casa: String,
    val ospiteId: String,
    val ospite: String,
    val stato: StatoEvento,
    val punteggioCasa: Int? = null,
    val punteggioOspite: Int? = null,
    /** Esito valido per la scommessa (nel calcio: risultato dei 90 minuti; null se deciso ai supplementari). */
    val esito: Esito? = null,
    val quote: Quote? = null,
    /** Partita di preseason/amichevole: risultati poco indicativi, esclusa dai pronostici. */
    val amichevole: Boolean = false,
) {
    val risultato: String? get() = if (punteggioCasa != null && punteggioOspite != null) "$punteggioCasa-$punteggioOspite" else null
}

/** Statistiche stagionali di una squadra, dalla classifica. */
data class StatSquadra(
    val id: String,
    val nome: String,
    val partite: Int,
    val vinte: Int,
    val pareggi: Int,
    val perse: Int,
    val fatti: Double,
    val subiti: Double,
)

/** Probabilità dei tre esiti (pareggio = 0 nel basket). Sempre normalizzate a 1. */
data class Probabilita(val casa: Double, val pareggio: Double, val ospite: Double) {
    fun di(e: Esito) = when (e) {
        Esito.CASA -> casa
        Esito.PAREGGIO -> pareggio
        Esito.OSPITE -> ospite
    }

    fun normalizzata(): Probabilita {
        val s = casa + pareggio + ospite
        return if (s <= 0) this else Probabilita(casa / s, pareggio / s, ospite / s)
    }
}

enum class TipoQuota { REALE, DERIVATA, STIMATA }

/** Una possibile giocata su una partita, con probabilità già corretta dall'esperienza. */
data class Opzione(
    val scommessa: Scommessa,
    val probabilita: Double,
    val quota: Double,
    val tipoQuota: TipoQuota,
    val pMercato: Double? = null,
    val pModello: Double? = null,
) {
    val valoreAtteso: Double get() = probabilita * quota - 1.0
}

/** Analisi completa di una partita: tutte le giocate possibili e i dati per l'esperienza. */
data class Analisi(
    val evento: Evento,
    val mercato: Probabilita?,
    val modello: Probabilita?,
    val finale: Probabilita,
    /** Probabilità (prima della calibrazione) che segnino entrambe / che ci siano almeno 3 gol. Solo calcio. */
    val pGoal: Double?,
    val pOver: Double?,
    val opzioni: List<Opzione>,
    /** Affidabilità storica della lega (1 = in linea con le previsioni). */
    val affidabilita: Double,
)

enum class Strategia(val etichetta: String, val descrizione: String) {
    SICURA("Sicura", "Sceglie i pronostici con la probabilità più alta, corretta dall'esperienza."),
    VALORE("Valore", "Tra i pronostici probabili, preferisce quelli con quota superiore al loro rischio."),
}

data class Pronostico(
    val evento: Evento,
    val scelta: Scommessa,
    val quota: Double,
    val tipoQuota: TipoQuota,
    val probabilita: Double,
    val pMercato: Double?,
    val pModello: Double?,
) {
    val quotaStimata: Boolean get() = tipoQuota == TipoQuota.STIMATA
}

data class Schedina(
    val sport: Sport,
    val pronostici: List<Pronostico>,
    val strategia: Strategia,
) {
    val quotaTotale: Double get() = pronostici.fold(1.0) { acc, p -> acc * p.quota }
    /** Probabilità stimata che tutti i pronostici siano corretti (eventi indipendenti). */
    val probabilita: Double get() = pronostici.fold(1.0) { acc, p -> acc * p.probabilita }
    fun valoreAtteso(puntata: Double) = puntata * (probabilita * quotaTotale - 1.0)
}

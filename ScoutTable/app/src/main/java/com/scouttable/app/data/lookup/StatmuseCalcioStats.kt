package com.scouttable.app.data.lookup

import java.net.URLEncoder
import java.text.Normalizer
import okhttp3.Request
import org.jsoup.Jsoup

data class StatmuseCalcioTotals(
    val assist: Int = 0,
    val tackle: Int = 0,
    val golEvitati: Int = 0,
    /** Gol subiti in carriera (portieri): colonna "GC" (Goals Conceded), presente automaticamente
     * nella stessa query generica "-career-stats" usata per l'assist quando il giocatore è un
     * portiere — statmuse adatta le colonne mostrate al ruolo, come già osservato per i difensori
     * (TKL/INT) e gli attaccanti (SH/SOT/TCH) sulla stessa identica query. */
    val golSubiti: Int = 0,
    /** Presenze totali (colonna "M", sempre presente): usato come rete di sicurezza quando
     * WikipediaCareerStats non trova nulla (es. TheSportsDB risolve il nome del giocatore in modo
     * leggermente sbagliato, e la ricerca diretta del titolo Wikipedia fallisce di conseguenza —
     * verificato su Michele Di Gregorio, che TheSportsDB registra come "Michele Gregorio"). */
    val presenze: Int = 0,
)

/**
 * Recupera da statmuse.com (motore di query in linguaggio naturale su statistiche sportive) dati di
 * carriera che l'infobox Wikipedia non traccia per il calcio: assist, tackle e intercettazioni
 * (usate come approssimazione di "gol evitati" per i difensori, non essendoci uno stat standard con
 * questo nome), e gol subiti per i portieri (colonna "GC", compare automaticamente nella stessa
 * query generica quando il giocatore è un portiere). Nessun URL da incollare: l'ID del giocatore si
 * risolve da solo componendo una query testuale tipo "francesco-totti-career-stats" — verificato
 * manualmente su Francesco Totti, Franco Baresi e Gianluigi Buffon, tutti risolti al giocatore
 * giusto senza ambiguità.
 *
 * Due richieste separate perché le colonne mostrate da statmuse dipendono dalla query testuale (se
 * non nomini esplicitamente uno stat, sceglie lei le colonne "in evidenza" in base al ruolo, es.
 * mostra tiri/tocchi per un attaccante e tackle/intercettazioni per un difensore): una query
 * generica "-career-stats" per l'assist (che compare sempre, a prescindere dal ruolo, e copre
 * l'intera carriera dal debutto), una che nomina esplicitamente "tackles"/"interceptions" per i dati
 * difensivi (altrimenti su un giocatore offensivo quella colonna potrebbe non comparire affatto).
 *
 * ATTENZIONE (verificato manualmente su Francesco Totti, carriera 1992-2017): tackle/intercettazioni
 * su statmuse sono tracciati in modo affidabile solo dalle stagioni più recenti (a partire dal
 * 2014-15 circa per i campionati europei principali, da quando questi eventi hanno iniziato a essere
 * raccolti sistematicamente) — per un giocatore con gran parte di carriera precedente il totale sarà
 * quindi sottostimato rispetto alla carriera reale. Non è un bug del parser: il dato grezzo più
 * vecchio semplicemente non esiste sulla fonte. L'assist invece copre correttamente tutta la
 * carriera (riga "Total" già aggregata da statmuse su ogni stagione disponibile).
 */
object StatmuseCalcioStats {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    suspend fun fetchCareerTotals(playerName: String): StatmuseCalcioTotals? = runCatching {
        val slug = slugify(playerName)
        if (slug.isBlank()) return@runCatching null

        val baseRow = fetchTotalsRow("$slug-career-stats")
        val assist = baseRow?.intValue("A")
        // "GC" (Goals Conceded) compare in questa stessa riga solo per i portieri: per tutti gli
        // altri ruoli la colonna non esiste e intValue ritorna null, senza bisogno di distinguere
        // il ruolo prima di provare.
        val golSubiti = baseRow?.intValue("GC")
        val presenze = baseRow?.intValue("M")
        val defensive = fetchTotalsRow("$slug-career-tackles-interceptions")
        val tackle = defensive?.intValue("TKL")
        val golEvitati = defensive?.intValue("INT")

        if (assist == null && tackle == null && golEvitati == null && golSubiti == null && presenze == null) {
            return@runCatching null
        }
        StatmuseCalcioTotals(assist ?: 0, tackle ?: 0, golEvitati ?: 0, golSubiti ?: 0, presenze ?: 0)
    }.getOrNull()

    private class TotalsRow(private val headers: List<String>, private val cells: List<String>) {
        /** Le colonne d'intestazione e quelle della riga sono nello stesso ordine (entrambe
         * includono le colonne "tecniche" iniziali senza etichetta, es. il numero di riga): cercare
         * l'indice nell'intestazione e leggere la stessa posizione nella riga funziona quindi anche
         * se statmuse riordina le colonne in base alla query (es. porta "A"/"TKL"/"INT" in testa
         * quando richiesti esplicitamente, invece che nella posizione "standard"). */
        fun intValue(header: String): Int? {
            val index = headers.indexOf(header)
            if (index == -1 || index >= cells.size) return null
            return cells[index].replace(",", "").toIntOrNull()
        }
    }

    /** Interroga statmuse con una query in linguaggio naturale e ritorna la riga "Total" già
     * aggregata dalla tabella di risposta (prima tabella della pagina), o null se la query non
     * risolve a nessun giocatore (statmuse risponde con HTTP non-2xx, non con una pagina vuota) o
     * non produce una tabella con una riga "Total". */
    private fun fetchTotalsRow(query: String): TotalsRow? = runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://www.statmuse.com/fc/ask/$encoded")
            .header("User-Agent", USER_AGENT)
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val html = response.body?.string() ?: return@use null
            val table = Jsoup.parse(html).selectFirst("table") ?: return@use null
            val headers = table.select("thead th").map { it.text().trim() }
            val totalCells = table.select("tbody tr").map { row -> row.select("td").map { it.text().trim() } }
                // La colonna "NAME" (dove compare "Total" sulla riga aggregata) è sempre la terza
                // ("", "", "NAME", ...): verificato sia con l'ordine colonne standard sia con quello
                // riordinato dalle query che nominano stat specifici.
                .firstOrNull { cells -> cells.getOrNull(2).equals("Total", ignoreCase = true) }
                ?: return@use null
            TotalsRow(headers, totalCells)
        }
    }.getOrNull()

    /** "Francesco Totti" -> "francesco-totti": stesso stile di slug usato dagli URL statmuse (e
     * footystats), verificato costruendo a mano l'URL per un giocatore diverso da quello di test
     * (Messi/Argentina) e ottenendo comunque la pagina corretta. */
    private fun slugify(name: String): String {
        val withoutAccents = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }
}

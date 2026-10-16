package com.matchmind.app.dati

import com.matchmind.app.dominio.Evento
import com.matchmind.app.dominio.Lega
import com.matchmind.app.dominio.StatSquadra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Accesso al feed pubblico ESPN (gratuito, senza chiave): partite del giorno con quote DraftKings,
 * risultati reali e classifiche. Le richieste partono in parallelo, con un limite per non sovraccaricare il server.
 *
 * © Roberto Di Flumeri
 */
class EspnClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val limite = Semaphore(8)
    private val formato = DateTimeFormatter.BASIC_ISO_DATE

    /** Numero di richieste fallite nell'ultima operazione (rete assente, server non raggiungibile). */
    @Volatile var errori = 0
        private set

    fun azzeraErrori() { errori = 0 }

    private suspend fun scarica(url: String): String? = limite.withPermit {
        withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "MatchMind/1.0 (Android)").build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() else null
                }
            }.getOrNull().also { if (it == null) errori++ }
        }
    }

    suspend fun partite(lega: Lega, giorno: LocalDate): List<Evento> {
        val url = "$BASE/site/v2/sports/${lega.sport.percorso}/${lega.codice}/scoreboard?dates=${giorno.format(formato)}"
        val json = scarica(url) ?: return emptyList()
        return runCatching { EspnParser.partite(json, lega.sport, lega.codice) }.getOrElse { errori++; emptyList() }
    }

    /**
     * Classifica della stagione regolare. Nel basket il feed restituirebbe la preseason (amichevoli poco
     * indicative): si chiede esplicitamente la stagione regolare, eventualmente di un anno indicato.
     */
    suspend fun classifica(lega: Lega, stagione: Int? = null): Map<String, StatSquadra> {
        val parametri = buildList {
            if (lega.sport == com.matchmind.app.dominio.Sport.BASKET) add("seasontype=2")
            stagione?.let { add("season=$it") }
        }.joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
        val json = scarica("$BASE/v2/sports/${lega.sport.percorso}/${lega.codice}/standings$parametri") ?: return emptyMap()
        return runCatching { EspnParser.classifica(json) }.getOrElse { emptyMap() }
    }

    companion object {
        const val BASE = "https://site.api.espn.com/apis"
    }
}

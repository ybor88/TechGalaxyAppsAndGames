package com.scouttable.app.data.lookup

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class SourceState { CHECKING, ONLINE, OFFLINE }

data class DataSource(
    val name: String,
    /** URL interrogata per il controllo online/offline. */
    val checkUrl: String,
    /** URL aperta nel browser quando l'utente tocca la voce (di solito la stessa, o l'homepage). */
    val homeUrl: String,
)

/** Le 4 fonti usate (o utili come riferimento) dall'app, per il pallino di stato nella home. */
object DataSources {
    val THESPORTSDB = DataSource(
        "TheSportsDB",
        "https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=Arsenal",
        "https://www.thesportsdb.com",
    )
    val WIKIPEDIA = DataSource(
        "Wikipedia",
        "https://en.wikipedia.org",
        "https://en.wikipedia.org",
    )
    val PROBALLERS = DataSource(
        "Proballers",
        "https://www.proballers.com",
        "https://www.proballers.com",
    )
    val TRANSFERMARKT = DataSource(
        "Transfermarkt",
        "https://www.transfermarkt.com",
        "https://www.transfermarkt.com",
    )

    val all = listOf(THESPORTSDB, WIKIPEDIA, PROBALLERS, TRANSFERMARKT)
}

/**
 * Verifica solo se una fonte risponde (raggiungibilità), non se i dati restituiti sono quelli
 * attesi: un timeout breve (6s) per non far attendere a lungo l'utente nella schermata iniziale.
 */
object SourceStatus {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun check(source: DataSource): SourceState = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(source.checkUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
                )
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) SourceState.ONLINE else SourceState.OFFLINE
            }
        }.getOrDefault(SourceState.OFFLINE)
    }
}

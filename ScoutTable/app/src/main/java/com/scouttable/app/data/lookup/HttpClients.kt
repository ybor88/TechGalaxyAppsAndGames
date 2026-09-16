package com.scouttable.app.data.lookup

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client HTTP condiviso dal lookup: timeout più larghi del default OkHttp (10s), utili sulla
 * rete più lenta/variabile di un emulatore o di una connessione mobile debole.
 */
internal val lookupHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

/** Riusato anche da [ProballersWebViewFetcher], che deve presentarsi come lo stesso Chrome
 * desktop per ottenere da Proballers la pagina completa (non la versione mobile ridotta, che
 * omette colonne/sezioni come Eff, "secondo logo" e statistiche Nazionale). */
internal const val CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

/**
 * Intestazioni di un browser Chrome reale (oltre allo User-Agent), non solo per euristiche anti-bot
 * "semplici" (alcuni siti, es. Proballers dietro Cloudflare, valutano anche Accept/Accept-Language/
 * Sec-Fetch-* per distinguere un client automatico da un browser, non solo lo User-Agent). Non basta
 * comunque contro una sfida interattiva (JS/Turnstile): quella richiede di eseguire JavaScript, cosa
 * che una richiesta OkHttp non fa.
 */
internal fun Request.Builder.withBrowserHeaders(): Request.Builder = this
    .header("User-Agent", CHROME_USER_AGENT)
    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
    .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
    .header("Sec-Fetch-Dest", "document")
    .header("Sec-Fetch-Mode", "navigate")
    .header("Sec-Fetch-Site", "none")
    .header("Sec-Fetch-User", "?1")
    .header("Upgrade-Insecure-Requests", "1")

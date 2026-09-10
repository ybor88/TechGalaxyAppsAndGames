package com.scouttable.app.data.lookup

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Client HTTP condiviso dal lookup: timeout più larghi del default OkHttp (10s), utili sulla
 * rete più lenta/variabile di un emulatore o di una connessione mobile debole.
 */
internal val lookupHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

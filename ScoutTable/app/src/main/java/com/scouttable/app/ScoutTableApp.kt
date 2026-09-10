package com.scouttable.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Fornisce a Coil (immagini in AsyncImage/SubcomposeAsyncImage) un client con uno User-Agent
 * descrittivo: i server immagini di Wikimedia (thumb.wikimedia.org) rispondono 403 a richieste
 * con lo User-Agent generico di default di OkHttp, quindi le foto dei giocatori risultavano
 * sempre "non caricate" (fallback silenzioso alla sola icona/avatar).
 */
class ScoutTableApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
        .build()
}

package com.scouttable.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.scouttable.app.data.lookup.AppContext
import okhttp3.OkHttpClient

/**
 * Fornisce a Coil (immagini in AsyncImage/SubcomposeAsyncImage) un client con uno User-Agent
 * descrittivo: i server immagini di Wikimedia (thumb.wikimedia.org) rispondono 403 a richieste
 * con lo User-Agent generico di default di OkHttp, quindi le foto dei giocatori risultavano
 * sempre "non caricate" (fallback silenzioso alla sola icona/avatar).
 * SvgDecoder: gli stemmi delle squadre di Proballers (secondo logo, basket) sono serviti come
 * .svg, che Coil non decodifica di default — senza questo componente fallisce silenziosamente
 * mostrando l'avatar con le iniziali al posto del logo reale.
 */
class ScoutTableApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Serve al fallback WebView di ProballersWebViewFetcher (vedi ProballersCareerStats),
        // l'unico punto del layer di lookup che ha bisogno di un Context.
        AppContext.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
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

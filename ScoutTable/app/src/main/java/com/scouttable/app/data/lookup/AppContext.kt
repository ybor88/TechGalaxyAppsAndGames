// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Riferimento al Context applicativo, impostato da [com.scouttable.app.ScoutTableApp] all'avvio.
 * Serve solo a [ProballersWebViewFetcher] (l'unico punto del layer di lookup che ha bisogno di un
 * Context, per creare la WebView di fallback quando Proballers blocca la richiesta HTTP diretta):
 * il resto del layer è puro Kotlin/rete e non ne ha bisogno.
 */
object AppContext {
    var contextOrNull: Context? = null
        private set

    private var currentActivity: WeakReference<Activity>? = null

    /** Activity in primo piano, se c'è: la WebView di [ProballersWebViewFetcher] viene agganciata
     *  alla sua finestra (invisibile), altrimenti Chromium la considera nascosta e sospende i timer
     *  con cui Cloudflare completa la verifica. */
    val activityOrNull: Activity? get() = currentActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun init(application: Application) {
        contextOrNull = application.applicationContext
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { currentActivity = WeakReference(activity) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { currentActivity = WeakReference(activity) }
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity?.get() === activity) currentActivity = null
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }
}

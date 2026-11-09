package com.matchmind.app.avvisi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dopo il riavvio del telefono o un aggiornamento dell'app il monitoraggio riparte da solo,
 * se ci sono schedine con partite da seguire.
 *
 * © Roberto Di Flumeri
 */
class AvvioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val attesa = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MonitoraggioService.avviaSeServe(context)
            } finally {
                attesa.finish()
            }
        }
    }
}

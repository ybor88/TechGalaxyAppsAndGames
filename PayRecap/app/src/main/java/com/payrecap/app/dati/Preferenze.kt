// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dati

import android.content.Context
import com.payrecap.app.dominio.Impostazioni

/** Impostazioni salvate sul telefono. */
class Preferenze(context: Context) {
    private val sp = context.getSharedPreferences("payrecap", Context.MODE_PRIVATE)

    fun leggi() = Impostazioni(
        sogliaVariazione = sp.getFloat("soglia", 10f).toDouble(),
        giorniPreavvisoScadenza = sp.getInt("preavviso", 60),
    )

    fun scrivi(i: Impostazioni) {
        sp.edit()
            .putFloat("soglia", i.sogliaVariazione.toFloat())
            .putInt("preavviso", i.giorniPreavvisoScadenza)
            .apply()
    }
}

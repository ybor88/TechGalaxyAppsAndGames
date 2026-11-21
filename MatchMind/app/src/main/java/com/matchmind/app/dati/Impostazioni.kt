package com.matchmind.app.dati

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.matchmind.app.dominio.Mercato
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Preferenze dell'utente (puntata, tipi di pronostico). © Roberto Di Flumeri

data class Preferenze(
    val puntata: Double = 10.0,
    val configurato: Boolean = false,
    /** Tipi di pronostico ammessi (il vincente 1/2 è sempre ammesso). */
    val mercati: Set<Mercato> = Mercato.entries.toSet(),
)

private val Context.archivio by preferencesDataStore("preferenze")

class Impostazioni(private val context: Context) {
    private val kPuntata = doublePreferencesKey("puntata")
    private val kConfigurato = booleanPreferencesKey("configurato")
    private val kMercati = stringSetPreferencesKey("mercati")

    val preferenze: Flow<Preferenze> = context.archivio.data.map { p ->
        Preferenze(
            puntata = p[kPuntata] ?: 10.0,
            configurato = p[kConfigurato] ?: false,
            mercati = p[kMercati]?.mapNotNull { n -> Mercato.entries.firstOrNull { it.name == n } }?.toSet()?.plus(Mercato.VINCENTE)
                ?: Mercato.entries.toSet(),
        )
    }

    suspend fun salva(pref: Preferenze) {
        context.archivio.edit {
            it[kPuntata] = pref.puntata
            it[kConfigurato] = pref.configurato
            it[kMercati] = (pref.mercati + Mercato.VINCENTE).map { m -> m.name }.toSet()
        }
    }
}

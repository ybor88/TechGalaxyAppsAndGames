package com.matchmind.app.dati

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.Mercato
import com.matchmind.app.dominio.Strategia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Preferenze dell'utente (puntata, finestra, strategia, sicurezza, tipi di pronostico). © Roberto Di Flumeri

data class Preferenze(
    val puntata: Double = 10.0,
    val giorni: Int = 3,
    val strategia: Strategia = Strategia.SICURA,
    val configurato: Boolean = false,
    /** Probabilità minima di vincere l'intera schedina: decide quante partite includere. */
    val sicurezza: Double = Generatore.SICUREZZA_PREDEFINITA,
    /** Tipi di pronostico ammessi (il vincente 1/2 è sempre ammesso). */
    val mercati: Set<Mercato> = Mercato.entries.toSet(),
)

private val Context.archivio by preferencesDataStore("preferenze")

class Impostazioni(private val context: Context) {
    private val kPuntata = doublePreferencesKey("puntata")
    private val kGiorni = intPreferencesKey("giorni")
    private val kStrategia = stringPreferencesKey("strategia")
    private val kConfigurato = booleanPreferencesKey("configurato")
    private val kSicurezza = doublePreferencesKey("sicurezza")
    private val kMercati = stringSetPreferencesKey("mercati")

    val preferenze: Flow<Preferenze> = context.archivio.data.map { p ->
        Preferenze(
            puntata = p[kPuntata] ?: 10.0,
            giorni = p[kGiorni] ?: 3,
            strategia = p[kStrategia]?.let { runCatching { Strategia.valueOf(it) }.getOrNull() } ?: Strategia.SICURA,
            configurato = p[kConfigurato] ?: false,
            sicurezza = p[kSicurezza] ?: Generatore.SICUREZZA_PREDEFINITA,
            mercati = p[kMercati]?.mapNotNull { n -> Mercato.entries.firstOrNull { it.name == n } }?.toSet()?.plus(Mercato.VINCENTE)
                ?: Mercato.entries.toSet(),
        )
    }

    suspend fun salva(pref: Preferenze) {
        context.archivio.edit {
            it[kPuntata] = pref.puntata
            it[kGiorni] = pref.giorni.coerceIn(1, 7)
            it[kStrategia] = pref.strategia.name
            it[kConfigurato] = pref.configurato
            it[kSicurezza] = pref.sicurezza.coerceIn(0.05, 0.9)
            it[kMercati] = (pref.mercati + Mercato.VINCENTE).map { m -> m.name }.toSet()
        }
    }
}

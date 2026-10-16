package com.matchmind.app

import android.app.Application
import com.matchmind.app.dati.EspnClient
import com.matchmind.app.dati.Impostazioni
import com.matchmind.app.dati.MatchMindDb
import com.matchmind.app.dati.Repository

// Punto d'ingresso dell'applicazione: archivio, rete e preferenze. © Roberto Di Flumeri
class MatchMindApp : Application() {
    val db by lazy { MatchMindDb.crea(this) }
    val repository by lazy { Repository(db, EspnClient()) }
    val impostazioni by lazy { Impostazioni(this) }
}

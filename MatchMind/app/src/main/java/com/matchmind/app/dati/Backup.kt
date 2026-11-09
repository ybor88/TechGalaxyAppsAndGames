package com.matchmind.app.dati

import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.Mercato
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.Strategia
import com.matchmind.app.dominio.TipoMovimento
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup completo di MatchMind in un file JSON, per passare a un altro telefono e ripartire dallo stesso stato:
 * schedine con i loro pronostici, movimenti del credito (saldo, ricariche, vincite), esperienza appresa
 * (partite osservate) e impostazioni.
 *
 * Funzioni pure (dati ↔ testo) per poterle collaudare senza telefono. La lettura controlla tutto prima di
 * toccare l'archivio: un file sbagliato non cancella niente.
 *
 * © Roberto Di Flumeri
 */
object Backup {
    const val APP = "MatchMind"
    const val FORMATO = 1

    data class Contenuto(
        val creatoIl: Long,
        val schedine: List<SchedinaEntity>,
        val selezioni: List<SelezioneEntity>,
        val movimenti: List<MovimentoEntity>,
        val osservazioni: List<OsservazioneEntity>,
        val preferenze: Preferenze,
    )

    class BackupNonValido(messaggio: String, causa: Throwable? = null) : Exception(messaggio, causa)

    // ── Scrittura ────────────────────────────────────────────────────────────

    fun scrivi(c: Contenuto): String = JSONObject().apply {
        put("app", APP)
        put("formato", FORMATO)
        put("creatoIl", c.creatoIl)
        put("schedine", JSONArray(c.schedine.map { s ->
            JSONObject().apply {
                put("id", s.id); put("sport", s.sport); put("strategia", s.strategia); put("creataIl", s.creataIl)
                put("puntata", s.puntata); put("quotaTotale", s.quotaTotale); put("probabilita", s.probabilita)
                put("stato", s.stato); opz("chiusaIl", s.chiusaIl); put("vincita", s.vincita); opz("verificataIl", s.verificataIl)
            }
        }))
        put("selezioni", JSONArray(c.selezioni.map { s ->
            JSONObject().apply {
                put("id", s.id); put("schedinaId", s.schedinaId); put("eventoId", s.eventoId); put("lega", s.lega)
                put("inizio", s.inizio); put("casa", s.casa); put("ospite", s.ospite); put("scelta", s.scelta)
                put("quota", s.quota); put("quotaStimata", s.quotaStimata); put("probabilita", s.probabilita)
                opz("pMercato", s.pMercato); opz("pModello", s.pModello); put("esito", s.esito)
                opz("risultato", s.risultato); opz("allerta", s.allerta)
            }
        }))
        put("movimenti", JSONArray(c.movimenti.map { m ->
            JSONObject().apply { put("id", m.id); put("data", m.data); put("tipo", m.tipo); put("importo", m.importo); opz("schedinaId", m.schedinaId) }
        }))
        put("osservazioni", JSONArray(c.osservazioni.map { o ->
            JSONObject().apply {
                put("eventoId", o.eventoId); put("sport", o.sport); put("lega", o.lega); put("inizio", o.inizio)
                opz("mercatoCasa", o.mercatoCasa); opz("mercatoPareggio", o.mercatoPareggio); opz("mercatoOspite", o.mercatoOspite)
                opz("modelloCasa", o.modelloCasa); opz("modelloPareggio", o.modelloPareggio); opz("modelloOspite", o.modelloOspite)
                opz("esito", o.esito); opz("pGoal", o.pGoal); opz("pOver", o.pOver); opz("golCasa", o.golCasa); opz("golOspite", o.golOspite)
            }
        }))
        val p = c.preferenze
        put("preferenze", JSONObject().apply {
            put("puntata", p.puntata); put("giorni", p.giorni); put("strategia", p.strategia.name); put("configurato", p.configurato)
            put("sicurezza", p.sicurezza); put("mercati", JSONArray(p.mercati.map { it.name })); put("avvisiCashout", p.avvisiCashout)
        })
    }.toString()

    private fun JSONObject.opz(chiave: String, valore: Any?) { put(chiave, valore ?: JSONObject.NULL) }

    // ── Lettura ──────────────────────────────────────────────────────────────

    fun leggi(testo: String): Contenuto {
        val r = try { JSONObject(testo) } catch (e: Exception) { throw BackupNonValido("Il file non è un backup di MatchMind", e) }
        if (r.optString("app") != APP) throw BackupNonValido("Il file non è un backup di MatchMind")
        val formato = r.optInt("formato", -1)
        if (formato < 1) throw BackupNonValido("Backup non valido")
        if (formato > FORMATO) throw BackupNonValido("Backup creato da una versione più recente di MatchMind: aggiorna l'app")
        try {
            val schedine = r.lista("schedine").map { s ->
                SchedinaEntity(
                    id = s.getLong("id"), sport = s.enumerato("sport", Sport.entries), strategia = s.enumerato("strategia", Strategia.entries),
                    creataIl = s.getLong("creataIl"), puntata = s.getDouble("puntata"), quotaTotale = s.getDouble("quotaTotale"),
                    probabilita = s.getDouble("probabilita"), stato = s.enumerato("stato", StatoSchedina.entries),
                    chiusaIl = s.lungo("chiusaIl"), vincita = s.getDouble("vincita"), verificataIl = s.lungo("verificataIl"),
                )
            }
            val idSchedine = schedine.map { it.id }.toSet()
            if (idSchedine.size != schedine.size) throw BackupNonValido("Backup danneggiato: schedine duplicate")
            val selezioni = r.lista("selezioni").map { s ->
                SelezioneEntity(
                    id = s.getLong("id"), schedinaId = s.getLong("schedinaId"), eventoId = s.getString("eventoId"), lega = s.getString("lega"),
                    inizio = s.getLong("inizio"), casa = s.getString("casa"), ospite = s.getString("ospite"), scelta = s.getString("scelta"),
                    quota = s.getDouble("quota"), quotaStimata = s.getBoolean("quotaStimata"), probabilita = s.getDouble("probabilita"),
                    pMercato = s.decimale("pMercato"), pModello = s.decimale("pModello"), esito = s.enumerato("esito", EsitoSelezione.entries),
                    risultato = s.testo("risultato"), allerta = s.testo("allerta"),
                )
            }
            if (selezioni.any { it.schedinaId !in idSchedine }) throw BackupNonValido("Backup danneggiato: pronostici senza schedina")
            val movimenti = r.lista("movimenti").map { m ->
                MovimentoEntity(id = m.getLong("id"), data = m.getLong("data"), tipo = m.enumerato("tipo", TipoMovimento.entries),
                    importo = m.getDouble("importo"), schedinaId = m.lungo("schedinaId"))
            }
            val osservazioni = r.lista("osservazioni").map { o ->
                OsservazioneEntity(
                    eventoId = o.getString("eventoId"), sport = o.enumerato("sport", Sport.entries), lega = o.getString("lega"), inizio = o.getLong("inizio"),
                    mercatoCasa = o.decimale("mercatoCasa"), mercatoPareggio = o.decimale("mercatoPareggio"), mercatoOspite = o.decimale("mercatoOspite"),
                    modelloCasa = o.decimale("modelloCasa"), modelloPareggio = o.decimale("modelloPareggio"), modelloOspite = o.decimale("modelloOspite"),
                    esito = o.testo("esito"), pGoal = o.decimale("pGoal"), pOver = o.decimale("pOver"), golCasa = o.intero("golCasa"), golOspite = o.intero("golOspite"),
                )
            }
            val p = r.optJSONObject("preferenze") ?: JSONObject()
            val base = Preferenze()
            val preferenze = Preferenze(
                puntata = p.optDouble("puntata", base.puntata),
                giorni = p.optInt("giorni", base.giorni),
                strategia = Strategia.entries.firstOrNull { it.name == p.optString("strategia") } ?: base.strategia,
                // chi ripristina un backup ha già configurato il conto
                configurato = true,
                sicurezza = p.optDouble("sicurezza", base.sicurezza),
                mercati = p.optJSONArray("mercati")?.let { a -> (0 until a.length()).mapNotNull { i -> Mercato.entries.firstOrNull { it.name == a.optString(i) } }.toSet() }
                    ?: base.mercati,
                avvisiCashout = p.optBoolean("avvisiCashout", base.avvisiCashout),
            )
            return Contenuto(r.optLong("creatoIl", 0L), schedine, selezioni, movimenti, osservazioni, preferenze)
        } catch (e: BackupNonValido) {
            throw e
        } catch (e: Exception) {
            throw BackupNonValido("Backup danneggiato o incompleto", e)
        }
    }

    private fun JSONObject.lista(chiave: String): List<JSONObject> {
        val a = optJSONArray(chiave) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun JSONObject.lungo(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.intero(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.decimale(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.testo(k: String): String? = if (isNull(k)) null else getString(k)

    /** Valore di un elenco noto (stato, sport…): un valore sconosciuto renderebbe illeggibile lo storico. */
    private fun <E : Enum<E>> JSONObject.enumerato(k: String, valori: List<E>): String {
        val v = getString(k)
        if (valori.none { it.name == v }) throw BackupNonValido("Backup danneggiato: valore \"$v\" non riconosciuto")
        return v
    }
}

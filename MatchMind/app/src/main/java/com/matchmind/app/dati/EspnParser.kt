package com.matchmind.app.dati

import com.matchmind.app.dominio.Esito
import com.matchmind.app.dominio.Evento
import com.matchmind.app.dominio.ModelliProbabilistici
import com.matchmind.app.dominio.Quote
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatSquadra
import com.matchmind.app.dominio.StatoEvento
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Lettura del feed pubblico ESPN: calendario, risultati reali, quote DraftKings e classifiche.
 * Funzioni pure (stringa JSON → modelli) per poterle collaudare con risposte reali salvate.
 *
 * © Roberto Di Flumeri
 */
object EspnParser {

    fun partite(json: String, sport: Sport, lega: String): List<Evento> {
        val eventi = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return (0 until eventi.length()).mapNotNull { i -> eventi.optJSONObject(i)?.let { evento(it, sport, lega) } }
    }

    private fun evento(e: JSONObject, sport: Sport, lega: String): Evento? {
        val c = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val concorrenti = c.optJSONArray("competitors") ?: return null
        var casa: JSONObject? = null
        var ospite: JSONObject? = null
        for (i in 0 until concorrenti.length()) {
            val x = concorrenti.optJSONObject(i) ?: continue
            if (x.optString("homeAway") == "home") casa = x else ospite = x
        }
        if (casa == null || ospite == null) return null
        val tipo = c.optJSONObject("status")?.optJSONObject("type") ?: JSONObject()
        val nomeStato = tipo.optString("name")
        val stato = stato(tipo)
        val pc = punteggio(casa)
        val po = punteggio(ospite)
        val esito = if (stato == StatoEvento.CONCLUSO && pc != null && po != null) esito(sport, nomeStato, pc, po) else null
        val inizio = runCatching {
            OffsetDateTime.parse(e.optString("date").let { if (it.length == 17) it.replace("Z", ":00Z") else it },
                DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull() ?: return null
        val squadra = { x: JSONObject -> x.optJSONObject("team") ?: JSONObject() }
        return Evento(
            id = e.optString("id"),
            sport = sport,
            lega = lega,
            inizioMillis = inizio,
            casaId = squadra(casa).optString("id"),
            casa = squadra(casa).optString("displayName"),
            ospiteId = squadra(ospite).optString("id"),
            ospite = squadra(ospite).optString("displayName"),
            stato = stato,
            punteggioCasa = if (stato == StatoEvento.PROGRAMMATO) null else pc,
            punteggioOspite = if (stato == StatoEvento.PROGRAMMATO) null else po,
            esito = esito,
            quote = quote(c.optJSONArray("odds"), sport),
            amichevole = e.optJSONObject("season")?.let { st ->
                st.optString("slug").contains("preseason") || (sport == Sport.BASKET && st.optInt("type") == 1)
            } ?: false,
        )
    }

    fun stato(tipo: JSONObject): StatoEvento {
        val nome = tipo.optString("name")
        return when {
            nome.contains("POSTPONED") || nome.contains("SUSPENDED") -> StatoEvento.RINVIATO
            nome.contains("CANCELED") || nome.contains("CANCELLED") || nome.contains("ABANDONED") || nome.contains("FORFEIT") -> StatoEvento.ANNULLATO
            tipo.optBoolean("completed") -> StatoEvento.CONCLUSO
            tipo.optString("state") == "in" -> StatoEvento.IN_CORSO
            tipo.optString("state") == "post" -> StatoEvento.CONCLUSO
            else -> StatoEvento.PROGRAMMATO
        }
    }

    /**
     * Esito ai fini della scommessa. Nel calcio vale il 90°: se la partita è finita ai supplementari o ai
     * rigori il feed non fornisce il parziale dei 90 minuti, quindi l'esito resta sconosciuto e il pronostico
     * verrà annullato (quota 1.00) invece di essere indovinato.
     */
    fun esito(sport: Sport, nomeStato: String, casa: Int, ospite: Int): Esito? {
        if (sport == Sport.CALCIO && (nomeStato.contains("AET") || nomeStato.contains("PEN") || nomeStato.contains("EXTRA"))) return null
        return when {
            casa > ospite -> Esito.CASA
            casa < ospite -> Esito.OSPITE
            sport == Sport.CALCIO -> Esito.PAREGGIO
            else -> null
        }
    }

    private fun punteggio(x: JSONObject): Int? {
        val s = x.opt("score")
        return when (s) {
            is JSONObject -> s.optString("displayValue").toIntOrNull() ?: s.optDouble("value").takeIf { !it.isNaN() }?.toInt()
            is String -> s.toIntOrNull()
            is Number -> s.toInt()
            else -> null
        }
    }

    fun quote(odds: JSONArray?, sport: Sport): Quote? {
        if (odds == null) return null
        for (i in 0 until odds.length()) {
            val o = odds.optJSONObject(i) ?: continue
            val ml = o.optJSONObject("moneyline")
            val casa = ml?.let { americana(it.opt("home")) } ?: ModelliProbabilistici.decimaleDaAmericana(intero(o.optJSONObject("homeTeamOdds")))
            val ospite = ml?.let { americana(it.opt("away")) } ?: ModelliProbabilistici.decimaleDaAmericana(intero(o.optJSONObject("awayTeamOdds")))
            val pareggio = ml?.let { americana(it.opt("draw")) } ?: ModelliProbabilistici.decimaleDaAmericana(intero(o.optJSONObject("drawOdds")))
            if (casa == null || ospite == null) continue
            if (sport == Sport.CALCIO && pareggio == null) continue // nel calcio servono tutte e tre le quote
            if (sport != Sport.CALCIO) return Quote(arrotonda(casa), null, arrotonda(ospite))
            // Under/Over gol: linea (es. 2.5) e quote dei due lati
            val totale = o.optJSONObject("total")
            val linea = o.optDouble("overUnder").takeIf { !it.isNaN() }
                ?: totale?.optJSONObject("over")?.optJSONObject("close")?.optString("line")?.removePrefix("o")?.toDoubleOrNull()
            val over = totale?.let { americana(it.opt("over")) }
            val under = totale?.let { americana(it.opt("under")) }
            return Quote(arrotonda(casa), pareggio?.let(::arrotonda), arrotonda(ospite),
                lineaGol = linea, over = over?.let(::arrotonda), under = under?.let(::arrotonda))
        }
        return null
    }

    private fun arrotonda(q: Double) = Math.round(q * 100) / 100.0

    private fun intero(o: JSONObject?): Int? = o?.opt("moneyLine")?.let { (it as? Number)?.toInt() }

    /** "+145" oppure {"open":{"odds":"+135"},"close":{"odds":"+145"}}: vale la quota più recente. */
    private fun americana(v: Any?): Double? = when (v) {
        is String -> ModelliProbabilistici.decimaleDaAmericana(v)
        is Number -> ModelliProbabilistici.decimaleDaAmericana(v.toInt())
        is JSONObject -> {
            val testo = v.optJSONObject("close")?.optString("odds")?.takeIf { it.isNotBlank() }
                ?: v.optJSONObject("current")?.optString("odds")?.takeIf { it.isNotBlank() }
                ?: v.optJSONObject("open")?.optString("odds")?.takeIf { it.isNotBlank() }
            ModelliProbabilistici.decimaleDaAmericana(testo)
        }
        else -> null
    }

    // ── Classifiche ──────────────────────────────────────────────────────────

    fun classifica(json: String): Map<String, StatSquadra> {
        val radice = JSONObject(json)
        val risultato = mutableMapOf<String, StatSquadra>()
        fun visita(nodo: JSONObject) {
            nodo.optJSONObject("standings")?.optJSONArray("entries")?.let { voci ->
                for (i in 0 until voci.length()) voci.optJSONObject(i)?.let { voce(it) }?.let { risultato[it.id] = it }
            }
            nodo.optJSONArray("children")?.let { figli -> for (i in 0 until figli.length()) figli.optJSONObject(i)?.let(::visita) }
        }
        visita(radice)
        return risultato
    }

    private fun voce(v: JSONObject): StatSquadra? {
        val team = v.optJSONObject("team") ?: return null
        val stats = mutableMapOf<String, Double>()
        v.optJSONArray("stats")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                val valore = s.optDouble("value")
                if (!valore.isNaN()) stats[s.optString("name")] = valore
            }
        }
        val vinte = stats["wins"]?.toInt() ?: 0
        val pareggi = stats["ties"]?.toInt() ?: 0
        val perse = stats["losses"]?.toInt() ?: 0
        val partite = stats["gamesPlayed"]?.toInt() ?: (vinte + pareggi + perse)
        return StatSquadra(team.optString("id"), team.optString("displayName"), partite, vinte, pareggi, perse,
            stats["pointsFor"] ?: 0.0, stats["pointsAgainst"] ?: 0.0)
    }
}

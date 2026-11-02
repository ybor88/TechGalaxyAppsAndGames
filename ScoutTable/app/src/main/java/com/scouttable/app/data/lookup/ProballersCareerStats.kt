// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.ranking.PlayerScoring
import java.util.Calendar
import kotlin.math.roundToInt
import okhttp3.Request
import org.jsoup.Jsoup

data class ProballersTotals(
    val presenze: Int,
    val punteggio: Int,
    val assist: Int,
    val competizione: String,
    /** Squadra in cui ha giocato più partite: usata anche per i ritirati, che su TheSportsDB
     * non hanno più un club reale associato. */
    val squadraPrincipale: String?,
    /** Logo di [squadraPrincipale], già incorporato nella riga Proballers: fallback quando
     *  TheSportsDB non conosce il club (comune per le leghe minori, es. Serie B italiana). */
    val squadraPrincipaleLogoUrl: String? = null,
    val nazione: String? = null,
    val annoNascita: Int? = null,
    /** Ruolo grezzo in inglese (es. "Point Guard"): va tradotto con [translateRole]. */
    val posizione: String? = null,
    /** Presenze/punti/assist con la Nazionale (sezione "International competitions" della stessa
     * pagina: Olimpiadi/EuroBasket/Mondiali, es. "USA"/"Olympics" per Michael Jordan 1984/1992). */
    val presenzeNazionale: Int = 0,
    val punteggioNazionale: Int = 0,
    val assistNazionale: Int = 0,
    /** Rimbalzi totali di club, dalla colonna "Reb" (media a partita), stesso calcolo di punti/assist. */
    val rimbalzi: Int = 0,
    /** Rimbalzi totali in Nazionale, stessa sezione "International competitions" di [presenzeNazionale]. */
    val rimbalziNazionale: Int = 0,
    /** Minuti totali di carriera: somma di (minuti medi a partita, colonna "MIN") × GP per ogni
     *  stagione — Proballers non ha una colonna "minuti totali" diretta. */
    val minutiCarriera: Int = 0,
    /** Minuti totali in Nazionale, stesso calcolo di [minutiCarriera] sulla sezione internazionale. */
    val minutiNazionale: Int = 0,
    /** Media dell'Eff (colonna "Eff", ultima della tabella) su tutte le stagioni di club: solo
     *  valore, senza logo associato (vedi [bestEffTeam] per la stagione col singolo Eff più alto). */
    val effMedio: Int = 0,
    /** Squadra della singola stagione di club con l'Eff più alto ("secondo logo" del giocatore). */
    val bestEffTeam: String? = null,
    /** URL del logo della squadra di [bestEffTeam], già incorporato nella riga Proballers (nessun
     *  bisogno di TheSportsDB per questo logo). */
    val bestEffLogoUrl: String? = null,
    /** Etichetta della stagione di [bestEffTeam] (es. "07-08"). */
    val bestEffStagione: String? = null,
    /** Valore Eff della stagione di [bestEffTeam]. */
    val bestEffValue: Int? = null,
    /** Anno di fine dell'ultima stagione giocata (club o Nazionale, es. "24-25" -> 2025): dice se
     *  il giocatore è ancora in attività (vedi [PlayerStatusFromSeasons]). Null se non leggibile. */
    val ultimaStagione: Int? = null,
    /** Palle recuperate e percentuali al tiro da 2/3 di carriera: solo da RealGM
     *  ([RealGmCareerStats]), Proballers non le riporta nella tabella letta qui. */
    val palleRecuperate: Int = 0,
    val percentualeTiriDaDue: Int = 0,
    val percentualeTiriDaTre: Int = 0,
)

/**
 * Stato del giocatore dedotto dall'ultima stagione giocata su Proballers. La pagina è identificata
 * dall'URL incollato, quindi è più affidabile di TheSportsDB (che può restituire un omonimo o un
 * profilo non aggiornato). Attivo se ha giocato la stagione in corso o quella appena conclusa;
 * Ritirato se l'ultima stagione è più vecchia; null se la stagione non è leggibile.
 */
object PlayerStatusFromSeasons {
    fun fromLastSeason(lastSeasonEndYear: Int?, today: Calendar = Calendar.getInstance()): String? {
        if (lastSeasonEndYear == null) return null
        // Le stagioni di basket iniziano in autunno: da agosto in poi la stagione "in corso" è
        // quella che finisce l'anno successivo (es. ottobre 2026 -> stagione 26-27).
        val year = today.get(Calendar.YEAR)
        val currentSeasonEnd = if (today.get(Calendar.MONTH) >= Calendar.AUGUST) year + 1 else year
        return if (lastSeasonEndYear >= currentSeasonEnd - 1) PlayerStatus.ATTIVO
        else PlayerStatus.RITIRATO
    }
}

/** "24-25", "2024-25", "2024-2025" -> 2025; "2024" -> 2024; altrimenti null. */
internal fun seasonEndYear(label: String): Int? {
    val range = Regex("""(\d{2,4})\s*[-/]\s*(\d{2,4})""").find(label)
    val raw = range?.groupValues?.get(2) ?: Regex("""(19|20)\d{2}""").find(label)?.value ?: return null
    val n = raw.toIntOrNull() ?: return null
    return when (raw.length) {
        4 -> n
        2 -> {
            // Secolo dedotto: "07" è 2007, "98" è 1998 (nessuna stagione oltre l'anno prossimo).
            val nextYy = (Calendar.getInstance().get(Calendar.YEAR) + 1) % 100
            if (n <= nextYy) 2000 + n else 1900 + n
        }
        else -> null
    }
}

// Sigle usate da Proballers nel campo "Position" (es. "sg, sf" per un giocatore che copre due
// ruoli: si prende sempre e solo il primo, quello principale).
internal val proballersPositionAbbrev: Map<String, String> = mapOf(
    "pg" to "Point Guard", "sg" to "Shooting Guard", "sf" to "Small Forward",
    "pf" to "Power Forward", "c" to "Center", "g" to "Guard", "f" to "Forward",
)

/**
 * Legge la tabella "Regular Season Stats" di una pagina giocatore Proballers (es.
 * https://www.proballers.com/basketball/player/2765/michael-jordan, verificata manualmente) e
 * calcola i totali di carriera. La tabella riporta MEDIE a partita (Pts/Reb/Ast) + GP (partite
 * giocate) per stagione: i totali si ottengono moltiplicando media * GP e sommando tutte le
 * stagioni. "Competizione massima" = la lega in cui ha giocato più stagioni, oppure NBA/WNBA se lì c'è
 * la squadra principale o la stagione migliore (vedi [sumSeasonRows]); "squadra principale"
 * = la squadra in cui ha totalizzato più partite.
 */
object ProballersCareerStats {

    suspend fun fetchCareerTotals(rawProfileUrl: String): ProballersTotals? = runCatching {
        // Le pagine localizzate (es. /it/pallacanestro/giocatore/48763/...) hanno la stessa tabella
        // ma le etichette anagrafiche tradotte ("Nato il" al posto di "Date of birth"): si legge
        // sempre la versione inglese, l'unica di cui il parsing qui sotto conosce le etichette.
        val profileUrl = canonicalProfileUrl(rawProfileUrl)
        val html = fetchHtml(profileUrl) ?: return@runCatching null

        val doc = Jsoup.parse(html, profileUrl)
        // I giovani spesso non hanno ancora una "Regular Season Stats": solo coppe europee giovanili
        // (sezione "European competitions stats", stessa tabella) e Nazionale giovanile. Verificato
        // su Thierry Henry Alves De Souza (2008): solo ANGT con U18 Baskonia + AmeriCup U18 col
        // Brasile — prima risultava "non trovato" pur con l'URL Proballers incollato.
        val regularRows = doc.select("section#anchor-regular-season table.table tbody tr")
        val europeRows = doc.select("section#anchor-europe table.table tbody tr")
        val rows = regularRows.ifEmpty { europeRows }
        // Playoff (e coppe nazionali, stessa sezione "anchor-playo") e coppe europee contano nei
        // totali e nell'Eff: verificato su Justin Anderson, la cui stagione 25-26 a Dubai compare
        // solo lì (EuroLeague/ABA ed ABA - PO), e senza risultava "Ritirato" fermo al Barcellona.
        val playoffRows = doc.select("section#anchor-playo table.table tbody tr")
        val extraRows = org.jsoup.select.Elements(playoffRows + if (regularRows.isEmpty()) emptyList() else europeRows)

        val club = sumSeasonRows(rows, extraRows)

        // Sezione separata sulla stessa pagina ("International competitions stats"): Olimpiadi/
        // EuroBasket/Mondiali con la Nazionale, stessa struttura di tabella (Season/Team/League/
        // Pts/Reb/Ast/GP/...) della carriera di club, verificata su Michael Jordan (USA, Olympics
        // 1984 e 1992). Se assente (giocatore mai convocato) i totali restano a zero.
        // L'id "anchor-international" è quello usato quando il giocatore ha più competizioni
        // internazionali; chi ne ha una sola (es. Larry Bird, solo Olimpiadi 1992) può avere
        // un id/intestazione diversa ("Olympic Games...") con lo stesso id generico assente: si
        // cerca quindi anche una qualunque sezione la cui intestazione parli di competizioni
        // internazionali/olimpiadi, non solo l'id esatto (verificato su Michael Jordan).
        val nationalSection = doc.selectFirst("section#anchor-international")
            ?: doc.select("section").firstOrNull { section ->
                section.select("h1, h2, h3, h4").any { heading ->
                    val text = heading.text().lowercase()
                    text.contains("international") || text.contains("olympic")
                }
            }
        val nationalRows = nationalSection?.select("table.table tbody tr") ?: org.jsoup.select.Elements()
        val national = sumSeasonRows(nationalRows)
        if (club.games == 0 && national.games == 0) return@runCatching null
        val ultimaStagione = (rows + extraRows + nationalRows).mapNotNull { row ->
            row.selectFirst("> td")?.text()?.let(::seasonEndYear)
        }.maxOrNull()

        // Blocco anagrafico ("Date of birth"/"Nationality"/"Position"), a fianco della tabella
        // statistiche sulla stessa pagina: coppie <span class="title">/<span class="info">
        // dentro "div.identity__stats__profil" (verificato su Michael Jordan).
        val profileFields = doc.select("div.identity__stats__profil > div").associate { row ->
            row.selectFirst("span.title")?.text()?.trim().orEmpty() to
                row.selectFirst("span.info")?.text()?.trim().orEmpty()
        }
        val nazione = profileFields["Nationality"]?.let(::countryFromDemonym)
        val annoNascita = profileFields["Date of birth"]
            ?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() }
        // "sg, sf" -> solo il primo (ruolo principale).
        val posizione = profileFields["Position"]?.split(',')?.firstOrNull()?.trim()?.lowercase()
            ?.let { proballersPositionAbbrev[it] }

        ProballersTotals(
            presenze = club.games,
            punteggio = club.points,
            assist = club.assists,
            competizione = club.topLeague,
            squadraPrincipale = club.topTeam,
            squadraPrincipaleLogoUrl = club.topTeamLogoUrl,
            nazione = nazione,
            annoNascita = annoNascita,
            posizione = posizione,
            presenzeNazionale = national.games,
            punteggioNazionale = national.points,
            assistNazionale = national.assists,
            rimbalzi = club.rebounds,
            rimbalziNazionale = national.rebounds,
            minutiCarriera = club.minutes,
            minutiNazionale = national.minutes,
            effMedio = club.effMedio,
            bestEffTeam = club.bestEff?.team,
            bestEffLogoUrl = club.bestEff?.logoUrl,
            bestEffStagione = club.bestEff?.season,
            bestEffValue = club.bestEff?.eff,
            ultimaStagione = ultimaStagione,
        )
    }.getOrNull()

    /**
     * Richiesta diretta (veloce); se Proballers la blocca con una sfida anti-bot Cloudflare
     * (vedi PlayerLookupService/SourceStatus per lo stesso problema sul pallino di stato), ultimo
     * tentativo più lento con [ProballersWebViewFetcher], che usa un vero motore di rendering
     * capace di eseguire il JS della sfida come un browser.
     */
    private suspend fun fetchHtml(profileUrl: String): String? = fetchHtmlWithFallback(profileUrl)

    /**
     * Qualsiasi URL giocatore Proballers, in qualunque lingua (es.
     * https://www.proballers.com/it/pallacanestro/giocatore/48763/nunzio-sabbatino) ->
     * https://www.proballers.com/basketball/player/48763/nunzio-sabbatino. L'id numerico è l'unica
     * parte che conta davvero; un URL non riconosciuto viene restituito così com'è.
     */
    fun canonicalProfileUrl(rawUrl: String): String {
        val match = Regex("""proballers\.com/(?:[a-z]{2}/)?[^/]+/[^/]+/(\d+)(?:/([^/?#\s]+))?""", RegexOption.IGNORE_CASE)
            .find(rawUrl.trim()) ?: return rawUrl.trim()
        val id = match.groupValues[1]
        val slug = match.groupValues[2]
        return "https://www.proballers.com/basketball/player/$id" + if (slug.isNotBlank()) "/$slug" else ""
    }

    /** Il college (NCAA) non è un club professionistico: escluso da squadra/competizione principale
     *  e dalla stagione migliore (vedi i commenti in [sumSeasonRows]). */
    private fun isCollegeLeague(league: String): Boolean = league.contains("NCAA", ignoreCase = true)

    /** I playoff valgono come la loro lega: "NBA Playoffs" -> "NBA", "USA-1 PO" -> "USA-1". */
    private fun baseLeague(league: String): String =
        league.replace(Regex("""(?:\s+|\s*-\s*)(Play-?offs?|PO)$""", RegexOption.IGNORE_CASE), "").trim()

    private data class BestEffRow(val team: String?, val logoUrl: String?, val season: String, val eff: Int, val league: String)

    private data class SeasonRowsTotals(
        val games: Int,
        val points: Int,
        val assists: Int,
        val topLeague: String,
        val topTeam: String?,
        val topTeamLogoUrl: String?,
        val rebounds: Int,
        val minutes: Int,
        val effMedio: Int,
        val bestEff: BestEffRow?,
    )

    /** Somma le righe di una tabella stagione-per-stagione Proballers (formato condiviso da
     * "Regular Season Stats" e "International competitions stats"): MEDIE a partita (Pts/Reb/Ast/
     * MIN) + GP, moltiplicate e sommate su tutte le stagioni. Le colonne oltre GP (indice 6) —
     * MIN (7) e, in fondo alla tabella estesa, Eff (ultima cella) — sono lette solo quando presenti
     * (tabella "International competitions" più corta, senza queste colonne): [BestEffRow]/
     * l'Eff medio restano quindi vuoti per la sezione Nazionale, che non ne ha bisogno.
     * [extraRows] (playoff, coppe nazionali ed europee) contano come la regular season per tutto:
     * totali, Eff medio (pesato sulle partite, così un playoff di 3 gare non vale quanto una stagione
     * intera), squadra e competizione principale, stagione migliore. Per non falsare le ultime due
     * i playoff valgono come la loro lega ("NBA Playoffs" = "NBA", vedi [baseLeague]), una stagione
     * conta una volta sola per lega, e la stagione migliore unisce tutte le righe della stessa
     * stagione con la stessa squadra (regular + playoff + coppe), con l'Eff pesato sulle partite. */
    private fun sumSeasonRows(
        rows: org.jsoup.select.Elements,
        extraRows: org.jsoup.select.Elements = org.jsoup.select.Elements(),
    ): SeasonRowsTotals {
        var totalGames = 0
        var totalPoints = 0
        var totalAssists = 0
        var totalRebounds = 0
        var totalMinutes = 0
        var effWeighted = 0.0
        var effGames = 0
        // Stagione+squadra -> partite, Eff pesato, partite per lega, stemma: per la stagione migliore.
        class SeasonTeam(val season: String, val team: String?, var logoUrl: String?) {
            var games = 0
            var effWeighted = 0.0
            var effGames = 0
            val leagueGames = mutableMapOf<String, Int>()
        }
        val seasonTeams = linkedMapOf<Pair<String, String?>, SeasonTeam>()
        // Stagioni distinte per lega (una stagione con regular e playoff conta una volta).
        val leagueSeasons = mutableMapOf<String, MutableSet<String>>()
        val collegeLeagueSeasons = mutableMapOf<String, MutableSet<String>>()
        val teamGames = mutableMapOf<String, Int>()
        val teamLogos = mutableMapOf<String, String>()
        // Partite per lega di ogni squadra: serve a sapere in che lega gioca la squadra principale.
        val teamLeagueGames = mutableMapOf<String, MutableMap<String, Int>>()
        // Stesse statistiche ma solo per il college: usate come ripiego quando il giocatore non
        // ha (ancora) nessuna stagione da professionista.
        val collegeTeamGames = mutableMapOf<String, Int>()

        for (row in rows + extraRows) {
            val cells = row.select("> td")
            if (cells.size < 7) continue

            val teamCell = cells[1]
            val teamName = teamCell.selectFirst("a")?.text()?.trim()?.ifBlank { null }
            val leagueName = baseLeague(
                cells[2].selectFirst("a")?.attr("title")?.ifBlank { null } ?: cells[2].text().trim()
            )
            val season = cells[0].text().trim()
            val ptsAvg = cells[3].text().trim().toDoubleOrNull()
            val rebAvg = cells[4].text().trim().toDoubleOrNull()
            val astAvg = cells[5].text().trim().toDoubleOrNull()
            val gp = cells[6].text().trim().toIntOrNull()
            // Colonna MIN (media a partita), subito dopo GP: presente solo nella tabella estesa
            // (club), non in quella "International competitions" (più corta).
            val minAvg = if (cells.size > 7) cells[7].text().trim().toDoubleOrNull() else null
            // Eff è sempre l'ultima colonna della tabella estesa (~21 colonne): la soglia esclude
            // la tabella Nazionale (più corta, niente Eff) invece di leggere per sbaglio un'altra
            // colonna come se fosse l'efficienza. Il valore è quasi sempre con un decimale (es.
            // "3.7"), quindi va letto come Double (toIntOrNull() su "3.7" fallisce silenziosamente
            // e ritorna null): verificato su Tyler Zeller, dove questo scartava quasi tutte le
            // stagioni e lasciava un solo valore intero "per caso" come media E massimo.
            val effExact = if (cells.size >= 20) cells.last()?.text()?.trim()?.toDoubleOrNull() else null

            if (gp != null && gp > 0) {
                totalGames += gp
                if (ptsAvg != null) totalPoints += (ptsAvg * gp).roundToInt()
                if (rebAvg != null) totalRebounds += (rebAvg * gp).roundToInt()
                if (astAvg != null) totalAssists += (astAvg * gp).roundToInt()
                if (minAvg != null) totalMinutes += (minAvg * gp).roundToInt()
                // Squadra/competizione principale = quella con più presenze da PROFESSIONISTA:
                // le stagioni NCAA (college) non contano, altrimenti 4 anni di college possono
                // superare una carriera pro più breve e diventare la "carriera migliore".
                val college = isCollegeLeague(leagueName)
                val leagues = if (college) collegeLeagueSeasons else leagueSeasons
                val teams = if (college) collegeTeamGames else teamGames
                if (leagueName.isNotBlank()) leagues.getOrPut(leagueName) { mutableSetOf() } += season
                if (teamName != null) {
                    teams[teamName] = (teams[teamName] ?: 0) + gp
                    if (leagueName.isNotBlank()) {
                        val byLeague = teamLeagueGames.getOrPut(teamName) { mutableMapOf() }
                        byLeague[leagueName] = (byLeague[leagueName] ?: 0) + gp
                    }
                    teamCell.selectFirst("img")?.attr("abs:src")?.ifBlank { null }
                        ?.let { teamLogos.putIfAbsent(teamName, it) }
                }
                if (effExact != null) {
                    effWeighted += effExact * gp
                    effGames += gp
                }
                // Il college (NCAA) non va considerato per la stagione "migliore" (secondo logo):
                // l'Eff universitario è calcolato contro un livello di gioco più basso di quello
                // professionistico (verificato su Tyler Zeller: la stagione NCAA 11-12 risultava
                // "periodo migliore" al posto di una stagione NBA).
                if (!college) {
                    val logoUrl = teamCell.selectFirst("img")?.attr("abs:src")?.ifBlank { null }
                    val st = seasonTeams.getOrPut(season to teamName) { SeasonTeam(season, teamName, logoUrl) }
                    if (st.logoUrl == null) st.logoUrl = logoUrl
                    st.games += gp
                    if (effExact != null) {
                        st.effWeighted += effExact * gp
                        st.effGames += gp
                    }
                    if (leagueName.isNotBlank()) st.leagueGames[leagueName] = (st.leagueGames[leagueName] ?: 0) + gp
                }
            }
        }

        // A parità di Eff vince la stagione che compare prima (come prima dell'unione delle righe).
        val bestEff = seasonTeams.values.filter { it.effGames > 0 }
            .map { st ->
                val league = st.leagueGames.maxByOrNull { it.value }?.key.orEmpty()
                BestEffRow(st.team, st.logoUrl, st.season, (st.effWeighted / st.effGames).roundToInt(), league)
            }
            .fold(null as BestEffRow?) { best, r -> if (best == null || r.eff > best.eff) r else best }
        val leagueCounts = leagueSeasons.mapValues { it.value.size }
        val collegeLeagueCounts = collegeLeagueSeasons.mapValues { it.value.size }

        val topTeam = (teamGames.ifEmpty { collegeTeamGames }).maxByOrNull { it.value }?.key
        val topTeamLeague = topTeam?.let { teamLeagueGames[it] }?.maxByOrNull { it.value }?.key
        // Ripiego "lega con più stagioni": NBA/WNBA escluse, altrimenti il bonus scatterebbe senza
        // che squadra principale o stagione migliore siano lì. Verificato su Wade Baldwin: 3 stagioni
        // NBA e 3 in Turchia (parità vinta dalla NBA solo perché compare prima), ma squadra
        // principale Fenerbahce e stagione migliore Raptors 905 (G League) -> bonus non dovuto.
        val seasonCounts = leagueCounts.ifEmpty { collegeLeagueCounts }
        val mostSeasonsLeague = (seasonCounts.filterKeys { !PlayerScoring.isBonusLeague(it) }.ifEmpty { seasonCounts })
            .maxByOrNull { it.value }?.key ?: ""
        // La lega con più stagioni da sola non basta: verificato su Simone Fontecchio, 9 stagioni
        // in Serie A contro 5 in NBA, ma squadra principale (Utah Jazz, più partite) e stagione
        // migliore (Detroit Pistons) entrambe NBA -> risultava "Italy - LBA Serie A" e niente bonus
        // NBA. Se la squadra principale o la stagione migliore sono in NBA (o WNBA, stesso bonus),
        // la competizione è quella lega.
        val topLeague = listOf(topTeamLeague, bestEff?.league).firstOrNull { PlayerScoring.isBonusLeague(it) }
            ?.trim() ?: mostSeasonsLeague
        return SeasonRowsTotals(
            games = totalGames,
            points = totalPoints,
            assists = totalAssists,
            topLeague = topLeague,
            topTeam = topTeam,
            topTeamLogoUrl = topTeam?.let { teamLogos[it] },
            rebounds = totalRebounds,
            minutes = totalMinutes,
            effMedio = if (effGames == 0) 0 else (effWeighted / effGames).roundToInt(),
            bestEff = bestEff,
        )
    }
}

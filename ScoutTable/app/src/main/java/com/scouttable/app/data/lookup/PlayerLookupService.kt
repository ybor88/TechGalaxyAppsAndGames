package com.scouttable.app.data.lookup

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Recupera automaticamente le informazioni di un giocatore da TheSportsDB (API pubblica gratuita,
 * chiave di test "123") dato solo il nome: sostituisce l'import manuale da file.
 * Funziona sia per il calcio (strSport = "Soccer") sia per il basket (strSport = "Basketball").
 * Best-effort: alcuni giocatori poco noti potrebbero non essere trovati.
 *
 * Statistiche di carriera (presenze/punteggio/assist/competizione):
 * - Calcio: sommate dall'infobox Wikipedia ([WikipediaCareerStats]) — Transfermarkt non è
 *   utilizzabile, le sue statistiche sono caricate via JavaScript e non raggiungibili con una
 *   richiesta HTTP semplice.
 * - Basket: stimate automaticamente dall'infobox Wikipedia ([WikipediaBasketballStats]); se
 *   l'utente incolla anche l'URL della pagina Proballers del giocatore, quei dati (più precisi,
 *   stagione per stagione) hanno la precedenza ([ProballersCareerStats]).
 */
object PlayerLookupService {

    private const val API_KEY = "123"
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/$API_KEY"

    sealed class LookupResult {
        data class Found(val row: PlayerImportRow) : LookupResult()
        data class NotFound(val query: String) : LookupResult()
        data class Error(val query: String, val message: String) : LookupResult()
    }

    /**
     * @param extraUrl per il basket, l'URL della pagina Proballers del giocatore (opzionale ma
     *   necessario per ottenere presenze/punteggio/assist/competizione).
     * @param expectedYear anno di nascita per disambiguare omonimi (es. "Francesco Totti" 1976).
     *   Se assente viene estratto automaticamente da un eventuale numero a 4 cifre in coda a [name].
     */
    suspend fun lookup(
        name: String,
        sport: Sport,
        existingId: String? = null,
        extraUrl: String? = null,
        expectedYear: Int? = null,
    ): LookupResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val (cleanName, parsedYear) = splitTrailingYear(name)
                val yearHint = expectedYear ?: parsedYear

                val encodedName = URLEncoder.encode(cleanName, "UTF-8")
                val searchJson = getJson("$BASE/searchplayers.php?p=$encodedName")
                val candidates = searchJson?.optJSONArray("player")

                val wantedSport = if (sport == Sport.BASKET) "Basketball" else "Soccer"
                val sportMatches = if (candidates == null) emptyList() else (0 until candidates.length())
                    .map { candidates.getJSONObject(it) }
                    .filter { it.optString("strSport").equals(wantedSport, ignoreCase = true) }

                // Se ci sono più omonimi dello stesso sport, l'anno di nascita sceglie quello giusto.
                val chosen = if (yearHint != null) {
                    sportMatches.firstOrNull { it.optString("dateBorn").take(4).toIntOrNull() == yearHint }
                } else null

                // TheSportsDB (gratuito) non copre molti campionati minori (es. Pietro Aradori,
                // Serie A basket): nessun risultato, o nessuno dello sport giusto, non deve
                // significare "non trovato" — si tenta Wikipedia direttamente prima di arrendersi.
                // In precedenza si ripiegava su candidates.getJSONObject(0), cioè un omonimo dello
                // SPORT SBAGLIATO pur di non restituire NotFound: dati completamente fuorvianti.
                val player = chosen ?: sportMatches.firstOrNull()
                    ?: return@withContext lookupViaWikipediaOnly(cleanName, sport, existingId, extraUrl)

                var anno = player.optString("dateBorn").take(4).toIntOrNull() ?: 0
                var nazione = player.optString("strNationality")
                val rawTeam = player.optString("strTeam")
                val rawPosition = player.optString("strPosition")
                // TheSportsDB usa una squadra placeholder ("_Retired Soccer"/"_Retired Basketball",
                // ma anche "_Deceased Soccer" per i giocatori che risultano deceduti nel loro
                // database — dato spesso sbagliato, es. Franco Baresi è vivo, ma comunque un segnale
                // che il profilo non è più aggiornato/attivo) al posto di un vero club per chi non
                // gioca più: non è un nome di club reale.
                // Per chi ha smesso di giocare ed è diventato allenatore/manager (es. Hernán Crespo),
                // TheSportsDB tiene un solo profilo e lo aggiorna al ruolo attuale (strPosition =
                // "Manager"/"Coach", strTeam = la squadra che allena oggi): va trattato come i
                // ritirati, ignorando squadra/stato attuali, per ottenere il profilo da GIOCATORE
                // (carriera e club recuperati da Wikipedia) e non quello da allenatore.
                val isCoachProfile = rawPosition.contains("manager", ignoreCase = true) ||
                    rawPosition.contains("coach", ignoreCase = true)
                val isRetiredPlaceholder = rawTeam.startsWith("_Retired", ignoreCase = true) ||
                    rawTeam.startsWith("_Deceased", ignoreCase = true) || isCoachProfile
                val stato = if (isRetiredPlaceholder) PlayerStatus.RITIRATO else mapStatus(player.optString("strStatus"))
                val resolvedName = player.optString("strPlayer").ifBlank { cleanName }
                var ruolo = if (isCoachProfile) "" else translateRole(rawPosition, sport)

                var club = if (isRetiredPlaceholder) "" else rawTeam
                var teamInfo = if (club.isNotBlank()) fetchTeamInfo(club) else null
                var presenze = 0
                var punteggio = 0
                var assist = 0
                var rimbalzi = 0
                var palleRecuperate = 0
                var percentualeTiriDaDue = 0
                var percentualeTiriDaTre = 0
                var tackle = 0
                var golEvitati = 0
                var golSubiti = 0

                if (sport == Sport.CALCIO) {
                    WikipediaCareerStats.fetchClubCareerTotals(resolvedName)?.let {
                        // Somma di TUTTA la carriera (tutti i club, non solo il migliore): vedi
                        // WikipediaCareerStats, che somma caps1+caps2+... su ogni club elencato.
                        presenze = it.presenze
                        punteggio = it.punteggio
                        // "Carriera migliore" = il club con più presenze in assoluto, sempre: non
                        // solo per i ritirati. Per un giocatore attivo (es. Cristiano Ronaldo ad
                        // Al-Nassr) il club ATTUALE di TheSportsDB non è necessariamente quello
                        // dove ha reso di più in carriera (es. Real Madrid); Wikipedia lo sostituisce
                        // ogni volta che lo trova, indipendentemente dallo stato del giocatore.
                        if (!it.club.isNullOrBlank()) {
                            club = it.club
                            teamInfo = fetchTeamInfo(club)
                        }
                        // TheSportsDB spesso non riporta più un ruolo per i giocatori ritirati
                        // (strPosition vuoto/obsoleto nel loro profilo placeholder): Wikipedia
                        // resta una fonte valida anche per loro.
                        if (ruolo.isBlank() && !it.posizione.isNullOrBlank()) {
                            ruolo = translateRole(it.posizione, sport)
                        }
                        // TheSportsDB a volte storpia leggermente il nome del giocatore
                        // (strPlayer, usato per resolvedName): quando manca, il paese di
                        // "nationalteamN" nell'infobox è un fallback affidabile (verificato su
                        // Michele Di Gregorio, registrato da TheSportsDB come "Michele Gregorio"
                        // con nazionalità comunque corretta lì — questo caso serve per quando
                        // anche TheSportsDB non ha nazionalità, es. dopo il fallback Wikipedia-only).
                        if (nazione.isBlank() && !it.nazione.isNullOrBlank()) {
                            nazione = it.nazione
                        }
                    }
                    // Wikipedia non traccia assist/tackle/intercettazioni/gol subiti: statmuse.com
                    // sì (vedi StatmuseCalcioStats), niente URL da incollare, ma dati difensivi
                    // affidabili solo dalle stagioni più recenti.
                    StatmuseCalcioStats.fetchCareerTotals(resolvedName)?.let {
                        assist = it.assist
                        tackle = it.tackle
                        golEvitati = it.golEvitati
                        golSubiti = it.golSubiti
                        // Rete di sicurezza se Wikipedia non ha trovato nulla (es. resolvedName
                        // leggermente sbagliato, vedi sopra): statmuse risolve la query in
                        // linguaggio naturale in modo più tollerante sul nome.
                        if (presenze == 0) presenze = it.presenze
                    }
                } else {
                    // Automatico da Wikipedia (nessun link da incollare), come per il calcio.
                    WikipediaBasketballStats.fetch(resolvedName)?.let {
                        presenze = it.presenze
                        punteggio = it.punteggio
                        assist = it.assist
                        rimbalzi = it.rimbalzi
                        if (!it.squadra.isNullOrBlank()) {
                            club = it.squadra
                            teamInfo = fetchTeamInfo(club)
                        }
                        if (!it.competizione.isNullOrBlank()) {
                            teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                        }
                        if (ruolo.isBlank() && !it.posizione.isNullOrBlank()) {
                            ruolo = translateRole(it.posizione, sport)
                        }
                        // TheSportsDB può non avere nazionalità per profili poco aggiornati:
                        // Wikipedia riporta spesso un campo "nationality" esplicito (es. "Italian
                        // / British" per Carlton Myers) da cui dedurre almeno la prima nazionalità.
                        if (nazione.isBlank() && !it.nazione.isNullOrBlank()) {
                            nazione = it.nazione
                        }
                    }
                    // Wikipedia spesso non traccia i totali di carriera per i cestisti (comune per
                    // giocatori europei, es. Dino Meneghin): un secondo tentativo automatico su
                    // basketball-reference.com prima di arrendersi e chiedere l'URL Proballers a
                    // mano. Scarta da solo i risultati troppo parziali per essere una carriera vera
                    // (es. sole Olimpiadi): vedi BasketballReferenceStats. Interrogata anche quando
                    // Wikipedia ha già dato punti/assist ma rimbalzi/palle recuperate/tiri sono
                    // ancora a zero: sono proprio i giocatori NBA più moderni (con Wikipedia già
                    // completo per punti/assist) ad avere più probabilità di avere queste colonne
                    // tracciate su basketball-reference, quindi senza questo secondo caso salterebbero
                    // sempre questa fonte.
                    val needsShootingStats = rimbalzi == 0 || palleRecuperate == 0 ||
                        percentualeTiriDaDue == 0 || percentualeTiriDaTre == 0
                    if ((punteggio == 0 && assist == 0) || needsShootingStats) {
                        BasketballReferenceStats.fetchCareerTotals(resolvedName)?.let {
                            if (punteggio == 0 && assist == 0) {
                                presenze = it.presenze
                                punteggio = it.punteggio
                                assist = it.assist
                                if (ruolo.isBlank() && !it.posizione.isNullOrBlank()) {
                                    ruolo = translateRole(it.posizione, sport)
                                }
                                if (nazione.isBlank() && !it.nazione.isNullOrBlank()) {
                                    nazione = it.nazione
                                }
                            }
                            if (rimbalzi == 0) rimbalzi = it.rimbalzi
                            if (palleRecuperate == 0) palleRecuperate = it.palleRecuperate
                            if (percentualeTiriDaDue == 0) percentualeTiriDaDue = it.percentualeTiriDaDue
                            if (percentualeTiriDaTre == 0) percentualeTiriDaTre = it.percentualeTiriDaTre
                        }
                    }
                    // Se l'utente ha incollato un URL Proballers, i suoi dati (più precisi,
                    // stagione per stagione) hanno la precedenza su quelli stimati da Wikipedia.
                    if (!extraUrl.isNullOrBlank()) {
                        ProballersCareerStats.fetchCareerTotals(extraUrl)?.let {
                            presenze = it.presenze
                            punteggio = it.punteggio
                            assist = it.assist
                            rimbalzi = it.rimbalzi
                            if (!it.squadraPrincipale.isNullOrBlank()) {
                                club = it.squadraPrincipale
                                teamInfo = fetchTeamInfo(club)
                            }
                            if (it.competizione.isNotBlank()) {
                                teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                            }
                            // La pagina Proballers ha anche nazionalità/data di nascita/ruolo
                            // (blocco "Date of birth"/"Nationality"/"Position" a fianco delle
                            // statistiche): usati quando l'utente incolla l'URL così da recuperare
                            // tutto in un colpo solo, non solo le statistiche.
                            if (!it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                            if (!it.nazione.isNullOrBlank()) nazione = it.nazione
                            if (it.annoNascita != null && anno == 0) anno = it.annoNascita
                        }
                    }
                }

                // Stemma del club al primo posto (è quello associato alla "carriera migliore"
                // mostrata); se manca, foto reale del giocatore da Wikipedia; se manca pure quella,
                // la UI mostra un avatar generato (mai una riga senza immagine, come richiesto).
                val fotoGiocatore = if (teamInfo?.badge.isNullOrBlank()) WikipediaPlayerPhoto.fetch(resolvedName) else null

                LookupResult.Found(
                    PlayerImportRow(
                        id = existingId,
                        nome = resolvedName,
                        anno = anno,
                        carrieraMigliore = club,
                        stato = stato,
                        nazione = nazione,
                        logoPath = teamInfo?.badge ?: fotoGiocatore,
                        ruolo = ruolo,
                        presenze = presenze,
                        punteggio = punteggio,
                        assist = assist,
                        competizione = teamInfo?.league.orEmpty(),
                        rimbalzi = rimbalzi,
                        palleRecuperate = palleRecuperate,
                        percentualeTiriDaDue = percentualeTiriDaDue,
                        percentualeTiriDaTre = percentualeTiriDaTre,
                        tackle = tackle,
                        golEvitati = golEvitati,
                        golSubiti = golSubiti,
                        proballersUrl = if (sport == Sport.BASKET) extraUrl else null,
                    )
                )
            }.getOrElse { LookupResult.Error(name, it.message ?: "errore sconosciuto") }
        }

    /**
     * Quando TheSportsDB non ha nessun risultato (o nessuno dello sport giusto) per il nome
     * cercato: prova a trovare comunque il giocatore direttamente su Wikipedia (club/statistiche/
     * ruolo/anno di nascita/foto). Nazione e stato non sono ricavabili in modo affidabile da qui
     * (l'infobox non ha un campo "nazionalità" strutturato) e restano vuoti: l'utente li completa
     * a mano in "Modifica giocatore", ma almeno il resto dei dati non manca del tutto.
     */
    private suspend fun lookupViaWikipediaOnly(
        cleanName: String,
        sport: Sport,
        existingId: String?,
        extraUrl: String?,
    ): LookupResult {
        val wikiTitle = WikipediaPlayerSearch.findTitle(cleanName) ?: return LookupResult.NotFound(cleanName)
        val resolvedName = wikiTitle.replace('_', ' ')

        var club = ""
        var teamInfo: TeamInfo? = null
        var presenze = 0
        var punteggio = 0
        var assist = 0
        var ruolo = ""
        var anno = 0
        var nazione = ""
        var rimbalzi = 0
        var palleRecuperate = 0
        var percentualeTiriDaDue = 0
        var percentualeTiriDaTre = 0
        var tackle = 0
        var golEvitati = 0
        var golSubiti = 0

        if (sport == Sport.CALCIO) {
            WikipediaCareerStats.fetchClubCareerTotals(resolvedName)?.let {
                presenze = it.presenze
                punteggio = it.punteggio
                if (!it.club.isNullOrBlank()) {
                    club = it.club
                    teamInfo = fetchTeamInfo(club)
                }
                if (!it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                if (it.annoNascita != null) anno = it.annoNascita
                // In questo percorso (TheSportsDB non ha trovato nulla) la nazione non è mai
                // impostata da nessun'altra fonte: "nationalteamN" nell'infobox è meglio di niente.
                if (!it.nazione.isNullOrBlank()) nazione = it.nazione
            }
            StatmuseCalcioStats.fetchCareerTotals(resolvedName)?.let {
                assist = it.assist
                tackle = it.tackle
                golEvitati = it.golEvitati
                golSubiti = it.golSubiti
                if (presenze == 0) presenze = it.presenze
            }
        } else {
            WikipediaBasketballStats.fetch(resolvedName)?.let {
                presenze = it.presenze
                punteggio = it.punteggio
                assist = it.assist
                rimbalzi = it.rimbalzi
                if (!it.squadra.isNullOrBlank()) {
                    club = it.squadra
                    teamInfo = fetchTeamInfo(club)
                }
                if (!it.competizione.isNullOrBlank()) {
                    teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                }
                if (!it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                if (it.annoNascita != null) anno = it.annoNascita
                if (!it.nazione.isNullOrBlank()) nazione = it.nazione
            }
            val needsShootingStats = rimbalzi == 0 || palleRecuperate == 0 ||
                percentualeTiriDaDue == 0 || percentualeTiriDaTre == 0
            if ((punteggio == 0 && assist == 0) || needsShootingStats) {
                BasketballReferenceStats.fetchCareerTotals(resolvedName)?.let {
                    if (punteggio == 0 && assist == 0) {
                        presenze = it.presenze
                        punteggio = it.punteggio
                        assist = it.assist
                        if (ruolo.isBlank() && !it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                        if (nazione.isBlank() && !it.nazione.isNullOrBlank()) nazione = it.nazione
                        if (anno == 0 && it.annoNascita != null) anno = it.annoNascita
                    }
                    if (rimbalzi == 0) rimbalzi = it.rimbalzi
                    if (palleRecuperate == 0) palleRecuperate = it.palleRecuperate
                    if (percentualeTiriDaDue == 0) percentualeTiriDaDue = it.percentualeTiriDaDue
                    if (percentualeTiriDaTre == 0) percentualeTiriDaTre = it.percentualeTiriDaTre
                }
            }
            if (!extraUrl.isNullOrBlank()) {
                ProballersCareerStats.fetchCareerTotals(extraUrl)?.let {
                    presenze = it.presenze
                    punteggio = it.punteggio
                    assist = it.assist
                    rimbalzi = it.rimbalzi
                    if (!it.squadraPrincipale.isNullOrBlank()) {
                        club = it.squadraPrincipale
                        teamInfo = fetchTeamInfo(club)
                    }
                    if (it.competizione.isNotBlank()) {
                        teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                    }
                    if (!it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                    if (!it.nazione.isNullOrBlank()) nazione = it.nazione
                    if (it.annoNascita != null && anno == 0) anno = it.annoNascita
                }
            }
        }

        if (club.isBlank() && presenze == 0 && punteggio == 0) return LookupResult.NotFound(cleanName)

        val fotoGiocatore = if (teamInfo?.badge.isNullOrBlank()) WikipediaPlayerPhoto.fetch(resolvedName) else null

        return LookupResult.Found(
            PlayerImportRow(
                id = existingId,
                nome = resolvedName,
                anno = anno,
                carrieraMigliore = club,
                stato = "",
                nazione = nazione,
                logoPath = teamInfo?.badge ?: fotoGiocatore,
                ruolo = ruolo,
                presenze = presenze,
                punteggio = punteggio,
                assist = assist,
                competizione = teamInfo?.league.orEmpty(),
                rimbalzi = rimbalzi,
                palleRecuperate = palleRecuperate,
                percentualeTiriDaDue = percentualeTiriDaDue,
                percentualeTiriDaTre = percentualeTiriDaTre,
                tackle = tackle,
                golEvitati = golEvitati,
                golSubiti = golSubiti,
                proballersUrl = if (sport == Sport.BASKET) extraUrl else null,
            )
        )
    }

    /** "Francesco Totti 1976" -> ("Francesco Totti", 1976). Nessun numero finale -> anno null. */
    private fun splitTrailingYear(raw: String): Pair<String, Int?> {
        val trimmed = raw.trim()
        val match = Regex("""^(.*\S)\s+((?:18|19|20)\d{2})$""").find(trimmed) ?: return trimmed to null
        return match.groupValues[1].trim() to match.groupValues[2].toInt()
    }

    private data class TeamInfo(val badge: String?, val league: String)

    // Il logo del club è il campo su cui l'utente ha insistito di più: un retry silenzioso
    // assorbe i blip temporanei della chiave di test gratuita di TheSportsDB (rate limit basso).
    // Se il nome contiene un trattino (es. "Paris Saint-Germain") si ritenta anche senza: la
    // ricerca squadre di TheSportsDB non trova nulla con l'esatto trattino su diversi club noti,
    // pur avendo l'esatto badge se interrogata con uno spazio al suo posto ("Paris Saint Germain").
    private fun fetchTeamInfo(teamName: String): TeamInfo? =
        fetchTeamInfoOnce(teamName)
            ?: fetchTeamInfoOnce(teamName)
            ?: teamName.takeIf { it.contains('-') }?.replace('-', ' ')?.let { fetchTeamInfoOnce(it) }

    private fun fetchTeamInfoOnce(teamName: String): TeamInfo? = runCatching {
        val encoded = URLEncoder.encode(teamName, "UTF-8")
        val json = getJson("$BASE/searchteams.php?t=$encoded")
        val team = json?.optJSONArray("teams")?.let { if (it.length() > 0) it.getJSONObject(0) else null }
            ?: return@runCatching null
        val badge = team.optString("strBadge").ifBlank { null } ?: team.optString("strLogo").ifBlank { null }
        TeamInfo(badge, team.optString("strLeague"))
    }.getOrNull()

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()
            if (body.isNullOrBlank() || body == "null") return null
            return JSONObject(body)
        }
    }

    private fun mapStatus(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("retire") -> "Ritirato"
            lower.contains("deceas") -> "Ritirato"
            lower.contains("injur") -> "Infortunato"
            lower.contains("free") -> "Svincolato"
            else -> "Attivo"
        }
    }
}

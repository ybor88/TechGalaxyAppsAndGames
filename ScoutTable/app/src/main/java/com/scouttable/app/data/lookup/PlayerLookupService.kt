// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.encodeYouthClubs
import com.scouttable.app.data.importexport.PlayerImportRow
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
     *   necessario per ottenere presenze/punteggio/assist/competizione); per il calcio, l'URL della
     *   pagina Wikipedia (it o en) incollato dall'utente, che identifica la voce senza ambiguità.
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
                // L'URL Proballers identifica il giocatore senza ambiguità: letto per primo, così il
                // suo anno di nascita scarta gli omonimi su TheSportsDB/Wikipedia/basketball-reference.
                // Verificato su Derrick Alston (figlio, 1997): tutte e tre le fonti restituivano il
                // padre omonimo (1972, ex NBA, oggi allenatore) e l'URL ne correggeva solo una parte
                // (anno, stato "Ritirato", college e percentuali di tiro restavano quelli del padre).
                // Solo un vero URL Proballers: una voce Wikipedia incollata per un cestista non è
                // una pagina Proballers da leggere.
                val proballers = if (sport == Sport.BASKET && extraUrl?.contains("proballers", ignoreCase = true) == true) {
                    ProballersCareerStats.fetchCareerTotals(extraUrl)
                } else null
                // Calcio con URL Wikipedia incollato: la voce indicata dall'utente è letta per
                // prima, così il suo anno di nascita scarta gli omonimi su TheSportsDB (verificato
                // su Thomas Kristensen: TheSportsDB conosce solo l'omonimo del 1983).
                val wikiLink = if (sport == Sport.CALCIO) parseWikipediaUrl(extraUrl) else null
                val itPrefetch = wikiLink?.let { link ->
                    val itTitle = if (link.lang == "it") link.title else WikipediaItCareerStats.linkedTitle("en", link.title, "it")
                    itTitle?.let { WikipediaItCareerStats.fetch(cleanName, expectedYear ?: parsedYear, exactTitle = it) }
                }
                val enTitleHint = wikiLink?.takeIf { it.lang == "en" }?.title
                val yearHint = expectedYear ?: parsedYear ?: proballers?.annoNascita ?: itPrefetch?.annoNascita

                val encodedName = URLEncoder.encode(cleanName, "UTF-8")
                val searchJson = getJson("$BASE/searchplayers.php?p=$encodedName")
                val candidates = searchJson?.optJSONArray("player")

                val wantedSport = if (sport == Sport.BASKET) "Basketball" else "Soccer"
                val sportMatches = if (candidates == null) emptyList() else (0 until candidates.length())
                    .map { candidates.getJSONObject(it) }
                    .filter { it.optString("strSport").equals(wantedSport, ignoreCase = true) }

                // Se ci sono più omonimi dello stesso sport, l'anno di nascita sceglie quello giusto.
                // Con un anno noto, un candidato nato in un ALTRO anno è un omonimo e va scartato
                // (prima si ripiegava comunque sul primo risultato): resta accettabile solo un
                // candidato senza data di nascita, che non si può smentire.
                val chosen = if (yearHint != null) {
                    sportMatches.firstOrNull { it.optString("dateBorn").take(4).toIntOrNull() == yearHint }
                        ?: sportMatches.firstOrNull { it.optString("dateBorn").take(4).toIntOrNull() == null }
                } else sportMatches.firstOrNull()

                // TheSportsDB (gratuito) non copre molti campionati minori (es. Pietro Aradori,
                // Serie A basket): nessun risultato, o nessuno dello sport giusto, non deve
                // significare "non trovato" — si tenta Wikipedia direttamente prima di arrendersi.
                // In precedenza si ripiegava su candidates.getJSONObject(0), cioè un omonimo dello
                // SPORT SBAGLIATO pur di non restituire NotFound: dati completamente fuorvianti.
                val player = chosen
                    ?: return@withContext lookupViaWikipediaOnly(
                        cleanName, sport, existingId, extraUrl, proballers, yearHint, itPrefetch, enTitleHint,
                    )

                var anno = player.optString("dateBorn").take(4).toIntOrNull() ?: 0
                var nazione = cleanApiText(player.optString("strNationality"))
                val rawTeam = cleanApiText(player.optString("strTeam"))
                val rawPosition = player.optString("strPosition")
                // TheSportsDB usa una squadra placeholder ("_Retired Soccer"/"_Retired Basketball",
                // ma anche "_Deceased Soccer" per i giocatori che risultano deceduti nel loro
                // database — dato spesso sbagliato, es. Franco Baresi è vivo, ma comunque un segnale
                // che il profilo non è più aggiornato/attivo) al posto di un vero club per chi non
                // gioca più: non è un nome di club reale.
                // Per chi ha smesso di giocare ed è diventato allenatore/manager/dirigente (es.
                // Hernán Crespo come allenatore, o Edwin van der Sar diventato CEO dell'Ajax),
                // TheSportsDB tiene un solo profilo e lo aggiorna al ruolo attuale (strPosition =
                // "Manager"/"Coach"/"CEO"/"Chairman"/..., strTeam = la squadra/società di oggi): va
                // trattato come i ritirati, ignorando ruolo/squadra/stato attuali, per ottenere il
                // profilo da GIOCATORE (ruolo, carriera e club recuperati da Wikipedia) e non quello
                // professionale attuale. Si controlla che rawPosition NON sia un vero ruolo da
                // giocatore (isKnownPlayingPosition) invece di elencare a mano ogni possibile
                // etichetta dirigenziale, altrimenti basta un titolo non previsto (es. "President",
                // "Sporting Director") per far scattare di nuovo lo stesso bug.
                val isCoachProfile = rawPosition.isNotBlank() && !isKnownPlayingPosition(rawPosition, sport)
                val isRetiredPlaceholder = rawTeam.startsWith("_Retired", ignoreCase = true) ||
                    rawTeam.startsWith("_Deceased", ignoreCase = true) || isCoachProfile
                // "_Free Agent Basketball"/"_Free Agent Soccer": anche questa è una squadra
                // placeholder, non un club (verificato su Reggie Jackson, finito in lista con
                // "_Free Agent Basketball" come carriera migliore): il giocatore è svincolato.
                val isFreeAgentPlaceholder = rawTeam.startsWith("_Free Agent", ignoreCase = true)
                val stato = when {
                    isRetiredPlaceholder -> PlayerStatus.RITIRATO
                    isFreeAgentPlaceholder -> PlayerStatus.SVINCOLATO
                    else -> mapStatus(player.optString("strStatus"))
                }
                // TheSportsDB restituisce alcuni caratteri come entità HTML (verificato su Samuel Eto'o:
                // strPlayer = "Samuel Eto&#039;o"): senza decodifica quel nome finisce così in lista e
                // fa fallire tutte le ricerche successive su Wikipedia/statmuse.
                var resolvedName = cleanApiText(player.optString("strPlayer")).ifBlank { cleanName }
                var ruolo = if (isCoachProfile) "" else translateRole(rawPosition, sport)

                var club = if (isRetiredPlaceholder || rawTeam.startsWith("_")) "" else rawTeam
                var teamInfo = if (club.isNotBlank()) fetchTeamInfo(club, sport) else null
                // Titolo Wikipedia verificato (voce del giocatore giusto) da cui prendere la foto:
                // mai il nome cercato così com'è, che può essere un omonimo (verificato su "Reggie
                // Jackson": la voce inglese è il giocatore di baseball, la foto era la sua).
                var photoTitle: String? = null
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
                var presenzeNazionale = 0
                var punteggioNazionale = 0
                var golSubitiNazionale = 0
                var secondLogoClub = ""
                var secondLogoPath: String? = null
                var secondLogoPeriodo = ""
                var secondLogoPresenze = 0
                var secondLogoGol = 0
                var giovanili: String? = null
                var college = ""
                var effMedio = 0
                var minutiCarriera = 0
                var minutiNazionale = 0
                var secondLogoEff = 0

                if (sport == Sport.CALCIO) {
                    val calcio = fetchCalcio(
                        name = resolvedName,
                        yearHint = yearHint ?: anno.takeIf { it != 0 },
                        teamHint = club.ifBlank { null },
                        itPrefetch = itPrefetch,
                        enTitleHint = enTitleHint,
                    )
                    calcio.displayName?.let { resolvedName = it }
                    if (calcio.club.isNotBlank()) {
                        club = calcio.club
                        teamInfo = calcio.teamInfo
                    }
                    presenze = calcio.presenze
                    punteggio = calcio.punteggio
                    golSubiti = calcio.golSubiti
                    assist = calcio.assist
                    tackle = calcio.tackle
                    golEvitati = calcio.golEvitati
                    calcio.ruolo?.let { ruolo = it }
                    if (anno == 0) calcio.annoNascita?.let { anno = it }
                    if (nazione.isBlank()) calcio.nazione?.let { nazione = it }
                    presenzeNazionale = calcio.presenzeNazionale
                    punteggioNazionale = calcio.punteggioNazionale
                    golSubitiNazionale = calcio.golSubitiNazionale
                    secondLogoClub = calcio.secondLogoClub
                    secondLogoPath = calcio.secondLogoPath
                    secondLogoPeriodo = calcio.secondLogoPeriodo
                    secondLogoPresenze = calcio.secondLogoPresenze
                    secondLogoGol = calcio.secondLogoGol
                    giovanili = calcio.giovanili
                    photoTitle = calcio.enTitle
                } else {
                    // Automatico da Wikipedia (nessun link da incollare), come per il calcio.
                    // Anche l'anno di TheSportsDB: senza, gli omonimi su en.wikipedia non si
                    // distinguono (es. "Reggie Jackson (basketball, born 1990)").
                    val basketWiki = fetchBasketballWiki(resolvedName, yearHint ?: anno.takeIf { it != 0 })
                    photoTitle = basketWiki?.first
                    basketWiki?.second?.let {
                        presenze = it.presenze
                        punteggio = it.punteggio
                        assist = it.assist
                        rimbalzi = it.rimbalzi
                        if (!it.squadra.isNullOrBlank()) {
                            club = it.squadra
                            teamInfo = fetchTeamInfo(club, sport)
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
                        if (!it.college.isNullOrBlank()) college = it.college
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
                        BasketballReferenceStats.fetchCareerTotals(resolvedName)
                            ?.takeIf { yearCompatible(it.annoNascita, yearHint) }?.let {
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
                    proballers?.let {
                        presenze = it.presenze
                        punteggio = it.punteggio
                        assist = it.assist
                        rimbalzi = it.rimbalzi
                        if (!it.squadraPrincipale.isNullOrBlank()) {
                            club = it.squadraPrincipale
                            teamInfo = fetchTeamInfo(club, sport)
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
                        // Proballers identifica il giocatore dall'URL: il suo anno vince sempre.
                        if (it.annoNascita != null) anno = it.annoNascita
                        // Eff/minuti/secondo logo: disponibili solo con l'URL Proballers
                        // incollato, stessa limitazione già esistente per rimbalzi/Nazionale.
                        effMedio = it.effMedio
                        minutiCarriera = it.minutiCarriera
                        minutiNazionale = it.minutiNazionale
                        if (it.presenzeNazionale > 0) presenzeNazionale = it.presenzeNazionale
                        if (it.punteggioNazionale > 0) punteggioNazionale = it.punteggioNazionale
                        if (!it.bestEffTeam.isNullOrBlank()) {
                            secondLogoClub = it.bestEffTeam
                            secondLogoPath = it.bestEffLogoUrl
                            secondLogoPeriodo = it.bestEffStagione.orEmpty()
                            secondLogoEff = it.bestEffValue ?: 0
                        }
                    }
                }

                // Stemma del club al primo posto (è quello associato alla "carriera migliore"
                // mostrata). La foto del giocatore solo se non c'è proprio un club: con un club noto
                // ma senza stemma trovato la foto finiva al posto dello stemma della squadra con più
                // presenze; meglio l'avatar generato dalla UI. Foto da TheSportsDB (è del giocatore
                // scelto) o dalla voce Wikipedia verificata, mai da una ricerca per nome.
                val fotoGiocatore = if (teamInfo?.badge.isNullOrBlank() && club.isBlank()) {
                    player.optString("strThumb").ifBlank { null }
                        ?: photoTitle?.let { WikipediaPlayerPhoto.fetch(it) }
                } else null

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
                        presenzeNazionale = presenzeNazionale,
                        punteggioNazionale = punteggioNazionale,
                        golSubitiNazionale = golSubitiNazionale,
                        proballersUrl = sourceUrlToKeep(sport, extraUrl),
                        secondLogoClub = secondLogoClub,
                        secondLogoPath = secondLogoPath,
                        secondLogoPeriodo = secondLogoPeriodo,
                        secondLogoPresenze = secondLogoPresenze,
                        secondLogoGol = secondLogoGol,
                        giovanili = giovanili,
                        secondLogoEff = secondLogoEff,
                        effMedio = effMedio,
                        minutiCarriera = minutiCarriera,
                        minutiNazionale = minutiNazionale,
                        college = college,
                    )
                )
            }.getOrElse { LookupResult.Error(name, it.message ?: "errore sconosciuto") }
        }

    /**
     * Come [lookup], ma ritenta una volta dopo una breve pausa se il primo tentativo termina in
     * "Error" (eccezione di rete/parsing, es. un blip temporaneo della chiave di test gratuita di
     * TheSportsDB o un timeout): un errore transitorio non va confuso con un vero "NotFound".
     * Usata da qualsiasi schermata che faccia lookup in serie su più giocatori (Genera/Aggiorna da
     * lista, Revisione), così che la ricerca sia identica ovunque invece di rifare a mano una
     * versione più fragile (senza retry) in un punto e non nell'altro.
     */
    suspend fun lookupWithRetry(
        name: String,
        sport: Sport,
        existingId: String? = null,
        extraUrl: String? = null,
        expectedYear: Int? = null,
    ): LookupResult {
        val result = lookup(name, sport, existingId, extraUrl, expectedYear)
        if (result !is LookupResult.Error) return result
        delay(1500)
        return lookup(name, sport, existingId, extraUrl, expectedYear)
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
        proballers: ProballersTotals?,
        yearHint: Int? = null,
        itPrefetch: ItCareerData? = null,
        enTitleHint: String? = null,
    ): LookupResult {
        // Ricerca solo se l'utente non ha già indicato la voce con l'URL.
        val searched = if (itPrefetch == null && enTitleHint == null) WikipediaPlayerSearch.findTitleWithLang(cleanName, sport) else null
        val wikiTitle = itPrefetch?.title ?: enTitleHint ?: searched?.title
            ?: return lookupViaProballersOnly(cleanName, sport, existingId, extraUrl, proballers, yearHint)
        // Il titolo può contenere la disambiguazione ("Thomas Kristensen (calciatore 2002)"): come
        // nome in lista va senza parentesi.
        var resolvedName = wikiTitle.replace('_', ' ').replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()
        var photoTitle: String? = null

        var club = ""
        var teamInfo: TeamInfo? = null
        var presenze = 0
        var punteggio = 0
        var assist = 0
        var ruolo = ""
        var anno = yearHint ?: 0
        var nazione = ""
        var rimbalzi = 0
        var palleRecuperate = 0
        var percentualeTiriDaDue = 0
        var percentualeTiriDaTre = 0
        var tackle = 0
        var golEvitati = 0
        var golSubiti = 0
        var presenzeNazionale = 0
        var punteggioNazionale = 0
        var golSubitiNazionale = 0
        var secondLogoClub = ""
        var secondLogoPath: String? = null
        var secondLogoPeriodo = ""
        var secondLogoPresenze = 0
        var secondLogoGol = 0
        var giovanili: String? = null
        var college = ""
        var effMedio = 0
        var minutiCarriera = 0
        var minutiNazionale = 0
        var secondLogoEff = 0

        if (sport == Sport.CALCIO) {
            // Voce it.wikipedia trovata dalla ricerca: provata per prima, ma (a differenza di un URL
            // incollato dall'utente) soggetta al controllo dell'anno di nascita, perché la ricerca
            // per nome può restituire un omonimo.
            val searchedItTitle = searched?.takeIf { it.lang == "it" }?.title
            val calcio = fetchCalcio(
                name = resolvedName,
                yearHint = yearHint,
                teamHint = null,
                itPrefetch = itPrefetch ?: searchedItTitle?.let {
                    WikipediaItCareerStats.fetch(resolvedName, yearHint, exactTitle = it, trustExactTitle = false)
                },
                enTitleHint = enTitleHint,
            )
            calcio.displayName?.let { resolvedName = it }
            club = calcio.club
            teamInfo = calcio.teamInfo
            presenze = calcio.presenze
            punteggio = calcio.punteggio
            golSubiti = calcio.golSubiti
            assist = calcio.assist
            tackle = calcio.tackle
            golEvitati = calcio.golEvitati
            calcio.ruolo?.let { ruolo = it }
            if (anno == 0) calcio.annoNascita?.let { anno = it }
            calcio.nazione?.let { nazione = it }
            presenzeNazionale = calcio.presenzeNazionale
            punteggioNazionale = calcio.punteggioNazionale
            golSubitiNazionale = calcio.golSubitiNazionale
            secondLogoClub = calcio.secondLogoClub
            secondLogoPath = calcio.secondLogoPath
            secondLogoPeriodo = calcio.secondLogoPeriodo
            secondLogoPresenze = calcio.secondLogoPresenze
            secondLogoGol = calcio.secondLogoGol
            giovanili = calcio.giovanili
            photoTitle = calcio.enTitle
        } else {
            // La ricerca Wikipedia per prefisso può restituire una pagina qualunque (un omonimo,
            // o una voce che non è di un cestista): se non contiene un infobox da cestista e
            // l'utente ha incollato l'URL Proballers, quello identifica il giocatore senza
            // ambiguità ed è la fonte da usare, col nome scritto dall'utente. Lo stesso vale per un
            // omonimo nato in un altro anno (vedi fetchBasketballWiki).
            val wikiMatch = fetchBasketballWiki(resolvedName, yearHint)
            if (wikiMatch == null && proballers != null) {
                return lookupViaProballersOnly(cleanName, sport, existingId, extraUrl, proballers, yearHint)
            }
            wikiMatch?.let { (title, _) -> resolvedName = title; photoTitle = title }
            wikiMatch?.second?.let {
                presenze = it.presenze
                punteggio = it.punteggio
                assist = it.assist
                rimbalzi = it.rimbalzi
                if (!it.squadra.isNullOrBlank()) {
                    club = it.squadra
                    teamInfo = fetchTeamInfo(club, sport)
                }
                if (!it.competizione.isNullOrBlank()) {
                    teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                }
                if (!it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                if (it.annoNascita != null) anno = it.annoNascita
                if (!it.nazione.isNullOrBlank()) nazione = it.nazione
                if (!it.college.isNullOrBlank()) college = it.college
            }
            val needsShootingStats = rimbalzi == 0 || palleRecuperate == 0 ||
                percentualeTiriDaDue == 0 || percentualeTiriDaTre == 0
            if ((punteggio == 0 && assist == 0) || needsShootingStats) {
                BasketballReferenceStats.fetchCareerTotals(resolvedName)
                    ?.takeIf { yearCompatible(it.annoNascita, yearHint) }?.let {
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
            proballers?.let {
                presenze = it.presenze
                punteggio = it.punteggio
                assist = it.assist
                rimbalzi = it.rimbalzi
                if (!it.squadraPrincipale.isNullOrBlank()) {
                    club = it.squadraPrincipale
                    teamInfo = fetchTeamInfo(club, sport)
                }
                if (it.competizione.isNotBlank()) {
                    teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                }
                if (!it.posizione.isNullOrBlank()) ruolo = translateRole(it.posizione, sport)
                if (!it.nazione.isNullOrBlank()) nazione = it.nazione
                if (it.annoNascita != null) anno = it.annoNascita
                effMedio = it.effMedio
                minutiCarriera = it.minutiCarriera
                minutiNazionale = it.minutiNazionale
                if (it.presenzeNazionale > 0) presenzeNazionale = it.presenzeNazionale
                if (it.punteggioNazionale > 0) punteggioNazionale = it.punteggioNazionale
                if (!it.bestEffTeam.isNullOrBlank()) {
                    secondLogoClub = it.bestEffTeam
                    secondLogoPath = it.bestEffLogoUrl
                    secondLogoPeriodo = it.bestEffStagione.orEmpty()
                    secondLogoEff = it.bestEffValue ?: 0
                }
            }
        }

        if (club.isBlank() && presenze == 0 && punteggio == 0) return LookupResult.NotFound(cleanName)

        // Vedi lookup(): foto solo senza club, e solo da una voce verificata.
        val fotoGiocatore = if (teamInfo?.badge.isNullOrBlank() && club.isBlank()) {
            photoTitle?.let { WikipediaPlayerPhoto.fetch(it) }
        } else null

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
                presenzeNazionale = presenzeNazionale,
                punteggioNazionale = punteggioNazionale,
                golSubitiNazionale = golSubitiNazionale,
                proballersUrl = sourceUrlToKeep(sport, extraUrl),
                secondLogoClub = secondLogoClub,
                secondLogoPath = secondLogoPath,
                secondLogoPeriodo = secondLogoPeriodo,
                secondLogoPresenze = secondLogoPresenze,
                secondLogoGol = secondLogoGol,
                giovanili = giovanili,
                secondLogoEff = secondLogoEff,
                effMedio = effMedio,
                minutiCarriera = minutiCarriera,
                minutiNazionale = minutiNazionale,
                college = college,
            )
        )
    }

    /**
     * Basket, giocatore sconosciuto sia a TheSportsDB sia a Wikipedia (tipico delle serie minori,
     * es. Serie B/C italiana): se l'utente ha incollato l'URL Proballers, la pagina Proballers da
     * sola basta per statistiche, club, competizione, nazione, ruolo e anno di nascita. Il nome
     * resta quello scritto dall'utente. Senza URL (o con URL non leggibile): NotFound come prima.
     */
    private suspend fun lookupViaProballersOnly(
        cleanName: String,
        sport: Sport,
        existingId: String?,
        extraUrl: String?,
        proballers: ProballersTotals?,
        yearHint: Int?,
    ): LookupResult {
        if (sport != Sport.BASKET) return LookupResult.NotFound(cleanName)
        val it = proballers ?: return LookupResult.NotFound(cleanName)

        val club = it.squadraPrincipale.orEmpty()
        val teamInfo = if (club.isNotBlank()) fetchTeamInfo(club, sport) else null
        return LookupResult.Found(
            PlayerImportRow(
                id = existingId,
                nome = cleanName,
                anno = it.annoNascita ?: yearHint ?: 0,
                carrieraMigliore = club,
                stato = "",
                nazione = it.nazione.orEmpty(),
                logoPath = teamInfo?.badge ?: it.squadraPrincipaleLogoUrl,
                ruolo = it.posizione?.let { pos -> translateRole(pos, sport) }.orEmpty(),
                presenze = it.presenze,
                punteggio = it.punteggio,
                assist = it.assist,
                competizione = it.competizione.ifBlank { teamInfo?.league.orEmpty() },
                rimbalzi = it.rimbalzi,
                presenzeNazionale = it.presenzeNazionale,
                punteggioNazionale = it.punteggioNazionale,
                proballersUrl = extraUrl,
                secondLogoClub = it.bestEffTeam.orEmpty(),
                secondLogoPath = it.bestEffLogoUrl,
                secondLogoPeriodo = it.bestEffStagione.orEmpty(),
                secondLogoEff = it.bestEffValue ?: 0,
                effMedio = it.effMedio,
                minutiCarriera = it.minutiCarriera,
                minutiNazionale = it.minutiNazionale,
            )
        )
    }

    /** Tutti i dati calcio ricavati da Wikipedia (it, poi en come ripiego) e statmuse. */
    private data class CalcioData(
        val displayName: String? = null,
        val enTitle: String? = null,
        val club: String = "",
        val teamInfo: TeamInfo? = null,
        val presenze: Int = 0,
        val punteggio: Int = 0,
        val golSubiti: Int = 0,
        val assist: Int = 0,
        val tackle: Int = 0,
        val golEvitati: Int = 0,
        val ruolo: String? = null,
        val annoNascita: Int? = null,
        val nazione: String? = null,
        val presenzeNazionale: Int = 0,
        val punteggioNazionale: Int = 0,
        val golSubitiNazionale: Int = 0,
        val secondLogoClub: String = "",
        val secondLogoPath: String? = null,
        val secondLogoPeriodo: String = "",
        val secondLogoPresenze: Int = 0,
        val secondLogoGol: Int = 0,
        val giovanili: String? = null,
    )

    /**
     * Wikipedia in italiano è la fonte primaria per il calcio (dati più completi e ruolo già in
     * lingua): vedi [WikipediaItCareerStats] per gol subiti per spell (anche per i portieri, dove
     * l'infobox EN riporta sempre 0), giovanili, Nazionale maggiore già isolata per categoria. La
     * voce inglese (quella collegata alla voce italiana, non una ricerca per nome che può finire su
     * un omonimo) serve solo per i campi ancora mancanti; statmuse per assist/tackle/intercettazioni,
     * che Wikipedia non traccia.
     *
     * Unica implementazione per entrambi i percorsi di ricerca ([lookup] e
     * [lookupViaWikipediaOnly]): prima era duplicata, e le correzioni finivano in uno solo dei due.
     */
    private suspend fun fetchCalcio(
        name: String,
        yearHint: Int?,
        teamHint: String?,
        itPrefetch: ItCareerData?,
        enTitleHint: String?,
    ): CalcioData {
        val itData = itPrefetch ?: WikipediaItCareerStats.fetch(name, yearHint, teamHint = teamHint)
        var data = CalcioData()

        // Nome con i diacritici della voce italiana, solo se è lo stesso nome scritto in modo
        // diverso (TheSportsDB: "Mario Mandžukic", it.wikipedia: "Mario Mandžukić").
        val itNome = itData?.nome?.takeIf { normalizeForMatch(it) == normalizeForMatch(name) }
        data = data.copy(displayName = itNome)

        if (itData != null) {
            data = data.copy(
                giovanili = encodeYouthClubs(itData.youthClubs),
                ruolo = itData.ruolo,
                annoNascita = itData.annoNascita,
                nazione = itData.nazione,
            )
            if (itData.spells.isNotEmpty()) {
                // "Carriera migliore" = il club con più presenze totali (sommando eventuali spell
                // ripetuti allo stesso club, es. Atalanta 2020-2021 poi 2023- per Carnesecchi).
                val bestClub = itData.spells.groupBy { it.club }
                    .mapValues { (_, spells) -> spells.sumOf { it.presenze } }
                    .maxByOrNull { it.value }?.key
                val totalGol = itData.spells.sumOf { it.gol }
                data = data.copy(
                    club = bestClub.orEmpty(),
                    teamInfo = bestClub?.takeIf { it.isNotBlank() }?.let { fetchTeamInfo(it, Sport.CALCIO) },
                    presenze = itData.spells.sumOf { it.presenze },
                    punteggio = if (itData.spells.first().golSubiti) 0 else totalGol,
                    golSubiti = if (itData.spells.first().golSubiti) totalGol else 0,
                )
            }
            BestSpellPicker.pickBest(itData.spells)?.let { win ->
                data = data.copy(
                    secondLogoClub = win.club,
                    secondLogoPeriodo = win.anni,
                    secondLogoPresenze = win.presenze,
                    secondLogoGol = win.gol,
                    // Stesso club della carriera migliore: stesso stemma, nessuna seconda ricerca.
                    secondLogoPath = if (win.club == data.club) data.teamInfo?.badge else fetchTeamInfo(win.club, Sport.CALCIO)?.badge,
                )
            }
            if (itData.nazionalePresenze != null) {
                val gol = itData.nazionaleGol ?: 0
                data = data.copy(
                    presenzeNazionale = itData.nazionalePresenze,
                    punteggioNazionale = if (itData.nazionaleGolSubiti) 0 else gol,
                    golSubitiNazionale = if (itData.nazionaleGolSubiti) gol else 0,
                )
            }
        }

        val enTitle = enTitleHint ?: itData?.title?.let { WikipediaItCareerStats.linkedTitle("it", it, "en") }
        data = data.copy(enTitle = enTitle)

        // en.wikipedia solo per i campi ancora mancanti, Nazionale compresa (prima la Nazionale
        // dell'inglese non veniva mai usata: chi non aveva il blocco Nazionali sulla voce italiana
        // restava a 0 presenze anche con la Nazionale ben compilata sulla voce inglese).
        // Un blocco Nazionali presente ma con sole giovanili vuol dire 0 presenze in Nazionale
        // maggiore (es. Thomas Kristensen, solo Under): non va "completato" dall'inglese.
        val missingNazionale = itData == null || !itData.hasNazionaliBlock
        if (data.club.isBlank() || data.presenze == 0 || data.ruolo.isNullOrBlank() || missingNazionale) {
            WikipediaCareerStats.fetchClubCareerTotals(enTitle ?: name)
                // Senza voce collegata la ricerca è per nome: un anno di nascita diverso vuol dire
                // un omonimo, i suoi dati non vanno usati.
                ?.takeIf { enTitle != null || yearCompatible(it.annoNascita, yearHint ?: data.annoNascita) }
                ?.let { en ->
                    if (data.presenze == 0) data = data.copy(presenze = en.presenze)
                    if (data.punteggio == 0 && data.golSubiti == 0) data = data.copy(punteggio = en.punteggio)
                    if (data.club.isBlank() && !en.club.isNullOrBlank()) {
                        data = data.copy(club = en.club, teamInfo = fetchTeamInfo(en.club, Sport.CALCIO))
                    }
                    if (data.ruolo.isNullOrBlank() && !en.posizione.isNullOrBlank()) {
                        data = data.copy(ruolo = translateRole(en.posizione, Sport.CALCIO))
                    }
                    if (data.annoNascita == null) data = data.copy(annoNascita = en.annoNascita)
                    if (data.nazione.isNullOrBlank() && !en.nazione.isNullOrBlank()) data = data.copy(nazione = en.nazione)
                    if (missingNazionale && en.presenzeNazionale > 0) {
                        data = data.copy(presenzeNazionale = en.presenzeNazionale, punteggioNazionale = en.punteggioNazionale)
                    }
                }
        }

        // statmuse: scartato se le sue stagioni non hanno nessun club in comune con la carriera
        // Wikipedia (omonimo, vedi StatmuseCalcioTotals.matchesCareer). Gol subiti solo per i
        // portieri: per gli altri ruoli quella colonna non riguarda il giocatore.
        val careerClubs = itData?.spells?.map { it.club }.orEmpty()
        StatmuseCalcioStats.fetchCareerTotals(data.displayName ?: name)
            ?.takeIf { it.matchesCareer(careerClubs) }
            ?.let { st ->
                data = data.copy(assist = st.assist, tackle = st.tackle, golEvitati = st.golEvitati)
                if (data.golSubiti == 0 && isPortiere(data.ruolo.orEmpty())) data = data.copy(golSubiti = st.golSubiti)
                if (data.presenze == 0) data = data.copy(presenze = st.presenze)
            }
        return data
    }

    private data class WikipediaLink(val lang: String, val title: String)

    private val wikipediaUrlRegex = Regex("""\b(it|en)\.(?:m\.)?wikipedia\.org/wiki/([^?#\s]+)""", RegexOption.IGNORE_CASE)

    /** "https://it.wikipedia.org/wiki/Thomas_Kristensen_(calciatore_2002)" -> (it, "Thomas Kristensen (calciatore 2002)"). */
    private fun parseWikipediaUrl(url: String?): WikipediaLink? {
        val m = wikipediaUrlRegex.find(url ?: return null) ?: return null
        val title = runCatching { java.net.URLDecoder.decode(m.groupValues[2], "UTF-8") }.getOrNull() ?: return null
        return WikipediaLink(m.groupValues[1].lowercase(), title.replace('_', ' '))
    }

    /** URL da salvare sul giocatore per rifare la stessa ricerca in "Revisione": Proballers per il
     *  basket, la voce Wikipedia incollata per il calcio (colonna [com.scouttable.app.data.Player.proballersUrl]). */
    private fun sourceUrlToKeep(sport: Sport, extraUrl: String?): String? =
        if (sport == Sport.BASKET) extraUrl?.takeIf { it.contains("proballers", ignoreCase = true) }
        else extraUrl?.takeIf { parseWikipediaUrl(it) != null }

    // Caratteri invisibili che TheSportsDB lascia in alcuni nomi (verificato su Mario Mandžukić:
    // strPlayer inizia con un BOM, U+FEFF): restano attaccati al nome e fanno fallire ogni ricerca
    // successiva per titolo su Wikipedia/statmuse.
    private val invisibleCharsRegex = Regex("""[\uFEFF\u200B-\u200D\u2060]""")

    /** Testo di TheSportsDB pronto all'uso: entità HTML decodificate, caratteri invisibili tolti. */
    private fun cleanApiText(text: String): String =
        decodeHtmlEntities(text).replace(invisibleCharsRegex, "").replace('\u00A0', ' ').trim()

    /** Un anno sconosciuto (da una delle due parti) non smentisce l'identità: solo due anni noti e diversi. */
    private fun yearCompatible(found: Int?, expected: Int?): Boolean =
        expected == null || found == null || found == expected

    /**
     * Statistiche Wikipedia di un cestista, scartando un omonimo nato in un anno diverso da
     * [expectedYear]: in quel caso si tentano le voci "<nome> Jr." / "<nome> Sr.", il modo in cui
     * en.wikipedia distingue padre e figlio omonimi (verificato su Derrick Alston: "Derrick Alston"
     * è il padre, 1972; il figlio, 1997, è "Derrick Alston Jr."). Restituisce anche il titolo
     * effettivamente usato, da mostrare come nome del giocatore.
     */
    private suspend fun fetchBasketballWiki(name: String, expectedYear: Int?): Pair<String, BasketballCareerTotals>? {
        // La voce col nome esatto può essere di un altro sportivo omonimo (es. "Reggie Jackson" è
        // il giocatore di baseball, nessun infobox da cestista): in quel caso, invece di
        // arrendersi, si provano le convenzioni di disambiguazione di en.wikipedia per i cestisti.
        val direct = WikipediaBasketballStats.fetch(name)
        if (direct != null && yearCompatible(direct.annoNascita, expectedYear)) return name to direct
        if (Regex("""\b(Jr|Sr)\.?$""", RegexOption.IGNORE_CASE).containsMatchIn(name)) return null
        val variants = buildList {
            if (expectedYear != null) add("$name (basketball, born $expectedYear)")
            add("$name (basketball)")
            if (direct != null) { add("$name Jr."); add("$name Sr.") }
        }
        for (title in variants) {
            val stats = WikipediaBasketballStats.fetch(title) ?: continue
            if (yearCompatible(stats.annoNascita, expectedYear)) return title to stats
        }
        return null
    }

    /** "Francesco Totti 1976" -> ("Francesco Totti", 1976). Nessun numero finale -> anno null. */
    private fun splitTrailingYear(raw: String): Pair<String, Int?> {
        val trimmed = raw.trim()
        val match = Regex("""^(.*\S)\s+((?:18|19|20)\d{2})$""").find(trimmed) ?: return trimmed to null
        return match.groupValues[1].trim() to match.groupValues[2].toInt()
    }

    private data class TeamInfo(val badge: String?, val league: String)

    // Alcuni nomi di club/città su it.wikipedia.org sono esonimi italiani che TheSportsDB non
    // riconosce (indicizza i club col nome nella lingua originale/inglese): verificato su Mitchel
    // Bakker ("→ Lilla", TheSportsDB conosce solo "Lille") e Odilon Kossounou ("Club Bruges",
    // TheSportsDB conosce solo "Club Brugge"). Elenco non esaustivo, solo gli esonimi più comuni
    // per città con un unico club di massimo livello riconoscibile senza ambiguità.
    private val italianClubExonyms = mapOf(
        "Bruges" to "Brugge",
        "Lilla" to "Lille",
        "Siviglia" to "Sevilla",
        "Marsiglia" to "Marseille",
        "Saragozza" to "Zaragoza",
        "Norimberga" to "Nurnberg",
        "Amburgo" to "Hamburg",
        "Stoccarda" to "Stuttgart",
        "Lipsia" to "Leipzig",
        "Zurigo" to "Zurich",
        "Basilea" to "Basel",
    )

    private fun translateItalianExonym(teamName: String): String? {
        for ((exonym, original) in italianClubExonyms) {
            val regex = Regex("""\b${Regex.escape(exonym)}\b""")
            if (regex.containsMatchIn(teamName)) return regex.replace(teamName, original)
        }
        return null
    }

    // Il logo del club è il campo su cui l'utente ha insistito di più: un retry silenzioso
    // assorbe i blip temporanei della chiave di test gratuita di TheSportsDB (rate limit basso).
    // Se il nome contiene un trattino (es. "Paris Saint-Germain") si ritenta anche senza: la
    // ricerca squadre di TheSportsDB non trova nulla con l'esatto trattino su diversi club noti,
    // pur avendo l'esatto badge se interrogata con uno spazio al suo posto ("Paris Saint Germain").
    // Come ultima risorsa, se il nome contiene un esonimo italiano noto (vedi sopra), si ritenta
    // con l'equivalente in lingua originale prima di arrendersi e mostrare l'avatar generico.
    // Per il calcio, ultima risorsa: lo stemma dalla voce it.wikipedia del club, che risolve da
    // sola i nomi italiani (es. "Bayern Monaco" -> "FC Bayern München", sconosciuto a
    // TheSportsDB). Senza questo ripiego al posto dello stemma finiva la foto del giocatore.
    private fun fetchTeamInfo(teamName: String, sport: Sport): TeamInfo? =
        fetchTeamInfoOnce(teamName, sport)
            ?: fetchTeamInfoOnce(teamName, sport)
            ?: teamName.takeIf { it.contains('-') }?.replace('-', ' ')?.let { fetchTeamInfoOnce(it, sport) }
            ?: translateItalianExonym(teamName)?.let { fetchTeamInfoOnce(it, sport) }
            ?: if (sport == Sport.CALCIO) WikipediaClubLogo.fetch(teamName)?.let { TeamInfo(it, "") } else null

    // Varianti di una squadra che non sono la prima squadra: TheSportsDB le restituisce anche per
    // la ricerca del nome nudo (verificato: "Milan" -> "Milan Youth", stemma sbagliato).
    private val secondaryTeamRegex = Regex("""\b(youth|u-?\d{2}|under[ -]?\d{2}|women|woman|ladies|femminile|feminino|reserves?|ii|b team|primavera|academy)\b""", RegexOption.IGNORE_CASE)

    private fun fetchTeamInfoOnce(teamName: String, sport: Sport): TeamInfo? = runCatching {
        val encoded = URLEncoder.encode(teamName, "UTF-8")
        val json = getJson("$BASE/searchteams.php?t=$encoded")
        val teams = json?.optJSONArray("teams") ?: return@runCatching null
        val wantedSport = if (sport == Sport.BASKET) "Basketball" else "Soccer"
        val queryIsSecondary = secondaryTeamRegex.containsMatchIn(teamName)
        val candidates = (0 until teams.length()).map { teams.getJSONObject(it) }.filter { team ->
            val teamSport = team.optString("strSport")
            (teamSport.isBlank() || teamSport.equals(wantedSport, ignoreCase = true)) &&
                (queryIsSecondary || !secondaryTeamRegex.containsMatchIn(team.optString("strTeam")))
        }
        // A parità, il nome identico (o tra i nomi alternativi) batte un risultato solo simile.
        val query = normalizeForMatch(teamName)
        val team = candidates.firstOrNull { team ->
            normalizeForMatch(cleanApiText(team.optString("strTeam"))) == query ||
                cleanApiText(team.optString("strTeamAlternate")).split(',').any { normalizeForMatch(it) == query }
        } ?: candidates.firstOrNull() ?: return@runCatching null
        val badge = team.optString("strBadge").ifBlank { null } ?: team.optString("strLogo").ifBlank { null }
            ?: return@runCatching null
        TeamInfo(badge, cleanApiText(team.optString("strLeague")))
    }.getOrNull()

    private val htmlEntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|quot|apos|lt|gt|nbsp);""")

    /** "Samuel Eto&#039;o" -> "Samuel Eto'o": entità HTML nei testi JSON di TheSportsDB. */
    private fun decodeHtmlEntities(text: String): String =
        htmlEntityRegex.replace(text) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                "lt" -> "<"
                "gt" -> ">"
                "nbsp" -> " "
                else -> {
                    val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                    code?.let { String(Character.toChars(it)) } ?: m.value
                }
            }
        }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()
            if (body.isNullOrBlank() || body == "null") return null
            return JSONObject(body)
        }
    }

    // Uno svincolato resta "Svincolato" anche nel basket (prima diventava "Attivo" per restare
    // nella revisione mensile: ora la revisione include anche gli svincolati, vedi
    // PlayerDao.flagActiveForReview, quindi non serve più falsare lo stato).
    private fun mapStatus(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("retire") -> PlayerStatus.RITIRATO
            lower.contains("deceas") -> PlayerStatus.RITIRATO
            lower.contains("injur") -> PlayerStatus.INFORTUNATO
            lower.contains("free") -> PlayerStatus.SVINCOLATO
            else -> PlayerStatus.ATTIVO
        }
    }
}

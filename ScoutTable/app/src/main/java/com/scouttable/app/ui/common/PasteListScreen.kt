// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.looksLikePlayerPhoto
import com.scouttable.app.ui.review.ChangesTable
import com.scouttable.app.ui.review.FieldChange
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

/** Riga incollata già scomposta: nome da cercare (con eventuale anno in coda) e URL della fonte
 *  (Proballers per il basket, la voce Wikipedia per il calcio). */
private data class ParsedLine(val name: String, val sourceUrl: String?)

/**
 * Ogni riga è un URL: Wikipedia per il calcio, Proballers per il basket. Nome (e, se presente
 * nella disambiguazione, anno di nascita) si ricavano dall'URL stesso, così non vanno riscritti a
 * mano. Una riga che non è un URL viene ancora trattata come "Nome Cognome [Anno]".
 */
private fun parseLine(line: String): ParsedLine {
    val wiki = Regex("""wikipedia\.org/wiki/([^?#\s]+)""", RegexOption.IGNORE_CASE).find(line)
    if (wiki != null) {
        // es. "Francesco_Totti" o "Marco_Rossi_(calciatore_1987)" -> "Marco Rossi 1987"
        val title = URLDecoder.decode(wiki.groupValues[1], "UTF-8").replace('_', ' ')
        val year = Regex("""\(([^)]*)\)""").find(title)?.groupValues?.get(1)
            ?.let { Regex("""(?:18|19|20)\d{2}""").find(it)?.value }
        val name = title.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        // L'URL va passato anche alla ricerca: identifica la voce esatta. Con il solo "Nome Anno"
        // la ricerca poteva finire su un omonimo o su una disambigua (es. "Thomas Kristensen
        // (calciatore 2002)" letto come il Kristensen del 1983).
        return ParsedLine(if (year != null) "$name $year" else name, line.trim())
    }
    val proballers = Regex("""proballers\.com/(?:[a-z]{2}/)?[^/]+/[^/]+/\d+/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
        .find(line)
    if (proballers != null) {
        // es. ".../player/2765/michael-jordan" -> "Michael Jordan"
        val name = proballers.groupValues[1].split('-').filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        return ParsedLine(name, line.trim())
    }
    val fbref = Regex("""fbref\.com/(?:[a-z]{2}/)?players/[0-9a-f]{8}/([^/?#\s]+)""", RegexOption.IGNORE_CASE).find(line)
    if (fbref != null) {
        // es. ".../players/ed75b6c7/Francesco-Totti" -> "Francesco Totti"
        val name = URLDecoder.decode(fbref.groupValues[1], "UTF-8").split('-').filter { it.isNotBlank() }
            .joinToString(" ")
        return ParsedLine(name, line.trim())
    }
    if (line.contains("playbasket.it", ignoreCase = true)) {
        // Nessun nome nell'URL (solo identificativi): il nome arriva dalla scheda stessa.
        val obj = Regex("""[?&]obj=(\d+)""").find(line)?.groupValues?.get(1)
        return ParsedLine(if (obj != null) "Giocatore playbasket $obj" else "Giocatore playbasket", line.trim())
    }
    val realGm = Regex("""realgm\.com/(?:[a-z]+/)?player/([^/?#\s]+)/""", RegexOption.IGNORE_CASE).find(line)
    if (realGm != null) {
        // es. ".../player/Andrea-Meneghin/Summary/3051" -> "Andrea Meneghin"
        val name = URLDecoder.decode(realGm.groupValues[1], "UTF-8").split('-').filter { it.isNotBlank() }
            .joinToString(" ")
        return ParsedLine(name, line.trim())
    }
    val parts = line.split("|").map { it.trim() }
    return ParsedLine(parts[0], parts.getOrNull(1)?.ifBlank { null })
}

/** Da quale sito arrivano i link incollati, per sport: la prima fonte di ogni sport è quella
 *  consigliata, le altre sono piani B. Cambia solo le indicazioni a schermo: ogni riga viene
 *  comunque riconosciuta dal suo URL (vedi [parseLine]), quindi una lista può mescolare le fonti. */
private enum class LinkSource(
    val sport: Sport,
    val label: String,
    val description: String,
    val fieldLabel: String,
    val example: String,
) {
    WIKIPEDIA_CALCIO(
        Sport.CALCIO, "Wikipedia (italiano o inglese)",
        "Consigliato: club e presenze per ogni periodo, giovanili, Nazionale.",
        "Un link Wikipedia per riga", "https://it.wikipedia.org/wiki/Francesco_Totti",
    ),
    FBREF(
        Sport.CALCIO, "FBref",
        "Alternativa: presenze, gol, assist e minuti di campionato stagione per stagione; completa i dati di Wikipedia.",
        "Un link FBref per riga", "https://fbref.com/en/players/ed75b6c7/Francesco-Totti",
    ),
    PROBALLERS(
        Sport.BASKET, "Proballers",
        "Consigliato: statistiche stagione per stagione, Eff, minuti e secondo logo.",
        "Un link Proballers per riga", "https://www.proballers.com/basketball/player/2765/michael-jordan",
    ),
    WIKIPEDIA_BASKET(
        Sport.BASKET, "Wikipedia (italiano o inglese)",
        "Piano B se il giocatore non è su Proballers: totali di carriera da Wikipedia inglese e italiana.",
        "Un link Wikipedia per riga", "https://it.wikipedia.org/wiki/Gianmarco_Pozzecco",
    ),
    REALGM(
        Sport.BASKET, "RealGM",
        "Terza opzione: totali stagione per stagione, palle recuperate e percentuali al tiro, ma spesso senza le stagioni più vecchie.",
        "Un link RealGM per riga", "https://basketball.realgm.com/player/Andrea-Meneghin/Summary/3051",
    ),
    PLAYBASKET(
        Sport.BASKET_REGIONALE, "playbasket.it",
        "Leghe minori italiane: squadra, campionato, ruolo, età, punti e presenze di tutte le stagioni registrate.",
        "Un link playbasket per riga",
        "https://www.playbasket.it/campania/profile.php?season=2027&subj=1&exteid=4&extobj=14632&obj=204567&action=view&eid=5",
    ),
    ;

    companion object {
        fun forSport(sport: Sport) = entries.filter { it.sport == sport }
    }
}

/**
 * Schermata base per "Genera nuova lista" e "Aggiorna nuova lista": l'utente incolla una lista
 * di nomi (uno per riga), l'app cerca ogni giocatore su internet (TheSportsDB) e recupera anno,
 * carriera migliore, stato, nazione e stemma del club. Nessun file da preparare.
 */
@Composable
fun PasteListScreen(
    sport: Sport,
    padding: PaddingValues,
    title: String,
    description: String,
    buttonLabel: String,
    onFound: suspend (List<PlayerImportRow>) -> Unit,
    /** Giocatore già in lista corrispondente a una riga trovata (null se nuovo): per quelli già
     *  presenti si chiede conferma prima di aggiornarli. */
    findExisting: suspend (PlayerImportRow) -> Player? = { null },
    /** Dati che cambierebbero aggiornando un giocatore già presente (vedi PlayerRepository.previewUpdate),
     *  mostrati nella conferma "esiste già". */
    previewChanges: suspend (PlayerImportRow) -> List<FieldChange> = { emptyList() },
    /** Stemma che verrebbe salvato per la riga (trovato in rete o tra i giocatori già salvati),
     *  null se nessuno: in quel caso, prima di salvare, si chiede all'utente di caricarlo. Se
     *  non indicato, nessuna richiesta. */
    resolvedLogo: (suspend (PlayerImportRow) -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Stemma mancante in attesa di upload: giocatori interessati (stesso club) e risposta attesa
    // dal ciclo di salvataggio (percorso dell'immagine caricata, o null = salta).
    var pendingLogo by remember { mutableStateOf<MissingLogo?>(null) }
    var logoAnswer by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var uploadedLogo by remember { mutableStateOf<String?>(null) }
    var skipAllLogos by remember { mutableStateOf(false) }
    val pickLogoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { copyImageToInternalStorage(context, uri)?.let { uploadedLogo = it } }
    }

    logoAnswer?.let { answer ->
        pendingLogo?.let { missing ->
            MissingLogoDialog(
                missing = missing,
                uploaded = uploadedLogo,
                onPick = {
                    pickLogoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSkipAll = {
                    skipAllLogos = true
                    logoAnswer = null
                    pendingLogo = null
                    uploadedLogo = null
                    answer.complete(null)
                },
                onDone = { path ->
                    logoAnswer = null
                    pendingLogo = null
                    uploadedLogo = null
                    answer.complete(path)
                },
            )
        }
    }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }
    var progressDetail by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var linkSource by rememberSaveable { mutableStateOf(LinkSource.forSport(sport).first()) }
    var showSourceDialog by remember { mutableStateOf(false) }
    // Giocatori già presenti in attesa della scelta "aggiornare sì/no" e la risposta attesa dal
    // ciclo di ricerca (indici in [pendingExisting] da aggiornare).
    var pendingExisting by remember { mutableStateOf<List<ExistingMatch>>(emptyList()) }
    var existingAnswer by remember { mutableStateOf<CompletableDeferred<Set<Int>>?>(null) }

    existingAnswer?.let { answer ->
        ExistingPlayersDialog(
            existing = pendingExisting,
            onConfirm = { selected ->
                existingAnswer = null
                answer.complete(selected)
            },
        )
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Fonte dei link (${sport.label})") },
            text = {
                Column {
                    LinkSource.forSport(sport).forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { linkSource = option; showSourceDialog = false }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = linkSource == option, onClick = { linkSource = option; showSourceDialog = false })
                            Column {
                                Text(option.label)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSourceDialog = false }) { Text("Chiudi") } },
        )
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        OutlinedButton(
            onClick = { showSourceDialog = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Text("Fonte link: ${linkSource.label}")
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(linkSource.fieldLabel) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Text(
            "Incolla l'URL della pagina ${linkSource.label.substringBefore(" (")} di ogni giocatore, es: ${linkSource.example}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Button(
            enabled = !busy && text.isNotBlank(),
            onClick = {
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                busy = true
                statusMessage = null
                scope.launch {
                    val found = mutableListOf<PlayerImportRow>()
                    val notFound = mutableListOf<String>()
                    val errors = mutableListOf<String>()
                    val existingFound = mutableListOf<ExistingMatch>()
                    val startedAt = System.currentTimeMillis()
                    progressFraction = 0f
                    progressDetail = null
                    lines.forEachIndexed { index, line ->
                        val (name, sourceUrl) = parseLine(line)
                        progress = "Ricerca ${index + 1}/${lines.size}: $name"
                        // FBref si usa solo se scelto apposta come fonte: con Wikipedia selezionata
                        // un link FBref non deve cambiare silenziosamente la ricerca.
                        if (sourceUrl?.contains("fbref.com", ignoreCase = true) == true && linkSource != LinkSource.FBREF) {
                            errors.add("$name (link FBref: scegli \"FBref\" come fonte link)")
                            progressFraction = (index + 1f) / lines.size
                            return@forEachIndexed
                        }
                        // Avanzamento reale anche dentro il singolo giocatore (una ricerca può
                        // durare parecchi secondi): prima la barra si muoveva solo a giocatore
                        // concluso e restava a 0% per tutto il primo, o per sempre con un solo link.
                        // maxOf: un nuovo tentativo dopo un errore non fa tornare indietro la barra.
                        var playerFraction = 0f
                        val result = PlayerLookupService.lookupWithRetry(name, sport, extraUrl = sourceUrl) { step ->
                            playerFraction = maxOf(playerFraction, step)
                            val overall = (index + playerFraction) / lines.size
                            // Il callback arriva dal thread di rete: lo stato UI si aggiorna sul main.
                            scope.launch { progressFraction = maxOf(progressFraction, overall) }
                        }
                        when (result) {
                            is PlayerLookupService.LookupResult.Found -> {
                                found.add(result.row)
                                // Segnalato subito, mentre la ricerca prosegue: la conferma
                                // vera e propria arriva alla fine, per tutti insieme.
                                findExisting(result.row)?.let { existing ->
                                    existingFound.add(ExistingMatch(result.row, existing, previewChanges(result.row)))
                                    progress = "Ricerca ${index + 1}/${lines.size}: $name — già presente in lista"
                                }
                            }
                            is PlayerLookupService.LookupResult.NotFound -> notFound.add(name)
                            // Distinto da "non trovato": qui è successo un errore vero (rete,
                            // parsing...) — mostrare il messaggio aiuta a capire la causa reale
                            // invece di confonderlo con un nome semplicemente non riconosciuto.
                            is PlayerLookupService.LookupResult.Error -> errors.add("$name (${result.message})")
                        }
                        // Piccola pausa tra un giocatore e l'altro: le API gratuite usate per la
                        // ricerca (TheSportsDB con chiave di test condivisa, Wikipedia,
                        // basketball-reference.com) possono limitare o bloccare temporaneamente
                        // raffiche di richieste ravvicinate, facendo apparire dati mancanti/
                        // incompleti (loghi, giovanili, secondo logo) su intere liste che invece
                        // funzionerebbero singolarmente. Per il calcio ogni giocatore fa ormai il
                        // doppio delle richieste di prima (Wikipedia EN+IT, due ricerche stemma
                        // TheSportsDB per i due loghi): 400ms non bastava più oltre le 5-6 righe,
                        // portato a 900ms.
                        if (index < lines.lastIndex) delay(900)
                        // Percentuale sui giocatori già elaborati; il tempo rimanente è stimato
                        // dalla media reale dei giocatori finora (pausa di 900ms compresa).
                        val done = index + 1
                        progressFraction = done.toFloat() / lines.size
                        val remainingMillis = (System.currentTimeMillis() - startedAt) / done * (lines.size - done)
                        progressDetail = if (done < lines.size) "Tempo rimanente stimato: ${formatRemaining(remainingMillis)}" else null
                    }
                    // Già presenti: si aggiornano solo quelli confermati dall'utente.
                    val updated = mutableListOf<String>()
                    val skipped = mutableListOf<String>()
                    if (existingFound.isNotEmpty()) {
                        progress = "In attesa di conferma per i giocatori già presenti…"
                        val answer = CompletableDeferred<Set<Int>>()
                        pendingExisting = existingFound.toList()
                        existingAnswer = answer
                        val selected = answer.await()
                        existingFound.forEachIndexed { i, (row, existing, _) ->
                            if (i in selected) updated += existing.nome
                            else {
                                skipped += existing.nome
                                found.remove(row)
                            }
                        }
                    }
                    // Stemma non trovato né in rete né tra i giocatori già salvati: si chiede
                    // all'utente di caricarlo prima di salvare, una volta per club (vale per tutti
                    // i giocatori della lista con lo stesso club). "Salta" salva senza stemma.
                    if (resolvedLogo != null) {
                        val missing = found.filter { row ->
                            resolvedLogo(row).let { it.isNullOrBlank() || looksLikePlayerPhoto(it) }
                        }
                        val groups = missing.groupBy { it.carrieraMigliore.trim().lowercase().ifBlank { "#" + it.nome } }
                        var asked = 0
                        skipAllLogos = false
                        for ((_, rows) in groups) {
                            if (skipAllLogos) break
                            asked++
                            progress = "Stemma mancante ($asked/${groups.size}): in attesa del caricamento…"
                            val answer = CompletableDeferred<String?>()
                            pendingLogo = MissingLogo(rows.first().carrieraMigliore, rows.map { it.nome }, asked, groups.size)
                            uploadedLogo = null
                            logoAnswer = answer
                            val path = answer.await() ?: continue
                            rows.forEach { row ->
                                val i = found.indexOf(row)
                                if (i >= 0) found[i] = row.copy(logoPath = path)
                            }
                        }
                    }
                    progress = "Salvataggio in corso…"
                    onFound(found)
                    busy = false
                    progress = ""
                    progressDetail = null
                    statusMessage = buildString {
                        val newCount = found.size - updated.size
                        append("Trovati ${found.size + skipped.size} giocatori su ${lines.size}: $newCount nuovi salvati.")
                        if (updated.isNotEmpty()) append("\nGià presenti, aggiornati: ${updated.joinToString(", ")}")
                        if (skipped.isNotEmpty()) append("\nGià presenti, NON aggiornati: ${skipped.joinToString(", ")}")
                        if (notFound.isNotEmpty()) {
                            append("\nNon trovati (nome non riconosciuto o sport diverso): ")
                            append(notFound.joinToString(", "))
                        }
                        if (errors.isNotEmpty()) {
                            append("\nErrori: ")
                            append(errors.joinToString(", "))
                        }
                        incompleteDataNote(found, sport)?.let { append("\n\n$it") }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(buttonLabel)
        }

        if (busy) {
            LoadingBar(
                label = progress,
                progress = progressFraction,
                detail = progressDetail ?: if (progressFraction == 0f) "Calcolo del tempo rimanente…" else null,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        statusMessage?.let {
            Text(it, modifier = Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Club il cui stemma non è stato trovato, con i giocatori della lista che lo usano. */
private data class MissingLogo(val club: String, val players: List<String>, val index: Int, val total: Int)

/**
 * Richiesta dello stemma mancante prima di salvare: anteprima dell'immagine scelta, "Salva con
 * questo stemma" (attivo solo dopo averla scelta), "Salta" per questo club e "Salta tutti".
 */
@Composable
private fun MissingLogoDialog(
    missing: MissingLogo,
    uploaded: String?,
    onPick: () -> Unit,
    onSkipAll: () -> Unit,
    onDone: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = {
            Text(
                (if (missing.club.isNotBlank()) "Stemma di ${missing.club} non trovato" else "Stemma non trovato") +
                    if (missing.total > 1) " (${missing.index}/${missing.total})" else "",
            )
        },
        text = {
            Column {
                Text(
                    "Non ho trovato lo stemma né tra i giocatori salvati né in rete per: " +
                        missing.players.joinToString(", ") + ". Caricalo dal dispositivo prima di salvare.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    PlayerAvatar(name = missing.club.ifBlank { missing.players.first() }, logoPath = uploaded, size = 56.dp)
                    OutlinedButton(onClick = onPick, modifier = Modifier.padding(start = 16.dp)) {
                        Text(if (uploaded == null) "Carica stemma" else "Cambia immagine")
                    }
                }
                if (missing.total > 1) {
                    TextButton(onClick = onSkipAll, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Salta tutti (salva senza stemma)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(uploaded) }, enabled = uploaded != null) { Text("Salva con questo stemma") }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text("Salta") }
        },
    )
}

/** Giocatore trovato che è già in lista: dato nuovo, giocatore salvato e dati che cambierebbero. */
private data class ExistingMatch(val row: PlayerImportRow, val existing: Player, val changes: List<FieldChange>)

/**
 * Conferma per i giocatori trovati che sono già in lista: per ognuno i dati che cambierebbero
 * (prima/dopo, come in Revisione) e la scelta se aggiornarlo (tutti selezionati di partenza).
 * Chiudere senza scegliere equivale a "Non aggiornare": i dati già salvati restano intatti.
 */
@Composable
private fun ExistingPlayersDialog(
    existing: List<ExistingMatch>,
    onConfirm: (Set<Int>) -> Unit,
) {
    var selected by remember(existing) { mutableStateOf(existing.indices.toSet()) }
    val single = existing.size == 1
    AlertDialog(
        onDismissRequest = { onConfirm(emptySet()) },
        title = {
            Text(if (single) "${existing.first().existing.nome} esiste già" else "${existing.size} giocatori esistono già")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (single) "Questo giocatore è già nella tua lista. Vuoi aggiornarlo con i dati appena trovati?"
                    else "Questi giocatori sono già nella tua lista. Seleziona quelli da aggiornare con i dati appena trovati:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                existing.forEachIndexed { i, match ->
                    val player = match.existing
                    if (single) {
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clickable { selected = if (i in selected) selected - i else selected + i },
                        ) {
                            Checkbox(
                                checked = i in selected,
                                onCheckedChange = { checked -> selected = if (checked) selected + i else selected - i },
                            )
                            Text(
                                buildString {
                                    append(player.nome)
                                    if (player.anno != 0) append(" (${player.anno})")
                                    if (player.carrieraMigliore.isNotBlank()) append(" · ${player.carrieraMigliore}")
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (match.changes.isEmpty()) {
                        Text(
                            "Nessun dato cambierebbe: i dati trovati sono uguali a quelli salvati.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${match.changes.size} ${if (match.changes.size == 1) "dato cambierebbe" else "dati cambierebbero"}:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        ChangesTable(match.changes)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (single) setOf(0) else selected) }, enabled = single || selected.isNotEmpty()) {
                Text(if (single) "Sì, aggiorna" else "Aggiorna selezionati (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(emptySet()) }) { Text(if (single) "No" else "Non aggiornare nessuno") }
        },
    )
}

private fun formatRemaining(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(1)
    return if (seconds < 60) "circa $seconds s" else "circa ${seconds / 60} min ${seconds % 60} s"
}

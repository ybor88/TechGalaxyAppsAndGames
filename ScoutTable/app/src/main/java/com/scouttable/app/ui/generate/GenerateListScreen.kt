// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.generate

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.PasteListScreen
import com.scouttable.app.ui.review.playerChanges

@Composable
fun GenerateListScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()

    if (sport.isAthlete) {
        val word = com.scouttable.app.data.athlete.AthleteScoring.athleteWord(sport, plural = true)
        PasteListScreen(
            sport = sport,
            padding = padding,
            title = "Genera nuova lista",
            description = "Incolla i link Wikipedia dei $word (uno per riga" +
                (if (sport.isCombat) "; it.wikipedia.org, oppure en.wikipedia.org per chi non ha la voce italiana" else "; it.wikipedia.org") +
                "): l'app legge dalla scheda " +
                when (sport) {
                    Sport.WRESTLING -> "(o da cagematch.net) incontri totali, vittorie, sconfitte, pareggi, anni di attività, sesso, "
                    Sport.PUGILATO, Sport.MMA, Sport.KICKBOXING, Sport.SUMO ->
                        "disciplina, incontri disputati, vittorie e sconfitte (con i KO), pareggi, titoli, categoria, sesso, "
                    Sport.MOTOGP -> "le sole statistiche del Motomondiale (mondiali, gare, vittorie, podi, punti, pole, giri veloci), "
                    Sport.FOOTBALL -> "ruolo, squadre e stagioni, Super Bowl, Pro Bowl, All-Pro, MVP e (dalla voce inglese) yard, " +
                        "touchdown, tackle, sack e intercetti, "
                    Sport.RUGBY -> "ruolo, presenze e punti nei club e in Nazionale, squadra principale, "
                    Sport.HOCKEY -> "ruolo, partite, gol e assist nei club e in Nazionale, titoli, squadra principale, "
                    else -> "le sole statistiche di Formula 1 (mondiali, GP disputati e vinti, podi, punti, pole, giri veloci, scuderia), "
                } +
                "anno di nascita, nazione e foto. Se un atleta è già in lista viene aggiornato, mostrandoti prima cosa cambia.",
            buttonLabel = "Genera lista",
            onFound = { rows -> if (rows.isNotEmpty()) repository.generateList(sport, rows) },
            findExisting = { row -> repository.findExisting(sport, row) },
            previewChanges = { row ->
                val (existing, updated) = repository.previewUpdate(sport, row)
                existing?.let { playerChanges(it, updated, sport) }.orEmpty()
            },
            // Nessuna richiesta di stemma: per gli atleti l'immagine è la foto della voce.
            resolvedLogo = null,
        )
        return
    }
    PasteListScreen(
        sport = sport,
        padding = padding,
        title = "Genera nuova lista",
        description = "Incolla i link dei giocatori (uno per riga: " +
            (if (sport == Sport.BASKET_REGIONALE) "la scheda giocatore di playbasket.it" else "Wikipedia o FBref per il calcio, Proballers, Wikipedia o RealGM per il basket") + "): " +
            "l'app ricava il giocatore dal link " +
            "e recupera automaticamente anno, carriera migliore, stato, nazione e stemma del club. " +
            "I giocatori trovati si aggiungono alla lista di ${sport.label}; se un giocatore è già presente " +
            "(stesso nome e nazione) viene aggiornato invece di duplicarlo.",
        buttonLabel = "Genera lista",
        onFound = { rows -> if (rows.isNotEmpty()) repository.generateList(sport, rows) },
        findExisting = { row -> repository.findExisting(sport, row) },
        previewChanges = { row ->
            val (existing, updated) = repository.previewUpdate(sport, row)
            existing?.let { playerChanges(it, updated, sport) }.orEmpty()
        },
        // Stemma che verrebbe salvato (rete, stemma già salvato o preso da un altro giocatore
        // dello stesso club): se non c'è, la schermata chiede di caricarlo prima di salvare.
        resolvedLogo = { row -> repository.previewUpdate(sport, row).second.logoPath },
    )
}

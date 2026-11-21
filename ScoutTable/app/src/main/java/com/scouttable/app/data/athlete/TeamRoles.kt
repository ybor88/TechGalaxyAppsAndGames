// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import kotlin.math.roundToInt

/**
 * Gruppi di ruolo di Football americano, Rugby e Hockey, ognuno con la sua formula di punteggio
 * (come le classifiche per ruolo del calcio): ogni giocatore ha un punteggio anche se il suo ruolo
 * non segna (un defensive tackle non ha touchdown né intercetti, un pilone quasi mai punti).
 * Colonne usate: vedi la tabella in AthleteStats.kt.
 */
enum class TeamRole(val sport: Sport, val label: String, val formula: String) {
    FB_ATTACCO(
        Sport.FOOTBALL, "Attacco",
        "QB, running back, ricevitori, tight end: touchdown per stagione × 10",
    ),
    FB_DIFESA(
        Sport.FOOTBALL, "Difesa",
        "linea difensiva, linebacker, cornerback, safety: (intercetti + sack + touchdown + tackle/10) per stagione × 10",
    ),
    FB_LINEA(
        Sport.FOOTBALL, "Linea offensiva e special team",
        "tackle, guard, centri, kicker, punter (Wikipedia non ha loro statistiche): (Pro Bowl + All-Pro) per stagione × 100",
    ),
    RU_AVANTI(
        Sport.RUGBY, "Avanti",
        "piloni, tallonatori, seconde e terze linee: presenze totali / 10 + punti per presenza × 100",
    ),
    RU_MEDIANI(Sport.RUGBY, "Mediani", "mediano di mischia e d'apertura: punti per presenza × 100"),
    RU_TREQUARTI(Sport.RUGBY, "Trequarti", "centri, ali, estremi: punti per presenza × 100"),
    HO_ATTACCANTI(Sport.HOCKEY, "Attaccanti", "centri e ali: (gol + assist) per partita × 100"),
    HO_DIFENSORI(Sport.HOCKEY, "Difensori", "partite totali / 10 + (gol + assist) per partita × 100"),
    HO_PORTIERI(Sport.HOCKEY, "Portieri", "partite totali / 10 + titoli × 10"),
    ;

    companion object {
        fun forSport(sport: Sport): List<TeamRole> = entries.filter { it.sport == sport }
    }
}

object TeamRoles {

    private val fbDefense = listOf("defensive", "linebacker", "cornerback", "safety", "nose", "difensiv", "linea difensiva")
    private val fbOffense = listOf("quarterback", "running back", "fullback", "halfback", "wide receiver", "ricevitore", "tight end", "runningback")
    private val fbLine = listOf("offensive", "guard", "center", "centro", "kicker", "punter", "snapper", "tackle", "linea offensiva")

    private val ruForwards = listOf("pilone", "tallonatore", "seconda linea", "terza linea", "flanker", "numero 8", "numero otto", "avanti", "prop", "hooker", "lock")
    private val ruHalves = listOf("mediano")

    /** Gruppo di ruolo del giocatore; con un ruolo non riconosciuto decidono le statistiche. */
    fun roleOf(p: Player): TeamRole? {
        val r = p.ruolo.lowercase()
        return when (p.sport) {
            Sport.FOOTBALL -> when {
                fbDefense.any { it in r } -> TeamRole.FB_DIFESA
                fbOffense.any { it in r } -> TeamRole.FB_ATTACCO
                fbLine.any { it in r } -> TeamRole.FB_LINEA
                p.punteggio > 0 && p.punteggio >= p.golEvitati + p.tackle -> TeamRole.FB_ATTACCO
                p.golEvitati + p.tackle + p.rimbalzi > 0 -> TeamRole.FB_DIFESA
                else -> TeamRole.FB_LINEA
            }
            Sport.RUGBY -> when {
                ruForwards.any { it in r } -> TeamRole.RU_AVANTI
                ruHalves.any { it in r } -> TeamRole.RU_MEDIANI
                else -> TeamRole.RU_TREQUARTI
            }
            Sport.HOCKEY -> when {
                "portiere" in r || "goalie" in r || "goaltender" in r -> TeamRole.HO_PORTIERI
                "difensore" in r || "defense" in r || "defence" in r -> TeamRole.HO_DIFENSORI
                else -> TeamRole.HO_ATTACCANTI
            }
            else -> null
        }
    }

    /** Punteggio per ruolo (vedi le formule di [TeamRole]); 0 senza partite o stagioni. */
    fun score(p: Player): Int {
        val role = roleOf(p) ?: return 0
        val stagioni = p.presenze
        val partite = AthleteScoring.partiteTotali(p)
        val perPartita = AthleteScoring.puntiPerPartita(p) ?: 0.0
        val value = when (role) {
            TeamRole.FB_ATTACCO -> if (stagioni > 0) p.punteggio * 10.0 / stagioni else 0.0
            TeamRole.FB_DIFESA ->
                if (stagioni > 0) (p.golEvitati + p.tackle + p.punteggio + p.rimbalzi / 10.0) * 10.0 / stagioni else 0.0
            TeamRole.FB_LINEA ->
                if (stagioni > 0) (p.presenzeNazionale + p.punteggioNazionale) * 100.0 / stagioni else 0.0
            TeamRole.RU_AVANTI, TeamRole.HO_DIFENSORI -> partite / 10.0 + perPartita * 100
            TeamRole.RU_MEDIANI, TeamRole.RU_TREQUARTI, TeamRole.HO_ATTACCANTI -> perPartita * 100
            TeamRole.HO_PORTIERI -> partite / 10.0 + p.palleRecuperate * 10.0
        }
        return value.roundToInt()
    }

    /** Riga breve con i numeri che entrano nel punteggio del ruolo. */
    fun detail(p: Player): String {
        val role = roleOf(p) ?: return ""
        val numbers = when (role) {
            TeamRole.FB_ATTACCO -> "${p.punteggio} TD in ${p.presenze} stagioni"
            TeamRole.FB_DIFESA -> "${p.golEvitati} INT, ${p.tackle} sack, ${p.rimbalzi} tackle in ${p.presenze} stagioni"
            TeamRole.FB_LINEA -> "${p.presenzeNazionale} Pro Bowl, ${p.punteggioNazionale} All-Pro in ${p.presenze} stagioni"
            TeamRole.HO_PORTIERI -> "${AthleteScoring.partiteTotali(p)} partite, ${p.palleRecuperate} titoli"
            else -> "${AthleteScoring.puntiTotali(p)} punti in ${AthleteScoring.partiteTotali(p)} partite"
        }
        return "${role.label} · $numbers"
    }
}

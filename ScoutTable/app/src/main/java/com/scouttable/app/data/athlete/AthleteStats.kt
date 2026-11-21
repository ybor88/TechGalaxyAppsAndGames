// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import java.util.Locale

/**
 * Sport da combattimento, MotoGP e Formula 1 riusano la tabella [Player] (nessuna migrazione del
 * database): le colonne numeriche di basket/calcio prendono un significato proprio dello sport.
 *
 * | Colonna            | MotoGP / Formula 1        | Lotte                         |
 * |--------------------|---------------------------|-------------------------------|
 * | presenze           | gare disputate            | incontri disputati            |
 * | punteggio          | punti ottenuti            | incontri vinti                |
 * | assist             | podi                      | incontri persi                |
 * | rimbalzi           | gare vinte                | vittorie per KO               |
 * | palleRecuperate    | mondiali vinti            | titoli mondiali               |
 * | tackle             | pole position             | sconfitte per KO              |
 * | golEvitati         | giri veloci               | pareggi                       |
 * | carrieraMigliore   | scuderia principale       | categoria di peso             |
 * | competizione       | "Motomondiale"/"Formula 1"| disciplina (es. "Pugilato")   |
 * | ruolo              | —                         | "Maschile" / "Femminile"      |
 * | secondLogoPeriodo  | stagioni (es. "1996-2021")| —                             |
 *
 * Football americano, Rugby e Hockey (sport di squadra, vedi [TeamSportStats]):
 *
 * | Colonna            | Football americano       | Rugby                 | Hockey                |
 * |--------------------|--------------------------|-----------------------|-----------------------|
 * | presenze           | stagioni giocate         | presenze nei club     | partite nei club      |
 * | punteggio          | touchdown                | punti nei club        | gol nei club          |
 * | assist             | yard (lanci+corse+prese) | —                     | assist nei club       |
 * | rimbalzi           | tackle                   | —                     | —                     |
 * | tackle             | sack                     | —                     | —                     |
 * | golEvitati         | intercetti               | —                     | —                     |
 * | palleRecuperate    | Super Bowl vinti         | titoli (palmarès)     | titoli (palmarès)     |
 * | presenzeNazionale  | selezioni al Pro Bowl    | presenze in Nazionale | partite in Nazionale  |
 * | punteggioNazionale | selezioni All-Pro        | punti in Nazionale    | gol in Nazionale      |
 * | assistNazionale    | MVP della NFL            | —                     | assist in Nazionale   |
 * | ruolo              | ruolo in campo           | ruolo in campo        | ruolo in campo        |
 * | carrieraMigliore   | squadra principale       | squadra principale    | squadra principale    |
 * | competizione       | "Football americano"     | "Rugby a 15"          | "Hockey su ghiaccio"  |
 * | secondLogoPeriodo  | anni di carriera         | anni di carriera      | anni di carriera      |
 * | logoPath           | logo squadra principale  | logo squadra principale | logo squadra principale |
 * | secondLogoClub     | squadra stagione migliore| squadra periodo migliore| squadra stagione migliore|
 * | secondLogoPath     | suo logo                 | suo logo              | suo logo              |
 * | college            | stagione migliore ("2007")| periodo ("2005-19")  | stagione ("1981–82")  |
 * | secondLogoPresenze | partite nella stagione   | presenze nel periodo  | partite nella stagione|
 * | secondLogoGol      | touchdown nella stagione | punti nel periodo     | gol nella stagione    |
 * | secondLogoEff      | intercetti + sack stagione| —                     | assist nella stagione |
 */
val Player.gare: Int get() = presenze

/** Football/Rugby/Hockey: stagione (o periodo, nel rugby) migliore, salvata nella colonna college. */
val Player.stagioneMigliore: String get() = college

/** Titolo della colonna della stagione migliore: nel rugby è un periodo in un club. */
fun bestSeasonTitle(sport: Sport): String = if (sport == Sport.RUGBY) "Periodo migliore" else "Stagione migliore"

/** Numeri della stagione migliore, es. "2007 · 16 partite · 50 TD, 0 intercetti". */
fun bestSeasonDetail(p: Player): String {
    val numbers = when (p.sport) {
        Sport.FOOTBALL -> "${p.secondLogoPresenze} partite · ${p.secondLogoGol} TD, ${p.secondLogoEff} intercetti + sack"
        Sport.RUGBY -> "${p.secondLogoPresenze} presenze · ${p.secondLogoGol} punti"
        else -> if (TeamRoles.roleOf(p) == TeamRole.HO_PORTIERI) {
            "${p.secondLogoPresenze} partite · ${p.secondLogoGol} vittorie, ${p.secondLogoEff} shutout"
        } else {
            "${p.secondLogoPresenze} partite · ${p.secondLogoGol} gol, ${p.secondLogoEff} assist (${p.secondLogoGol + p.secondLogoEff} punti)"
        }
    }
    return listOf(p.stagioneMigliore, numbers).filter { it.isNotBlank() }.joinToString(" · ")
}
val Player.punti: Int get() = punteggio
val Player.podi: Int get() = assist
val Player.gareVinte: Int get() = rimbalzi
val Player.mondiali: Int get() = palleRecuperate
val Player.pole: Int get() = tackle
val Player.giriVeloci: Int get() = golEvitati

val Player.incontri: Int get() = presenze
val Player.vinti: Int get() = punteggio
val Player.persi: Int get() = assist
val Player.vintiKo: Int get() = rimbalzi
val Player.persiKo: Int get() = tackle
val Player.pareggi: Int get() = golEvitati
val Player.titoli: Int get() = palleRecuperate

const val GENERE_MASCHILE = "Maschile"
const val GENERE_FEMMINILE = "Femminile"

val Player.isFemale: Boolean get() = sport.isCombat && ruolo.equals(GENERE_FEMMINILE, ignoreCase = true)

/** Le classifiche disponibili per uno sport individuale: la prima è la classifica generale. */
enum class AthleteMetric(val label: String, val short: String) {
    // MotoGP / Formula 1
    // Classifica generale dei piloti: gare vinte rapportate alle gare disputate.
    PERC_VITTORIE_GARE("% gare vinte", "% vittorie"),
    PUNTI("Punti ottenuti", "punti"),
    PUNTI_GARA("Punti per gara", "punti/gara"),
    PODI("Podi", "podi"),
    MONDIALI("Mondiali vinti", "mondiali"),
    VITTORIE_GARE("Gare vinte", "vittorie"),
    POLE("Pole position", "pole"),
    GIRI_VELOCI("Giri veloci", "giri veloci"),

    // Lotte
    PERCENTUALE("% vittorie", "% vittorie"),
    VINTI("Incontri vinti", "vinti"),
    KO("Vittorie per KO", "KO"),
    TITOLI("Titoli vinti", "titoli"),
    INCONTRI("Incontri disputati", "incontri"),

    // Rugby / Hockey (club + Nazionale)
    // Classifica generale (efficienza): punti segnati per partita giocata.
    PUNTI_PARTITA("Punti per partita", "punti/partita"),
    PUNTI_CARRIERA("Punti in carriera", "punti"),
    GOL("Gol", "gol"),
    ASSIST("Assist", "assist"),
    PARTITE("Partite in carriera", "partite"),
    PARTITE_NAZIONALE("Presenze in Nazionale", "in Nazionale"),
    PUNTI_NAZIONALE("Punti in Nazionale", "punti in Nazionale"),

    // Football americano
    // Football / Rugby / Hockey: classifica generale, punteggio con la formula del ruolo (TeamRoles).
    PUNTEGGIO_RUOLO("Punteggio per ruolo", "pt"),

    // Touchdown + intercetti diviso stagioni giocate.
    TD_INT_STAGIONE("Touchdown + intercetti per stagione", "TD+INT/stagione"),
    TOUCHDOWN("Touchdown", "TD"),
    YARD("Yard totali", "yard"),
    TACKLE_FB("Tackle", "tackle"),
    SACK("Sack", "sack"),
    INTERCETTI("Intercetti", "intercetti"),
    PRO_BOWL("Selezioni al Pro Bowl", "Pro Bowl"),
    ALL_PRO("Selezioni All-Pro", "All-Pro"),
    MVP("MVP della NFL", "MVP"),
    STAGIONI("Stagioni giocate", "stagioni"),
    ;

    companion object {
        fun forSport(sport: Sport): List<AthleteMetric> = when {
            sport.isMotor -> listOf(PERC_VITTORIE_GARE, PUNTI, PUNTI_GARA, PODI, MONDIALI, VITTORIE_GARE, POLE, GIRI_VELOCI)
            sport == Sport.FOOTBALL ->
                listOf(PUNTEGGIO_RUOLO, TD_INT_STAGIONE, TOUCHDOWN, YARD, TACKLE_FB, SACK, INTERCETTI, PRO_BOWL, ALL_PRO, MVP, TITOLI, STAGIONI)
            sport == Sport.RUGBY -> listOf(PUNTEGGIO_RUOLO, PUNTI_PARTITA, PUNTI_CARRIERA, PARTITE, PARTITE_NAZIONALE, PUNTI_NAZIONALE, TITOLI)
            sport == Sport.HOCKEY -> listOf(PUNTEGGIO_RUOLO, PUNTI_PARTITA, PUNTI_CARRIERA, GOL, ASSIST, PARTITE, PARTITE_NAZIONALE, TITOLI)
            else -> listOf(PERCENTUALE, VINTI, KO, TITOLI, INCONTRI)
        }
    }
}

/** true se il valore della classifica è una percentuale. */
val AthleteMetric.isPercent: Boolean
    get() = this == AthleteMetric.PERCENTUALE || this == AthleteMetric.PERC_VITTORIE_GARE

/** Etichetta della classifica per lo sport: i "titoli" e i "punti" cambiano significato da sport a sport. */
fun AthleteMetric.labelFor(sport: Sport): String = when {
    this == AthleteMetric.TITOLI -> AthleteScoring.titlesLabel(sport)
    this == AthleteMetric.PUNTI_CARRIERA && sport == Sport.HOCKEY -> "Punti in carriera (gol + assist)"
    this == AthleteMetric.PUNTI_PARTITA && sport == Sport.HOCKEY -> "Punti (gol + assist) per partita"
    else -> label
}

object AthleteScoring {

    /** Cosa conta come "titolo" (colonna palleRecuperate). */
    fun titlesLabel(sport: Sport): String = when (sport) {
        Sport.SUMO -> "Tornei vinti (yūshō)"
        Sport.PUGILATO -> "Titoli mondiali"
        Sport.FOOTBALL -> "Super Bowl vinti"
        Sport.HOCKEY -> "Titoli vinti (Stanley Cup, ori…)"
        else -> "Titoli vinti"
    }

    /** Classifica generale dello sport: % gare vinte (MotoGP/F1), % incontri vinti (lotte), punti per
     *  partita (rugby, hockey), touchdown + intercetti per stagione (football). */
    fun generalMetric(sport: Sport): AthleteMetric = AthleteMetric.forSport(sport).first()

    /** Valore della freccia ↑/↓: lo stesso della classifica generale. */
    fun ratioMetric(sport: Sport): AthleteMetric = generalMetric(sport)

    /** Gare vinte su gare disputate, in percentuale; null senza gare. */
    fun percentualeGareVinte(p: Player): Double? = if (p.gare > 0) p.gareVinte * 100.0 / p.gare else null

    /** Punti ottenuti diviso gare disputate; null senza gare. */
    fun puntiPerGara(p: Player): Double? = if (p.gare > 0) p.punti.toDouble() / p.gare else null

    /** Incontri vinti su incontri disputati, in percentuale; null senza incontri. */
    fun percentualeVittorie(p: Player): Double? = if (p.incontri > 0) p.vinti * 100.0 / p.incontri else null

    /** Rugby/Hockey: partite giocate in carriera, club e Nazionale. */
    fun partiteTotali(p: Player): Int = p.presenze + p.presenzeNazionale

    /** Rugby: punti di club e Nazionale. Hockey: gol + assist di club e Nazionale. */
    fun puntiTotali(p: Player): Int =
        if (p.sport == Sport.HOCKEY) p.punteggio + p.assist + p.punteggioNazionale + p.assistNazionale
        else p.punteggio + p.punteggioNazionale

    /** Punti per partita (rugby, hockey); null senza partite. */
    fun puntiPerPartita(p: Player): Double? = partiteTotali(p).takeIf { it > 0 }?.let { puntiTotali(p).toDouble() / it }

    /** Football: touchdown + intercetti (colonne punteggio e golEvitati) diviso stagioni giocate; null senza stagioni. */
    fun tdIntPerStagione(p: Player): Double? =
        if (p.presenze > 0) (p.punteggio + p.golEvitati).toDouble() / p.presenze else null

    /** Football: statistiche che dipendono dal ruolo (un linebacker non ha yard, un quarterback non ha
     *  tackle): chi non le ha resta fuori da quella classifica invece di comparire con zero. */
    private fun positional(v: Int): Double? = v.takeIf { it > 0 }?.toDouble()

    fun value(p: Player, metric: AthleteMetric): Double? = when (metric) {
        AthleteMetric.PERC_VITTORIE_GARE -> percentualeGareVinte(p)
        AthleteMetric.PUNTI -> p.punti.toDouble()
        AthleteMetric.PUNTI_GARA -> puntiPerGara(p)
        AthleteMetric.PODI -> p.podi.toDouble()
        AthleteMetric.MONDIALI -> p.mondiali.toDouble()
        AthleteMetric.VITTORIE_GARE -> p.gareVinte.toDouble()
        AthleteMetric.POLE -> p.pole.toDouble()
        AthleteMetric.GIRI_VELOCI -> p.giriVeloci.toDouble()
        AthleteMetric.PERCENTUALE -> percentualeVittorie(p)
        AthleteMetric.VINTI -> p.vinti.toDouble()
        AthleteMetric.KO -> p.vintiKo.toDouble()
        AthleteMetric.TITOLI -> p.titoli.toDouble()
        AthleteMetric.INCONTRI -> p.incontri.toDouble()
        AthleteMetric.PUNTI_PARTITA -> puntiPerPartita(p)
        AthleteMetric.PUNTI_CARRIERA -> puntiTotali(p).toDouble()
        AthleteMetric.GOL -> (p.punteggio + p.punteggioNazionale).toDouble()
        AthleteMetric.ASSIST -> (p.assist + p.assistNazionale).toDouble()
        AthleteMetric.PARTITE -> partiteTotali(p).toDouble()
        AthleteMetric.PARTITE_NAZIONALE -> p.presenzeNazionale.toDouble()
        AthleteMetric.PUNTI_NAZIONALE -> p.punteggioNazionale.toDouble()
        AthleteMetric.TD_INT_STAGIONE -> tdIntPerStagione(p)
        AthleteMetric.PUNTEGGIO_RUOLO -> TeamRoles.score(p).toDouble()
        AthleteMetric.TOUCHDOWN -> positional(p.punteggio)
        AthleteMetric.YARD -> positional(p.assist)
        AthleteMetric.TACKLE_FB -> positional(p.rimbalzi)
        AthleteMetric.SACK -> positional(p.tackle)
        AthleteMetric.INTERCETTI -> positional(p.golEvitati)
        AthleteMetric.PRO_BOWL -> p.presenzeNazionale.toDouble()
        AthleteMetric.ALL_PRO -> p.punteggioNazionale.toDouble()
        AthleteMetric.MVP -> p.assistNazionale.toDouble()
        AthleteMetric.STAGIONI -> p.presenze.toDouble()
    }

    /** Valore della freccia per l'atleta (vedi [ratioMetric]). */
    fun ratio(p: Player): Double? = value(p, ratioMetric(p.sport))

    fun format(metric: AthleteMetric, value: Double?): String = when {
        value == null -> "—"
        metric == AthleteMetric.PUNTI_GARA || metric == AthleteMetric.PUNTI_PARTITA || metric == AthleteMetric.TD_INT_STAGIONE -> String.format(Locale.ITALIAN, "%.2f", value)
        metric.isPercent -> String.format(Locale.ITALIAN, "%.1f%%", value)
        else -> value.toLong().toString()
    }

    fun formatValue(p: Player, metric: AthleteMetric): String = format(metric, value(p, metric))

    /** In classifica solo chi ha statistiche (gare, incontri, partite o stagioni). */
    fun hasStats(p: Player): Boolean = p.presenze > 0 || (p.sport.isTeamSport && p.presenzeNazionale > 0)

    /**
     * Classifica per [metric], dal valore più alto. Spareggio: il valore della classifica
     * generale, poi il nome. Esclusi gli atleti senza statistiche.
     */
    fun ranking(players: List<Player>, metric: AthleteMetric): List<Player> {
        val sport = players.firstOrNull()?.sport ?: return emptyList()
        val general = generalMetric(sport)
        return players.filter { hasStats(it) && value(it, metric) != null }
            .sortedWith(
                compareByDescending<Player> { value(it, metric) ?: 0.0 }
                    .thenByDescending { value(it, general) ?: 0.0 }
                    .thenBy { it.nome },
            )
    }

    /** Nelle lotte maschi e femmine sono classifiche separate; negli altri sport [female] è ignorato. */
    fun sameGroup(players: List<Player>, sport: Sport, female: Boolean): List<Player> =
        if (sport.isCombat) players.filter { it.isFemale == female } else players

    /** Gruppo di confronto per la freccia: stesso genere (lotte), stesso gruppo di ruolo (football,
     *  rugby, hockey: i punteggi hanno formule diverse per ruolo), altrimenti tutta la lista. */
    fun comparisonGroup(p: Player, all: List<Player>): List<Player> = when {
        p.sport.isTeamSport -> TeamRoles.roleOf(p).let { role -> all.filter { TeamRoles.roleOf(it) == role } }
        else -> sameGroup(all, p.sport, p.isFemale)
    }

    /** Media del rapporto (valore della classifica generale) sugli atleti del gruppo con statistiche. */
    fun averageRatio(group: List<Player>): Double? =
        group.mapNotNull { if (hasStats(it)) ratio(it) else null }.takeIf { it.isNotEmpty() }?.average()

    /**
     * Distacco del rapporto dell'atleta dalla media del suo gruppo (tutta la lista; nelle lotte
     * solo lo stesso genere): positivo = sopra la media (freccia su), negativo = sotto (giù).
     */
    fun distacco(p: Player, all: List<Player>): Double? {
        val r = ratio(p) ?: return null
        if (!hasStats(p)) return null
        val group = comparisonGroup(p, all)
        if (group.count { hasStats(it) } < 2) return null
        val avg = averageRatio(group) ?: return null
        return r - avg
    }

    /** 1 = freccia su, -1 = giù, 0 = trattino (pari alla media o dati mancanti). */
    fun trend(p: Player, all: List<Player>): Int {
        val d = distacco(p, all) ?: return 0
        return when {
            d > EPSILON -> 1
            d < -EPSILON -> -1
            else -> 0
        }
    }

    fun formatDistacco(sport: Sport, d: Double?): String {
        if (d == null) return "—"
        val sign = if (d > 0) "+" else ""
        // Rapporto in percentuale: distacco in punti percentuali; punti per partita: in punti.
        return when {
            ratioMetric(sport).isPercent -> sign + String.format(Locale.ITALIAN, "%.1f", d) + " pt %"
            sport.isTeamSport -> sign + String.format(Locale.ITALIAN, "%.0f", d) + " punti (media del ruolo)"
            else -> sign + String.format(Locale.ITALIAN, "%.2f", d) + " punti/partita"
        }
    }

    private const val EPSILON = 1e-9

    // ---- Stella automatica (vedi PlayerScoring.computeStar) ----

    private const val LOTTA_STAR_PERCENT = 85.0
    private const val FOOTBALL_STAR_PRO_BOWL = 5
    private const val RUGBY_STAR_CAPS = 50
    private const val HOCKEY_STAR_PPG = 1.0

    /** Stella: almeno un mondiale vinto (MotoGP/F1), almeno l'85% di vittorie (lotte), 5 Pro Bowl o un
     *  MVP (football), 50 presenze in Nazionale (rugby), un punto a partita (hockey). */
    fun computeStar(p: Player): Boolean = when {
        p.sport.isMotor -> p.mondiali >= 1
        p.sport == Sport.FOOTBALL -> p.presenzeNazionale >= FOOTBALL_STAR_PRO_BOWL || p.assistNazionale >= 1
        p.sport == Sport.RUGBY -> p.presenzeNazionale >= RUGBY_STAR_CAPS
        p.sport == Sport.HOCKEY -> (puntiPerPartita(p) ?: 0.0) >= HOCKEY_STAR_PPG
        else -> (percentualeVittorie(p) ?: 0.0) >= LOTTA_STAR_PERCENT
    }

    fun starReason(p: Player): String? {
        if (!p.star) return null
        val auto = computeStar(p)
        val kind = if (auto) "Stella automatica" else "Stella manuale"
        return when {
            p.sport == Sport.FOOTBALL ->
                "$kind: ${p.presenzeNazionale} Pro Bowl, ${p.assistNazionale} MVP (soglia $FOOTBALL_STAR_PRO_BOWL Pro Bowl o 1 MVP)"
            p.sport == Sport.RUGBY -> "$kind: ${p.presenzeNazionale} presenze in Nazionale (soglia $RUGBY_STAR_CAPS)"
            p.sport == Sport.HOCKEY ->
                "$kind: ${format(AthleteMetric.PUNTI_PARTITA, puntiPerPartita(p))} punti per partita (soglia 1)"
            p.sport.isMotor ->
                if (auto) "Stella automatica: ${p.mondiali} mondiali vinti" else "Stella manuale: nessun mondiale vinto"
            else -> {
                val pct = format(AthleteMetric.PERCENTUALE, percentualeVittorie(p))
                if (auto) "Stella automatica: $pct vittorie (soglia 85%)" else "Stella manuale: $pct vittorie, sotto soglia 85%"
            }
        }
    }

    // ---- Etichette ----

    /** Nome della "carriera migliore" per lo sport: scuderia (MotoGP/F1), squadra (sport di squadra)
     *  o categoria (lotte). */
    fun teamLabel(sport: Sport): String = when {
        sport.isMotor -> "Scuderia"
        sport.isTeamSport -> "Squadra"
        else -> "Categoria"
    }

    /** Riga breve delle statistiche principali, per le card. */
    fun statsLine(p: Player): String = when {
        p.sport.isMotor -> "${p.gare} gare · ${p.punti} punti · ${p.podi} podi · ${p.mondiali} mondiali"
        p.sport == Sport.FOOTBALL -> listOfNotNull(
            "${p.presenze} stagioni",
            p.punteggio.takeIf { it > 0 }?.let { "$it TD" },
            p.assist.takeIf { it > 0 }?.let { "$it yard" },
            p.rimbalzi.takeIf { it > 0 }?.let { "$it tackle" },
            "${p.presenzeNazionale} Pro Bowl",
        ).joinToString(" · ")
        p.sport == Sport.RUGBY -> "${partiteTotali(p)} presenze · ${puntiTotali(p)} punti · ${p.presenzeNazionale} in Nazionale"
        p.sport == Sport.HOCKEY ->
            "${partiteTotali(p)} partite · ${p.punteggio + p.punteggioNazionale} gol · ${p.assist + p.assistNazionale} assist"
        else -> "${p.incontri} incontri · ${p.vinti} vinti (${p.vintiKo} KO) · ${p.persi} persi"
    }

    /** Riga sotto il nome nelle classifiche: il dato di contorno più utile per lo sport. */
    fun rankingDetail(p: Player): String = when {
        p.sport.isTeamSport -> TeamRoles.detail(p)
        p.sport.isMotor -> "${p.podi} podi"
        else -> "${p.vinti}V ${p.persi}P su ${p.incontri}"
    }

    fun athleteWord(sport: Sport, plural: Boolean = false): String = when {
        sport.isMotor -> if (plural) "piloti" else "pilota"
        sport.isTeamSport -> if (plural) "giocatori" else "giocatore"
        sport == Sport.PUGILATO -> if (plural) "pugili" else "pugile"
        sport == Sport.SUMO -> if (plural) "lottatori di sumo" else "lottatore di sumo"
        sport == Sport.WRESTLING -> "wrestler"
        sport == Sport.KICKBOXING -> "kickboxer"
        sport == Sport.MMA -> if (plural) "lottatori MMA" else "lottatore MMA"
        else -> if (plural) "lottatori" else "lottatore"
    }
}

/** Una statistica modificabile/confrontabile di uno sport di squadra: etichetta e colonna del [Player]. */
class TeamStatField(val label: String, val get: (Player) -> Int, val set: (Player, Int) -> Player)

/** Le statistiche salvate per Football, Rugby e Hockey, con il significato delle colonne (vedi tabella sopra). */
fun teamStatFields(sport: Sport): List<TeamStatField> = when (sport) {
    Sport.FOOTBALL -> listOf(
        TeamStatField("Stagioni giocate", { it.presenze }) { p, v -> p.copy(presenze = v) },
        TeamStatField("Touchdown", { it.punteggio }) { p, v -> p.copy(punteggio = v) },
        TeamStatField("Yard totali", { it.assist }) { p, v -> p.copy(assist = v) },
        TeamStatField("Tackle", { it.rimbalzi }) { p, v -> p.copy(rimbalzi = v) },
        TeamStatField("Sack", { it.tackle }) { p, v -> p.copy(tackle = v) },
        TeamStatField("Intercetti", { it.golEvitati }) { p, v -> p.copy(golEvitati = v) },
        TeamStatField("Selezioni al Pro Bowl", { it.presenzeNazionale }) { p, v -> p.copy(presenzeNazionale = v) },
        TeamStatField("Selezioni All-Pro", { it.punteggioNazionale }) { p, v -> p.copy(punteggioNazionale = v) },
        TeamStatField("MVP della NFL", { it.assistNazionale }) { p, v -> p.copy(assistNazionale = v) },
        TeamStatField("Super Bowl vinti", { it.palleRecuperate }) { p, v -> p.copy(palleRecuperate = v) },
    )
    Sport.RUGBY -> listOf(
        TeamStatField("Presenze nei club", { it.presenze }) { p, v -> p.copy(presenze = v) },
        TeamStatField("Punti nei club", { it.punteggio }) { p, v -> p.copy(punteggio = v) },
        TeamStatField("Presenze in Nazionale", { it.presenzeNazionale }) { p, v -> p.copy(presenzeNazionale = v) },
        TeamStatField("Punti in Nazionale", { it.punteggioNazionale }) { p, v -> p.copy(punteggioNazionale = v) },
        TeamStatField("Titoli vinti", { it.palleRecuperate }) { p, v -> p.copy(palleRecuperate = v) },
    )
    else -> listOf(
        TeamStatField("Partite nei club", { it.presenze }) { p, v -> p.copy(presenze = v) },
        TeamStatField("Gol nei club", { it.punteggio }) { p, v -> p.copy(punteggio = v) },
        TeamStatField("Assist nei club", { it.assist }) { p, v -> p.copy(assist = v) },
        TeamStatField("Partite in Nazionale", { it.presenzeNazionale }) { p, v -> p.copy(presenzeNazionale = v) },
        TeamStatField("Gol in Nazionale", { it.punteggioNazionale }) { p, v -> p.copy(punteggioNazionale = v) },
        TeamStatField("Assist in Nazionale", { it.assistNazionale }) { p, v -> p.copy(assistNazionale = v) },
        TeamStatField(AthleteScoring.titlesLabel(Sport.HOCKEY), { it.palleRecuperate }) { p, v -> p.copy(palleRecuperate = v) },
    )
}

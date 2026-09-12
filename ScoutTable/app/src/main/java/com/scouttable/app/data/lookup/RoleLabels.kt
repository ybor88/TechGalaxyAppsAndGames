package com.scouttable.app.data.lookup

import com.scouttable.app.data.Sport

// Stessa parola inglese può indicare ruoli diversi tra calcio e basket (es. "Forward" ->
// "Attaccante" nel calcio, "Ala" nel basket): la traduzione dipende dallo sport.
private val calcioRoles: Map<String, String> = mapOf(
    "goalkeeper" to "Portiere",
    "sweeper" to "Libero",
    "centre-back" to "Difensore centrale",
    "center-back" to "Difensore centrale",
    "left-back" to "Terzino sinistro",
    "right-back" to "Terzino destro",
    "defender" to "Difensore",
    "defensive midfield" to "Mediano",
    "central midfield" to "Centrocampista centrale",
    "attacking midfield" to "Trequartista",
    "left midfield" to "Centrocampista sinistro",
    "right midfield" to "Centrocampista destro",
    "midfielder" to "Centrocampista",
    "centre-forward" to "Centravanti",
    "center-forward" to "Centravanti",
    "striker" to "Attaccante",
    "left winger" to "Ala sinistra",
    "right winger" to "Ala destra",
    "winger" to "Ala",
    "forward" to "Attaccante",
)

private val basketRoles: Map<String, String> = mapOf(
    "point guard" to "Playmaker",
    "shooting guard" to "Guardia",
    "small forward" to "Ala piccola",
    "power forward" to "Ala grande",
    "center" to "Centro",
    "centre" to "Centro",
    "guard" to "Guardia",
    "forward" to "Ala",
)

/**
 * Vero se [raw] è un vero ruolo da giocatore per lo sport dato (es. "Goalkeeper", "Point Guard",
 * anche in forma composta come "Forward/Center"). Usata da [com.scouttable.app.data.lookup.PlayerLookupService]
 * per distinguere un ruolo da giocatore da un'etichetta professionale (es. "CEO", "Chairman",
 * "Manager", "Sporting Director") che TheSportsDB può restituire per un ex giocatore diventato
 * dirigente/allenatore: senza questo controllo un caso come Edwin van der Sar (portiere, oggi CEO
 * dell'Ajax) verrebbe salvato con ruolo "CEO" invece di "Portiere", perdendo anche i gol subiti in
 * PlayerRow (mostrati solo per [isPortiere]).
 */
fun isKnownPlayingPosition(raw: String, sport: Sport): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return false
    val table = if (sport == Sport.BASKET) basketRoles else calcioRoles
    val lower = trimmed.lowercase()
    if (table.containsKey(lower) || table.values.any { it.lowercase() == lower }) return true
    val segments = lower.split('/', ',').map { it.trim() }.filter { it.isNotBlank() }
    return segments.any { seg -> table.containsKey(seg) || table.values.any { it.lowercase() == seg } }
}

/**
 * Traduce in italiano un ruolo restituito in inglese da TheSportsDB/Wikipedia (es. "Point Guard",
 * "Centre-Back"); se non riconosciuto (o già in italiano) restituisce il valore originale invariato,
 * così nessun dato viene perso in caso di etichetta non prevista.
 *
 * Gestisce anche i ruoli composti che TheSportsDB restituisce per alcuni giocatori versatili (es.
 * "Forward/Center" per Kareem Abdul-Jabbar): nel basket preferisce il segmento che corrisponde a
 * uno dei 5 ruoli specifici (qui "Center") invece del primo in assoluto ("Forward" -> "Ala",
 * un'etichetta troppo generica perché [basketRoleOf] la riconosca, con la conseguenza che il
 * giocatore perderebbe le statistiche aggiuntive per ruolo in PlayerRow).
 */
fun translateRole(raw: String, sport: Sport): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return trimmed
    val table = if (sport == Sport.BASKET) basketRoles else calcioRoles
    table[trimmed.lowercase()]?.let { return it }

    val segments = trimmed.split('/', ',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
    if (segments.size > 1) {
        if (sport == Sport.BASKET) {
            segments.firstOrNull { it in basketRoleByLabel }?.let { return basketRoles.getValue(it) }
        }
        table[segments.first()]?.let { return it }
    }
    return trimmed
}

// Enum dei 5 ruoli basket usati per decidere quali statistiche mostrare in PlayerRow: il valore
// grezzo di Player.ruolo può essere in italiano (già tradotto) o, più raramente, ancora in inglese
// (es. un valore inserito a mano, o un'etichetta non riconosciuta da translateRole) — il match
// controlla entrambe le forme.
enum class BasketRole { CENTRO, ALA_GRANDE, ALA_PICCOLA, GUARDIA, PLAYMAKER }

private val basketRoleByLabel: Map<String, BasketRole> = buildMap {
    basketRoles.forEach { (english, italian) ->
        val role = when (english) {
            "center", "centre" -> BasketRole.CENTRO
            "power forward" -> BasketRole.ALA_GRANDE
            "small forward" -> BasketRole.ALA_PICCOLA
            "point guard" -> BasketRole.PLAYMAKER
            "shooting guard", "guard" -> BasketRole.GUARDIA
            else -> null
        } ?: return@forEach
        put(english, role)
        put(italian.lowercase(), role)
    }
}

/** Ruolo basket per cui mostrare statistiche aggiuntive in [com.scouttable.app.ui.common.PlayerRow]
 * (rimbalzi/palle recuperate/tiri/assist), o null se non riconosciuto (es. "Ala" generico, o
 * "Forward" non tradotto: nessuno dei 5 ruoli specifici).
 *
 * Gestisce anche un valore composto non tradotto già salvato in [com.scouttable.app.data.Player]
 * da prima del fix in [translateRole] (es. "Forward/Center" per un giocatore aggiunto quando
 * questa funzione non spezzava ancora i ruoli composti): senza questo, un giocatore già presente
 * in lista resterebbe senza statistiche per ruolo finché non viene ricercato di nuovo. */
fun basketRoleOf(ruolo: String): BasketRole? {
    val key = ruolo.trim().lowercase()
    basketRoleByLabel[key]?.let { return it }
    if (key.contains('/') || key.contains(',')) {
        return key.split('/', ',').map { it.trim() }.firstNotNullOfOrNull { basketRoleByLabel[it] }
    }
    return null
}

// Stesso approccio per il calcio: raggruppa i ruoli specifici di calcioRoles nelle due macro-
// categorie usate in PlayerRow (difensori: tackle/gol evitati; centrocampisti: assist).
// "goalkeeper"/Portiere escluso di proposito: mostra già golSubiti in PlayerRow, non tackle/gol evitati.
private val difensoreKeys = setOf("sweeper", "centre-back", "center-back", "left-back", "right-back", "defender")
private val centrocampistaKeys = setOf("defensive midfield", "central midfield", "attacking midfield", "left midfield", "right midfield", "midfielder")

private fun calcioRoleMatches(ruolo: String, englishKeys: Set<String>): Boolean {
    val key = ruolo.trim().lowercase()
    if (key in englishKeys) return true
    return englishKeys.any { calcioRoles[it]?.lowercase() == key }
}

/** Vero per i ruoli difensivi (Difensore, Difensore centrale, Terzino sinistro/destro, Libero):
 * mostra tackle/gol evitati in PlayerRow. */
fun isDifensore(ruolo: String): Boolean = calcioRoleMatches(ruolo, difensoreKeys)

/** Vero per i ruoli di centrocampo (Centrocampista, Mediano, Trequartista, ...): mostra assist
 * in PlayerRow. */
fun isCentrocampista(ruolo: String): Boolean = calcioRoleMatches(ruolo, centrocampistaKeys)

/** Vero per il portiere: nasconde "gol fatti" (sempre ~0, non ha senso per questo ruolo) sia in
 * PlayerRow sia in "Modifica giocatore", mostrando solo presenze e gol subiti. */
fun isPortiere(ruolo: String): Boolean = calcioRoleMatches(ruolo, setOf("goalkeeper"))

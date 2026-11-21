// Copyright © Roberto Di Flumeri
package com.scouttable.app.data

import java.text.Normalizer

/**
 * Un gruppo di schede che sembrano lo stesso giocatore: [keep] resta (la più completa, salvo
 * scelta diversa dell'utente), [remove] vengono eliminate. [reasons] spiega perché sono simili.
 */
data class DuplicateGroup(
    val players: List<Player>,
    val keepId: String,
    val reasons: List<String>,
) {
    val keep: Player get() = players.first { it.id == keepId }
    val remove: List<Player> get() = players.filter { it.id != keepId }
}

/**
 * Ricerca dei giocatori duplicati nella lista di uno sport. Due schede sono lo stesso giocatore se:
 *  - hanno lo stesso link della fonte (Proballers, Wikipedia, FBref...), oppure
 *  - hanno lo stesso nome (senza accenti, maiuscole e punteggiatura) e lo stesso anno di nascita, oppure
 *  - hanno lo stesso nome, a una delle due manca l'anno e la nazione coincide (o manca).
 * Stesso nome ma anni diversi (es. padre e figlio) NON sono duplicati.
 */
object PlayerDuplicates {

    fun normalizeName(nome: String): String =
        Normalizer.normalize(nome, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun normalizeUrl(url: String?): String? = url?.trim()?.lowercase()
        ?.removePrefix("https://")?.removePrefix("http://")?.removePrefix("www.")
        ?.substringBefore('#')?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }

    private fun normalizeNation(n: String) = normalizeName(n)

    /** Perché [a] e [b] sono lo stesso giocatore, o null se non lo sono. */
    fun reason(a: Player, b: Player): String? {
        val ua = normalizeUrl(a.proballersUrl)
        if (ua != null && ua == normalizeUrl(b.proballersUrl)) return "stesso link della fonte"
        val na = normalizeName(a.nome)
        if (na.isEmpty() || na != normalizeName(b.nome)) return null
        if (a.anno != 0 && a.anno == b.anno) return "stesso nome e anno di nascita"
        if (a.anno == 0 || b.anno == 0) {
            val xa = normalizeNation(a.nazione)
            val xb = normalizeNation(b.nazione)
            if (xa.isEmpty() || xb.isEmpty() || xa == xb) return "stesso nome (anno di nascita mancante)"
        }
        return null
    }

    /** Quanti dati ha la scheda: si tiene la più completa. */
    fun completeness(p: Player): Int = listOf(
        p.anno != 0, p.nazione.isNotBlank(), p.ruolo.isNotBlank(), p.carrieraMigliore.isNotBlank(),
        !p.logoPath.isNullOrBlank(), !p.secondLogoPath.isNullOrBlank(), !p.proballersUrl.isNullOrBlank(),
        p.competizione.isNotBlank(), p.presenze > 0, p.punteggio > 0, p.assist > 0, p.rimbalzi > 0,
        p.presenzeNazionale > 0, p.punteggioNazionale > 0, p.golSubiti > 0, p.tackle > 0,
        p.effMedio > 0, p.minutiCarriera > 0, !p.giovanili.isNullOrBlank(), p.college.isNotBlank(),
    ).count { it }

    /** La scheda da tenere: la più completa, poi la revisionata più di recente, poi l'ultima aggiornata. */
    fun best(players: List<Player>): Player = players.maxWith(
        compareBy<Player> { completeness(it) }.thenBy { it.lastReviewedAt }.thenBy { it.updatedAt },
    )

    fun find(players: List<Player>): List<DuplicateGroup> {
        val n = players.size
        val parent = IntArray(n) { it }
        fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; parent[i] = r; return r }
        val reasons = mutableMapOf<Pair<Int, Int>, String>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val why = reason(players[i], players[j]) ?: continue
            reasons[i to j] = why
            val ri = root(i); val rj = root(j)
            if (ri != rj) parent[rj] = ri
        }
        return (0 until n).groupBy { root(it) }.values
            .filter { it.size > 1 }
            .map { idx ->
                val group = idx.map { players[it] }.sortedBy { it.nome.lowercase() }
                val why = reasons.filterKeys { (a, b) -> a in idx && b in idx }.values.distinct()
                DuplicateGroup(group, best(group).id, why)
            }
            .sortedBy { it.keep.nome.lowercase() }
    }

    /** La scheda tenuta eredita i segni manuali (stella, scouting, visionato) delle schede eliminate. */
    fun merged(group: DuplicateGroup): Player {
        val k = group.keep
        return k.copy(
            star = group.players.any { it.star },
            scouting = group.players.any { it.scouting },
            visionato = group.players.any { it.visionato },
        )
    }
}

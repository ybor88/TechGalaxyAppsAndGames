package com.scouttable.app.data.lookup

/**
 * Sceglie lo spell (periodo a un club) da mostrare come "secondo logo" del giocatore. Gli spell
 * senza gol (0, rapporto indefinito) sono esclusi: un giocatore che non ha mai segnato/subito in
 * quello spell non offre un rapporto significativo da confrontare con gli altri.
 *
 * Il rapporto usato è sempre presenze/gol, ma la direzione "migliore" dipende dal segno di
 * [ClubSpell.golSubiti]:
 * - attaccanti/centrocampisti/difensori (gol = gol segnati): valore più BASSO è migliore (meno
 *   presenze servite per segnare un gol — un attaccante prolifico).
 * - portieri (gol = gol subiti): valore più ALTO è migliore (più presenze per ogni gol subito,
 *   cioè meno gol subiti a partita — un portiere solido). Usare qui lo stesso "più basso è
 *   meglio" dell'outfield sceglierebbe lo spell con PIÙ gol subiti per presenza, cioè il periodo
 *   peggiore spacciato per migliore.
 */
object BestSpellPicker {

    fun pickBest(spells: List<ClubSpell>): ClubSpell? {
        val candidates = spells.filter { it.gol > 0 }
        if (candidates.isEmpty()) return null
        val isGoalkeeper = candidates.first().golSubiti
        return if (isGoalkeeper) {
            candidates.maxByOrNull { it.presenze.toDouble() / it.gol }
        } else {
            candidates.minByOrNull { it.presenze.toDouble() / it.gol }
        }
    }
}

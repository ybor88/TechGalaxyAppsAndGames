package com.scouttable.app.data.lookup

/**
 * Sceglie lo spell (periodo a un club) da mostrare come "secondo logo" del giocatore: quello con
 * il miglior rapporto presenze/gol (attaccanti/centrocampisti/difensori) o gol-subiti/presenze
 * (portieri) — in entrambi i casi il valore più basso è la performance migliore. Gli spell senza
 * gol (0, rapporto indefinito) sono esclusi: un giocatore che non ha mai segnato/subito in quello
 * spell non offre un rapporto significativo da confrontare con gli altri.
 */
object BestSpellPicker {

    fun pickBest(spells: List<ClubSpell>): ClubSpell? =
        spells.filter { it.gol > 0 }.minByOrNull { it.presenze.toDouble() / it.gol }
}

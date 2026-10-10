package com.scouttable.app.data.lookup

/**
 * Sceglie lo spell (periodo a un club) da mostrare come "secondo logo" del giocatore. Gli spell
 * senza gol (0, rapporto indefinito) sono esclusi dal confronto: un giocatore che non ha mai
 * segnato/subito in quello spell non offre un rapporto significativo (se nessuno spell ha gol,
 * vedi il ripiego sulle presenze in [pickBest]).
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

    // Uno spell brevissimo vince il confronto per caso (verificato su Lewandowski: "Znicz
    // Pruszków II", 4 presenze e 8 gol, batteva il Bayern; Kvaratskhelia: 11 presenze alla Dinamo
    // Batumi). Contano solo gli spell con almeno questo numero di presenze (o, per una carriera
    // tutta più corta, almeno quanto lo spell più lungo).
    private const val MIN_PRESENZE = 20

    fun pickBest(spells: List<ClubSpell>): ClubSpell? {
        val minPresenze = minOf(MIN_PRESENZE, spells.maxOfOrNull { it.presenze } ?: 0)
        val candidates = spells.filter { it.gol > 0 && it.presenze >= minPresenze }
        // Nessuno spell con gol (es. un difensore che non ha mai segnato): prima non c'era nessun
        // periodo migliore e il dettaglio restava senza statistiche; ora vale lo spell con più
        // presenze, l'unico criterio che resta significativo.
        if (candidates.isEmpty()) return spells.filter { it.presenze > 0 }.maxByOrNull { it.presenze }
        val isGoalkeeper = candidates.first().golSubiti
        return if (isGoalkeeper) {
            candidates.maxByOrNull { it.presenze.toDouble() / it.gol }
        } else {
            candidates.minByOrNull { it.presenze.toDouble() / it.gol }
        }
    }
}

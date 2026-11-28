// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dominio

/** Un pezzo di testo riconosciuto dall'OCR con la sua posizione sulla pagina. */
data class Frammento(val testo: String, val sinistra: Int, val alto: Int, val basso: Int) {
    val centroY: Double get() = (alto + basso) / 2.0
    val altezza: Int get() = (basso - alto).coerceAtLeast(1)
}

/**
 * L'OCR restituisce blocchi sparsi: nei cedolini l'etichetta ("Netto in busta") e l'importo
 * finiscono spesso in blocchi diversi. Qui si ricompongono le righe visive della pagina
 * raggruppando i frammenti alla stessa altezza e ordinandoli da sinistra a destra.
 * Le colonne sono separate da due spazi.
 */
fun ricomponiRighe(frammenti: List<Frammento>): String {
    if (frammenti.isEmpty()) return ""
    val altezze = frammenti.map { it.altezza }.sorted()
    val altezzaTipica = altezze[altezze.size / 2]
    val ordinati = frammenti.sortedBy { it.centroY }
    val righe = mutableListOf<MutableList<Frammento>>()
    for (f in ordinati) {
        val corrente = righe.lastOrNull()
        val centroRiga = corrente?.map { it.centroY }?.average()
        if (corrente != null && centroRiga != null && kotlin.math.abs(f.centroY - centroRiga) <= altezzaTipica * 0.55) {
            corrente += f
        } else {
            righe += mutableListOf(f)
        }
    }
    return righe.joinToString("\n") { riga -> riga.sortedBy { it.sinistra }.joinToString("  ") { it.testo.trim() } }
}

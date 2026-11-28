// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dominio

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

enum class Gravita { INFO, AVVISO, CRITICO }

data class Avviso(val gravita: Gravita, val titolo: String, val dettaglio: String)

data class Confronto(val voce: String, val attuale: Double?, val precedente: Double?, val unita: String = "€") {
    val delta: Double? get() = if (attuale != null && precedente != null) attuale - precedente else null
    val deltaPercentuale: Double?
        get() = delta?.let { d -> precedente?.takeIf { abs(it) > 0.009 }?.let { d / abs(it) * 100.0 } }
}

/** Stima indicativa dell'IRPEF mensile: NON sostituisce il calcolo del sostituto d'imposta. */
data class StimaIrpef(
    val imponibileMensile: Double,
    val imponibileAnnuo: Double,
    val irpefLordaAnnua: Double,
    val detrazioneLavoroAnnua: Double,
    val irpefMensileStimata: Double,
    val irpefDaCedolino: Double?,
) {
    val differenza: Double? get() = irpefDaCedolino?.let { it - irpefMensileStimata }
}

data class Impostazioni(
    /** Variazione percentuale oltre la quale netto/lordo/tasse generano un avviso. */
    val sogliaVariazione: Double = 10.0,
    /** Giorni di preavviso per la scadenza del contratto. */
    val giorniPreavvisoScadenza: Int = 60,
)

object Analisi {

    private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Il cedolino ordinario precedente (le mensilità aggiuntive si confrontano tra loro). */
    fun precedente(c: Cedolino, tutti: List<Cedolino>): Cedolino? =
        tutti.filter { it.id != c.id && it.aggiuntiva == c.aggiuntiva && it.chiavePeriodo < c.chiavePeriodo }
            .maxByOrNull { it.chiavePeriodo }

    fun confronto(c: Cedolino, prec: Cedolino?): List<Confronto> = listOf(
        Confronto("Lordo", c.lordo, prec?.lordo),
        Confronto("Netto", c.netto, prec?.netto),
        Confronto("Tasse e contributi", c.totaleTasseEContributi, prec?.totaleTasseEContributi),
        Confronto("IRPEF", c.irpef, prec?.irpef),
        Confronto("Ferie residue", c.ferieResidue, prec?.ferieResidue, c.unitaFerie),
        Confronto("ROL residui", c.rolResidui, prec?.rolResidui, c.unitaRol),
        Confronto("Straordinario", c.straordinarioEffettuate, prec?.straordinarioEffettuate, "ore"),
    )

    fun parseData(s: String?): LocalDate? = try {
        s?.let { LocalDate.parse(it, FORMATO_DATA) }
    } catch (_: Exception) {
        null
    }

    fun avvisi(c: Cedolino, prec: Cedolino?, oggi: LocalDate, imp: Impostazioni = Impostazioni()): List<Avviso> {
        val out = mutableListOf<Avviso>()
        val soglia = imp.sogliaVariazione

        // --- dati mancanti
        if (c.lordo == null || c.netto == null) {
            out += Avviso(Gravita.INFO, "Dati principali incompleti",
                "Lordo o netto non riconosciuti nel documento: completali a mano per avere confronti e controlli.")
        }

        // --- coerenza interna
        if (c.lordo != null && c.netto != null) {
            if (c.netto > c.lordo + 0.01) {
                out += Avviso(Gravita.AVVISO, "Netto superiore al lordo",
                    "Il netto (${euro(c.netto)}) supera il lordo (${euro(c.lordo)}): possibili rimborsi, conguagli a credito o un valore letto male.")
            }
            val trattenute = c.totaleTasseEContributi
            if (trattenute != null) {
                val scarto = c.lordo - trattenute - c.netto
                if (abs(scarto) > max(10.0, c.lordo * 0.03)) {
                    val verso = if (scarto > 0) "trattenute" else "somme aggiunte"
                    out += Avviso(Gravita.INFO, "Altre voci non riepilogate",
                        "Lordo − tasse e contributi − netto = ${euro(scarto)}: ci sono $verso non classificate (es. TFR in busta, buoni pasto, " +
                            "rimborsi, trattenute sindacali, prestiti, arrotondamenti).")
                }
            }
        }

        // --- variazioni rispetto al mese precedente
        if (prec != null) {
            fun variazione(nome: String, a: Double?, b: Double?, gravita: Gravita) {
                if (a == null || b == null || abs(b) < 0.01) return
                val perc = (a - b) / abs(b) * 100.0
                if (abs(perc) >= soglia) {
                    val segno = if (perc > 0) "+" else ""
                    out += Avviso(gravita, "$nome ${if (perc > 0) "in aumento" else "in calo"} del $segno${"%.1f".format(java.util.Locale.ITALY, perc)}%",
                        "${nome}: ${euro(a)} contro ${euro(b)} di ${prec.etichettaPeriodo} (${segno}${euro(a - b)}).")
                }
            }
            variazione("Netto", c.netto, prec.netto, Gravita.AVVISO)
            variazione("Lordo", c.lordo, prec.lordo, Gravita.INFO)
            variazione("Tasse e contributi", c.totaleTasseEContributi, prec.totaleTasseEContributi, Gravita.INFO)

            if (!c.livello.isNullOrBlank() && !prec.livello.isNullOrBlank() && !c.livello.equals(prec.livello, true)) {
                out += Avviso(Gravita.INFO, "Livello cambiato", "Da ${prec.livello} a ${c.livello}: verifica che la retribuzione sia stata adeguata.")
            }
            if (!c.tipoContratto.isNullOrBlank() && !prec.tipoContratto.isNullOrBlank() && !c.tipoContratto.equals(prec.tipoContratto, true)) {
                out += Avviso(Gravita.INFO, "Tipo di contratto cambiato", "Da ${prec.tipoContratto} a ${c.tipoContratto}.")
            }
            if (!c.ccnl.isNullOrBlank() && !prec.ccnl.isNullOrBlank() && !c.ccnl.equals(prec.ccnl, true)) {
                out += Avviso(Gravita.INFO, "CCNL cambiato", "Da ${prec.ccnl} a ${c.ccnl}.")
            }
            val ferieAtt = c.ferieResidue
            val feriePrec = prec.ferieResidue
            if (ferieAtt != null && feriePrec != null && c.unitaFerie == prec.unitaFerie && c.ferieGodute == null && ferieAtt < feriePrec - 0.01 && c.ferieMaturate == null) {
                out += Avviso(Gravita.INFO, "Ferie residue in calo",
                    "Da ${num(feriePrec)} a ${num(ferieAtt)} ${c.unitaFerie}.")
            }
        }

        // --- ferie e ROL
        fun controllaMgr(nome: String, mat: Double?, god: Double?, res: Double?, unita: String) {
            if (res != null && res < -0.01) {
                out += Avviso(Gravita.AVVISO, "$nome in negativo", "Residuo di ${num(res)} $unita: hai usato più di quanto maturato.")
            }
            if (mat != null && god != null && res != null && abs(mat - god - res) > 0.5) {
                out += Avviso(Gravita.INFO, "$nome: conti da verificare",
                    "Maturati ${num(mat)} − goduti ${num(god)} = ${num(mat - god)}, ma il residuo indicato è ${num(res)} (può includere l'anno precedente).")
            }
        }
        controllaMgr("Ferie", c.ferieMaturate, c.ferieGodute, c.ferieResidue, c.unitaFerie)
        controllaMgr("ROL", c.rolMaturati, c.rolGoduti, c.rolResidui, c.unitaRol)
        val sogliaFerie = if (c.unitaFerie == Cedolino.UNITA_ORE) 240.0 else 30.0
        if ((c.ferieResidue ?: 0.0) > sogliaFerie) {
            out += Avviso(Gravita.INFO, "Molte ferie accumulate",
                "${num(c.ferieResidue!!)} ${c.unitaFerie} residui: le ferie non godute entro 18 mesi dall'anno di maturazione possono creare problemi. Pianificale.")
        }

        // --- straordinario
        val eff = c.straordinarioEffettuate
        if (eff != null) {
            val coperte = (c.straordinarioPagate ?: 0.0) + (c.straordinarioAccantonate ?: 0.0)
            if (c.straordinarioPagate != null && eff - coperte > 0.5) {
                out += Avviso(Gravita.AVVISO, "Straordinario non tutto riconosciuto",
                    "Effettuate ${num(eff)} ore, pagate o accantonate ${num(coperte)}: mancano ${num(eff - coperte)} ore.")
            }
        }

        // --- scadenza contratto
        parseData(c.scadenza)?.let { s ->
            val giorni = ChronoUnit.DAYS.between(oggi, s)
            when {
                giorni < 0 -> out += Avviso(Gravita.CRITICO, "Contratto scaduto",
                    "La scadenza indicata (${c.scadenza}) è passata da ${-giorni} giorni: verifica proroga o rinnovo.")
                giorni <= imp.giorniPreavvisoScadenza -> out += Avviso(Gravita.AVVISO, "Contratto in scadenza",
                    "Scade il ${c.scadenza}, tra $giorni giorni.")
            }
        }

        // --- tasse molto diverse dalla stima
        stimaIrpef(c)?.differenza?.let { diff ->
            val base = max(50.0, (c.irpef ?: 0.0) * 0.5)
            if (abs(diff) > base && !c.aggiuntiva) {
                out += Avviso(Gravita.INFO, "IRPEF lontana dalla stima",
                    "L'IRPEF in busta differisce di ${euro(diff)} dalla stima indicativa. Può dipendere da conguagli, detrazioni per familiari, " +
                        "bonus o variazioni dell'imponibile progressivo: chiedi conferma all'ufficio paghe se non ti torna.")
            }
        }

        return out.sortedByDescending { it.gravita.ordinal }
    }

    // ---------------------------------------------------------------- stima IRPEF

    /**
     * Stima indicativa: annualizza l'imponibile del mese (×12), applica gli scaglioni IRPEF 2026
     * (23% fino a 28.000 €, 33% fino a 50.000 €, 43% oltre) e la detrazione per lavoro dipendente
     * (art. 13 TUIR) per un anno intero. Non considera familiari a carico, cuneo fiscale, bonus,
     * conguagli, tredicesima né l'imponibile progressivo reale.
     */
    fun stimaIrpef(c: Cedolino): StimaIrpef? {
        if (c.aggiuntiva) return null
        val imponibile = c.imponibileFiscale
            ?: if (c.lordo != null && c.contributiPrevidenziali != null) c.lordo - c.contributiPrevidenziali else null
        if (imponibile == null || imponibile <= 0) return null
        val annuo = imponibile * 12
        val lorda = irpefLorda(annuo)
        val detrazione = detrazioneLavoroDipendente(annuo)
        val netta = max(0.0, lorda - detrazione)
        return StimaIrpef(imponibile, annuo, lorda, detrazione, netta / 12.0, c.irpef)
    }

    fun irpefLorda(reddito: Double): Double {
        var imposta = 0.0
        val scaglioni = listOf(28_000.0 to 0.23, 50_000.0 to 0.33, Double.MAX_VALUE to 0.43)
        var inferiore = 0.0
        for ((superiore, aliquota) in scaglioni) {
            if (reddito <= inferiore) break
            imposta += (minOf(reddito, superiore) - inferiore) * aliquota
            inferiore = superiore
        }
        return imposta
    }

    fun detrazioneLavoroDipendente(reddito: Double): Double {
        val base = when {
            reddito <= 15_000 -> 1_955.0
            reddito <= 28_000 -> 1_910.0 + 1_190.0 * (28_000 - reddito) / 13_000
            reddito <= 50_000 -> 1_910.0 * (50_000 - reddito) / 22_000
            else -> 0.0
        }
        val maggiorazione = if (reddito > 25_000 && reddito <= 35_000) 65.0 else 0.0
        return base + maggiorazione
    }

    // ---------------------------------------------------------------- formattazione

    fun euro(v: Double): String {
        val s = "%,.2f".format(java.util.Locale.ITALY, abs(v))
        return (if (v < 0) "−" else "") + "€ " + s
    }

    fun num(v: Double): String =
        if (abs(v - Math.round(v)) < 0.005) "%d".format(Math.round(v)) else "%,.2f".format(java.util.Locale.ITALY, v)
}

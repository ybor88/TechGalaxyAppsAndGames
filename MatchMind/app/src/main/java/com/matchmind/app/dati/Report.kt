package com.matchmind.app.dati

import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.Leghe
import com.matchmind.app.dominio.Scommessa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.etichettaSport
import com.matchmind.app.dominio.sportDi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Report di una bolletta persa, in testo semplice da condividere (WhatsApp, email, chat): tutti i pronostici
 * con risultato, probabilità e fonti, quale ha fatto perdere e perché, più un'analisi automatica.
 * Serve a capire dove sbaglia il metodo e migliorarlo.
 *
 * © Roberto Di Flumeri
 */
object Report {

    private val lingua = Locale.ITALY
    private fun euro(v: Double) = String.format(lingua, "%.2f €", v)
    private fun quota(v: Double) = String.format(lingua, "%.2f", v)
    private fun perc(v: Double) = String.format(lingua, "%.0f%%", v * 100)
    private fun perc1(v: Double) = String.format(lingua, "%.1f%%", v * 100)

    /** Il report della bolletta, o null se non è persa. */
    fun sconfitta(b: SchedinaCompleta, zona: ZoneId = ZoneId.systemDefault()): String? {
        val s = b.schedina
        if (s.stato != StatoSchedina.PERSA.name) return null
        val data = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", lingua).withZone(zona)
        fun ora(t: Long) = data.format(Instant.ofEpochMilli(t))
        val sport = etichettaSport(sportDi(s.sport))
        val selezioni = b.selezioni.sortedBy { it.inizio }
        val perse = selezioni.filter { it.esito == EsitoSelezione.PERSA.name }

        return buildString {
            appendLine("MatchMind · Report bolletta persa")
            appendLine("Bolletta n. ${s.id} · $sport · giocata il ${ora(s.creataIl)}" + (s.chiusaIl?.let { " · chiusa il ${ora(it)}" } ?: ""))
            appendLine("Puntata ${euro(s.puntata)} · quota ${quota(s.quotaTotale)} · vincita mancata ${euro(s.puntata * s.quotaTotale)}")
            appendLine("Probabilità stimata di vincerla: ${perc1(s.probabilita)}")
            appendLine()
            appendLine("Pronostici (${selezioni.size}):")
            for (sel in selezioni) {
                val segno = when (sel.esito) {
                    EsitoSelezione.VINTA.name -> "✓"
                    EsitoSelezione.PERSA.name -> "✗"
                    EsitoSelezione.NULLA.name -> "○"
                    else -> "…"
                }
                appendLine("$segno ${Leghe.nome(sel.lega)} · ${ora(sel.inizio)} · ${sel.casa} – ${sel.ospite}")
                val scelta = Scommessa.di(sel.scelta)?.descrivi(sel.casa, sel.ospite) ?: sel.scelta
                appendLine("   Giocato: $scelta · risultato ${sel.risultato ?: "non disponibile"} → " + when (sel.esito) {
                    EsitoSelezione.VINTA.name -> "indovinato"
                    EsitoSelezione.PERSA.name -> "SBAGLIATO"
                    EsitoSelezione.NULLA.name -> "annullato"
                    else -> "in attesa"
                })
                appendLine("   Quota ${quota(sel.quota)}" + (if (sel.quotaStimata) " (stimata)" else "") +
                    " · probabilità ${perc(sel.probabilita)}" +
                    (sel.pMercato?.let { " · mercato ${perc(it)}" } ?: "") +
                    (sel.pModello?.let { " · statistica ${perc(it)}" } ?: ""))
                sel.storico?.let { appendLine("   Affidabilità: $it") }
            }
            appendLine()
            appendLine("Analisi automatica:")
            for (sel in perse) appendLine("- ${diagnosi(sel, selezioni.size > 1 && sel.probabilita <= selezioni.minOf { it.probabilita })}")
            if (perse.isEmpty()) appendLine("- Nessun pronostico risulta sbagliato: controllare i dati della partita.")
            appendLine()
            append("© Roberto Di Flumeri")
        }
    }

    /** Perché un pronostico è andato male, in una riga. */
    internal fun diagnosi(sel: SelezioneEntity, ilMenoProbabile: Boolean): String {
        val scelta = Scommessa.di(sel.scelta)
        val gol = sel.risultato?.split('-')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }
        val partita = "${sel.casa} – ${sel.ospite}"
        val cosa = if (gol == null || scelta == null) "risultato non disponibile" else {
            val (c, o) = gol
            when (scelta) {
                Scommessa.CASA -> if (c == o) "pareggio $c-$o: ${sel.casa} favorita ma non ha vinto" else "ha vinto ${sel.ospite} $c-$o: sorpresa completa"
                Scommessa.OSPITE -> if (c == o) "pareggio $c-$o: ${sel.ospite} favorita ma non ha vinto" else "ha vinto ${sel.casa} $c-$o: sorpresa completa"
                Scommessa.CASA_O_PAREGGIO -> "ha vinto ${sel.ospite} $c-$o: ${sel.casa} ha perso, cosa che succedeva di rado"
                Scommessa.PAREGGIO_O_OSPITE -> "ha vinto ${sel.casa} $c-$o: ${sel.ospite} ha perso, cosa che succedeva di rado"
                Scommessa.CASA_O_OSPITE -> "pareggio $c-$o"
                Scommessa.PAREGGIO -> "non è finita in parità ($c-$o)"
                Scommessa.GOAL -> "non hanno segnato entrambe ($c-$o)"
                Scommessa.NO_GOAL -> "hanno segnato entrambe ($c-$o)"
                Scommessa.OVER -> "solo ${c + o} gol ($c-$o), ne servivano almeno 3"
                Scommessa.UNDER -> "${c + o} gol ($c-$o), al massimo 2 per l'Under"
            }
        }
        val note = buildList {
            val p = sel.probabilita
            if (p in 0.0..0.999) add("con il ${perc(p)} capita circa 1 volta su ${(1 / (1 - p)).roundToInt()}")
            val m = sel.pMercato
            val st = sel.pModello
            if (m != null && st != null && abs(m - st) >= 0.08) add("mercato (${perc(m)}) e statistica (${perc(st)}) non erano d'accordo")
            if (ilMenoProbabile) add("era il pronostico meno probabile della bolletta")
            if (sel.quotaStimata) add("quota stimata, non del bookmaker")
            if (sel.storico == null) add("bolletta vecchia, senza dati di affidabilità")
        }
        return "Ha fatto perdere $partita (${Leghe.nome(sel.lega)}): $cosa" + if (note.isEmpty()) "." else ". " + note.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
    }

    /** Tutte le bollette perse, dalla più recente, in un unico testo. */
    fun tutte(bollette: List<SchedinaCompleta>, zona: ZoneId = ZoneId.systemDefault()): String =
        bollette.filter { it.schedina.stato == StatoSchedina.PERSA.name }
            .sortedByDescending { it.schedina.chiusaIl ?: it.schedina.creataIl }
            .mapNotNull { sconfitta(it, zona) }
            .joinToString("\n\n────────────\n\n")
}

package com.scouttable.app.data.lookup

private val wikiLinkRegex = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")
private val refRegex = Regex("""<ref[^>]*>.*?</ref>""", RegexOption.DOT_MATCHES_ALL)
// Le graffe vanno escapate sia in apertura che in chiusura: lasciarle "nude" in chiusura
// (com'era prima) fa fallire la compilazione della regex su Android con PatternSyntaxException.
private val templateRegex = Regex("""\{\{[^}]*\}\}""")
private val parenRegex = Regex("""\([^)]*\)""")
private val htmlCommentRegex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

/**
 * Frammento di regex per il "valore" di un campo infobox (es. dopo "clubs9 = "): molti articoli
 * (verificato su Christian Vieri) elencano più campi sulla stessa riga separati da "|"
 * ("clubs9 = [[Inter Milan]]  |caps9 = 143 |goals9 = 103"). Catturare fino a fine riga con un
 * generico ".+" (com'era prima) include anche i campi successivi nel valore, producendo nomi
 * club "sporchi" (es. "Inter Milan               |caps9  = 143 |goals9  = 103") che TheSportsDB
 * non trova mai, mostrando la foto del giocatore al posto dello stemma. Questo frammento si ferma
 * al primo "|" che non fa parte di un wikilink/template/ref (dove "|" ha un significato diverso,
 * es. "[[AS Roma|Roma]]"), consumando quei blocchi come un'unica unità.
 * Graffe di chiusura escapate ("\\}\\}", non "}}") per lo stesso motivo di [templateRegex] sopra:
 * lasciarle "nude" fa fallire PatternSyntaxException sul motore regex ICU di Android (non sul
 * java.util.regex del JDK desktop, dove compila silenziosamente) — bug che ha reso completamente
 * inutilizzabile la ricerca automatica sia per basket sia per calcio (entrambi usano questa
 * costante) finché non veniva intercettato con un vero test su dispositivo/emulatore.
 */
internal const val INFOBOX_VALUE = """(?:\[\[[^\]]*]]|\{\{[^}]*\}\}|<ref[^>]*>.*?</ref>|[^|\n])*"""

/**
 * Frammento di regex per un valore numerico di infobox (es. "goals3 = 13"): i giocatori con un
 * conteggio di gol/presenze abbastanza notevole da avere una pagina dedicata (es. "nationalgoals3
 * = [[List of international goals scored by Kylian Mbappé|66]]" per Mbappé, o l'equivalente per
 * Cristiano Ronaldo) riportano il numero come TESTO VISIBILE di un wikilink invece che come cifra
 * nuda: un pattern che richiede "\d+" subito dopo "=" non trova nulla per quell'indice e lo somma
 * come zero, azzerando silenziosamente proprio i giocatori più prolifici in Nazionale. Il prefisso
 * opzionale "[[...|" consuma il link fino al "|" prima di catturare la cifra, che resta nel gruppo
 * di cattura in entrambi i casi (numero nudo o wikilink).
 */
internal const val INFOBOX_NUMBER = """(?:\[\[[^\]|]*\|)?(\d+)\]{0,2}"""

/** "[[AS Roma|Roma]] (loan)" -> "Roma". Rimuove markup wiki, riferimenti e note tra parentesi. */
internal fun cleanWikiText(raw: String): String {
    var s = wikiLinkRegex.replace(raw) { m -> m.groupValues[2].ifBlank { m.groupValues[1] } }
    s = s.replace(refRegex, "")
    s = s.replace(templateRegex, "")
    s = s.replace(parenRegex, "")
    return s.trim()
}

/**
 * Rimuove i commenti HTML (`<!-- ... -->`) dal wikitext prima di applicare i regex sui campi
 * dell'infobox: articoli molto modificati/protetti (es. Alessandro Del Piero) inseriscono commenti
 * proprio tra "=" e il valore (es. "caps2 = <!-- nota -->513"), il che fa fallire silenziosamente
 * il match del numero e fa sparire quell'indice dai risultati (scelta del club sbagliata).
 */
internal fun stripHtmlComments(raw: String): String = raw.replace(htmlCommentRegex, "")

/**
 * Come regex.find(wikitext) sul gruppo 1, ma scorre tutte le occorrenze e prende la prima che,
 * ripulita, non è vuota: alcuni infobox (es. Carlton Myers, Gregor Fučka) hanno un primo campo
 * "position = " lasciato vuoto e il valore vero solo in un secondo campo omonimo più avanti
 * ("career_position = ..."), che .find() da solo non troverebbe mai (si ferma al primo, vuoto).
 */
internal fun firstNonBlank(regex: Regex, wikitext: String): String? =
    regex.findAll(wikitext).map { cleanWikiText(it.groupValues[1]) }.firstOrNull { it.isNotBlank() }

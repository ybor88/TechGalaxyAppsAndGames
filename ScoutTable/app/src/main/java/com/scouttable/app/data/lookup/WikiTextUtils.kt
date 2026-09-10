package com.scouttable.app.data.lookup

private val wikiLinkRegex = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")
private val refRegex = Regex("""<ref[^>]*>.*?</ref>""", RegexOption.DOT_MATCHES_ALL)
// Le graffe vanno escapate sia in apertura che in chiusura: lasciarle "nude" in chiusura
// (com'era prima) fa fallire la compilazione della regex su Android con PatternSyntaxException.
private val templateRegex = Regex("""\{\{[^}]*\}\}""")
private val parenRegex = Regex("""\([^)]*\)""")
private val htmlCommentRegex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

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

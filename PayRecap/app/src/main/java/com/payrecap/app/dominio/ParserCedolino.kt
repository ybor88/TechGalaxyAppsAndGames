// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dominio

import kotlin.math.abs

/**
 * Riconosce le voci di un cedolino italiano a partire dal testo letto dal PDF o dall'OCR.
 *
 * I cedolini non hanno un formato unico (Zucchetti, TeamSystem, INAZ, ADP, paghe interne...):
 * il parser cerca le etichette più diffuse riga per riga, gestisce sia "etichetta  valore" sulla
 * stessa riga sia le tabelle con intestazione sopra e valori sotto, e non inventa nulla:
 * ciò che non trova resta vuoto e l'utente lo può completare nella schermata di revisione.
 */
object ParserCedolino {

    data class Risultato(
        val cedolino: Cedolino,
        val campiRiconosciuti: Int,
        val campiTotali: Int,
        val campiMancanti: List<String>,
        val periodoTrovato: Boolean,
    )

    // ---------------------------------------------------------------- etichette

    private val L_LORDO = listOf(
        "totale competenze", "totale lordo", "retribuzione lorda", "lordo mensile", "totale retribuzione",
        "tot. competenze", "tot competenze", "lordo",
    )
    private val L_NETTO = listOf(
        "netto in busta", "netto a pagare", "netto del mese", "netto da pagare", "netto busta",
        "netto mese", "netto retribuzione", "netto",
    )
    private val L_IMPONIBILE = listOf(
        "imponibile fiscale", "imponibile irpef", "imp. fiscale", "imp.fiscale", "imponibile ai fini irpef",
    )
    private val L_IRPEF = listOf(
        "irpef netta", "ritenute irpef", "ritenuta irpef", "irpef trattenuta", "irpef a debito", "irpef",
    )
    private val L_ADD_REG = listOf(
        "addizionale regionale", "add.le regionale", "addiz. regionale", "add. regionale", "add.le reg",
        "addiz. reg", "addiz.reg", "add. reg", "add.reg", "add reg",
    )
    private val L_ADD_COM = listOf(
        "addizionale comunale", "add.le comunale", "addiz. comunale", "add. comunale", "add.le com",
        "addiz. com", "addiz.com", "add. com", "add.com", "add com",
    )
    private val L_CONTRIBUTI = listOf(
        "totale contributi", "contributi previdenziali", "ritenute previdenziali", "contributi inps",
        "contributo ivs", "contrib. ivs", "contributi c/dip", "inps c/dip", "ritenute sociali", "contributi",
    )

    private val ESCLUDI_IMPORTI = listOf("progr", "annuo", "anno prec", "a.p.")

    /** Tutte le etichette di importo: servono a capire la colonna nelle tabelle "intestazione sopra, valori sotto". */
    private val LESSICO_INTESTAZIONI = (L_LORDO + L_NETTO + L_IMPONIBILE + L_IRPEF + L_ADD_REG + L_ADD_COM + L_CONTRIBUTI +
        listOf("totale trattenute", "arrotondamento", "tfr", "rimborsi")).distinct()

    // ---------------------------------------------------------------- regex di servizio

    private val RE_DATA = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
    private val RE_FRAZIONE = Regex("""\b\d{1,2}/\d{1,2}\b""")
    private val RE_PERCENTUALE = Regex("""-?\d+(?:[.,]\d+)?\s*%""")
    private val RE_NUMERO = Regex("""(?<![\w])-?\d+(?:[.,]\d+)*(?![\w])""")
    private val RE_MESE_ANNO = Regex(
        """\b(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\s*[-/]?\s*(20\d{2})\b""",
    )
    private val RE_MESE_BREVE_ANNO = Regex("""\b(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)\.?\s*[-/]?\s*(20\d{2})\b""")
    private val RE_MM_AAAA = Regex("""\b(0?[1-9]|1[0-4])\s*[/\-]\s*(20\d{2})\b""")
    private val RE_ROL = Regex("""\br\.?\s?o\.?\s?l\.?(?![a-z])""")
    private val RE_PERMESSI = Regex("""\b(permessi|riduzione orario|rid\. orario)""")

    // ---------------------------------------------------------------- API

    fun analizza(testo: String, annoDefault: Int, meseDefault: Int, nomeFile: String? = null): Risultato {
        val originali = normalizza(testo)
        val righe = originali.map { it.lowercase() }

        val periodo = trovaPeriodo(righe)
        val aggiuntiva = periodo?.third == true || trovaAggiuntiva(righe)

        val ferie = trovaMaturatiGodutiResidui(
            righe,
            rigaChiave = { it.contains("ferie") && !it.contains("indennit") && !it.contains("rateo") },
            residue = listOf("ferie residue", "residuo ferie", "ferie residuo", "saldo ferie", "ferie saldo", "ferie da godere", "ferie res"),
            maturate = listOf("ferie maturate", "maturato ferie", "ferie maturato", "ferie spettanti", "ferie matur"),
            godute = listOf("ferie godute", "ferie goduto", "goduto ferie", "ferie fruite", "ferie usufruite", "ferie god"),
        )
        val rol = trovaMaturatiGodutiResidui(
            righe,
            rigaChiave = { (RE_ROL.containsMatchIn(it) || RE_PERMESSI.containsMatchIn(it)) && !it.contains("indennit") },
            residue = listOf("rol residui", "residuo rol", "rol residuo", "saldo rol", "permessi residui", "residuo permessi", "rol res"),
            maturate = listOf("rol maturati", "maturato rol", "rol maturato", "permessi maturati", "rol matur"),
            godute = listOf("rol goduti", "rol goduto", "goduto rol", "rol fruiti", "permessi goduti", "permessi fruiti", "rol god"),
        )

        val straord = trovaStraordinario(righe)

        val c = Cedolino(
            mese = periodo?.first ?: meseDefault,
            anno = periodo?.second ?: annoDefault,
            aggiuntiva = aggiuntiva,
            azienda = trovaTesto(originali, righe, listOf("ragione sociale", "datore di lavoro", "azienda", "ditta")),
            nomeFile = nomeFile,
            lordo = importo(righe, L_LORDO, ESCLUDI_IMPORTI + listOf("imponibile", "progressiv")),
            netto = importo(righe, L_NETTO, ESCLUDI_IMPORTI + listOf("irpef", "arrotond")),
            imponibileFiscale = importo(righe, L_IMPONIBILE, ESCLUDI_IMPORTI),
            irpef = importo(righe, L_IRPEF, ESCLUDI_IMPORTI + listOf("imponib", "lorda", "detraz", "addiz", "add.", "add ", "credito", "rimborso")),
            addizionaleRegionale = importo(righe, L_ADD_REG, ESCLUDI_IMPORTI + listOf("imponib", "residu", "da trattenere"), somma = true),
            addizionaleComunale = importo(righe, L_ADD_COM, ESCLUDI_IMPORTI + listOf("imponib", "residu", "da trattenere"), somma = true),
            contributiPrevidenziali = importo(righe, L_CONTRIBUTI, ESCLUDI_IMPORTI + listOf("imponib", "azienda", "ditta", "c/az")),
            tipoContratto = trovaTipoContratto(righe.joinToString(" ")),
            ccnl = trovaCcnl(originali, righe),
            livello = trovaLivello(righe),
            scadenza = trovaScadenza(righe),
            ferieMaturate = ferie.maturati,
            ferieGodute = ferie.goduti,
            ferieResidue = ferie.residui,
            unitaFerie = ferie.unita ?: if ((ferie.residui ?: ferie.maturati ?: 0.0) > 45.0) Cedolino.UNITA_ORE else Cedolino.UNITA_GIORNI,
            rolMaturati = rol.maturati,
            rolGoduti = rol.goduti,
            rolResidui = rol.residui,
            unitaRol = rol.unita ?: Cedolino.UNITA_ORE,
            straordinarioEffettuate = straord.effettuate,
            straordinarioPagate = straord.pagate,
            straordinarioAccantonate = straord.accantonate,
            bancaOreResidua = straord.bancaOre,
            testoEstratto = testo.take(20_000),
        )

        val controlli = listOf(
            "Lordo" to c.lordo, "Netto" to c.netto, "IRPEF" to c.irpef,
            "Addizionale regionale" to c.addizionaleRegionale, "Addizionale comunale" to c.addizionaleComunale,
            "Contributi" to c.contributiPrevidenziali, "Tipo di contratto" to c.tipoContratto, "CCNL" to c.ccnl,
            "Livello" to c.livello, "Ferie" to (c.ferieResidue ?: c.ferieMaturate), "ROL" to (c.rolResidui ?: c.rolMaturati),
        )
        val mancanti = controlli.filter { it.second == null }.map { it.first }
        return Risultato(c, controlli.size - mancanti.size, controlli.size, mancanti, periodo != null)
    }

    // ---------------------------------------------------------------- numeri

    /** Converte un numero scritto all'italiana ("1.234,56") o all'inglese ("1,234.56" / "12.5"). */
    fun parseNumero(token: String): Double? {
        var s = token.trim().replace(" ", "")
        if (s.isEmpty()) return null
        val negativo = s.startsWith("-") || s.endsWith("-")
        s = s.trim('-')
        val ultimaVirgola = s.lastIndexOf(',')
        val ultimoPunto = s.lastIndexOf('.')
        s = when {
            ultimaVirgola >= 0 && ultimoPunto >= 0 ->
                if (ultimaVirgola > ultimoPunto) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            ultimaVirgola >= 0 -> {
                val dopo = s.length - ultimaVirgola - 1
                if (s.count { it == ',' } == 1 && dopo != 3) s.replace(',', '.')
                else if (dopo == 3) s.replace(",", "") else s.substring(0, ultimaVirgola).replace(",", "") + "." + s.substring(ultimaVirgola + 1)
            }
            ultimoPunto >= 0 -> {
                val dopo = s.length - ultimoPunto - 1
                if (s.count { it == '.' } > 1 || dopo == 3) s.replace(".", "") else s
            }
            else -> s
        }
        val v = s.toDoubleOrNull() ?: return null
        return if (negativo) -v else v
    }

    /** Numeri presenti nella stringa, ignorando date, frazioni ("rata 3/11") e percentuali. */
    fun numeri(s: String): List<Double> {
        val pulita = s.replace(RE_DATA, " ").replace(RE_FRAZIONE, " ").replace(RE_PERCENTUALE, " ")
        return RE_NUMERO.findAll(pulita).mapNotNull { parseNumero(it.value) }.toList()
    }

    private fun haDecimali(token: String) = Regex("""[.,]\d{2}$""").containsMatchIn(token)

    /** Numeri "monetari" (con due decimali) se presenti, altrimenti tutti. */
    private fun importiIn(s: String): List<Double> {
        val pulita = s.replace(RE_DATA, " ").replace(RE_FRAZIONE, " ").replace(RE_PERCENTUALE, " ")
        val token = RE_NUMERO.findAll(pulita).map { it.value }.toList()
        val monetari = token.filter { haDecimali(it) }
        return (monetari.ifEmpty { token }).mapNotNull { parseNumero(it) }
    }

    // ---------------------------------------------------------------- normalizzazione

    private fun normalizza(testo: String): List<String> = testo
        .replace(' ', ' ').replace('\t', ' ').replace("€", " ").replace("EUR ", " ")
        .replace('’', '\'').replace('–', '-').replace('—', '-')
        .lines()
        .map { it.replace(Regex(""" {2,}"""), "  ").trim() }
        .filter { it.isNotBlank() }

    // ---------------------------------------------------------------- importi

    private fun trovaEtichetta(riga: String, etichetta: String): Int {
        // l'etichetta deve iniziare a inizio parola ("netto" non deve trovare "lordonetto")
        var from = 0
        while (true) {
            val i = riga.indexOf(etichetta, from)
            if (i < 0) return -1
            if (i == 0 || !riga[i - 1].isLetterOrDigit()) return i
            from = i + 1
        }
    }

    /**
     * Cerca l'importo associato a una delle etichette, in ordine di priorità.
     * Con [somma] = true somma tutte le righe trovate (es. addizionale comunale saldo + acconto).
     */
    fun importo(righe: List<String>, etichette: List<String>, escludi: List<String> = emptyList(), somma: Boolean = false): Double? {
        val valori = mutableListOf<Double>()
        val righeUsate = mutableSetOf<Int>()
        for (etichetta in etichette) {
            for ((i, riga) in righe.withIndex()) {
                if (i in righeUsate) continue
                val pos = trovaEtichetta(riga, etichetta)
                if (pos < 0) continue
                if (escludi.any { riga.contains(it) }) continue
                val dopo = finoAllaProssimaEtichetta(riga.substring(pos + etichetta.length))
                val stessaRiga = importiIn(dopo)
                val v = if (stessaRiga.isNotEmpty()) {
                    stessaRiga.last()
                } else {
                    valoreSottoIntestazione(riga, pos, righe.getOrNull(i + 1))
                }
                if (v != null) {
                    if (!somma) return v
                    valori += v
                    righeUsate += i
                }
            }
        }
        return if (somma && valori.isNotEmpty()) Math.round(valori.sum() * 100) / 100.0 else null
    }

    /**
     * "Totale competenze 2.216,80  Totale trattenute 535,78": il valore di un'etichetta finisce
     * dove inizia l'etichetta successiva sulla stessa riga.
     */
    private fun finoAllaProssimaEtichetta(dopo: String): String {
        var fine = dopo.length
        for (e in LESSICO_INTESTAZIONI) {
            val i = trovaEtichetta(dopo, e)
            // solo etichette che seguono almeno un numero: "IRPEF netta 498,57" resta intera
            if (i > 0 && i < fine && dopo.substring(0, i).any { it.isDigit() }) fine = i
        }
        return dopo.substring(0, fine)
    }

    /** Tabelle con le etichette su una riga e gli importi sulla riga sotto, nello stesso ordine. */
    private fun valoreSottoIntestazione(riga: String, pos: Int, rigaSotto: String?): Double? {
        if (rigaSotto == null) return null
        // la riga sotto deve essere "solo numeri" (al più qualche simbolo)
        if (rigaSotto.count { it.isLetter() } > 3) return null
        val valori = importiIn(rigaSotto)
        if (valori.isEmpty()) return null
        val posizioni = mutableListOf<IntRange>()
        for (e in LESSICO_INTESTAZIONI.sortedByDescending { it.length }) {
            var from = 0
            while (true) {
                val i = trovaEtichetta(riga.substring(from), e).let { if (it < 0) -1 else it + from }
                if (i < 0) break
                val r = i until i + e.length
                if (posizioni.none { it.first <= r.last && r.first <= it.last }) posizioni += r
                from = i + e.length
                if (from >= riga.length) break
            }
        }
        posizioni.sortBy { it.first }
        val colonna = posizioni.indexOfFirst { pos in it }
        return when {
            colonna < 0 -> valori.last()
            valori.size == posizioni.size -> valori[colonna]
            colonna < valori.size && valori.size > 1 -> valori[colonna]
            valori.size == 1 -> valori.first()
            else -> null
        }
    }

    // ---------------------------------------------------------------- periodo

    /** (mese, anno, aggiuntiva) */
    private fun trovaPeriodo(righe: List<String>): Triple<Int, Int, Boolean>? {
        val chiavi = listOf("periodo", "mese di", "competenza", "retribuzione di", "cedolino", "busta paga", "mese")
        val candidate = righe.filter { r -> chiavi.any { r.contains(it) } } + righe
        for (r in candidate) {
            RE_MESE_ANNO.find(r)?.let { m ->
                val mese = meseDaNome(m.groupValues[1]) ?: return@let
                return Triple(mese, m.groupValues[2].toInt(), false)
            }
        }
        for (r in righe.filter { r -> chiavi.any { r.contains(it) } }) {
            RE_MESE_BREVE_ANNO.find(r)?.let { m ->
                val mese = meseDaNome(m.groupValues[1]) ?: return@let
                return Triple(mese, m.groupValues[2].toInt(), false)
            }
            RE_MM_AAAA.find(r.replace(RE_DATA, " "))?.let { m ->
                val n = m.groupValues[1].toInt()
                val anno = m.groupValues[2].toInt()
                return when (n) {
                    13 -> Triple(12, anno, true)
                    14 -> Triple(6, anno, true)
                    else -> Triple(n, anno, false)
                }
            }
        }
        return null
    }

    private fun trovaAggiuntiva(righe: List<String>): Boolean {
        val re = Regex("""(tredicesima|quattordicesima|\b1[34]\s*[a^ª°]\s*mensilit|mensilit[aà] aggiuntiva)""")
        return righe.any { r ->
            re.containsMatchIn(r) && !r.contains("rate") && !r.contains("ratei") &&
                listOf("periodo", "cedolino", "mese", "competenza", "busta").any { r.contains(it) }
        }
    }

    // ---------------------------------------------------------------- contratto

    fun trovaTipoContratto(testo: String): String? {
        val t = testo.lowercase()
        val base = when {
            t.contains("indeterminato") -> "Tempo indeterminato"
            Regex("""(tempo determinato|t\.\s?determinato|t\.d\.|a termine)""").containsMatchIn(t) -> "Tempo determinato"
            t.contains("apprendist") -> "Apprendistato"
            t.contains("somministra") -> "Somministrazione"
            t.contains("intermittent") -> "Intermittente"
            else -> null
        }
        val pt = Regex("""part[\s-]?time(?:\s*(?:al\s*)?(\d{1,3}(?:[.,]\d+)?)\s*%)?""").find(t)
        return when {
            base != null && pt != null -> "$base · Part-time" + (pt.groupValues[1].takeIf { it.isNotEmpty() }?.let { " $it%" } ?: "")
            base != null -> base
            pt != null -> "Part-time"
            else -> null
        }
    }

    private fun trovaCcnl(originali: List<String>, righe: List<String>): String? {
        val re = Regex("""(c\.?\s?c\.?\s?n\.?\s?l\.?|contratto collettivo(?: nazionale)?(?: di lavoro)?|contratto applicato)\s*[:\-]?\s*""")
        for ((i, r) in righe.withIndex()) {
            val m = re.find(r) ?: continue
            var valore = originali[i].substring(m.range.last + 1).trim()
            if (valore.length < 3) valore = originali.getOrNull(i + 1)?.trim().orEmpty()
            valore = valore.split(Regex("""(?i)\s{2,}|\b(livello|liv\.|qualifica|mansione|matricola|data|posizione|scadenza)\b""")).first()
                .trim(' ', ':', '-', ',', ';')
            if (valore.length >= 3 && valore.any { it.isLetter() }) return valore.take(60)
        }
        return null
    }

    fun trovaLivello(righe: List<String>): String? {
        val dopo = Regex("""\b(?:livello|liv\.?)\s*[:\-]?\s*(\d{1,2}\s*[°ª^]?\s*(?:super|[a-z])?\b|[a-d]\s?\d\b|[ivx]{1,4}\b|quadro\b|q\b|super\b)""")
        val prima = Regex("""\b(\d{1,2})\s*[°ª^]\s*liv""")
        for (r in righe) {
            prima.find(r)?.let { return "${it.groupValues[1]}°" }
            dopo.find(r)?.let { m ->
                val v = m.groupValues[1].replace(" ", "").replace("^", "°").replace("ª", "°")
                return when {
                    v.first().isDigit() -> {
                        val resto = v.dropWhile { it.isDigit() }.trim('°')
                        v.takeWhile { it.isDigit() } + "°" + if (resto == "super") " Super" else resto.uppercase()
                    }
                    v == "q" || v == "quadro" -> "Quadro"
                    v == "super" -> "Super"
                    else -> v.uppercase()
                }
            }
        }
        return null
    }

    private fun trovaScadenza(righe: List<String>): String? {
        val etichette = listOf(
            "scadenza contratto", "data scadenza", "fine contratto", "data fine rapporto", "fine rapporto",
            "termine contratto", "data termine", "data fine", "scadenza",
        )
        for (e in etichette) {
            for ((i, r) in righe.withIndex()) {
                val pos = trovaEtichetta(r, e)
                if (pos < 0 || r.contains("pagament") || r.contains("permesso di sogg")) continue
                val dopo = r.substring(pos + e.length)
                val m = RE_DATA.find(dopo) ?: righe.getOrNull(i + 1)?.let { RE_DATA.find(it) }
                if (m != null) return formattaData(m) ?: continue
            }
        }
        return null
    }

    private fun formattaData(m: MatchResult): String? {
        val g = m.groupValues[1].toInt()
        val me = m.groupValues[2].toInt()
        var a = m.groupValues[3].toInt()
        if (a < 100) a += 2000
        if (g !in 1..31 || me !in 1..12 || a !in 1990..2100) return null
        return "%02d/%02d/%04d".format(g, me, a)
    }

    private fun trovaTesto(originali: List<String>, righe: List<String>, etichette: List<String>): String? {
        for (e in etichette) {
            for ((i, r) in righe.withIndex()) {
                val pos = trovaEtichetta(r, e)
                if (pos < 0) continue
                val v = originali[i].substring(pos + e.length).trim(' ', ':', '-', '.')
                    // il PDF spesso unisce le colonne con un solo spazio: si taglia alle parole che non fanno parte del nome
                    .split(Regex("""\s{2,}|(?i)\b(p\.?\s?iva|partita iva|codice fiscale|cod\. fisc|c\.f\.|cedolino|busta paga|periodo|matricola|sede|indirizzo|via|piazza)\b""")).first().trim()
                if (v.length >= 3 && v.count { it.isLetter() } >= 3) return v.take(50)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- ferie / ROL

    data class Mgr(val maturati: Double?, val goduti: Double?, val residui: Double?, val unita: String?)

    private enum class Col { AP, MAT, GOD, RES }

    private fun trovaMaturatiGodutiResidui(
        righe: List<String>,
        rigaChiave: (String) -> Boolean,
        residue: List<String>,
        maturate: List<String>,
        godute: List<String>,
    ): Mgr {
        fun esplicito(etichette: List<String>): Pair<Double, String>? {
            for (e in etichette) for (r in righe) {
                val pos = trovaEtichetta(r, e)
                if (pos < 0 || r.contains("progr") || r.contains("indennit")) continue
                val n = numeri(r.substring(pos + e.length).replace(Regex("""^[a-z.]*"""), ""))
                if (n.isNotEmpty()) return n.first() to r
            }
            return null
        }

        var mat = esplicito(maturate)?.first
        var god = esplicito(godute)?.first
        val resE = esplicito(residue)
        var res = resE?.first
        var rigaUnita: String? = resE?.second

        if (mat == null || god == null || res == null) {
            // forma a tabella: "Ferie  26,00  8,00  18,00" con o senza intestazione sopra
            for ((i, r) in righe.withIndex()) {
                if (!rigaChiave(r)) continue
                val inizioValori = Regex("""(ferie|r\.?\s?o\.?\s?l\.?|permessi|riduzione orario)[a-z.\s]*""").find(r)?.range?.last ?: continue
                val n = numeri(r.substring(inizioValori + 1))
                if (n.size < 2) continue
                val colonne = intestazioneColonne(righe, i)
                val valori: Map<Col, Double> = if (colonne != null && colonne.size >= n.size) {
                    colonne.zip(n).toMap()
                } else {
                    when (n.size) {
                        2 -> mapOf(Col.MAT to n[0], Col.GOD to n[1])
                        3 -> mapOf(Col.MAT to n[0], Col.GOD to n[1], Col.RES to n[2])
                        else -> mapOf(Col.AP to n[0], Col.MAT to n[1], Col.GOD to n[2], Col.RES to n[3])
                    }
                }
                val ap = valori[Col.AP] ?: 0.0
                if (mat == null) mat = valori[Col.MAT]?.let { it + ap }
                if (god == null) god = valori[Col.GOD]
                if (res == null) res = valori[Col.RES]
                rigaUnita = rigaUnita ?: r
                break
            }
        }

        // completa il terzo valore se ne mancano uno e gli altri due sono noti
        if (res == null && mat != null && god != null) res = arrotonda(mat!! - god!!)
        if (god == null && mat != null && res != null) god = arrotonda(mat!! - res!!).takeIf { it >= 0 }
        if (mat == null && god != null && res != null) mat = arrotonda(god!! + res!!)

        val unita = rigaUnita?.let {
            when {
                Regex("""\b(ore|h|hh)\b""").containsMatchIn(it) -> Cedolino.UNITA_ORE
                Regex("""\b(gg|giorni|gg\.)\b""").containsMatchIn(it) -> Cedolino.UNITA_GIORNI
                else -> null
            }
        }
        return Mgr(mat, god, res, unita)
    }

    private fun intestazioneColonne(righe: List<String>, indice: Int): List<Col>? {
        for (j in (indice - 1) downTo maxOf(0, indice - 6)) {
            val h = righe[j]
            if (!(h.contains("matur") || h.contains("spett")) || !(h.contains("god") || h.contains("fruit") || h.contains("usufr"))) continue
            val trovate = mutableListOf<Pair<Int, Col>>()
            fun cerca(re: Regex, col: Col) = re.find(h)?.let { trovate += it.range.first to col }
            cerca(Regex("""(anno prec|a\.\s?p\.|res\.?\s*a\.?p|residuo a\.?p|\bap\b)"""), Col.AP)
            cerca(Regex("""(matur|spett)"""), Col.MAT)
            cerca(Regex("""(godut|god\.|goduto|fruit|usufr)"""), Col.GOD)
            cerca(Regex("""(saldo|resid(?!uo a\.?p)|da god)"""), Col.RES)
            return trovate.sortedBy { it.first }.map { it.second }.distinct()
        }
        return null
    }

    // ---------------------------------------------------------------- straordinario

    data class Straordinario(val effettuate: Double?, val pagate: Double?, val accantonate: Double?, val bancaOre: Double?)

    private fun trovaStraordinario(righe: List<String>): Straordinario {
        fun primoNumeroDopo(etichette: List<String>, escludi: List<String> = emptyList()): Double? {
            for (e in etichette) for (r in righe) {
                val pos = trovaEtichetta(r, e)
                if (pos < 0 || r.contains("progr") || escludi.any { r.contains(it) }) continue
                val n = numeri(r.substring(pos + e.length).replace(Regex("""^[a-z.]*"""), ""))
                if (n.isNotEmpty()) return n.first()
            }
            return null
        }

        val effettuateEsplicite = primoNumeroDopo(listOf(
            "ore straordinario effettuate", "straordinario effettuato", "straordinari effettuati", "ore straordinarie effettuate",
            "totale ore straordinario", "totale straordinario ore",
        ))
        val pagateEsplicite = primoNumeroDopo(listOf(
            "straordinario pagato", "straordinari pagati", "ore straordinario pagate", "straordinario liquidato", "ore straordinario liquidate",
        ))
        val accantonate = primoNumeroDopo(listOf(
            "ore accantonate", "straordinario accantonato", "accantonate banca ore", "accantonamento banca ore",
            "straordinari accantonati", "ore a banca ore",
        ))
        val bancaOre = run {
            for (e in listOf("banca ore residua", "saldo banca ore", "residuo banca ore", "banca ore saldo", "banca ore")) for (r in righe) {
                val pos = trovaEtichetta(r, e)
                if (pos < 0 || r.contains("progr") || r.contains("accanton")) continue
                val n = numeri(r.substring(pos + e.length))
                if (n.isNotEmpty()) return@run n.last()
            }
            null
        }

        // voci di competenza "Straordinario 25%  10,00  15,20  152,00": la prima quantità sono le ore pagate
        var sommaVoci = 0.0
        var trovataVoce = false
        if (pagateEsplicite == null) {
            for (r in righe) {
                if (!Regex("""\bstraord""").containsMatchIn(r)) continue
                if (listOf("accant", "banca", "progr", "effettuat", "pagat", "liquidat", "totale ore").any { r.contains(it) }) continue
                val inizio = Regex("""straord[a-z.]*""").find(r)!!.range.last + 1
                val pulita = r.substring(inizio)
                val token = RE_NUMERO.findAll(pulita.replace(RE_DATA, " ").replace(RE_FRAZIONE, " ").replace(RE_PERCENTUALE, " ")).toList()
                // una sola cifra senza "ore" è probabilmente l'importo, non le ore
                if (token.size < 2 && !Regex("""\b(ore|h)\b""").containsMatchIn(r)) continue
                val ore = token.firstOrNull()?.let { parseNumero(it.value) } ?: continue
                if (ore in 0.0..300.0) {
                    sommaVoci += ore
                    trovataVoce = true
                }
            }
        }
        val pagate = pagateEsplicite ?: if (trovataVoce) arrotonda(sommaVoci) else null
        val effettuate = effettuateEsplicite
            ?: if (pagate != null || accantonate != null) arrotonda((pagate ?: 0.0) + (accantonate ?: 0.0)) else null
        return Straordinario(effettuate, pagate, accantonate, bancaOre)
    }

    private fun arrotonda(v: Double) = Math.round(v * 100) / 100.0

    /** true se due importi coincidono a meno di [tolleranza]. */
    fun quasiUguali(a: Double, b: Double, tolleranza: Double = 0.05) = abs(a - b) <= tolleranza
}

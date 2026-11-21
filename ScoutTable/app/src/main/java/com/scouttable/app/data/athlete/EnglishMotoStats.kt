// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import java.net.URLEncoder

/**
 * MotoGP dalla voce en.wikipedia del pilota ({{Infobox motorcycle rider}}), usata quando quella
 * italiana non indica la squadra:
 * "MotoGP Manufacturers = [[Honda]] (2000–2003)<br /> [[Yamaha Motor Company|Yamaha]] (2004–2010,
 * 2013–2021)<br /> [[Ducati]] (2011–2012)" -> Yamaha (più stagioni), voce "Yamaha Motor Company".
 * Il logo viene dal campo "logo" dell'infobox della casa (es. "Yamaha Motor 2025.svg").
 */
object EnglishMotoStats {

    /** (Casa con più stagioni nella classe regina, voce en.wikipedia della casa) o null. */
    fun mainManufacturer(enWikitext: String): Pair<String, String?>? {
        val infobox = WikiTemplates.findPrefixed(enWikitext, "Infobox motorcycle").firstOrNull()?.let(WikiTemplates::params)
            ?: WikiTemplates.findPrefixed(enWikitext, "Infobox").firstOrNull()?.let(WikiTemplates::params)
            ?: return null
        // Classe regina: MotoGP, prima la 500 (piloti fino al 2001).
        val raw = listOf("motogpmanufacturers", "500manufacturers", "500ccmanufacturers", "manufacturers")
            .firstNotNullOfOrNull { k -> infobox[k]?.takeIf { it.isNotBlank() } } ?: return null
        val team = WikipediaAthleteStats.mainTeam(raw) ?: return null
        val link = Regex("""\[\[([^\]|]+)(?:\|([^\]]*))?]]""").findAll(raw).firstOrNull { m ->
            val shown = m.groupValues[2].ifBlank { m.groupValues[1] }
            shown.trim().equals(team, ignoreCase = true)
        }?.groupValues?.get(1)?.trim()
        return team to link
    }

    /** File del logo dall'infobox di una voce ("| logo = Honda logo.svg"); null se assente. */
    fun logoFile(enWikitext: String): String? {
        val infobox = WikiTemplates.findPrefixed(enWikitext, "Infobox").firstOrNull()?.let(WikiTemplates::params) ?: return null
        val raw = infobox["logo"]?.trim()?.ifBlank { null } ?: return null
        // "Honda logo.svg", "File:Honda logo.svg" o "[[File:Honda logo.svg|200px]]".
        val name = Regex("""(?:File:|Image:)?([^\[\]|]+\.(?:svg|png|jpe?g|gif|webp))""", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.get(1)?.trim() ?: return null
        return name.ifBlank { null }
    }

    fun fileUrl(file: String): String =
        "https://en.wikipedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file.replace(' ', '_'), "UTF-8") + "?width=200"
}

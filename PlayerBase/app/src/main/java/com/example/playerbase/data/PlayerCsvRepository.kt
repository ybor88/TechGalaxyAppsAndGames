package com.example.playerbase.data

import android.content.Context
import java.io.File

/**
 * Persistenza permanente su file CSV nella memoria interna dell'app
 * (context.filesDir/players.csv). Nessuna dipendenza esterna, nessun
 * permesso di storage richiesto: il file sopravvive tra un avvio e l'altro.
 * Espone anche la conversione da/verso testo CSV grezzo, usata da
 * [BackupManager] per il backup completo (dati + immagini) in zip.
 */
class PlayerCsvRepository(context: Context) {

    private val file: File = File(context.filesDir, "players.csv")

    fun loadAll(): List<Player> {
        if (!file.exists()) return emptyList()
        return parseContent(file.readText(Charsets.UTF_8))
    }

    fun saveAll(players: List<Player>) {
        file.writeText(buildContent(players), Charsets.UTF_8)
    }

    fun toCsv(players: List<Player>): String = buildContent(players)

    fun fromCsv(text: String): List<Player> = parseContent(text)

    companion object {
        private fun parseContent(content: String): List<Player> {
            val lines = content.lines()
            if (lines.size <= 1) return emptyList()
            val parser = when (lines[0].trim()) {
                HEADER_V1 -> ::parseLegacyLine
                HEADER_V2 -> ::parseLineV2
                HEADER_V3 -> ::parseLineV3
                HEADER_V4 -> ::parseLineV4
                HEADER_V5 -> ::parseLineV5
                HEADER_V6 -> ::parseLineV6
                HEADER_V7 -> ::parseLineV7
                else -> ::parseLine
            }
            return lines.drop(1)
                .filter { it.isNotBlank() }
                .mapNotNull { line -> runCatching { parser(line) }.getOrNull() }
        }

        private fun buildContent(players: List<Player>): String {
            val sb = StringBuilder()
            sb.appendLine(HEADER)
            players.forEach { sb.appendLine(toCsvLine(it)) }
            return sb.toString()
        }

        private const val HEADER_V1 =
            "id,name,sport,birthYear,maxCareer,maxTeam,retired,scoutingTimestamp," +
                "skinColor,hairStyle,hairColor,eyesColor,beardStyle,jerseyColor,jerseyPattern,patternColor,symbol"

        /** Formato precedente: colore pantaloncini ancora non presente, aveva invece la sigla lega (NBA/FIBA/...). */
        private const val HEADER_V2 =
            "id,name,surname,gender,sport,birthYear,heightCm,maxCareer,maxPower,maxTeam,retired,scoutingTimestamp," +
                "skinColor,hairStyle,hairColor,eyesColor,beardStyle,jerseyColor,jerseyPattern,patternColor,trimColor,symbol"

        /** Formato precedente: aveva ancora pelle/capelli/occhi/barba dell'avatar disegnato, poi rimosso. */
        private const val HEADER_V3 =
            "id,name,surname,gender,sport,birthYear,heightCm,maxCareer,maxPower,maxTeam,retired,scoutingTimestamp," +
                "skinColor,hairStyle,hairColor,eyesColor,beardStyle,jerseyColor,jerseyPattern,patternColor,trimColor,shortsColor"

        /** Formato precedente: aveva ancora il completino (colori maglia/pantaloncini), poi rimosso. */
        private const val HEADER_V4 =
            "id,name,surname,gender,sport,birthYear,heightCm,maxCareer,maxPower,maxTeam,retired,scoutingTimestamp," +
                "jerseyColor,jerseyPattern,patternColor,trimColor,shortsColor"

        /** Formato precedente: aveva ancora Max Power, poi rimosso come funzionalità. */
        private const val HEADER_V5 =
            "id,name,surname,gender,sport,role,birthYear,heightCm,maxCareer,maxPower,maxTeam,retired,scoutingTimestamp"

        /** Formato precedente: non aveva ancora Power Double. */
        private const val HEADER_V6 =
            "id,name,surname,gender,sport,role,birthYear,heightCm,maxCareer,maxTeam,retired,scoutingTimestamp"

        /** Formato precedente: aveva ancora Power Double, non ancora Visionato. */
        private const val HEADER_V7 =
            "id,name,surname,gender,sport,role,birthYear,heightCm,maxCareer,maxTeam,powerDouble,retired,scoutingTimestamp"

        private const val HEADER =
            "id,name,surname,gender,sport,role,birthYear,heightCm,maxCareer,maxTeam,viewed,retired,scoutingTimestamp"

        private fun escape(value: String): String {
            val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n')
            val escaped = value.replace("\"", "\"\"")
            return if (needsQuoting) "\"$escaped\"" else escaped
        }

        private fun toCsvLine(p: Player): String = listOf(
            p.id,
            escape(p.name),
            escape(p.surname),
            p.gender.name,
            p.sport.name,
            p.role.name,
            p.birthYear?.toString() ?: "",
            p.heightCm?.toString() ?: "",
            escape(p.maxCareer),
            escape(p.maxTeam),
            p.viewed.toString(),
            p.retired.toString(),
            p.scoutingTimestamp.toString()
        ).joinToString(",")

        private fun parseLine(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                role = PlayerRole.valueOf(f[5]),
                birthYear = f[6].toIntOrNull(),
                heightCm = f[7].toIntOrNull(),
                maxCareer = f[8],
                maxTeam = f[9],
                viewed = f[10].toBoolean(),
                retired = f[11].toBoolean(),
                scoutingTimestamp = f[12].toLong()
            )
        }

        /** Formato precedente: aveva ancora Power Double, non ancora Visionato. */
        private fun parseLineV7(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                role = PlayerRole.valueOf(f[5]),
                birthYear = f[6].toIntOrNull(),
                heightCm = f[7].toIntOrNull(),
                maxCareer = f[8],
                maxTeam = f[9],
                retired = f[11].toBoolean(),
                scoutingTimestamp = f[12].toLong()
            )
        }

        /** Formato precedente: non aveva ancora Power Double. */
        private fun parseLineV6(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                role = PlayerRole.valueOf(f[5]),
                birthYear = f[6].toIntOrNull(),
                heightCm = f[7].toIntOrNull(),
                maxCareer = f[8],
                maxTeam = f[9],
                retired = f[10].toBoolean(),
                scoutingTimestamp = f[11].toLong()
            )
        }

        /** Formato precedente: aveva ancora Max Power. */
        private fun parseLineV5(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                role = PlayerRole.valueOf(f[5]),
                birthYear = f[6].toIntOrNull(),
                heightCm = f[7].toIntOrNull(),
                maxCareer = f[8],
                maxTeam = f[10],
                retired = f[11].toBoolean(),
                scoutingTimestamp = f[12].toLong()
            )
        }

        /** Formato precedente: aveva ancora il completino (colori maglia/pantaloncini). */
        private fun parseLineV4(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                birthYear = f[5].toIntOrNull(),
                heightCm = f[6].toIntOrNull(),
                maxCareer = f[7],
                maxTeam = f[9],
                retired = f[10].toBoolean(),
                scoutingTimestamp = f[11].toLong()
            )
        }

        /** Formato precedente: aveva ancora pelle/capelli/occhi/barba dell'avatar disegnato. */
        private fun parseLineV3(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                birthYear = f[5].toIntOrNull(),
                heightCm = f[6].toIntOrNull(),
                maxCareer = f[7],
                maxTeam = f[9],
                retired = f[10].toBoolean(),
                scoutingTimestamp = f[11].toLong()
            )
        }

        /** Formato precedente (aveva la sigla lega NBA/FIBA/... al posto del colore pantaloncini). */
        private fun parseLineV2(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                surname = f[2],
                gender = Gender.valueOf(f[3]),
                sport = Sport.valueOf(f[4]),
                birthYear = f[5].toIntOrNull(),
                heightCm = f[6].toIntOrNull(),
                maxCareer = f[7],
                maxTeam = f[9],
                retired = f[10].toBoolean(),
                scoutingTimestamp = f[11].toLong()
            )
        }

        /** Formato precedente (senza cognome/altezza/max power/gender/trim): mappato sui nuovi default. */
        private fun parseLegacyLine(line: String): Player {
            val f = splitCsvLine(line)
            return Player(
                id = f[0],
                name = f[1],
                sport = Sport.valueOf(f[2]),
                birthYear = f[3].toIntOrNull(),
                maxCareer = f[4],
                maxTeam = f[5],
                retired = f[6].toBoolean(),
                scoutingTimestamp = f[7].toLong()
            )
        }

        private fun splitCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    inQuotes -> when {
                        c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                            current.append('"'); i++
                        }
                        c == '"' -> inQuotes = false
                        else -> current.append(c)
                    }
                    c == '"' -> inQuotes = true
                    c == ',' -> { result.add(current.toString()); current.clear() }
                    else -> current.append(c)
                }
                i++
            }
            result.add(current.toString())
            return result
        }
    }
}

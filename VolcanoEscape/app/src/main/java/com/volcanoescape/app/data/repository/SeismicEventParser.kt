package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.SeismicEvent
import com.volcanoescape.app.data.model.Volcano
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Parsing del formato "text" FDSN-event dell'INGV e filtraggio delle scosse per vulcano.
 * Isolato dal repository (che si occupa solo di IO di rete) per poter essere testato
 * come semplice funzione pura, senza dover mockare la rete.
 */
object SeismicEventParser {

    fun parse(raw: String): List<SeismicEvent> {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        return lines.mapNotNull { line ->
            val cols = line.split("|")
            if (cols.size < 13) return@mapNotNull null
            runCatching {
                SeismicEvent(
                    eventId = cols[0],
                    time = LocalDateTime.parse(cols[1]).atOffset(ZoneOffset.UTC),
                    latitude = cols[2].toDouble(),
                    longitude = cols[3].toDouble(),
                    depthKm = cols[4].toDoubleOrNull() ?: 0.0,
                    magnitudeType = cols[9],
                    magnitude = cols[10].toDoubleOrNull() ?: 0.0,
                    locationName = cols[12],
                )
            }.getOrNull()
        }.sortedByDescending { it.time }.toList()
    }

    /**
     * L'INGV etichetta esplicitamente col nome dell'area vulcanica ("Vesuvio", "Campi Flegrei",
     * "Ischia", ...) le scosse che ricadono in quelle zone sismiche nominate. Vulcani vicini fra
     * loro (es. Vesuvio e Campi Flegrei distano solo ~24 km) hanno raggi di ricerca che si
     * sovrappongono: senza questo filtro, cercando le scosse del Vesuvio si otterrebbero anche
     * gli sciami sismici dei Campi Flegrei (o viceversa). Si escludono quindi le scosse
     * esplicitamente attribuite dall'INGV a un vulcano diverso da quello richiesto.
     */
    fun filterForVolcano(
        events: List<SeismicEvent>,
        volcano: Volcano,
        allVolcanoes: List<Volcano>,
    ): List<SeismicEvent> {
        val otherNamedAreas = allVolcanoes
            .asSequence()
            .filter { it.id != volcano.id }
            .mapNotNull { it.namedSeismicArea }
            .map { it.trim().lowercase() }
            .toSet()

        if (otherNamedAreas.isEmpty()) return events

        return events.filterNot { event ->
            event.locationName.trim().lowercase() in otherNamedAreas
        }
    }
}

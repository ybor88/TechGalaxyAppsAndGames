package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.model.VolcanoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeismicEventParserTest {

    private val sampleFdsnText = """
        #EventID|Time|Latitude|Longitude|Depth/Km|Author|Catalog|Contributor|ContributorID|MagType|Magnitude|MagAuthor|EventLocationName|EventType
        1|2026-08-29T18:18:59.250000|40.8205|14.4325|0.1|SURVEY-INGV-OV#WESSEL||||Md|2.0|--|Vesuvio|earthquake
        2|2026-08-28T16:52:39.480000|40.825833|14.156667|2.7|SURVEY-INGV-OV#WESSEL||||Md|1.6|--|Campi Flegrei|earthquake
        3|2026-08-27T05:53:14.970000|40.822667|14.084|3.0|SURVEY-INGV-OV#WESSEL||||Md|1.8|--|Campi Flegrei|earthquake
        4|2026-08-26T00:04:52.490000|40.7300|13.8974|4.0|SURVEY-INGV-OV#WESSEL||||Md|1.2|--|Ischia|earthquake
        5|2026-08-25T04:22:37.730000|40.8205|14.4300|0.5|SURVEY-INGV-OV#WESSEL||||Md|0.9|--|5 km NE Somma Vesuviana (NA)|earthquake

        not-enough-columns|a|b
    """.trimIndent()

    @Test
    fun `parse skips header comment and malformed lines`() {
        val events = SeismicEventParser.parse(sampleFdsnText)

        assertEquals(5, events.size)
    }

    @Test
    fun `parse maps columns to the right fields`() {
        val events = SeismicEventParser.parse(sampleFdsnText)
        val first = events.first { it.eventId == "1" }

        assertEquals(40.8205, first.latitude, 0.0001)
        assertEquals(14.4325, first.longitude, 0.0001)
        assertEquals(0.1, first.depthKm, 0.0001)
        assertEquals("Md", first.magnitudeType)
        assertEquals(2.0, first.magnitude, 0.0001)
        assertEquals("Vesuvio", first.locationName)
    }

    @Test
    fun `parse sorts events by time descending`() {
        val events = SeismicEventParser.parse(sampleFdsnText)

        val times = events.map { it.time }
        assertEquals(times.sortedDescending(), times)
    }

    @Test
    fun `filterForVolcano removes events explicitly attributed to a nearby different volcano`() {
        // Riproduce il bug segnalato: cercando le scosse del Vesuvio (raggio 30km) l'INGV
        // restituisce anche gli sciami di Campi Flegrei e Ischia, che sono più vicini di 30km.
        val events = SeismicEventParser.parse(sampleFdsnText)
        val vesuvio = VolcanoRepository.italianVolcanoes.first { it.id == "vesuvio" }

        val filtered = SeismicEventParser.filterForVolcano(events, vesuvio, VolcanoRepository.italianVolcanoes)

        assertTrue(filtered.none { it.locationName == "Campi Flegrei" })
        assertTrue(filtered.none { it.locationName == "Ischia" })
        assertTrue(filtered.any { it.locationName == "Vesuvio" })
        // Un evento con nome generico (non un'area nominata di un altro vulcano) resta incluso.
        assertTrue(filtered.any { it.locationName == "5 km NE Somma Vesuviana (NA)" })
    }

    @Test
    fun `filterForVolcano is case and whitespace insensitive`() {
        val vesuvio = Volcano(
            id = "vesuvio",
            displayName = "Vesuvio",
            region = "Campania",
            latitude = 40.8210,
            longitude = 14.4260,
            ingvBulletinUrl = "",
            namedSeismicArea = "Vesuvio",
        )
        val campiFlegrei = Volcano(
            id = "campi_flegrei",
            displayName = "Campi Flegrei",
            region = "Campania",
            latitude = 40.8267,
            longitude = 14.1392,
            ingvBulletinUrl = "",
            namedSeismicArea = "Campi Flegrei",
        )
        val events = SeismicEventParser.parse(
            """
            1|2026-08-29T18:18:59.250000|40.82|14.43|0.1|A||||Md|2.0|--|  campi flegrei  |earthquake
            """.trimIndent(),
        )

        val filtered = SeismicEventParser.filterForVolcano(events, vesuvio, listOf(vesuvio, campiFlegrei))

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `filterForVolcano keeps events with a generic town-based location name`() {
        // L'INGV etichetta le scosse dell'Etna con toponimi generici (nessun vulcano vicino usa
        // un'area nominata condivisa), quindi il filtro non deve scartare nulla in questo caso.
        val etna = VolcanoRepository.italianVolcanoes.first { it.id == "etna" }
        val events = SeismicEventParser.parse(
            """
            1|2026-08-29T18:18:59.250000|37.75|14.99|9.0|A|||||Md|2.1|--|9 km SW Linguaglossa (CT)|earthquake
            2|2026-08-28T16:52:39.480000|37.76|15.00|8.0|A|||||Md|1.6|--|3 km SW Zafferana Etnea (CT)|earthquake
            """.trimIndent(),
        )

        val filtered = SeismicEventParser.filterForVolcano(events, etna, VolcanoRepository.italianVolcanoes)

        assertEquals(events.size, filtered.size)
    }
}

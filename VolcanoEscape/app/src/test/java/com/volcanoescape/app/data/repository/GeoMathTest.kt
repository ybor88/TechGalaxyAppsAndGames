// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    private val vesuvio = GeoPoint(40.8210, 14.4260)

    @Test
    fun `distanceMeters matches known Naples to Rome great-circle distance`() {
        val naples = GeoPoint(40.8518, 14.2681)
        val rome = GeoPoint(41.9028, 12.4964)

        val distanceKm = distanceMeters(naples, rome) / 1000.0

        assertEquals(188.0, distanceKm, 5.0)
    }

    @Test
    fun `safe destination is always at least the safety radius from the volcano`() {
        // Utente già dentro il raggio di sicurezza: il punto sicuro deve comunque
        // finire ad almeno safetyRadiusMeters dal cratere.
        val userNearby = GeoPoint(40.8300, 14.4300)

        val destination = safeDestinationAwayFromVolcano(
            volcano = vesuvio,
            userLocation = userNearby,
            safetyRadiusMeters = 30_000.0,
            minimumBufferMeters = 10_000.0,
        )

        val distanceFromVolcano = distanceMeters(vesuvio, destination)
        assertTrue(
            "distanza dal vulcano era $distanceFromVolcano, attesa >= 30000",
            distanceFromVolcano >= 30_000.0 - 1.0,
        )
    }

    @Test
    fun `safe destination stays a minimum buffer beyond an already-distant user`() {
        // Utente già a 40 km, oltre il raggio di sicurezza di default: il punto sicuro deve
        // comunque allontanarsi ulteriormente di almeno minimumBufferMeters, non fermarsi lì.
        val bearingAway = bearingRad(vesuvio, GeoPoint(40.9, 14.5))
        val userFarAway = destinationPoint(vesuvio, bearingAway, 40_000.0)

        val destination = safeDestinationAwayFromVolcano(
            volcano = vesuvio,
            userLocation = userFarAway,
            safetyRadiusMeters = 30_000.0,
            minimumBufferMeters = 10_000.0,
        )

        val distanceFromVolcano = distanceMeters(vesuvio, destination)
        assertTrue(
            "distanza dal vulcano era $distanceFromVolcano, attesa >= 50000",
            distanceFromVolcano >= 50_000.0 - 1.0,
        )
    }
}

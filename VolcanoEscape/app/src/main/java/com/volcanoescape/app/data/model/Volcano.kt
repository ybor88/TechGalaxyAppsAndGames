package com.volcanoescape.app.data.model

/**
 * Lista precompilata dei vulcani attivi/monitorati INGV in Italia.
 * Coordinate del cratere/edificio principale, usate come centro di ricerca
 * per il bollettino sismico e come origine per il calcolo della via di fuga.
 */
data class Volcano(
    val id: String,
    val displayName: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val ingvBulletinUrl: String,
    /**
     * Nome dell'area sismica "nominata" usata dall'INGV (campo EventLocationName) per le
     * scosse ufficialmente attribuite a questo vulcano (es. "Vesuvio", "Campi Flegrei").
     * Quando presente, viene usato per escludere dai risultati di un vulcano le scosse che
     * l'INGV attribuisce esplicitamente a un *altro* vulcano che ricade nello stesso raggio
     * di ricerca (es. gli sciami di Campi Flegrei che altrimenti comparirebbero anche
     * cercando intorno al Vesuvio, distante solo ~24 km).
     */
    val namedSeismicArea: String? = null,
)

object VolcanoRepository {

    val italianVolcanoes: List<Volcano> = listOf(
        Volcano(
            id = "etna",
            displayName = "Etna",
            region = "Sicilia",
            latitude = 37.7510,
            longitude = 14.9934,
            ingvBulletinUrl = "https://www.ct.ingv.it/",
        ),
        Volcano(
            id = "vesuvio",
            displayName = "Vesuvio",
            region = "Campania",
            latitude = 40.8210,
            longitude = 14.4260,
            ingvBulletinUrl = "https://www.ov.ingv.it/",
            namedSeismicArea = "Vesuvio",
        ),
        Volcano(
            id = "campi_flegrei",
            displayName = "Campi Flegrei",
            region = "Campania",
            latitude = 40.8267,
            longitude = 14.1392,
            ingvBulletinUrl = "https://www.ov.ingv.it/",
            namedSeismicArea = "Campi Flegrei",
        ),
        Volcano(
            id = "stromboli",
            displayName = "Stromboli",
            region = "Isole Eolie, Sicilia",
            latitude = 38.7893,
            longitude = 15.2131,
            ingvBulletinUrl = "https://www.ct.ingv.it/",
        ),
        Volcano(
            id = "vulcano",
            displayName = "Vulcano",
            region = "Isole Eolie, Sicilia",
            latitude = 38.4045,
            longitude = 14.9622,
            ingvBulletinUrl = "https://www.ct.ingv.it/",
        ),
        Volcano(
            id = "ischia",
            displayName = "Ischia (Epomeo)",
            region = "Campania",
            latitude = 40.7300,
            longitude = 13.8974,
            ingvBulletinUrl = "https://www.ov.ingv.it/",
            namedSeismicArea = "Ischia",
        ),
        Volcano(
            id = "colli_albani",
            displayName = "Colli Albani",
            region = "Lazio",
            latitude = 41.7300,
            longitude = 12.7000,
            ingvBulletinUrl = "https://www.ingv.it/",
        ),
        Volcano(
            id = "pantelleria",
            displayName = "Pantelleria",
            region = "Sicilia",
            latitude = 36.7700,
            longitude = 12.0000,
            ingvBulletinUrl = "https://www.ingv.it/",
        ),
        Volcano(
            id = "vulture",
            displayName = "Vulture",
            region = "Basilicata",
            latitude = 40.9680,
            longitude = 15.6180,
            ingvBulletinUrl = "https://www.ingv.it/",
        ),
    )
}

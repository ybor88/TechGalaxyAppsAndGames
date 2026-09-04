package com.romanopetroli.rpfidelity.data

/**
 * URL base dell'API RP Fidelity.
 *
 * Produzione: hosting Aruba su rpfidelity.it.
 * Per tornare a sviluppare in locale con l'emulatore, usare "http://10.0.2.2:8000/api"
 * (10.0.2.2 è l'alias speciale con cui l'emulatore raggiunge "localhost" del PC host).
 */
object NetworkConfig {
    const val BASE_URL = "https://www.rpfidelity.it/api"
}

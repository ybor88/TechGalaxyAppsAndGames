package com.romanopetroli.rpfidelity.data

/**
 * URL base dell'API RP Fidelity.
 *
 * - Emulatore Android: 10.0.2.2 è l'alias speciale per raggiungere "localhost" del PC host,
 *   quindi punta al server di sviluppo PHP avviato con `php -S localhost:8000 -t public public/index.php`.
 * - Dispositivo fisico sulla stessa rete Wi-Fi del PC: sostituire con l'IP LAN del PC, es. "http://192.168.1.50:8000/api".
 * - Produzione: sostituire con il dominio reale una volta pubblicato l'hosting, es. "https://app.romanopetroli.it/api".
 */
object NetworkConfig {
    const val BASE_URL = "http://10.0.2.2:8000/api"
}

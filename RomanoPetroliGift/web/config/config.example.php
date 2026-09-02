<?php
// Copia questo file in config.php e inserisci le credenziali del tuo hosting/ambiente locale.
// config.php NON va mai versionato (è nel .gitignore).

return [
    'db' => [
        'host' => '127.0.0.1',
        'port' => 3306,
        'name' => 'rpfidelity',
        'user' => 'root',
        'pass' => '',
        'charset' => 'utf8mb4',
    ],
    'app' => [
        'name' => 'RP Fidelity',
        'base_url' => 'http://localhost:8000',
        // Cambia questa chiave in produzione con una stringa casuale lunga.
        'session_secret' => 'cambia-questa-chiave-in-produzione',
    ],
];

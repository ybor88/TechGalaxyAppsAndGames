<?php
// Copyright (c) Roberto Di Flumeri

namespace App\Models;

use App\Core\Database;

class MessaggioContatto
{
    public static function create(?int $userId, string $nome, string $email, string $messaggio, string $mittente = 'cliente'): void
    {
        $stmt = Database::connection()->prepare(
            'INSERT INTO messaggi_contatto (user_id, nome, email, mittente, messaggio) VALUES (?, ?, ?, ?, ?)'
        );
        $stmt->execute([$userId, $nome, $email, $mittente, $messaggio]);
    }

    /** Conversazione completa di un cliente, in ordine cronologico. */
    public static function perCliente(int $userId): array
    {
        $stmt = Database::connection()->prepare(
            'SELECT * FROM messaggi_contatto WHERE user_id = ? ORDER BY creato_il ASC'
        );
        $stmt->execute([$userId]);

        return $stmt->fetchAll();
    }

    /** Elenco clienti con almeno un messaggio, per la inbox admin: più recenti prima. */
    public static function clientiConMessaggi(): array
    {
        $stmt = Database::connection()->query(
            "SELECT u.id, u.nome, u.cognome, u.email,
                    (SELECT m2.messaggio FROM messaggi_contatto m2
                     WHERE m2.user_id = u.id ORDER BY m2.creato_il DESC LIMIT 1) AS ultimo_messaggio,
                    (SELECT m2.mittente FROM messaggi_contatto m2
                     WHERE m2.user_id = u.id ORDER BY m2.creato_il DESC LIMIT 1) AS ultimo_mittente,
                    (SELECT m2.creato_il FROM messaggi_contatto m2
                     WHERE m2.user_id = u.id ORDER BY m2.creato_il DESC LIMIT 1) AS ultimo_il
             FROM users u
             WHERE u.ruolo = 'cliente' AND EXISTS (SELECT 1 FROM messaggi_contatto m WHERE m.user_id = u.id)
             ORDER BY ultimo_il DESC"
        );

        return $stmt->fetchAll();
    }
}

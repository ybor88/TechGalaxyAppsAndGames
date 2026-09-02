<?php
// Copyright (c) Roberto Di Flumeri

namespace App\Models;

use App\Core\Database;

class MessaggioContatto
{
    public static function create(?int $userId, string $nome, string $email, string $messaggio): void
    {
        $stmt = Database::connection()->prepare(
            'INSERT INTO messaggi_contatto (user_id, nome, email, messaggio) VALUES (?, ?, ?, ?)'
        );
        $stmt->execute([$userId, $nome, $email, $messaggio]);
    }
}

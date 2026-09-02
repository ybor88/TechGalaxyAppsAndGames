<?php

namespace App\Models;

use App\Core\Database;

class Distributore
{
    public static function unica(): ?array
    {
        $row = Database::connection()
            ->query('SELECT * FROM distributori ORDER BY id LIMIT 1')
            ->fetch();

        return $row ?: null;
    }
}

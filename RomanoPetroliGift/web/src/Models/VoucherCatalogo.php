<?php

namespace App\Models;

use App\Core\Database;

class VoucherCatalogo
{
    public static function attivi(): array
    {
        return Database::connection()
            ->query('SELECT * FROM voucher_catalogo WHERE attivo = 1 ORDER BY costo_punti')
            ->fetchAll();
    }

    public static function all(): array
    {
        return Database::connection()
            ->query('SELECT * FROM voucher_catalogo ORDER BY costo_punti')
            ->fetchAll();
    }

    public static function find(int $id): ?array
    {
        $stmt = Database::connection()->prepare('SELECT * FROM voucher_catalogo WHERE id = ?');
        $stmt->execute([$id]);
        $row = $stmt->fetch();

        return $row ?: null;
    }

    public static function create(string $nome, int $costoPunti, float $importoPremio): void
    {
        $stmt = Database::connection()->prepare(
            'INSERT INTO voucher_catalogo (nome, costo_punti, importo_premio, attivo) VALUES (?, ?, ?, 1)'
        );
        $stmt->execute([$nome, $costoPunti, $importoPremio]);
    }
}

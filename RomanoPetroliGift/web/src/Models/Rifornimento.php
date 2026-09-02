<?php

namespace App\Models;

use App\Core\Database;

class Rifornimento
{
    // Limite massimo di importo caricabile in una singola registrazione rifornimento.
    public const IMPORTO_MASSIMO = 150.0;

    // 1 punto ogni 10 euro EFFETTIVAMENTE PAGATI (dopo lo sconto degli eventuali voucher usati)
    // es. 45 euro pagati = 4.50 punti; se il rifornimento è di 50 euro ma si usa un voucher da 10,
    // si pagano 40 euro e si maturano 4 punti (non 5).
    public static function calcolaPunti(float $importoPagato): float
    {
        return round(max(0, $importoPagato) / 10, 2);
    }

    public static function create(int $userId, int $distributoreId, float $importo, float $importoVoucher = 0.0): array
    {
        $importoPagato = max(0, $importo - $importoVoucher);
        $punti = self::calcolaPunti($importoPagato);
        $codice = 'RF' . date('ymdHis') . random_int(10, 99);

        $stmt = Database::connection()->prepare(
            'INSERT INTO rifornimenti (user_id, distributore_id, data_ora, codice_rifornimento, importo, importo_pagato, importo_voucher, punti_maturati)
             VALUES (?, ?, NOW(), ?, ?, ?, ?, ?)'
        );
        $stmt->execute([$userId, $distributoreId, $codice, $importo, $importoPagato, $importoVoucher, $punti]);

        return [
            'id' => (int) Database::connection()->lastInsertId(),
            'codice_rifornimento' => $codice,
            'importo_pagato' => $importoPagato,
            'punti_maturati' => $punti,
        ];
    }

    public static function perUtente(int $userId, ?string $dal, ?string $al): array
    {
        $sql = 'SELECT r.*
                FROM rifornimenti r
                WHERE r.user_id = ?';
        $params = [$userId];

        if ($dal) {
            $sql .= ' AND r.data_ora >= ?';
            $params[] = $dal . ' 00:00:00';
        }
        if ($al) {
            $sql .= ' AND r.data_ora <= ?';
            $params[] = $al . ' 23:59:59';
        }

        $sql .= ' ORDER BY r.data_ora DESC';

        $stmt = Database::connection()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll();
    }

    public static function ricerca(?string $dal, ?string $al): array
    {
        $sql = 'SELECT r.*, u.nome AS cliente_nome, u.cognome AS cliente_cognome
                FROM rifornimenti r
                LEFT JOIN users u ON u.id = r.user_id
                WHERE 1=1';
        $params = [];

        if ($dal) {
            $sql .= ' AND r.data_ora >= ?';
            $params[] = $dal . ' 00:00:00';
        }
        if ($al) {
            $sql .= ' AND r.data_ora <= ?';
            $params[] = $al . ' 23:59:59';
        }

        $sql .= ' ORDER BY r.data_ora DESC';

        $stmt = Database::connection()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetchAll();
    }

    public static function totali(?string $dal, ?string $al): array
    {
        $sql = 'SELECT
                    COALESCE(SUM(r.importo), 0) AS totale_rifornimenti,
                    COALESCE(SUM(r.importo_voucher), 0) AS totale_voucher,
                    COALESCE(SUM(r.importo_pagato) - SUM(r.importo_voucher), 0) AS saldo
                FROM rifornimenti r
                WHERE 1=1';
        $params = [];

        if ($dal) {
            $sql .= ' AND r.data_ora >= ?';
            $params[] = $dal . ' 00:00:00';
        }
        if ($al) {
            $sql .= ' AND r.data_ora <= ?';
            $params[] = $al . ' 23:59:59';
        }

        $stmt = Database::connection()->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetch() ?: ['totale_rifornimenti' => 0, 'totale_voucher' => 0, 'saldo' => 0];
    }
}

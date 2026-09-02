<?php

namespace App\Models;

use App\Core\Database;
use DateTime;
use DateInterval;

class VoucherUtente
{
    public static function perUtente(int $userId): array
    {
        $stmt = Database::connection()->prepare(
            'SELECT vu.*, vc.nome, vc.importo_premio
             FROM voucher_utente vu
             JOIN voucher_catalogo vc ON vc.id = vu.voucher_catalogo_id
             WHERE vu.user_id = ?
             ORDER BY vu.data_riscatto DESC'
        );
        $stmt->execute([$userId]);

        return $stmt->fetchAll();
    }

    public static function findByCodice(string $codice): ?array
    {
        $stmt = Database::connection()->prepare(
            'SELECT vu.*, vc.nome, vc.importo_premio, u.nome AS cliente_nome, u.cognome AS cliente_cognome
             FROM voucher_utente vu
             JOIN voucher_catalogo vc ON vc.id = vu.voucher_catalogo_id
             JOIN users u ON u.id = vu.user_id
             WHERE vu.codice_voucher = ?'
        );
        $stmt->execute([$codice]);
        $row = $stmt->fetch();

        return $row ?: null;
    }

    public static function riscatta(int $userId, int $voucherCatalogoId): string
    {
        $codice = strtoupper(bin2hex(random_bytes(8)));
        $scadenza = (new DateTime())->add(new DateInterval('P6M'))->format('Y-m-d');

        $stmt = Database::connection()->prepare(
            'INSERT INTO voucher_utente (user_id, voucher_catalogo_id, codice_voucher, data_scadenza, stato)
             VALUES (?, ?, ?, ?, "attivo")'
        );
        $stmt->execute([$userId, $voucherCatalogoId, $codice, $scadenza]);

        return $codice;
    }

    public static function segnaUsato(int $voucherId, ?int $rifornimentoId = null): void
    {
        $stmt = Database::connection()->prepare(
            'UPDATE voucher_utente SET stato = "usato", data_utilizzo = NOW(), rifornimento_id_utilizzo = ?
             WHERE id = ?'
        );
        $stmt->execute([$rifornimentoId, $voucherId]);
    }

    /** Numero di voucher riscattati per ciascuna taglia del catalogo, ordinati per costo punti. */
    public static function conteggioPerCatalogo(): array
    {
        $stmt = Database::connection()->query(
            'SELECT vc.nome, vc.costo_punti, COUNT(vu.id) AS totale
             FROM voucher_catalogo vc
             LEFT JOIN voucher_utente vu ON vu.voucher_catalogo_id = vc.id
             GROUP BY vc.id, vc.nome, vc.costo_punti
             ORDER BY vc.costo_punti'
        );

        return $stmt->fetchAll();
    }

    public static function totaleRiscattati(): int
    {
        return (int) Database::connection()->query('SELECT COUNT(*) FROM voucher_utente')->fetchColumn();
    }

    public static function totaleUsati(): int
    {
        return (int) Database::connection()->query("SELECT COUNT(*) FROM voucher_utente WHERE stato = 'usato'")->fetchColumn();
    }
}

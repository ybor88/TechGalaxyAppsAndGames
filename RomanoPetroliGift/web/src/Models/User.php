<?php

namespace App\Models;

use App\Core\Database;

class User
{
    public static function findByEmail(string $email): ?array
    {
        $stmt = Database::connection()->prepare('SELECT * FROM users WHERE email = ?');
        $stmt->execute([$email]);
        $user = $stmt->fetch();

        return $user ?: null;
    }

    public static function find(int $id): ?array
    {
        $stmt = Database::connection()->prepare('SELECT * FROM users WHERE id = ?');
        $stmt->execute([$id]);
        $user = $stmt->fetch();

        return $user ?: null;
    }

    public static function findByCodiceCard(string $codiceCard): ?array
    {
        $stmt = Database::connection()->prepare('SELECT * FROM users WHERE codice_card = ?');
        $stmt->execute([$codiceCard]);
        $user = $stmt->fetch();

        return $user ?: null;
    }

    public static function findByApiToken(string $token): ?array
    {
        $stmt = Database::connection()->prepare('SELECT * FROM users WHERE api_token = ?');
        $stmt->execute([$token]);
        $user = $stmt->fetch();

        return $user ?: null;
    }

    // Rigenerato ad ogni login: invalida eventuali token precedenti (un solo dispositivo loggato per volta).
    public static function generaApiToken(int $id): string
    {
        $token = bin2hex(random_bytes(32));
        $stmt = Database::connection()->prepare('UPDATE users SET api_token = ? WHERE id = ?');
        $stmt->execute([$token, $id]);

        return $token;
    }

    private static function generaCodiceCard(): string
    {
        do {
            $codice = 'RPF' . strtoupper(bin2hex(random_bytes(4)));
        } while (self::findByCodiceCard($codice) !== null);

        return $codice;
    }

    public static function create(string $nome, string $cognome, string $email, string $password, ?string $telefono): int
    {
        $stmt = Database::connection()->prepare(
            'INSERT INTO users (nome, cognome, email, password_hash, telefono, ruolo, punti_saldo, codice_card)
             VALUES (?, ?, ?, ?, ?, "cliente", 0, ?)'
        );
        $stmt->execute([
            $nome,
            $cognome,
            $email,
            password_hash($password, PASSWORD_DEFAULT),
            $telefono,
            self::generaCodiceCard(),
        ]);

        return (int) Database::connection()->lastInsertId();
    }

    public static function emailExists(string $email, ?int $excludeId = null): bool
    {
        $existing = self::findByEmail($email);

        if (!$existing) {
            return false;
        }

        return $excludeId === null || (int) $existing['id'] !== $excludeId;
    }

    public static function update(int $id, string $nome, string $cognome, string $email, ?string $telefono, string $stato): void
    {
        $stmt = Database::connection()->prepare(
            'UPDATE users SET nome = ?, cognome = ?, email = ?, telefono = ?, stato = ? WHERE id = ?'
        );
        $stmt->execute([$nome, $cognome, $email, $telefono, $stato, $id]);
    }

    public static function delete(int $id): void
    {
        $stmt = Database::connection()->prepare('DELETE FROM users WHERE id = ?');
        $stmt->execute([$id]);
    }

    public static function all(): array
    {
        $stmt = Database::connection()->query(
            'SELECT id, nome, cognome, email, telefono, ruolo, punti_saldo, stato, codice_card, data_registrazione
             FROM users ORDER BY data_registrazione DESC'
        );

        return $stmt->fetchAll();
    }

    public static function clienti(): array
    {
        $stmt = Database::connection()->query(
            "SELECT id, nome, cognome, email, punti_saldo FROM users WHERE ruolo = 'cliente' ORDER BY nome, cognome"
        );

        return $stmt->fetchAll();
    }

    public static function contaClienti(): int
    {
        return (int) Database::connection()->query("SELECT COUNT(*) FROM users WHERE ruolo = 'cliente'")->fetchColumn();
    }

    public static function puntiTotaliInCircolazione(): float
    {
        return (float) Database::connection()->query("SELECT COALESCE(SUM(punti_saldo), 0) FROM users WHERE ruolo = 'cliente'")->fetchColumn();
    }

    /** Nuove registrazioni cliente per mese, ultimi $mesi mesi (mesi senza registrazioni inclusi, a 0). */
    public static function registrazioniPerMese(int $mesi = 6): array
    {
        $stmt = Database::connection()->prepare(
            "SELECT DATE_FORMAT(data_registrazione, '%Y-%m') AS mese, COUNT(*) AS totale
             FROM users
             WHERE ruolo = 'cliente' AND data_registrazione >= DATE_SUB(CURDATE(), INTERVAL ? MONTH)
             GROUP BY mese
             ORDER BY mese"
        );
        $stmt->execute([$mesi - 1]);
        $conteggi = array_column($stmt->fetchAll(), 'totale', 'mese');

        $risultato = [];
        for ($i = $mesi - 1; $i >= 0; $i--) {
            $chiave = date('Y-m', strtotime("-{$i} months"));
            $risultato[] = ['mese' => $chiave, 'totale' => (int) ($conteggi[$chiave] ?? 0)];
        }

        return $risultato;
    }

    public static function addPunti(int $userId, float $punti, string $tipo, string $causale, ?int $riferimentoId = null): void
    {
        $db = Database::connection();
        $db->beginTransaction();

        $delta = $tipo === 'accredito' ? $punti : -$punti;

        $stmt = $db->prepare('UPDATE users SET punti_saldo = punti_saldo + ? WHERE id = ?');
        $stmt->execute([$delta, $userId]);

        $stmt = $db->prepare(
            'INSERT INTO punti_transazioni (user_id, tipo, punti, causale, riferimento_id) VALUES (?, ?, ?, ?, ?)'
        );
        $stmt->execute([$userId, $tipo, $punti, $causale, $riferimentoId]);

        $db->commit();
    }
}

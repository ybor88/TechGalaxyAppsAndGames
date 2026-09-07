<?php

namespace App\Core;

use App\Models\User;

class Auth
{
    // "Resta connesso": la sessione dura 30 giorni (rinnovati ad ogni visita) invece di scadere
    // alla chiusura del browser/PWA, che è il comportamento di default dei cookie di sessione PHP.
    private const SESSION_LIFETIME = 60 * 60 * 24 * 30;

    public static function start(): void
    {
        if (session_status() !== PHP_SESSION_NONE) {
            return;
        }

        ini_set('session.gc_maxlifetime', (string) self::SESSION_LIFETIME);
        session_set_cookie_params([
            'lifetime' => self::SESSION_LIFETIME,
            'path' => '/',
            'secure' => !empty($_SERVER['HTTPS']),
            'httponly' => true,
            'samesite' => 'Lax',
        ]);
        session_start();

        if (isset($_SESSION['user_id'])) {
            setcookie(session_name(), session_id(), [
                'expires' => time() + self::SESSION_LIFETIME,
                'path' => '/',
                'secure' => !empty($_SERVER['HTTPS']),
                'httponly' => true,
                'samesite' => 'Lax',
            ]);
        }
    }

    public static function attempt(string $email, string $password): bool
    {
        $user = User::findByEmail($email);

        if (!$user || !password_verify($password, $user['password_hash'])) {
            return false;
        }

        if ($user['stato'] !== 'attivo') {
            return false;
        }

        $_SESSION['user_id'] = $user['id'];
        $_SESSION['user_ruolo'] = $user['ruolo'];

        return true;
    }

    public static function logout(): void
    {
        $_SESSION = [];

        if (ini_get('session.use_cookies')) {
            $params = session_get_cookie_params();
            setcookie(session_name(), '', time() - 42000, $params['path'], $params['domain'], $params['secure'], $params['httponly']);
        }

        session_destroy();
    }

    public static function check(): bool
    {
        return isset($_SESSION['user_id']);
    }

    public static function isAdmin(): bool
    {
        return self::check() && $_SESSION['user_ruolo'] === 'admin';
    }

    public static function isDipendente(): bool
    {
        return self::check() && $_SESSION['user_ruolo'] === 'dipendente';
    }

    public static function isCliente(): bool
    {
        return self::check() && $_SESSION['user_ruolo'] === 'cliente';
    }

    // Admin e dipendente: entrambi possono operare alla cassa (registrare rifornimenti).
    public static function isStaff(): bool
    {
        return self::isAdmin() || self::isDipendente();
    }

    public static function userId(): ?int
    {
        return $_SESSION['user_id'] ?? null;
    }

    public static function user(): ?array
    {
        $id = self::userId();

        return $id ? User::find($id) : null;
    }

    public static function requireLogin(): void
    {
        if (!self::check()) {
            header('Location: /login');
            exit;
        }
    }

    public static function requireAdmin(): void
    {
        self::requireLogin();

        if (!self::isAdmin()) {
            http_response_code(403);
            echo 'Accesso non autorizzato.';
            exit;
        }
    }

    public static function requireCliente(): void
    {
        self::requireLogin();

        if (!self::isCliente()) {
            http_response_code(403);
            echo 'Questa sezione è riservata ai clienti.';
            exit;
        }
    }

    // Sezione "cassa": accessibile ad admin e dipendenti, non ai clienti.
    public static function requireStaff(): void
    {
        self::requireLogin();

        if (!self::isStaff()) {
            http_response_code(403);
            echo 'Accesso non autorizzato.';
            exit;
        }
    }
}

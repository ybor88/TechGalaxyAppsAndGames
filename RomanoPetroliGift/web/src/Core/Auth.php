<?php

namespace App\Core;

use App\Models\User;

class Auth
{
    public static function start(): void
    {
        if (session_status() === PHP_SESSION_NONE) {
            session_start();
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

        if (self::isAdmin()) {
            http_response_code(403);
            echo 'Questa sezione è riservata ai clienti.';
            exit;
        }
    }
}

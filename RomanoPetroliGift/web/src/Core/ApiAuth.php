<?php

namespace App\Core;

use App\Models\User;

class ApiAuth
{
    public static function user(): ?array
    {
        $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';

        if ($header === '' && function_exists('apache_request_headers')) {
            $headers = apache_request_headers();
            $header = $headers['Authorization'] ?? '';
        }

        if (!preg_match('/^Bearer\s+(.+)$/i', trim($header), $matches)) {
            return null;
        }

        $user = User::findByApiToken(trim($matches[1]));

        if (!$user || $user['stato'] !== 'attivo') {
            return null;
        }

        return $user;
    }

    public static function requireUser(): array
    {
        $user = self::user();

        if (!$user) {
            Json::error('Non autenticato.', 401);
        }

        return $user;
    }

    public static function requireCliente(): array
    {
        $user = self::requireUser();

        if ($user['ruolo'] !== 'cliente') {
            Json::error('Questa risorsa è riservata ai clienti.', 403);
        }

        return $user;
    }

    public static function requireAdmin(): array
    {
        $user = self::requireUser();

        if ($user['ruolo'] !== 'admin') {
            Json::error('Questa risorsa è riservata agli amministratori.', 403);
        }

        return $user;
    }
}

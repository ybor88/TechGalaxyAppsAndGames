<?php

namespace App\Controllers\Api;

use App\Core\ApiAuth;
use App\Core\Json;
use App\Models\User;

class AuthApiController
{
    public function login(array $input): void
    {
        $email = trim($input['email'] ?? '');
        $password = $input['password'] ?? '';

        $user = $email !== '' ? User::findByEmail($email) : null;

        if (!$user || !password_verify($password, $user['password_hash'])) {
            Json::error('Email o password non corretti.', 401);
        }
        if ($user['stato'] !== 'attivo') {
            Json::error('Account sospeso.', 403);
        }

        $token = User::generaApiToken((int) $user['id']);

        Json::send(['token' => $token, 'user' => self::publicUser(User::find((int) $user['id']))]);
    }

    public function registrati(array $input): void
    {
        $nome = trim($input['nome'] ?? '');
        $cognome = trim($input['cognome'] ?? '');
        $email = trim($input['email'] ?? '');
        $telefono = trim($input['telefono'] ?? '') ?: null;
        $password = $input['password'] ?? '';

        if ($nome === '' || $cognome === '' || $email === '' || strlen($password) < 6) {
            Json::error('Compila tutti i campi obbligatori (password min. 6 caratteri).', 422);
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            Json::error('Indirizzo email non valido.', 422);
        }
        if (User::emailExists($email)) {
            Json::error('Esiste già un account con questa email.', 422);
        }

        $id = User::create($nome, $cognome, $email, $password, $telefono);
        $token = User::generaApiToken($id);

        Json::send(['token' => $token, 'user' => self::publicUser(User::find($id))], 201);
    }

    public function me(): void
    {
        $user = ApiAuth::requireUser();

        Json::send(['user' => self::publicUser($user)]);
    }

    public static function publicUser(array $user): array
    {
        return [
            'id' => (int) $user['id'],
            'nome' => $user['nome'],
            'cognome' => $user['cognome'],
            'email' => $user['email'],
            'telefono' => $user['telefono'],
            'ruolo' => $user['ruolo'],
            'punti_saldo' => (float) $user['punti_saldo'],
            'codice_card' => $user['codice_card'],
        ];
    }
}

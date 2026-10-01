<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Mailer;
use App\Core\View;
use App\Models\User;

class AuthController
{
    public function showLogin(): void
    {
        if (Auth::check()) {
            header('Location: /dashboard');
            return;
        }

        View::render('auth/login');
    }

    public function login(): void
    {
        $email = trim($_POST['email'] ?? '');
        $password = $_POST['password'] ?? '';

        if (Auth::attempt($email, $password)) {
            header('Location: /dashboard');
            return;
        }

        View::render('auth/login', ['error' => 'Email o password non corretti.']);
    }

    public function showRegister(): void
    {
        if (Auth::check()) {
            header('Location: /dashboard');
            return;
        }

        View::render('auth/register');
    }

    public function register(): void
    {
        $nome = trim($_POST['nome'] ?? '');
        $cognome = trim($_POST['cognome'] ?? '');
        $email = trim($_POST['email'] ?? '');
        $telefono = trim($_POST['telefono'] ?? '') ?: null;
        $password = $_POST['password'] ?? '';

        if ($nome === '' || $cognome === '' || $email === '' || strlen($password) < 6) {
            View::render('auth/register', ['error' => 'Compila tutti i campi obbligatori (password min. 6 caratteri).']);
            return;
        }

        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            View::render('auth/register', ['error' => 'Indirizzo email non valido.']);
            return;
        }

        if (User::emailExists($email)) {
            View::render('auth/register', ['error' => 'Esiste già un account con questa email.']);
            return;
        }

        $id = User::create($nome, $cognome, $email, $password, $telefono);
        self::inviaEmailBenvenuto(User::find($id));
        Auth::attempt($email, $password);

        header('Location: /dashboard');
    }

    public static function inviaEmailBenvenuto(array $user): void
    {
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        $host = $_SERVER['HTTP_HOST'] ?? 'www.rpfidelity.it';
        $loginUrl = $scheme . '://' . $host . '/login';

        $body = View::renderToString('emails/registrazione', ['user' => $user, 'loginUrl' => $loginUrl]);
        Mailer::send($user['email'], 'Benvenuto in RP Fidelity', Mailer::wrap($body));
    }

    public static function inviaEmailResetPassword(array $user): void
    {
        $token = User::generaResetToken((int) $user['id']);

        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        $host = $_SERVER['HTTP_HOST'] ?? 'www.rpfidelity.it';
        $resetUrl = $scheme . '://' . $host . '/reset-password?token=' . $token;

        $body = View::renderToString('emails/password-reset', ['user' => $user, 'resetUrl' => $resetUrl]);
        Mailer::send($user['email'], 'Reimposta la tua password — RP Fidelity', Mailer::wrap($body));
    }

    public function logout(): void
    {
        Auth::logout();
        header('Location: /login');
    }

    public function showPasswordDimenticata(): void
    {
        if (Auth::check()) {
            header('Location: /dashboard');
            return;
        }

        View::render('auth/password-dimenticata');
    }

    public function inviaPasswordDimenticata(): void
    {
        $email = trim($_POST['email'] ?? '');
        $user = $email !== '' ? User::findByEmail($email) : null;

        if ($user) {
            self::inviaEmailResetPassword($user);
        }

        // Messaggio sempre uguale, esista o meno l'account: evita di rivelare quali email sono registrate.
        View::render('auth/password-dimenticata', [
            'success' => 'Se l\'indirizzo è registrato, riceverai a breve un\'email con le istruzioni per reimpostare la password.',
        ]);
    }

    public function showResetPassword(): void
    {
        $token = $_GET['token'] ?? '';
        $user = $token !== '' ? User::findByResetToken($token) : null;

        if (!$user) {
            View::render('auth/reset-password', ['tokenValido' => false]);
            return;
        }

        View::render('auth/reset-password', ['tokenValido' => true, 'token' => $token]);
    }

    public function aggiornaResetPassword(): void
    {
        $token = $_POST['token'] ?? '';
        $user = $token !== '' ? User::findByResetToken($token) : null;

        if (!$user) {
            View::render('auth/reset-password', ['tokenValido' => false]);
            return;
        }

        $password = $_POST['password'] ?? '';
        $conferma = $_POST['conferma_password'] ?? '';

        if (strlen($password) < 6) {
            View::render('auth/reset-password', ['tokenValido' => true, 'token' => $token, 'error' => 'La password deve avere almeno 6 caratteri.']);
            return;
        }

        if ($password !== $conferma) {
            View::render('auth/reset-password', ['tokenValido' => true, 'token' => $token, 'error' => 'Le due password non coincidono.']);
            return;
        }

        User::resetPasswordConToken((int) $user['id'], $password);

        View::render('auth/login', ['success' => 'Password aggiornata. Ora puoi accedere con la nuova password.']);
    }
}

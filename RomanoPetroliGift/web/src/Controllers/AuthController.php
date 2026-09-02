<?php

namespace App\Controllers;

use App\Core\Auth;
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

        User::create($nome, $cognome, $email, $password, $telefono);
        Auth::attempt($email, $password);

        header('Location: /dashboard');
    }

    public function logout(): void
    {
        Auth::logout();
        header('Location: /login');
    }
}

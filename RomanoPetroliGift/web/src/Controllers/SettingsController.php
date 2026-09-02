<?php
// Copyright (c) Roberto Di Flumeri

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;
use App\Models\User;

class SettingsController
{
    public function index(): void
    {
        Auth::requireCliente();

        $this->render(Auth::user());
    }

    public function aggiornaProfilo(): void
    {
        Auth::requireCliente();

        $id = Auth::userId();
        $utente = Auth::user();

        $nome = trim($_POST['nome'] ?? '');
        $cognome = trim($_POST['cognome'] ?? '');
        $email = trim($_POST['email'] ?? '');
        $telefono = trim($_POST['telefono'] ?? '') ?: null;

        $error = null;

        if ($nome === '' || $cognome === '' || $email === '') {
            $error = 'Compila tutti i campi obbligatori.';
        } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $error = 'Indirizzo email non valido.';
        } elseif (User::emailExists($email, $id)) {
            $error = 'Esiste già un altro account con questa email.';
        }

        if ($error) {
            $this->render(array_merge($utente, compact('nome', 'cognome', 'email', 'telefono')), $error);
            return;
        }

        User::update($id, $nome, $cognome, $email, $telefono, $utente['stato']);

        $this->render(Auth::user(), null, 'Dati aggiornati con successo.');
    }

    public function aggiornaPassword(): void
    {
        Auth::requireCliente();

        $id = Auth::userId();
        $utente = User::find($id);

        $passwordAttuale = $_POST['password_attuale'] ?? '';
        $nuovaPassword = $_POST['nuova_password'] ?? '';
        $confermaPassword = $_POST['conferma_password'] ?? '';

        $error = null;

        if (!password_verify($passwordAttuale, $utente['password_hash'])) {
            $error = 'La password attuale non è corretta.';
        } elseif (strlen($nuovaPassword) < 6) {
            $error = 'La nuova password deve avere almeno 6 caratteri.';
        } elseif ($nuovaPassword !== $confermaPassword) {
            $error = 'Le due password non coincidono.';
        }

        if ($error) {
            $this->render(Auth::user(), null, null, $error);
            return;
        }

        User::updatePassword($id, $nuovaPassword);

        $this->render(Auth::user(), null, null, null, 'Password aggiornata con successo.');
    }

    private function render(
        array $user,
        ?string $error = null,
        ?string $success = null,
        ?string $passwordError = null,
        ?string $passwordSuccess = null
    ): void {
        View::layout('impostazioni', [
            'pageTitle' => 'Impostazioni — RP Fidelity',
            'user' => $user,
            'error' => $error,
            'success' => $success,
            'passwordError' => $passwordError,
            'passwordSuccess' => $passwordSuccess,
        ]);
    }
}

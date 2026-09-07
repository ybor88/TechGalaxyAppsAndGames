<?php
// Copyright (c) Roberto Di Flumeri

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;
use App\Models\Distributore;
use App\Models\MessaggioContatto;

class ContattiController
{
    public function index(): void
    {
        Auth::requireCliente();

        $this->render();
    }

    public function invia(): void
    {
        Auth::requireCliente();

        $user = Auth::user();
        $messaggio = trim($_POST['messaggio'] ?? '');

        $error = null;

        if ($messaggio === '') {
            $error = 'Scrivi un messaggio prima di inviare.';
        } else {
            MessaggioContatto::create($user['id'], $user['nome'] . ' ' . $user['cognome'], $user['email'], $messaggio, 'cliente');
        }

        $this->render($error);
    }

    private function render(?string $error = null): void
    {
        $user = Auth::user();

        View::layout('contatti', [
            'pageTitle' => 'Contatti — RP Fidelity',
            'distributore' => Distributore::unica(),
            'messaggi' => MessaggioContatto::perCliente($user['id']),
            'error' => $error,
        ]);
    }
}

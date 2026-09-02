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

        View::layout('contatti', [
            'pageTitle' => 'Contatti — RP Fidelity',
            'distributore' => Distributore::unica(),
        ]);
    }

    public function invia(): void
    {
        Auth::requireCliente();

        $user = Auth::user();
        $messaggio = trim($_POST['messaggio'] ?? '');

        $error = null;
        $success = null;

        if ($messaggio === '') {
            $error = 'Scrivi un messaggio prima di inviare.';
        } else {
            MessaggioContatto::create($user['id'], $user['nome'] . ' ' . $user['cognome'], $user['email'], $messaggio);
            $success = 'Messaggio inviato! Ti risponderemo al più presto.';
        }

        View::layout('contatti', [
            'pageTitle' => 'Contatti — RP Fidelity',
            'distributore' => Distributore::unica(),
            'error' => $error,
            'success' => $success,
        ]);
    }
}

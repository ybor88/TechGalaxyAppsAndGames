<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;
use App\Models\Rifornimento;

class DashboardController
{
    public function index(): void
    {
        Auth::requireLogin();

        View::layout('dashboard', [
            'pageTitle' => 'Dashboard — RP Fidelity',
            'user' => Auth::user(),
        ]);
    }

    public function rifornimenti(): void
    {
        Auth::requireCliente();

        $dal = $_GET['dal'] ?? '';
        $al = $_GET['al'] ?? '';

        View::layout('rifornimenti', [
            'pageTitle' => 'I miei rifornimenti — RP Fidelity',
            'rifornimenti' => Rifornimento::perUtente(Auth::userId(), $dal ?: null, $al ?: null),
            'dal' => $dal,
            'al' => $al,
        ]);
    }

    public function card(): void
    {
        Auth::requireCliente();

        View::layout('la-mia-card', [
            'pageTitle' => 'La mia Card — RP Fidelity',
            'user' => Auth::user(),
        ]);
    }
}

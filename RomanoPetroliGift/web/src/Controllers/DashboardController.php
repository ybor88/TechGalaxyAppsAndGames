<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Json;
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

    // Usato dal pulsante "Aggiorna" lato cliente per ricaricare il saldo punti via fetch(),
    // senza dover chiudere/riaprire l'app o ricaricare l'intera pagina.
    public function saldoPunti(): void
    {
        Auth::requireCliente();

        $user = Auth::user();

        Json::send([
            'punti_saldo' => (float) $user['punti_saldo'],
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

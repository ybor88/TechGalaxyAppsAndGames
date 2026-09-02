<?php

namespace App\Controllers;

use App\Core\View;

class PublicController
{
    public function scaricaApp(): void
    {
        $apkPath = __DIR__ . '/../../public/downloads/RPFidelity.apk';

        View::layout('scarica-app', [
            'pageTitle' => 'Scarica l\'app — RP Fidelity',
            'apkDisponibile' => is_file($apkPath),
        ]);
    }
}

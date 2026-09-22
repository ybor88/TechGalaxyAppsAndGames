<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;

class PublicController
{
    public function home(): void
    {
        if (Auth::check()) {
            header('Location: /dashboard');
            return;
        }

        View::layout('home', [
            'pageTitle' => 'RP Fidelity — Programma fedeltà Romano Petroli',
            'metaDescription' => 'RP Fidelity: il programma fedeltà Romano Petroli. Registrati, accumula punti a ogni rifornimento e riscattali in buoni benzina.',
            'metaRobots' => 'index, follow',
        ]);
    }

    public function scaricaApp(): void
    {
        $apkPath = $_SERVER['DOCUMENT_ROOT'] . '/downloads/RPFidelity.apk';

        View::layout('scarica-app', [
            'pageTitle' => 'Scarica l\'app — RP Fidelity',
            'metaDescription' => 'Scarica l\'app RP Fidelity per Android o aggiungila alla schermata Home su iPhone/iPad.',
            'metaRobots' => 'index, follow',
            'apkDisponibile' => is_file($apkPath),
        ]);
    }
}

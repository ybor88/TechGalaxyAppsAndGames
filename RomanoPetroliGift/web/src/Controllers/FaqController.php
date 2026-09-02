<?php
// Copyright (c) Roberto Di Flumeri

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;

class FaqController
{
    public function index(): void
    {
        Auth::requireCliente();

        View::layout('faq', [
            'pageTitle' => 'FAQ — RP Fidelity',
        ]);
    }
}

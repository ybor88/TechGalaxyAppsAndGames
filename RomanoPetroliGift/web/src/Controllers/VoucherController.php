<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;
use App\Models\User;
use App\Models\VoucherCatalogo;
use App\Models\VoucherUtente;

class VoucherController
{
    public function index(): void
    {
        Auth::requireCliente();

        View::layout('voucher', [
            'pageTitle' => 'I miei voucher — RP Fidelity',
            'miei' => VoucherUtente::perUtente(Auth::userId()),
        ]);
    }

    public function catalogo(): void
    {
        Auth::requireCliente();

        View::layout('catalogo', [
            'pageTitle' => 'Catalogo premi — RP Fidelity',
            'user' => Auth::user(),
            'catalogo' => VoucherCatalogo::attivi(),
        ]);
    }

    public function riscatta(): void
    {
        Auth::requireCliente();

        $voucherCatalogoId = (int) ($_POST['voucher_catalogo_id'] ?? 0);
        $catalogo = VoucherCatalogo::find($voucherCatalogoId);
        $user = Auth::user();

        $error = null;
        $success = null;

        if (!$catalogo || !$catalogo['attivo']) {
            $error = 'Voucher non disponibile.';
        } elseif ((float) $user['punti_saldo'] < $catalogo['costo_punti']) {
            $error = 'Punti insufficienti per riscattare questo voucher.';
        } else {
            $codice = VoucherUtente::riscatta(Auth::userId(), $voucherCatalogoId);
            User::addPunti(Auth::userId(), $catalogo['costo_punti'], 'addebito', 'Riscatto voucher: ' . $catalogo['nome']);
            $success = 'Voucher riscattato con successo! Codice: ' . $codice;
        }

        View::layout('catalogo', [
            'pageTitle' => 'Catalogo premi — RP Fidelity',
            'user' => Auth::user(),
            'catalogo' => VoucherCatalogo::attivi(),
            'error' => $error,
            'success' => $success,
        ]);
    }
}

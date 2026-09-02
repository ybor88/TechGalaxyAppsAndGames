<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\View;
use App\Models\Distributore;
use App\Models\Rifornimento;
use App\Models\User;
use App\Models\VoucherUtente;

class AdminController
{
    public function statistiche(): void
    {
        Auth::requireAdmin();

        View::layout('admin/statistiche', [
            'pageTitle' => 'Statistiche — RP Fidelity',
            'contaClienti' => User::contaClienti(),
            'puntiInCircolazione' => User::puntiTotaliInCircolazione(),
            'voucherRiscattati' => VoucherUtente::totaleRiscattati(),
            'voucherUsati' => VoucherUtente::totaleUsati(),
            'riscattiPerCatalogo' => VoucherUtente::conteggioPerCatalogo(),
            'registrazioniPerMese' => User::registrazioniPerMese(6),
        ]);
    }

    public function clienti(): void
    {
        Auth::requireAdmin();

        View::layout('admin/clienti', [
            'pageTitle' => 'Gestione clienti — RP Fidelity',
            'clienti' => User::all(),
        ]);
    }

    public function modificaClienteForm(): void
    {
        Auth::requireAdmin();

        $id = (int) ($_GET['id'] ?? 0);
        $cliente = User::find($id);

        if (!$cliente || $cliente['ruolo'] !== 'cliente') {
            header('Location: /admin/clienti');
            return;
        }

        View::layout('admin/modifica-cliente', [
            'pageTitle' => 'Modifica cliente — RP Fidelity',
            'cliente' => $cliente,
        ]);
    }

    public function modificaCliente(): void
    {
        Auth::requireAdmin();

        $id = (int) ($_POST['id'] ?? 0);
        $cliente = User::find($id);

        if (!$cliente || $cliente['ruolo'] !== 'cliente') {
            header('Location: /admin/clienti');
            return;
        }

        $nome = trim($_POST['nome'] ?? '');
        $cognome = trim($_POST['cognome'] ?? '');
        $email = trim($_POST['email'] ?? '');
        $telefono = trim($_POST['telefono'] ?? '') ?: null;
        $stato = ($_POST['stato'] ?? '') === 'sospeso' ? 'sospeso' : 'attivo';

        $error = null;

        if ($nome === '' || $cognome === '' || $email === '') {
            $error = 'Compila tutti i campi obbligatori.';
        } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $error = 'Indirizzo email non valido.';
        } elseif (User::emailExists($email, $id)) {
            $error = 'Esiste già un altro account con questa email.';
        }

        if ($error) {
            View::layout('admin/modifica-cliente', [
                'pageTitle' => 'Modifica cliente — RP Fidelity',
                'cliente' => array_merge($cliente, compact('nome', 'cognome', 'email', 'telefono', 'stato')),
                'error' => $error,
            ]);
            return;
        }

        User::update($id, $nome, $cognome, $email, $telefono, $stato);

        header('Location: /admin/clienti');
    }

    public function eliminaCliente(): void
    {
        Auth::requireAdmin();

        $id = (int) ($_POST['id'] ?? 0);
        $cliente = User::find($id);

        if ($cliente && $cliente['ruolo'] === 'cliente') {
            User::delete($id);
        }

        header('Location: /admin/clienti');
    }

    public function reports(): void
    {
        Auth::requireAdmin();

        $dal = $_GET['dal'] ?? '';
        $al = $_GET['al'] ?? '';

        View::layout('admin/reports', [
            'pageTitle' => 'Reports — RP Fidelity',
            'distributore' => Distributore::unica(),
            'dal' => $dal,
            'al' => $al,
            'rifornimenti' => Rifornimento::ricerca($dal ?: null, $al ?: null),
            'totali' => Rifornimento::totali($dal ?: null, $al ?: null),
        ]);
    }

    public function verificaVoucher(): void
    {
        Auth::requireAdmin();

        $codice = trim($_GET['codice'] ?? '');
        $voucher = null;
        $error = null;

        if ($codice !== '') {
            $voucher = VoucherUtente::findByCodice($codice);
            if (!$voucher) {
                $error = 'Nessun voucher trovato con questo codice.';
            }
        }

        View::layout('admin/verifica-voucher', [
            'pageTitle' => 'Verifica voucher — RP Fidelity',
            'codice' => $codice,
            'voucher' => $voucher,
            'error' => $error,
        ]);
    }

    public function usaVoucher(): void
    {
        Auth::requireAdmin();

        $voucherId = (int) ($_POST['voucher_id'] ?? 0);
        VoucherUtente::segnaUsato($voucherId);

        header('Location: /admin/verifica-voucher');
    }

    private function cart(): array
    {
        return $_SESSION['rifornimento_cart'] ?? ['cliente_id' => null, 'vouchers' => []];
    }

    private function salvaCart(array $cart): void
    {
        $_SESSION['rifornimento_cart'] = $cart;
    }

    private function resetCart(): void
    {
        unset($_SESSION['rifornimento_cart']);
    }

    private function renderRegistraRifornimento(?string $error = null, ?string $success = null): void
    {
        $cart = $this->cart();
        $cliente = $cart['cliente_id'] ? User::find($cart['cliente_id']) : null;

        View::layout('admin/registra-rifornimento', [
            'pageTitle' => 'Registra rifornimento — RP Fidelity',
            'cliente' => $cliente,
            'vouchers' => $cart['vouchers'],
            'error' => $error,
            'success' => $success,
        ]);
    }

    public function nuovoRifornimento(): void
    {
        Auth::requireAdmin();

        $this->renderRegistraRifornimento();
    }

    public function identificaCliente(): void
    {
        Auth::requireAdmin();

        $codiceCard = trim($_POST['codice_card'] ?? '');
        $cliente = $codiceCard !== '' ? User::findByCodiceCard($codiceCard) : null;

        if (!$cliente || $cliente['ruolo'] !== 'cliente') {
            $this->renderRegistraRifornimento('Nessun cliente trovato con questo codice card.');
            return;
        }

        $this->salvaCart(['cliente_id' => (int) $cliente['id'], 'vouchers' => []]);
        $this->renderRegistraRifornimento();
    }

    public function cambiaCliente(): void
    {
        Auth::requireAdmin();

        $this->resetCart();

        header('Location: /admin/rifornimenti/nuovo');
    }

    public function aggiungiVoucher(): void
    {
        Auth::requireAdmin();

        $cart = $this->cart();

        if (!$cart['cliente_id']) {
            $this->renderRegistraRifornimento('Identifica prima il cliente tramite il codice card.');
            return;
        }

        $codice = trim($_POST['codice_voucher'] ?? '');
        $voucher = $codice !== '' ? VoucherUtente::findByCodice($codice) : null;

        if (!$voucher) {
            $this->renderRegistraRifornimento('Nessun voucher trovato con questo codice.');
            return;
        }
        if ((int) $voucher['user_id'] !== $cart['cliente_id']) {
            $this->renderRegistraRifornimento('Questo voucher non appartiene al cliente identificato.');
            return;
        }
        if ($voucher['stato'] !== 'attivo') {
            $this->renderRegistraRifornimento('Questo voucher non è più utilizzabile (stato: ' . $voucher['stato'] . ').');
            return;
        }
        foreach ($cart['vouchers'] as $v) {
            if ($v['id'] === (int) $voucher['id']) {
                $this->renderRegistraRifornimento('Voucher già aggiunto a questo rifornimento.');
                return;
            }
        }

        $cart['vouchers'][] = [
            'id' => (int) $voucher['id'],
            'codice' => $voucher['codice_voucher'],
            'nome' => $voucher['nome'],
            'importo_premio' => (float) $voucher['importo_premio'],
        ];
        $this->salvaCart($cart);

        $this->renderRegistraRifornimento();
    }

    public function rimuoviVoucher(): void
    {
        Auth::requireAdmin();

        $cart = $this->cart();
        $voucherId = (int) ($_POST['voucher_id'] ?? 0);

        $cart['vouchers'] = array_values(array_filter(
            $cart['vouchers'],
            fn (array $v) => $v['id'] !== $voucherId
        ));
        $this->salvaCart($cart);

        $this->renderRegistraRifornimento();
    }

    public function confermaRifornimento(): void
    {
        Auth::requireAdmin();

        $cart = $this->cart();
        $cliente = $cart['cliente_id'] ? User::find($cart['cliente_id']) : null;
        $importo = (float) str_replace(',', '.', $_POST['importo'] ?? '0');

        if (!$cliente) {
            $this->renderRegistraRifornimento('Identifica prima il cliente tramite il codice card.');
            return;
        }
        if ($importo <= 0) {
            $this->renderRegistraRifornimento('Inserisci un importo maggiore di zero.');
            return;
        }
        if ($importo > Rifornimento::IMPORTO_MASSIMO) {
            $this->renderRegistraRifornimento(sprintf(
                'Importo troppo alto: massimo %s€ per singola operazione. Per importi maggiori registra più rifornimenti separati.',
                number_format(Rifornimento::IMPORTO_MASSIMO, 0, ',', '.')
            ));
            return;
        }

        $importoVoucher = array_sum(array_column($cart['vouchers'], 'importo_premio'));
        if ($importoVoucher > $importo) {
            $this->renderRegistraRifornimento('Il valore dei voucher applicati supera l\'importo del rifornimento.');
            return;
        }

        $distributore = Distributore::unica();
        $rifornimento = Rifornimento::create($cliente['id'], (int) $distributore['id'], $importo, $importoVoucher);

        foreach ($cart['vouchers'] as $v) {
            VoucherUtente::segnaUsato($v['id'], $rifornimento['id']);
        }

        User::addPunti(
            $cliente['id'],
            $rifornimento['punti_maturati'],
            'accredito',
            'Rifornimento ' . $rifornimento['codice_rifornimento'],
            $rifornimento['id']
        );

        $success = sprintf(
            'Rifornimento %s registrato: %s€ pagati (di cui %s€ voucher), %s punti accreditati a %s %s.',
            $rifornimento['codice_rifornimento'],
            number_format($rifornimento['importo_pagato'], 2, ',', '.'),
            number_format($importoVoucher, 2, ',', '.'),
            format_punti($rifornimento['punti_maturati']),
            $cliente['nome'],
            $cliente['cognome']
        );

        $this->resetCart();
        $this->renderRegistraRifornimento(null, $success);
    }
}

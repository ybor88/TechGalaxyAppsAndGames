<?php

namespace App\Controllers\Api;

use App\Core\ApiAuth;
use App\Core\Json;
use App\Models\Distributore;
use App\Models\MessaggioContatto;
use App\Models\Rifornimento;
use App\Models\User;
use App\Models\VoucherCatalogo;
use App\Models\VoucherUtente;

class ClienteApiController
{
    public function rifornimenti(array $query): void
    {
        $user = ApiAuth::requireCliente();

        $rifornimenti = Rifornimento::perUtente((int) $user['id'], $query['dal'] ?? null, $query['al'] ?? null);

        Json::send(['rifornimenti' => array_map(static fn (array $r) => [
            'id' => (int) $r['id'],
            'data_ora' => $r['data_ora'],
            'importo' => (float) $r['importo'],
            'importo_pagato' => (float) $r['importo_pagato'],
            'importo_voucher' => (float) $r['importo_voucher'],
            'punti_maturati' => (float) $r['punti_maturati'],
        ], $rifornimenti)]);
    }

    public function catalogo(): void
    {
        ApiAuth::requireUser();

        $catalogo = VoucherCatalogo::attivi();

        Json::send(['catalogo' => array_map(static fn (array $v) => [
            'id' => (int) $v['id'],
            'nome' => $v['nome'],
            'costo_punti' => (int) $v['costo_punti'],
            'importo_premio' => (float) $v['importo_premio'],
        ], $catalogo)]);
    }

    public function mieiVoucher(): void
    {
        $user = ApiAuth::requireCliente();

        $voucher = VoucherUtente::perUtente((int) $user['id']);

        Json::send(['voucher' => array_map(static fn (array $v) => [
            'id' => (int) $v['id'],
            'codice_voucher' => $v['codice_voucher'],
            'nome' => $v['nome'],
            'importo_premio' => (float) $v['importo_premio'],
            'data_scadenza' => $v['data_scadenza'],
            'stato' => $v['stato'],
        ], $voucher)]);
    }

    public function riscatta(array $input): void
    {
        $user = ApiAuth::requireCliente();

        $voucherCatalogoId = (int) ($input['voucher_catalogo_id'] ?? 0);
        $catalogo = VoucherCatalogo::find($voucherCatalogoId);

        if (!$catalogo || !$catalogo['attivo']) {
            Json::error('Voucher non disponibile.', 422);
        }
        if ((float) $user['punti_saldo'] < $catalogo['costo_punti']) {
            Json::error('Punti insufficienti per riscattare questo voucher.', 422);
        }

        $codice = VoucherUtente::riscatta((int) $user['id'], $voucherCatalogoId);
        User::addPunti((int) $user['id'], (float) $catalogo['costo_punti'], 'addebito', 'Riscatto voucher: ' . $catalogo['nome']);

        Json::send(['success' => true, 'codice' => $codice]);
    }

    public function contatti(): void
    {
        ApiAuth::requireCliente();

        $distributore = Distributore::unica();

        Json::send(['distributore' => $distributore ? [
            'nome' => $distributore['nome'],
            'indirizzo' => $distributore['indirizzo'],
            'citta' => $distributore['citta'],
        ] : null]);
    }

    public function inviaContatto(array $input): void
    {
        $user = ApiAuth::requireCliente();

        $messaggio = trim($input['messaggio'] ?? '');
        if ($messaggio === '') {
            Json::error('Scrivi un messaggio prima di inviare.', 422);
        }

        MessaggioContatto::create((int) $user['id'], $user['nome'] . ' ' . $user['cognome'], $user['email'], $messaggio);

        Json::send(['success' => true]);
    }

    public function aggiornaProfilo(array $input): void
    {
        $user = ApiAuth::requireCliente();

        $id = (int) $user['id'];
        $nome = trim($input['nome'] ?? '');
        $cognome = trim($input['cognome'] ?? '');
        $email = trim($input['email'] ?? '');
        $telefono = trim($input['telefono'] ?? '') ?: null;

        if ($nome === '' || $cognome === '' || $email === '') {
            Json::error('Compila tutti i campi obbligatori.', 422);
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            Json::error('Indirizzo email non valido.', 422);
        }
        if (User::emailExists($email, $id)) {
            Json::error('Esiste già un altro account con questa email.', 422);
        }

        User::update($id, $nome, $cognome, $email, $telefono, $user['stato']);

        Json::send(['user' => AuthApiController::publicUser(User::find($id))]);
    }

    public function aggiornaPassword(array $input): void
    {
        $user = ApiAuth::requireCliente();

        $passwordAttuale = $input['password_attuale'] ?? '';
        $nuovaPassword = $input['nuova_password'] ?? '';
        $confermaPassword = $input['conferma_password'] ?? '';

        if (!password_verify($passwordAttuale, $user['password_hash'])) {
            Json::error('La password attuale non è corretta.', 422);
        }
        if (strlen($nuovaPassword) < 6) {
            Json::error('La nuova password deve avere almeno 6 caratteri.', 422);
        }
        if ($nuovaPassword !== $confermaPassword) {
            Json::error('Le due password non coincidono.', 422);
        }

        User::updatePassword((int) $user['id'], $nuovaPassword);

        Json::send(['success' => true]);
    }
}

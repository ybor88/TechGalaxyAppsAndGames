<?php

namespace App\Controllers\Api;

use App\Core\ApiAuth;
use App\Core\Json;
use App\Models\Distributore;
use App\Models\Rifornimento;
use App\Models\User;
use App\Models\VoucherUtente;

class AdminApiController
{
    public function cercaCliente(array $query): void
    {
        ApiAuth::requireAdmin();

        $codiceCard = trim($query['codice_card'] ?? '');
        $cliente = $codiceCard !== '' ? User::findByCodiceCard($codiceCard) : null;

        if (!$cliente || $cliente['ruolo'] !== 'cliente') {
            Json::error('Nessun cliente trovato con questo codice card.', 404);
        }

        Json::send(['cliente' => AuthApiController::publicUser($cliente)]);
    }

    public function verificaVoucherPerCarrello(array $query): void
    {
        ApiAuth::requireAdmin();

        $codice = trim($query['codice'] ?? '');
        $codiceCard = trim($query['codice_card'] ?? '');

        $voucher = $codice !== '' ? VoucherUtente::findByCodice($codice) : null;
        if (!$voucher) {
            Json::error('Nessun voucher trovato con questo codice.', 404);
        }

        $cliente = $codiceCard !== '' ? User::findByCodiceCard($codiceCard) : null;
        if (!$cliente || (int) $voucher['user_id'] !== (int) $cliente['id']) {
            Json::error('Questo voucher non appartiene al cliente identificato.', 409);
        }
        if ($voucher['stato'] !== 'attivo') {
            Json::error('Questo voucher non è più utilizzabile (stato: ' . $voucher['stato'] . ').', 409);
        }

        Json::send(['voucher' => [
            'id' => (int) $voucher['id'],
            'codice_voucher' => $voucher['codice_voucher'],
            'nome' => $voucher['nome'],
            'importo_premio' => (float) $voucher['importo_premio'],
        ]]);
    }

    public function confermaRifornimento(array $input): void
    {
        ApiAuth::requireAdmin();

        $codiceCard = trim($input['codice_card'] ?? '');
        $importo = (float) ($input['importo'] ?? 0);
        $voucherCodici = is_array($input['voucher_codici'] ?? null) ? $input['voucher_codici'] : [];

        $cliente = $codiceCard !== '' ? User::findByCodiceCard($codiceCard) : null;
        if (!$cliente || $cliente['ruolo'] !== 'cliente') {
            Json::error('Nessun cliente trovato con questo codice card.', 404);
        }
        if ($importo <= 0) {
            Json::error('Inserisci un importo maggiore di zero.', 422);
        }
        if ($importo > Rifornimento::IMPORTO_MASSIMO) {
            Json::error(sprintf(
                'Importo troppo alto: massimo %s€ per singola operazione.',
                number_format(Rifornimento::IMPORTO_MASSIMO, 0, ',', '.')
            ), 422);
        }

        $voucherRows = [];
        $importoVoucher = 0.0;
        foreach ($voucherCodici as $codice) {
            $voucher = VoucherUtente::findByCodice((string) $codice);
            if (!$voucher || (int) $voucher['user_id'] !== (int) $cliente['id'] || $voucher['stato'] !== 'attivo') {
                Json::error('Voucher non valido: ' . $codice, 422);
            }
            $voucherRows[] = $voucher;
            $importoVoucher += (float) $voucher['importo_premio'];
        }

        if ($importoVoucher > $importo) {
            Json::error('Il valore dei voucher applicati supera l\'importo del rifornimento.', 422);
        }

        $distributore = Distributore::unica();
        $rifornimento = Rifornimento::create((int) $cliente['id'], (int) $distributore['id'], $importo, $importoVoucher);

        foreach ($voucherRows as $v) {
            VoucherUtente::segnaUsato((int) $v['id'], $rifornimento['id']);
        }

        User::addPunti(
            (int) $cliente['id'],
            $rifornimento['punti_maturati'],
            'accredito',
            'Rifornimento ' . $rifornimento['codice_rifornimento'],
            $rifornimento['id']
        );

        Json::send([
            'success' => true,
            'codice_rifornimento' => $rifornimento['codice_rifornimento'],
            'importo_pagato' => $rifornimento['importo_pagato'],
            'importo_voucher' => $importoVoucher,
            'punti_maturati' => $rifornimento['punti_maturati'],
        ]);
    }

    public function reports(array $query): void
    {
        ApiAuth::requireAdmin();

        $rifornimenti = Rifornimento::ricerca($query['dal'] ?? null, $query['al'] ?? null);
        $totali = Rifornimento::totali($query['dal'] ?? null, $query['al'] ?? null);

        Json::send([
            'rifornimenti' => array_map(static fn (array $r) => [
                'id' => (int) $r['id'],
                'data_ora' => $r['data_ora'],
                'codice_rifornimento' => $r['codice_rifornimento'],
                'cliente_nome' => $r['cliente_nome'],
                'cliente_cognome' => $r['cliente_cognome'],
                'importo' => (float) $r['importo'],
                'importo_pagato' => (float) $r['importo_pagato'],
                'importo_voucher' => (float) $r['importo_voucher'],
            ], $rifornimenti),
            'totali' => [
                'totale_rifornimenti' => (float) $totali['totale_rifornimenti'],
                'totale_voucher' => (float) $totali['totale_voucher'],
                'saldo' => (float) $totali['saldo'],
            ],
        ]);
    }

    public function verificaVoucher(array $query): void
    {
        ApiAuth::requireAdmin();

        $codice = trim($query['codice'] ?? '');
        $voucher = $codice !== '' ? VoucherUtente::findByCodice($codice) : null;

        if (!$voucher) {
            Json::error('Nessun voucher trovato con questo codice.', 404);
        }

        Json::send(['voucher' => [
            'id' => (int) $voucher['id'],
            'codice_voucher' => $voucher['codice_voucher'],
            'nome' => $voucher['nome'],
            'importo_premio' => (float) $voucher['importo_premio'],
            'data_scadenza' => $voucher['data_scadenza'],
            'stato' => $voucher['stato'],
            'cliente_nome' => $voucher['cliente_nome'],
            'cliente_cognome' => $voucher['cliente_cognome'],
        ]]);
    }

    public function usaVoucher(array $input): void
    {
        ApiAuth::requireAdmin();

        $voucherId = (int) ($input['voucher_id'] ?? 0);
        VoucherUtente::segnaUsato($voucherId);

        Json::send(['success' => true]);
    }

    public function statistiche(): void
    {
        ApiAuth::requireAdmin();

        Json::send([
            'conta_clienti' => User::contaClienti(),
            'punti_in_circolazione' => User::puntiTotaliInCircolazione(),
            'voucher_riscattati' => VoucherUtente::totaleRiscattati(),
            'voucher_usati' => VoucherUtente::totaleUsati(),
            'riscatti_per_catalogo' => VoucherUtente::conteggioPerCatalogo(),
            'registrazioni_per_mese' => User::registrazioniPerMese(6),
        ]);
    }

    public function listaClienti(): void
    {
        ApiAuth::requireAdmin();

        Json::send(['clienti' => array_map(static fn (array $u) => [
            'id' => (int) $u['id'],
            'nome' => $u['nome'],
            'cognome' => $u['cognome'],
            'email' => $u['email'],
            'telefono' => $u['telefono'],
            'ruolo' => $u['ruolo'],
            'punti_saldo' => (float) $u['punti_saldo'],
            'stato' => $u['stato'],
            'codice_card' => $u['codice_card'],
            'data_registrazione' => $u['data_registrazione'],
        ], User::all())]);
    }

    public function aggiornaCliente(array $input): void
    {
        ApiAuth::requireAdmin();

        $id = (int) ($input['id'] ?? 0);
        $cliente = User::find($id);

        if (!$cliente || $cliente['ruolo'] !== 'cliente') {
            Json::error('Cliente non trovato.', 404);
        }

        $nome = trim($input['nome'] ?? '');
        $cognome = trim($input['cognome'] ?? '');
        $email = trim($input['email'] ?? '');
        $telefono = trim($input['telefono'] ?? '') ?: null;
        $stato = ($input['stato'] ?? '') === 'sospeso' ? 'sospeso' : 'attivo';

        if ($nome === '' || $cognome === '' || $email === '') {
            Json::error('Compila tutti i campi obbligatori.', 422);
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            Json::error('Indirizzo email non valido.', 422);
        }
        if (User::emailExists($email, $id)) {
            Json::error('Esiste già un altro account con questa email.', 422);
        }

        User::update($id, $nome, $cognome, $email, $telefono, $stato);

        Json::send(['success' => true]);
    }

    public function eliminaCliente(array $input): void
    {
        ApiAuth::requireAdmin();

        $id = (int) ($input['id'] ?? 0);
        $cliente = User::find($id);

        if ($cliente && $cliente['ruolo'] === 'cliente') {
            User::delete($id);
        }

        Json::send(['success' => true]);
    }
}

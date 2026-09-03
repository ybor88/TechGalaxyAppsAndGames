<?php

namespace App\Controllers;

use App\Core\Auth;
use App\Core\SimplePdf;
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

    public function pdf(): void
    {
        Auth::requireCliente();

        $codice = trim($_GET['codice'] ?? '');
        $voucher = $codice !== '' ? VoucherUtente::findByCodice($codice) : null;

        if (!$voucher || (int) $voucher['user_id'] !== Auth::userId()) {
            http_response_code(404);
            echo 'Voucher non trovato.';
            return;
        }

        $bytes = $this->buildVoucherPdf($voucher);

        header('Content-Type: application/pdf');
        header('Content-Disposition: attachment; filename="voucher-' . $voucher['codice_voucher'] . '.pdf"');
        header('Content-Length: ' . strlen($bytes));
        echo $bytes;
    }

    private function buildVoucherPdf(array $voucher): string
    {
        $pdf = new SimplePdf();

        // Intestazione
        $pdf->setColor(0.043, 0.078, 0.251);
        $pdf->rect(0, 780, 595.28, 61.89, false, true);
        $pdf->setColor(1, 1, 1);
        $pdf->text(40, 805, 'RP FIDELITY', 'F2', 22);
        $pdf->text(40, 788, 'Romano Petroli', 'F1', 11);

        // Titolo
        $pdf->setColor(0.11, 0.13, 0.25);
        $pdf->text(40, 740, 'Voucher Premio', 'F2', 20);
        $pdf->setColor(0, 0, 0);
        $pdf->line(40, 730, 555, 730);

        $pdf->text(40, 700, sprintf('%s — %s €', $voucher['nome'], number_format((float) $voucher['importo_premio'], 2, ',', '.')), 'F1', 14);

        // Codice voucher, in evidenza
        $pdf->setColor(0.4, 0.4, 0.4);
        $pdf->text(40, 675, 'Codice voucher (mostralo al gestore o comunicalo a voce)', 'F1', 10);
        $pdf->setColor(0, 0, 0);
        $pdf->rect(35, 610, 525, 45, true, false);
        $pdf->text(50, 625, $voucher['codice_voucher'], 'F2', 24);

        $pdf->text(40, 580, sprintf('Cliente: %s %s', $voucher['cliente_nome'], $voucher['cliente_cognome']), 'F1', 12);
        $pdf->text(40, 560, 'Data riscatto: ' . date('d/m/Y', strtotime($voucher['data_riscatto'])), 'F1', 12);
        $pdf->text(40, 540, 'Scadenza: ' . date('d/m/Y', strtotime($voucher['data_scadenza'])), 'F1', 12);
        $pdf->text(40, 520, 'Stato: ' . ucfirst($voucher['stato']), 'F1', 12);

        $pdf->setColor(0.3, 0.3, 0.3);
        $pdf->text(40, 480, 'Mostra questo codice al gestore Romano Petroli al momento del rifornimento:', 'F1', 10);
        $pdf->text(40, 466, 'verrà scalato dall\'importo da pagare. Il voucher non è più utilizzabile una volta usato.', 'F1', 10);

        $pdf->line(40, 60, 555, 60);
        $pdf->setColor(0.5, 0.5, 0.5);
        $pdf->text(40, 45, 'RP Fidelity — Romano Petroli. Documento generato il ' . date('d/m/Y H:i') . '.', 'F1', 8);
        $pdf->text(40, 32, '© Roberto Di Flumeri', 'F1', 8);

        return $pdf->output();
    }
}

<?php

use App\Models\Rifornimento;

/** @var array|null $cliente */
/** @var array $vouchers */
/** @var string|null $error */
/** @var string|null $success */
$importoMassimo = Rifornimento::IMPORTO_MASSIMO;
$totaleVoucher = array_sum(array_column($vouchers, 'importo_premio'));
?>
<div class="rp-card">
    <h1 class="rp-title">Registra rifornimento</h1>
    <p class="rp-subtitle">
        1 punto ogni 10&euro; effettivamente pagati — massimo <?= number_format($importoMassimo, 0, ',', '.') ?>&euro; per singola operazione
    </p>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>
    <?php if (!empty($success)): ?>
        <div class="rp-alert rp-alert-success"><?= htmlspecialchars($success) ?></div>
    <?php endif; ?>

    <?php if (!$cliente): ?>
        <!-- Passo 1: identificazione cliente tramite codice card -->
        <form class="rp-form" method="post" action="/admin/rifornimenti/nuovo/cliente" style="max-width: 420px;">
            <label for="codice_card">Codice Card Cliente</label>
            <div class="rp-input-scan">
                <input type="text" id="codice_card" name="codice_card" required placeholder="es. RPF1A2B3C4D" autofocus>
                <button type="button" class="rp-btn-scan" onclick="rpOpenScanner('codice_card')" title="Scansiona QR">&#128247;</button>
            </div>
            <div style="margin-top: 20px;">
                <button type="submit" class="rp-btn">Identifica cliente</button>
            </div>
        </form>
    <?php else: ?>
        <!-- Cliente identificato -->
        <div class="rp-cliente-box">
            <div>
                <strong><?= htmlspecialchars($cliente['nome'] . ' ' . $cliente['cognome']) ?></strong><br>
                Saldo attuale: <?= format_punti((float) $cliente['punti_saldo']) ?> punti
            </div>
            <form method="post" action="/admin/rifornimenti/nuovo/cambia-cliente">
                <button type="submit" class="rp-btn rp-btn-outline">Cambia cliente</button>
            </form>
        </div>

        <h2 class="rp-title" style="font-size:16px;">Elenco voucher applicati</h2>
        <?php if (empty($vouchers)): ?>
            <p style="color:#5b6180;">Nessun voucher inserito.</p>
        <?php else: ?>
            <?php foreach ($vouchers as $v): ?>
                <div class="rp-voucher-riga">
                    <span>
                        <strong><?= htmlspecialchars($v['nome']) ?></strong>
                        — <?= number_format($v['importo_premio'], 2, ',', '.') ?>&euro;
                        <span style="font-family:monospace; color:#5b6180;"> (<?= htmlspecialchars($v['codice']) ?>)</span>
                    </span>
                    <form method="post" action="/admin/rifornimenti/nuovo/voucher/rimuovi">
                        <input type="hidden" name="voucher_id" value="<?= (int) $v['id'] ?>">
                        <button type="submit" class="rp-voucher-riga-rimuovi">Rimuovi</button>
                    </form>
                </div>
            <?php endforeach; ?>
            <p><strong>Totale voucher: <?= number_format($totaleVoucher, 2, ',', '.') ?>&euro;</strong></p>
        <?php endif; ?>

        <form class="rp-form" method="post" action="/admin/rifornimenti/nuovo/voucher" style="max-width: 420px; margin-top: 12px;">
            <label for="codice_voucher">Utilizza un voucher</label>
            <div class="rp-input-scan">
                <input type="text" id="codice_voucher" name="codice_voucher" placeholder="codice voucher">
                <button type="button" class="rp-btn-scan" onclick="rpOpenScanner('codice_voucher')" title="Scansiona QR">&#128247;</button>
                <button type="submit" class="rp-btn" style="flex-shrink:0;">Aggiungi</button>
            </div>
        </form>

        <hr style="margin: 24px 0; border: none; border-top: 1px solid var(--rp-gray-border);">

        <form class="rp-form" method="post" action="/admin/rifornimenti/nuovo/conferma" style="max-width: 420px;">
            <label for="importo">Importo rifornimento (&euro;)</label>
            <input type="number" id="importo" name="importo" min="0.01" max="<?= $importoMassimo ?>" step="0.01" required
                placeholder="es. 45.00"
                oninvalid="rpImportoInvalid(this)"
                oninput="this.setCustomValidity('')">

            <?php if ($totaleVoucher > 0): ?>
                <p style="margin-top:8px; color:#5b6180;">Verranno scalati <?= number_format($totaleVoucher, 2, ',', '.') ?>&euro; di voucher dall'importo inserito.</p>
            <?php endif; ?>

            <div style="margin-top: 20px;">
                <button type="submit" class="rp-btn">Conferma Rifornimento</button>
            </div>
        </form>
    <?php endif; ?>
</div>

<!-- Modale scanner QR fotocamera -->
<div id="rp-scanner-modal" class="rp-modal-overlay">
    <div class="rp-modal-box">
        <h3>Inquadra il QR code</h3>
        <div id="rp-qr-reader"></div>
        <button type="button" class="rp-modal-close" onclick="rpCloseScanner()">Annulla</button>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/html5-qrcode@2.3.8/html5-qrcode.min.js"></script>
<script>
function rpImportoInvalid(el) {
    if (el.validity.valueMissing) {
        el.setCustomValidity('Inserisci l\'importo del rifornimento.');
    } else if (el.validity.rangeOverflow) {
        el.setCustomValidity('Importo troppo alto: massimo <?= number_format($importoMassimo, 0, ',', '.') ?>€ per singola operazione.');
    } else if (el.validity.rangeUnderflow) {
        el.setCustomValidity('Inserisci un importo maggiore di zero.');
    } else {
        el.setCustomValidity('Importo non valido.');
    }
}

let rpScannerTargetId = null;
let rpHtml5QrCode = null;

function rpOpenScanner(targetInputId) {
    rpScannerTargetId = targetInputId;
    document.getElementById('rp-scanner-modal').style.display = 'flex';
    rpHtml5QrCode = new Html5Qrcode('rp-qr-reader');
    rpHtml5QrCode.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: 220 },
        function (decodedText) {
            document.getElementById(rpScannerTargetId).value = decodedText;
            rpCloseScanner();
        },
        function () {}
    ).catch(function (err) {
        alert('Impossibile accedere alla fotocamera: ' + err);
        rpCloseScanner();
    });
}

function rpCloseScanner() {
    if (rpHtml5QrCode) {
        rpHtml5QrCode.stop().then(function () {
            rpHtml5QrCode.clear();
        }).catch(function () {});
    }
    document.getElementById('rp-scanner-modal').style.display = 'none';
}
</script>

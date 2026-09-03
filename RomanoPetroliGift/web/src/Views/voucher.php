<?php
/** @var array $miei */
?>
<div class="rp-card">
    <h1 class="rp-title">I miei voucher</h1>
    <p class="rp-subtitle">I buoni che hai riscattato con i tuoi punti</p>

    <?php if (empty($miei)): ?>
        <p>Non hai ancora riscattato nessun voucher.</p>
        <a href="/catalogo" class="rp-btn" style="display:inline-block; margin-top:8px;">Vai al catalogo premi</a>
    <?php endif; ?>

    <div class="rp-voucher-grid">
        <?php foreach ($miei as $v): ?>
            <div class="rp-voucher-card">
                <img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=<?= urlencode($v['codice_voucher']) ?>"
                     alt="QR voucher" style="align-self:center;">
                <div><strong><?= htmlspecialchars($v['nome']) ?></strong> — <?= number_format((float) $v['importo_premio'], 2, ',', '.') ?> &euro;</div>
                <div style="font-family: monospace; font-size: 13px;"><?= htmlspecialchars($v['codice_voucher']) ?></div>
                <div>Scadenza: <?= htmlspecialchars(date('d/m/Y', strtotime($v['data_scadenza']))) ?></div>
                <span class="rp-badge-stato rp-badge-<?= htmlspecialchars($v['stato']) ?>">
                    <?= htmlspecialchars(ucfirst($v['stato'])) ?>
                </span>
                <a href="/voucher/pdf?codice=<?= urlencode($v['codice_voucher']) ?>" class="rp-btn" style="text-align:center; margin-top:6px;">Scarica PDF</a>
            </div>
        <?php endforeach; ?>
    </div>
</div>

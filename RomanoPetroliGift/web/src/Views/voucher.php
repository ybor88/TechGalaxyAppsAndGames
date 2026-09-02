<?php
/** @var array $catalogo */
/** @var array $miei */
/** @var array $user */
/** @var string|null $error */
/** @var string|null $success */
?>
<?php if (!empty($error)): ?>
    <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
<?php endif; ?>
<?php if (!empty($success)): ?>
    <div class="rp-alert rp-alert-success"><?= htmlspecialchars($success) ?></div>
<?php endif; ?>

<div class="rp-card">
    <h1 class="rp-title">Catalogo voucher</h1>
    <p class="rp-subtitle">Il tuo saldo: <strong><?= format_punti((float) $user['punti_saldo']) ?> punti</strong></p>

    <div class="rp-voucher-grid">
        <?php foreach ($catalogo as $v): ?>
            <?php
                $mancanti = $v['costo_punti'] - (float) $user['punti_saldo'];
                $raggiungibile = $mancanti <= 0;
            ?>
            <div class="rp-voucher-card" style="<?= $raggiungibile ? '' : 'opacity:0.55;' ?>">
                <div class="rp-voucher-amount"><?= number_format((float) $v['importo_premio'], 2, ',', '.') ?> &euro;</div>
                <div><strong><?= htmlspecialchars($v['nome']) ?></strong></div>
                <div>Costo: <?= (int) $v['costo_punti'] ?> punti</div>
                <?php if (!$raggiungibile): ?>
                    <div style="color:#a12622; font-size:13px;">Ti mancano <?= format_punti($mancanti) ?> punti</div>
                <?php endif; ?>
                <form method="post" action="/voucher/riscatta">
                    <input type="hidden" name="voucher_catalogo_id" value="<?= (int) $v['id'] ?>">
                    <button type="submit" class="rp-btn" style="width:100%;" <?= $raggiungibile ? '' : 'disabled' ?>>
                        <?= $raggiungibile ? 'Riscatta' : 'Punti insufficienti' ?>
                    </button>
                </form>
            </div>
        <?php endforeach; ?>
    </div>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:18px;">I miei voucher</h2>

    <?php if (empty($miei)): ?>
        <p>Non hai ancora riscattato nessun voucher.</p>
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
            </div>
        <?php endforeach; ?>
    </div>
</div>

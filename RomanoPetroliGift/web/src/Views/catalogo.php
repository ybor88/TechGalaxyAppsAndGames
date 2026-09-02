<?php
// Copyright (c) Roberto Di Flumeri
/** @var array $catalogo */
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
    <h1 class="rp-title">Catalogo premi</h1>
    <p class="rp-subtitle">Il tuo saldo: <strong><?= format_punti((float) $user['punti_saldo']) ?> punti</strong></p>

    <?php if (empty($catalogo)): ?>
        <p>Nessun premio disponibile al momento.</p>
    <?php endif; ?>

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
    <a href="/voucher" class="rp-btn rp-btn-outline">Vai ai miei voucher &rarr;</a>
</div>

<?php
/** @var array|null $voucher */
/** @var string|null $error */
/** @var string|null $success */
/** @var string|null $codice */
?>
<div class="rp-card">
    <h1 class="rp-title">Verifica voucher</h1>
    <p class="rp-subtitle">Inserisci il codice del voucher mostrato dal cliente per validarlo</p>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>
    <?php if (!empty($success)): ?>
        <div class="rp-alert rp-alert-success"><?= htmlspecialchars($success) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="get" action="/admin/verifica-voucher">
        <label for="codice">Codice voucher</label>
        <input type="text" id="codice" name="codice" value="<?= htmlspecialchars($codice ?? '') ?>" autofocus>
        <div style="margin-top: 16px;">
            <button type="submit" class="rp-btn">Cerca</button>
        </div>
    </form>

    <?php if ($voucher): ?>
        <div class="rp-card" style="margin-top: 20px; background:#f9fafc;">
            <p><strong>Cliente:</strong> <?= htmlspecialchars($voucher['cliente_nome'] . ' ' . $voucher['cliente_cognome']) ?></p>
            <p><strong>Voucher:</strong> <?= htmlspecialchars($voucher['nome']) ?> (<?= number_format((float) $voucher['importo_premio'], 2, ',', '.') ?> &euro;)</p>
            <p><strong>Scadenza:</strong> <?= htmlspecialchars(date('d/m/Y', strtotime($voucher['data_scadenza']))) ?></p>
            <p><strong>Stato:</strong>
                <span class="rp-badge-stato rp-badge-<?= htmlspecialchars($voucher['stato']) ?>">
                    <?= htmlspecialchars(ucfirst($voucher['stato'])) ?>
                </span>
            </p>

            <?php if ($voucher['stato'] === 'attivo'): ?>
                <form method="post" action="/admin/verifica-voucher/usa">
                    <input type="hidden" name="voucher_id" value="<?= (int) $voucher['id'] ?>">
                    <button type="submit" class="rp-btn">Segna come usato / eroga rifornimento</button>
                </form>
            <?php endif; ?>
        </div>
    <?php endif; ?>
</div>

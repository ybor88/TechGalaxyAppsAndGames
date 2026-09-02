<?php
/** @var array $rifornimenti */
/** @var string $dal */
/** @var string $al */
?>
<div class="rp-card">
    <h1 class="rp-title">I miei rifornimenti</h1>
    <p class="rp-subtitle">Storico dei rifornimenti effettuati presso Romano Petroli</p>

    <form class="rp-filters" method="get" action="/rifornimenti">
        <div class="rp-field">
            <label for="dal">Dal</label>
            <input type="date" id="dal" name="dal" value="<?= htmlspecialchars($dal) ?>">
        </div>
        <div class="rp-field">
            <label for="al">Al</label>
            <input type="date" id="al" name="al" value="<?= htmlspecialchars($al) ?>">
        </div>
        <button type="submit" class="rp-btn">Cerca</button>
    </form>

    <table class="rp-table">
        <thead>
            <tr>
                <th>Data</th>
                <th>Importo</th>
                <th>Voucher</th>
                <th>Pagato</th>
                <th>Punti</th>
            </tr>
        </thead>
        <tbody>
            <?php if (empty($rifornimenti)): ?>
                <tr><td colspan="5">Nessun rifornimento trovato.</td></tr>
            <?php endif; ?>
            <?php foreach ($rifornimenti as $r): ?>
                <tr>
                    <td><?= htmlspecialchars(date('d/m/Y H:i', strtotime($r['data_ora']))) ?></td>
                    <td><?= number_format((float) $r['importo'], 2, ',', '.') ?> &euro;</td>
                    <td><?= number_format((float) $r['importo_voucher'], 2, ',', '.') ?> &euro;</td>
                    <td><?= number_format((float) $r['importo_pagato'], 2, ',', '.') ?> &euro;</td>
                    <td>+<?= format_punti((float) $r['punti_maturati']) ?></td>
                </tr>
            <?php endforeach; ?>
        </tbody>
    </table>
</div>

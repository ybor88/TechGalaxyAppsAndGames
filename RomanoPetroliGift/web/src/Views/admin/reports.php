<?php
/** @var array $rifornimenti */
/** @var array $totali */
/** @var array|null $distributore */
/** @var string $dal */
/** @var string $al */
?>
<div class="rp-card">
    <h1 class="rp-title">Reports rifornimenti</h1>
    <p class="rp-subtitle">
        <?= $distributore ? htmlspecialchars($distributore['nome']) : 'Romano Petroli' ?>
        — ricerca rifornimenti erogati per periodo
    </p>

    <form class="rp-filters" method="get" action="/admin/reports">
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

    <h2 class="rp-title" style="font-size:16px;">Dettaglio rifornimenti</h2>
    <table class="rp-table">
        <thead>
            <tr>
                <th>Data</th>
                <th>Cliente</th>
                <th>Codice</th>
                <th>Totale</th>
                <th>Pagato</th>
                <th>Voucher</th>
            </tr>
        </thead>
        <tbody>
            <?php if (empty($rifornimenti)): ?>
                <tr><td colspan="6">Nessun rifornimento trovato.</td></tr>
            <?php endif; ?>
            <?php foreach ($rifornimenti as $r): ?>
                <tr>
                    <td><?= htmlspecialchars(date('d/m/Y H:i', strtotime($r['data_ora']))) ?></td>
                    <td><?= $r['cliente_nome'] ? htmlspecialchars($r['cliente_nome'] . ' ' . $r['cliente_cognome']) : '-' ?></td>
                    <td><?= htmlspecialchars($r['codice_rifornimento']) ?></td>
                    <td><?= number_format((float) $r['importo'], 2, ',', '.') ?> &euro;</td>
                    <td><?= number_format((float) $r['importo_pagato'], 2, ',', '.') ?> &euro;</td>
                    <td><?= number_format((float) $r['importo_voucher'], 2, ',', '.') ?> &euro;</td>
                </tr>
            <?php endforeach; ?>
        </tbody>
    </table>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:16px;">Totali del periodo</h2>
    <div style="display:flex; gap:16px; flex-wrap:wrap;">
        <div class="rp-card" style="flex:1; min-width:200px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Totale rifornimenti</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);">
                <?= number_format((float) $totali['totale_rifornimenti'], 2, ',', '.') ?> &euro;
            </div>
        </div>
        <div class="rp-card" style="flex:1; min-width:200px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Totale voucher</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);">
                <?= number_format((float) $totali['totale_voucher'], 2, ',', '.') ?> &euro;
            </div>
        </div>
        <div class="rp-card" style="flex:1; min-width:200px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Saldo</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);">
                <?= number_format((float) $totali['saldo'], 2, ',', '.') ?> &euro;
            </div>
        </div>
    </div>
</div>

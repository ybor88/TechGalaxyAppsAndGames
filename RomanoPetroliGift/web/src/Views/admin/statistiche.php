<?php
/** @var int $contaClienti */
/** @var float $puntiInCircolazione */
/** @var int $voucherRiscattati */
/** @var int $voucherUsati */
/** @var array $riscattiPerCatalogo */
/** @var array $registrazioniPerMese */

$meseAbbreviato = ['01' => 'gen', '02' => 'feb', '03' => 'mar', '04' => 'apr', '05' => 'mag', '06' => 'giu',
    '07' => 'lug', '08' => 'ago', '09' => 'set', '10' => 'ott', '11' => 'nov', '12' => 'dic'];

// --- Grafico a barre: riscatti per taglia voucher ---
$barChartW = 640;
$barChartH = 260;
$barBaseline = 200;
$barGap = 20;
$barCount = max(1, count($riscattiPerCatalogo));
$barWidth = ($barChartW - $barGap * ($barCount + 1)) / $barCount;
$maxRiscatti = max(1, max(array_column($riscattiPerCatalogo, 'totale') ?: [0]));

// --- Grafico a linea: nuove registrazioni clienti per mese ---
$lineChartW = 640;
$lineChartH = 220;
$linePadX = 30;
$linePadTop = 20;
$linePadBottom = 40;
$plotH = $lineChartH - $linePadTop - $linePadBottom;
$n = max(1, count($registrazioniPerMese));
$maxRegistrazioni = max(1, max(array_column($registrazioniPerMese, 'totale') ?: [0]));
$stepX = $n > 1 ? ($lineChartW - 2 * $linePadX) / ($n - 1) : 0;

$puntiLinea = [];
foreach ($registrazioniPerMese as $i => $m) {
    $x = $linePadX + $i * $stepX;
    $y = $linePadTop + $plotH - ($m['totale'] / $maxRegistrazioni) * $plotH;
    $puntiLinea[] = ['x' => $x, 'y' => $y, 'totale' => $m['totale'], 'mese' => $m['mese']];
}
$polyline = implode(' ', array_map(fn ($p) => round($p['x'], 1) . ',' . round($p['y'], 1), $puntiLinea));
$areaBaseline = $linePadTop + $plotH;
$areaPath = 'M ' . round($puntiLinea[0]['x'], 1) . ',' . $areaBaseline;
foreach ($puntiLinea as $p) {
    $areaPath .= ' L ' . round($p['x'], 1) . ',' . round($p['y'], 1);
}
$areaPath .= ' L ' . round(end($puntiLinea)['x'], 1) . ',' . $areaBaseline . ' Z';
?>
<div class="rp-card">
    <h1 class="rp-title">Statistiche</h1>
    <p class="rp-subtitle">Andamento clienti e riscatti voucher — Romano Petroli</p>

    <div style="display:flex; gap:16px; flex-wrap:wrap;">
        <div class="rp-card" style="flex:1; min-width:180px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Totale clienti</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);"><?= $contaClienti ?></div>
        </div>
        <div class="rp-card" style="flex:1; min-width:180px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Punti in circolazione</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);"><?= format_punti($puntiInCircolazione) ?></div>
        </div>
        <div class="rp-card" style="flex:1; min-width:180px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Voucher riscattati</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);"><?= $voucherRiscattati ?></div>
        </div>
        <div class="rp-card" style="flex:1; min-width:180px; text-align:center; margin-bottom:0;">
            <div class="rp-subtitle" style="margin:0;">Voucher usati</div>
            <div style="font-size:24px; font-weight:700; color:var(--rp-navy);"><?= $voucherUsati ?></div>
        </div>
    </div>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:16px;">Riscatti per taglia voucher</h2>
    <div style="overflow-x:auto;">
        <svg viewBox="0 0 <?= $barChartW ?> <?= $barChartH ?>" style="width:100%; max-width:700px; height:auto;" role="img" aria-label="Riscatti per taglia voucher">
            <line x1="0" y1="<?= $barBaseline ?>" x2="<?= $barChartW ?>" y2="<?= $barBaseline ?>" stroke="#dfe2ee" stroke-width="1"></line>
            <?php foreach ($riscattiPerCatalogo as $i => $r): ?>
                <?php
                    $totale = (int) $r['totale'];
                    $barH = $totale > 0 ? ($totale / $maxRiscatti) * ($barBaseline - 40) : 0;
                    $x = $barGap + $i * ($barWidth + $barGap);
                    $y = $barBaseline - $barH;
                ?>
                <rect x="<?= round($x, 1) ?>" y="<?= round($y, 1) ?>" width="<?= round($barWidth, 1) ?>" height="<?= round($barH, 1) ?>"
                      rx="4" fill="#f5821f"></rect>
                <text x="<?= round($x + $barWidth / 2, 1) ?>" y="<?= round($y - 8, 1) ?>" text-anchor="middle"
                      font-size="13" font-weight="700" fill="#1c2340"><?= $totale ?></text>
                <text x="<?= round($x + $barWidth / 2, 1) ?>" y="<?= $barBaseline + 20 ?>" text-anchor="middle"
                      font-size="12" fill="#5b6180"><?= (int) $r['costo_punti'] ?>€</text>
            <?php endforeach; ?>
        </svg>
    </div>
    <p style="color:#5b6180; font-size:13px;">Numero di voucher riscattati per ciascuna taglia (etichetta: valore in euro del buono).</p>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:16px;">Nuovi clienti per mese</h2>
    <div style="overflow-x:auto;">
        <svg viewBox="0 0 <?= $lineChartW ?> <?= $lineChartH ?>" style="width:100%; max-width:700px; height:auto;" role="img" aria-label="Nuovi clienti registrati per mese">
            <line x1="<?= $linePadX ?>" y1="<?= $areaBaseline ?>" x2="<?= $lineChartW - $linePadX ?>" y2="<?= $areaBaseline ?>" stroke="#dfe2ee" stroke-width="1"></line>
            <path d="<?= $areaPath ?>" fill="#0b1440" fill-opacity="0.08"></path>
            <polyline points="<?= $polyline ?>" fill="none" stroke="#0b1440" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"></polyline>
            <?php foreach ($puntiLinea as $p): ?>
                <circle cx="<?= round($p['x'], 1) ?>" cy="<?= round($p['y'], 1) ?>" r="4" fill="#0b1440"></circle>
                <text x="<?= round($p['x'], 1) ?>" y="<?= round($p['y'] - 10, 1) ?>" text-anchor="middle"
                      font-size="12" font-weight="700" fill="#1c2340"><?= $p['totale'] ?></text>
                <text x="<?= round($p['x'], 1) ?>" y="<?= $areaBaseline + 22 ?>" text-anchor="middle"
                      font-size="12" fill="#5b6180"><?= $meseAbbreviato[substr($p['mese'], 5, 2)] ?? $p['mese'] ?></text>
            <?php endforeach; ?>
        </svg>
    </div>
    <p style="color:#5b6180; font-size:13px;">Numero di nuovi clienti registrati negli ultimi 6 mesi.</p>
</div>

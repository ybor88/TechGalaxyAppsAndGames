<?php
// Copyright (c) Roberto Di Flumeri
/** @var array|null $distributore */
/** @var array $messaggi */
/** @var string|null $error */
?>
<div class="rp-card">
    <h1 class="rp-title">Contatti</h1>
    <p class="rp-subtitle">Siamo qui per aiutarti</p>

    <?php if ($distributore): ?>
        <div class="rp-cliente-box" style="flex-direction:column; align-items:flex-start; gap:6px;">
            <strong><?= htmlspecialchars($distributore['nome']) ?></strong>
            <?php if (!empty($distributore['indirizzo']) || !empty($distributore['citta'])): ?>
                <span><?= htmlspecialchars(trim($distributore['indirizzo'] . ', ' . $distributore['citta'], ', ')) ?></span>
            <?php endif; ?>
        </div>
    <?php endif; ?>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:18px;">Assistenza</h2>
    <p class="rp-subtitle">Scrivici una domanda sul programma fedeltà, sui punti o sui voucher: ti risponderemo qui.</p>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>

    <div style="max-width:560px; display:flex; flex-direction:column; gap:10px; margin-bottom:16px;">
        <?php if (empty($messaggi)): ?>
            <p style="color:#5b6180;">Nessun messaggio ancora. Scrivi qui sotto per iniziare.</p>
        <?php endif; ?>
        <?php foreach ($messaggi as $m): ?>
            <?php $daAdmin = $m['mittente'] === 'admin'; ?>
            <div style="align-self:<?= $daAdmin ? 'flex-start' : 'flex-end' ?>; max-width:80%;">
                <div style="font-size:12px; color:#5b6180; margin-bottom:2px; text-align:<?= $daAdmin ? 'left' : 'right' ?>;">
                    <?= $daAdmin ? 'RP Fidelity' : 'Tu' ?> — <?= htmlspecialchars(date('d/m/Y H:i', strtotime($m['creato_il']))) ?>
                </div>
                <div style="background:<?= $daAdmin ? '#eef1fb' : '#0b1440' ?>; color:<?= $daAdmin ? '#0b1440' : '#fff' ?>; padding:10px 14px; border-radius:12px; white-space:pre-wrap;">
                    <?= htmlspecialchars($m['messaggio']) ?>
                </div>
            </div>
        <?php endforeach; ?>
    </div>

    <form class="rp-form" method="post" action="/contatti" style="max-width: 500px;">
        <label for="messaggio">Messaggio</label>
        <textarea id="messaggio" name="messaggio" rows="4" required style="width:100%; padding:10px 12px; border:1px solid var(--rp-gray-border); border-radius:6px; font-size:14px; font-family:inherit;"></textarea>

        <div style="margin-top: 16px;">
            <button type="submit" class="rp-btn">Invia messaggio</button>
        </div>
    </form>
</div>

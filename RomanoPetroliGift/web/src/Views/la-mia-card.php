<?php
/** @var array $user */
?>
<div class="rp-card" style="text-align:center; max-width: 380px; margin-left:auto; margin-right:auto;">
    <h1 class="rp-title">La mia Card</h1>
    <p class="rp-subtitle">Mostra questo QR alla cassa per caricare i punti</p>

    <?php if (!empty($user['codice_card'])): ?>
        <img src="https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=<?= urlencode($user['codice_card']) ?>"
             alt="QR Card" style="margin: 16px 0;">
        <div style="font-family: monospace; font-size: 16px; letter-spacing: 1px;"><?= htmlspecialchars($user['codice_card']) ?></div>
        <p style="margin-top: 20px;">Saldo punti: <span class="rp-points-badge"><?= format_punti((float) $user['punti_saldo']) ?> punti</span></p>
    <?php else: ?>
        <p>Nessun codice card associato al tuo account.</p>
    <?php endif; ?>
</div>

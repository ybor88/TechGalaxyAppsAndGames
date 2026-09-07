<?php
// Copyright (c) Roberto Di Flumeri
/** @var array $cliente */
/** @var array $messaggi */
?>
<div class="rp-card">
    <p><a href="/admin/messaggi">&larr; Tutte le conversazioni</a></p>
    <h1 class="rp-title">Chat con <?= htmlspecialchars($cliente['nome'] . ' ' . $cliente['cognome']) ?></h1>
    <p class="rp-subtitle"><?= htmlspecialchars($cliente['email']) ?></p>

    <div style="max-width:640px; display:flex; flex-direction:column; gap:10px; margin:16px 0;">
        <?php foreach ($messaggi as $m): ?>
            <?php $daAdmin = $m['mittente'] === 'admin'; ?>
            <div style="align-self:<?= $daAdmin ? 'flex-end' : 'flex-start' ?>; max-width:80%;">
                <div style="font-size:12px; color:#5b6180; margin-bottom:2px; text-align:<?= $daAdmin ? 'right' : 'left' ?>;">
                    <?= $daAdmin ? 'Tu' : htmlspecialchars($cliente['nome']) ?> — <?= htmlspecialchars(date('d/m/Y H:i', strtotime($m['creato_il']))) ?>
                </div>
                <div style="background:<?= $daAdmin ? '#0b1440' : '#eef1fb' ?>; color:<?= $daAdmin ? '#fff' : '#0b1440' ?>; padding:10px 14px; border-radius:12px; white-space:pre-wrap;">
                    <?= htmlspecialchars($m['messaggio']) ?>
                </div>
            </div>
        <?php endforeach; ?>
        <?php if (empty($messaggi)): ?>
            <p style="color:#5b6180;">Nessun messaggio in questa conversazione.</p>
        <?php endif; ?>
    </div>

    <form class="rp-form" method="post" action="/admin/messaggi/rispondi" style="max-width: 640px;">
        <input type="hidden" name="cliente_id" value="<?= (int) $cliente['id'] ?>">
        <label for="messaggio">Rispondi</label>
        <textarea id="messaggio" name="messaggio" rows="4" required style="width:100%; padding:10px 12px; border:1px solid var(--rp-gray-border); border-radius:6px; font-size:14px; font-family:inherit;"></textarea>
        <div style="margin-top: 16px;">
            <button type="submit" class="rp-btn">Invia risposta</button>
        </div>
    </form>
</div>

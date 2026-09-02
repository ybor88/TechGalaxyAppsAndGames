<?php
// Copyright (c) Roberto Di Flumeri
/** @var array|null $distributore */
/** @var string|null $error */
/** @var string|null $success */
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
    <h2 class="rp-title" style="font-size:18px;">Scrivici</h2>
    <p class="rp-subtitle">Compila il modulo per una domanda sul programma fedeltà, sui punti o sui voucher.</p>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>
    <?php if (!empty($success)): ?>
        <div class="rp-alert rp-alert-success"><?= htmlspecialchars($success) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="post" action="/contatti" style="max-width: 500px;">
        <label for="messaggio">Messaggio</label>
        <textarea id="messaggio" name="messaggio" rows="5" required style="width:100%; padding:10px 12px; border:1px solid var(--rp-gray-border); border-radius:6px; font-size:14px; font-family:inherit;"><?= htmlspecialchars($_POST['messaggio'] ?? '') ?></textarea>

        <div style="margin-top: 16px;">
            <button type="submit" class="rp-btn">Invia messaggio</button>
        </div>
    </form>
</div>

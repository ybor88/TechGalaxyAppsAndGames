<?php
// Copyright (c) Roberto Di Flumeri
/** @var array $user */
/** @var string|null $error */
/** @var string|null $success */
/** @var string|null $passwordError */
/** @var string|null $passwordSuccess */
?>
<div class="rp-card">
    <h1 class="rp-title">Impostazioni</h1>
    <p class="rp-subtitle">Gestisci i tuoi dati personali e la password</p>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:18px;">I miei dati</h2>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>
    <?php if (!empty($success)): ?>
        <div class="rp-alert rp-alert-success"><?= htmlspecialchars($success) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="post" action="/impostazioni" style="max-width: 420px;">
        <label for="nome">Nome</label>
        <input type="text" id="nome" name="nome" required value="<?= htmlspecialchars($user['nome']) ?>">

        <label for="cognome">Cognome</label>
        <input type="text" id="cognome" name="cognome" required value="<?= htmlspecialchars($user['cognome']) ?>">

        <label for="email">Email</label>
        <input type="email" id="email" name="email" required value="<?= htmlspecialchars($user['email']) ?>">

        <label for="telefono">Telefono</label>
        <input type="tel" id="telefono" name="telefono" value="<?= htmlspecialchars($user['telefono'] ?? '') ?>">

        <div style="margin-top: 20px;">
            <button type="submit" class="rp-btn">Salva modifiche</button>
        </div>
    </form>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:18px;">Cambia password</h2>

    <?php if (!empty($passwordError)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($passwordError) ?></div>
    <?php endif; ?>
    <?php if (!empty($passwordSuccess)): ?>
        <div class="rp-alert rp-alert-success"><?= htmlspecialchars($passwordSuccess) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="post" action="/impostazioni/password" style="max-width: 420px;">
        <label for="password_attuale">Password attuale</label>
        <input type="password" id="password_attuale" name="password_attuale" required>

        <label for="nuova_password">Nuova password</label>
        <input type="password" id="nuova_password" name="nuova_password" required minlength="6">

        <label for="conferma_password">Conferma nuova password</label>
        <input type="password" id="conferma_password" name="conferma_password" required minlength="6">

        <div style="margin-top: 20px;">
            <button type="submit" class="rp-btn">Aggiorna password</button>
        </div>
    </form>
</div>

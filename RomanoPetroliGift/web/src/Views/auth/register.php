<?php
/** @var string|null $error */
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registrati — RP Fidelity</title>
    <link rel="icon" href="/assets/img/logo.jpeg">
    <link rel="stylesheet" href="/assets/css/style.css">
</head>
<body>
    <div class="rp-login-wrap">
        <div class="rp-login-box" style="max-width: 440px;">
            <img src="/assets/img/logo.jpeg" alt="RP Fidelity">
            <h1 class="rp-title">Registrati</h1>
            <p class="rp-subtitle">Crea il tuo account RP Fidelity</p>

            <?php if (!empty($error)): ?>
                <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
            <?php endif; ?>

            <form class="rp-form" method="post" action="/registrati">
                <label for="nome">Nome</label>
                <input type="text" id="nome" name="nome" required value="<?= htmlspecialchars($_POST['nome'] ?? '') ?>">

                <label for="cognome">Cognome</label>
                <input type="text" id="cognome" name="cognome" required value="<?= htmlspecialchars($_POST['cognome'] ?? '') ?>">

                <label for="email">Email</label>
                <input type="email" id="email" name="email" required value="<?= htmlspecialchars($_POST['email'] ?? '') ?>">

                <label for="telefono">Telefono</label>
                <input type="tel" id="telefono" name="telefono" value="<?= htmlspecialchars($_POST['telefono'] ?? '') ?>">

                <label for="password">Password</label>
                <input type="password" id="password" name="password" required minlength="6">

                <div style="margin-top: 20px;">
                    <button type="submit" class="rp-btn" style="width:100%;">Crea account</button>
                </div>
            </form>

            <p style="margin-top: 18px; font-size: 14px;">
                Hai già un account? <a href="/login">Accedi</a>
            </p>
        </div>
    </div>
</body>
</html>

<?php
/** @var string|null $success */
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Password dimenticata — RP Fidelity</title>
    <meta name="description" content="Recupera l'accesso al tuo account RP Fidelity via email.">
    <meta name="robots" content="noindex, nofollow">
    <link rel="icon" href="/assets/img/logo.jpeg">
    <link rel="stylesheet" href="/assets/css/style.css">
</head>
<body>
    <div class="rp-login-wrap">
        <div class="rp-login-box">
            <div class="rp-login-logo-wrap">
                <img src="/assets/img/logo.jpeg" alt="RP Fidelity">
            </div>
            <h1 class="rp-title">Password dimenticata</h1>
            <p class="rp-subtitle">Inserisci la tua email: ti invieremo un link per reimpostarla</p>

            <?php if (!empty($success)): ?>
                <div class="rp-alert rp-alert-success" style="margin-top:18px;"><?= htmlspecialchars($success) ?></div>
            <?php else: ?>
                <form class="rp-form" method="post" action="/password-dimenticata" style="margin-top: 24px;">
                    <div class="rp-field-icon">
                        <label for="email">Email</label>
                        <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16v16H4z" stroke="none"/><path d="M22 6l-10 7L2 6"/><path d="M2 6h20v12H2z"/></svg>
                        <input type="email" id="email" name="email" required placeholder="nome@esempio.it">
                    </div>

                    <div style="margin-top: 26px;">
                        <button type="submit" class="rp-btn">Invia il link di recupero</button>
                    </div>
                </form>
            <?php endif; ?>

            <p class="rp-login-footer-link">
                <a href="/login">&larr; Torna al login</a>
            </p>
        </div>
    </div>
</body>
</html>

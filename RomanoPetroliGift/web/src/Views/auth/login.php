<?php
/** @var string|null $error */
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Accedi — RP Fidelity</title>
    <link rel="icon" href="/assets/img/logo.jpeg">
    <link rel="stylesheet" href="/assets/css/style.css">
    <link rel="manifest" href="/manifest.webmanifest">
    <meta name="theme-color" content="#0b1440">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <link rel="apple-touch-icon" href="/assets/img/icons/icon-180.png">
</head>
<body>
    <div class="rp-login-wrap">
        <div class="rp-login-box">
            <div class="rp-login-logo-wrap">
                <img src="/assets/img/logo.jpeg" alt="RP Fidelity">
            </div>
            <h1 class="rp-title">RP Fidelity</h1>
            <p class="rp-subtitle">Romano Petroli</p>

            <?php if (!empty($error)): ?>
                <div class="rp-alert rp-alert-error" style="margin-top:18px;"><?= htmlspecialchars($error) ?></div>
            <?php endif; ?>

            <form class="rp-form" method="post" action="/login" style="margin-top: 24px;">
                <div class="rp-field-icon">
                    <label for="email">Email</label>
                    <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16v16H4z" stroke="none"/><path d="M22 6l-10 7L2 6"/><path d="M2 6h20v12H2z"/></svg>
                    <input type="email" id="email" name="email" required value="<?= htmlspecialchars($_POST['email'] ?? '') ?>" placeholder="nome@esempio.it">
                </div>

                <div class="rp-field-icon">
                    <label for="password">Password</label>
                    <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
                    <input type="password" id="password" name="password" required placeholder="••••••••">
                </div>

                <div style="margin-top: 26px;">
                    <button type="submit" class="rp-btn">Accedi</button>
                </div>
            </form>

            <p class="rp-login-footer-link">
                Non hai un account? <a href="/registrati">Registrati</a>
            </p>
            <p class="rp-login-footer-link">
                <a href="/scarica-app">📱 Scarica l'app</a>
            </p>
        </div>
    </div>
</body>
</html>

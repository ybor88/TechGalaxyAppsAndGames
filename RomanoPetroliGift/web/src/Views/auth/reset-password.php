<?php
/** @var bool $tokenValido */
/** @var string|null $token */
/** @var string|null $error */
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reimposta password — RP Fidelity</title>
    <meta name="description" content="Imposta una nuova password per il tuo account RP Fidelity.">
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

            <?php if (!$tokenValido): ?>
                <h1 class="rp-title">Link non valido</h1>
                <p class="rp-subtitle">Il link è scaduto o è già stato usato</p>
                <div class="rp-alert rp-alert-error" style="margin-top:18px;">
                    Richiedi un nuovo link dalla pagina "Password dimenticata".
                </div>
                <p class="rp-login-footer-link">
                    <a href="/password-dimenticata">Richiedi un nuovo link</a>
                </p>
            <?php else: ?>
                <h1 class="rp-title">Reimposta password</h1>
                <p class="rp-subtitle">Scegli la tua nuova password</p>

                <?php if (!empty($error)): ?>
                    <div class="rp-alert rp-alert-error" style="margin-top:18px;"><?= htmlspecialchars($error) ?></div>
                <?php endif; ?>

                <form class="rp-form" method="post" action="/reset-password" style="margin-top: 24px;">
                    <input type="hidden" name="token" value="<?= htmlspecialchars($token) ?>">

                    <div class="rp-field-icon">
                        <label for="password">Nuova password</label>
                        <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
                        <input type="password" id="password" name="password" required minlength="6" placeholder="Almeno 6 caratteri">
                    </div>

                    <div class="rp-field-icon">
                        <label for="conferma_password">Conferma password</label>
                        <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
                        <input type="password" id="conferma_password" name="conferma_password" required minlength="6" placeholder="Ripeti la password">
                    </div>

                    <div style="margin-top: 26px;">
                        <button type="submit" class="rp-btn">Salva nuova password</button>
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

<?php
/** @var string $content */
/** @var string $pageTitle */
use App\Core\Auth;

$user = Auth::user();
$currentPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
?>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= htmlspecialchars($pageTitle ?? 'RP Fidelity') ?></title>
    <link rel="icon" href="/assets/img/logo.jpeg">
    <link rel="stylesheet" href="/assets/css/style.css">

    <!-- PWA: installabile su Android (Chrome) e su iOS/iPhone via "Aggiungi a Home" (Safari) -->
    <link rel="manifest" href="/manifest.webmanifest">
    <meta name="theme-color" content="#0b1440">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="RP Fidelity">
    <link rel="apple-touch-icon" href="/assets/img/icons/icon-180.png">
</head>
<body>
    <header class="rp-header">
        <div class="rp-brand">
            <img src="/assets/img/logo.jpeg" alt="RP Fidelity">
            <span>RP Fidelity</span>
        </div>
        <?php if ($user): ?>
            <div class="rp-user">
                Benvenuto, <?= htmlspecialchars($user['nome']) ?>
                &nbsp;|&nbsp; <a href="/logout" style="color:#fff;">Esci</a>
            </div>
        <?php endif; ?>
    </header>

    <?php if ($user): ?>
    <nav class="rp-nav">
        <a href="/dashboard" class="<?= $currentPath === '/dashboard' ? 'active' : '' ?>">Home</a>
        <?php if ($user['ruolo'] !== 'admin'): ?>
            <a href="/la-mia-card" class="<?= $currentPath === '/la-mia-card' ? 'active' : '' ?>">La mia Card</a>
            <a href="/rifornimenti" class="<?= $currentPath === '/rifornimenti' ? 'active' : '' ?>">I miei dati</a>
            <a href="/voucher" class="<?= $currentPath === '/voucher' ? 'active' : '' ?>">Voucher</a>
        <?php endif; ?>
        <?php if ($user['ruolo'] === 'admin'): ?>
            <a href="/admin/statistiche" class="<?= $currentPath === '/admin/statistiche' ? 'active' : '' ?>">Statistiche</a>
            <a href="/admin/clienti" class="<?= $currentPath === '/admin/clienti' ? 'active' : '' ?>">Gestione</a>
            <a href="/admin/rifornimenti/nuovo" class="<?= $currentPath === '/admin/rifornimenti/nuovo' ? 'active' : '' ?>">Registra Rifornimento</a>
            <a href="/admin/reports" class="<?= $currentPath === '/admin/reports' ? 'active' : '' ?>">Reports</a>
            <a href="/admin/verifica-voucher" class="<?= $currentPath === '/admin/verifica-voucher' ? 'active' : '' ?>">Verifica Voucher</a>
        <?php endif; ?>
    </nav>
    <?php endif; ?>

    <main class="rp-container">
        <?= $content ?>
    </main>

    <footer class="rp-footer">
        <a href="/scarica-app">Scarica l'app</a> —
        &copy; <?= date('Y') ?> RP Fidelity — Romano Petroli. Tutti i diritti riservati.
    </footer>

    <script>
        if ('serviceWorker' in navigator) {
            window.addEventListener('load', function () {
                navigator.serviceWorker.register('/sw.js').catch(function () {});
            });
        }
    </script>
</body>
</html>

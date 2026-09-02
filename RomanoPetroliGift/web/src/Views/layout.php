<?php
/** @var string $content */
/** @var string $pageTitle */
use App\Core\Auth;

$user = Auth::user();
$isAdmin = $user && $user['ruolo'] === 'admin';
$currentPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);

// Icone stile "feather" (stroke, 24x24) per le voci del menu laterale cliente.
$rpIcons = [
    'home' => '<path d="M3 11.5 12 4l9 7.5"/><path d="M5 10v10h5v-6h4v6h5V10"/>',
    'card' => '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>',
    'dati' => '<line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>',
    'voucher' => '<path d="M20.59 13.41 11 3.83A2 2 0 0 0 9.59 3H4a1 1 0 0 0-1 1v5.59a2 2 0 0 0 .59 1.41l9.58 9.58a2 2 0 0 0 2.83 0l4.59-4.59a2 2 0 0 0 0-2.83z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
    'catalogo' => '<path d="M20 12v9H4v-9"/><path d="M2 7h20v5H2z"/><path d="M12 22V7"/><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/>',
    'contatti' => '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z"/>',
    'settings' => '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
    'faq' => '<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
    'logout' => '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>',
];
function rp_icon(array $icons, string $name): string
{
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' . ($icons[$name] ?? '') . '</svg>';
}
$rpClientMenu = [
    ['href' => '/dashboard', 'label' => 'Home', 'icon' => 'home'],
    ['href' => '/la-mia-card', 'label' => 'La mia Card', 'icon' => 'card'],
    ['href' => '/rifornimenti', 'label' => 'I miei dati', 'icon' => 'dati'],
    ['href' => '/voucher', 'label' => 'I miei voucher', 'icon' => 'voucher'],
    ['href' => '/catalogo', 'label' => 'Catalogo premi', 'icon' => 'catalogo'],
    ['href' => '/contatti', 'label' => 'Contatti', 'icon' => 'contatti'],
    ['href' => '/impostazioni', 'label' => 'Impostazioni', 'icon' => 'settings'],
    ['href' => '/faq', 'label' => 'FAQ', 'icon' => 'faq'],
];
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
        <div class="rp-header-left">
            <?php if ($user && !$isAdmin): ?>
                <button type="button" class="rp-hamburger" id="rp-menu-toggle" aria-label="Apri menu" aria-expanded="false" aria-controls="rp-sidebar">
                    <span></span><span></span><span></span>
                </button>
            <?php endif; ?>
            <div class="rp-brand">
                <img src="/assets/img/logo.jpeg" alt="RP Fidelity">
                <span>RP Fidelity</span>
            </div>
        </div>
        <?php if ($user): ?>
            <div class="rp-user">
                Benvenuto, <?= htmlspecialchars($user['nome']) ?>
                &nbsp;|&nbsp; <a href="/logout" style="color:#fff;">Esci</a>
            </div>
        <?php endif; ?>
    </header>

    <?php if ($user && $isAdmin): ?>
    <nav class="rp-nav">
        <a href="/dashboard" class="<?= $currentPath === '/dashboard' ? 'active' : '' ?>">Home</a>
        <a href="/admin/statistiche" class="<?= $currentPath === '/admin/statistiche' ? 'active' : '' ?>">Statistiche</a>
        <a href="/admin/clienti" class="<?= $currentPath === '/admin/clienti' ? 'active' : '' ?>">Gestione</a>
        <a href="/admin/rifornimenti/nuovo" class="<?= $currentPath === '/admin/rifornimenti/nuovo' ? 'active' : '' ?>">Registra Rifornimento</a>
        <a href="/admin/reports" class="<?= $currentPath === '/admin/reports' ? 'active' : '' ?>">Reports</a>
        <a href="/admin/verifica-voucher" class="<?= $currentPath === '/admin/verifica-voucher' ? 'active' : '' ?>">Verifica Voucher</a>
    </nav>
    <?php endif; ?>

    <?php if ($user && !$isAdmin): ?>
    <div class="rp-sidebar-overlay" id="rp-sidebar-overlay"></div>
    <aside class="rp-sidebar" id="rp-sidebar">
        <div class="rp-sidebar-brand">
            <img src="/assets/img/logo.jpeg" alt="RP Fidelity">
            <span>RP Fidelity</span>
        </div>
        <nav class="rp-sidebar-nav">
            <?php foreach ($rpClientMenu as $item): ?>
                <a href="<?= $item['href'] ?>" class="<?= $currentPath === $item['href'] ? 'active' : '' ?>">
                    <?= rp_icon($rpIcons, $item['icon']) ?>
                    <span><?= htmlspecialchars($item['label']) ?></span>
                </a>
            <?php endforeach; ?>
        </nav>
        <div class="rp-sidebar-footer">
            <a href="/logout" class="rp-sidebar-logout">
                <?= rp_icon($rpIcons, 'logout') ?>
                <span>Esci</span>
            </a>
        </div>
    </aside>
    <?php endif; ?>

    <main class="rp-container">
        <?= $content ?>
    </main>

    <footer class="rp-footer">
        <a href="/scarica-app">Scarica l'app</a> —
        &copy; <?= date('Y') ?> RP Fidelity — Romano Petroli. Tutti i diritti riservati.
        <br>Sviluppo: &copy; Roberto Di Flumeri
    </footer>

    <script>
        if ('serviceWorker' in navigator) {
            window.addEventListener('load', function () {
                navigator.serviceWorker.register('/sw.js').catch(function () {});
            });
        }

        (function () {
            var toggle = document.getElementById('rp-menu-toggle');
            var sidebar = document.getElementById('rp-sidebar');
            var overlay = document.getElementById('rp-sidebar-overlay');
            if (!toggle || !sidebar || !overlay) {
                return;
            }

            function closeMenu() {
                document.body.classList.remove('rp-sidebar-open');
                toggle.setAttribute('aria-expanded', 'false');
            }

            function openMenu() {
                document.body.classList.add('rp-sidebar-open');
                toggle.setAttribute('aria-expanded', 'true');
            }

            toggle.addEventListener('click', function () {
                document.body.classList.contains('rp-sidebar-open') ? closeMenu() : openMenu();
            });
            overlay.addEventListener('click', closeMenu);
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') {
                    closeMenu();
                }
            });
            sidebar.querySelectorAll('a').forEach(function (a) {
                a.addEventListener('click', closeMenu);
            });
        })();
    </script>
</body>
</html>

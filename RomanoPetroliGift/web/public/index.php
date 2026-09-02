<?php

// Solo per il server di sviluppo PHP (`php -S`): lascia servire i file statici esistenti
// così com'è, senza passare dal router applicativo. Su Apache/hosting reale questo
// controllo non serve, perché il .htaccess già esclude i file esistenti a monte.
if (PHP_SAPI === 'cli-server') {
    $requestedFile = __DIR__ . parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
    if ($requestedFile !== __FILE__ && is_file($requestedFile)) {
        return false;
    }
}

require __DIR__ . '/../src/Core/helpers.php';

spl_autoload_register(function (string $class) {
    $prefix = 'App\\';
    if (!str_starts_with($class, $prefix)) {
        return;
    }
    $relative = substr($class, strlen($prefix));
    $file = __DIR__ . '/../src/' . str_replace('\\', '/', $relative) . '.php';
    if (is_file($file)) {
        require $file;
    }
});

$requestUri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?: '/';
if ($requestUri === '/api' || str_starts_with($requestUri, '/api/')) {
    require __DIR__ . '/../api/index.php';
    return;
}

use App\Core\Auth;
use App\Core\Router;
use App\Controllers\AuthController;
use App\Controllers\DashboardController;
use App\Controllers\VoucherController;
use App\Controllers\AdminController;
use App\Controllers\PublicController;

Auth::start();

$router = new Router();

$router->get('/', function () {
    header('Location: ' . (Auth::check() ? '/dashboard' : '/login'));
});

$router->get('/login', [new AuthController(), 'showLogin']);
$router->post('/login', [new AuthController(), 'login']);
$router->get('/registrati', [new AuthController(), 'showRegister']);
$router->post('/registrati', [new AuthController(), 'register']);
$router->get('/logout', [new AuthController(), 'logout']);
$router->get('/scarica-app', [new PublicController(), 'scaricaApp']);

$router->get('/dashboard', [new DashboardController(), 'index']);
$router->get('/rifornimenti', [new DashboardController(), 'rifornimenti']);
$router->get('/la-mia-card', [new DashboardController(), 'card']);

$router->get('/voucher', [new VoucherController(), 'index']);
$router->post('/voucher/riscatta', [new VoucherController(), 'riscatta']);

$router->get('/admin/statistiche', [new AdminController(), 'statistiche']);
$router->get('/admin/clienti', [new AdminController(), 'clienti']);
$router->get('/admin/clienti/modifica', [new AdminController(), 'modificaClienteForm']);
$router->post('/admin/clienti/modifica', [new AdminController(), 'modificaCliente']);
$router->post('/admin/clienti/elimina', [new AdminController(), 'eliminaCliente']);
$router->get('/admin/reports', [new AdminController(), 'reports']);
$router->get('/admin/verifica-voucher', [new AdminController(), 'verificaVoucher']);
$router->post('/admin/verifica-voucher/usa', [new AdminController(), 'usaVoucher']);
$router->get('/admin/rifornimenti/nuovo', [new AdminController(), 'nuovoRifornimento']);
$router->post('/admin/rifornimenti/nuovo/cliente', [new AdminController(), 'identificaCliente']);
$router->post('/admin/rifornimenti/nuovo/cambia-cliente', [new AdminController(), 'cambiaCliente']);
$router->post('/admin/rifornimenti/nuovo/voucher', [new AdminController(), 'aggiungiVoucher']);
$router->post('/admin/rifornimenti/nuovo/voucher/rimuovi', [new AdminController(), 'rimuoviVoucher']);
$router->post('/admin/rifornimenti/nuovo/conferma', [new AdminController(), 'confermaRifornimento']);

$router->dispatch($_SERVER['REQUEST_METHOD'], $_SERVER['REQUEST_URI']);

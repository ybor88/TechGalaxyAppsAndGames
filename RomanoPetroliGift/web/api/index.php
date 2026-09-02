<?php
// API REST per le app mobile (Android/iOS). Autenticazione a token Bearer (App\Core\ApiAuth).
// Richiesto da web/public/index.php, che ha già registrato l'autoloader per il namespace App\.

use App\Controllers\Api\AdminApiController;
use App\Controllers\Api\AuthApiController;
use App\Controllers\Api\ClienteApiController;
use App\Core\Json;

$method = $_SERVER['REQUEST_METHOD'];
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?: '/api';
$path = rtrim($path, '/');
$path = preg_replace('#^/api#', '', $path);
if ($path === '') {
    $path = '/';
}

$rawBody = file_get_contents('php://input');
$input = json_decode($rawBody, true);
if (!is_array($input)) {
    $input = $_POST;
}

$routes = [
    'POST /login' => static fn () => (new AuthApiController())->login($input),
    'POST /registrati' => static fn () => (new AuthApiController())->registrati($input),
    'GET /me' => static fn () => (new AuthApiController())->me(),

    'GET /rifornimenti' => static fn () => (new ClienteApiController())->rifornimenti($_GET),
    'GET /voucher/catalogo' => static fn () => (new ClienteApiController())->catalogo(),
    'GET /voucher/miei' => static fn () => (new ClienteApiController())->mieiVoucher(),
    'POST /voucher/riscatta' => static fn () => (new ClienteApiController())->riscatta($input),

    'GET /admin/clienti/cerca' => static fn () => (new AdminApiController())->cercaCliente($_GET),
    'GET /admin/voucher/verifica' => static fn () => (new AdminApiController())->verificaVoucherPerCarrello($_GET),
    'POST /admin/rifornimenti' => static fn () => (new AdminApiController())->confermaRifornimento($input),
    'GET /admin/reports' => static fn () => (new AdminApiController())->reports($_GET),
    'GET /admin/verifica-voucher' => static fn () => (new AdminApiController())->verificaVoucher($_GET),
    'POST /admin/verifica-voucher/usa' => static fn () => (new AdminApiController())->usaVoucher($input),
];

$key = $method . ' ' . $path;

if (!isset($routes[$key])) {
    Json::error('Endpoint non trovato.', 404);
}

try {
    $routes[$key]();
} catch (\Throwable $e) {
    Json::error('Errore interno del server.', 500);
}

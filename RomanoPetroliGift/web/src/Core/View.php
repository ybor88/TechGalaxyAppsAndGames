<?php

namespace App\Core;

class View
{
    public static function render(string $view, array $data = []): void
    {
        extract($data);
        $viewFile = __DIR__ . '/../Views/' . $view . '.php';

        require $viewFile;
    }

    public static function layout(string $view, array $data = []): void
    {
        extract($data);
        $viewFile = __DIR__ . '/../Views/' . $view . '.php';

        ob_start();
        require $viewFile;
        $content = ob_get_clean();

        require __DIR__ . '/../Views/layout.php';
    }

    // Come render(), ma restituisce l'HTML come stringa invece di stamparlo: usato per i corpi email.
    public static function renderToString(string $view, array $data = []): string
    {
        extract($data);
        $viewFile = __DIR__ . '/../Views/' . $view . '.php';

        ob_start();
        require $viewFile;

        return ob_get_clean();
    }
}

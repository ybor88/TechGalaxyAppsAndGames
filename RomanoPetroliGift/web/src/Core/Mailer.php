<?php

namespace App\Core;

class Mailer
{
    // mail() nativa (nessuna dipendenza Composer): su hosting condiviso come Aruba usa il
    // sendmail/SMTP locale già configurato dal pannello. In locale senza un MTA configurato
    // semplicemente non parte: non blocchiamo mai il flusso chiamante per un errore di invio.
    public static function send(string $to, string $subject, string $htmlBody): bool
    {
        $config = require __DIR__ . '/../../config/config.php';
        $mail = $config['mail'] ?? ['from_email' => 'noreply@rpfidelity.it', 'from_name' => 'RP Fidelity'];

        $headers = implode("\r\n", [
            'MIME-Version: 1.0',
            'Content-Type: text/html; charset=UTF-8',
            sprintf('From: %s <%s>', $mail['from_name'], $mail['from_email']),
        ]);

        $encodedSubject = '=?UTF-8?B?' . base64_encode($subject) . '?=';

        try {
            return @mail($to, $encodedSubject, $htmlBody, $headers);
        } catch (\Throwable $e) {
            return false;
        }
    }

    // Involucro HTML comune (logo, colori del brand, footer) attorno al contenuto specifico
    // di ogni email. Stili inline perché i client di posta ignorano i <link>/<style> esterni.
    public static function wrap(string $innerHtml): string
    {
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        $host = $_SERVER['HTTP_HOST'] ?? 'www.rpfidelity.it';
        $logoUrl = htmlspecialchars($scheme . '://' . $host . '/assets/img/logo.jpeg');
        $thisYear = date('Y');

        return <<<HTML
<!DOCTYPE html>
<html lang="it">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
<body style="margin:0; padding:0; background:#f4f5fa; font-family:Arial, Helvetica, sans-serif; color:#1c2340;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f5fa; padding:24px 0;">
        <tr><td align="center">
            <table role="presentation" width="100%" style="max-width:480px; background:#ffffff; border-radius:12px; overflow:hidden;" cellpadding="0" cellspacing="0">
                <tr>
                    <td align="center" style="background:#0b1440; padding:28px 20px;">
                        <img src="{$logoUrl}" alt="RP Fidelity" width="64" height="64" style="border-radius:12px; display:block;">
                        <div style="color:#ffffff; font-size:20px; font-weight:bold; margin-top:10px;">RP Fidelity</div>
                        <div style="color:#c7cbe0; font-size:13px;">Romano Petroli</div>
                    </td>
                </tr>
                <tr>
                    <td style="padding:28px 24px; font-size:15px; line-height:1.5;">
                        {$innerHtml}
                    </td>
                </tr>
                <tr>
                    <td style="padding:16px 24px; background:#f4f5fa; font-size:12px; color:#5b6180; text-align:center;">
                        &copy; {$thisYear} RP Fidelity — Romano Petroli. Questa è un'email automatica, non rispondere a questo indirizzo.
                    </td>
                </tr>
            </table>
        </td></tr>
    </table>
</body>
</html>
HTML;
    }
}

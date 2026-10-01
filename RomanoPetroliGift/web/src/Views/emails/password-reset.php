<?php
/** @var array $user */
/** @var string $resetUrl */
?>
<h2 style="margin:0 0 8px; color:#0b1440; font-size:19px;">Ciao <?= htmlspecialchars($user['nome']) ?>,</h2>
<p style="margin:0 0 20px; color:#5b6180;">Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account RP Fidelity. Clicca sul pulsante qui sotto per sceglierne una nuova:</p>

<table role="presentation" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
    <tr>
        <td style="border-radius:8px; background:#f5821f;">
            <a href="<?= htmlspecialchars($resetUrl) ?>" style="display:inline-block; padding:12px 28px; color:#ffffff; text-decoration:none; font-weight:bold; font-size:14px;">Reimposta la password</a>
        </td>
    </tr>
</table>

<p style="margin:0 0 8px; color:#5b6180; font-size:13px;">Il link è valido per 1 ora. Se non hai richiesto tu il reset, ignora semplicemente questa email: la tua password resterà invariata.</p>
<p style="margin:0; color:#9499b5; font-size:12px; word-break:break-all;">Se il pulsante non funziona, copia e incolla questo indirizzo nel browser:<br><?= htmlspecialchars($resetUrl) ?></p>

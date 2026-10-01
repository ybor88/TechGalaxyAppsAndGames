<?php
/** @var array $user */
/** @var string $loginUrl */
?>
<h2 style="margin:0 0 8px; color:#0b1440; font-size:19px;">Benvenuto/a, <?= htmlspecialchars($user['nome']) ?>!</h2>
<p style="margin:0 0 20px; color:#5b6180;">La tua registrazione a RP Fidelity è andata a buon fine. Ecco il riepilogo dei tuoi dati:</p>

<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid #dfe2ee; border-radius:8px; margin-bottom:20px;">
    <tr>
        <td style="padding:10px 14px; border-bottom:1px solid #dfe2ee; color:#5b6180; font-size:13px; width:40%;">Nome</td>
        <td style="padding:10px 14px; border-bottom:1px solid #dfe2ee; font-weight:bold;"><?= htmlspecialchars($user['nome'] . ' ' . $user['cognome']) ?></td>
    </tr>
    <tr>
        <td style="padding:10px 14px; border-bottom:1px solid #dfe2ee; color:#5b6180; font-size:13px;">Email</td>
        <td style="padding:10px 14px; border-bottom:1px solid #dfe2ee; font-weight:bold;"><?= htmlspecialchars($user['email']) ?></td>
    </tr>
    <tr>
        <td style="padding:10px 14px; color:#5b6180; font-size:13px;">Codice Card</td>
        <td style="padding:10px 14px; font-weight:bold;"><?= htmlspecialchars($user['codice_card'] ?? '—') ?></td>
    </tr>
</table>

<p style="margin:0 0 20px; color:#5b6180;">Da ora puoi accumulare punti a ogni rifornimento e riscattarli in buoni benzina.</p>

<table role="presentation" cellpadding="0" cellspacing="0">
    <tr>
        <td style="border-radius:8px; background:#f5821f;">
            <a href="<?= htmlspecialchars($loginUrl) ?>" style="display:inline-block; padding:12px 28px; color:#ffffff; text-decoration:none; font-weight:bold; font-size:14px;">Accedi al tuo account</a>
        </td>
    </tr>
</table>

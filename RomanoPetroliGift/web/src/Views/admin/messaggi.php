<?php
// Copyright (c) Roberto Di Flumeri
/** @var array $conversazioni */
?>
<div class="rp-card">
    <h1 class="rp-title">Messaggi</h1>
    <p class="rp-subtitle">Conversazioni di assistenza con i clienti</p>

    <table class="rp-table">
        <thead>
            <tr>
                <th>Cliente</th>
                <th>Ultimo messaggio</th>
                <th>Da</th>
                <th>Quando</th>
                <th></th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($conversazioni as $c): ?>
                <tr>
                    <td><?= htmlspecialchars($c['nome'] . ' ' . $c['cognome']) ?></td>
                    <td style="max-width:320px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">
                        <?= htmlspecialchars($c['ultimo_messaggio']) ?>
                    </td>
                    <td><?= $c['ultimo_mittente'] === 'admin' ? 'Tu' : 'Cliente' ?></td>
                    <td><?= htmlspecialchars(date('d/m/Y H:i', strtotime($c['ultimo_il']))) ?></td>
                    <td><a href="/admin/messaggi/thread?id=<?= (int) $c['id'] ?>">Apri</a></td>
                </tr>
            <?php endforeach; ?>
            <?php if (empty($conversazioni)): ?>
                <tr><td colspan="5">Nessun messaggio ricevuto.</td></tr>
            <?php endif; ?>
        </tbody>
    </table>
</div>

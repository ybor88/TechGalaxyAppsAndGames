<?php
/** @var array $clienti */
?>
<div class="rp-card">
    <h1 class="rp-title">Gestione clienti</h1>
    <p class="rp-subtitle">Elenco di tutti gli utenti registrati</p>

    <table class="rp-table">
        <thead>
            <tr>
                <th>Nome</th>
                <th>Email</th>
                <th>Telefono</th>
                <th>Codice Card</th>
                <th>Ruolo</th>
                <th>Punti</th>
                <th>Stato</th>
                <th>Registrato il</th>
                <th>Azioni</th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($clienti as $c): ?>
                <tr>
                    <td><?= htmlspecialchars($c['nome'] . ' ' . $c['cognome']) ?></td>
                    <td><?= htmlspecialchars($c['email']) ?></td>
                    <td><?= htmlspecialchars($c['telefono'] ?? '-') ?></td>
                    <td style="font-family:monospace;"><?= htmlspecialchars($c['codice_card'] ?? '-') ?></td>
                    <td><?= htmlspecialchars($c['ruolo']) ?></td>
                    <td><?= format_punti((float) $c['punti_saldo']) ?></td>
                    <td><?= htmlspecialchars($c['stato']) ?></td>
                    <td><?= htmlspecialchars(date('d/m/Y', strtotime($c['data_registrazione']))) ?></td>
                    <td>
                        <?php if ($c['ruolo'] === 'cliente'): ?>
                            <a href="/admin/clienti/modifica?id=<?= (int) $c['id'] ?>">Modifica</a>
                        <?php endif; ?>
                    </td>
                </tr>
            <?php endforeach; ?>
        </tbody>
    </table>
</div>

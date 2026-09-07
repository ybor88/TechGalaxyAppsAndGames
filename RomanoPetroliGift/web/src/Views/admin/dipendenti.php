<?php
/** @var array $dipendenti */
?>
<div class="rp-card">
    <h1 class="rp-title">Gestione dipendenti</h1>
    <p class="rp-subtitle">Account con accesso alla sola registrazione rifornimenti</p>

    <p style="margin-bottom: 16px;">
        <a href="/admin/dipendenti/nuovo" class="rp-btn">Nuovo dipendente</a>
    </p>

    <table class="rp-table">
        <thead>
            <tr>
                <th>Nome</th>
                <th>Email</th>
                <th>Telefono</th>
                <th>Stato</th>
                <th>Creato il</th>
                <th>Azioni</th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($dipendenti as $d): ?>
                <tr>
                    <td><?= htmlspecialchars($d['nome'] . ' ' . $d['cognome']) ?></td>
                    <td><?= htmlspecialchars($d['email']) ?></td>
                    <td><?= htmlspecialchars($d['telefono'] ?? '-') ?></td>
                    <td><?= htmlspecialchars($d['stato']) ?></td>
                    <td><?= htmlspecialchars(date('d/m/Y', strtotime($d['data_registrazione']))) ?></td>
                    <td>
                        <a href="/admin/dipendenti/modifica?id=<?= (int) $d['id'] ?>">Modifica</a>
                    </td>
                </tr>
            <?php endforeach; ?>
            <?php if (empty($dipendenti)): ?>
                <tr><td colspan="6">Nessun dipendente registrato.</td></tr>
            <?php endif; ?>
        </tbody>
    </table>
</div>

<?php
/** @var string|null $error */
?>
<div class="rp-card">
    <h1 class="rp-title">Nuovo dipendente</h1>
    <p class="rp-subtitle">L'account potrà solo registrare i rifornimenti alla cassa, senza accesso a clienti, report o statistiche.</p>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="post" action="/admin/dipendenti/nuovo" style="max-width: 420px;">
        <label for="nome">Nome</label>
        <input type="text" id="nome" name="nome" required value="<?= htmlspecialchars($_POST['nome'] ?? '') ?>">

        <label for="cognome">Cognome</label>
        <input type="text" id="cognome" name="cognome" required value="<?= htmlspecialchars($_POST['cognome'] ?? '') ?>">

        <label for="email">Email</label>
        <input type="email" id="email" name="email" required value="<?= htmlspecialchars($_POST['email'] ?? '') ?>">

        <label for="telefono">Telefono</label>
        <input type="tel" id="telefono" name="telefono" value="<?= htmlspecialchars($_POST['telefono'] ?? '') ?>">

        <label for="password">Password</label>
        <input type="password" id="password" name="password" required minlength="6">

        <div style="margin-top: 20px; display:flex; gap:12px;">
            <button type="submit" class="rp-btn">Crea dipendente</button>
            <a href="/admin/dipendenti" class="rp-btn rp-btn-outline">Annulla</a>
        </div>
    </form>
</div>

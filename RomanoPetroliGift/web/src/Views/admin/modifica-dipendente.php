<?php
/** @var array $dipendente */
/** @var string|null $error */
?>
<div class="rp-card">
    <h1 class="rp-title">Modifica dipendente</h1>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="post" action="/admin/dipendenti/modifica" style="max-width: 420px;">
        <input type="hidden" name="id" value="<?= (int) $dipendente['id'] ?>">

        <label for="nome">Nome</label>
        <input type="text" id="nome" name="nome" required value="<?= htmlspecialchars($dipendente['nome']) ?>">

        <label for="cognome">Cognome</label>
        <input type="text" id="cognome" name="cognome" required value="<?= htmlspecialchars($dipendente['cognome']) ?>">

        <label for="email">Email</label>
        <input type="email" id="email" name="email" required value="<?= htmlspecialchars($dipendente['email']) ?>">

        <label for="telefono">Telefono</label>
        <input type="tel" id="telefono" name="telefono" value="<?= htmlspecialchars($dipendente['telefono'] ?? '') ?>">

        <label for="stato">Stato</label>
        <select id="stato" name="stato">
            <option value="attivo" <?= $dipendente['stato'] === 'attivo' ? 'selected' : '' ?>>Attivo</option>
            <option value="sospeso" <?= $dipendente['stato'] === 'sospeso' ? 'selected' : '' ?>>Sospeso</option>
        </select>

        <div style="margin-top: 20px; display:flex; gap:12px;">
            <button type="submit" class="rp-btn">Salva modifiche</button>
            <a href="/admin/dipendenti" class="rp-btn rp-btn-outline">Annulla</a>
        </div>
    </form>

    <form method="post" action="/admin/dipendenti/elimina"
          onsubmit="return confirm('Eliminare definitivamente questo dipendente? L\'operazione non è reversibile.');"
          style="margin-top: 24px;">
        <input type="hidden" name="id" value="<?= (int) $dipendente['id'] ?>">
        <button type="submit" class="rp-btn" style="background:#c0392b;">Elimina dipendente</button>
    </form>
</div>

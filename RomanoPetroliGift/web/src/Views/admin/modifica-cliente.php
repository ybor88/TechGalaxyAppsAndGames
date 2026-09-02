<?php
/** @var array $cliente */
/** @var string|null $error */
?>
<div class="rp-card">
    <h1 class="rp-title">Modifica cliente</h1>
    <p class="rp-subtitle">Punti attuali: <strong><?= format_punti((float) $cliente['punti_saldo']) ?></strong></p>

    <?php if (!empty($error)): ?>
        <div class="rp-alert rp-alert-error"><?= htmlspecialchars($error) ?></div>
    <?php endif; ?>

    <form class="rp-form" method="post" action="/admin/clienti/modifica" style="max-width: 420px;">
        <input type="hidden" name="id" value="<?= (int) $cliente['id'] ?>">

        <label for="nome">Nome</label>
        <input type="text" id="nome" name="nome" required value="<?= htmlspecialchars($cliente['nome']) ?>">

        <label for="cognome">Cognome</label>
        <input type="text" id="cognome" name="cognome" required value="<?= htmlspecialchars($cliente['cognome']) ?>">

        <label for="email">Email</label>
        <input type="email" id="email" name="email" required value="<?= htmlspecialchars($cliente['email']) ?>">

        <label for="telefono">Telefono</label>
        <input type="tel" id="telefono" name="telefono" value="<?= htmlspecialchars($cliente['telefono'] ?? '') ?>">

        <label for="stato">Stato</label>
        <select id="stato" name="stato">
            <option value="attivo" <?= $cliente['stato'] === 'attivo' ? 'selected' : '' ?>>Attivo</option>
            <option value="sospeso" <?= $cliente['stato'] === 'sospeso' ? 'selected' : '' ?>>Sospeso</option>
        </select>

        <div style="margin-top: 20px; display:flex; gap:12px;">
            <button type="submit" class="rp-btn">Salva modifiche</button>
            <a href="/admin/clienti" class="rp-btn rp-btn-outline">Annulla</a>
        </div>
    </form>

    <form method="post" action="/admin/clienti/elimina"
          onsubmit="return confirm('Eliminare definitivamente questo cliente? L\'operazione non è reversibile.');"
          style="margin-top: 24px;">
        <input type="hidden" name="id" value="<?= (int) $cliente['id'] ?>">
        <button type="submit" class="rp-btn" style="background:#c0392b;">Elimina cliente</button>
    </form>
</div>

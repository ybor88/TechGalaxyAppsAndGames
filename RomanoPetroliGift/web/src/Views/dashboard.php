<?php
/** @var array $user */
$isAdmin = $user['ruolo'] === 'admin';
?>
<div class="rp-card">
    <h1 class="rp-title">Ciao <?= htmlspecialchars($user['nome']) ?>!</h1>
    <p class="rp-subtitle">RP Fidelity — Gestione Fidelizzazione Romano Petroli</p>
    <?php if (!$isAdmin): ?>
        <p>Il tuo saldo punti attuale:</p>
        <span class="rp-points-badge"><?= format_punti((float) $user['punti_saldo']) ?> punti</span>
    <?php endif; ?>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:18px;">Cosa puoi fare</h2>
    <ul>
        <?php if ($isAdmin): ?>
            <li><a href="/admin/statistiche">Consulta le statistiche di clienti e riscatti</a></li>
            <li><a href="/admin/clienti">Gestisci i clienti registrati</a></li>
            <li><a href="/admin/reports">Consulta i report dei rifornimenti</a></li>
            <li><a href="/admin/verifica-voucher">Verifica e valida i voucher dei clienti</a></li>
        <?php else: ?>
            <li><a href="/la-mia-card">Mostra la tua Card per caricare i punti al rifornimento</a></li>
            <li><a href="/rifornimenti">Consulta lo storico dei tuoi rifornimenti</a></li>
            <li><a href="/voucher">Riscatta i tuoi punti in buoni benzina</a></li>
        <?php endif; ?>
    </ul>
</div>

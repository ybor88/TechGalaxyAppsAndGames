<?php
/** @var array $user */
$isAdmin = $user['ruolo'] === 'admin';
$isDipendente = $user['ruolo'] === 'dipendente';
?>
<div class="rp-card">
    <h1 class="rp-title">Ciao <?= htmlspecialchars($user['nome']) ?>!</h1>
    <p class="rp-subtitle">RP Fidelity — Gestione Fidelizzazione Romano Petroli</p>
    <?php if (!$isAdmin && !$isDipendente): ?>
        <p>Il tuo saldo punti attuale:</p>
        <span style="display:inline-flex; align-items:center; gap:10px;">
            <span class="rp-points-badge" id="rp-saldo-punti"><?= format_punti((float) $user['punti_saldo']) ?> punti</span>
            <button type="button" id="rp-saldo-refresh" class="rp-btn rp-btn-outline" style="padding:6px 12px; font-size:13px;" onclick="rpAggiornaSaldoPunti()">&#x21bb; Aggiorna</button>
        </span>
        <script>
            function rpAggiornaSaldoPunti() {
                var btn = document.getElementById('rp-saldo-refresh');
                var badge = document.getElementById('rp-saldo-punti');
                btn.disabled = true;
                var testoOriginale = btn.textContent;
                btn.textContent = 'Aggiorno...';
                fetch('/saldo-punti', { headers: { 'Accept': 'application/json' } })
                    .then(function (res) { return res.json(); })
                    .then(function (data) {
                        if (typeof data.punti_saldo === 'number') {
                            var formattato = (data.punti_saldo % 1 === 0)
                                ? data.punti_saldo.toLocaleString('it-IT')
                                : data.punti_saldo.toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
                            badge.textContent = formattato + ' punti';
                        }
                    })
                    .catch(function () { /* saldo resta quello già mostrato */ })
                    .finally(function () {
                        btn.disabled = false;
                        btn.textContent = testoOriginale;
                    });
            }
        </script>
    <?php endif; ?>
</div>

<div class="rp-card">
    <h2 class="rp-title" style="font-size:18px;">Cosa puoi fare</h2>
    <ul>
        <?php if ($isAdmin): ?>
            <li><a href="/admin/statistiche">Consulta le statistiche di clienti e riscatti</a></li>
            <li><a href="/admin/clienti">Gestisci i clienti registrati</a></li>
            <li><a href="/admin/dipendenti">Gestisci gli account dipendenti</a></li>
            <li><a href="/admin/messaggi">Rispondi ai messaggi dei clienti</a></li>
            <li><a href="/admin/rifornimenti/nuovo">Registra un rifornimento</a></li>
            <li><a href="/admin/reports">Consulta i report dei rifornimenti</a></li>
            <li><a href="/admin/verifica-voucher">Verifica e valida i voucher dei clienti</a></li>
        <?php elseif ($isDipendente): ?>
            <li><a href="/admin/rifornimenti/nuovo">Registra un rifornimento</a></li>
            <li><a href="/admin/verifica-voucher">Verifica ed eroga i voucher dei clienti</a></li>
            <li><a href="/admin/reports">Consulta gli ultimi rifornimenti (verifica i punti caricati)</a></li>
        <?php else: ?>
            <li><a href="/la-mia-card">Mostra la tua Card per caricare i punti al rifornimento</a></li>
            <li><a href="/rifornimenti">Consulta lo storico dei tuoi rifornimenti</a></li>
            <li><a href="/catalogo">Riscatta i tuoi punti in buoni benzina</a></li>
            <li><a href="/voucher">Consulta i tuoi voucher riscattati</a></li>
            <li><a href="/contatti">Contattaci per assistenza</a></li>
            <li><a href="/faq">Domande frequenti</a></li>
        <?php endif; ?>
    </ul>
</div>

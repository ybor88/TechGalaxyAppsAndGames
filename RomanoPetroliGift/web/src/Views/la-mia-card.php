<?php
/** @var array $user */
?>
<div class="rp-card" style="text-align:center; max-width: 380px; margin-left:auto; margin-right:auto;">
    <h1 class="rp-title">La mia Card</h1>
    <p class="rp-subtitle">Mostra questo QR alla cassa per caricare i punti</p>

    <?php if (!empty($user['codice_card'])): ?>
        <img src="https://api.qrserver.com/v1/create-qr-code/?size=320x320&data=<?= urlencode($user['codice_card']) ?>"
             alt="QR Card" style="margin: 16px 0; max-width:100%; height:auto;">
        <div style="font-family: monospace; font-size: 16px; letter-spacing: 1px;"><?= htmlspecialchars($user['codice_card']) ?></div>
        <p style="margin-top: 20px;">
            Saldo punti: <span class="rp-points-badge" id="rp-saldo-punti"><?= format_punti((float) $user['punti_saldo']) ?> punti</span>
            <button type="button" id="rp-saldo-refresh" class="rp-btn rp-btn-outline" style="padding:6px 12px; font-size:13px; margin-left:8px;" onclick="rpAggiornaSaldoPunti()">&#x21bb; Aggiorna</button>
        </p>
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
    <?php else: ?>
        <p>Nessun codice card associato al tuo account.</p>
    <?php endif; ?>
</div>

<?php

use App\Core\Auth;

/** @var bool $apkDisponibile */
?>
<div class="rp-card">
    <h1 class="rp-title">Scarica l'app RP Fidelity</h1>
    <p class="rp-subtitle">Disponibile per Android e per iPhone/iPad</p>
</div>

<div style="display:flex; gap:20px; flex-wrap:wrap;">
    <div class="rp-card" id="rp-card-android" style="flex:1; min-width:280px;">
        <h2 class="rp-title" style="font-size:18px;">📱 Android</h2>
        <p>Scarica e installa il file APK direttamente sul tuo telefono Android.</p>
        <?php if ($apkDisponibile): ?>
            <a href="/downloads/RPFidelity.apk" class="rp-btn" style="display:inline-block; margin-top:8px;">Scarica APK</a>
        <?php else: ?>
            <p class="rp-alert rp-alert-error">L'app Android non è ancora disponibile per il download. Torna a trovarci a breve.</p>
        <?php endif; ?>
        <p style="margin-top:16px; font-size:13px; color:#5b6180;">
            Al primo avvio, il telefono potrebbe chiederti di autorizzare l'installazione da "origini sconosciute": è normale per le app installate fuori dal Play Store, conferma per procedere.
        </p>
    </div>

    <div class="rp-card" id="rp-card-ios" style="flex:1; min-width:280px;">
        <h2 class="rp-title" style="font-size:18px;">🍎 iPhone / iPad</h2>
        <p>Su iOS non è disponibile un file da installare: aggiungi il portale RP Fidelity alla schermata Home. Funzionerà esattamente come l'app Android, con la sua icona dedicata.</p>
        <ol style="padding-left: 20px;">
            <li>Apri questo sito con <strong>Safari</strong></li>
            <li>Tocca l'icona <strong>Condividi</strong> (il quadrato con la freccia verso l'alto)</li>
            <li>Scegli <strong>"Aggiungi a Home"</strong></li>
            <li>Conferma: l'icona RP Fidelity comparirà sulla tua schermata Home</li>
        </ol>
    </div>
</div>

<div class="rp-card" style="margin-top:20px;">
    <?php if (Auth::check()): ?>
        <a href="/dashboard" class="rp-btn rp-btn-outline" style="display:inline-block;">&larr; Torna alla dashboard</a>
    <?php else: ?>
        <div style="display:flex; gap:12px;">
            <a href="/login" class="rp-btn">Accedi</a>
            <a href="/registrati" class="rp-btn rp-btn-outline">Registrati</a>
        </div>
    <?php endif; ?>
</div>

<script>
    // Evidenzia la scheda giusta in base al dispositivo (solo estetico, entrambe restano visibili).
    (function () {
        var isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);
        var card = document.getElementById(isIOS ? 'rp-card-ios' : 'rp-card-android');
        if (card) {
            card.style.borderColor = '#f5821f';
            card.style.borderWidth = '2px';
        }
    })();
</script>

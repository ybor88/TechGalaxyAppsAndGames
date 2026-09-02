<?php
// Copyright (c) Roberto Di Flumeri
$domande = [
    [
        'q' => 'Come si accumulano i punti?',
        'a' => 'Ogni volta che fai rifornimento presso il nostro distributore, maturi 1 punto ogni 10 euro spesi (es. 45€ di rifornimento = 4,50 punti). I punti vengono accreditati automaticamente sul tuo saldo dopo ogni rifornimento.',
    ],
    [
        'q' => 'Come riscatto un voucher?',
        'a' => 'Vai nella sezione "Catalogo premi": ogni voucher mostra i punti necessari per ottenerlo. Se hai punti a sufficienza puoi premere "Riscatta" e il voucher verrà aggiunto a "I miei voucher".',
    ],
    [
        'q' => 'Dove trovo i voucher che ho già riscattato?',
        'a' => 'Nella sezione "I miei voucher" trovi tutti i tuoi buoni con il relativo codice QR, la scadenza e lo stato (attivo, usato o scaduto).',
    ],
    [
        'q' => 'Come uso un voucher al distributore?',
        'a' => 'Mostra il codice QR del voucher al gestore al momento del rifornimento: verrà scalato dall\'importo da pagare. Puoi applicare più voucher allo stesso rifornimento, purché il loro valore complessivo non superi l\'importo del rifornimento.',
    ],
    [
        'q' => 'I voucher scadono?',
        'a' => 'Sì, ogni voucher riscattato ha una data di scadenza indicata nella sezione "I miei voucher". Ti consigliamo di utilizzarlo prima di quella data.',
    ],
    [
        'q' => 'Come mostro la mia Card per accumulare punti?',
        'a' => 'Nella sezione "La mia Card" trovi il tuo QR personale: mostralo al gestore ad ogni rifornimento per far accreditare i punti sul tuo account.',
    ],
    [
        'q' => 'Come modifico i miei dati personali o la password?',
        'a' => 'Vai in "Impostazioni": da lì puoi aggiornare nome, cognome, email e telefono, oppure cambiare la password del tuo account.',
    ],
    [
        'q' => 'Non trovo risposta alla mia domanda, cosa faccio?',
        'a' => 'Scrivici dalla sezione "Contatti": ti risponderemo il prima possibile.',
    ],
];
?>
<div class="rp-card">
    <h1 class="rp-title">Domande frequenti</h1>
    <p class="rp-subtitle">Tutto quello che c'è da sapere sul programma fedeltà RP Fidelity</p>

    <?php foreach ($domande as $d): ?>
        <details style="border-bottom: 1px solid var(--rp-gray-border); padding: 14px 0;">
            <summary style="cursor:pointer; font-weight:600; color: var(--rp-navy);"><?= htmlspecialchars($d['q']) ?></summary>
            <p style="margin: 10px 0 0; color: #5b6180;"><?= htmlspecialchars($d['a']) ?></p>
        </details>
    <?php endforeach; ?>
</div>

// Scanner QR condiviso (fotocamera, via html5-qrcode) per le pagine admin che leggono
// codici card/voucher. Richiede nel markup della pagina: un input con id = targetInputId,
// e il modale #rp-scanner-modal / #rp-qr-reader (vedi registra-rifornimento.php / verifica-voucher.php).
let rpScannerTargetId = null;
let rpHtml5QrCode = null;

function rpOpenScanner(targetInputId, onDecoded) {
    rpScannerTargetId = targetInputId;
    document.getElementById('rp-scanner-modal').style.display = 'flex';
    rpHtml5QrCode = new Html5Qrcode('rp-qr-reader');

    // Su iOS in modalità PWA standalone (soprattutto prima di iOS 15.4) l'avvio della
    // fotocamera può restare bloccato senza mai risolversi né dare errore (bug WebKit):
    // questo timeout evita che l'utente resti fermo su un riquadro vuoto senza spiegazioni.
    let rpScannerSettled = false;
    const rpScannerWatchdog = setTimeout(function () {
        if (!rpScannerSettled) {
            rpScannerSettled = true;
            alert('La fotocamera non si è avviata. Su iPhone questo può succedere nell\'app installata in Home: prova ad aprire il sito da Safari, oppure inserisci il codice a mano nel campo di testo.');
            rpCloseScanner();
        }
    }, 6000);

    rpHtml5QrCode.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: 220 },
        function (decodedText) {
            rpScannerSettled = true;
            clearTimeout(rpScannerWatchdog);
            const input = document.getElementById(rpScannerTargetId);
            input.value = decodedText;
            rpCloseScanner();
            if (typeof onDecoded === 'function') {
                onDecoded(decodedText, input);
            }
        },
        function () {}
    ).then(function () {
        rpScannerSettled = true;
        clearTimeout(rpScannerWatchdog);
    }).catch(function (err) {
        rpScannerSettled = true;
        clearTimeout(rpScannerWatchdog);
        alert(rpScannerErrorMessage(err));
        rpCloseScanner();
    });
}

function rpScannerErrorMessage(err) {
    const name = (err && err.name) || '';
    if (name === 'NotAllowedError' || name === 'SecurityError') {
        return 'Permesso fotocamera negato. Su iPhone: apri questo sito in Safari (non dall\'icona salvata in Home), tocca "aA" nella barra degli indirizzi > Impostazioni sito web > Fotocamera > Consenti, poi riprova. Su Android: controlla i permessi del sito/app nelle impostazioni del browser.';
    }
    if (name === 'NotFoundError' || name === 'OverconstrainedError') {
        return 'Nessuna fotocamera trovata sul dispositivo, oppure non è disponibile una fotocamera posteriore.';
    }
    if (name === 'NotReadableError') {
        return 'La fotocamera è occupata da un\'altra app. Chiudi le altre app che la usano e riprova.';
    }
    return 'Impossibile accedere alla fotocamera: ' + err + '. Su iPhone assicurati di avere iOS 15.4 o superiore.';
}

function rpCloseScanner() {
    if (rpHtml5QrCode) {
        rpHtml5QrCode.stop().then(function () {
            rpHtml5QrCode.clear();
        }).catch(function () {});
    }
    document.getElementById('rp-scanner-modal').style.display = 'none';
}

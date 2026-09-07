// Scanner QR condiviso (fotocamera, via html5-qrcode) per le pagine admin che leggono
// codici card/voucher. Richiede nel markup della pagina: un input con id = targetInputId,
// e il modale #rp-scanner-modal / #rp-qr-reader (vedi registra-rifornimento.php / verifica-voucher.php).
let rpScannerTargetId = null;
let rpHtml5QrCode = null;

function rpOpenScanner(targetInputId, onDecoded) {
    rpScannerTargetId = targetInputId;
    document.getElementById('rp-scanner-modal').style.display = 'flex';
    rpHtml5QrCode = new Html5Qrcode('rp-qr-reader');
    rpHtml5QrCode.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: 220 },
        function (decodedText) {
            const input = document.getElementById(rpScannerTargetId);
            input.value = decodedText;
            rpCloseScanner();
            if (typeof onDecoded === 'function') {
                onDecoded(decodedText, input);
            }
        },
        function () {}
    ).catch(function (err) {
        alert('Impossibile accedere alla fotocamera: ' + err);
        rpCloseScanner();
    });
}

function rpCloseScanner() {
    if (rpHtml5QrCode) {
        rpHtml5QrCode.stop().then(function () {
            rpHtml5QrCode.clear();
        }).catch(function () {});
    }
    document.getElementById('rp-scanner-modal').style.display = 'none';
}

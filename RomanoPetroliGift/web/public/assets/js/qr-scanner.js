// Scanner QR condiviso (fotocamera, via html5-qrcode) per le pagine admin che leggono
// codici card/voucher. Richiede nel markup della pagina: un input con id = targetInputId,
// e il modale #rp-scanner-modal / #rp-qr-reader (vedi registra-rifornimento.php / verifica-voucher.php).
//
// Su iPhone, quando il sito è installato come PWA in Home (modalità standalone), avviare la
// fotocamera live via getUserMedia può bloccarsi indefinitamente senza mai risolversi né dare
// errore (bug noto di WebKit, non riproducibile su Android/Chrome). Per questo motivo offriamo
// SEMPRE, fin da subito, un'alternativa "Scatta una foto" basata su <input type="file" capture>:
// usa la fotocamera nativa del sistema (non getUserMedia), quindi funziona in modo affidabile
// anche nelle PWA iOS, e decodifichiamo il QR dalla foto con Html5Qrcode.scanFile().
let rpScannerTargetId = null;
let rpOnDecodedCallback = null;
let rpHtml5QrCode = null;
let rpFileHtml5QrCode = null;

function rpOpenScanner(targetInputId, onDecoded) {
    rpScannerTargetId = targetInputId;
    rpOnDecodedCallback = onDecoded;
    document.getElementById('rp-scanner-modal').style.display = 'flex';
    rpSetFallbackMessage('');
    rpEnsureFallbackUi();

    rpHtml5QrCode = new Html5Qrcode('rp-qr-reader');

    rpHtml5QrCode.start(
        {
            facingMode: 'environment',
            // Risoluzione più alta possibile: utile soprattutto quando si inquadra il QR
            // mostrato sullo schermo di un altro telefono (caso tipico: card del cliente),
            // dove la fotocamera ha bisogno di più dettaglio per isolare i moduli del QR
            // rispetto a un QR stampato su carta.
            width: { ideal: 1920 },
            height: { ideal: 1080 }
        },
        {
            fps: 10,
            // Riquadro proporzionale al frame video reale (anziché un valore fisso in pixel):
            // su iPhone il frame della fotocamera spesso ha proporzioni diverse da Android, e un
            // riquadro fisso può non corrispondere alla zona che la libreria analizza davvero,
            // facendo sembrare che "la fotocamera si apre ma non legge mai il QR".
            qrbox: function (viewfinderWidth, viewfinderHeight) {
                const minEdge = Math.min(viewfinderWidth, viewfinderHeight);
                const size = Math.floor(minEdge * 0.7);
                return { width: size, height: size };
            }
        },
        function (decodedText) {
            rpHandleDecoded(decodedText);
        },
        function () {}
    ).catch(function (err) {
        rpSetFallbackMessage(rpScannerErrorMessage(err));
    });
}

function rpHandleDecoded(decodedText) {
    const input = document.getElementById(rpScannerTargetId);
    input.value = decodedText;
    const callback = rpOnDecodedCallback;
    rpCloseScanner();
    if (typeof callback === 'function') {
        callback(decodedText, input);
    }
}

function rpEnsureFallbackUi() {
    if (document.getElementById('rp-qr-fallback')) {
        return;
    }
    const box = document.querySelector('#rp-scanner-modal .rp-modal-box');
    const closeBtn = document.getElementById('rp-scanner-modal').querySelector('.rp-modal-close');

    const wrap = document.createElement('div');
    wrap.id = 'rp-qr-fallback';
    wrap.style.marginTop = '14px';
    wrap.innerHTML =
        '<p id="rp-qr-fallback-msg" style="color:#5b6180; font-size:13px; min-height:18px;"></p>' +
        '<input type="file" accept="image/*" capture="environment" id="rp-qr-file-input" style="display:none;">' +
        '<button type="button" class="rp-btn rp-btn-outline" id="rp-qr-fallback-btn" style="width:100%;">' +
        'La fotocamera live non si apre? Scatta una foto del QR' +
        '</button>' +
        '<div id="rp-qr-reader-file" style="display:none;"></div>';

    box.insertBefore(wrap, closeBtn);

    document.getElementById('rp-qr-fallback-btn').addEventListener('click', function () {
        document.getElementById('rp-qr-file-input').click();
    });
    document.getElementById('rp-qr-file-input').addEventListener('change', function (e) {
        const file = e.target.files && e.target.files[0];
        e.target.value = '';
        if (file) {
            rpDecodeFromFile(file);
        }
    });
}

function rpSetFallbackMessage(message) {
    const msgEl = document.getElementById('rp-qr-fallback-msg');
    if (msgEl) {
        msgEl.textContent = message;
    }
}

function rpDecodeFromFile(file) {
    rpSetFallbackMessage('Lettura del QR in corso...');
    if (!rpFileHtml5QrCode) {
        rpFileHtml5QrCode = new Html5Qrcode('rp-qr-reader-file');
    }
    rpFileHtml5QrCode.scanFile(file, false)
        .then(function (decodedText) {
            rpHandleDecoded(decodedText);
        })
        .catch(function () {
            rpSetFallbackMessage('QR non riconosciuto nella foto: riprova più vicino e con buona luce, oppure inserisci il codice a mano.');
        });
}

function rpScannerErrorMessage(err) {
    const name = (err && err.name) || '';
    if (name === 'NotAllowedError' || name === 'SecurityError') {
        return 'Permesso fotocamera negato. Su iPhone: apri "aA" nella barra indirizzi > Impostazioni sito web > Fotocamera > Consenti. Nel frattempo puoi usare "Scatta una foto" qui sotto.';
    }
    if (name === 'NotFoundError' || name === 'OverconstrainedError') {
        return 'Nessuna fotocamera posteriore trovata. Usa "Scatta una foto" qui sotto.';
    }
    if (name === 'NotReadableError') {
        return 'La fotocamera è occupata da un\'altra app. Chiudila e riprova, oppure usa "Scatta una foto" qui sotto.';
    }
    return 'Fotocamera live non disponibile. Usa "Scatta una foto" qui sotto, oppure inserisci il codice a mano.';
}

function rpCloseScanner() {
    if (rpHtml5QrCode) {
        rpHtml5QrCode.stop().then(function () {
            rpHtml5QrCode.clear();
        }).catch(function () {});
    }
    rpSetFallbackMessage('');
    document.getElementById('rp-scanner-modal').style.display = 'none';
}

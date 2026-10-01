// Scanner QR condiviso (fotocamera) per le pagine admin che leggono codici card/voucher.
// Richiede nel markup della pagina: un input con id = targetInputId, e il modale
// #rp-scanner-modal / #rp-qr-reader (vedi registra-rifornimento.php / verifica-voucher.php).
//
// Motore di decodifica: quando il browser espone l'API nativa BarcodeDetector (Chrome/Edge su
// Android e desktop) la usiamo in via prioritaria — è un decoder a livello di sistema operativo,
// paragonabile in affidabilità al ML Kit usato dall'app Android nativa, e decisamente più robusto
// del decoder "fatto in casa" in puro JavaScript (html5-qrcode/jsQR) soprattutto quando si inquadra
// un QR mostrato sullo schermo di un altro telefono (il caso tipico: la card del cliente) invece
// che stampato su carta. Dove BarcodeDetector non è disponibile (Safari/iOS, Firefox) si ricade
// su html5-qrcode. In più, offriamo SEMPRE un'alternativa "Scatta una foto" basata su
// <input type="file" capture">: usa la fotocamera nativa del sistema (non getUserMedia), quindi
// funziona anche quando il flusso live si blocca (bug noto di WebKit nelle PWA iOS installate in Home).
let rpScannerTargetId = null;
let rpOnDecodedCallback = null;

// Percorso "nativo" (BarcodeDetector)
const rpHasBarcodeDetector = typeof window !== 'undefined' && 'BarcodeDetector' in window;
let rpBarcodeDetector = null;
let rpNativeStream = null;
let rpNativeVideoEl = null;
let rpNativeRafId = null;

// Percorso di fallback (html5-qrcode)
let rpHtml5QrCode = null;
let rpFileHtml5QrCode = null;

function rpGetBarcodeDetector() {
    if (!rpBarcodeDetector) {
        rpBarcodeDetector = new BarcodeDetector({ formats: ['qr_code'] });
    }
    return rpBarcodeDetector;
}

const rpVideoConstraints = {
    facingMode: 'environment',
    // Risoluzione più alta possibile: utile soprattutto quando si inquadra il QR mostrato
    // sullo schermo di un altro telefono, dove serve più dettaglio per isolare i moduli del QR
    // rispetto a un QR stampato su carta.
    width: { ideal: 1920 },
    height: { ideal: 1080 }
};

function rpOpenScanner(targetInputId, onDecoded) {
    rpScannerTargetId = targetInputId;
    rpOnDecodedCallback = onDecoded;
    document.getElementById('rp-scanner-modal').style.display = 'flex';
    rpSetFallbackMessage('');
    rpEnsureFallbackUi();

    let detectorPronto = false;
    if (rpHasBarcodeDetector) {
        try {
            rpGetBarcodeDetector();
            detectorPronto = true;
        } catch (e) {
            detectorPronto = false;
        }
    }

    if (detectorPronto) {
        rpStartNativeScanner();
    } else {
        rpStartHtml5QrScanner();
    }
}

function rpStartNativeScanner() {
    const container = document.getElementById('rp-qr-reader');
    container.innerHTML = '';

    rpNativeVideoEl = document.createElement('video');
    rpNativeVideoEl.setAttribute('playsinline', 'true');
    rpNativeVideoEl.setAttribute('muted', 'true');
    rpNativeVideoEl.muted = true;
    rpNativeVideoEl.style.width = '100%';
    rpNativeVideoEl.style.borderRadius = '8px';
    container.appendChild(rpNativeVideoEl);

    navigator.mediaDevices.getUserMedia({ video: rpVideoConstraints })
        .then(function (stream) {
            rpNativeStream = stream;
            rpNativeVideoEl.srcObject = stream;
            return rpNativeVideoEl.play();
        })
        .then(function () {
            const detector = rpGetBarcodeDetector();
            const scanFrame = function () {
                if (!rpNativeStream) {
                    return;
                }
                detector.detect(rpNativeVideoEl).then(function (codici) {
                    if (codici.length > 0 && codici[0].rawValue) {
                        rpHandleDecoded(codici[0].rawValue);
                        return;
                    }
                    rpNativeRafId = requestAnimationFrame(scanFrame);
                }).catch(function () {
                    rpNativeRafId = requestAnimationFrame(scanFrame);
                });
            };
            scanFrame();
        })
        .catch(function (err) {
            rpStopNativeScanner();
            // Se l'accesso nativo alla fotocamera fallisce proviamo comunque con html5-qrcode
            // prima di arrenderci: alcuni browser riportano BarcodeDetector come disponibile ma
            // falliscono su dettagli minori dell'implementazione.
            rpStartHtml5QrScanner(err);
        });
}

function rpStopNativeScanner() {
    if (rpNativeRafId) {
        cancelAnimationFrame(rpNativeRafId);
        rpNativeRafId = null;
    }
    if (rpNativeStream) {
        rpNativeStream.getTracks().forEach(function (track) { track.stop(); });
        rpNativeStream = null;
    }
    rpNativeVideoEl = null;
}

function rpStartHtml5QrScanner(previousErr) {
    rpHtml5QrCode = new Html5Qrcode('rp-qr-reader');

    rpHtml5QrCode.start(
        rpVideoConstraints,
        {
            fps: 10,
            // Riquadro proporzionale al frame video reale (anziché un valore fisso in pixel):
            // su mobile il frame della fotocamera spesso ha proporzioni diverse dal desktop, e un
            // riquadro fisso può non corrispondere alla zona che la libreria analizza davvero.
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
        rpSetFallbackMessage(rpScannerErrorMessage(err || previousErr));
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
        'Non legge il QR? Scatta una foto' +
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

    if (rpHasBarcodeDetector) {
        createImageBitmap(file)
            .then(function (bitmap) {
                return rpGetBarcodeDetector().detect(bitmap);
            })
            .then(function (codici) {
                if (codici.length > 0 && codici[0].rawValue) {
                    rpHandleDecoded(codici[0].rawValue);
                } else {
                    rpDecodeFromFileWithHtml5Qr(file);
                }
            })
            .catch(function () {
                rpDecodeFromFileWithHtml5Qr(file);
            });
    } else {
        rpDecodeFromFileWithHtml5Qr(file);
    }
}

function rpDecodeFromFileWithHtml5Qr(file) {
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
    rpStopNativeScanner();
    if (rpHtml5QrCode) {
        rpHtml5QrCode.stop().then(function () {
            rpHtml5QrCode.clear();
        }).catch(function () {});
        rpHtml5QrCode = null;
    }
    rpSetFallbackMessage('');
    document.getElementById('rp-scanner-modal').style.display = 'none';
}

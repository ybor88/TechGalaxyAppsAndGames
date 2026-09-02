-- RP Fidelity — dati di prova

INSERT INTO distributori (nome, indirizzo, citta) VALUES
    ('Romano Petroli - Sant''Anastasia', 'Via Nazionale', 'Sant''Anastasia');

-- Password admin: admin123
INSERT INTO users (nome, cognome, email, password_hash, ruolo, punti_saldo) VALUES
    ('Admin', 'RP Fidelity', 'admin@rpfidelity.it', '$2y$10$ZYmQre.wwTXtJ/Z2G3MiouXZGcqFxxApeboKWHqyBc/8lEvzMgKIe', 'admin', 0);

-- Password cliente: cliente123
-- Saldo punti coerente con i rifornimenti sotto (1 punto ogni 10 euro): 2+5+3+10+15+20 = 55
INSERT INTO users (nome, cognome, email, password_hash, ruolo, punti_saldo, codice_card) VALUES
    ('Mario', 'Rossi', 'cliente@rpfidelity.it', '$2y$10$qvnI6o6whsnt0hQgfXpXV.OSLr53du.AqCZwbW0eQCdv6tuHUAkcS', 'cliente', 55.00, 'RPFDEMO0001');

-- Catalogo voucher: ogni 50 punti = 5 euro di buono in più (50→5€, 100→10€, ... 300→30€)
INSERT INTO voucher_catalogo (nome, costo_punti, importo_premio, attivo) VALUES
    ('Buono 5 euro', 50, 5.00, 1),
    ('Buono 10 euro', 100, 10.00, 1),
    ('Buono 15 euro', 150, 15.00, 1),
    ('Buono 20 euro', 200, 20.00, 1),
    ('Buono 25 euro', 250, 25.00, 1),
    ('Buono 30 euro', 300, 30.00, 1);

INSERT INTO rifornimenti (user_id, distributore_id, data_ora, codice_rifornimento, importo, importo_pagato, importo_voucher, punti_maturati) VALUES
    (2, 1, '2026-08-01 07:57:00', 'RF260801001', 20.00, 20.00, 0.00, 2.00),
    (2, 1, '2026-08-01 08:20:00', 'RF260801002', 50.00, 50.00, 0.00, 5.00),
    (2, 1, '2026-08-01 08:27:00', 'RF260801003', 30.00, 30.00, 0.00, 3.00),
    (2, 1, '2026-08-01 09:10:00', 'RF260801004', 100.00, 100.00, 0.00, 10.00),
    (2, 1, '2026-08-01 09:47:00', 'RF260801005', 150.00, 150.00, 0.00, 15.00),
    (2, 1, '2026-08-01 09:59:00', 'RF260801006', 200.00, 200.00, 0.00, 20.00),
    (NULL, 1, '2026-08-01 08:31:00', 'RF260801007', 20.00, 20.00, 0.00, 0.00),
    (NULL, 1, '2026-08-01 08:55:00', 'RF260801008', 40.00, 40.00, 0.00, 0.00);

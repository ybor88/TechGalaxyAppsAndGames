-- RP Fidelity — schema database

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS distributori (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    indirizzo VARCHAR(255) NULL,
    citta VARCHAR(100) NULL,
    creato_il DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    cognome VARCHAR(100) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    telefono VARCHAR(30) NULL,
    -- codice della "card" digitale del cliente, mostrato come QR nell'app e scansionato dall'admin al rifornimento
    codice_card VARCHAR(20) NULL UNIQUE,
    ruolo ENUM('cliente','admin') NOT NULL DEFAULT 'cliente',
    punti_saldo DECIMAL(10,2) NOT NULL DEFAULT 0,
    stato ENUM('attivo','sospeso') NOT NULL DEFAULT 'attivo',
    api_token VARCHAR(64) NULL UNIQUE,
    data_registrazione DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rifornimenti (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NULL,
    distributore_id INT UNSIGNED NOT NULL,
    data_ora DATETIME NOT NULL,
    codice_rifornimento VARCHAR(50) NOT NULL,
    importo DECIMAL(10,2) NOT NULL DEFAULT 0,
    importo_pagato DECIMAL(10,2) NOT NULL DEFAULT 0,
    importo_voucher DECIMAL(10,2) NOT NULL DEFAULT 0,
    -- 1 punto ogni 10 euro di carburante (frazionabile, es. 45 euro = 4.50 punti)
    punti_maturati DECIMAL(10,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_rifornimenti_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_rifornimenti_distributore FOREIGN KEY (distributore_id) REFERENCES distributori(id),
    INDEX idx_rifornimenti_data (data_ora),
    INDEX idx_rifornimenti_distributore (distributore_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS voucher_catalogo (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    costo_punti INT NOT NULL,
    importo_premio DECIMAL(10,2) NOT NULL,
    attivo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS voucher_utente (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    voucher_catalogo_id INT UNSIGNED NOT NULL,
    codice_voucher VARCHAR(40) NOT NULL UNIQUE,
    data_riscatto DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_scadenza DATE NOT NULL,
    stato ENUM('attivo','usato','scaduto') NOT NULL DEFAULT 'attivo',
    rifornimento_id_utilizzo INT UNSIGNED NULL,
    data_utilizzo DATETIME NULL,
    CONSTRAINT fk_voucher_utente_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_voucher_utente_catalogo FOREIGN KEY (voucher_catalogo_id) REFERENCES voucher_catalogo(id),
    CONSTRAINT fk_voucher_utente_rifornimento FOREIGN KEY (rifornimento_id_utilizzo) REFERENCES rifornimenti(id) ON DELETE SET NULL,
    INDEX idx_voucher_utente_stato (stato)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messaggi_contatto (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NULL,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(190) NOT NULL,
    messaggio TEXT NOT NULL,
    creato_il DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messaggi_contatto_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS punti_transazioni (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    tipo ENUM('accredito','addebito') NOT NULL,
    punti DECIMAL(10,2) NOT NULL,
    causale VARCHAR(255) NOT NULL,
    riferimento_id INT UNSIGNED NULL,
    creato_il DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_punti_transazioni_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

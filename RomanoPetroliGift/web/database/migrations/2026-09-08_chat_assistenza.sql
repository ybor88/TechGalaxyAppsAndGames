-- Trasforma messaggi_contatto (form di contatto one-shot) in una vera chat cliente <-> admin.
-- Da eseguire una tantum sul database di produzione (phpMyAdmin su Aruba).
ALTER TABLE messaggi_contatto
    ADD COLUMN mittente ENUM('cliente','admin') NOT NULL DEFAULT 'cliente' AFTER email;

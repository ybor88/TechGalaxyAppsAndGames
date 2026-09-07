-- Aggiunge il ruolo "dipendente" (cassa: solo registrazione rifornimenti) agli utenti esistenti.
-- Da eseguire una tantum sul database di produzione (es. da phpMyAdmin su Aruba), poiché
-- schema.sql viene applicato solo in fase di primo setup e non ri-eseguito sugli ambienti già in vita.
ALTER TABLE users MODIFY ruolo ENUM('cliente','admin','dipendente') NOT NULL DEFAULT 'cliente';

-- Aggiunge il supporto al recupero password via email (token monouso con scadenza).
-- Da eseguire una tantum sul database di produzione (phpMyAdmin su Aruba), poiché
-- schema.sql viene applicato solo in fase di primo setup e non ri-eseguito sugli ambienti già in vita.
ALTER TABLE users
    ADD COLUMN password_reset_token VARCHAR(64) NULL UNIQUE AFTER api_token,
    ADD COLUMN password_reset_scadenza DATETIME NULL AFTER password_reset_token;

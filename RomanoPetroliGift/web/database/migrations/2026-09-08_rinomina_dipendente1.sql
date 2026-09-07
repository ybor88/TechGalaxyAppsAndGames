-- Una tantum: rinomina l'email del dipendente creato come dipendente1@rpfidelity.it.
-- Password invariata. Deve restare un solo account di ruolo 'dipendente'.
UPDATE users SET email = 'antonio.carfora.94@gmail.com' WHERE email = 'dipendente1@rpfidelity.it' AND ruolo = 'dipendente';

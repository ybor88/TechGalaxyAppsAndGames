// © Roberto Di Flumeri
// Carica backend/.env in process.env prima di qualunque altro modulo (Node >= 20.12).
try {
  process.loadEnvFile();
} catch {
  // .env assente: si usano le variabili d'ambiente di sistema
}

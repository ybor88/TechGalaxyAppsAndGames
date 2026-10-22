// Avvio del server API di Nexora e dei job periodici (alert e riordino automatico). © Roberto Di Flumeri
import { creaApp } from "./app.js";
import { config } from "./lib/config.js";
import { db } from "./lib/db.js";
import { aggiornaAlert } from "./services/ai.js";

const app = await creaApp({ log: true });

async function jobAlert() {
  for (const a of await db.azienda.findMany({ select: { id: true } })) {
    try {
      const r = await aggiornaAlert(a.id);
      if (r.ordiniCreati) app.log.info({ azienda: a.id, ...r }, "riordino automatico");
    } catch (e) {
      app.log.error(e, "errore nel job degli alert");
    }
  }
}

await app.listen({ port: config.porta, host: config.host });
setTimeout(jobAlert, 5_000);
setInterval(jobAlert, 60 * 60 * 1000);

for (const segnale of ["SIGINT", "SIGTERM"]) {
  process.on(segnale, async () => {
    await app.close();
    await db.$disconnect();
    process.exit(0);
  });
}

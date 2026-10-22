// Configurazione dei test (database SQLite dedicato, esecuzione sequenziale). © Roberto Di Flumeri
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globalSetup: ["./test/setup-globale.ts"],
    env: {
      DATABASE_URL: "file:./test.db",
      NEXORA_JWT_SECRET: "segreto-di-test-nexora-0123456789-abcdefghijklmnop",
      NEXORA_DATA_KEY: Buffer.alloc(32, 7).toString("base64"),
      NEXORA_UPLOADS: "./test-uploads",
      // le API esterne puntano a un server finto locale avviato dai test
      NEXORA_STRIPE_API: "http://127.0.0.1:8399",
      NEXORA_PAYPAL_API: "http://127.0.0.1:8399",
      NEXORA_DHL_API: "http://127.0.0.1:8399",
    },
    fileParallelism: false,
    testTimeout: 30000,
    hookTimeout: 60000,
  },
});

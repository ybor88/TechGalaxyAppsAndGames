// Ricrea il database di test prima di ogni esecuzione. © Roberto Di Flumeri
import { execSync } from "node:child_process";
import { rmSync } from "node:fs";

export default function setup() {
  rmSync("./prisma/test.db", { force: true });
  rmSync("./prisma/test.db-journal", { force: true });
  rmSync("./test-uploads", { recursive: true, force: true });
  execSync("npx prisma db push --skip-generate", { env: { ...process.env, DATABASE_URL: "file:./test.db" }, stdio: "ignore" });
}

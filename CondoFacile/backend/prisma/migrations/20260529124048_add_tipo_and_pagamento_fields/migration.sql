-- AlterTable
ALTER TABLE "PagamentoQuota" ADD COLUMN "metodoPagamento" TEXT;
ALTER TABLE "PagamentoQuota" ADD COLUMN "note" TEXT;

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_Condomino" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "nome" TEXT NOT NULL,
    "cognome" TEXT NOT NULL,
    "email" TEXT,
    "telefono" TEXT,
    "unita" TEXT NOT NULL,
    "millesimi" REAL NOT NULL DEFAULT 0,
    "tipo" TEXT NOT NULL DEFAULT 'proprietario',
    "stato" TEXT NOT NULL DEFAULT 'attivo',
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Condomino_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
INSERT INTO "new_Condomino" ("cognome", "condominioId", "email", "id", "millesimi", "nome", "stato", "telefono", "unita") SELECT "cognome", "condominioId", "email", "id", "millesimi", "nome", "stato", "telefono", "unita" FROM "Condomino";
DROP TABLE "Condomino";
ALTER TABLE "new_Condomino" RENAME TO "Condomino";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

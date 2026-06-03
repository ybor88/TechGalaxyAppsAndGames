-- CreateTable
CREATE TABLE "ComunicazioneLettura" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "comunicazioneId" INTEGER NOT NULL,
    "condominoId" INTEGER NOT NULL,
    "dataLettura" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ComunicazioneLettura_comunicazioneId_fkey" FOREIGN KEY ("comunicazioneId") REFERENCES "Comunicazione" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ComunicazioneLettura_condominoId_fkey" FOREIGN KEY ("condominoId") REFERENCES "Condomino" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_Comunicazione" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "titolo" TEXT NOT NULL,
    "corpo" TEXT NOT NULL DEFAULT '',
    "tipo" TEXT NOT NULL,
    "data" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "destinatari" INTEGER NOT NULL DEFAULT 0,
    "destinatariTipo" TEXT NOT NULL DEFAULT 'tutti',
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Comunicazione_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);
INSERT INTO "new_Comunicazione" ("condominioId", "data", "destinatari", "id", "tipo", "titolo") SELECT "condominioId", "data", "destinatari", "id", "tipo", "titolo" FROM "Comunicazione";
DROP TABLE "Comunicazione";
ALTER TABLE "new_Comunicazione" RENAME TO "Comunicazione";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

-- CreateIndex
CREATE UNIQUE INDEX "ComunicazioneLettura_comunicazioneId_condominoId_key" ON "ComunicazioneLettura"("comunicazioneId", "condominoId");

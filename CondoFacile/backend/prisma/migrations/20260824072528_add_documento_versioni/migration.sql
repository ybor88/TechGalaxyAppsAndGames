-- CreateTable
CREATE TABLE "DocumentoVersione" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "documentoId" INTEGER NOT NULL,
    "versione" INTEGER NOT NULL,
    "filePath" TEXT NOT NULL,
    "fileSize" INTEGER NOT NULL,
    "mimeType" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "DocumentoVersione_documentoId_fkey" FOREIGN KEY ("documentoId") REFERENCES "Documento" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_QuotaMensile" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "mese" INTEGER NOT NULL,
    "anno" INTEGER NOT NULL,
    "importoTotale" REAL NOT NULL,
    "tipo" TEXT NOT NULL DEFAULT 'collettiva',
    "destinatarioId" INTEGER,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "QuotaMensile_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "QuotaMensile_destinatarioId_fkey" FOREIGN KEY ("destinatarioId") REFERENCES "Condomino" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_QuotaMensile" ("anno", "condominioId", "id", "importoTotale", "mese") SELECT "anno", "condominioId", "id", "importoTotale", "mese" FROM "QuotaMensile";
DROP TABLE "QuotaMensile";
ALTER TABLE "new_QuotaMensile" RENAME TO "QuotaMensile";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

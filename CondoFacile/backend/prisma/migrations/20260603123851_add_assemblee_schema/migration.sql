-- CreateTable
CREATE TABLE "Assemblea" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "titolo" TEXT NOT NULL,
    "data" DATETIME NOT NULL,
    "luogo" TEXT NOT NULL DEFAULT '',
    "tipo" TEXT NOT NULL DEFAULT 'ordinaria',
    "stato" TEXT NOT NULL DEFAULT 'convocata',
    "ordineDelGiorno" TEXT NOT NULL DEFAULT '',
    "verbale" TEXT,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Assemblea_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "AssembleaPresenza" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "assembleaId" INTEGER NOT NULL,
    "condominoId" INTEGER NOT NULL,
    "presente" BOOLEAN NOT NULL DEFAULT false,
    "delegatoId" INTEGER,
    CONSTRAINT "AssembleaPresenza_assembleaId_fkey" FOREIGN KEY ("assembleaId") REFERENCES "Assemblea" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "AssembleaPresenza_condominoId_fkey" FOREIGN KEY ("condominoId") REFERENCES "Condomino" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "AssembleaPuntoOdG" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "assembleaId" INTEGER NOT NULL,
    "titolo" TEXT NOT NULL,
    "descrizione" TEXT NOT NULL DEFAULT '',
    "ordine" INTEGER NOT NULL DEFAULT 0,
    "esito" TEXT,
    "votiSi" REAL NOT NULL DEFAULT 0,
    "votiNo" REAL NOT NULL DEFAULT 0,
    "votiAstenuti" REAL NOT NULL DEFAULT 0,
    CONSTRAINT "AssembleaPuntoOdG_assembleaId_fkey" FOREIGN KEY ("assembleaId") REFERENCES "Assemblea" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateIndex
CREATE UNIQUE INDEX "AssembleaPresenza_assembleaId_condominoId_key" ON "AssembleaPresenza"("assembleaId", "condominoId");

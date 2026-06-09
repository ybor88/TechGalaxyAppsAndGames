-- CreateTable
CREATE TABLE "Fornitore" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "nome" TEXT NOT NULL,
    "tipo" TEXT NOT NULL DEFAULT 'generico',
    "email" TEXT,
    "telefono" TEXT,
    "indirizzo" TEXT,
    "note" TEXT,
    "condominioId" INTEGER,
    CONSTRAINT "Fornitore_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Intervento" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "fornitoreId" INTEGER NOT NULL,
    "ticketId" INTEGER,
    "descrizione" TEXT NOT NULL,
    "data" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "costo" REAL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "Intervento_fornitoreId_fkey" FOREIGN KEY ("fornitoreId") REFERENCES "Fornitore" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "Intervento_ticketId_fkey" FOREIGN KEY ("ticketId") REFERENCES "Ticket" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "AssembleaVoto" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "puntoOdGId" INTEGER NOT NULL,
    "condominoId" INTEGER NOT NULL,
    "scelta" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "AssembleaVoto_puntoOdGId_fkey" FOREIGN KEY ("puntoOdGId") REFERENCES "AssembleaPuntoOdG" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "AssembleaVoto_condominoId_fkey" FOREIGN KEY ("condominoId") REFERENCES "Condomino" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_Ticket" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "titolo" TEXT NOT NULL,
    "descrizione" TEXT NOT NULL DEFAULT '',
    "categoria" TEXT NOT NULL,
    "priorita" TEXT NOT NULL DEFAULT 'media',
    "stato" TEXT NOT NULL DEFAULT 'Nuova',
    "foto" TEXT,
    "assegnatoa" TEXT,
    "fornitoreId" INTEGER,
    "dataApertura" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "dataChiusura" DATETIME,
    "condominioId" INTEGER NOT NULL,
    "apertoCondominoId" INTEGER,
    CONSTRAINT "Ticket_fornitoreId_fkey" FOREIGN KEY ("fornitoreId") REFERENCES "Fornitore" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "Ticket_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "Ticket_apertoCondominoId_fkey" FOREIGN KEY ("apertoCondominoId") REFERENCES "Condomino" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_Ticket" ("apertoCondominoId", "assegnatoa", "categoria", "condominioId", "dataApertura", "dataChiusura", "descrizione", "foto", "id", "priorita", "stato", "titolo") SELECT "apertoCondominoId", "assegnatoa", "categoria", "condominioId", "dataApertura", "dataChiusura", "descrizione", "foto", "id", "priorita", "stato", "titolo" FROM "Ticket";
DROP TABLE "Ticket";
ALTER TABLE "new_Ticket" RENAME TO "Ticket";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

-- CreateIndex
CREATE UNIQUE INDEX "AssembleaVoto_puntoOdGId_condominoId_key" ON "AssembleaVoto"("puntoOdGId", "condominoId");

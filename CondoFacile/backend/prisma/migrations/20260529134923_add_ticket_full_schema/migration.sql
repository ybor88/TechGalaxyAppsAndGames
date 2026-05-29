-- CreateTable
CREATE TABLE "TicketNota" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "testo" TEXT NOT NULL,
    "autore" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ticketId" INTEGER NOT NULL,
    CONSTRAINT "TicketNota_ticketId_fkey" FOREIGN KEY ("ticketId") REFERENCES "Ticket" ("id") ON DELETE CASCADE ON UPDATE CASCADE
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
    "dataApertura" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "dataChiusura" DATETIME,
    "condominioId" INTEGER NOT NULL,
    "apertoCondominoId" INTEGER,
    CONSTRAINT "Ticket_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "Ticket_apertoCondominoId_fkey" FOREIGN KEY ("apertoCondominoId") REFERENCES "Condomino" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_Ticket" ("categoria", "condominioId", "dataApertura", "id", "priorita", "stato", "titolo") SELECT "categoria", "condominioId", "dataApertura", "id", "priorita", "stato", "titolo" FROM "Ticket";
DROP TABLE "Ticket";
ALTER TABLE "new_Ticket" RENAME TO "Ticket";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

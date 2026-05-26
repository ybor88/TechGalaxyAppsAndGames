-- CreateTable
CREATE TABLE "Condominio" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "nome" TEXT NOT NULL,
    "indirizzo" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "Condomino" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "nome" TEXT NOT NULL,
    "cognome" TEXT NOT NULL,
    "email" TEXT,
    "telefono" TEXT,
    "unita" TEXT NOT NULL,
    "millesimi" REAL NOT NULL DEFAULT 0,
    "stato" TEXT NOT NULL DEFAULT 'attivo',
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Condomino_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "QuotaMensile" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "mese" INTEGER NOT NULL,
    "anno" INTEGER NOT NULL,
    "importoTotale" REAL NOT NULL,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "QuotaMensile_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "PagamentoQuota" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "importo" REAL NOT NULL,
    "dataPagamento" DATETIME,
    "stato" TEXT NOT NULL DEFAULT 'in_attesa',
    "condominoId" INTEGER NOT NULL,
    "quotaId" INTEGER NOT NULL,
    CONSTRAINT "PagamentoQuota_condominoId_fkey" FOREIGN KEY ("condominoId") REFERENCES "Condomino" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "PagamentoQuota_quotaId_fkey" FOREIGN KEY ("quotaId") REFERENCES "QuotaMensile" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Ticket" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "titolo" TEXT NOT NULL,
    "categoria" TEXT NOT NULL,
    "priorita" TEXT NOT NULL DEFAULT 'media',
    "stato" TEXT NOT NULL DEFAULT 'Nuova',
    "dataApertura" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Ticket_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Scadenza" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "descrizione" TEXT NOT NULL,
    "data" DATETIME NOT NULL,
    "tipo" TEXT NOT NULL,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Scadenza_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Comunicazione" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "titolo" TEXT NOT NULL,
    "tipo" TEXT NOT NULL,
    "data" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "destinatari" INTEGER NOT NULL DEFAULT 0,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Comunicazione_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Lavoro" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "descrizione" TEXT NOT NULL,
    "fornitore" TEXT NOT NULL,
    "dataInizio" DATETIME NOT NULL,
    "dataFine" DATETIME NOT NULL,
    "stato" TEXT NOT NULL DEFAULT 'In corso',
    "progressione" INTEGER NOT NULL DEFAULT 0,
    "condominioId" INTEGER NOT NULL,
    CONSTRAINT "Lavoro_condominioId_fkey" FOREIGN KEY ("condominioId") REFERENCES "Condominio" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

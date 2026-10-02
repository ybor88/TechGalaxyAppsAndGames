-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_User" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "username" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'CONDOMINO',
    "condominoId" INTEGER,
    "profilePhoto" TEXT,
    "stato" TEXT NOT NULL DEFAULT 'attivo',
    "nome" TEXT,
    "cognome" TEXT,
    "email" TEXT,
    "telefono" TEXT,
    "unitaRichiesta" TEXT,
    "resetPasswordRichiesto" BOOLEAN NOT NULL DEFAULT false,
    "resetPasswordRichiestoAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "User_condominoId_fkey" FOREIGN KEY ("condominoId") REFERENCES "Condomino" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_User" ("cognome", "condominoId", "createdAt", "email", "id", "nome", "passwordHash", "profilePhoto", "role", "stato", "telefono", "unitaRichiesta", "username") SELECT "cognome", "condominoId", "createdAt", "email", "id", "nome", "passwordHash", "profilePhoto", "role", "stato", "telefono", "unitaRichiesta", "username" FROM "User";
DROP TABLE "User";
ALTER TABLE "new_User" RENAME TO "User";
CREATE UNIQUE INDEX "User_username_key" ON "User"("username");
CREATE UNIQUE INDEX "User_condominoId_key" ON "User"("condominoId");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

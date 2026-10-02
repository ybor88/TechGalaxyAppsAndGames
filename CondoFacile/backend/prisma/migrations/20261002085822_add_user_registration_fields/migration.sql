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
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "User_condominoId_fkey" FOREIGN KEY ("condominoId") REFERENCES "Condomino" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_User" ("condominoId", "createdAt", "id", "passwordHash", "profilePhoto", "role", "username") SELECT "condominoId", "createdAt", "id", "passwordHash", "profilePhoto", "role", "username" FROM "User";
DROP TABLE "User";
ALTER TABLE "new_User" RENAME TO "User";
CREATE UNIQUE INDEX "User_username_key" ON "User"("username");
CREATE UNIQUE INDEX "User_condominoId_key" ON "User"("condominoId");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

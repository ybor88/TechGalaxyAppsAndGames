const { PrismaClient } = require('@prisma/client');
const bcrypt = require('bcryptjs');

const prisma = new PrismaClient();

async function main() {
  const a = await prisma.user.findUnique({ where: { username: 'admin' } });
  if (!a) {
    await prisma.user.create({
      data: {
        username: 'admin',
        passwordHash: await bcrypt.hash('admin123', 10),
        role: 'AMMINISTRATORE',
      },
    });
    console.log('admin creato');
  } else {
    console.log('admin esiste');
  }

  // Nessun condòmino demo qui: questo script gira a ogni avvio (start.bat) e ricreerebbe
  // account eliminati dall'amministratore. I condòmini si registrano da /registrati.
}

main().finally(() => prisma.$disconnect());

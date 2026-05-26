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

  const b = await prisma.user.findUnique({ where: { username: 'mario.rossi' } });
  if (!b) {
    await prisma.user.create({
      data: {
        username: 'mario.rossi',
        passwordHash: await bcrypt.hash('condo123', 10),
        role: 'CONDOMINO',
      },
    });
    console.log('mario.rossi creato');
  } else {
    console.log('mario.rossi esiste');
  }
}

main().finally(() => prisma.$disconnect());

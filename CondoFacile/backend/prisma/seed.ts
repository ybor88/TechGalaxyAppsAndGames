import { PrismaClient } from '@prisma/client';
import * as bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  const adminExists = await prisma.user.findUnique({ where: { username: 'admin' } });
  if (!adminExists) {
    await prisma.user.create({
      data: {
        username: 'admin',
        passwordHash: await bcrypt.hash('admin123', 10),
        role: 'AMMINISTRATORE',
      },
    });
    console.log('✅ Utente admin creato');
  } else {
    console.log('ℹ️  Utente admin già esistente');
  }

  const demoExists = await prisma.user.findUnique({ where: { username: 'mario.rossi' } });
  if (!demoExists) {
    await prisma.user.create({
      data: {
        username: 'mario.rossi',
        passwordHash: await bcrypt.hash('condo123', 10),
        role: 'CONDOMINO',
      },
    });
    console.log('✅ Utente condòmino demo creato');
  } else {
    console.log('ℹ️  Utente mario.rossi già esistente');
  }
}

main()
  .catch((e) => { console.error(e); process.exit(1); })
  .finally(() => prisma.$disconnect());

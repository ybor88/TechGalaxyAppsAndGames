import { Module } from '@nestjs/common';
import { AssembleaController } from './assemblea.controller';
import { AssembleaService } from './assemblea.service';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [PrismaModule, AuthModule],
  controllers: [AssembleaController],
  providers: [AssembleaService],
})
export class AssembleaModule {}

import { Module } from '@nestjs/common';
import { CondominioController } from './condominio.controller';
import { CondominioService } from './condominio.service';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [PrismaModule, AuthModule],
  controllers: [CondominioController],
  providers: [CondominioService],
})
export class CondominioModule {}

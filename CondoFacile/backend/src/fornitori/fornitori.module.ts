import { Module } from '@nestjs/common';
import { FornitoriService } from './fornitori.service';
import { FornitoriController } from './fornitori.controller';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [PrismaModule, AuthModule],
  providers: [FornitoriService],
  controllers: [FornitoriController],
})
export class FornitoriModule {}

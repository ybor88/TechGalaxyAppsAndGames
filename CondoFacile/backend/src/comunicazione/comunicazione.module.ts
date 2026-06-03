import { Module } from '@nestjs/common';
import { ComunicazioneController } from './comunicazione.controller';
import { ComunicazioneService } from './comunicazione.service';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [PrismaModule, AuthModule],
  controllers: [ComunicazioneController],
  providers: [ComunicazioneService],
})
export class ComunicazioneModule {}

import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { PrismaModule } from './prisma/prisma.module';
import { DashboardModule } from './dashboard/dashboard.module';
import { AuthModule } from './auth/auth.module';
import { CondominioModule } from './condominio/condominio.module';
import { QuoteModule } from './quote/quote.module';
import { TicketModule } from './ticket/ticket.module';
import { ComunicazioneModule } from './comunicazione/comunicazione.module';
import { AssembleaModule } from './assemblea/assemblea.module';
import { DocumentoModule } from './documento/documento.module';
import { FornitoriModule } from './fornitori/fornitori.module';

@Module({
  imports: [PrismaModule, DashboardModule, AuthModule, CondominioModule, QuoteModule, TicketModule, ComunicazioneModule, AssembleaModule, DocumentoModule, FornitoriModule],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}

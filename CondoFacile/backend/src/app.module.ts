import { Module } from '@nestjs/common';
import { APP_FILTER } from '@nestjs/core';
import { PrismaExceptionFilter } from './common/prisma-exception.filter';
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
import { MailModule } from './mail/mail.module';

@Module({
  imports: [PrismaModule, MailModule, DashboardModule, AuthModule, CondominioModule, QuoteModule, TicketModule, ComunicazioneModule, AssembleaModule, DocumentoModule, FornitoriModule],
  controllers: [AppController],
  providers: [AppService, { provide: APP_FILTER, useClass: PrismaExceptionFilter }],
})
export class AppModule {}

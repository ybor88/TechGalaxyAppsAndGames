import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { PrismaModule } from './prisma/prisma.module';
import { DashboardModule } from './dashboard/dashboard.module';
import { AuthModule } from './auth/auth.module';
import { CondominioModule } from './condominio/condominio.module';
import { QuoteModule } from './quote/quote.module';
import { TicketModule } from './ticket/ticket.module';

@Module({
  imports: [PrismaModule, DashboardModule, AuthModule, CondominioModule, QuoteModule, TicketModule],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}

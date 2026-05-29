import { Module } from '@nestjs/common';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';
import { TicketController } from './ticket.controller';
import { TicketService } from './ticket.service';

@Module({
  imports: [PrismaModule, AuthModule],
  controllers: [TicketController],
  providers: [TicketService],
})
export class TicketModule {}

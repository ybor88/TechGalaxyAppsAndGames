import { Module } from '@nestjs/common';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';
import { QuoteController } from './quote.controller';
import { MieQuoteController } from './mie-quote.controller';
import { QuoteService } from './quote.service';

@Module({
  imports: [PrismaModule, AuthModule],
  controllers: [QuoteController, MieQuoteController],
  providers: [QuoteService],
})
export class QuoteModule {}

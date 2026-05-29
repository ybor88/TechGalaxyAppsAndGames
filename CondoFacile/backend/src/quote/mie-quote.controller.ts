import {
  Controller,
  ForbiddenException,
  Get,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { QuoteService } from './quote.service';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

@Controller('mie-quote')
@UseGuards(JwtAuthGuard)
export class MieQuoteController {
  constructor(private service: QuoteService) {}

  @Get()
  getMieQuote(@Req() req: Request) {
    const user = (req as Request & { user: JwtPayload }).user;
    if (!user.condominoId) {
      throw new ForbiddenException('Endpoint riservato ai condòmini');
    }
    return this.service.findMiePagamenti(user.condominoId);
  }
}

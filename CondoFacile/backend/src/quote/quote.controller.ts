import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseIntPipe,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AdminGuard } from '../auth/admin.guard';
import { QuoteService } from './quote.service';

@Controller()
@UseGuards(JwtAuthGuard, AdminGuard)
export class QuoteController {
  constructor(private service: QuoteService) {}

  // ── Quote mensili ─────────────────────────────────────────────────────────

  @Get('quote')
  findAllQuote(@Query('condominioId', ParseIntPipe) condominioId: number) {
    return this.service.findAllQuote(condominioId);
  }

  @Post('quote')
  createQuota(
    @Body() body: { condominioId: number; mese: number; anno: number; importoTotale: number },
  ) {
    if (!body?.condominioId || !body?.mese || !body?.anno || !body?.importoTotale) {
      throw new BadRequestException('condominioId, mese, anno e importoTotale sono obbligatori');
    }
    return this.service.createQuota(body.condominioId, body.mese, body.anno, body.importoTotale);
  }

  @Delete('quote/:id')
  deleteQuota(@Param('id', ParseIntPipe) id: number) {
    return this.service.deleteQuota(id);
  }

  @Post('quote/:id/genera')
  generaPagamenti(@Param('id', ParseIntPipe) id: number) {
    return this.service.generaPagamenti(id);
  }

  // ── Pagamenti ────────────────────────────────────────────────────────────

  @Get('quote/:id/pagamenti')
  findPagamenti(@Param('id', ParseIntPipe) id: number) {
    return this.service.findPagamenti(id);
  }

  @Patch('pagamenti/:id')
  updatePagamento(
    @Param('id', ParseIntPipe) id: number,
    @Body()
    body: {
      stato?: string;
      importo?: number;
      dataPagamento?: string | null;
      metodoPagamento?: string | null;
      note?: string | null;
    },
  ) {
    return this.service.updatePagamento(id, body);
  }

  // ── Morosità & Bilancio ───────────────────────────────────────────────────

  @Get('morosita')
  getMorosita(@Query('condominioId', ParseIntPipe) condominioId: number) {
    return this.service.getMorosita(condominioId);
  }

  @Get('bilancio')
  getBilancio(@Query('condominioId', ParseIntPipe) condominioId: number) {
    return this.service.getBilancio(condominioId);
  }
}

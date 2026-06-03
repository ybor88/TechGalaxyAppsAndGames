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
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AdminGuard } from '../auth/admin.guard';
import { ComunicazioneService } from './comunicazione.service';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

type AuthRequest = Request & { user: JwtPayload };

@Controller('comunicazioni')
@UseGuards(JwtAuthGuard)
export class ComunicazioneController {
  constructor(private service: ComunicazioneService) {}

  // ── Admin: lista comunicazioni ─────────────────────────────────────────────

  @Get()
  @UseGuards(AdminGuard)
  findAll(
    @Query('condominioId', ParseIntPipe) condominioId: number,
    @Query('tipo') tipo?: string,
  ) {
    return this.service.findAll(condominioId, { tipo });
  }

  // ── Condomino: proprie comunicazioni ──────────────────────────────────────

  @Get('mie')
  findMie(@Req() req: AuthRequest) {
    const { condominoId } = req.user;
    if (!condominoId) throw new BadRequestException('Riservato ai condòmini');
    return this.service.findMie(condominoId);
  }

  // ── Admin: crea comunicazione ─────────────────────────────────────────────

  @Post()
  @UseGuards(AdminGuard)
  create(
    @Body()
    body: {
      titolo: string;
      corpo: string;
      tipo: string;
      destinatariTipo?: string;
      condominioId: number;
    },
  ) {
    if (!body?.titolo || !body?.tipo || !body?.condominioId) {
      throw new BadRequestException('titolo, tipo e condominioId obbligatori');
    }
    return this.service.create(body);
  }

  // ── Admin: aggiorna ───────────────────────────────────────────────────────

  @Patch(':id')
  @UseGuards(AdminGuard)
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { titolo?: string; corpo?: string; tipo?: string; destinatariTipo?: string },
  ) {
    return this.service.update(id, body);
  }

  // ── Admin: elimina ────────────────────────────────────────────────────────

  @Delete(':id')
  @UseGuards(AdminGuard)
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.service.remove(id);
  }

  // ── Condomino: presa visione ──────────────────────────────────────────────

  @Post(':id/presa-visione')
  presoVisione(
    @Param('id', ParseIntPipe) id: number,
    @Req() req: AuthRequest,
  ) {
    const { condominoId } = req.user;
    if (!condominoId) throw new BadRequestException('Riservato ai condòmini');
    return this.service.presoVisione(id, condominoId);
  }

  // ── Admin: chi ha letto ───────────────────────────────────────────────────

  @Get(':id/letture')
  @UseGuards(AdminGuard)
  getLetture(@Param('id', ParseIntPipe) id: number) {
    return this.service.getLetture(id);
  }
}

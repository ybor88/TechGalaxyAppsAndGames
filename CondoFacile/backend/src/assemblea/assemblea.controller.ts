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
  Put,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AdminGuard } from '../auth/admin.guard';
import { AssembleaService } from './assemblea.service';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

type AuthRequest = Request & { user: JwtPayload };

@Controller('assemblee')
@UseGuards(JwtAuthGuard)
export class AssembleaController {
  constructor(private service: AssembleaService) {}

  // ── Admin: lista assemblee ─────────────────────────────────────────────────

  @Get()
  @UseGuards(AdminGuard)
  findAll(
    @Query('condominioId', ParseIntPipe) condominioId: number,
    @Query('stato') stato?: string,
    @Query('tipo') tipo?: string,
  ) {
    return this.service.findAll(condominioId, { stato, tipo });
  }

  // ── Condomino: proprie assemblee ───────────────────────────────────────────

  @Get('mie')
  findMie(@Req() req: AuthRequest) {
    const { condominoId } = req.user;
    if (!condominoId) throw new BadRequestException('Riservato ai condòmini');
    return this.service.findMie(condominoId);
  }

  // ── Dettaglio assemblea ────────────────────────────────────────────────────

  @Get(':id')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.service.findOne(id);
  }

  // ── Admin: crea assemblea ─────────────────────────────────────────────────

  @Post()
  @UseGuards(AdminGuard)
  create(
    @Body()
    body: {
      titolo: string;
      data: string;
      luogo: string;
      tipo: string;
      ordineDelGiorno?: string;
      condominioId: number;
    },
  ) {
    if (!body?.titolo || !body?.data || !body?.condominioId) {
      throw new BadRequestException('titolo, data e condominioId obbligatori');
    }
    return this.service.create({ ...body, data: new Date(body.data) });
  }

  // ── Admin: aggiorna assemblea ─────────────────────────────────────────────

  @Patch(':id')
  @UseGuards(AdminGuard)
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body()
    body: {
      titolo?: string;
      data?: string;
      luogo?: string;
      tipo?: string;
      stato?: string;
      ordineDelGiorno?: string;
      verbale?: string;
    },
  ) {
    const data = { ...body, data: body.data ? new Date(body.data) : undefined };
    return this.service.update(id, data);
  }

  // ── Admin: elimina assemblea ──────────────────────────────────────────────

  @Delete(':id')
  @UseGuards(AdminGuard)
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.service.remove(id);
  }

  // ── Admin: gestione presenze ───────────────────────────────────────────────

  @Put(':id/presenze/:condominoId')
  @UseGuards(AdminGuard)
  upsertPresenza(
    @Param('id', ParseIntPipe) assembleaId: number,
    @Param('condominoId', ParseIntPipe) condominoId: number,
    @Body() body: { presente: boolean; delegatoId?: number | null },
  ) {
    return this.service.upsertPresenza(assembleaId, condominoId, body);
  }

  // ── Admin: calcola quorum ─────────────────────────────────────────────────

  @Get(':id/quorum')
  @UseGuards(AdminGuard)
  calcolaQuorum(@Param('id', ParseIntPipe) id: number) {
    return this.service.calcolaQuorum(id);
  }

  // ── Admin: aggiungi punto OdG ─────────────────────────────────────────────

  @Post(':id/punti-odg')
  @UseGuards(AdminGuard)
  createPuntoOdG(
    @Param('id', ParseIntPipe) assembleaId: number,
    @Body() body: { titolo: string; descrizione?: string; ordine?: number },
  ) {
    if (!body?.titolo) throw new BadRequestException('titolo obbligatorio');
    return this.service.createPuntoOdG(assembleaId, body);
  }

  // ── Admin: aggiorna punto OdG ─────────────────────────────────────────────

  @Patch('punti-odg/:id')
  @UseGuards(AdminGuard)
  updatePuntoOdG(
    @Param('id', ParseIntPipe) id: number,
    @Body()
    body: {
      titolo?: string;
      descrizione?: string;
      ordine?: number;
      esito?: string | null;
      votiSi?: number;
      votiNo?: number;
      votiAstenuti?: number;
    },
  ) {
    return this.service.updatePuntoOdG(id, body);
  }

  // ── Admin: elimina punto OdG ──────────────────────────────────────────────

  @Delete('punti-odg/:id')
  @UseGuards(AdminGuard)
  deletePuntoOdG(@Param('id', ParseIntPipe) id: number) {
    return this.service.deletePuntoOdG(id);
  }

  // ── Condomino: invia delega ───────────────────────────────────────────────

  @Post(':id/delega')
  inviaDelega(
    @Param('id', ParseIntPipe) assembleaId: number,
    @Body() body: { delegatoId: number },
    @Req() req: AuthRequest,
  ) {
    const { condominoId } = req.user;
    if (!condominoId) throw new BadRequestException('Riservato ai condòmini');
    if (!body?.delegatoId) throw new BadRequestException('delegatoId obbligatorio');
    return this.service.inviaDelega(assembleaId, condominoId, body.delegatoId);
  }

  // ── Condomino: vota un punto OdG ───────────────────────────────────────────

  @Post('punti-odg/:id/vota')
  votaPunto(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { scelta: 'si' | 'no' | 'astenuto' },
    @Req() req: AuthRequest,
  ) {
    const { condominoId } = req.user;
    if (!condominoId) throw new BadRequestException('Riservato ai condòmini');
    if (!body || !['si', 'no', 'astenuto'].includes(body.scelta)) {
      throw new BadRequestException('scelta non valida');
    }
    return this.service.votaPunto(id, condominoId, body.scelta);
  }
}

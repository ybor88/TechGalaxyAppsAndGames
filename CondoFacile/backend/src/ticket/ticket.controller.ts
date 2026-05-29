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
import { TicketService } from './ticket.service';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

type AuthRequest = Request & { user: JwtPayload };

@Controller('ticket')
@UseGuards(JwtAuthGuard)
export class TicketController {
  constructor(private service: TicketService) {}

  // ── Admin: lista ticket del condominio ────────────────────────────────────

  @Get()
  @UseGuards(AdminGuard)
  findAll(
    @Query('condominioId', ParseIntPipe) condominioId: number,
    @Query('stato') stato?: string,
    @Query('priorita') priorita?: string,
    @Query('categoria') categoria?: string,
  ) {
    return this.service.findAll(condominioId, { stato, priorita, categoria });
  }

  // ── Condomino: propri ticket (deve venire PRIMA di :id) ──────────────────

  @Get('miei')
  findMiei(@Req() req: AuthRequest) {
    const { condominoId } = req.user;
    if (!condominoId) {
      throw new BadRequestException('Endpoint riservato ai condòmini');
    }
    return this.service.findMiei(condominoId);
  }

  // ── Entrambi: dettaglio ticket ────────────────────────────────────────────

  @Get(':id')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.service.findOne(id);
  }

  // ── Entrambi: crea ticket ─────────────────────────────────────────────────

  @Post()
  create(
    @Req() req: AuthRequest,
    @Body()
    body: {
      titolo: string;
      descrizione?: string;
      categoria: string;
      priorita?: string;
      foto?: string | null;
      condominioId?: number;
    },
  ) {
    if (!body?.titolo || !body?.categoria) {
      throw new BadRequestException('titolo e categoria obbligatori');
    }
    return this.service.create({
      ...body,
      apertoCondominoId: req.user.condominoId ?? null,
    });
  }

  // ── Admin: aggiorna ticket ────────────────────────────────────────────────

  @Patch(':id')
  @UseGuards(AdminGuard)
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { stato?: string; priorita?: string; assegnatoa?: string | null },
  ) {
    return this.service.update(id, body);
  }

  // ── Admin: elimina ticket ─────────────────────────────────────────────────

  @Delete(':id')
  @UseGuards(AdminGuard)
  delete(@Param('id', ParseIntPipe) id: number) {
    return this.service.delete(id);
  }

  // ── Entrambi: note di un ticket ──────────────────────────────────────────

  @Get(':id/note')
  findNote(@Param('id', ParseIntPipe) id: number, @Req() req: AuthRequest) {
    return this.service.findNote(id, req.user.condominoId);
  }

  // ── Admin: aggiungi nota interna ─────────────────────────────────────────

  @Post(':id/note')
  @UseGuards(AdminGuard)
  addNota(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { testo: string },
    @Req() req: AuthRequest,
  ) {
    if (!body?.testo) throw new BadRequestException('Testo nota obbligatorio');
    return this.service.addNota(id, body.testo, req.user.username);
  }
}

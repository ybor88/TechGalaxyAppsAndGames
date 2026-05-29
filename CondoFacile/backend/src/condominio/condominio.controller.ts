import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  ParseIntPipe,
  Post,
  Put,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AdminGuard } from '../auth/admin.guard';
import { CondominioService } from './condominio.service';

@Controller('condomini')
@UseGuards(JwtAuthGuard, AdminGuard)
export class CondominioController {
  constructor(private service: CondominioService) {}

  @Get()
  findAll() {
    return this.service.findAll();
  }

  @Post()
  create(@Body() body: { nome: string; indirizzo: string }) {
    if (!body?.nome || !body?.indirizzo) {
      throw new BadRequestException('Nome e indirizzo obbligatori');
    }
    return this.service.create(body.nome, body.indirizzo);
  }

  @Put(':id')
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { nome: string; indirizzo: string },
  ) {
    if (!body?.nome || !body?.indirizzo) {
      throw new BadRequestException('Nome e indirizzo obbligatori');
    }
    return this.service.update(id, body.nome, body.indirizzo);
  }

  @Get(':id')
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.service.findOne(id);
  }

  @Post(':id/condomini')
  addCondomino(
    @Param('id', ParseIntPipe) id: number,
    @Body()
    body: {
      nome: string;
      cognome: string;
      email?: string;
      telefono?: string;
      unita: string;
      millesimi?: number;
      tipo?: string;
      username?: string;
      password?: string;
    },
  ) {
    if (!body?.nome || !body?.cognome || !body?.unita) {
      throw new BadRequestException('Nome, cognome e unità obbligatori');
    }
    return this.service.addCondomino(id, body);
  }

  @Put(':condominioId/condomini/:condominoId')
  updateCondomino(
    @Param('condominioId', ParseIntPipe) condominioId: number,
    @Param('condominoId', ParseIntPipe) condominoId: number,
    @Body()
    body: {
      nome?: string;
      cognome?: string;
      email?: string;
      telefono?: string;
      unita?: string;
      millesimi?: number;
      tipo?: string;
    },
  ) {
    return this.service.updateCondomino(condominioId, condominoId, body);
  }

  @Patch(':condominioId/condomini/:condominoId/toggle-stato')
  toggleStato(
    @Param('condominioId', ParseIntPipe) condominioId: number,
    @Param('condominoId', ParseIntPipe) condominoId: number,
  ) {
    return this.service.deactivateCondomino(condominioId, condominoId);
  }
}

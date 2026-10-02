import {
  BadRequestException,
  Body,
  Controller,
  Delete,
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

  @Get('users/non-associati')
  getUsersNonAssociati() {
    return this.service.getUsersNonAssociati();
  }

  @Get('users/richieste-reset-password')
  getRichiesteResetPassword() {
    return this.service.getRichiesteResetPassword();
  }

  @Post('users/:userId/approva-registrazione')
  approvaRegistrazione(
    @Param('userId', ParseIntPipe) userId: number,
    @Body() body: { condominioId: number; unita: string; millesimi?: number; tipo?: string },
  ) {
    const condominioId = Number(body?.condominioId);
    const unita = body?.unita?.trim();
    if (!condominioId || !unita) {
      throw new BadRequestException('Condominio e unità sono obbligatori');
    }
    return this.service.approvaRegistrazione(userId, {
      condominioId,
      unita,
      millesimi: body.millesimi !== undefined ? Number(body.millesimi) : undefined,
      tipo: body.tipo,
    });
  }

  @Delete('users/:userId/registrazione')
  rifiutaRegistrazione(@Param('userId', ParseIntPipe) userId: number) {
    return this.service.rifiutaRegistrazione(userId);
  }

  @Post('users/:userId/approva-reset-password')
  approvaResetPassword(@Param('userId', ParseIntPipe) userId: number) {
    return this.service.approvaResetPassword(userId);
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

  @Patch(':condominioId/condomini/:condominoId/reset-password')
  resetPassword(
    @Param('condominioId', ParseIntPipe) condominioId: number,
    @Param('condominoId', ParseIntPipe) condominoId: number,
    @Body() body: { newPassword: string },
  ) {
    if (!body?.newPassword || body.newPassword.length < 6) {
      throw new BadRequestException('La nuova password deve avere almeno 6 caratteri');
    }
    return this.service.resetPasswordCondomino(condominioId, condominoId, body.newPassword);
  }
}

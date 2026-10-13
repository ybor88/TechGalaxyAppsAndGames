import { BadRequestException, Body, Controller, Delete, Get, Param, ParseIntPipe, Post, Put, Query, UseGuards } from '@nestjs/common';
import { dataValida, soloCampi } from '../common/validazione';

const CAMPI_FORNITORE = ['nome', 'tipo', 'email', 'telefono', 'indirizzo', 'note', 'condominioId'] as const;
import { AdminGuard } from '../auth/admin.guard';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { FornitoriService } from './fornitori.service';

@Controller('fornitori')
@UseGuards(JwtAuthGuard)
export class FornitoriController {
  constructor(private service: FornitoriService) {}

  @Get()
  @UseGuards(AdminGuard)
  findAll(@Query('condominioId') condominioId?: string) {
    if (condominioId) {
      const parsed = Number(condominioId);
      if (Number.isNaN(parsed)) throw new BadRequestException('condominioId must be numeric');
      return this.service.findAll(parsed as any);
    }
    return this.service.findAll(undefined as any);
  }

  @Get('analytics')
  @UseGuards(AdminGuard)
  analytics(@Query('condominioId') condominioId?: string) {
    if (condominioId) {
      const parsed = Number(condominioId);
      if (Number.isNaN(parsed)) throw new BadRequestException('condominioId must be numeric');
      return this.service.analytics(parsed as any);
    }
    return this.service.analytics(undefined as any);
  }

  @Get(':id')
  @UseGuards(AdminGuard)
  findOne(@Param('id', ParseIntPipe) id: number) {
    return this.service.findOne(id);
  }

  @Post()
  @UseGuards(AdminGuard)
  create(@Body() body: { nome: string; tipo?: string; email?: string; telefono?: string; indirizzo?: string; note?: string; condominioId?: number }) {
    if (!body?.nome) throw new BadRequestException('nome obbligatorio');
    return this.service.create(soloCampi(body, CAMPI_FORNITORE) as { nome: string });
  }

  @Put(':id')
  @UseGuards(AdminGuard)
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { nome?: string; tipo?: string; email?: string; telefono?: string; indirizzo?: string; note?: string; condominioId?: number },
  ) {
    if (body?.nome !== undefined && !String(body.nome).trim()) throw new BadRequestException('nome obbligatorio');
    return this.service.update(id, soloCampi(body, CAMPI_FORNITORE));
  }

  @Delete(':id')
  @UseGuards(AdminGuard)
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.service.remove(id);
  }

  @Get(':id/interventi')
  @UseGuards(AdminGuard)
  findInterventi(@Param('id', ParseIntPipe) id: number) {
    return this.service.findInterventi(id);
  }

  @Post(':id/interventi')
  @UseGuards(AdminGuard)
  addIntervento(@Param('id', ParseIntPipe) id: number, @Body() body: { descrizione: string; ticketId?: number; data?: string; costo?: number }) {
    if (!body?.descrizione) throw new BadRequestException('descrizione obbligatoria');
    if (body.costo !== undefined && body.costo !== null && (typeof body.costo !== 'number' || body.costo < 0)) {
      throw new BadRequestException('Il costo deve essere un numero non negativo');
    }
    return this.service.addIntervento(id, { descrizione: body.descrizione, ticketId: body.ticketId, data: dataValida('data', body.data), costo: body.costo });
  }
}

import { BadRequestException, Body, Controller, Delete, Get, Param, ParseIntPipe, Post, Put, Query, UseGuards } from '@nestjs/common';
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
    return this.service.create(body);
  }

  @Put(':id')
  @UseGuards(AdminGuard)
  update(@Param('id', ParseIntPipe) id: number, @Body() body: any) {
    return this.service.update(id, body);
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
    return this.service.addIntervento(id, { descrizione: body.descrizione, ticketId: body.ticketId, data: body.data ? new Date(body.data) : undefined, costo: body.costo });
  }
}

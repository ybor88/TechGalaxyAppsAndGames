import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class FornitoriService {
  constructor(private prisma: PrismaService) {}

  async findAll(condominioId?: number) {
    const where = condominioId ? { condominioId } : undefined;
    return this.prisma.fornitore.findMany({ where, orderBy: { nome: 'asc' } });
  }

  async findOne(id: number) {
    const f = await this.prisma.fornitore.findUnique({ where: { id } });
    if (!f) throw new NotFoundException('Fornitore non trovato');
    return f;
  }

  async create(data: { nome: string; tipo?: string; email?: string; telefono?: string; indirizzo?: string; note?: string; condominioId?: number }) {
    return this.prisma.fornitore.create({ data });
  }

  async update(id: number, data: Partial<{ nome: string; tipo: string; email: string; telefono: string; indirizzo: string; note: string; condominioId?: number }>) {
    await this.findOne(id);
    return this.prisma.fornitore.update({ where: { id }, data });
  }

  async remove(id: number) {
    await this.findOne(id);
    return this.prisma.fornitore.delete({ where: { id } });
  }

  // Interventi
  async findInterventi(fornitoreId: number) {
    await this.findOne(fornitoreId);
    return this.prisma.intervento.findMany({ where: { fornitoreId }, orderBy: { data: 'desc' } });
  }

  async addIntervento(fornitoreId: number, data: { descrizione: string; ticketId?: number; data?: Date; costo?: number }) {
    await this.findOne(fornitoreId);
    return this.prisma.intervento.create({ data: { fornitoreId, descrizione: data.descrizione, ticketId: data.ticketId ?? undefined, data: data.data ?? undefined, costo: data.costo ?? undefined } });
  }

  async analytics(condominioId?: number) {
    const whereF = condominioId ? { condominioId } : undefined;
    const whereIntervento = condominioId ? { fornitore: { condominioId } } : undefined;

    const totalFornitori = await this.prisma.fornitore.count({ where: whereF });
    const totalInterventi = await this.prisma.intervento.count({ where: whereIntervento });
    const sumObj = await this.prisma.intervento.aggregate({ _sum: { costo: true }, where: whereIntervento });
    const totalCosto = sumObj._sum.costo ?? 0;

    const byF = await this.prisma.intervento.groupBy({
      by: ['fornitoreId'],
      where: whereIntervento,
      _count: { fornitoreId: true },
      _sum: { costo: true },
      orderBy: { _count: { fornitoreId: 'desc' } },
      take: 5,
    });

    const topFornitori = await Promise.all(byF.map(async (b) => {
      const f = await this.prisma.fornitore.findUnique({ where: { id: b.fornitoreId } });
      return {
        id: b.fornitoreId,
        nome: f?.nome ?? 'Sconosciuto',
        interventi: b._count?.fornitoreId ?? 0,
        costo: b._sum?.costo ?? 0,
      };
    }));

    return { totalFornitori, totalInterventi, totalCosto, topFornitori };
  }
}

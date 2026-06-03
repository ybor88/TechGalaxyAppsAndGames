import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

const INCLUDE_COM = {
  _count: { select: { letture: true } },
  letture: {
    select: {
      condomino: { select: { nome: true, cognome: true, unita: true } },
      dataLettura: true,
    },
  },
};

@Injectable()
export class ComunicazioneService {
  constructor(private prisma: PrismaService) {}

  // ── Admin: tutte le comunicazioni del condominio ───────────────────────────

  async findAll(condominioId: number, filters?: { tipo?: string }) {
    const where: Record<string, unknown> = { condominioId };
    if (filters?.tipo && filters.tipo !== 'tutti') where['tipo'] = filters.tipo;

    return this.prisma.comunicazione.findMany({
      where,
      include: INCLUDE_COM,
      orderBy: { data: 'desc' },
    });
  }

  // ── Condomino: comunicazioni visibili per lui ──────────────────────────────

  async findMie(condominoId: number) {
    const condomino = await this.prisma.condomino.findUnique({
      where: { id: condominoId },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');

    const comunicazioni = await this.prisma.comunicazione.findMany({
      where: {
        condominioId: condomino.condominioId,
        destinatariTipo: 'tutti',
      },
      include: {
        _count: { select: { letture: true } },
        letture: {
          where: { condominoId },
          select: { dataLettura: true },
        },
      },
      orderBy: { data: 'desc' },
    });

    return comunicazioni.map((c) => ({
      ...c,
      presoVisione: c.letture.length > 0,
      dataLettura: c.letture[0]?.dataLettura ?? null,
    }));
  }

  // ── Crea comunicazione (admin) ─────────────────────────────────────────────

  async create(data: {
    titolo: string;
    corpo: string;
    tipo: string;
    destinatariTipo?: string;
    condominioId: number;
  }) {
    // Conta condòmini attivi per la metrica `destinatari`
    const count = await this.prisma.condomino.count({
      where: { condominioId: data.condominioId, stato: 'attivo' },
    });

    return this.prisma.comunicazione.create({
      data: {
        titolo: data.titolo,
        corpo: data.corpo,
        tipo: data.tipo,
        destinatariTipo: data.destinatariTipo ?? 'tutti',
        destinatari: count,
        condominioId: data.condominioId,
      },
      include: INCLUDE_COM,
    });
  }

  // ── Aggiorna comunicazione (admin) ────────────────────────────────────────

  async update(
    id: number,
    data: { titolo?: string; corpo?: string; tipo?: string; destinatariTipo?: string },
  ) {
    await this.findOrFail(id);
    return this.prisma.comunicazione.update({
      where: { id },
      data,
      include: INCLUDE_COM,
    });
  }

  // ── Elimina comunicazione (admin) ─────────────────────────────────────────

  async remove(id: number) {
    await this.findOrFail(id);
    return this.prisma.comunicazione.delete({ where: { id } });
  }

  // ── Condomino: segna presa visione ────────────────────────────────────────

  async presoVisione(comunicazioneId: number, condominoId: number) {
    const com = await this.findOrFail(comunicazioneId);

    // Verifica che il condomino appartenga allo stesso condominio
    const condomino = await this.prisma.condomino.findUnique({
      where: { id: condominoId },
    });
    if (!condomino || condomino.condominioId !== com.condominioId) {
      throw new ForbiddenException('Non autorizzato');
    }

    return this.prisma.comunicazioneLettura.upsert({
      where: {
        comunicazioneId_condominoId: { comunicazioneId, condominoId },
      },
      create: { comunicazioneId, condominoId },
      update: {},
    });
  }

  // ── Admin: chi ha letto ────────────────────────────────────────────────────

  async getLetture(id: number) {
    await this.findOrFail(id);
    return this.prisma.comunicazioneLettura.findMany({
      where: { comunicazioneId: id },
      include: {
        condomino: { select: { nome: true, cognome: true, unita: true } },
      },
      orderBy: { dataLettura: 'asc' },
    });
  }

  // ── Utility ───────────────────────────────────────────────────────────────

  private async findOrFail(id: number) {
    const c = await this.prisma.comunicazione.findUnique({ where: { id } });
    if (!c) throw new NotFoundException('Comunicazione non trovata');
    return c;
  }
}

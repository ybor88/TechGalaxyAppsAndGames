import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

const INCLUDE_ASSEMBLEA = {
  presenze: {
    include: {
      condomino: { select: { id: true, nome: true, cognome: true, unita: true, millesimi: true } },
    },
  },
  puntiOdG: { orderBy: { ordine: 'asc' as const } },
  _count: { select: { presenze: true } },
};

@Injectable()
export class AssembleaService {
  constructor(private prisma: PrismaService) {}

  // ── Admin: tutte le assemblee di un condominio ─────────────────────────────

  async findAll(condominioId: number, filters?: { stato?: string; tipo?: string }) {
    const where: Record<string, unknown> = { condominioId };
    if (filters?.stato && filters.stato !== 'tutti') where['stato'] = filters.stato;
    if (filters?.tipo && filters.tipo !== 'tutti') where['tipo'] = filters.tipo;

    return this.prisma.assemblea.findMany({
      where,
      include: INCLUDE_ASSEMBLEA,
      orderBy: { data: 'desc' },
    });
  }

  // ── Condomino: assemblee del suo condominio ────────────────────────────────

  async findMie(condominoId: number) {
    const condomino = await this.prisma.condomino.findUnique({
      where: { id: condominoId },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');

    const assemblee = await this.prisma.assemblea.findMany({
      where: { condominioId: condomino.condominioId },
      include: {
        puntiOdG: { orderBy: { ordine: 'asc' } },
        presenze: {
          where: { condominoId },
          select: { presente: true, delegatoId: true },
        },
        _count: { select: { presenze: true } },
      },
      orderBy: { data: 'desc' },
    });

    return assemblee.map((a) => ({
      ...a,
      miaPresenza: a.presenze[0] ?? null,
    }));
  }

  // ── Dettaglio assemblea ────────────────────────────────────────────────────

  async findOne(id: number) {
    const assemblea = await this.prisma.assemblea.findUnique({
      where: { id },
      include: INCLUDE_ASSEMBLEA,
    });
    if (!assemblea) throw new NotFoundException('Assemblea non trovata');
    return assemblea;
  }

  // ── Crea assemblea (admin) ─────────────────────────────────────────────────

  async create(data: {
    titolo: string;
    data: Date;
    luogo: string;
    tipo: string;
    ordineDelGiorno?: string;
    condominioId: number;
  }) {
    // Recupera tutti i condòmini attivi e inizializza le presenze a false
    const condomini = await this.prisma.condomino.findMany({
      where: { condominioId: data.condominioId, stato: 'attivo' },
      select: { id: true },
    });

    const assemblea = await this.prisma.assemblea.create({
      data: {
        titolo: data.titolo,
        data: data.data,
        luogo: data.luogo,
        tipo: data.tipo,
        ordineDelGiorno: data.ordineDelGiorno ?? '',
        condominioId: data.condominioId,
        presenze: {
          create: condomini.map((c) => ({ condominoId: c.id })),
        },
      },
      include: INCLUDE_ASSEMBLEA,
    });

    return assemblea;
  }

  // ── Aggiorna assemblea (admin) ─────────────────────────────────────────────

  async update(
    id: number,
    data: {
      titolo?: string;
      data?: Date;
      luogo?: string;
      tipo?: string;
      stato?: string;
      ordineDelGiorno?: string;
      verbale?: string;
    },
  ) {
    await this.findOne(id);
    return this.prisma.assemblea.update({
      where: { id },
      data,
      include: INCLUDE_ASSEMBLEA,
    });
  }

  // ── Elimina assemblea (admin) ──────────────────────────────────────────────

  async remove(id: number) {
    await this.findOne(id);
    return this.prisma.assemblea.delete({ where: { id } });
  }

  // ── Presenze: aggiorna presenza di un condòmino (admin) ───────────────────

  async upsertPresenza(
    assembleaId: number,
    condominoId: number,
    data: { presente: boolean; delegatoId?: number | null },
  ) {
    await this.findOne(assembleaId);
    return this.prisma.assembleaPresenza.upsert({
      where: { assembleaId_condominoId: { assembleaId, condominoId } },
      create: {
        assembleaId,
        condominoId,
        presente: data.presente,
        delegatoId: data.delegatoId ?? null,
      },
      update: {
        presente: data.presente,
        delegatoId: data.delegatoId ?? null,
      },
      include: {
        condomino: { select: { id: true, nome: true, cognome: true, unita: true, millesimi: true } },
      },
    });
  }

  // ── Condomino: invia delega ────────────────────────────────────────────────

  async inviaDelega(assembleaId: number, condominoId: number, delegatoId: number) {
    const assemblea = await this.findOne(assembleaId);
    const condomino = await this.prisma.condomino.findUnique({ where: { id: condominoId } });
    if (!condomino || condomino.condominioId !== assemblea.condominioId) {
      throw new ForbiddenException('Non autorizzato');
    }

    return this.prisma.assembleaPresenza.upsert({
      where: { assembleaId_condominoId: { assembleaId, condominoId } },
      create: { assembleaId, condominoId, presente: false, delegatoId },
      update: { delegatoId },
    });
  }

  // ── Quorum: calcola quorum in base alle presenze ───────────────────────────

  async calcolaQuorum(assembleaId: number) {
    const assemblea = await this.prisma.assemblea.findUnique({
      where: { id: assembleaId },
      include: {
        presenze: {
          where: { presente: true },
          include: { condomino: { select: { millesimi: true } } },
        },
      },
    });
    if (!assemblea) throw new NotFoundException('Assemblea non trovata');

    // Millesimi totali del condominio
    const totale = await this.prisma.condomino.aggregate({
      where: { condominioId: assemblea.condominioId, stato: 'attivo' },
      _sum: { millesimi: true },
    });
    const millTotali = totale._sum.millesimi ?? 0;

    // Millesimi presenti (inclusi delegati)
    const presenzeMill = assemblea.presenze.reduce(
      (sum, p) => sum + (p.condomino?.millesimi ?? 0),
      0,
    );

    // Anche chi ha delegato un altro conta come presente
    const delegati = await this.prisma.assembleaPresenza.findMany({
      where: { assembleaId, delegatoId: { not: null } },
      include: { condomino: { select: { millesimi: true } } },
    });
    const delegatiMill = delegati.reduce((sum, d) => sum + (d.condomino?.millesimi ?? 0), 0);
    const millPresenti = presenzeMill + delegatiMill;

    const percPresenti = millTotali > 0 ? (millPresenti / millTotali) * 100 : 0;
    const quorumOrdinario = percPresenti > 50;
    const quorumStraordinario = percPresenti >= 66.67;

    return {
      millTotali,
      millPresenti,
      percPresenti: Math.round(percPresenti * 10) / 10,
      quorumOrdinario,
      quorumStraordinario,
      presentiCount: assemblea.presenze.length,
      delegatiCount: delegati.length,
    };
  }

  // ── Punti OdG: aggiungi ────────────────────────────────────────────────────

  async createPuntoOdG(
    assembleaId: number,
    data: { titolo: string; descrizione?: string; ordine?: number },
  ) {
    await this.findOne(assembleaId);
    const max = await this.prisma.assembleaPuntoOdG.aggregate({
      where: { assembleaId },
      _max: { ordine: true },
    });
    return this.prisma.assembleaPuntoOdG.create({
      data: {
        assembleaId,
        titolo: data.titolo,
        descrizione: data.descrizione ?? '',
        ordine: data.ordine ?? (max._max.ordine ?? 0) + 1,
      },
    });
  }

  // ── Punti OdG: aggiorna ────────────────────────────────────────────────────

  async updatePuntoOdG(
    id: number,
    data: {
      titolo?: string;
      descrizione?: string;
      ordine?: number;
      esito?: string | null;
      votiSi?: number;
      votiNo?: number;
      votiAstenuti?: number;
    },
  ) {
    return this.prisma.assembleaPuntoOdG.update({ where: { id }, data });
  }

  // ── Punti OdG: elimina ────────────────────────────────────────────────────

  async deletePuntoOdG(id: number) {
    return this.prisma.assembleaPuntoOdG.delete({ where: { id } });
  }
}

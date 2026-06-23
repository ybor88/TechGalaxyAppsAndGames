import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class QuoteService {
  constructor(private prisma: PrismaService) {}

  // ── Quote mensili ─────────────────────────────────────────────────────────

  async findAllQuote(condominioId: number) {
    return this.prisma.quotaMensile.findMany({
      where: { condominioId },
      orderBy: [{ anno: 'desc' }, { mese: 'desc' }],
      include: {
        _count: { select: { pagamenti: true } },
        pagamenti: { select: { stato: true, importo: true } },
        destinatario: { select: { nome: true, cognome: true, unita: true } },
      },
    });
  }

  async createQuota(
    condominioId: number,
    mese: number,
    anno: number,
    importoTotale: number,
    tipo: string = 'collettiva',
    destinatarioId?: number,
  ) {
    const condo = await this.prisma.condominio.findUnique({ where: { id: condominioId } });
    if (!condo) throw new NotFoundException('Condominio non trovato');

    if (tipo === 'personale') {
      if (!destinatarioId) throw new BadRequestException('Seleziona il condòmino destinatario');
      const dest = await this.prisma.condomino.findFirst({ where: { id: destinatarioId, condominioId } });
      if (!dest) throw new NotFoundException('Condòmino non trovato');
      const existing = await this.prisma.quotaMensile.findFirst({
        where: { condominioId, mese, anno, tipo: 'personale', destinatarioId },
      });
      if (existing) throw new BadRequestException(`Quota personale ${mese}/${anno} già esistente per questo condòmino`);
    } else {
      const existing = await this.prisma.quotaMensile.findFirst({
        where: { condominioId, mese, anno, tipo: 'collettiva' },
      });
      if (existing) throw new BadRequestException(`Quota collettiva ${mese}/${anno} già esistente`);
    }

    return this.prisma.quotaMensile.create({
      data: { condominioId, mese, anno, importoTotale, tipo, destinatarioId: destinatarioId ?? null },
      include: { destinatario: { select: { nome: true, cognome: true, unita: true } } },
    });
  }

  async deleteQuota(quotaId: number) {
    const quota = await this.prisma.quotaMensile.findUnique({ where: { id: quotaId } });
    if (!quota) throw new NotFoundException('Quota non trovata');

    const hasPagati = await this.prisma.pagamentoQuota.findFirst({
      where: { quotaId, stato: 'pagato' },
    });
    if (hasPagati) throw new BadRequestException('Impossibile eliminare quota con pagamenti già registrati');

    await this.prisma.pagamentoQuota.deleteMany({ where: { quotaId } });
    return this.prisma.quotaMensile.delete({ where: { id: quotaId } });
  }

  // ── Generazione automatica pagamenti ────────────────────────────────────

  async generaPagamenti(quotaId: number) {
    const quota = await this.prisma.quotaMensile.findUnique({
      where: { id: quotaId },
      include: { condominio: { include: { condomini: { where: { stato: 'attivo' } } } } },
    });
    if (!quota) throw new NotFoundException('Quota non trovata');

    if (quota.tipo === 'personale') {
      if (!quota.destinatarioId) throw new BadRequestException('Quota personale senza destinatario');
      const alreadyExists = await this.prisma.pagamentoQuota.findFirst({
        where: { quotaId, condominoId: quota.destinatarioId },
      });
      if (alreadyExists) throw new BadRequestException('Pagamento già generato per questo condòmino');

      await this.prisma.pagamentoQuota.create({
        data: { condominoId: quota.destinatarioId, quotaId, importo: quota.importoTotale, stato: 'in_attesa' },
      });
    } else {
      const condomini = quota.condominio.condomini;
      if (condomini.length === 0) throw new BadRequestException('Nessun condòmino attivo nel condominio');

      const millesimaTotale = condomini.reduce((sum, c) => sum + c.millesimi, 0);

      const existing = await this.prisma.pagamentoQuota.findMany({ where: { quotaId } });
      const existingCondominoIds = new Set(existing.map((p) => p.condominoId));

      const toCreate = condomini
        .filter((c) => !existingCondominoIds.has(c.id))
        .map((c) => {
          const importo =
            millesimaTotale > 0
              ? Math.round((quota.importoTotale * c.millesimi) / millesimaTotale * 100) / 100
              : Math.round((quota.importoTotale / condomini.length) * 100) / 100;
          return { condominoId: c.id, quotaId, importo, stato: 'in_attesa' };
        });

      if (toCreate.length === 0) throw new BadRequestException('Pagamenti già generati per tutti i condòmini');

      await this.prisma.pagamentoQuota.createMany({ data: toCreate });
    }

    return this.prisma.pagamentoQuota.findMany({
      where: { quotaId },
      include: { condomino: { select: { nome: true, cognome: true, unita: true, millesimi: true } } },
      orderBy: { condomino: { cognome: 'asc' } },
    });
  }

  // ── Pagamenti ────────────────────────────────────────────────────────────

  async findPagamenti(quotaId: number) {
    const quota = await this.prisma.quotaMensile.findUnique({ where: { id: quotaId } });
    if (!quota) throw new NotFoundException('Quota non trovata');

    return this.prisma.pagamentoQuota.findMany({
      where: { quotaId },
      include: { condomino: { select: { nome: true, cognome: true, unita: true, millesimi: true } } },
      orderBy: { condomino: { cognome: 'asc' } },
    });
  }

  async updatePagamento(
    pagamentoId: number,
    data: {
      stato?: string;
      importo?: number;
      dataPagamento?: string | null;
      metodoPagamento?: string | null;
      note?: string | null;
    },
  ) {
    const pagamento = await this.prisma.pagamentoQuota.findUnique({ where: { id: pagamentoId } });
    if (!pagamento) throw new NotFoundException('Pagamento non trovato');

    const stato = data.stato ?? pagamento.stato;
    let dataPagamento = pagamento.dataPagamento;
    if (data.dataPagamento !== undefined) {
      dataPagamento = data.dataPagamento ? new Date(data.dataPagamento) : null;
    } else if (stato === 'pagato' && !dataPagamento) {
      dataPagamento = new Date();
    }

    return this.prisma.pagamentoQuota.update({
      where: { id: pagamentoId },
      data: {
        stato,
        importo: data.importo ?? pagamento.importo,
        dataPagamento,
        metodoPagamento: data.metodoPagamento !== undefined ? data.metodoPagamento : pagamento.metodoPagamento,
        note: data.note !== undefined ? data.note : pagamento.note,
      },
      include: { condomino: { select: { nome: true, cognome: true, unita: true } } },
    });
  }

  // ── Morosità ──────────────────────────────────────────────────────────────

  async getMorosita(condominioId: number) {
    const condo = await this.prisma.condominio.findUnique({ where: { id: condominioId } });
    if (!condo) throw new NotFoundException('Condominio non trovato');

    const pagamentiInMora = await this.prisma.pagamentoQuota.findMany({
      where: { stato: 'in_mora', condomino: { condominioId } },
      include: {
        condomino: { select: { nome: true, cognome: true, unita: true } },
        quota: { select: { mese: true, anno: true } },
      },
      orderBy: { condomino: { cognome: 'asc' } },
    });

    // Raggruppa per condomino
    const byCondomino = new Map<
      number,
      { nome: string; cognome: string; unita: string; importoTotale: number; quote: { mese: number; anno: number; importo: number }[] }
    >();
    for (const p of pagamentiInMora) {
      const key = p.condominoId;
      if (!byCondomino.has(key)) {
        byCondomino.set(key, {
          nome: p.condomino.nome,
          cognome: p.condomino.cognome,
          unita: p.condomino.unita,
          importoTotale: 0,
          quote: [],
        });
      }
      const entry = byCondomino.get(key)!;
      entry.importoTotale += p.importo;
      entry.quote.push({ mese: p.quota.mese, anno: p.quota.anno, importo: p.importo });
    }

    return {
      totaleInMora: pagamentiInMora.reduce((sum, p) => sum + p.importo, 0),
      condominoriMorosi: Array.from(byCondomino.values()),
    };
  }

  // ── Bilancio ─────────────────────────────────────────────────────────────

  async getBilancio(condominioId: number) {
    const condo = await this.prisma.condominio.findUnique({ where: { id: condominioId } });
    if (!condo) throw new NotFoundException('Condominio non trovato');

    const pagamenti = await this.prisma.pagamentoQuota.findMany({
      where: { condomino: { condominioId } },
      include: { quota: { select: { mese: true, anno: true } } },
    });

    const totaleEmesso = pagamenti.reduce((sum, p) => sum + p.importo, 0);
    const totalePagato = pagamenti.filter((p) => p.stato === 'pagato').reduce((sum, p) => sum + p.importo, 0);
    const totaleAttesa = pagamenti.filter((p) => p.stato === 'in_attesa').reduce((sum, p) => sum + p.importo, 0);
    const totaleMora = pagamenti.filter((p) => p.stato === 'in_mora').reduce((sum, p) => sum + p.importo, 0);

    const percentualePagata = totaleEmesso > 0 ? Math.round((totalePagato / totaleEmesso) * 100) : 0;

    return {
      totaleEmesso,
      totalePagato,
      totaleAttesa,
      totaleMora,
      percentualePagata,
      numeroPagamenti: pagamenti.length,
      numeroPagati: pagamenti.filter((p) => p.stato === 'pagato').length,
      numeroAttesa: pagamenti.filter((p) => p.stato === 'in_attesa').length,
      numeroMora: pagamenti.filter((p) => p.stato === 'in_mora').length,
    };
  }

  // ── Vista Condòmino: le proprie quote ─────────────────────────────────────

  async findMiePagamenti(condominoId: number) {
    const condomino = await this.prisma.condomino.findUnique({
      where: { id: condominoId },
      include: { condominio: { select: { nome: true, indirizzo: true } } },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');

    const pagamenti = await this.prisma.pagamentoQuota.findMany({
      where: { condominoId },
      include: { quota: { select: { mese: true, anno: true, importoTotale: true } } },
      orderBy: [{ quota: { anno: 'desc' } }, { quota: { mese: 'desc' } }],
    });

    return {
      condomino: {
        nome: condomino.nome,
        cognome: condomino.cognome,
        unita: condomino.unita,
        millesimi: condomino.millesimi,
        tipo: condomino.tipo,
      },
      condominio: {
        nome: condomino.condominio.nome,
        indirizzo: condomino.condominio.indirizzo,
      },
      pagamenti,
    };
  }
}

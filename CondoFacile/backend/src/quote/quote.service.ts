import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

const STATI_PAGAMENTO = ['in_attesa', 'pagato', 'in_mora'];

/** Arrotonda a 2 decimali evitando residui floating point (es. 0.1 + 0.2). */
const round2 = (n: number) => Math.round(n * 100) / 100;

/**
 * Ripartisce un importo in centesimi proporzionalmente ai pesi con il metodo
 * dei resti maggiori: la somma delle parti coincide sempre con il totale.
 */
export function ripartisciImporto(totale: number, pesi: number[]): number[] {
  const centesimi = Math.round(totale * 100);
  const sommaPesi = pesi.reduce((a, b) => a + b, 0);
  const quoteEsatte = pesi.map((w) =>
    sommaPesi > 0 ? (centesimi * w) / sommaPesi : centesimi / pesi.length,
  );
  const parti = quoteEsatte.map((q) => Math.floor(q));
  let resto = centesimi - parti.reduce((a, b) => a + b, 0);
  const ordine = quoteEsatte
    .map((q, i) => ({ i, frazione: q - Math.floor(q) }))
    .sort((a, b) => b.frazione - a.frazione);
  for (let k = 0; resto > 0; k = (k + 1) % ordine.length, resto--) parti[ordine[k].i]++;
  return parti.map((c) => c / 100);
}

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
    if (!Number.isInteger(mese) || mese < 1 || mese > 12) {
      throw new BadRequestException('Il mese deve essere compreso tra 1 e 12');
    }
    if (!Number.isInteger(anno) || anno < 2000 || anno > 2100) {
      throw new BadRequestException('Anno non valido');
    }
    if (typeof importoTotale !== 'number' || !(importoTotale > 0)) {
      throw new BadRequestException("L'importo deve essere maggiore di zero");
    }
    if (tipo !== 'collettiva' && tipo !== 'personale') {
      throw new BadRequestException('Tipo quota non valido');
    }
    importoTotale = round2(importoTotale);
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
      // Ripartizione al centesimo: la somma dei pagamenti è esattamente l'importo della quota
      const importi = ripartisciImporto(
        quota.importoTotale,
        condomini.map((c) => (millesimaTotale > 0 ? c.millesimi : 1)),
      );

      const existing = await this.prisma.pagamentoQuota.findMany({ where: { quotaId } });
      const existingCondominoIds = new Set(existing.map((p) => p.condominoId));

      const toCreate = condomini
        .map((c, i) => ({ condominoId: c.id, quotaId, importo: importi[i], stato: 'in_attesa' }))
        .filter((p) => !existingCondominoIds.has(p.condominoId));

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
    if (data.stato !== undefined && !STATI_PAGAMENTO.includes(data.stato)) {
      throw new BadRequestException(`Stato non valido. Valori ammessi: ${STATI_PAGAMENTO.join(', ')}`);
    }
    if (data.importo !== undefined && (typeof data.importo !== 'number' || data.importo < 0)) {
      throw new BadRequestException("L'importo non può essere negativo");
    }
    const pagamento = await this.prisma.pagamentoQuota.findUnique({ where: { id: pagamentoId } });
    if (!pagamento) throw new NotFoundException('Pagamento non trovato');

    const stato = data.stato ?? pagamento.stato;
    let dataPagamento = pagamento.dataPagamento;
    if (data.dataPagamento !== undefined) {
      dataPagamento = data.dataPagamento ? new Date(data.dataPagamento) : null;
      if (dataPagamento && Number.isNaN(dataPagamento.getTime())) {
        throw new BadRequestException('Data di pagamento non valida');
      }
    } else if (stato === 'pagato' && !dataPagamento) {
      dataPagamento = new Date();
    } else if (stato !== 'pagato') {
      // un pagamento riportato in attesa/mora non ha più una data di incasso
      dataPagamento = null;
    }

    return this.prisma.pagamentoQuota.update({
      where: { id: pagamentoId },
      data: {
        stato,
        importo: data.importo !== undefined ? round2(data.importo) : pagamento.importo,
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
      totaleInMora: round2(pagamentiInMora.reduce((sum, p) => sum + p.importo, 0)),
      condominoriMorosi: Array.from(byCondomino.values()).map((m) => ({
        ...m,
        importoTotale: round2(m.importoTotale),
      })),
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
      totaleEmesso: round2(totaleEmesso),
      totalePagato: round2(totalePagato),
      totaleAttesa: round2(totaleAttesa),
      totaleMora: round2(totaleMora),
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

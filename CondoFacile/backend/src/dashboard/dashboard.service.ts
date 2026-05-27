import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { CondominoDashboardData, DashboardData } from './dashboard.types';

@Injectable()
export class DashboardService {
  constructor(private readonly prisma: PrismaService) {}

  async getDashboardData(): Promise<DashboardData> {
    const oggi = new Date();

    // Recupera il primo condominio disponibile
    const condominio = await this.prisma.condominio.findFirst({
      include: {
        condomini: { include: { pagamenti: true } },
        ticket: { where: { stato: { notIn: ['Risolta', 'Chiusa'] } } },
        scadenze: {
          where: { data: { gte: oggi, lte: new Date(oggi.getTime() + 30 * 24 * 60 * 60 * 1000) } },
          orderBy: { data: 'asc' },
          take: 5,
        },
        comunicazioni: { orderBy: { data: 'desc' }, take: 5 },
        lavori: { where: { stato: { notIn: ['Completato'] } } },
        quote: {
          orderBy: [{ anno: 'desc' }, { mese: 'desc' }],
          take: 6,
          include: { pagamenti: true },
        },
      },
    });

    // Nessun dato nel database
    if (!condominio) {
      return {
        nomeCondominio: '',
        aggiornato: oggi.toISOString(),
        statoPagementi: {
          totaleCondomini: 0,
          pagato: 0,
          inAttesa: 0,
          inMora: 0,
          percentualePagato: 0,
        },
        speseUltimiMesi: [],
        segnalazioniAperte: [],
        scadenzeImminenti: [],
        comunicazioniRecenti: [],
        lavoriInCorso: [],
      };
    }

    // ── Stato pagamenti ──────────────────────────────────────────────
    const totaleCondomini = condominio.condomini.length;
    const pagati = condominio.condomini.filter((c) =>
      c.pagamenti.some((p) => p.stato === 'pagato'),
    ).length;
    const inMora = condominio.condomini.filter((c) => c.stato === 'moroso').length;
    const inAttesa = totaleCondomini - pagati - inMora;
    const percentualePagato =
      totaleCondomini > 0 ? Math.round((pagati / totaleCondomini) * 100) : 0;

    // ── Spese ultimi mesi ────────────────────────────────────────────
    const mesiNomi = ['Gen','Feb','Mar','Apr','Mag','Giu','Lug','Ago','Set','Ott','Nov','Dic'];
    const speseUltimiMesi = [...condominio.quote]
      .reverse()
      .map((q) => ({
        mese: `${mesiNomi[q.mese - 1]} ${q.anno}`,
        importo: q.importoTotale,
      }));

    // ── Ticket aperti ────────────────────────────────────────────────
    const segnalazioniAperte = condominio.ticket.map((t) => ({
      id: t.id,
      titolo: t.titolo,
      categoria: t.categoria,
      priorita: t.priorita as 'alta' | 'media' | 'bassa',
      stato: t.stato,
      dataApertura: t.dataApertura.toISOString().split('T')[0],
    }));

    // ── Scadenze ─────────────────────────────────────────────────────
    const scadenzeImminenti = condominio.scadenze.map((s) => {
      const diffMs = s.data.getTime() - oggi.getTime();
      const giorniMancanti = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
      return {
        id: s.id,
        descrizione: s.descrizione,
        data: s.data.toISOString().split('T')[0],
        tipo: s.tipo,
        giorniMancanti,
      };
    });

    // ── Comunicazioni ────────────────────────────────────────────────
    const comunicazioniRecenti = condominio.comunicazioni.map((c) => ({
      id: c.id,
      titolo: c.titolo,
      tipo: c.tipo,
      data: c.data.toISOString().split('T')[0],
      destinatari: c.destinatari,
    }));

    // ── Lavori in corso ──────────────────────────────────────────────
    const lavoriInCorso = condominio.lavori.map((l) => ({
      id: l.id,
      descrizione: l.descrizione,
      fornitore: l.fornitore,
      dataInizio: l.dataInizio.toISOString().split('T')[0],
      dataFine: l.dataFine.toISOString().split('T')[0],
      stato: l.stato,
      progressione: l.progressione,
    }));

    return {
      nomeCondominio: `${condominio.nome} · ${condominio.indirizzo}`,
      aggiornato: oggi.toISOString(),
      statoPagementi: { totaleCondomini, pagato: pagati, inAttesa, inMora, percentualePagato },
      speseUltimiMesi,
      segnalazioniAperte,
      scadenzeImminenti,
      comunicazioniRecenti,
      lavoriInCorso,
    };
  }

  // ── Dashboard personalizzata per il condomino ────────────────────────────
  async getDashboardCondomino(condominoId: number): Promise<CondominoDashboardData> {
    const oggi = new Date();
    const tra30 = new Date(oggi.getTime() + 30 * 24 * 60 * 60 * 1000);
    const mesiNomi = ['Gen','Feb','Mar','Apr','Mag','Giu','Lug','Ago','Set','Ott','Nov','Dic'];

    const condomino = await this.prisma.condomino.findUnique({
      where: { id: condominoId },
      include: {
        pagamenti: {
          include: { quota: true },
          orderBy: [{ quota: { anno: 'desc' } }, { quota: { mese: 'desc' } }],
          take: 1,
        },
        condominio: {
          include: {
            comunicazioni: { orderBy: { data: 'desc' }, take: 100 },
            ticket: { where: { stato: { notIn: ['Risolta', 'Chiusa'] } } },
            scadenze: {
              where: { data: { gte: oggi, lte: tra30 } },
              orderBy: { data: 'asc' },
              take: 5,
            },
          },
        },
      },
    });

    if (!condomino) throw new NotFoundException('Condomino non trovato');

    // Quota corrente
    const ultimoPagamento = condomino.pagamenti[0] ?? null;
    const quotaCorrente = ultimoPagamento
      ? {
          mese: ultimoPagamento.quota.mese,
          anno: ultimoPagamento.quota.anno,
          importo: ultimoPagamento.importo,
          stato: (ultimoPagamento.stato === 'pagato'
            ? 'pagata'
            : ultimoPagamento.stato === 'in_mora'
            ? 'in_mora'
            : 'in_attesa') as 'pagata' | 'in_attesa' | 'in_mora',
          dataPagamento: ultimoPagamento.dataPagamento
            ? ultimoPagamento.dataPagamento.toISOString().split('T')[0]
            : null,
        }
      : null;

    const comunicazioniRecenti = condomino.condominio.comunicazioni.map((c) => ({
      id: c.id,
      titolo: c.titolo,
      tipo: c.tipo,
      data: c.data.toISOString().split('T')[0],
      destinatari: c.destinatari,
    }));

    const segnalazioniAperte = condomino.condominio.ticket.map((t) => ({
      id: t.id,
      titolo: t.titolo,
      categoria: t.categoria,
      priorita: t.priorita as 'alta' | 'media' | 'bassa',
      stato: t.stato,
      dataApertura: t.dataApertura.toISOString().split('T')[0],
    }));

    const scadenzeImminenti = condomino.condominio.scadenze.map((s) => {
      const diffMs = s.data.getTime() - oggi.getTime();
      const giorniMancanti = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
      return {
        id: s.id,
        descrizione: s.descrizione,
        data: s.data.toISOString().split('T')[0],
        tipo: s.tipo,
        giorniMancanti,
      };
    });

    void mesiNomi; // used indirectly via quota.mese
    return {
      nomeCondomino: `${condomino.nome} ${condomino.cognome}`,
      unita: condomino.unita,
      nomeCondominio: `${condomino.condominio.nome} · ${condomino.condominio.indirizzo}`,
      aggiornato: oggi.toISOString(),
      quotaCorrente,
      comunicazioniRecenti,
      segnalazioniAperte,
      scadenzeImminenti,
    };
  }
}


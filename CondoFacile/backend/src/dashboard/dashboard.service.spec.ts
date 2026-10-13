import { NotFoundException } from '@nestjs/common';
import { DashboardService } from './dashboard.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    condominio: { findFirst: jest.fn() },
    condomino: { findUnique: jest.fn() },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('DashboardService', () => {
  let service: DashboardService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new DashboardService(prisma as unknown as PrismaService);
  });

  describe('getDashboardData', () => {
    it('returns an empty-state payload when there is no condominio', async () => {
      prisma.condominio.findFirst.mockResolvedValue(null);
      const result = await service.getDashboardData();
      expect(result.nomeCondominio).toBe('');
      expect(result.statoPagementi.totaleCondomini).toBe(0);
    });

    it('computes payment stats from condomini and their pagamenti', async () => {
      prisma.condominio.findFirst.mockResolvedValue({
        nome: 'Residenza A',
        indirizzo: 'Via Roma 1',
        condomini: [
          { stato: 'attivo', pagamenti: [{ stato: 'pagato' }] },
          { stato: 'attivo', pagamenti: [{ stato: 'pagato' }, { stato: 'in_mora' }] },
          { stato: 'attivo', pagamenti: [] },
        ],
        ticket: [],
        scadenze: [],
        comunicazioni: [],
        lavori: [],
        quote: [],
      });

      const result = await service.getDashboardData();

      expect(result.statoPagementi).toEqual({
        totaleCondomini: 3,
        pagato: 1,
        inAttesa: 1,
        inMora: 1,
        percentualePagato: 33,
      });
      expect(result.nomeCondominio).toBe('Residenza A · Via Roma 1');
    });
  });

  describe('getDashboardCondomino', () => {
    it('throws NotFoundException for an unknown condomino', async () => {
      prisma.condomino.findUnique.mockResolvedValue(null);
      await expect(service.getDashboardCondomino(1)).rejects.toThrow(NotFoundException);
    });

    it('maps the most recent pagamento to quotaCorrente', async () => {
      prisma.condomino.findUnique.mockResolvedValue({
        pagamenti: [
          {
            stato: 'pagato',
            importo: 150,
            dataPagamento: new Date('2026-01-10'),
            quota: { mese: 1, anno: 2026 },
          },
        ],
        condominio: { comunicazioni: [], ticket: [], scadenze: [] },
      });

      const result = await service.getDashboardCondomino(1);

      expect(result.quotaCorrente).toEqual({
        mese: 1,
        anno: 2026,
        importo: 150,
        stato: 'pagata',
        dataPagamento: '2026-01-10',
      });
    });
  });

  describe('regressioni stato pagamenti', () => {
    const base = { nome: 'R', indirizzo: 'V', ticket: [], scadenze: [], comunicazioni: [], lavori: [], quote: [] };

    it('chi ha pagato in passato ma ha una quota aperta è in attesa, non pagato', async () => {
      prisma.condominio.findFirst.mockResolvedValue({
        ...base,
        condomini: [{ stato: 'attivo', pagamenti: [{ stato: 'pagato' }, { stato: 'in_attesa' }] }],
      });
      const r = await service.getDashboardData();
      expect(r.statoPagementi).toMatchObject({ pagato: 0, inAttesa: 1, inMora: 0 });
    });

    it('le categorie non si sovrappongono e sommano al totale', async () => {
      prisma.condominio.findFirst.mockResolvedValue({
        ...base,
        condomini: [
          { stato: 'attivo', pagamenti: [{ stato: 'pagato' }, { stato: 'in_mora' }] },
          { stato: 'attivo', pagamenti: [{ stato: 'pagato' }] },
          { stato: 'attivo', pagamenti: [{ stato: 'in_mora' }, { stato: 'in_attesa' }] },
        ],
      });
      const { statoPagementi: s } = await service.getDashboardData();
      expect(s.pagato + s.inAttesa + s.inMora).toBe(s.totaleCondomini);
      expect(s.inMora).toBe(2);
      expect(s.inAttesa).toBeGreaterThanOrEqual(0);
    });

    it('usa il condominio richiesto con ordinamento deterministico', async () => {
      prisma.condominio.findFirst.mockResolvedValue(null);
      await service.getDashboardData(7);
      expect(prisma.condominio.findFirst).toHaveBeenCalledWith(
        expect.objectContaining({ where: { id: 7 }, orderBy: { id: 'asc' } }),
      );
    });
  });
});

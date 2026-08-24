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
          { stato: 'moroso', pagamenti: [] },
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
});

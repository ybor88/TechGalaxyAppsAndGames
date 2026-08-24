import { FornitoriService } from './fornitori.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    fornitore: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn(), count: jest.fn() },
    intervento: { findMany: jest.fn(), create: jest.fn(), count: jest.fn(), aggregate: jest.fn(), groupBy: jest.fn() },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('FornitoriService', () => {
  let service: FornitoriService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new FornitoriService(prisma as unknown as PrismaService);
  });

  describe('analytics', () => {
    it('aggregates totals and top fornitori', async () => {
      prisma.fornitore.count.mockResolvedValue(3);
      prisma.intervento.count.mockResolvedValue(10);
      prisma.intervento.aggregate.mockResolvedValue({ _sum: { costo: 1500 } });
      prisma.intervento.groupBy.mockResolvedValue([
        { fornitoreId: 1, _count: { fornitoreId: 6 }, _sum: { costo: 1000 } },
      ]);
      prisma.fornitore.findUnique.mockResolvedValue({ id: 1, nome: 'Idraulico Rossi' });

      const result = await service.analytics(1);

      expect(result.totalFornitori).toBe(3);
      expect(result.totalInterventi).toBe(10);
      expect(result.totalCosto).toBe(1500);
      expect(result.topFornitori).toEqual([{ id: 1, nome: 'Idraulico Rossi', interventi: 6, costo: 1000 }]);
    });

    it('defaults total cost to 0 when there are no interventi', async () => {
      prisma.fornitore.count.mockResolvedValue(0);
      prisma.intervento.count.mockResolvedValue(0);
      prisma.intervento.aggregate.mockResolvedValue({ _sum: { costo: null } });
      prisma.intervento.groupBy.mockResolvedValue([]);

      const result = await service.analytics();

      expect(result.totalCosto).toBe(0);
      expect(result.topFornitori).toEqual([]);
    });
  });
});

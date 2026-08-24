import { BadRequestException, NotFoundException } from '@nestjs/common';
import { QuoteService } from './quote.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    condominio: { findUnique: jest.fn() },
    condomino: { findFirst: jest.fn(), findUnique: jest.fn() },
    quotaMensile: { findMany: jest.fn(), findFirst: jest.fn(), findUnique: jest.fn(), create: jest.fn(), delete: jest.fn() },
    pagamentoQuota: {
      findFirst: jest.fn(),
      findMany: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      createMany: jest.fn(),
      deleteMany: jest.fn(),
      update: jest.fn(),
    },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('QuoteService', () => {
  let service: QuoteService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new QuoteService(prisma as unknown as PrismaService);
  });

  describe('createQuota', () => {
    it('throws NotFoundException when condominio does not exist', async () => {
      prisma.condominio.findUnique.mockResolvedValue(null);
      await expect(service.createQuota(1, 1, 2026, 1000)).rejects.toThrow(NotFoundException);
    });

    it('throws BadRequestException for duplicate collettiva quota', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.quotaMensile.findFirst.mockResolvedValue({ id: 5 });
      await expect(service.createQuota(1, 1, 2026, 1000)).rejects.toThrow(BadRequestException);
    });

    it('requires a destinatarioId for personal quote', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      await expect(service.createQuota(1, 1, 2026, 100, 'personale')).rejects.toThrow(BadRequestException);
    });

    it('throws NotFoundException when destinatario does not belong to the condominio', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.condomino.findFirst.mockResolvedValue(null);
      await expect(service.createQuota(1, 1, 2026, 100, 'personale', 99)).rejects.toThrow(NotFoundException);
    });

    it('creates a collettiva quota when none exists yet', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.quotaMensile.findFirst.mockResolvedValue(null);
      prisma.quotaMensile.create.mockResolvedValue({ id: 1, mese: 1, anno: 2026, importoTotale: 1000 });
      const result = await service.createQuota(1, 1, 2026, 1000);
      expect(result).toEqual({ id: 1, mese: 1, anno: 2026, importoTotale: 1000 });
      expect(prisma.quotaMensile.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ condominioId: 1, mese: 1, anno: 2026, importoTotale: 1000, tipo: 'collettiva' }),
        }),
      );
    });
  });

  describe('deleteQuota', () => {
    it('throws NotFoundException for unknown quota', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue(null);
      await expect(service.deleteQuota(1)).rejects.toThrow(NotFoundException);
    });

    it('refuses to delete a quota with paid payments', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue({ id: 1 });
      prisma.pagamentoQuota.findFirst.mockResolvedValue({ id: 1, stato: 'pagato' });
      await expect(service.deleteQuota(1)).rejects.toThrow(BadRequestException);
    });

    it('deletes payments then the quota when nothing is paid', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue({ id: 1 });
      prisma.pagamentoQuota.findFirst.mockResolvedValue(null);
      prisma.quotaMensile.delete.mockResolvedValue({ id: 1 });
      await service.deleteQuota(1);
      expect(prisma.pagamentoQuota.deleteMany).toHaveBeenCalledWith({ where: { quotaId: 1 } });
      expect(prisma.quotaMensile.delete).toHaveBeenCalledWith({ where: { id: 1 } });
    });
  });

  describe('generaPagamenti', () => {
    it('splits a collettiva quota proportionally by millesimi', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue({
        id: 1,
        tipo: 'collettiva',
        importoTotale: 900,
        condominio: {
          condomini: [
            { id: 1, millesimi: 600 },
            { id: 2, millesimi: 300 },
          ],
        },
      });
      prisma.pagamentoQuota.findMany
        .mockResolvedValueOnce([]) // existing check
        .mockResolvedValueOnce([]); // final findMany return value (irrelevant)
      prisma.pagamentoQuota.createMany.mockResolvedValue({ count: 2 });

      await service.generaPagamenti(1);

      expect(prisma.pagamentoQuota.createMany).toHaveBeenCalledWith({
        data: [
          { condominoId: 1, quotaId: 1, importo: 600, stato: 'in_attesa' },
          { condominoId: 2, quotaId: 1, importo: 300, stato: 'in_attesa' },
        ],
      });
    });

    it('splits equally when total millesimi is zero', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue({
        id: 1,
        tipo: 'collettiva',
        importoTotale: 100,
        condominio: {
          condomini: [
            { id: 1, millesimi: 0 },
            { id: 2, millesimi: 0 },
          ],
        },
      });
      prisma.pagamentoQuota.findMany.mockResolvedValueOnce([]).mockResolvedValueOnce([]);
      prisma.pagamentoQuota.createMany.mockResolvedValue({ count: 2 });

      await service.generaPagamenti(1);

      expect(prisma.pagamentoQuota.createMany).toHaveBeenCalledWith({
        data: [
          { condominoId: 1, quotaId: 1, importo: 50, stato: 'in_attesa' },
          { condominoId: 2, quotaId: 1, importo: 50, stato: 'in_attesa' },
        ],
      });
    });

    it('throws BadRequestException when there are no active condomini', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue({
        id: 1,
        tipo: 'collettiva',
        importoTotale: 100,
        condominio: { condomini: [] },
      });
      await expect(service.generaPagamenti(1)).rejects.toThrow(BadRequestException);
    });

    it('throws BadRequestException when payment for personale quota already exists', async () => {
      prisma.quotaMensile.findUnique.mockResolvedValue({
        id: 1,
        tipo: 'personale',
        destinatarioId: 5,
        importoTotale: 100,
        condominio: { condomini: [] },
      });
      prisma.pagamentoQuota.findFirst.mockResolvedValue({ id: 1 });
      await expect(service.generaPagamenti(1)).rejects.toThrow(BadRequestException);
    });
  });

  describe('getMorosita', () => {
    it('groups overdue payments by condomino and sums totals', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.pagamentoQuota.findMany.mockResolvedValue([
        { condominoId: 1, importo: 100, condomino: { nome: 'A', cognome: 'B', unita: 'U1' }, quota: { mese: 1, anno: 2026 } },
        { condominoId: 1, importo: 50, condomino: { nome: 'A', cognome: 'B', unita: 'U1' }, quota: { mese: 2, anno: 2026 } },
        { condominoId: 2, importo: 75, condomino: { nome: 'C', cognome: 'D', unita: 'U2' }, quota: { mese: 1, anno: 2026 } },
      ]);

      const result = await service.getMorosita(1);

      expect(result.totaleInMora).toBe(225);
      expect(result.condominoriMorosi).toHaveLength(2);
      const first = result.condominoriMorosi.find((c) => c.unita === 'U1');
      expect(first?.importoTotale).toBe(150);
      expect(first?.quote).toHaveLength(2);
    });
  });

  describe('getBilancio', () => {
    it('computes totals and percentage paid', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.pagamentoQuota.findMany.mockResolvedValue([
        { stato: 'pagato', importo: 100 },
        { stato: 'in_attesa', importo: 50 },
        { stato: 'in_mora', importo: 25 },
        { stato: 'pagato', importo: 25 },
      ]);

      const result = await service.getBilancio(1);

      expect(result.totaleEmesso).toBe(200);
      expect(result.totalePagato).toBe(125);
      expect(result.totaleAttesa).toBe(50);
      expect(result.totaleMora).toBe(25);
      expect(result.percentualePagata).toBe(63); // round(125/200*100)
      expect(result.numeroPagati).toBe(2);
    });
  });
});

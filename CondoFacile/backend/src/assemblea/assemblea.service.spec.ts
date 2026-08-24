import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { AssembleaService } from './assemblea.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    condomino: { findMany: jest.fn(), findUnique: jest.fn(), aggregate: jest.fn() },
    assemblea: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
    assembleaPresenza: { upsert: jest.fn(), findMany: jest.fn() },
    assembleaPuntoOdG: { aggregate: jest.fn(), create: jest.fn(), findUnique: jest.fn(), update: jest.fn() },
    assembleaVoto: { findUnique: jest.fn() },
    $transaction: jest.fn(),
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('AssembleaService', () => {
  let service: AssembleaService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new AssembleaService(prisma as unknown as PrismaService);
  });

  describe('inviaDelega', () => {
    it('forbids delegating on behalf of a condomino from another condominio', async () => {
      prisma.assemblea.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.condomino.findUnique.mockResolvedValue({ id: 2, condominioId: 6 });
      await expect(service.inviaDelega(1, 2, 3)).rejects.toThrow(ForbiddenException);
    });

    it('rejects self-delegation', async () => {
      prisma.assemblea.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.condomino.findUnique.mockResolvedValue({ id: 2, condominioId: 5 });
      await expect(service.inviaDelega(1, 2, 2)).rejects.toThrow(BadRequestException);
    });

    it('throws NotFoundException when delegato does not exist or belongs to another condominio', async () => {
      prisma.assemblea.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.condomino.findUnique
        .mockResolvedValueOnce({ id: 2, condominioId: 5 }) // delegante
        .mockResolvedValueOnce(null); // delegato non trovato
      await expect(service.inviaDelega(1, 2, 3)).rejects.toThrow(NotFoundException);
    });

    it('upserts the delega for a valid condomino and delegato', async () => {
      prisma.assemblea.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.condomino.findUnique
        .mockResolvedValueOnce({ id: 2, condominioId: 5 }) // delegante
        .mockResolvedValueOnce({ id: 3, condominioId: 5 }); // delegato
      prisma.assembleaPresenza.upsert.mockResolvedValue({ assembleaId: 1, condominoId: 2, delegatoId: 3 });

      await service.inviaDelega(1, 2, 3);

      expect(prisma.assembleaPresenza.upsert).toHaveBeenCalledWith({
        where: { assembleaId_condominoId: { assembleaId: 1, condominoId: 2 } },
        create: { assembleaId: 1, condominoId: 2, presente: false, delegatoId: 3 },
        update: { delegatoId: 3 },
      });
    });
  });

  describe('calcolaQuorum', () => {
    it('throws NotFoundException for an unknown assemblea', async () => {
      prisma.assemblea.findUnique.mockResolvedValue(null);
      await expect(service.calcolaQuorum(1)).rejects.toThrow(NotFoundException);
    });

    it('computes quorum percentages including delegated millesimi', async () => {
      prisma.assemblea.findUnique.mockResolvedValue({
        id: 1,
        condominioId: 5,
        presenze: [{ condomino: { millesimi: 300 } }],
      });
      prisma.condomino.aggregate.mockResolvedValue({ _sum: { millesimi: 1000 } });
      prisma.assembleaPresenza.findMany.mockResolvedValue([{ condomino: { millesimi: 200 } }]);

      const result = await service.calcolaQuorum(1);

      expect(result.millTotali).toBe(1000);
      expect(result.millPresenti).toBe(500);
      expect(result.percPresenti).toBe(50);
      expect(result.quorumOrdinario).toBe(false); // 50% is not > 50%
      expect(result.quorumStraordinario).toBe(false);
    });

    it('flags quorum ordinario true when strictly above 50%', async () => {
      prisma.assemblea.findUnique.mockResolvedValue({
        id: 1,
        condominioId: 5,
        presenze: [{ condomino: { millesimi: 600 } }],
      });
      prisma.condomino.aggregate.mockResolvedValue({ _sum: { millesimi: 1000 } });
      prisma.assembleaPresenza.findMany.mockResolvedValue([]);

      const result = await service.calcolaQuorum(1);

      expect(result.quorumOrdinario).toBe(true);
      expect(result.quorumStraordinario).toBe(false);
    });
  });

  describe('votaPunto', () => {
    const puntoBase = { id: 10, assemblea: { condominioId: 5 } };

    it('throws NotFoundException when the punto does not exist', async () => {
      prisma.assembleaPuntoOdG.findUnique.mockResolvedValue(null);
      await expect(service.votaPunto(10, 2, 'si')).rejects.toThrow(NotFoundException);
    });

    it('forbids voting from a condomino of another condominio', async () => {
      prisma.assembleaPuntoOdG.findUnique.mockResolvedValue(puntoBase);
      prisma.condomino.findUnique.mockResolvedValue({ id: 2, condominioId: 6, millesimi: 100 });
      await expect(service.votaPunto(10, 2, 'si')).rejects.toThrow(ForbiddenException);
    });

    it('creates a new weighted vote when none exists yet', async () => {
      prisma.assembleaPuntoOdG.findUnique.mockResolvedValue(puntoBase);
      prisma.condomino.findUnique.mockResolvedValue({ id: 2, condominioId: 5, millesimi: 150 });
      prisma.assembleaVoto.findUnique.mockResolvedValue(null);

      const tx = {
        assembleaVoto: { create: jest.fn().mockResolvedValue({}) },
        assembleaPuntoOdG: {
          update: jest.fn().mockResolvedValue({}),
          findUnique: jest.fn().mockResolvedValue({ id: 10, votiSi: 150 }),
        },
      };
      prisma.$transaction.mockImplementation((cb: (t: typeof tx) => unknown) => cb(tx));

      const result = await service.votaPunto(10, 2, 'si');

      expect(tx.assembleaVoto.create).toHaveBeenCalledWith({ data: { puntoOdGId: 10, condominoId: 2, scelta: 'si' } });
      expect(tx.assembleaPuntoOdG.update).toHaveBeenCalledWith({ where: { id: 10 }, data: { votiSi: { increment: 150 } } });
      expect(result).toEqual({ id: 10, votiSi: 150 });
    });

    it('moves the weight from the previous choice to the new one when changing vote', async () => {
      prisma.assembleaPuntoOdG.findUnique.mockResolvedValue(puntoBase);
      prisma.condomino.findUnique.mockResolvedValue({ id: 2, condominioId: 5, millesimi: 150 });
      prisma.assembleaVoto.findUnique.mockResolvedValue({ id: 99, scelta: 'no' });

      const tx = {
        assembleaVoto: { update: jest.fn().mockResolvedValue({}) },
        assembleaPuntoOdG: {
          update: jest.fn().mockResolvedValue({}),
          findUnique: jest.fn().mockResolvedValue({ id: 10 }),
        },
      };
      prisma.$transaction.mockImplementation((cb: (t: typeof tx) => unknown) => cb(tx));

      await service.votaPunto(10, 2, 'si');

      expect(tx.assembleaPuntoOdG.update).toHaveBeenNthCalledWith(1, { where: { id: 10 }, data: { votiNo: { decrement: 150 } } });
      expect(tx.assembleaVoto.update).toHaveBeenCalledWith({ where: { id: 99 }, data: { scelta: 'si', createdAt: expect.any(Date) } });
      expect(tx.assembleaPuntoOdG.update).toHaveBeenNthCalledWith(2, { where: { id: 10 }, data: { votiSi: { increment: 150 } } });
    });
  });
});

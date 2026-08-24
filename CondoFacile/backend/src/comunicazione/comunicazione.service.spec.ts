import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { ComunicazioneService } from './comunicazione.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    condomino: { count: jest.fn(), findUnique: jest.fn() },
    comunicazione: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
    comunicazioneLettura: { upsert: jest.fn(), findMany: jest.fn() },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('ComunicazioneService', () => {
  let service: ComunicazioneService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new ComunicazioneService(prisma as unknown as PrismaService);
  });

  describe('create', () => {
    it('stamps the current count of active condomini as destinatari', async () => {
      prisma.condomino.count.mockResolvedValue(12);
      prisma.comunicazione.create.mockResolvedValue({ id: 1, destinatari: 12 });

      await service.create({ titolo: 'Avviso', corpo: 'Testo', tipo: 'avviso', condominioId: 1 });

      expect(prisma.comunicazione.create).toHaveBeenCalledWith(
        expect.objectContaining({ data: expect.objectContaining({ destinatari: 12, destinatariTipo: 'tutti' }) }),
      );
    });

    it('counts only proprietari when destinatariTipo is proprietari', async () => {
      prisma.condomino.count.mockResolvedValue(4);
      prisma.comunicazione.create.mockResolvedValue({ id: 1, destinatari: 4 });

      await service.create({ titolo: 'Avviso', corpo: 'Testo', tipo: 'avviso', condominioId: 1, destinatariTipo: 'proprietari' });

      expect(prisma.condomino.count).toHaveBeenCalledWith({ where: { condominioId: 1, stato: 'attivo', tipo: 'proprietario' } });
    });
  });

  describe('findMie', () => {
    it('throws NotFoundException for an unknown condomino', async () => {
      prisma.condomino.findUnique.mockResolvedValue(null);
      await expect(service.findMie(1)).rejects.toThrow(NotFoundException);
    });

    it('flags presoVisione based on the reader lettura record', async () => {
      prisma.condomino.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.comunicazione.findMany.mockResolvedValue([
        { id: 1, titolo: 'A', letture: [{ dataLettura: new Date('2026-01-01') }] },
        { id: 2, titolo: 'B', letture: [] },
      ]);

      const result = await service.findMie(1);
      expect(result[0].presoVisione).toBe(true);
      expect(result[1].presoVisione).toBe(false);
    });

    it('includes proprietari-only comunicazioni for a condomino of type proprietario', async () => {
      prisma.condomino.findUnique.mockResolvedValue({ id: 1, condominioId: 5, tipo: 'proprietario' });
      prisma.comunicazione.findMany.mockResolvedValue([]);

      await service.findMie(1);

      expect(prisma.comunicazione.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ where: { condominioId: 5, destinatariTipo: { in: ['tutti', 'proprietari'] } } }),
      );
    });

    it('includes inquilini-only comunicazioni for a condomino of type inquilino', async () => {
      prisma.condomino.findUnique.mockResolvedValue({ id: 1, condominioId: 5, tipo: 'inquilino' });
      prisma.comunicazione.findMany.mockResolvedValue([]);

      await service.findMie(1);

      expect(prisma.comunicazione.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ where: { condominioId: 5, destinatariTipo: { in: ['tutti', 'inquilini'] } } }),
      );
    });
  });

  describe('update', () => {
    it('recomputes destinatari when destinatariTipo changes', async () => {
      prisma.comunicazione.findUnique.mockResolvedValue({ id: 1, condominioId: 5, destinatariTipo: 'tutti' });
      prisma.condomino.count.mockResolvedValue(3);
      prisma.comunicazione.update.mockResolvedValue({ id: 1, destinatari: 3 });

      await service.update(1, { destinatariTipo: 'inquilini' });

      expect(prisma.condomino.count).toHaveBeenCalledWith({ where: { condominioId: 5, stato: 'attivo', tipo: 'inquilino' } });
      expect(prisma.comunicazione.update).toHaveBeenCalledWith(
        expect.objectContaining({ data: expect.objectContaining({ destinatari: 3, destinatariTipo: 'inquilini' }) }),
      );
    });

    it('does not recompute destinatari when destinatariTipo is unchanged', async () => {
      prisma.comunicazione.findUnique.mockResolvedValue({ id: 1, condominioId: 5, destinatariTipo: 'tutti' });
      prisma.comunicazione.update.mockResolvedValue({ id: 1, titolo: 'Nuovo' });

      await service.update(1, { titolo: 'Nuovo' });

      expect(prisma.condomino.count).not.toHaveBeenCalled();
    });
  });

  describe('presoVisione', () => {
    it('forbids marking a comunicazione from a different condominio', async () => {
      prisma.comunicazione.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.condomino.findUnique.mockResolvedValue({ id: 1, condominioId: 6 });
      await expect(service.presoVisione(1, 1)).rejects.toThrow(ForbiddenException);
    });

    it('upserts the lettura for a valid condomino', async () => {
      prisma.comunicazione.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.condomino.findUnique.mockResolvedValue({ id: 1, condominioId: 5 });
      prisma.comunicazioneLettura.upsert.mockResolvedValue({ comunicazioneId: 1, condominoId: 1 });

      await service.presoVisione(1, 1);

      expect(prisma.comunicazioneLettura.upsert).toHaveBeenCalledWith({
        where: { comunicazioneId_condominoId: { comunicazioneId: 1, condominoId: 1 } },
        create: { comunicazioneId: 1, condominoId: 1 },
        update: {},
      });
    });
  });
});

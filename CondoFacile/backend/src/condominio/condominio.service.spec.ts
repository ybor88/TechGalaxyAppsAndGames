import { BadRequestException, ConflictException, NotFoundException } from '@nestjs/common';
import { CondominioService } from './condominio.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    condominio: { findUnique: jest.fn(), findMany: jest.fn(), create: jest.fn(), update: jest.fn() },
    condomino: { findFirst: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn() },
    user: { findUnique: jest.fn(), update: jest.fn(), create: jest.fn(), findMany: jest.fn() },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('CondominioService', () => {
  let service: CondominioService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new CondominioService(prisma as unknown as PrismaService);
  });

  describe('addCondomino', () => {
    it('throws NotFoundException when the condominio does not exist', async () => {
      prisma.condominio.findUnique.mockResolvedValue(null);
      await expect(
        service.addCondomino(1, { nome: 'Mario', cognome: 'Rossi', unita: 'A1' }),
      ).rejects.toThrow(NotFoundException);
    });

    it('throws ConflictException when the unita is already assigned', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.condomino.findFirst.mockResolvedValue({ id: 99, unita: 'A1' });
      await expect(
        service.addCondomino(1, { nome: 'Mario', cognome: 'Rossi', unita: 'A1' }),
      ).rejects.toThrow(ConflictException);
    });

    it('throws BadRequestException when username is given without password and no existing user', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.condomino.findFirst.mockResolvedValue(null);
      prisma.user.findUnique.mockResolvedValue(null);
      await expect(
        service.addCondomino(1, { nome: 'Mario', cognome: 'Rossi', unita: 'A1', username: 'mario' }),
      ).rejects.toThrow(BadRequestException);
    });

    it('throws ConflictException when username already linked to another condomino', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.condomino.findFirst.mockResolvedValue(null);
      prisma.user.findUnique.mockResolvedValue({ id: 5, condominoId: 10 });
      await expect(
        service.addCondomino(1, { nome: 'Mario', cognome: 'Rossi', unita: 'A1', username: 'mario', password: 'x' }),
      ).rejects.toThrow(ConflictException);
    });

    it('creates the condomino and a new user when username+password are provided', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.condomino.findFirst.mockResolvedValue(null);
      prisma.user.findUnique.mockResolvedValue(null);
      prisma.condomino.create.mockResolvedValue({ id: 7 });
      prisma.user.create.mockResolvedValue({ id: 8 });
      prisma.condomino.findUnique.mockResolvedValue({ id: 7, user: { username: 'mario' } });

      const result = await service.addCondomino(1, {
        nome: 'Mario',
        cognome: 'Rossi',
        unita: 'A1',
        username: 'mario',
        password: 'secret',
      });

      expect(prisma.user.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ username: 'mario', role: 'CONDOMINO', condominoId: 7 }),
        }),
      );
      expect(result).toEqual({ id: 7, user: { username: 'mario' } });
    });

    it('links an existing unassociated user instead of creating a new one', async () => {
      prisma.condominio.findUnique.mockResolvedValue({ id: 1 });
      prisma.condomino.findFirst.mockResolvedValue(null);
      prisma.user.findUnique.mockResolvedValue({ id: 5, condominoId: null });
      prisma.condomino.create.mockResolvedValue({ id: 7 });
      prisma.condomino.findUnique.mockResolvedValue({ id: 7, user: { username: 'mario' } });

      await service.addCondomino(1, { nome: 'Mario', cognome: 'Rossi', unita: 'A1', username: 'mario' });

      expect(prisma.user.update).toHaveBeenCalledWith({ where: { id: 5 }, data: { condominoId: 7 } });
      expect(prisma.user.create).not.toHaveBeenCalled();
    });
  });

  describe('updateCondomino', () => {
    it('throws NotFoundException when condomino not found in this condominio', async () => {
      prisma.condomino.findFirst.mockResolvedValue(null);
      await expect(service.updateCondomino(1, 2, { nome: 'X' })).rejects.toThrow(NotFoundException);
    });

    it('throws ConflictException when new unita already used by another condomino', async () => {
      prisma.condomino.findFirst
        .mockResolvedValueOnce({ id: 2, unita: 'A1', condominioId: 1 })
        .mockResolvedValueOnce({ id: 3, unita: 'A2' });
      await expect(service.updateCondomino(1, 2, { unita: 'A2' })).rejects.toThrow(ConflictException);
    });

    it('updates fields, falling back to existing values when not provided', async () => {
      prisma.condomino.findFirst.mockResolvedValue({
        id: 2,
        condominioId: 1,
        nome: 'Old',
        cognome: 'Name',
        email: 'old@x.it',
        telefono: '123',
        unita: 'A1',
        millesimi: 10,
        tipo: 'proprietario',
      });
      prisma.condomino.update.mockResolvedValue({ id: 2, nome: 'New' });

      await service.updateCondomino(1, 2, { nome: 'New' });

      expect(prisma.condomino.update).toHaveBeenCalledWith({
        where: { id: 2 },
        data: {
          nome: 'New',
          cognome: 'Name',
          email: 'old@x.it',
          telefono: '123',
          unita: 'A1',
          millesimi: 10,
          tipo: 'proprietario',
        },
        include: { user: { select: { username: true } } },
      });
    });
  });

  describe('deactivateCondomino', () => {
    it('toggles stato between attivo and disattivo', async () => {
      prisma.condomino.findFirst.mockResolvedValue({ id: 2, condominioId: 1, stato: 'attivo' });
      prisma.condomino.update.mockResolvedValue({ id: 2, stato: 'disattivo' });

      await service.deactivateCondomino(1, 2);

      expect(prisma.condomino.update).toHaveBeenCalledWith({
        where: { id: 2 },
        data: { stato: 'disattivo' },
        include: { user: { select: { username: true } } },
      });
    });

    it('throws NotFoundException for unknown condomino', async () => {
      prisma.condomino.findFirst.mockResolvedValue(null);
      await expect(service.deactivateCondomino(1, 999)).rejects.toThrow(NotFoundException);
    });
  });
});

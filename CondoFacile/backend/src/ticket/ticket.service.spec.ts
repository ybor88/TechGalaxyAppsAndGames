import { BadRequestException, ForbiddenException, NotFoundException } from '@nestjs/common';
import { TicketService } from './ticket.service';
import { PrismaService } from '../prisma/prisma.service';

function createPrismaMock() {
  return {
    condomino: { findUnique: jest.fn() },
    ticket: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
    ticketNota: { create: jest.fn(), findMany: jest.fn() },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('TicketService', () => {
  let service: TicketService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new TicketService(prisma as unknown as PrismaService);
  });

  describe('create', () => {
    it('throws BadRequestException without condominioId and without apertoCondominoId', async () => {
      await expect(service.create({ titolo: 'X', categoria: 'idraulica' })).rejects.toThrow(BadRequestException);
    });

    it('derives condominioId from the opening condomino', async () => {
      prisma.condomino.findUnique.mockResolvedValue({ id: 3, condominioId: 7 });
      prisma.ticket.create.mockResolvedValue({ id: 1 });

      await service.create({ titolo: 'Guasto', categoria: 'idraulica', apertoCondominoId: 3 });

      expect(prisma.ticket.create).toHaveBeenCalledWith(
        expect.objectContaining({ data: expect.objectContaining({ condominioId: 7, apertoCondominoId: 3 }) }),
      );
    });

    it('throws NotFoundException when the opening condomino does not exist', async () => {
      prisma.condomino.findUnique.mockResolvedValue(null);
      await expect(
        service.create({ titolo: 'Guasto', categoria: 'idraulica', apertoCondominoId: 999 }),
      ).rejects.toThrow(NotFoundException);
    });

    it('rejects photos larger than ~2MB', async () => {
      const bigPhoto = 'a'.repeat(2_800_001);
      await expect(
        service.create({ titolo: 'Guasto', categoria: 'idraulica', condominioId: 1, foto: bigPhoto }),
      ).rejects.toThrow(BadRequestException);
    });

    it('trims title and description', async () => {
      prisma.ticket.create.mockResolvedValue({ id: 1 });
      await service.create({ titolo: '  Guasto  ', descrizione: '  dettagli  ', categoria: 'idraulica', condominioId: 1 });
      expect(prisma.ticket.create).toHaveBeenCalledWith(
        expect.objectContaining({ data: expect.objectContaining({ titolo: 'Guasto', descrizione: 'dettagli' }) }),
      );
    });
  });

  describe('update', () => {
    it('sets dataChiusura when moving to a closed state', async () => {
      prisma.ticket.findUnique.mockResolvedValue({ id: 1, stato: 'Aperta', priorita: 'media', assegnatoa: null, dataChiusura: null });
      prisma.ticket.update.mockResolvedValue({ id: 1, stato: 'Risolta' });

      await service.update(1, { stato: 'Risolta' });

      expect(prisma.ticket.update).toHaveBeenCalledWith(
        expect.objectContaining({ data: expect.objectContaining({ stato: 'Risolta', dataChiusura: expect.any(Date) }) }),
      );
    });

    it('throws NotFoundException for unknown ticket', async () => {
      prisma.ticket.findUnique.mockResolvedValue(null);
      await expect(service.update(1, { stato: 'Chiusa' })).rejects.toThrow(NotFoundException);
    });
  });

  describe('addNota', () => {
    it('rejects empty note text', async () => {
      prisma.ticket.findUnique.mockResolvedValue({ id: 1 });
      await expect(service.addNota(1, '   ', 'admin')).rejects.toThrow(BadRequestException);
    });

    it('creates a trimmed note', async () => {
      prisma.ticket.findUnique.mockResolvedValue({ id: 1 });
      prisma.ticketNota.create.mockResolvedValue({ id: 1, testo: 'Nota' });
      await service.addNota(1, '  Nota  ', 'admin');
      expect(prisma.ticketNota.create).toHaveBeenCalledWith({ data: { ticketId: 1, testo: 'Nota', autore: 'admin' } });
    });
  });

  describe('findNote', () => {
    it('forbids a condomino from reading notes on someone else ticket', async () => {
      prisma.ticket.findUnique.mockResolvedValue({ id: 1, apertoCondominoId: 5 });
      await expect(service.findNote(1, 6)).rejects.toThrow(ForbiddenException);
    });

    it('allows the owning condomino to read notes', async () => {
      prisma.ticket.findUnique.mockResolvedValue({ id: 1, apertoCondominoId: 5 });
      prisma.ticketNota.findMany.mockResolvedValue([{ id: 1, testo: 'ciao' }]);
      const result = await service.findNote(1, 5);
      expect(result).toHaveLength(1);
    });
  });
});

import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { DocumentoService } from './documento.service';
import { PrismaService } from '../prisma/prisma.service';

jest.mock('fs', () => ({
  createReadStream: jest.fn(() => 'stream'),
  existsSync: jest.fn(),
}));

import { existsSync } from 'fs';

function createPrismaMock() {
  return {
    condomino: { findUnique: jest.fn() },
    documento: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
    documentoVersione: { create: jest.fn(), findMany: jest.fn(), findUnique: jest.fn() },
  };
}

type MockPrisma = ReturnType<typeof createPrismaMock>;

describe('DocumentoService', () => {
  let service: DocumentoService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new DocumentoService(prisma as unknown as PrismaService);
    jest.clearAllMocks();
  });

  describe('findMiei', () => {
    it('throws NotFoundException for an unknown condomino', async () => {
      prisma.condomino.findUnique.mockResolvedValue(null);
      await expect(service.findMiei(1)).rejects.toThrow(NotFoundException);
    });

    it('filters documents based on visibilita and unita', async () => {
      prisma.condomino.findUnique.mockResolvedValue({ condominioId: 1, unita: 'A1' });
      prisma.documento.findMany.mockResolvedValue([
        { id: 1, visibilita: 'pubblica' },
        { id: 2, visibilita: 'privata' },
        { id: 3, visibilita: 'selettiva', unitaAccesso: 'A1, B2' },
        { id: 4, visibilita: 'selettiva', unitaAccesso: 'B2, C3' },
      ]);

      const result = await service.findMiei(1);

      expect(result.map((d) => d.id)).toEqual([1, 3]);
    });
  });

  describe('download', () => {
    it('forbids condomino from a different condominio', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, condominioId: 5, visibilita: 'pubblica' });
      prisma.condomino.findUnique.mockResolvedValue({ condominioId: 6, unita: 'A1' });
      await expect(service.download(1, 1)).rejects.toThrow(ForbiddenException);
    });

    it('forbids access to a private document for condomini', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, condominioId: 5, visibilita: 'privata' });
      prisma.condomino.findUnique.mockResolvedValue({ condominioId: 5, unita: 'A1' });
      await expect(service.download(1, 1)).rejects.toThrow(ForbiddenException);
    });

    it('throws NotFoundException when the file is missing on disk', async () => {
      prisma.documento.findUnique.mockResolvedValue({
        id: 1,
        condominioId: 5,
        visibilita: 'pubblica',
        filePath: 'uploads/documenti/x.pdf',
      });
      (existsSync as jest.Mock).mockReturnValue(false);
      await expect(service.download(1)).rejects.toThrow(NotFoundException);
    });
  });

  describe('update (nuova versione)', () => {
    it('archives the current file as a DocumentoVersione before overwriting it', async () => {
      prisma.documento.findUnique.mockResolvedValue({
        id: 1,
        versione: 2,
        filePath: 'uploads/documenti/old.pdf',
        fileSize: 111,
        mimeType: 'application/pdf',
      });
      prisma.documento.update.mockResolvedValue({ id: 1, versione: 3 });

      await service.update(1, { filePath: 'uploads/documenti/new.pdf', fileSize: 222, mimeType: 'application/pdf' });

      expect(prisma.documentoVersione.create).toHaveBeenCalledWith({
        data: { documentoId: 1, versione: 2, filePath: 'uploads/documenti/old.pdf', fileSize: 111, mimeType: 'application/pdf' },
      });
      expect(prisma.documento.update).toHaveBeenCalledWith({
        where: { id: 1 },
        data: expect.objectContaining({ versione: 3, filePath: 'uploads/documenti/new.pdf' }),
      });
    });

    it('does not archive anything when only metadata changes', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, versione: 2 });
      prisma.documento.update.mockResolvedValue({ id: 1, nome: 'Nuovo nome' });

      await service.update(1, { nome: 'Nuovo nome' });

      expect(prisma.documentoVersione.create).not.toHaveBeenCalled();
    });
  });

  describe('findVersioni', () => {
    it('forbids a condomino from another condominio', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, condominioId: 5, visibilita: 'pubblica' });
      prisma.condomino.findUnique.mockResolvedValue({ condominioId: 6, unita: 'A1' });
      await expect(service.findVersioni(1, 1)).rejects.toThrow(ForbiddenException);
    });

    it('returns archived versions ordered by versione desc', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, condominioId: 5, visibilita: 'pubblica' });
      prisma.documentoVersione.findMany.mockResolvedValue([{ id: 2, versione: 2 }, { id: 1, versione: 1 }]);

      const result = await service.findVersioni(1);

      expect(prisma.documentoVersione.findMany).toHaveBeenCalledWith({
        where: { documentoId: 1 },
        orderBy: { versione: 'desc' },
      });
      expect(result).toHaveLength(2);
    });
  });

  describe('downloadVersione', () => {
    it('throws NotFoundException when the version does not belong to the document', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, condominioId: 5, visibilita: 'pubblica' });
      prisma.documentoVersione.findUnique.mockResolvedValue({ id: 9, documentoId: 999 });
      await expect(service.downloadVersione(1, 9)).rejects.toThrow(NotFoundException);
    });

    it('streams the archived file when found on disk', async () => {
      prisma.documento.findUnique.mockResolvedValue({ id: 1, condominioId: 5, visibilita: 'pubblica', nome: 'Regolamento.pdf' });
      prisma.documentoVersione.findUnique.mockResolvedValue({
        id: 9,
        documentoId: 1,
        versione: 1,
        filePath: 'uploads/documenti/old.pdf',
        mimeType: 'application/pdf',
      });
      (existsSync as jest.Mock).mockReturnValue(true);

      const result = await service.downloadVersione(1, 9);

      expect(result.nome).toBe('v1-Regolamento.pdf');
      expect(result.mimeType).toBe('application/pdf');
    });
  });
});

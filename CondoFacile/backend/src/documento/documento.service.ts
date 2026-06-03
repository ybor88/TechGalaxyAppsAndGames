import {
  ForbiddenException,
  Injectable,
  NotFoundException,
  StreamableFile,
} from '@nestjs/common';
import { createReadStream, existsSync } from 'fs';
import { join } from 'path';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class DocumentoService {
  constructor(private prisma: PrismaService) {}

  // ── Admin: tutti i documenti di un condominio ──────────────────────────────

  async findAll(
    condominioId: number,
    filters?: { categoria?: string; search?: string },
  ) {
    const where: Record<string, unknown> = { condominioId };
    if (filters?.categoria && filters.categoria !== 'tutti') {
      where['categoria'] = filters.categoria;
    }
    if (filters?.search) {
      where['OR'] = [
        { nome: { contains: filters.search } },
        { tag: { contains: filters.search } },
      ];
    }
    return this.prisma.documento.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });
  }

  // ── Condomino: documenti visibili per lui ──────────────────────────────────

  async findMiei(condominoId: number) {
    const condomino = await this.prisma.condomino.findUnique({
      where: { id: condominoId },
      select: { condominioId: true, unita: true },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');

    const all = await this.prisma.documento.findMany({
      where: { condominioId: condomino.condominioId },
      orderBy: { createdAt: 'desc' },
    });

    // Filtra in base alla visibilità
    return all.filter((d) => {
      if (d.visibilita === 'pubblica') return true;
      if (d.visibilita === 'privata') return false;
      if (d.visibilita === 'selettiva') {
        const unita = d.unitaAccesso.split(',').map((u) => u.trim());
        return unita.includes(condomino.unita);
      }
      return false;
    });
  }

  // ── Crea documento (admin) ─────────────────────────────────────────────────

  async create(data: {
    nome: string;
    categoria: string;
    tag?: string;
    visibilita?: string;
    unitaAccesso?: string;
    filePath: string;
    fileSize: number;
    mimeType: string;
    condominioId: number;
  }) {
    return this.prisma.documento.create({ data: {
      nome: data.nome,
      categoria: data.categoria,
      tag: data.tag ?? '',
      visibilita: data.visibilita ?? 'pubblica',
      unitaAccesso: data.unitaAccesso ?? '',
      filePath: data.filePath,
      fileSize: data.fileSize,
      mimeType: data.mimeType,
      condominioId: data.condominioId,
    }});
  }

  // ── Aggiorna metadati documento (admin) ────────────────────────────────────

  async update(
    id: number,
    data: {
      nome?: string;
      categoria?: string;
      tag?: string;
      visibilita?: string;
      unitaAccesso?: string;
      filePath?: string;
      fileSize?: number;
      mimeType?: string;
    },
  ) {
    await this.findOrFail(id);
    const updateData: Record<string, unknown> = { ...data };
    if (data.filePath) {
      // Nuova versione del file: incrementa versione
      const current = await this.prisma.documento.findUnique({ where: { id } });
      updateData['versione'] = (current?.versione ?? 1) + 1;
    }
    return this.prisma.documento.update({ where: { id }, data: updateData });
  }

  // ── Elimina documento (admin) ──────────────────────────────────────────────

  async remove(id: number) {
    await this.findOrFail(id);
    return this.prisma.documento.delete({ where: { id } });
  }

  // ── Download file ──────────────────────────────────────────────────────────

  async download(id: number, condominoId?: number): Promise<{ file: StreamableFile; nome: string; mimeType: string }> {
    const doc = await this.findOrFail(id);

    // Verifica accesso condòmino
    if (condominoId !== undefined) {
      const condomino = await this.prisma.condomino.findUnique({
        where: { id: condominoId },
        select: { condominioId: true, unita: true },
      });
      if (!condomino || condomino.condominioId !== doc.condominioId) {
        throw new ForbiddenException('Non autorizzato');
      }
      if (doc.visibilita === 'privata') throw new ForbiddenException('Documento privato');
      if (doc.visibilita === 'selettiva') {
        const unita = doc.unitaAccesso.split(',').map((u) => u.trim());
        if (!unita.includes(condomino.unita)) throw new ForbiddenException('Non autorizzato');
      }
    }

    const fullPath = join(process.cwd(), doc.filePath);
    if (!existsSync(fullPath)) throw new NotFoundException('File non trovato sul server');

    const stream = createReadStream(fullPath);
    return { file: new StreamableFile(stream), nome: doc.nome, mimeType: doc.mimeType };
  }

  // ── Utility ───────────────────────────────────────────────────────────────

  private async findOrFail(id: number) {
    const doc = await this.prisma.documento.findUnique({ where: { id } });
    if (!doc) throw new NotFoundException('Documento non trovato');
    return doc;
  }
}

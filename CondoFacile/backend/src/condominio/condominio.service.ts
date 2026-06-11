import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import * as bcrypt from 'bcryptjs';
import { PrismaService } from '../prisma/prisma.service';

interface AddCondominoDto {
  nome: string;
  cognome: string;
  email?: string;
  telefono?: string;
  unita: string;
  millesimi?: number;
  tipo?: string;
  username?: string;
  password?: string;
}

interface UpdateCondominoDto {
  nome?: string;
  cognome?: string;
  email?: string;
  telefono?: string;
  unita?: string;
  millesimi?: number;
  tipo?: string;
}

@Injectable()
export class CondominioService {
  constructor(private prisma: PrismaService) {}

  findAll() {
    return this.prisma.condominio.findMany({
      include: { _count: { select: { condomini: true } } },
      orderBy: { createdAt: 'desc' },
    });
  }

  create(nome: string, indirizzo: string) {
    return this.prisma.condominio.create({ data: { nome, indirizzo } });
  }

  async update(id: number, nome: string, indirizzo: string) {
    const condo = await this.prisma.condominio.findUnique({ where: { id } });
    if (!condo) throw new NotFoundException('Condominio non trovato');
    return this.prisma.condominio.update({ where: { id }, data: { nome, indirizzo } });
  }

  async findOne(id: number) {
    const condo = await this.prisma.condominio.findUnique({
      where: { id },
      include: {
        condomini: {
          include: { user: { select: { username: true } } },
          orderBy: { cognome: 'asc' },
        },
        _count: { select: { condomini: true } },
      },
    });
    if (!condo) throw new NotFoundException('Condominio non trovato');
    return condo;
  }

  async getUsersNonAssociati() {
    return this.prisma.user.findMany({
      where: { condominoId: null, role: 'CONDOMINO' },
      select: { id: true, username: true },
      orderBy: { username: 'asc' },
    });
  }

  async addCondomino(condominioId: number, data: AddCondominoDto) {
    const condo = await this.prisma.condominio.findUnique({ where: { id: condominioId } });
    if (!condo) throw new NotFoundException('Condominio non trovato');

    let existingUser: { id: number; condominoId: number | null } | null = null;
    if (data.username) {
      existingUser = await this.prisma.user.findUnique({ where: { username: data.username } });
      if (existingUser && existingUser.condominoId !== null) {
        throw new ConflictException(`Username "${data.username}" già in uso`);
      }
      // existingUser without condominoId will be linked below
      if (!existingUser && !data.password) {
        throw new BadRequestException('Password obbligatoria se si specifica un username');
      }
    }

    const condomino = await this.prisma.condomino.create({
      data: {
        nome: data.nome,
        cognome: data.cognome,
        email: data.email ?? null,
        telefono: data.telefono ?? null,
        unita: data.unita,
        millesimi: data.millesimi ?? 0,
        tipo: data.tipo ?? 'proprietario',
        condominioId,
      },
    });

    if (data.username) {
      if (existingUser) {
        // Link existing unassociated user
        await this.prisma.user.update({
          where: { id: existingUser.id },
          data: { condominoId: condomino.id },
        });
      } else if (data.password) {
        const passwordHash = await bcrypt.hash(data.password, 10);
        await this.prisma.user.create({
          data: {
            username: data.username,
            passwordHash,
            role: 'CONDOMINO',
            condominoId: condomino.id,
          },
        });
      }
    }

    return this.prisma.condomino.findUnique({
      where: { id: condomino.id },
      include: { user: { select: { username: true } } },
    });
  }

  async updateCondomino(condominioId: number, condominoId: number, data: UpdateCondominoDto) {
    const condomino = await this.prisma.condomino.findFirst({
      where: { id: condominoId, condominioId },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');

    return this.prisma.condomino.update({
      where: { id: condominoId },
      data: {
        nome: data.nome ?? condomino.nome,
        cognome: data.cognome ?? condomino.cognome,
        email: data.email !== undefined ? (data.email || null) : condomino.email,
        telefono: data.telefono !== undefined ? (data.telefono || null) : condomino.telefono,
        unita: data.unita ?? condomino.unita,
        millesimi: data.millesimi !== undefined ? data.millesimi : condomino.millesimi,
        tipo: data.tipo ?? condomino.tipo,
      },
      include: { user: { select: { username: true } } },
    });
  }

  async deactivateCondomino(condominioId: number, condominoId: number) {
    const condomino = await this.prisma.condomino.findFirst({
      where: { id: condominoId, condominioId },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');

    return this.prisma.condomino.update({
      where: { id: condominoId },
      data: { stato: condomino.stato === 'attivo' ? 'disattivo' : 'attivo' },
      include: { user: { select: { username: true } } },
    });
  }
}

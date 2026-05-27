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
  username?: string;
  password?: string;
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

  async addCondomino(condominioId: number, data: AddCondominoDto) {
    const condo = await this.prisma.condominio.findUnique({ where: { id: condominioId } });
    if (!condo) throw new NotFoundException('Condominio non trovato');

    if (data.username && !data.password) {
      throw new BadRequestException('Password obbligatoria se si specifica un username');
    }

    if (data.username) {
      const existing = await this.prisma.user.findUnique({ where: { username: data.username } });
      if (existing) throw new ConflictException(`Username "${data.username}" già in uso`);
    }

    const condomino = await this.prisma.condomino.create({
      data: {
        nome: data.nome,
        cognome: data.cognome,
        email: data.email ?? null,
        telefono: data.telefono ?? null,
        unita: data.unita,
        millesimi: data.millesimi ?? 0,
        condominioId,
      },
    });

    if (data.username && data.password) {
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

    return this.prisma.condomino.findUnique({
      where: { id: condomino.id },
      include: { user: { select: { username: true } } },
    });
  }
}

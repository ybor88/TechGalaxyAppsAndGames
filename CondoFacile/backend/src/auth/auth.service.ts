import { Injectable, UnauthorizedException } from '@nestjs/common';
import * as bcrypt from 'bcryptjs';
import * as jwt from 'jsonwebtoken';
import { PrismaService } from '../prisma/prisma.service';

const JWT_SECRET = process.env.JWT_SECRET ?? 'condofacile-secret-2026';

@Injectable()
export class AuthService {
  constructor(private prisma: PrismaService) {}

  async login(username: string, password: string) {
    const user = await this.prisma.user.findUnique({ where: { username } });
    if (!user) throw new UnauthorizedException('Credenziali non valide');

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) throw new UnauthorizedException('Credenziali non valide');

    const payload = { sub: user.id, username: user.username, role: user.role, condominoId: user.condominoId };
    const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '8h' });

    return {
      token,
      user: {
        id: user.id,
        username: user.username,
        role: user.role,
        condominoId: user.condominoId,
        profilePhoto: user.profilePhoto ?? null,
      },
    };
  }

  verifyToken(token: string) {
    try {
      return jwt.verify(token, JWT_SECRET) as unknown as {
        sub: number;
        username: string;
        role: string;
        condominoId: number | null;
      };
    } catch {
      throw new UnauthorizedException('Token non valido o scaduto');
    }
  }

  async getMe(userId: number) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) throw new UnauthorizedException('Utente non trovato');
    return {
      id: user.id,
      username: user.username,
      role: user.role,
      condominoId: user.condominoId,
      profilePhoto: user.profilePhoto ?? null,
    };
  }

  async updateProfilePhoto(userId: number, base64: string) {
    // Limite ~2MB in base64
    if (base64.length > 2_800_000) {
      throw new Error('Immagine troppo grande (max 2MB)');
    }
    const user = await this.prisma.user.update({
      where: { id: userId },
      data: { profilePhoto: base64 },
    });
    return { profilePhoto: user.profilePhoto };
  }

  async changePassword(userId: number, currentPassword: string, newPassword: string) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) throw new UnauthorizedException('Utente non trovato');
    const valid = await bcrypt.compare(currentPassword, user.passwordHash);
    if (!valid) throw new UnauthorizedException('Password attuale non corretta');
    const hash = await bcrypt.hash(newPassword, 10);
    await this.prisma.user.update({ where: { id: userId }, data: { passwordHash: hash } });
    return { message: 'Password aggiornata con successo' };
  }

  static async hashPassword(password: string): Promise<string> {
    return bcrypt.hash(password, 10);
  }
}

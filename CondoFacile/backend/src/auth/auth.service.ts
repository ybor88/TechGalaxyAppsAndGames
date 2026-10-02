import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import * as bcrypt from 'bcryptjs';
import { createHash } from 'crypto';
import * as jwt from 'jsonwebtoken';
import { PrismaService } from '../prisma/prisma.service';

const JWT_SECRET = process.env.JWT_SECRET ?? 'condofacile-secret-2026';

// Solo indirizzi Gmail: garantisce che il recupero password via email arrivi sempre
const GMAIL_REGEX = /^[a-z0-9._%+-]+@gmail\.com$/i;

export function isGmail(email: string): boolean {
  return GMAIL_REGEX.test(email.trim());
}

export function hashResetToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

interface RegisterDto {
  nome: string;
  cognome: string;
  email: string;
  telefono?: string;
  unitaRichiesta: string;
  username: string;
  password: string;
}

@Injectable()
export class AuthService {
  constructor(private prisma: PrismaService) {}

  async login(username: string, password: string) {
    const user = await this.prisma.user.findUnique({ where: { username } });
    if (!user) throw new UnauthorizedException('Credenziali non valide');

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) throw new UnauthorizedException('Credenziali non valide');

    if (user.stato === 'in_attesa') {
      throw new UnauthorizedException(
        'Il tuo account è in attesa di approvazione da parte dell\'amministratore.',
      );
    }

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

  async register(dto: RegisterDto) {
    const existing = await this.prisma.user.findUnique({ where: { username: dto.username } });
    if (existing) {
      throw new ConflictException('Username già in uso, scegline un altro');
    }

    const email = dto.email.trim().toLowerCase();
    if (!isGmail(email)) {
      throw new BadRequestException('È richiesto un indirizzo Gmail (es. mario.rossi@gmail.com)');
    }
    const emailInUso = await this.prisma.user.findFirst({ where: { email } });
    if (emailInUso) {
      throw new ConflictException('Questo indirizzo email è già associato a un altro account');
    }

    const passwordHash = await bcrypt.hash(dto.password, 10);
    await this.prisma.user.create({
      data: {
        username: dto.username,
        passwordHash,
        role: 'CONDOMINO',
        stato: 'in_attesa',
        nome: dto.nome,
        cognome: dto.cognome,
        email,
        telefono: dto.telefono ?? null,
        unitaRichiesta: dto.unitaRichiesta,
      },
    });

    return {
      message:
        "Richiesta inviata! L'amministratore dovrà approvare il tuo account prima che tu possa accedere.",
    };
  }

  async requestPasswordReset(identifier: string) {
    const term = identifier.trim().toLowerCase();
    const users = await this.prisma.user.findMany({ include: { condomino: true } });
    const user = users.find(
      (u) =>
        u.username.toLowerCase() === term ||
        (u.email && u.email.toLowerCase() === term) ||
        (u.condomino?.email && u.condomino.email.toLowerCase() === term),
    );

    if (!user) {
      throw new NotFoundException(
        "Nessun account trovato con questo username o email. Se non hai ancora un account, registrati.",
      );
    }

    await this.prisma.user.update({
      where: { id: user.id },
      data: { resetPasswordRichiesto: true, resetPasswordRichiestoAt: new Date() },
    });

    return {
      message:
        "Richiesta inviata! Quando l'amministratore la approverà riceverai un'email con il tuo username e il link per scegliere una nuova password.",
    };
  }

  async confirmPasswordReset(token: string, newPassword: string) {
    const user = await this.prisma.user.findUnique({ where: { resetTokenHash: hashResetToken(token) } });
    if (!user || !user.resetTokenScadenza || user.resetTokenScadenza < new Date()) {
      throw new BadRequestException(
        'Link non valido o scaduto. Richiedi di nuovo il recupero credenziali.',
      );
    }

    const passwordHash = await bcrypt.hash(newPassword, 10);
    await this.prisma.user.update({
      where: { id: user.id },
      data: {
        passwordHash,
        resetTokenHash: null,
        resetTokenScadenza: null,
        resetPasswordRichiesto: false,
        resetPasswordRichiestoAt: null,
      },
    });

    return { message: 'Password aggiornata! Ora puoi accedere con le nuove credenziali.', username: user.username };
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
      throw new BadRequestException('Immagine troppo grande (max 2MB)');
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

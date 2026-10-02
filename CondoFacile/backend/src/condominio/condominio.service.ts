import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import * as bcrypt from 'bcryptjs';
import { randomBytes } from 'crypto';
import { PrismaService } from '../prisma/prisma.service';
import { LOGO_CID, MailService } from '../mail/mail.service';
import { hashResetToken } from '../auth/auth.service';

const RESET_LINK_VALIDITA_MS = 60 * 60 * 1000; // 1 ora

function frontendUrl(): string {
  return (process.env.FRONTEND_URL ?? 'http://localhost:3000').replace(/\/$/, '');
}

function emailButton(href: string, label: string): string {
  return `<p><a href="${href}" style="display:inline-block;background:#c0392b;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:bold">${label}</a></p>`;
}

function emailHtml(titolo: string, corpo: string[]): string {
  return [
    '<div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#222">',
    `<div style="text-align:center;margin-bottom:8px"><img src="cid:${LOGO_CID}" alt="CondoFacile" width="240" style="width:240px;max-width:100%;height:auto;border:0"></div>`,
    `<h2 style="color:#c0392b;text-align:center;margin-top:0">${titolo}</h2>`,
    ...corpo,
    '<p style="font-size:11px;color:#bbb">© Roberto Di Flumeri</p>',
    '</div>',
  ].join('\n');
}

function escapeHtml(v: string): string {
  return v.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

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
  constructor(
    private prisma: PrismaService,
    private mail: MailService,
  ) {}

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
          include: {
            user: {
              select: { username: true, resetPasswordRichiesto: true },
            },
          },
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
      select: {
        id: true,
        username: true,
        nome: true,
        cognome: true,
        email: true,
        telefono: true,
        unitaRichiesta: true,
      },
      orderBy: { username: 'asc' },
    });
  }

  async getRichiesteResetPassword() {
    return this.prisma.user.findMany({
      where: { resetPasswordRichiesto: true, role: 'CONDOMINO' },
      select: {
        id: true,
        username: true,
        resetPasswordRichiestoAt: true,
        email: true,
        condomino: {
          select: {
            id: true,
            email: true,
            nome: true,
            cognome: true,
            unita: true,
            condominioId: true,
            condominio: { select: { id: true, nome: true } },
          },
        },
      },
      orderBy: { resetPasswordRichiestoAt: 'asc' },
    });
  }

  async addCondomino(condominioId: number, data: AddCondominoDto) {
    const condo = await this.prisma.condominio.findUnique({ where: { id: condominioId } });
    if (!condo) throw new NotFoundException('Condominio non trovato');

    const unitaInUso = await this.prisma.condomino.findFirst({
      where: { condominioId, unita: data.unita },
    });
    if (unitaInUso) {
      throw new ConflictException(`L'unità "${data.unita}" è già assegnata ad un altro condòmino`);
    }

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
        // Link existing unassociated user (approva la richiesta di registrazione)
        await this.prisma.user.update({
          where: { id: existingUser.id },
          data: { condominoId: condomino.id, stato: 'attivo' },
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

    if (data.unita && data.unita !== condomino.unita) {
      const unitaInUso = await this.prisma.condomino.findFirst({
        where: { condominioId, unita: data.unita, NOT: { id: condominoId } },
      });
      if (unitaInUso) {
        throw new ConflictException(`L'unità "${data.unita}" è già assegnata ad un altro condòmino`);
      }
    }

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

  async resetPasswordCondomino(condominioId: number, condominoId: number, newPassword: string) {
    const condomino = await this.prisma.condomino.findFirst({
      where: { id: condominoId, condominioId },
      include: { user: true },
    });
    if (!condomino) throw new NotFoundException('Condòmino non trovato');
    if (!condomino.user) {
      throw new BadRequestException('Questo condòmino non ha un account utente associato');
    }

    const passwordHash = await bcrypt.hash(newPassword, 10);
    await this.prisma.user.update({
      where: { id: condomino.user.id },
      data: {
        passwordHash,
        resetPasswordRichiesto: false,
        resetPasswordRichiestoAt: null,
        resetTokenHash: null,
        resetTokenScadenza: null,
      },
    });

    return { message: 'Password reimpostata con successo' };
  }

  /** L'amministratore approva la richiesta: invia al condòmino username + link monouso per la nuova password. */
  async approvaResetPassword(userId: number) {
    if (!this.mail.configurato) {
      throw new BadRequestException(
        'Invio email non configurato: imposta SMTP_USER e SMTP_PASS in backend/.env e riavvia il backend. La richiesta resta in attesa.',
      );
    }

    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: { condomino: true },
    });
    if (!user) throw new NotFoundException('Utente non trovato');
    if (!user.resetPasswordRichiesto) {
      throw new BadRequestException('Nessuna richiesta di recupero credenziali in attesa per questo utente');
    }

    const email = user.email ?? user.condomino?.email;
    if (!email) {
      throw new BadRequestException(
        "L'utente non ha un indirizzo email: reimposta la password manualmente e comunicagliela.",
      );
    }

    const token = randomBytes(32).toString('hex');
    await this.prisma.user.update({
      where: { id: user.id },
      data: {
        resetTokenHash: hashResetToken(token),
        resetTokenScadenza: new Date(Date.now() + RESET_LINK_VALIDITA_MS),
        resetPasswordRichiesto: false,
        resetPasswordRichiestoAt: null,
      },
    });

    const link = `${frontendUrl()}/reimposta-password?token=${token}`;
    const nome = user.condomino?.nome ?? user.nome ?? user.username;

    const text = [
      `Ciao ${nome},`,
      '',
      "l'amministratore ha approvato la tua richiesta di recupero credenziali.",
      '',
      `Username: ${user.username}`,
      'Imposta una nuova password da questo link (valido 1 ora, utilizzabile una sola volta):',
      link,
      '',
      'Se non hai richiesto tu il recupero, ignora questa email.',
      '',
      'CondoFacile',
    ].join('\n');
    const html = emailHtml('Recupero credenziali', [
      `<p>Ciao ${escapeHtml(nome)},</p>`,
      "<p>l'amministratore ha approvato la tua richiesta di recupero credenziali.</p>",
      `<p><strong>Username:</strong> ${escapeHtml(user.username)}</p>`,
      '<p>Per scegliere una nuova password clicca qui (link valido 1 ora, utilizzabile una sola volta):</p>',
      emailButton(link, 'Imposta nuova password'),
      `<p style="font-size:12px;color:#888">Se il pulsante non funziona copia questo indirizzo nel browser:<br>${link}</p>`,
      '<p style="font-size:12px;color:#888">Se non hai richiesto tu il recupero, ignora questa email.</p>',
    ]);

    try {
      await this.mail.send(email, 'CondoFacile – Recupero credenziali', html, text);
    } catch {
      // Ripristina la richiesta così l'amministratore può riprovare
      await this.prisma.user.update({
        where: { id: user.id },
        data: {
          resetTokenHash: null,
          resetTokenScadenza: null,
          resetPasswordRichiesto: true,
          resetPasswordRichiestoAt: user.resetPasswordRichiestoAt,
        },
      });
      throw new BadRequestException(
        "Invio dell'email non riuscito: controlla SMTP_USER/SMTP_PASS in backend/.env. La richiesta resta in attesa.",
      );
    }

    return { message: `Richiesta approvata: email di recupero inviata a ${email}` };
  }

  /**
   * L'amministratore approva una registrazione: crea il condòmino nell'unità scelta e collega l'account,
   * che diventa attivo con username e password scelti dall'utente in fase di registrazione.
   */
  async approvaRegistrazione(
    userId: number,
    data: { condominioId: number; unita: string; millesimi?: number; tipo?: string },
  ) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user || user.role !== 'CONDOMINO') throw new NotFoundException('Richiesta di registrazione non trovata');
    if (user.condominoId !== null) {
      throw new ConflictException('Questo account è già collegato a un condòmino');
    }

    await this.addCondomino(data.condominioId, {
      nome: user.nome ?? user.username,
      cognome: user.cognome ?? '',
      email: user.email ?? undefined,
      telefono: user.telefono ?? undefined,
      unita: data.unita,
      millesimi: data.millesimi,
      tipo: data.tipo,
      username: user.username, // collega l'account esistente: la password resta quella della registrazione
    });

    if (!user.email) {
      return { message: `Registrazione approvata: @${user.username} ora può accedere.`, emailInviata: false };
    }

    const loginUrl = `${frontendUrl()}/login`;
    const nome = user.nome ?? user.username;
    const text = [
      `Ciao ${nome},`,
      '',
      "l'amministratore ha approvato la tua registrazione a CondoFacile.",
      '',
      `Puoi accedere da ${loginUrl} con lo username "${user.username}" e la password che hai scelto durante la registrazione.`,
      '',
      'CondoFacile',
    ].join('\n');
    const html = emailHtml('Registrazione approvata', [
      `<p>Ciao ${escapeHtml(nome)},</p>`,
      "<p>l'amministratore ha approvato la tua registrazione a CondoFacile.</p>",
      `<p>Accedi con lo username <strong>${escapeHtml(user.username)}</strong> e la password che hai scelto durante la registrazione.</p>`,
      emailButton(loginUrl, 'Accedi a CondoFacile'),
    ]);

    try {
      await this.mail.send(user.email, 'CondoFacile – Registrazione approvata', html, text);
      return {
        message: `Registrazione approvata: @${user.username} ora può accedere. Email di conferma inviata a ${user.email}.`,
        emailInviata: true,
      };
    } catch {
      // L'account resta attivo: l'email è solo una notifica
      return {
        message: `Registrazione approvata: @${user.username} ora può accedere. Email di conferma NON inviata (SMTP non configurato o non funzionante).`,
        emailInviata: false,
      };
    }
  }

  /**
   * L'amministratore rifiuta/elimina un account condòmino non ancora collegato a un'unità
   * (registrazione in attesa o account rimasto orfano): l'account viene eliminato.
   */
  async rifiutaRegistrazione(userId: number) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user || user.role !== 'CONDOMINO') {
      throw new NotFoundException('Account non trovato: aggiorna la pagina');
    }
    if (user.condominoId !== null) {
      throw new ConflictException(
        "Questo account è già collegato a un condòmino: gestiscilo da Anagrafica.",
      );
    }
    await this.prisma.user.delete({ where: { id: userId } });
    return { message: `Registrazione di @${user.username} rifiutata` };
  }
}

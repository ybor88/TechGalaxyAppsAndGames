// © Roberto Di Flumeri
import { Injectable, Logger } from '@nestjs/common';
import * as nodemailer from 'nodemailer';
import { existsSync } from 'fs';
import { join } from 'path';

/** Content-ID del logo incorporato: nell'HTML usare <img src="cid:${LOGO_CID}">. */
export const LOGO_CID = 'logo@condofacile';

// backend/assets/logo-email.jpg (avvio da backend/, oppure da src/mail o dist/src/mail)
const LOGO_PATH = [
  join(process.cwd(), 'assets', 'logo-email.jpg'),
  join(__dirname, '..', '..', 'assets', 'logo-email.jpg'),
  join(__dirname, '..', '..', '..', 'assets', 'logo-email.jpg'),
].find((p) => existsSync(p));

/**
 * Invio email tramite SMTP Gmail (password per le app).
 * Variabili in backend/.env: SMTP_USER, SMTP_PASS, opzionali SMTP_HOST, SMTP_PORT, MAIL_FROM.
 * Se SMTP_USER/SMTP_PASS mancano, l'invio fallisce con un errore esplicito (mai un falso "inviata").
 */
@Injectable()
export class MailService {
  private readonly logger = new Logger(MailService.name);
  private transporter: nodemailer.Transporter | null = null;

  constructor() {
    const user = process.env.SMTP_USER;
    const pass = process.env.SMTP_PASS;
    if (user && pass) {
      const port = Number(process.env.SMTP_PORT ?? 465);
      this.transporter = nodemailer.createTransport({
        host: process.env.SMTP_HOST ?? 'smtp.gmail.com',
        port,
        secure: port === 465,
        auth: { user, pass },
      });
    } else {
      this.logger.warn('SMTP_USER/SMTP_PASS non configurati in backend/.env: invio email disattivato.');
    }
  }

  get configurato(): boolean {
    return this.transporter !== null;
  }

  async send(to: string, subject: string, html: string, text: string) {
    if (!this.transporter) {
      throw new Error('SMTP non configurato');
    }
    try {
      await this.transporter.sendMail({
        from: process.env.MAIL_FROM ?? `CondoFacile <${process.env.SMTP_USER}>`,
        to,
        subject,
        html,
        text,
        attachments: LOGO_PATH
          ? [{ filename: 'condofacile.jpg', path: LOGO_PATH, cid: LOGO_CID }]
          : [],
      });
    } catch (err) {
      this.logger.error(`Invio email a ${to} fallito: ${(err as Error).message}`);
      throw err;
    }
    this.logger.log(`Email "${subject}" inviata a ${to}`);
  }
}

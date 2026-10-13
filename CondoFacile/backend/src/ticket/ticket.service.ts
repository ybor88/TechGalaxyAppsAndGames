import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { CATEGORIE_TICKET, PRIORITA_TICKET, STATI_TICKET, valoreAmmesso } from '../common/validazione';

const INCLUDE_TICKET = {
  apertoCondomino: { select: { nome: true, cognome: true, unita: true } },
  note: { orderBy: { createdAt: 'asc' as const } },
  _count: { select: { note: true } },
};

/** Utente autenticato che effettua la richiesta (dal JWT). */
export interface Richiedente {
  role: string;
  condominoId: number | null;
}

const isAdmin = (r?: Richiedente) => r?.role === 'AMMINISTRATORE';

@Injectable()
export class TicketService {
  constructor(private prisma: PrismaService) {}

  // ── Admin: tutti i ticket di un condominio ─────────────────────────────────

  async findAll(
    condominioId: number,
    filters?: { stato?: string; priorita?: string; categoria?: string },
  ) {
    const where: Record<string, unknown> = { condominioId };
    if (filters?.stato && filters.stato !== 'tutti') where['stato'] = filters.stato;
    if (filters?.priorita && filters.priorita !== 'tutti') where['priorita'] = filters.priorita;
    if (filters?.categoria && filters.categoria !== 'tutti') where['categoria'] = filters.categoria;

    return this.prisma.ticket.findMany({
      where,
      include: INCLUDE_TICKET,
      orderBy: [{ dataApertura: 'desc' }],
    });
  }

  // ── Condomino: i propri ticket ─────────────────────────────────────────────

  async findMiei(apertoCondominoId: number) {
    return this.prisma.ticket.findMany({
      where: { apertoCondominoId },
      include: INCLUDE_TICKET,
      orderBy: { dataApertura: 'desc' },
    });
  }

  // ── Dettaglio ticket ───────────────────────────────────────────────────────

  async findOne(id: number, richiedente?: Richiedente) {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id },
      include: INCLUDE_TICKET,
    });
    if (!ticket) throw new NotFoundException('Ticket non trovato');
    // Il condòmino vede solo i ticket che ha aperto (contengono note interne e foto)
    if (richiedente && !isAdmin(richiedente) && ticket.apertoCondominoId !== richiedente.condominoId) {
      throw new ForbiddenException('Non autorizzato');
    }
    return ticket;
  }

  // ── Crea ticket ────────────────────────────────────────────────────────────

  async create(data: {
    titolo: string;
    descrizione?: string;
    categoria: string;
    priorita?: string;
    foto?: string | null;
    condominioId?: number;
    apertoCondominoId?: number | null;
  }, richiedente?: Richiedente) {
    if (richiedente && !isAdmin(richiedente)) {
      // Un condòmino apre ticket solo nel proprio condominio: condominioId dal body ignorato
      if (!richiedente.condominoId) {
        throw new ForbiddenException('Account non associato ad alcuna unità condominiale');
      }
      data = { ...data, condominioId: undefined, apertoCondominoId: richiedente.condominoId };
    }
    let condominioId = data.condominioId;

    // Se aperto da un condomino, ricava il condominioId dalla sua anagrafica
    if (!condominioId && data.apertoCondominoId) {
      const condomino = await this.prisma.condomino.findUnique({
        where: { id: data.apertoCondominoId },
      });
      if (!condomino) throw new NotFoundException('Condòmino non trovato');
      condominioId = condomino.condominioId;
    }

    if (!condominioId) throw new BadRequestException('condominioId obbligatorio');

    valoreAmmesso('categoria', data.categoria, CATEGORIE_TICKET);
    valoreAmmesso('priorita', data.priorita, PRIORITA_TICKET);
    if (data.foto && data.foto.length > 2_800_000) {
      throw new BadRequestException('Immagine troppo grande (max ~2MB)');
    }

    return this.prisma.ticket.create({
      data: {
        titolo: data.titolo.trim(),
        descrizione: data.descrizione?.trim() ?? '',
        categoria: data.categoria,
        priorita: data.priorita ?? 'media',
        foto: data.foto ?? null,
        condominioId,
        apertoCondominoId: data.apertoCondominoId ?? null,
      },
      include: INCLUDE_TICKET,
    });
  }

  // ── Admin: aggiorna ticket ─────────────────────────────────────────────────

  async update(
    id: number,
    data: {
      stato?: string;
      priorita?: string;
      assegnatoa?: string | null;
    },
  ) {
    valoreAmmesso('stato', data?.stato, STATI_TICKET);
    valoreAmmesso('priorita', data?.priorita, PRIORITA_TICKET);
    const ticket = await this.prisma.ticket.findUnique({ where: { id } });
    if (!ticket) throw new NotFoundException('Ticket non trovato');

    const nuovoStato = data.stato ?? ticket.stato;
    const isChiuso = nuovoStato === 'Chiusa' || nuovoStato === 'Risolta';
    const dataChiusura =
      isChiuso && !ticket.dataChiusura ? new Date() : ticket.dataChiusura;

    return this.prisma.ticket.update({
      where: { id },
      data: {
        stato: nuovoStato,
        priorita: data.priorita ?? ticket.priorita,
        assegnatoa: data.assegnatoa !== undefined ? data.assegnatoa : ticket.assegnatoa,
        dataChiusura,
      },
      include: INCLUDE_TICKET,
    });
  }

  // ── Admin: aggiungi nota interna ──────────────────────────────────────────

  async addNota(ticketId: number, testo: string, autore: string) {
    const ticket = await this.prisma.ticket.findUnique({ where: { id: ticketId } });
    if (!ticket) throw new NotFoundException('Ticket non trovato');
    if (!testo?.trim()) throw new BadRequestException('Testo nota obbligatorio');

    return this.prisma.ticketNota.create({
      data: { ticketId, testo: testo.trim(), autore },
    });
  }

  // ── Entrambi: note di un ticket ──────────────────────────────────────────

  async findNote(ticketId: number, richiedente?: Richiedente) {
    const ticket = await this.prisma.ticket.findUnique({ where: { id: ticketId } });
    if (!ticket) throw new NotFoundException('Ticket non trovato');

    // Il condomino può leggere le note solo dei propri ticket. Il controllo si basa
    // sul ruolo: un account CONDOMINO senza unità associata non è un amministratore.
    if (richiedente && !isAdmin(richiedente) && ticket.apertoCondominoId !== richiedente.condominoId) {
      throw new ForbiddenException('Non autorizzato');
    }

    return this.prisma.ticketNota.findMany({
      where: { ticketId },
      orderBy: { createdAt: 'asc' },
    });
  }

  // ── Admin: elimina ticket ─────────────────────────────────────────────────

  async delete(id: number) {
    const ticket = await this.prisma.ticket.findUnique({ where: { id } });
    if (!ticket) throw new NotFoundException('Ticket non trovato');
    // Le note si eliminano per cascade (onDelete: Cascade)
    return this.prisma.ticket.delete({ where: { id } });
  }
}

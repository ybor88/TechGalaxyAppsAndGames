// © Roberto Di Flumeri
import { ArgumentsHost, Catch, ExceptionFilter, HttpStatus, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import type { Response } from 'express';

/**
 * Traduce gli errori del database in risposte HTTP comprensibili invece di un 500:
 * dati non validi o riferimenti a record inesistenti sono errori del client.
 */
@Catch(Prisma.PrismaClientKnownRequestError, Prisma.PrismaClientValidationError)
export class PrismaExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('Prisma');

  catch(
    exception: Prisma.PrismaClientKnownRequestError | Prisma.PrismaClientValidationError,
    host: ArgumentsHost,
  ) {
    const res = host.switchToHttp().getResponse<Response>();
    let status = HttpStatus.BAD_REQUEST;
    let message = 'Dati non validi';

    if (exception instanceof Prisma.PrismaClientKnownRequestError) {
      switch (exception.code) {
        case 'P2002':
          status = HttpStatus.CONFLICT;
          message = 'Esiste già un elemento con questi dati';
          break;
        case 'P2003':
          message = 'Riferimento a un elemento inesistente (es. condominio, condòmino o ticket)';
          break;
        case 'P2025':
          status = HttpStatus.NOT_FOUND;
          message = 'Elemento non trovato';
          break;
        default:
          status = HttpStatus.INTERNAL_SERVER_ERROR;
          message = 'Errore del database';
      }
    }
    this.logger.warn(`${exception.constructor.name}: ${exception.message.split('\n').pop()}`);
    res.status(status).json({ statusCode: status, message });
  }
}

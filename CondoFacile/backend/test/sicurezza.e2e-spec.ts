// © Roberto Di Flumeri
// Test end-to-end su database SQLite reale (temporaneo): controllo accessi tra
// ruoli/condomìni e regressioni sui calcoli delle quote.
import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';
import * as bcrypt from 'bcryptjs';
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync, mkdirSync } from 'fs';
import { join, relative } from 'path';
import { tmpdir } from 'os';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from '../src/app.module';
import { PrismaService } from '../src/prisma/prisma.service';

const MIGRAZIONI = join(__dirname, '..', 'prisma', 'migrations');

async function applicaMigrazioni(prisma: PrismaClient) {
  const cartelle = readdirSync(MIGRAZIONI, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name)
    .sort();
  for (const c of cartelle) {
    const sql = readFileSync(join(MIGRAZIONI, c, 'migration.sql'), 'utf8')
      .split('\n')
      .filter((l) => !l.trim().startsWith('--'))
      .join('\n');
    for (const stmt of sql.split(/;\s*(?:\r?\n|$)/).map((s) => s.trim()).filter(Boolean)) {
      await prisma.$executeRawUnsafe(stmt);
    }
  }
}

describe('CondoFacile – controllo accessi e regressioni (e2e)', () => {
  let app: INestApplication<App>;
  let prisma: PrismaClient;
  let dir: string;
  const tok: Record<string, string> = {};
  const ids: Record<string, number> = {};

  const api = () => request(app.getHttpServer());
  const auth = (chi: string) => ({ Authorization: `Bearer ${tok[chi]}` });

  beforeAll(async () => {
    dir = mkdtempSync(join(tmpdir(), 'condofacile-e2e-'));
    prisma = new PrismaClient({ datasources: { db: { url: `file:${join(dir, 'test.db')}` } } });
    await applicaMigrazioni(prisma);

    const c1 = await prisma.condominio.create({ data: { nome: 'Condominio Uno', indirizzo: 'Via Uno 1' } });
    const c2 = await prisma.condominio.create({ data: { nome: 'Condominio Due', indirizzo: 'Via Due 2' } });
    const a = await prisma.condomino.create({ data: { nome: 'Anna', cognome: 'Uno', unita: 'A1', millesimi: 100, condominioId: c1.id } });
    await prisma.condomino.create({ data: { nome: 'Aldo', cognome: 'Uno', unita: 'A2', millesimi: 100, condominioId: c1.id } });
    await prisma.condomino.create({ data: { nome: 'Alba', cognome: 'Uno', unita: 'A3', millesimi: 100, condominioId: c1.id } });
    const b = await prisma.condomino.create({ data: { nome: 'Bruno', cognome: 'Due', unita: 'B1', millesimi: 500, condominioId: c2.id } });
    Object.assign(ids, { c1: c1.id, c2: c2.id, a: a.id, b: b.id });

    const hash = await bcrypt.hash('password1', 4);
    await prisma.user.createMany({
      data: [
        { username: 'admin', passwordHash: hash, role: 'AMMINISTRATORE' },
        { username: 'anna', passwordHash: hash, role: 'CONDOMINO', condominoId: a.id },
        { username: 'bruno', passwordHash: hash, role: 'CONDOMINO', condominoId: b.id },
        // account attivo ma non (più) associato a un'unità
        { username: 'orfano', passwordHash: hash, role: 'CONDOMINO', condominoId: null },
      ],
    });

    const mod = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(PrismaService)
      .useValue(prisma)
      .compile();
    app = mod.createNestApplication();
    app.setGlobalPrefix('api');
    await app.init();

    for (const u of ['admin', 'anna', 'bruno', 'orfano']) {
      const r = await api().post('/api/auth/login').send({ username: u, password: 'password1' }).expect(201);
      tok[u] = r.body.token;
    }
  });

  afterAll(async () => {
    await app?.close();
    await prisma?.$disconnect();
    rmSync(dir, { recursive: true, force: true });
  });

  describe('dashboard', () => {
    it('la dashboard globale è riservata all’amministratore', async () => {
      await api().get('/api/dashboard').set(auth('anna')).expect(403);
      await api().get('/api/dashboard').set(auth('orfano')).expect(403);
      await api().get('/api/dashboard').set(auth('admin')).expect(200);
    });
  });

  describe('ticket', () => {
    let ticketAnna: number;

    beforeAll(async () => {
      const r = await api().post('/api/ticket').set(auth('anna'))
        .send({ titolo: 'Perdita in cantina', categoria: 'idraulica' }).expect(201);
      ticketAnna = r.body.id;
      await api().post(`/api/ticket/${ticketAnna}/note`).set(auth('admin'))
        .send({ testo: 'Nota interna: preventivo 800€' }).expect(201);
    });

    it('un condòmino non legge il ticket di un altro', async () => {
      await api().get(`/api/ticket/${ticketAnna}`).set(auth('bruno')).expect(403);
      await api().get(`/api/ticket/${ticketAnna}/note`).set(auth('bruno')).expect(403);
    });

    it('un account senza unità non è trattato come amministratore', async () => {
      await api().get(`/api/ticket/${ticketAnna}`).set(auth('orfano')).expect(403);
      await api().get(`/api/ticket/${ticketAnna}/note`).set(auth('orfano')).expect(403);
    });

    it('proprietario e amministratore leggono il ticket', async () => {
      await api().get(`/api/ticket/${ticketAnna}`).set(auth('anna')).expect(200);
      const r = await api().get(`/api/ticket/${ticketAnna}`).set(auth('admin')).expect(200);
      expect(r.body.note).toHaveLength(1);
    });

    it('un condòmino non può aprire ticket in un altro condominio', async () => {
      const r = await api().post('/api/ticket').set(auth('bruno'))
        .send({ titolo: 'Spam', categoria: 'altro', condominioId: ids.c1 }).expect(201);
      expect(r.body.condominioId).toBe(ids.c2);
      await api().post('/api/ticket').set(auth('orfano'))
        .send({ titolo: 'x', categoria: 'altro', condominioId: ids.c1 }).expect(403);
    });

    it('la lista ticket del condominio resta riservata all’amministratore', async () => {
      await api().get(`/api/ticket?condominioId=${ids.c1}`).set(auth('bruno')).expect(403);
    });
  });

  describe('assemblee', () => {
    let assembleaId: number;
    let puntoId: number;

    beforeAll(async () => {
      const r = await api().post('/api/assemblee').set(auth('admin')).send({
        titolo: 'Assemblea ordinaria', data: '2026-11-20T18:00:00.000Z', luogo: 'Androne',
        tipo: 'ordinaria', condominioId: ids.c1,
      }).expect(201);
      assembleaId = r.body.id;
      const p = await api().post(`/api/assemblee/${assembleaId}/punti-odg`).set(auth('admin'))
        .send({ titolo: 'Rifacimento facciata' }).expect(201);
      puntoId = p.body.id;
    });

    it('il condòmino vede solo le assemblee del proprio condominio', async () => {
      await api().get(`/api/assemblee/${assembleaId}`).set(auth('anna')).expect(200);
      await api().get(`/api/assemblee/${assembleaId}`).set(auth('bruno')).expect(403);
      await api().get(`/api/assemblee/${assembleaId}`).set(auth('orfano')).expect(403);
    });

    it('voto pesato per millesimi, poi chiuso quando l’assemblea è conclusa', async () => {
      const v = await api().post(`/api/assemblee/punti-odg/${puntoId}/vota`).set(auth('anna'))
        .send({ scelta: 'si' }).expect(201);
      expect(v.body.votiSi).toBe(100);
      await api().post(`/api/assemblee/punti-odg/${puntoId}/vota`).set(auth('bruno'))
        .send({ scelta: 'no' }).expect(403);

      const mie = await api().get('/api/assemblee/mie').set(auth('anna')).expect(200);
      const punto = mie.body.find((x: { id: number }) => x.id === assembleaId).puntiOdG[0];
      expect(punto.mioVoto).toBe('si');
      expect(punto.voti).toBeUndefined();

      await api().patch(`/api/assemblee/${assembleaId}`).set(auth('admin')).send({ stato: 'conclusa' }).expect(200);
      await api().post(`/api/assemblee/punti-odg/${puntoId}/vota`).set(auth('anna'))
        .send({ scelta: 'no' }).expect(400);
    });
  });

  describe('documenti', () => {
    let docPrivato: number;

    beforeAll(async () => {
      const cartella = join(dir, 'uploads');
      mkdirSync(cartella, { recursive: true });
      const file = join(cartella, 'bilancio.pdf');
      writeFileSync(file, '%PDF-1.4 bilancio riservato');
      const d = await prisma.documento.create({
        data: {
          nome: 'Bilancio riservato', categoria: 'fatture_pagamenti', visibilita: 'privata',
          filePath: relative(process.cwd(), file), fileSize: 27, mimeType: 'application/pdf',
          condominioId: ids.c1,
        },
      });
      docPrivato = d.id;
    });

    it('un documento privato è scaricabile solo dall’amministratore', async () => {
      await api().get(`/api/documenti/${docPrivato}/download`).set(auth('anna')).expect(403);
      await api().get(`/api/documenti/${docPrivato}/download`).set(auth('orfano')).expect(403);
      await api().get(`/api/documenti/${docPrivato}/versioni`).set(auth('orfano')).expect(403);
      await api().get(`/api/documenti/${docPrivato}/download`).set(auth('admin')).expect(200);
    });
  });

  describe('quote', () => {
    it('rifiuta mese e importo non validi', async () => {
      await api().post('/api/quote').set(auth('admin'))
        .send({ condominioId: ids.c1, mese: 13, anno: 2026, importoTotale: 100 }).expect(400);
      await api().post('/api/quote').set(auth('admin'))
        .send({ condominioId: ids.c1, mese: 1, anno: 2026, importoTotale: -100 }).expect(400);
    });

    it('1000€ ripartiti su tre condòmini sommano esattamente 1000€', async () => {
      const q = await api().post('/api/quote').set(auth('admin'))
        .send({ condominioId: ids.c1, mese: 3, anno: 2026, importoTotale: 1000 }).expect(201);
      const r = await api().post(`/api/quote/${q.body.id}/genera`).set(auth('admin')).expect(201);
      const somma = (r.body as { importo: number }[]).reduce((s, p) => s + Math.round(p.importo * 100), 0);
      expect(r.body).toHaveLength(3);
      expect(somma).toBe(100000);
    });

    it('le quote sono gestibili solo dall’amministratore', async () => {
      await api().get(`/api/quote?condominioId=${ids.c1}`).set(auth('anna')).expect(403);
    });

    it('il condòmino vede solo i propri pagamenti', async () => {
      const r = await api().get('/api/mie-quote').set(auth('anna')).expect(200);
      expect(r.body.condomino.unita).toBe('A1');
      expect(r.body.pagamenti.every((p: { condominoId: number }) => p.condominoId === ids.a)).toBe(true);
    });
  });

  describe('validazione input (nessun errore 500)', () => {
    it('riferimenti a condomìni inesistenti danno 404, non 500', async () => {
      await api().post('/api/comunicazioni').set(auth('admin'))
        .send({ titolo: 'x', tipo: 'avviso', condominioId: 99999 }).expect(404);
      await api().post('/api/assemblee').set(auth('admin'))
        .send({ titolo: 'x', data: '2026-12-01', condominioId: 99999 }).expect(404);
      await api().post('/api/quote').set(auth('admin'))
        .send({ condominioId: 99999, mese: 1, anno: 2026, importoTotale: 10 }).expect(404);
    });

    it('intervento su ticket inesistente: 400 con messaggio chiaro', async () => {
      const f = await api().post('/api/fornitori').set(auth('admin')).send({ nome: 'Idraulica Rossi' }).expect(201);
      const r = await api().post(`/api/fornitori/${f.body.id}/interventi`).set(auth('admin'))
        .send({ descrizione: 'Riparazione', ticketId: 99999 }).expect(400);
      expect(r.body.message).toMatch(/inesistente/);
      await api().post(`/api/fornitori/${f.body.id}/interventi`).set(auth('admin'))
        .send({ descrizione: 'Riparazione', costo: -5 }).expect(400);
    });

    it('date non valide e valori non ammessi danno 400', async () => {
      await api().post('/api/assemblee').set(auth('admin'))
        .send({ titolo: 'x', data: 'non-una-data', condominioId: ids.c1 }).expect(400);
      await api().post('/api/assemblee').set(auth('admin'))
        .send({ titolo: 'x', data: '2026-12-01', tipo: 'segreta', condominioId: ids.c1 }).expect(400);
      await api().post('/api/comunicazioni').set(auth('admin'))
        .send({ titolo: 'x', tipo: 'spam', condominioId: ids.c1 }).expect(400);
      await api().post('/api/ticket').set(auth('anna'))
        .send({ titolo: 'x', categoria: 'inventata' }).expect(400);
      await api().post(`/api/condomini/${ids.c1}/condomini`).set(auth('admin'))
        .send({ nome: 'X', cognome: 'Y', unita: 'Z9', millesimi: -10 }).expect(400);
    });

    it('i campi non modificabili nel body vengono ignorati', async () => {
      const com = await api().post('/api/comunicazioni').set(auth('admin'))
        .send({ titolo: 'Avviso', corpo: 'testo', tipo: 'avviso', condominioId: ids.c1 }).expect(201);
      const r = await api().patch(`/api/comunicazioni/${com.body.id}`).set(auth('admin'))
        .send({ titolo: 'Avviso aggiornato', condominioId: ids.c2, campoInventato: 1 }).expect(200);
      expect(r.body.titolo).toBe('Avviso aggiornato');
      expect(r.body.condominioId).toBe(ids.c1);

      const f = await api().post('/api/fornitori').set(auth('admin'))
        .send({ nome: 'Elettro Srl', id: 777, campoInventato: true }).expect(201);
      expect(f.body.id).not.toBe(777);
      await api().put(`/api/fornitori/${f.body.id}`).set(auth('admin'))
        .send({ nome: 'Elettro Srl 2', campoInventato: true }).expect(200);
    });

    it('presa visione solo per comunicazioni destinate al condòmino', async () => {
      const com = await api().post('/api/comunicazioni').set(auth('admin'))
        .send({ titolo: 'Solo inquilini', tipo: 'avviso', destinatariTipo: 'inquilini', condominioId: ids.c1 }).expect(201);
      await api().post(`/api/comunicazioni/${com.body.id}/presa-visione`).set(auth('anna')).expect(403);
    });
  });

  describe('account disattivati', () => {
    it('un condòmino disattivato non può più accedere', async () => {
      await api().patch(`/api/condomini/${ids.c2}/condomini/${ids.b}/toggle-stato`).set(auth('admin')).expect(200);
      await api().post('/api/auth/login').send({ username: 'bruno', password: 'password1' }).expect(401);
      await api().patch(`/api/condomini/${ids.c2}/condomini/${ids.b}/toggle-stato`).set(auth('admin')).expect(200);
      await api().post('/api/auth/login').send({ username: 'bruno', password: 'password1' }).expect(201);
    });
  });
});

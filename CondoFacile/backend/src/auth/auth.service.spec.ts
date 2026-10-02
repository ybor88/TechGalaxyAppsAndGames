import * as bcrypt from 'bcryptjs';
import { BadRequestException, ConflictException, UnauthorizedException } from '@nestjs/common';
import { AuthService, hashResetToken, isGmail } from './auth.service';
import { PrismaService } from '../prisma/prisma.service';

type MockPrisma = {
  user: { findUnique: jest.Mock; findFirst: jest.Mock; update: jest.Mock; create: jest.Mock };
};

function createPrismaMock(): MockPrisma {
  return {
    user: { findUnique: jest.fn(), findFirst: jest.fn(), update: jest.fn(), create: jest.fn() },
  };
}

describe('AuthService', () => {
  let service: AuthService;
  let prisma: MockPrisma;

  beforeEach(() => {
    prisma = createPrismaMock();
    service = new AuthService(prisma as unknown as PrismaService);
  });

  describe('login', () => {
    it('throws UnauthorizedException when user does not exist', async () => {
      prisma.user.findUnique.mockResolvedValue(null);
      await expect(service.login('nope', 'pwd')).rejects.toThrow(UnauthorizedException);
    });

    it('throws UnauthorizedException when password is wrong', async () => {
      const hash = await bcrypt.hash('correct-password', 10);
      prisma.user.findUnique.mockResolvedValue({
        id: 1,
        username: 'mario',
        passwordHash: hash,
        role: 'CONDOMINO',
        condominoId: 5,
        profilePhoto: null,
      });
      await expect(service.login('mario', 'wrong-password')).rejects.toThrow(UnauthorizedException);
    });

    it('returns a token and sanitized user on success', async () => {
      const hash = await bcrypt.hash('correct-password', 10);
      prisma.user.findUnique.mockResolvedValue({
        id: 1,
        username: 'mario',
        passwordHash: hash,
        role: 'CONDOMINO',
        condominoId: 5,
        profilePhoto: null,
      });
      const result = await service.login('mario', 'correct-password');
      expect(result.token).toEqual(expect.any(String));
      expect(result.user).toEqual({
        id: 1,
        username: 'mario',
        role: 'CONDOMINO',
        condominoId: 5,
        profilePhoto: null,
      });
      // passwordHash must never leak in the response
      expect(result.user).not.toHaveProperty('passwordHash');
    });
  });

  describe('verifyToken', () => {
    it('decodes a token produced by login', async () => {
      const hash = await bcrypt.hash('pwd', 10);
      prisma.user.findUnique.mockResolvedValue({
        id: 42,
        username: 'admin',
        passwordHash: hash,
        role: 'ADMIN',
        condominoId: null,
        profilePhoto: null,
      });
      const { token } = await service.login('admin', 'pwd');
      const decoded = service.verifyToken(token);
      expect(decoded.sub).toBe(42);
      expect(decoded.role).toBe('ADMIN');
    });

    it('throws UnauthorizedException for an invalid token', () => {
      expect(() => service.verifyToken('not-a-real-token')).toThrow(UnauthorizedException);
    });
  });

  describe('changePassword', () => {
    it('throws when current password is incorrect', async () => {
      const hash = await bcrypt.hash('right', 10);
      prisma.user.findUnique.mockResolvedValue({ id: 1, passwordHash: hash });
      await expect(service.changePassword(1, 'wrong', 'newpass')).rejects.toThrow(UnauthorizedException);
    });

    it('updates the password hash when current password is correct', async () => {
      const hash = await bcrypt.hash('right', 10);
      prisma.user.findUnique.mockResolvedValue({ id: 1, passwordHash: hash });
      prisma.user.update.mockResolvedValue({});
      const result = await service.changePassword(1, 'right', 'newpass');
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: 1 },
        data: { passwordHash: expect.any(String) },
      });
      expect(result.message).toMatch(/successo/i);
    });
  });

  describe('updateProfilePhoto', () => {
    it('rejects images larger than ~2MB', async () => {
      const big = 'a'.repeat(2_800_001);
      await expect(service.updateProfilePhoto(1, big)).rejects.toThrow('Immagine troppo grande (max 2MB)');
      expect(prisma.user.update).not.toHaveBeenCalled();
    });

    it('stores accepted images', async () => {
      prisma.user.update.mockResolvedValue({ profilePhoto: 'data:image/png;base64,abc' });
      const result = await service.updateProfilePhoto(1, 'data:image/png;base64,abc');
      expect(result).toEqual({ profilePhoto: 'data:image/png;base64,abc' });
    });
  });

  describe('isGmail', () => {
    it('accetta solo indirizzi @gmail.com', () => {
      expect(isGmail('mario.rossi@gmail.com')).toBe(true);
      expect(isGmail('Mario.Rossi@GMAIL.com')).toBe(true);
      expect(isGmail('mario@libero.it')).toBe(false);
      expect(isGmail('mario@gmail.com.evil.it')).toBe(false);
      expect(isGmail('mario@notgmail.com')).toBe(false);
    });
  });

  describe('register', () => {
    const dto = {
      nome: 'Mario',
      cognome: 'Rossi',
      email: 'Mario.Rossi@gmail.com',
      unitaRichiesta: 'A1',
      username: 'mario.rossi',
      password: 'segreta1',
    };

    it('rifiuta email non Gmail', async () => {
      prisma.user.findUnique.mockResolvedValue(null);
      await expect(service.register({ ...dto, email: 'mario@libero.it' })).rejects.toThrow(BadRequestException);
      expect(prisma.user.create).not.toHaveBeenCalled();
    });

    it('rifiuta email già usata da un altro account', async () => {
      prisma.user.findUnique.mockResolvedValue(null);
      prisma.user.findFirst.mockResolvedValue({ id: 2 });
      await expect(service.register(dto)).rejects.toThrow(ConflictException);
    });

    it("salva l'email Gmail in minuscolo", async () => {
      prisma.user.findUnique.mockResolvedValue(null);
      prisma.user.findFirst.mockResolvedValue(null);
      await service.register(dto);
      expect(prisma.user.create.mock.calls[0][0].data.email).toBe('mario.rossi@gmail.com');
    });
  });

  describe('confirmPasswordReset', () => {
    it('aggiorna la password e invalida il token', async () => {
      prisma.user.findUnique.mockResolvedValue({
        id: 7,
        username: 'mario.rossi',
        resetTokenScadenza: new Date(Date.now() + 60_000),
      });
      const res = await service.confirmPasswordReset('abc', 'nuovaPwd1');

      expect(prisma.user.findUnique).toHaveBeenCalledWith({ where: { resetTokenHash: hashResetToken('abc') } });
      const data = prisma.user.update.mock.calls[0][0].data;
      expect(await bcrypt.compare('nuovaPwd1', data.passwordHash)).toBe(true);
      expect(data.resetTokenHash).toBeNull();
      expect(res.username).toBe('mario.rossi');
    });

    it('rifiuta un token scaduto', async () => {
      prisma.user.findUnique.mockResolvedValue({ id: 7, username: 'x', resetTokenScadenza: new Date(Date.now() - 1) });
      await expect(service.confirmPasswordReset('abc', 'nuovaPwd1')).rejects.toThrow(BadRequestException);
      expect(prisma.user.update).not.toHaveBeenCalled();
    });

    it('rifiuta un token inesistente', async () => {
      prisma.user.findUnique.mockResolvedValue(null);
      await expect(service.confirmPasswordReset('abc', 'nuovaPwd1')).rejects.toThrow(BadRequestException);
    });
  });
});

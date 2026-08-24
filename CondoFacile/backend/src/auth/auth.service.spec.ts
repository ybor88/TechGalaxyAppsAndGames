import * as bcrypt from 'bcryptjs';
import { UnauthorizedException } from '@nestjs/common';
import { AuthService } from './auth.service';
import { PrismaService } from '../prisma/prisma.service';

type MockPrisma = { user: { findUnique: jest.Mock; update: jest.Mock } };

function createPrismaMock(): MockPrisma {
  return {
    user: { findUnique: jest.fn(), update: jest.fn() },
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
});

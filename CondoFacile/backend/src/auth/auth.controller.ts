import {
  Body,
  Controller,
  Get,
  Post,
  Req,
  UnauthorizedException,
  UseGuards,
  BadRequestException,
  HttpCode,
} from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from './jwt.guard';
import { AuthService } from './auth.service';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

@Controller('auth')
export class AuthController {
  constructor(private authService: AuthService) {}

  @Post('login')
  async login(@Body() body: { username: string; password: string }) {
    const { username, password } = body ?? {};
    if (!username || !password) {
      throw new UnauthorizedException('Username e password obbligatori');
    }
    return this.authService.login(username, password);
  }

  @UseGuards(JwtAuthGuard)
  @Get('me')
  async me(@Req() req: Request) {
    const payload = (req as Request & { user: JwtPayload }).user;
    return this.authService.getMe(payload.sub);
  }

  @UseGuards(JwtAuthGuard)
  @Post('profile-photo')
  async uploadProfilePhoto(
    @Req() req: Request,
    @Body() body: { photo: string },
  ) {
    const payload = (req as Request & { user: JwtPayload }).user;
    if (!body?.photo) throw new BadRequestException('Foto mancante');
    return this.authService.updateProfilePhoto(payload.sub, body.photo);
  }

  @UseGuards(JwtAuthGuard)
  @HttpCode(200)
  @Post('change-password')
  async changePassword(
    @Req() req: Request,
    @Body() body: { currentPassword: string; newPassword: string },
  ) {
    const payload = (req as Request & { user: JwtPayload }).user;
    if (!body?.currentPassword || !body?.newPassword) {
      throw new BadRequestException('Campi obbligatori mancanti');
    }
    if (body.newPassword.length < 6) {
      throw new BadRequestException('La nuova password deve avere almeno 6 caratteri');
    }
    return this.authService.changePassword(payload.sub, body.currentPassword, body.newPassword);
  }
}

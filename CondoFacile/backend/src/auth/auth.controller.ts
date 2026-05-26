import {
  Body,
  Controller,
  Get,
  Post,
  Req,
  UnauthorizedException,
  UseGuards,
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
}

import { Controller, Get, Req, UnauthorizedException, UseGuards } from '@nestjs/common';
import type { Request } from 'express';
import { DashboardService } from './dashboard.service';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AuthService } from '../auth/auth.service';
import type { CondominoDashboardData, DashboardData } from './dashboard.types';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

@Controller('dashboard')
export class DashboardController {
  constructor(
    private readonly dashboardService: DashboardService,
    private readonly authService: AuthService,
  ) {}

  @UseGuards(JwtAuthGuard)
  @Get()
  async getDashboard(): Promise<DashboardData> {
    return this.dashboardService.getDashboardData();
  }

  @UseGuards(JwtAuthGuard)
  @Get('condomino')
  async getDashboardCondomino(@Req() req: Request): Promise<CondominoDashboardData> {
    const payload = (req as Request & { user: JwtPayload }).user;
    if (!payload.condominoId) {
      throw new UnauthorizedException('Utente non associato a un condomino');
    }
    return this.dashboardService.getDashboardCondomino(payload.condominoId);
  }
}


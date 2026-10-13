import { Controller, Get, Query, Req, UnauthorizedException, UseGuards } from '@nestjs/common';
import type { Request } from 'express';
import { DashboardService } from './dashboard.service';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AdminGuard } from '../auth/admin.guard';
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

  // Dashboard globale di tutti i condomìni: solo amministratori
  @UseGuards(JwtAuthGuard, AdminGuard)
  @Get()
  async getDashboard(@Query('condominioId') condominioId?: string): Promise<DashboardData> {
    const id = condominioId ? Number(condominioId) : undefined;
    return this.dashboardService.getDashboardData(Number.isInteger(id) ? id : undefined);
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


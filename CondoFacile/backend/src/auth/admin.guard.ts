import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Request } from 'express';

@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request & { user?: { role: string } }>();
    if (request.user?.role !== 'AMMINISTRATORE') {
      throw new ForbiddenException('Solo gli amministratori possono eseguire questa azione');
    }
    return true;
  }
}

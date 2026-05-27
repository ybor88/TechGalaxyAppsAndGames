import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { PrismaModule } from './prisma/prisma.module';
import { DashboardModule } from './dashboard/dashboard.module';
import { AuthModule } from './auth/auth.module';
import { CondominioModule } from './condominio/condominio.module';

@Module({
  imports: [PrismaModule, DashboardModule, AuthModule, CondominioModule],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}

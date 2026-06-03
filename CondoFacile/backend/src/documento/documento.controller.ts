import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseIntPipe,
  Patch,
  Post,
  Query,
  Req,
  Res,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { diskStorage } from 'multer';
import { extname, join } from 'path';
import { mkdirSync } from 'fs';
import type { Request, Response } from 'express';
import { JwtAuthGuard } from '../auth/jwt.guard';
import { AdminGuard } from '../auth/admin.guard';
import { DocumentoService } from './documento.service';

interface JwtPayload {
  sub: number;
  username: string;
  role: string;
  condominoId: number | null;
}

type AuthRequest = Request & { user: JwtPayload };

// Cartella upload
const UPLOAD_DIR = join(process.cwd(), 'uploads', 'documenti');
mkdirSync(UPLOAD_DIR, { recursive: true });

const storage = diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => {
    const unique = `${Date.now()}-${Math.round(Math.random() * 1e9)}`;
    cb(null, `${unique}${extname(file.originalname)}`);
  },
});

@Controller('documenti')
@UseGuards(JwtAuthGuard)
export class DocumentoController {
  constructor(private service: DocumentoService) {}

  // ── Admin: lista documenti ─────────────────────────────────────────────────

  @Get()
  @UseGuards(AdminGuard)
  findAll(
    @Query('condominioId', ParseIntPipe) condominioId: number,
    @Query('categoria') categoria?: string,
    @Query('search') search?: string,
  ) {
    return this.service.findAll(condominioId, { categoria, search });
  }

  // ── Condomino: documenti visibili ──────────────────────────────────────────

  @Get('miei')
  findMiei(@Req() req: AuthRequest) {
    const { condominoId } = req.user;
    if (!condominoId) throw new BadRequestException('Riservato ai condòmini');
    return this.service.findMiei(condominoId);
  }

  // ── Admin: upload documento ────────────────────────────────────────────────

  @Post('upload')
  @UseGuards(AdminGuard)
  @UseInterceptors(FileInterceptor('file', { storage, limits: { fileSize: 20 * 1024 * 1024 } }))
  async upload(
    @UploadedFile() file: Express.Multer.File,
    @Body() body: {
      nome: string;
      categoria: string;
      tag?: string;
      visibilita?: string;
      unitaAccesso?: string;
      condominioId: string;
    },
  ) {
    if (!file) throw new BadRequestException('File obbligatorio');
    if (!body?.nome || !body?.categoria || !body?.condominioId) {
      throw new BadRequestException('nome, categoria e condominioId obbligatori');
    }
    const filePath = join('uploads', 'documenti', file.filename);
    return this.service.create({
      nome: body.nome,
      categoria: body.categoria,
      tag: body.tag,
      visibilita: body.visibilita,
      unitaAccesso: body.unitaAccesso,
      filePath,
      fileSize: file.size,
      mimeType: file.mimetype,
      condominioId: parseInt(body.condominioId),
    });
  }

  // ── Admin: aggiorna metadati ───────────────────────────────────────────────

  @Patch(':id')
  @UseGuards(AdminGuard)
  update(
    @Param('id', ParseIntPipe) id: number,
    @Body() body: { nome?: string; categoria?: string; tag?: string; visibilita?: string; unitaAccesso?: string },
  ) {
    return this.service.update(id, body);
  }

  // ── Admin: sostituisci file (nuova versione) ───────────────────────────────

  @Post(':id/nuova-versione')
  @UseGuards(AdminGuard)
  @UseInterceptors(FileInterceptor('file', { storage, limits: { fileSize: 20 * 1024 * 1024 } }))
  async nuovaVersione(
    @Param('id', ParseIntPipe) id: number,
    @UploadedFile() file: Express.Multer.File,
  ) {
    if (!file) throw new BadRequestException('File obbligatorio');
    const filePath = join('uploads', 'documenti', file.filename);
    return this.service.update(id, { filePath, fileSize: file.size, mimeType: file.mimetype });
  }

  // ── Admin: elimina ────────────────────────────────────────────────────────

  @Delete(':id')
  @UseGuards(AdminGuard)
  remove(@Param('id', ParseIntPipe) id: number) {
    return this.service.remove(id);
  }

  // ── Download file (admin e condomino) ─────────────────────────────────────

  @Get(':id/download')
  async download(
    @Param('id', ParseIntPipe) id: number,
    @Req() req: AuthRequest,
    @Res({ passthrough: true }) res: Response,
  ) {
    const condominoId = req.user.role === 'CONDOMINO' ? (req.user.condominoId ?? undefined) : undefined;
    const { file, nome, mimeType } = await this.service.download(id, condominoId);
    res.set({
      'Content-Type': mimeType,
      'Content-Disposition': `attachment; filename="${encodeURIComponent(nome)}"`,
    });
    return file;
  }
}

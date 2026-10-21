"""Caricamento e lettura delle foto tramite URL firmati. © Roberto Di Flumeri"""

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.database import get_db
from app.models import Photo, User, nuovo_id
from app.security import controlla_rate, utente_corrente
from app.services import foto

router = APIRouter(prefix="/v1/files", tags=["Foto"])


@router.post("", status_code=status.HTTP_201_CREATED)
async def carica(file: UploadFile = File(...), user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    controlla_rate(f"upload:{user.id}", 20)
    limite = get_settings().foto_max_mb * 1024 * 1024
    dati = await file.read(limite + 1)
    jpeg, w, h = foto.elabora(dati)
    pid = nuovo_id()
    (foto.cartella() / f"{pid}.jpg").write_bytes(jpeg)
    db.add(Photo(id=pid, proprietario_id=user.id, file=f"{pid}.jpg", larghezza=w, altezza=h))
    await db.commit()
    return {"id": pid, "url": foto.url_firmato(pid), "larghezza": w, "altezza": h}


@router.get("/{file_id}", include_in_schema=False)
async def leggi(file_id: str, exp: int = Query(...), sig: str = Query(...), db: AsyncSession = Depends(get_db)):
    if not foto.firma_valida(file_id, exp, sig):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Link alla foto scaduto o non valido")
    p = await db.get(Photo, file_id)
    percorso = foto.cartella() / (p.file if p else "")
    if p is None or not percorso.is_file():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Foto non trovata")
    return FileResponse(percorso, media_type="image/jpeg",
                        headers={"Cache-Control": "private, max-age=600", "X-Content-Type-Options": "nosniff"})

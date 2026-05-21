import io

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.fatturazione import (
    AnagraficaCreate,
    AnagraficaResponse,
    AnagraficaUpdate,
    DocumentoCreate,
    DocumentoResponse,
    DocumentoUpdate,
)
from app.services.fatturazione import AnagraficheService, DocumentiService, genera_pdf

# Due router separati: evita conflitti tra /anagrafiche e /{documento_id}
anagrafiche_router = APIRouter()
router = APIRouter()


# -- Anagrafiche -- registrato su /api/v1/anagrafiche -------------------------

@anagrafiche_router.get("/", response_model=list[AnagraficaResponse])
async def list_anagrafiche(
    tipo: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    service = AnagraficheService(db)
    return await service.list(tipo=tipo)


@anagrafiche_router.post(
    "/",
    response_model=AnagraficaResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_anagrafica(
    payload: AnagraficaCreate,
    db: AsyncSession = Depends(get_db),
):
    service = AnagraficheService(db)
    return await service.create(payload)


@anagrafiche_router.get("/{anagrafica_id}", response_model=AnagraficaResponse)
async def get_anagrafica(
    anagrafica_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = AnagraficheService(db)
    ana = await service.get(anagrafica_id)
    if not ana:
        raise HTTPException(status_code=404, detail="Anagrafica non trovata")
    return ana


@anagrafiche_router.put("/{anagrafica_id}", response_model=AnagraficaResponse)
async def update_anagrafica(
    anagrafica_id: int,
    payload: AnagraficaUpdate,
    db: AsyncSession = Depends(get_db),
):
    service = AnagraficheService(db)
    ana = await service.update(anagrafica_id, payload)
    if not ana:
        raise HTTPException(status_code=404, detail="Anagrafica non trovata")
    return ana


@anagrafiche_router.delete("/{anagrafica_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_anagrafica(
    anagrafica_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = AnagraficheService(db)
    ok = await service.delete(anagrafica_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Anagrafica non trovata")


# -- Documenti -- registrato su /api/v1/documenti -----------------------------

@router.get("/", response_model=list[DocumentoResponse])
async def list_documenti(
    tipo: str | None = Query(None),
    stato: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    service = DocumentiService(db)
    return await service.list(tipo=tipo, stato=stato)


@router.post("/", response_model=DocumentoResponse, status_code=status.HTTP_201_CREATED)
async def create_documento(
    payload: DocumentoCreate,
    db: AsyncSession = Depends(get_db),
):
    service = DocumentiService(db)
    return await service.create(payload)


# /pdf deve stare prima di /{documento_id} per evitare conflitti di match
@router.get("/{documento_id}/pdf")
async def download_pdf(
    documento_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = DocumentiService(db)
    doc = await service.get(documento_id)
    if not doc:
        raise HTTPException(status_code=404, detail="Documento non trovato")
    pdf_bytes = genera_pdf(doc)
    filename = f"{doc.numero}.pdf"
    return StreamingResponse(
        io.BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@router.get("/{documento_id}", response_model=DocumentoResponse)
async def get_documento(
    documento_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = DocumentiService(db)
    doc = await service.get(documento_id)
    if not doc:
        raise HTTPException(status_code=404, detail="Documento non trovato")
    return doc


@router.put("/{documento_id}", response_model=DocumentoResponse)
async def update_documento(
    documento_id: int,
    payload: DocumentoUpdate,
    db: AsyncSession = Depends(get_db),
):
    service = DocumentiService(db)
    doc = await service.update(documento_id, payload)
    if not doc:
        raise HTTPException(status_code=404, detail="Documento non trovato")
    return doc


@router.delete("/{documento_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_documento(
    documento_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = DocumentiService(db)
    ok = await service.delete(documento_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Documento non trovato")

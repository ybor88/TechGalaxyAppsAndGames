from fastapi import APIRouter, Depends, File, HTTPException, Path, UploadFile, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.ocr import OcrElaboraResponse, OcrRisultatoResponse
from app.services.ocr import OcrService

router = APIRouter()

_MAX_SIZE_MB = 20
_MAX_SIZE_BYTES = _MAX_SIZE_MB * 1024 * 1024

_TIPI_CONSENTITI = {
    "application/pdf",
    "image/png",
    "image/jpeg",
    "image/tiff",
    "image/bmp",
    "image/webp",
}


@router.post(
    "/elabora",
    response_model=OcrElaboraResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Carica e analizza una fattura (PDF o immagine)",
)
async def elabora_fattura(
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
):
    """
    Accetta PDF o immagini (PNG, JPG, TIFF, BMP, WEBP).
    Estrae il testo con Tesseract OCR o pypdf, poi individua
    P.IVA, importi, date e ragione sociale del fornitore.
    """
    content_type = file.content_type or ""
    ext = (file.filename or "").rsplit(".", 1)[-1].lower()

    allowed_ext = {"pdf", "png", "jpg", "jpeg", "tiff", "tif", "bmp", "webp"}
    if content_type not in _TIPI_CONSENTITI and ext not in allowed_ext:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=(
                f"Formato '{content_type or ext}' non supportato. "
                "Carica un PDF o un'immagine (PNG, JPG, TIFF, BMP, WEBP)."
            ),
        )

    data = await file.read()
    if len(data) > _MAX_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"File troppo grande. Massimo consentito: {_MAX_SIZE_MB} MB.",
        )
    if len(data) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File vuoto.",
        )

    service = OcrService(db)
    record, avvisi = await service.elabora(data, file.filename or "documento", content_type)
    return OcrElaboraResponse(risultato=OcrRisultatoResponse.model_validate(record), avvisi=avvisi)


@router.get(
    "/risultati",
    response_model=list[OcrRisultatoResponse],
    summary="Lista tutti i risultati OCR",
)
async def list_risultati(db: AsyncSession = Depends(get_db)):
    service = OcrService(db)
    return await service.list()


@router.get(
    "/risultati/{risultato_id}",
    response_model=OcrRisultatoResponse,
    summary="Dettaglio risultato OCR",
)
async def get_risultato(
    risultato_id: int = Path(..., ge=1),
    db: AsyncSession = Depends(get_db),
):
    service = OcrService(db)
    record = await service.get(risultato_id)
    if not record:
        raise HTTPException(status_code=404, detail="Risultato OCR non trovato.")
    return record


@router.delete(
    "/risultati/{risultato_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Elimina risultato OCR",
)
async def delete_risultato(
    risultato_id: int = Path(..., ge=1),
    db: AsyncSession = Depends(get_db),
):
    service = OcrService(db)
    ok = await service.delete(risultato_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Risultato OCR non trovato.")


@router.patch(
    "/risultati/{risultato_id}/collega-documento/{documento_id}",
    response_model=OcrRisultatoResponse,
    summary="Collega risultato OCR a un documento esistente",
)
async def collega_documento(
    risultato_id: int = Path(..., ge=1),
    documento_id: int = Path(..., ge=1),
    db: AsyncSession = Depends(get_db),
):
    service = OcrService(db)
    record = await service.collega_documento(risultato_id, documento_id)
    if not record:
        raise HTTPException(status_code=404, detail="Risultato OCR non trovato.")
    return record

"""Centro notifiche: avvisi consegnati, in attesa (ore di silenzio) e riepiloghi. © Roberto Di Flumeri"""

from fastapi import APIRouter, Depends, status
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Notification, User, adesso
from app.security import utente_corrente
from app.services.notifiche import genera_riepiloghi

router = APIRouter(prefix="/v1/notifications", tags=["Notifiche"])
VISIBILI = ("consegnata", "in_attesa")


def _visibili(user_id: str):
    return (Notification.user_id == user_id, Notification.stato.in_(VISIBILI), Notification.visibile_da <= adesso())


@router.get("")
async def elenco(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    await genera_riepiloghi(db)  # eventuali riepiloghi maturati vengono preparati alla lettura
    righe = (await db.execute(select(Notification).where(*_visibili(user.id))
                              .order_by(Notification.visibile_da.desc()).limit(100))).scalars().all()
    in_attesa = (await db.execute(select(func.count()).select_from(Notification).where(
        Notification.user_id == user.id,
        (Notification.stato == "raggruppata") | ((Notification.stato == "in_attesa") & (Notification.visibile_da > adesso())))
    )).scalar_one()
    return {
        "notifiche": [{"id": n.id, "tipo": n.tipo, "titolo": n.titolo, "testo": n.testo, "link": n.link, "letta": n.letta,
                       "data": n.visibile_da} for n in righe],
        "in_attesa": in_attesa,
    }


@router.get("/conteggio")
async def conteggio(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    n = (await db.execute(select(func.count()).select_from(Notification).where(
        *_visibili(user.id), Notification.letta.is_(False)))).scalar_one()
    return {"non_lette": n}


@router.post("/{notifica_id}/letta", status_code=status.HTTP_204_NO_CONTENT)
async def segna_letta(notifica_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    await db.execute(update(Notification).where(Notification.id == notifica_id, Notification.user_id == user.id)
                     .values(letta=True))
    await db.commit()


@router.post("/lette", status_code=status.HTTP_204_NO_CONTENT)
async def tutte_lette(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    await db.execute(update(Notification).where(*_visibili(user.id)).values(letta=True))
    await db.commit()

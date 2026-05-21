from decimal import Decimal
from datetime import date

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.financial import Conto
from app.models.contabilita import RegistrazioneContabile, RigaRegistrazione
from app.schemas.contabilita import (
    RegistrazioneCreate,
    RegistrazioneSummary,
    RegistrazioneDetail,
    RigaRegistrazioneOut,
    VoceBilancio,
    BilancioResponse,
    LiquidazioneIVAResponse,
    RigaIVA,
    InizializzaPianoContiResponse,
)

# Piano dei conti italiano standard (senza dati demo)
PIANO_CONTI_STANDARD: list[dict] = [
    # ── Classe 1 — Attività ──────────────────────────────────────────────────
    {"codice": "10000", "descrizione": "DISPONIBILITÀ LIQUIDE", "tipo": "attivo"},
    {"codice": "10100", "descrizione": "Cassa", "tipo": "attivo"},
    {"codice": "10200", "descrizione": "Banca c/c attivo", "tipo": "attivo"},
    {"codice": "10300", "descrizione": "Cassa valuta estera", "tipo": "attivo"},
    {"codice": "11000", "descrizione": "CREDITI COMMERCIALI", "tipo": "attivo"},
    {"codice": "11100", "descrizione": "Crediti verso clienti", "tipo": "attivo"},
    {"codice": "11200", "descrizione": "Anticipi a fornitori", "tipo": "attivo"},
    {"codice": "11300", "descrizione": "Effetti attivi", "tipo": "attivo"},
    {"codice": "12000", "descrizione": "RIMANENZE", "tipo": "attivo"},
    {"codice": "12100", "descrizione": "Merci", "tipo": "attivo"},
    {"codice": "12200", "descrizione": "Materie prime e sussidiarie", "tipo": "attivo"},
    {"codice": "12300", "descrizione": "Semilavorati", "tipo": "attivo"},
    {"codice": "13000", "descrizione": "ALTRE ATTIVITÀ CORRENTI", "tipo": "attivo"},
    {"codice": "13100", "descrizione": "IVA a credito", "tipo": "attivo"},
    {"codice": "13200", "descrizione": "Crediti tributari", "tipo": "attivo"},
    {"codice": "13300", "descrizione": "Ratei attivi", "tipo": "attivo"},
    {"codice": "13400", "descrizione": "Risconti attivi", "tipo": "attivo"},
    {"codice": "14000", "descrizione": "IMMOBILIZZAZIONI MATERIALI", "tipo": "attivo"},
    {"codice": "14100", "descrizione": "Impianti e macchinari", "tipo": "attivo"},
    {"codice": "14200", "descrizione": "Attrezzature", "tipo": "attivo"},
    {"codice": "14300", "descrizione": "Mobili e arredi", "tipo": "attivo"},
    {"codice": "14400", "descrizione": "Automezzi", "tipo": "attivo"},
    {"codice": "14500", "descrizione": "Fondo amm.to impianti e macchinari", "tipo": "attivo"},
    {"codice": "14600", "descrizione": "Fondo amm.to attrezzature", "tipo": "attivo"},
    {"codice": "15000", "descrizione": "IMMOBILIZZAZIONI IMMATERIALI", "tipo": "attivo"},
    {"codice": "15100", "descrizione": "Software e licenze", "tipo": "attivo"},
    {"codice": "15200", "descrizione": "Brevetti e marchi", "tipo": "attivo"},
    {"codice": "15300", "descrizione": "Fondo amm.to immobilizzazioni immateriali", "tipo": "attivo"},
    # ── Classe 2 — Passività ─────────────────────────────────────────────────
    {"codice": "20000", "descrizione": "DEBITI FINANZIARI", "tipo": "passivo"},
    {"codice": "20100", "descrizione": "Banca c/c passivo", "tipo": "passivo"},
    {"codice": "20200", "descrizione": "Mutui passivi", "tipo": "passivo"},
    {"codice": "20300", "descrizione": "Leasing passivi", "tipo": "passivo"},
    {"codice": "21000", "descrizione": "DEBITI COMMERCIALI", "tipo": "passivo"},
    {"codice": "21100", "descrizione": "Debiti verso fornitori", "tipo": "passivo"},
    {"codice": "21200", "descrizione": "Anticipi da clienti", "tipo": "passivo"},
    {"codice": "21300", "descrizione": "Effetti passivi", "tipo": "passivo"},
    {"codice": "22000", "descrizione": "ALTRI DEBITI", "tipo": "passivo"},
    {"codice": "22100", "descrizione": "IVA a debito", "tipo": "passivo"},
    {"codice": "22200", "descrizione": "Debiti tributari", "tipo": "passivo"},
    {"codice": "22300", "descrizione": "Debiti previdenziali", "tipo": "passivo"},
    {"codice": "22400", "descrizione": "Debiti verso dipendenti", "tipo": "passivo"},
    {"codice": "22500", "descrizione": "Ratei passivi", "tipo": "passivo"},
    {"codice": "22600", "descrizione": "Risconti passivi", "tipo": "passivo"},
    # ── Classe 3 — Patrimonio Netto ──────────────────────────────────────────
    {"codice": "30000", "descrizione": "PATRIMONIO NETTO", "tipo": "passivo"},
    {"codice": "30100", "descrizione": "Capitale sociale", "tipo": "passivo"},
    {"codice": "30200", "descrizione": "Riserva legale", "tipo": "passivo"},
    {"codice": "30300", "descrizione": "Riserve di utili", "tipo": "passivo"},
    {"codice": "30400", "descrizione": "Utile/Perdita d'esercizio", "tipo": "passivo"},
    {"codice": "30500", "descrizione": "Utili/Perdite portati a nuovo", "tipo": "passivo"},
    # ── Classe 4 — Costi ─────────────────────────────────────────────────────
    {"codice": "40000", "descrizione": "ACQUISTI", "tipo": "costo"},
    {"codice": "40100", "descrizione": "Acquisto merci", "tipo": "costo"},
    {"codice": "40200", "descrizione": "Acquisto materie prime", "tipo": "costo"},
    {"codice": "40300", "descrizione": "Acquisto servizi", "tipo": "costo"},
    {"codice": "40400", "descrizione": "Resi e rettifiche su acquisti", "tipo": "costo"},
    {"codice": "41000", "descrizione": "COSTI DEL PERSONALE", "tipo": "costo"},
    {"codice": "41100", "descrizione": "Salari e stipendi", "tipo": "costo"},
    {"codice": "41200", "descrizione": "Contributi previdenziali", "tipo": "costo"},
    {"codice": "41300", "descrizione": "TFR — accantonamento", "tipo": "costo"},
    {"codice": "41400", "descrizione": "Altri costi del personale", "tipo": "costo"},
    {"codice": "42000", "descrizione": "AMMORTAMENTI E SVALUTAZIONI", "tipo": "costo"},
    {"codice": "42100", "descrizione": "Amm.to immobilizzazioni materiali", "tipo": "costo"},
    {"codice": "42200", "descrizione": "Amm.to immobilizzazioni immateriali", "tipo": "costo"},
    {"codice": "42300", "descrizione": "Svalutazione crediti", "tipo": "costo"},
    {"codice": "43000", "descrizione": "ALTRI COSTI OPERATIVI", "tipo": "costo"},
    {"codice": "43100", "descrizione": "Affitti e locazioni passive", "tipo": "costo"},
    {"codice": "43200", "descrizione": "Utenze (luce, gas, acqua)", "tipo": "costo"},
    {"codice": "43300", "descrizione": "Assicurazioni", "tipo": "costo"},
    {"codice": "43400", "descrizione": "Pubblicità e marketing", "tipo": "costo"},
    {"codice": "43500", "descrizione": "Spese generali e amministrative", "tipo": "costo"},
    {"codice": "43600", "descrizione": "Spese di trasporto e spedizione", "tipo": "costo"},
    {"codice": "43700", "descrizione": "Compensi a professionisti", "tipo": "costo"},
    {"codice": "43800", "descrizione": "Manutenzione e riparazioni", "tipo": "costo"},
    {"codice": "43900", "descrizione": "Abbonamenti e canoni", "tipo": "costo"},
    {"codice": "44000", "descrizione": "ONERI FINANZIARI", "tipo": "costo"},
    {"codice": "44100", "descrizione": "Interessi passivi su finanziamenti", "tipo": "costo"},
    {"codice": "44200", "descrizione": "Spese bancarie e commissioni", "tipo": "costo"},
    {"codice": "44300", "descrizione": "Perdite su cambi", "tipo": "costo"},
    {"codice": "45000", "descrizione": "IMPOSTE SUL REDDITO", "tipo": "costo"},
    {"codice": "45100", "descrizione": "IRES", "tipo": "costo"},
    {"codice": "45200", "descrizione": "IRAP", "tipo": "costo"},
    # ── Classe 5 — Ricavi ────────────────────────────────────────────────────
    {"codice": "50000", "descrizione": "RICAVI OPERATIVI", "tipo": "ricavo"},
    {"codice": "50100", "descrizione": "Vendita merci", "tipo": "ricavo"},
    {"codice": "50200", "descrizione": "Prestazioni di servizi", "tipo": "ricavo"},
    {"codice": "50300", "descrizione": "Noleggi e locazioni attive", "tipo": "ricavo"},
    {"codice": "50400", "descrizione": "Resi e rettifiche su vendite", "tipo": "ricavo"},
    {"codice": "51000", "descrizione": "PROVENTI FINANZIARI", "tipo": "ricavo"},
    {"codice": "51100", "descrizione": "Interessi attivi", "tipo": "ricavo"},
    {"codice": "51200", "descrizione": "Proventi da partecipazioni", "tipo": "ricavo"},
    {"codice": "51300", "descrizione": "Utili su cambi", "tipo": "ricavo"},
    {"codice": "52000", "descrizione": "ALTRI PROVENTI", "tipo": "ricavo"},
    {"codice": "52100", "descrizione": "Sopravvenienze attive", "tipo": "ricavo"},
    {"codice": "52200", "descrizione": "Rimborsi e indennizzi", "tipo": "ricavo"},
    {"codice": "52300", "descrizione": "Contributi in conto esercizio", "tipo": "ricavo"},
]


def _d(val) -> Decimal:
    """Converte un valore numerico in Decimal in modo sicuro."""
    if val is None:
        return Decimal("0.00")
    return Decimal(str(val))


class ContabilitaService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Numerazione ───────────────────────────────────────────────────────────

    async def _next_numero(self) -> int:
        result = await self.db.execute(
            select(func.max(RegistrazioneContabile.numero))
        )
        return (result.scalar() or 0) + 1

    # ── Saldo conto ───────────────────────────────────────────────────────────

    @staticmethod
    def _delta_saldo(tipo: str, dare: Decimal, avere: Decimal) -> Decimal:
        """
        Variazione di saldo per un conto in base al suo tipo.
        Attivo/Costo: saldo aumenta con dare, diminuisce con avere.
        Passivo/Ricavo: saldo aumenta con avere, diminuisce con dare.
        """
        if tipo in ("attivo", "costo"):
            return dare - avere
        return avere - dare

    # ── Registrazioni ─────────────────────────────────────────────────────────

    async def crea_registrazione(self, payload: RegistrazioneCreate) -> RegistrazioneDetail:
        # 1. Carica tutti i conti coinvolti in un'unica query
        conto_ids = [r.conto_id for r in payload.righe]
        res = await self.db.execute(select(Conto).where(Conto.id.in_(conto_ids)))
        conti_map: dict[int, Conto] = {c.id: c for c in res.scalars().all()}

        missing = set(conto_ids) - conti_map.keys()
        if missing:
            raise ValueError(f"Conti non trovati: {sorted(missing)}")

        # 2. Numero progressivo
        numero = await self._next_numero()

        # 3. Crea la testata
        reg = RegistrazioneContabile(
            numero=numero,
            data=payload.data,
            causale=payload.causale,
            tipo_causale=payload.tipo_causale,
            note=payload.note,
        )
        self.db.add(reg)
        await self.db.flush()  # ottieni l'ID

        # 4. Crea le righe e aggiorna i saldi dei conti
        for riga_in in payload.righe:
            conto = conti_map[riga_in.conto_id]
            self.db.add(RigaRegistrazione(
                registrazione_id=reg.id,
                conto_id=riga_in.conto_id,
                descrizione=riga_in.descrizione,
                dare=riga_in.dare,
                avere=riga_in.avere,
                aliquota_iva=riga_in.aliquota_iva,
                tipo_iva=riga_in.tipo_iva,
            ))
            conto.saldo += self._delta_saldo(conto.tipo, riga_in.dare, riga_in.avere)

        await self.db.commit()
        detail = await self.get_registrazione(reg.id)
        assert detail is not None
        return detail

    async def list_registrazioni(
        self,
        skip: int = 0,
        limit: int = 200,
        data_da: date | None = None,
        data_a: date | None = None,
    ) -> list[RegistrazioneSummary]:
        stmt = (
            select(
                RegistrazioneContabile.id,
                RegistrazioneContabile.numero,
                RegistrazioneContabile.data,
                RegistrazioneContabile.causale,
                RegistrazioneContabile.tipo_causale,
                RegistrazioneContabile.chiusa,
                RegistrazioneContabile.created_at,
                func.coalesce(func.sum(RigaRegistrazione.dare), 0).label("tot_dare"),
                func.coalesce(func.sum(RigaRegistrazione.avere), 0).label("tot_avere"),
            )
            .outerjoin(RigaRegistrazione, RigaRegistrazione.registrazione_id == RegistrazioneContabile.id)
            .group_by(RegistrazioneContabile.id)
            .order_by(RegistrazioneContabile.data.desc(), RegistrazioneContabile.numero.desc())
            .offset(skip)
            .limit(limit)
        )
        if data_da:
            stmt = stmt.where(RegistrazioneContabile.data >= data_da)
        if data_a:
            stmt = stmt.where(RegistrazioneContabile.data <= data_a)

        rows = (await self.db.execute(stmt)).all()
        return [
            RegistrazioneSummary(
                id=row.id,
                numero=row.numero,
                data=row.data,
                causale=row.causale,
                tipo_causale=row.tipo_causale,
                chiusa=row.chiusa,
                totale_dare=_d(row.tot_dare),
                totale_avere=_d(row.tot_avere),
                created_at=row.created_at,
            )
            for row in rows
        ]

    async def get_registrazione(self, reg_id: int) -> RegistrazioneDetail | None:
        stmt = (
            select(RegistrazioneContabile)
            .options(
                selectinload(RegistrazioneContabile.righe).selectinload(RigaRegistrazione.conto)
            )
            .where(RegistrazioneContabile.id == reg_id)
        )
        reg = (await self.db.execute(stmt)).scalar_one_or_none()
        if reg is None:
            return None

        righe_out = [
            RigaRegistrazioneOut(
                id=r.id,
                registrazione_id=r.registrazione_id,
                conto_id=r.conto_id,
                conto_codice=r.conto.codice,
                conto_descrizione=r.conto.descrizione,
                descrizione=r.descrizione,
                dare=r.dare,
                avere=r.avere,
                aliquota_iva=r.aliquota_iva,
                tipo_iva=r.tipo_iva,
            )
            for r in reg.righe
        ]
        tot_dare = sum((r.dare for r in reg.righe), Decimal("0"))
        tot_avere = sum((r.avere for r in reg.righe), Decimal("0"))

        return RegistrazioneDetail(
            id=reg.id,
            numero=reg.numero,
            data=reg.data,
            causale=reg.causale,
            tipo_causale=reg.tipo_causale,
            note=reg.note,
            chiusa=reg.chiusa,
            totale_dare=tot_dare,
            totale_avere=tot_avere,
            created_at=reg.created_at,
            righe=righe_out,
        )

    async def elimina_registrazione(self, reg_id: int) -> bool:
        stmt = (
            select(RegistrazioneContabile)
            .options(
                selectinload(RegistrazioneContabile.righe).selectinload(RigaRegistrazione.conto)
            )
            .where(RegistrazioneContabile.id == reg_id)
        )
        reg = (await self.db.execute(stmt)).scalar_one_or_none()
        if reg is None:
            return False
        if reg.chiusa:
            raise ValueError("Registrazione chiusa: impossibile eliminare")

        # Annulla l'effetto sui saldi dei conti
        for riga in reg.righe:
            delta = self._delta_saldo(riga.conto.tipo, riga.dare, riga.avere)
            riga.conto.saldo -= delta

        await self.db.delete(reg)
        await self.db.commit()
        return True

    async def chiudi_registrazione(self, reg_id: int) -> bool:
        res = await self.db.execute(
            select(RegistrazioneContabile).where(RegistrazioneContabile.id == reg_id)
        )
        reg = res.scalar_one_or_none()
        if reg is None:
            return False
        reg.chiusa = True
        await self.db.commit()
        return True

    # ── Bilancio di verifica ──────────────────────────────────────────────────

    async def get_bilancio(self) -> BilancioResponse:
        stmt = (
            select(
                Conto.id,
                Conto.codice,
                Conto.descrizione,
                Conto.tipo,
                func.coalesce(func.sum(RigaRegistrazione.dare), 0).label("totale_dare"),
                func.coalesce(func.sum(RigaRegistrazione.avere), 0).label("totale_avere"),
            )
            .outerjoin(RigaRegistrazione, RigaRegistrazione.conto_id == Conto.id)
            .group_by(Conto.id, Conto.codice, Conto.descrizione, Conto.tipo)
            .order_by(Conto.codice)
        )
        rows = (await self.db.execute(stmt)).all()

        conti: list[VoceBilancio] = []
        tot_dare = Decimal("0")
        tot_avere = Decimal("0")
        tot_attivo = Decimal("0")
        tot_passivo = Decimal("0")
        tot_costi = Decimal("0")
        tot_ricavi = Decimal("0")

        for row in rows:
            td = _d(row.totale_dare)
            ta = _d(row.totale_avere)
            saldo = (td - ta) if row.tipo in ("attivo", "costo") else (ta - td)

            conti.append(VoceBilancio(
                conto_id=row.id,
                codice=row.codice,
                descrizione=row.descrizione,
                tipo=row.tipo,
                totale_dare=td,
                totale_avere=ta,
                saldo=saldo,
            ))
            tot_dare += td
            tot_avere += ta

            if row.tipo == "attivo":
                tot_attivo += saldo
            elif row.tipo == "passivo":
                tot_passivo += saldo
            elif row.tipo == "costo":
                tot_costi += saldo
            elif row.tipo == "ricavo":
                tot_ricavi += saldo

        return BilancioResponse(
            conti=conti,
            totale_dare=tot_dare,
            totale_avere=tot_avere,
            totale_attivo=tot_attivo,
            totale_passivo=tot_passivo,
            totale_costi=tot_costi,
            totale_ricavi=tot_ricavi,
            utile_perdita=tot_ricavi - tot_costi,
        )

    # ── Liquidazione IVA ──────────────────────────────────────────────────────

    async def get_liquidazione_iva(
        self, data_da: date, data_a: date
    ) -> LiquidazioneIVAResponse:
        # Righe IVA (tipo_iva = 'iva'): dare = iva su acquisti, avere = iva su vendite
        stmt_iva = (
            select(
                RigaRegistrazione.aliquota_iva,
                func.sum(RigaRegistrazione.dare).label("iva_credito"),
                func.sum(RigaRegistrazione.avere).label("iva_debito"),
            )
            .join(RegistrazioneContabile, RigaRegistrazione.registrazione_id == RegistrazioneContabile.id)
            .where(RigaRegistrazione.tipo_iva == "iva")
            .where(RegistrazioneContabile.data >= data_da)
            .where(RegistrazioneContabile.data <= data_a)
            .group_by(RigaRegistrazione.aliquota_iva)
        )

        # Righe imponibili (tipo_iva = 'imponibile'): per calcolare la base
        stmt_imp = (
            select(
                RigaRegistrazione.aliquota_iva,
                func.sum(RigaRegistrazione.dare).label("imp_dare"),
                func.sum(RigaRegistrazione.avere).label("imp_avere"),
            )
            .join(RegistrazioneContabile, RigaRegistrazione.registrazione_id == RegistrazioneContabile.id)
            .where(RigaRegistrazione.tipo_iva == "imponibile")
            .where(RegistrazioneContabile.data >= data_da)
            .where(RegistrazioneContabile.data <= data_a)
            .group_by(RigaRegistrazione.aliquota_iva)
        )

        iva_rows = (await self.db.execute(stmt_iva)).all()
        imp_rows = (await self.db.execute(stmt_imp)).all()

        # Mappe per aliquota
        iva_map: dict[str, dict] = {}
        for row in iva_rows:
            key = str(_d(row.aliquota_iva))
            iva_map[key] = {
                "iva_credito": _d(row.iva_credito),
                "iva_debito": _d(row.iva_debito),
            }

        imp_map: dict[str, dict] = {}
        for row in imp_rows:
            key = str(_d(row.aliquota_iva))
            imp_map[key] = {
                "imp_acquisti": _d(row.imp_dare),
                "imp_vendite": _d(row.imp_avere),
            }

        # Unisci per aliquota
        aliquote = sorted(set(iva_map.keys()) | set(imp_map.keys()))
        dettaglio: list[RigaIVA] = []
        tot_iva_credito = Decimal("0")
        tot_iva_debito = Decimal("0")

        for aliq_str in aliquote:
            iva_data = iva_map.get(aliq_str, {"iva_credito": Decimal("0"), "iva_debito": Decimal("0")})
            imp_data = imp_map.get(aliq_str, {"imp_acquisti": Decimal("0"), "imp_vendite": Decimal("0")})

            tot_iva_credito += iva_data["iva_credito"]
            tot_iva_debito += iva_data["iva_debito"]

            dettaglio.append(RigaIVA(
                aliquota_iva=Decimal(aliq_str),
                imponibile_acquisti=imp_data["imp_acquisti"],
                iva_a_credito=iva_data["iva_credito"],
                imponibile_vendite=imp_data["imp_vendite"],
                iva_a_debito=iva_data["iva_debito"],
            ))

        return LiquidazioneIVAResponse(
            data_da=data_da,
            data_a=data_a,
            iva_a_credito=tot_iva_credito,
            iva_a_debito=tot_iva_debito,
            saldo_iva=tot_iva_debito - tot_iva_credito,
            dettaglio=dettaglio,
        )

    # ── Piano dei conti standard ──────────────────────────────────────────────

    async def inizializza_piano_conti(self) -> InizializzaPianoContiResponse:
        existing = {
            r for r in (await self.db.execute(select(Conto.codice))).scalars()
        }
        creati = 0
        esistenti = 0
        for entry in PIANO_CONTI_STANDARD:
            if entry["codice"] in existing:
                esistenti += 1
            else:
                self.db.add(Conto(
                    codice=entry["codice"],
                    descrizione=entry["descrizione"],
                    tipo=entry["tipo"],
                    saldo=Decimal("0.00"),
                ))
                creati += 1

        if creati:
            await self.db.commit()

        return InizializzaPianoContiResponse(
            conti_creati=creati,
            conti_esistenti=esistenti,
            message=(
                f"Piano dei conti inizializzato: {creati} conti creati"
                + (f", {esistenti} già presenti." if esistenti else ".")
            ),
        )

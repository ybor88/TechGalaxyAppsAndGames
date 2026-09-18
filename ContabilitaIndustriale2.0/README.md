# BusinessFlow ERP

Applicazione web per la gestione della contabilità aziendale e del ciclo operativo amministrativo.
**100% open source, zero costi di licenza.**

## Stack tecnologico

| Layer | Tecnologia |
|-------|-----------|
| Backend | Python 3.12 · FastAPI · SQLAlchemy 2 |
| Frontend | Next.js 15 · React 19 · TailwindCSS · Recharts |
| Database | PostgreSQL 16 + pgvector |
| Cache / Queue | Redis · Celery |
| Storage | MinIO |
| AI locale | Ollama (Llama 3 / Mistral) |
| OCR | Tesseract OCR |
| PDF | WeasyPrint · ReportLab |

## Avvio rapido

### Prerequisiti
- Docker Desktop
- (opzionale) Node.js 22 e Python 3.12 per sviluppo locale senza Docker

### Con Docker Compose

```bash
docker compose up -d
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8000
- API Docs (Swagger): http://localhost:8000/docs
- MinIO Console: http://localhost:9001
- Ollama: http://localhost:11434

### Sviluppo locale (senza Docker)

**Backend**
```bash
cd backend
python -m venv .venv
.venv\Scripts\activate      # Windows
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

**Frontend**
```bash
cd frontend
npm install
npm run dev
```

## Struttura del progetto

```
businessflow/
├── backend/
│   └── app/
│       ├── main.py          # Entry point FastAPI
│       ├── config.py        # Configurazione (Settings)
│       ├── database.py      # Engine SQLAlchemy async
│       ├── models/          # ORM models
│       ├── schemas/         # Pydantic schemas
│       ├── routers/         # Route handlers
│       └── services/        # Business logic
├── frontend/
│   └── src/
│       ├── app/             # Next.js App Router
│       ├── components/      # React components
│       └── lib/             # API client, utility
└── docker-compose.yml
```

## Funzionalità implementate

- [x] **F1 — Dashboard Finanziaria**: KPI, entrate/uscite, cashflow, indicatore liquidità
- [ ] F2 — Fatturazione e gestione documentale
- [ ] F3 — OCR Contabile
- [ ] F4 — Contabilità Generale
- [ ] F5 — CRM Economico
- [ ] F6 — Workflow Aziendale
- [ ] F7 — Forecasting Aziendale
- [ ] F8 — AI Assistant Locale

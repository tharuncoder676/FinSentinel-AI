# FinSentinel AI — Local Setup & Production Deployment Runbook (Supabase + NVIDIA/Mistral + Vercel)

## 1. Prerequisites
- Java 21 LTS (Temurin / OpenJDK 21)
- Node.js 20+ & npm
- PostgreSQL 16 (local) or Supabase Managed PostgreSQL (production)
- Optional: Mailpit (local SMTP testing on port 1025, UI/API on port 8025)
- Optional LLM API keys (server-side only): `NVIDIA_API_KEY`, `MISTRAL_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `ANTHROPIC_API_KEY`, or `AI_LOCAL_BASE_URL`

## 2. Environment Variables
Copy `.env.example` to `.env` for local runs. **Never commit `.env` or API keys to Git.**
- **Database**: `DATABASE_URL` (or `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`)
- **Security & Cross-Origin Cookies**: `SESSION_COOKIE_NAME` (`FS_SESSION`), `SESSION_COOKIE_SECURE`, `SESSION_COOKIE_SAMESITE` (`Lax` locally/same-origin, `None` when Vercel frontend hits a separate HTTPS backend origin), `FRONTEND_ORIGIN` (comma-separated allowed origins/patterns)
- **AI & RAG (3-Key NVIDIA NIM -> Mistral Failover)**:
  - `AI_PROVIDER`: `nvidia` (recommended; tries all NVIDIA keys sequentially and falls back to Mistral ONLY if all 3 fail) | `mistral` | `openai` | `gemini` | `anthropic` | `local`
  - `AI_MODEL`: `meta/llama-3.1-70b-instruct` or `nvidia/llama-3.1-nemotron-70b-instruct` (NVIDIA NIM)
  - `MISTRAL_MODEL`: `mistral-large-latest` (used automatically when failover switches to Mistral)
  - `AI_EMBEDDING_MODEL`: defaults to `nvidia/nv-embedqa-e5-v5`
  - `AI_RAG_ENABLED`: `true` (seeds 8 Financial Skill Packs & enables user custom skill ingestion + PostgreSQL FTS/pgvector hybrid retrieval)
  - `NVIDIA_API_KEY`, `NVIDIA_API_KEY_2`, `NVIDIA_API_KEY_3` (or comma-separated `NVIDIA_API_KEYS`), plus `MISTRAL_API_KEY` (kept strictly server-side)

---

## 3. Clean Checkout Local Startup
### Option A: Docker Compose (when Docker daemon is available)
```bash
docker compose -f infra/docker-compose.yml up --build
```
- Frontend: `http://localhost:5173`
- Backend API & Swagger UI: `http://localhost:8080/swagger-ui.html`
- Mailpit UI: `http://localhost:8025`

### Option B: Direct Local Services (Java + Vite + PostgreSQL 16)
1. Ensure PostgreSQL 16 is running on `localhost:5432` with database `finsentinel` and user `finsentinel`.
2. Start backend:
   ```bash
   cd backend
   ./mvnw spring-boot:run   # Windows: .\mvnw.cmd spring-boot:run
   ```
3. Start frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

---

## 4. Production Deployment: Supabase + Container Backend + Vercel

> **Important Architecture Note:** Vercel hosts the static React 19 SPA (`frontend/`), Supabase hosts managed PostgreSQL + `pgvector`, and the Spring Boot 3.4 (Java 21) API (`backend/Dockerfile`) runs on any Docker container host (Render, Railway, Fly.io, Google Cloud Run) connected to Supabase PostgreSQL.

### Step A — Supabase Database Setup
1. Create a Supabase project at [supabase.com](https://supabase.com).
2. Open **SQL Editor** (or **Database -> Extensions**) and ensure the `vector` (`pgvector`) extension is enabled.
3. Copy your **Session Pooler** JDBC connection string (**Connect -> Session pooler**, port `5432`, or Transaction pooler port `6543` with `?prepareThreshold=0`):
   ```text
   jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
   ```
4. Set backend environment variables:
   - `DATABASE_URL`: `jdbc:postgresql://...pooler.supabase.com:5432/postgres?sslmode=require`
   - `POSTGRES_USER`: `postgres.<your-project-ref>`
   - `POSTGRES_PASSWORD`: `<your-supabase-db-password>`
   - On first boot, Flyway (`V1`–`V4`) automatically creates all tables, enables Row Level Security (RLS) on public RAG tables, creates `pgvector` HNSW indexes when available, and seeds the built-in Financial Intelligence Skill Packs.

### Step B — Spring Boot Backend Hosting (Render / Railway / Fly.io)
Deploy `backend/` using the included `backend/Dockerfile` with these environment variables:
- `SERVER_PORT=8080`
- `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (from Step A)
- `AI_PROVIDER=nvidia`
- `AI_MODEL=meta/llama-3.1-70b-instruct`
- `MISTRAL_MODEL=mistral-large-latest`
- `NVIDIA_API_KEY=<nvapi-key-1>`
- `NVIDIA_API_KEY_2=<nvapi-key-2>`
- `NVIDIA_API_KEY_3=<nvapi-key-3>`
- `MISTRAL_API_KEY=<mistral-fallback-key>` *(only called if all 3 NVIDIA keys fail)*
- `AI_EMBEDDING_MODEL=nvidia/nv-embedqa-e5-v5`
- `AI_RAG_ENABLED=true`
- `FRONTEND_ORIGIN=https://<your-project>.vercel.app,https://*.vercel.app`
- `APP_BASE_URL=https://<your-project>.vercel.app`
- `SESSION_COOKIE_SECURE=true`
- `SESSION_COOKIE_SAMESITE=None` *(if calling the backend URL directly via `VITE_API_BASE_URL`; keep `Lax` if using a Vercel reverse-proxy rewrite)*
- `DEMO_SEED_ENABLED=true` *(set `false` once real user traffic begins)*

### Step C — Vercel Frontend Deployment (`frontend/`)
1. Import the repository into [vercel.com](https://vercel.com) with **Root Directory** set to `frontend`.
2. Vercel auto-detects `frontend/vercel.json` (`framework: vite`, `buildCommand: npm run build`, `outputDirectory: dist`, SPA rewrite + security headers).
3. Configure API routing in one of two ways:
   - **Option 1 (Direct API Base URL):** Set Vercel Environment Variable `VITE_API_BASE_URL=https://<your-backend-host>` (no trailing slash) and ensure `SESSION_COOKIE_SECURE=true` + `SESSION_COOKIE_SAMESITE=None` on the backend.
   - **Option 2 (Recommended First-Party Rewrite):** Add `{ "source": "/api/:path*", "destination": "https://<your-backend-host>/api/:path*" }` before the SPA catch-all rewrite in `frontend/vercel.json` and leave `VITE_API_BASE_URL` blank. Cookies remain strictly same-origin (`SameSite=Lax`).
4. Deploy and verify `https://<your-project>.vercel.app/assistant` shows `Live AI provider 'nvidia' configured` (or `mistral`) and `RAG Skills` active.

---

## 5. Backup & Restore Verification
- Windows: `powershell -ExecutionPolicy Bypass -File infra/backup-restore.ps1`
- Linux/macOS: `bash infra/backup-restore.sh`

## 6. Production Readiness Notice
Do not deploy publicly with default local passwords or `SESSION_COOKIE_SECURE=false`. Production deployments handling real user financial data require HTTPS termination, managed secret storage, independent security review, and legal/privacy compliance verification.

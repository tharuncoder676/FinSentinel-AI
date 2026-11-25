# FinSentinel AI — Local Setup & Deployment Runbook

## 1. Prerequisites
- Java 21 LTS (Temurin / OpenJDK 21)
- Node.js 20+ & npm
- PostgreSQL 16 (or Docker Desktop / Docker Compose)
- Optional: Mailpit (local SMTP testing on port 1025, UI/API on port 8025)
- Optional: LLM API key (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `ANTHROPIC_API_KEY`, or `AI_LOCAL_BASE_URL`)

## 2. Environment Variables
Copy `.env.example` to `.env` and configure values as needed. **Never commit `.env` or API keys to Git.**
- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `SESSION_COOKIE_NAME` (`FS_SESSION`), `SESSION_COOKIE_SECURE` (`false` for HTTP localhost, `true` behind HTTPS)
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_ENABLED`
- `AI_PROVIDER` (`openai` | `gemini` | `anthropic` | `local`), `AI_MODEL`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `ANTHROPIC_API_KEY`, `AI_REQUEST_TIMEOUT`, `AI_MAX_OUTPUT_TOKENS`

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

## 4. Backup & Restore Verification
- Windows: `powershell -ExecutionPolicy Bypass -File infra/backup-restore.ps1`
- Linux/macOS: `bash infra/backup-restore.sh`

## 5. Production Readiness Notice
Do not deploy publicly with default local passwords or `SESSION_COOKIE_SECURE=false`. Production deployments handling real user financial data require HTTPS termination, managed secret storage, independent security review, and legal/privacy compliance verification.

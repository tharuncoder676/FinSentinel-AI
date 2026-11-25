# FinSentinel AI — Executed Test & Benchmark Evidence

## 1. Backend Automated Suite (Java 21 + PostgreSQL 16.6 + Flyway)
- **Command**: `cd backend && .\mvnw.cmd package`
- **Exit Code**: `0`
- **Result**: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`
  - `EndToEndPostgresIntegrationTest`: `1 passed` (Flyway `V1`–`V4` against live PostgreSQL 16.6; auth registration/login/HTTP-only cookies; empty-workspace state; cross-user IDOR `404`; ledger arithmetic `132,000`; transfer exclusion; partial payment without double-counting; Digital Twin non-mutation; CSV valid + malformed preview; AI prompt injection block + deterministic fallback; RAG system skill seeding (`>=8` docs), custom skill ingestion (`201`), PostgreSQL FTS + hybrid retrieval, cross-user RAG isolation, injection rejection (`400`), AI chat `knowledgeSources`; OpenPDF `%PDF` report generation & cross-user `404`; Admin `403` escalation & `200` overview; notification idempotent deduplication; account deletion `401`).
  - `AiProviderFactoryTest`: `2 passed` (ordered 3-key NVIDIA NIM failover chain -> `mistral` fallback with dedicated `mistralModel`; comma-separated key parsing, deduplication, and offline empty-embedding degradation).
  - `ForecastEngineUnitTest`: `3 passed` (same-day ordering, leap/non-leap Feb 31->28/29 clamping, shortfall vs reserve breach & recovery).
  - `RiskEngineUnitTest`: `4 passed` (threshold boundaries 0/30/31/60/61/80/81/100, bounded `[0,1]` normalization & reproducibility, monotonicity, missing-data `INSUFFICIENT_DATA` without silent zeroing).
  - `PaymentAndEmiUnitTest`: `5 passed` (fixed-rate amortizing principal reconciliation, zero-interest branch reconciliation, variable-rate unsupported flag, invalid rate rejection, `PriorityQueue` tie-breaking).
  - `CsvFormulaInjectionUnitTest`: `1 passed` (`=`, `+`, `-`, `@` prefix escaping).

## 2. Frontend Unit & Build Verification
- **Command**: `cd frontend && npm.cmd test && npm.cmd run build`
- **Exit Code**: `0`
- **Vitest**: `1 passed file, 2 passed tests` (`LandingPage` exact hero text, slider preview, English/Tamil switching).
- **Production Build (`tsc -b && vite build`)**: Built `2492` modules cleanly (`dist/index.html`, `dist/assets/index-*.css`, `dist/assets/index-*.js`).

## 3. Playwright E2E, Multi-Viewport, Screenshot & WCAG 2.2 AA Audit
- **Command**: `cd frontend && npm.cmd run test:e2e`
- **Exit Code**: `0`
- **Result**: `2 passed` (100% pass, `0` console errors, `0` serious/critical `@axe-core/playwright` WCAG 2.2 AA violations on Landing and Dashboard, `0` horizontal overflow at `360px`, `390px`, `768px`, `1024px`, `1440px`; verified SSE streaming completion cleanly via `DispatcherType.ASYNC` and RAG Knowledge Skills cited inside `/app/assistant`).
- **Captured Screenshots (`docs/screenshots/`)**:
  - `landing-desktop-1440.png` (266,181 bytes)
  - `login-desktop-1440.png` (80,563 bytes)
  - `dashboard-light-1440.png` (278,176 bytes)
  - `dashboard-dark-1440.png` (279,119 bytes)
  - `dashboard-mobile-390.png` (227,109 bytes)
  - `risk-anatomy-1440.png` (176,928 bytes)
  - `digital-twin-1440.png` (207,246 bytes)
  - `payment-planner-1440.png` (221,773 bytes)
  - `ai-assistant-1440.png` (190,856 bytes)

## 4. Email, OpenAPI, Backup/Restore & Dependency Scans
- **Mailpit SMTP Verification**: `http://127.0.0.1:8025/api/v1/messages` recorded `15` delivered verification messages during automated test runs.
- **OpenAPI Docs**: `http://127.0.0.1:8080/v3/api-docs` verified `76` documented API paths.
- **PostgreSQL Backup & Restore (`infra/backup-restore.ps1`)**: Exit code `0`; `pg_dump` exported `146,436` bytes and restored cleanly into disposable database `finsentinel_restore_check` with `9` verified `users` rows.
- **Frontend `npm audit --omit=dev`**: Exit code `1` reporting 1 high advisory (`zod <4.6.6`, GHSA-mrhx-c7xc-m3w5; fix requires major upgrade `zod@4.6.7`). Recorded openly for follow-up evaluation.

## 5. Repeatable Local Performance Measurements
- **Hardware**: `13th Gen Intel(R) Core(TM) i5-13450HX` (10 cores / 16 logical processors), `15.7 GB` RAM, Windows 11, local PostgreSQL 16.6, JVM 21.0.13.
- **Dataset**: Seeded Demo Household Profile (`2` accounts, `2` income streams, `16` transactions, `3` expense templates, `2` goals, `5` obligations).
- **Sequential `GET /api/v1/dashboard?period=90d`** (30 requests): `avg = 49.55ms`, `p50 = 43.65ms`, `p95 = 85.01ms`, `errors = 0`.
- **Concurrent `GET /api/v1/dashboard?period=90d`** (50 requests, concurrency `5`): `wall = 469ms`, `avg = 42.67ms`, `p50 = 37.68ms`, `p95 = 65.03ms`, `errors = 0`.
- **Concurrent `POST /api/v1/scenarios/simulate`** (90-day Digital Twin, 50 requests, concurrency `10`): `wall = 294ms`, `avg = 50.04ms`, `p50 = 44.91ms`, `p95 = 70.65ms`, `errors = 0`.

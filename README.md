# FinSentinel AI

**See the risk. Plan the future.**

FinSentinel AI is a predictive financial intelligence and adaptive payment planning platform built with **Java 21 LTS / Spring Boot**, **React / TypeScript / Vite**, and **PostgreSQL 16**.

## Core Capabilities
- **Single-Source-of-Truth Financial Ledger**: Accounts, income streams, transactions (transfers, refunds, duplicate detection), recurring expense templates, savings goals, and CSV statement import/export (formula-injection safe).
- **Explainable Risk Engine (`risk-policy-v1.0`)**: Deterministic 0–100 vulnerability index across 6 weighted factors with bounded `[0, 1]` normalization, explicit missing-data handling, and persisted snapshot history.
- **Deterministic Cash-Flow Forecasting (`cashflow-deterministic-v1.0`)**: `BigDecimal` (`HALF_UP`) event-driven daily projections with documented same-day ordering, month-end clamping, and distinct shortfall vs reserve-breach tracking.
- **Financial Digital Twin & Stress Test Laboratory**: Interactive sliders, timeline scrubbing, 6 adverse shock templates, multi-case comparison, and saved scenarios—strictly isolated from live ledger records.
- **Adaptive Payment Planner & EMI Advisor**: Java `PriorityQueue` obligation ranking, feasible schedule comparison, hypothetical calendar simulation, manual settlement recording without double-counting, and reconciled `BigDecimal` EMI amortization.
- **Grounded AI Financial Assistant**: Configurable OpenAI, Google Gemini, Anthropic, and local OpenAI-compatible adapters with SSE streaming, cancellation, prompt-injection defense, 8 server-scoped tools, and transparent deterministic fallback when offline.
- **Multilingual, Accessible & Printable**: Light/Dark themes, English/Tamil (`en`/`ta`), WCAG 2.2 AA checked UI, OpenPDF report generation, Spring Scheduler reminders, and role-protected Admin metrics.

## Quick Start
See [docs/deployment-runbook.md](docs/deployment-runbook.md) and [docs/demo-runbook.md](docs/demo-runbook.md).

```bash
# Backend tests & server (requires PostgreSQL 16 on localhost:5432)
cd backend
./mvnw test
./mvnw spring-boot:run

# Frontend dev server & tests
cd frontend
npm install
npm test
npm run dev
```

# FinSentinel AI — System Architecture & Data Flows

## 1. Architectural Style
FinSentinel AI is implemented as a **modular monolith** in **Java 21 LTS / Spring Boot 3.4.3** backed by **PostgreSQL 16**, paired with a **React 19 / TypeScript / Vite / Tailwind CSS** frontend.

## 2. Layered Backend Structure (`com.finsentinel`)
`Controller -> Application Service -> Domain Strategy/Logic -> JPA Repository -> PostgreSQL`

| Module | Package | Responsibility |
| :--- | :--- | :--- |
| Auth & Session | `com.finsentinel.auth` | Registration, BCrypt(12), HTTP-only `FS_SESSION` cookies, CSRF, verification/reset single-use expiring tokens, account export & deletion |
| Profile & Onboarding | `com.finsentinel.profile` | Multi-step baseline capture, skip-to-workspace, demo session entry |
| Ledger & CSV | `com.finsentinel.ledger` | Single-source-of-truth account balances, transfers, refunds, duplicate detection, CSV preview/import & formula-safe export |
| Explainable Risk | `com.finsentinel.risk` | Strategy/Factory `StandardRiskPolicyV1` (0–100 vulnerability score, 6 weighted factors, bounded 0–1 normalization, missing-data handling, persisted snapshots) |
| Cash-Flow Forecast | `com.finsentinel.forecast` | `DeterministicEventForecastStrategy` using `BigDecimal` (`HALF_UP`), documented same-day ordering, month-end clamping, shortfall vs reserve breach, timeline |
| Digital Twin & Stress | `com.finsentinel.simulation` | Read-only scenario simulation, 6 stress test templates, multi-case comparison, save/reopen/delete scenarios without ledger mutation |
| Payment Intelligence | `com.finsentinel.payment` | Java `PriorityQueue` obligation planner, feasible schedules vs explicit shortfall, manual recording without double counting, `BigDecimal` EMI & method comparisons |
| Statistical ML Layer | `com.finsentinel.ml` | Moving averages, exponential smoothing (`alpha = 0.35`), z-score spending anomalies with confirm/dismiss, user-scoped category suggestions, honest MAE/insufficient-data reporting |
| AI Orchestration | `com.finsentinel.ai` | Strategy/Factory adapters (OpenAI, Gemini, Anthropic, Local), SSE streaming, cancellation, timeouts, 8 typed user-scoped tools, prompt-injection fencing, deterministic fallback |
| Notifications | `com.finsentinel.notification` | Spring `@Scheduled` idempotent reminder delivery, timezone-aware checks, deduplication keys, retry limits, optional email |
| PDF Reporting | `com.finsentinel.report` | OpenPDF server-side report rendering with generation timestamps, policy/model versions, and strict owner download checks |
| Admin & Audit | `com.finsentinel.admin`, `common.audit` | Role-protected (`ADMIN`) aggregated privacy-preserving metrics, redacted audit logs, runtime configuration |

## 3. Data Integrity Contracts
- **Single Source of Truth**: Account balances derive from `opening_balance + credits - debits +/- transfers` on `occurred_on >= opening_balance_date`.
- **No Double-Counting**: Recording a manual `PaymentRecord` updates `financial_obligations.paid_amount` and optionally inserts a linked `Transaction` (`linked_payment_record_id`). Projections include only `obligation.remainingAmount`.
- **Simulation Isolation**: `SimulationService` and hypothetical calendar drag-and-drop operate strictly in memory/scenario tables and never mutate `accounts`, `transactions`, or `financial_obligations`.

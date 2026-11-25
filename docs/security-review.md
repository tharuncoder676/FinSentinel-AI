# FinSentinel AI — Security & Privacy Review

## 1. Implemented Security Controls
- **Password Hashing**: BCrypt (`BCryptPasswordEncoder` with strength 12) in `SecurityConfig.java`. Plaintext passwords are never logged or persisted.
- **Session Management**: Server-side `auth_sessions` storing SHA-256 hashes of 256-bit `SecureRandom` tokens, delivered exclusively via `HttpOnly`, `SameSite=Lax` cookies (`FS_SESSION`).
- **CSRF Protection**: `CookieCsrfTokenRepository.withHttpOnlyFalse()` (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header) enforced on all authenticated state-changing endpoints (`POST`, `PUT`, `PATCH`, `DELETE`).
- **Single-Use Expiring Verification & Reset Tokens**: Stored as SHA-256 hashes (`verification_tokens`, `password_reset_tokens`), invalidated upon single use and expiry; `forgot-password` returns a generic response to prevent account enumeration. Password reset revokes all active user sessions.
- **Authorization & IDOR Prevention**: Every domain repository query and service method filters strictly by `principal.userId()` extracted from the authenticated session (`UserSessionPrincipal`). Admin endpoints (`/api/v1/admin/**`) enforce `@PreAuthorize("hasRole('ADMIN')")` and return only aggregated metrics and redacted audit logs.
- **Rate Limiting**: `RateLimitFilter` applies per-IP sliding-window limits on auth endpoints (`30/min`) and per-user limits on `/api/v1/ai/**` (`25/min`), returning HTTP `429` and recording an audit event.
- **Security Headers & CORS**: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, Content Security Policy (`frame-ancestors 'none'`), and strict origin allowlist.
- **CSV Safety**: Upload size capped at 2 MB and 2,000 rows; CSV exports escape leading `=`, `+`, `-`, `@`, `\t`, and `\r` characters to prevent spreadsheet formula injection.
- **AI Safety & Prompt Injection Mitigation**: Prompt length capped at 2,000 characters; prompt-injection patterns blocked (`PROMPT_INJECTION_BLOCKED`); strict 8-tool allowlist; user IDs inside tool payloads rejected (`AI_CROSS_USER_TOOL_BLOCKED`); external AI calls gated by user consent (`ai_external_consent`); API keys kept server-side in environment variables only.
- **Data Portability & Deletion**: Authenticated users can export their account/profile JSON (`/api/v1/auth/export`) and permanently delete their account and cascaded records (`/api/v1/auth/account`).

## 2. Automated Security Verification Results
- `EndToEndPostgresIntegrationTest`: Verified HTTP-only cookie flag, cross-user IDOR rejection (`404` on other user's account and PDF report download), role escalation block (`403` for `USER` on `/api/v1/admin/overview`), prompt-injection rejection (`400 PROMPT_INJECTION_BLOCKED`), and account deletion invalidating sessions (`401`).
- `CsvFormulaInjectionUnitTest`: Verified neutralization of `=`, `+`, `-`, and `@` formula prefixes.
- Playwright E2E (`finsentinel.spec.ts`): Verified fresh user isolation and `/app/admin` access denial for normal users.

## 3. Dependency & Environment Notes
- Backend dependencies: Spring Boot 3.4.3, Spring Security 6, Hibernate 6.6, PostgreSQL JDBC, Flyway, OpenPDF 2.0.3, Commons CSV 1.12.0.
- Frontend `npm audit`: Reported 4 transitive/dev advisories (detailed in `docs/test-evidence.md`).
- **Disclaimer**: Official regulatory compliance (e.g., RBI/DPDP/SOC2) is not claimed; deployments handling real user financial data require independent penetration testing and legal/privacy review.

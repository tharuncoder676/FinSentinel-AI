# FinSentinel AI — National-Level Demonstration Runbook

## Demo Profile Credentials (Explicitly Labeled Fictional Data)
- **Email**: `demo@finsentinel.local`
- **Password**: `DemoPass#2026`
- **Role**: `USER` (`demoUser = true`)
- **Reset Endpoint**: `POST /api/v1/demo/reset` (or click **Reset Demo Data** in the top demo banner)

## 11-Step Reproducible Walkthrough
1. **Login**: Open `/auth/login` and sign in with `demo@finsentinel.local` / `DemoPass#2026` (or click **Explore Labeled Demo Workspace**).
2. **Financial Intelligence Dashboard**: Inspect `/app/overview` — available balance, trailing income/expenses (with transfers excluded), Risk Radar, Cash Flow Intelligence Chart (7d/30d/90d/12m + accessible table), Upcoming Payments, and Grounded Intelligence Feed.
3. **Explainable Risk Score**: Open `/app/risk` to inspect the 0–100 vulnerability score (`risk-policy-v1.0`), 6 weighted factor contributions, bounded `[0,1]` normalizations, and historical snapshot trend.
4. **Financial Digital Twin**: Open `/app/digital-twin`. Observe the baseline trajectory and zero-mutation guarantee.
5. **Salary Delay + Unexpected Expense**: Set **Salary / Income Delay** to `15 days` and **Unexpected Expense** to `25000`.
6. **Recalculated Cash-Flow Projection**: Inspect the synchronized baseline vs modified trajectory, updated minimum projected balance, reserve breach / shortage date, and risk score delta.
7. **Affected Obligations**: Inspect the highlighted obligations experiencing reserve pressure or shortfall under the simulated delay/shock.
8. **Compare Alternative Schedules**: Open `/app/payments` to compare Contractual Due-Date, Reserve-Preserving Staggered, and Essential-First PriorityQueue schedules, plus test hypothetical drag-and-drop without modifying contractual due dates.
9. **Ask AI Why Risk Changed**: Open `/app/assistant` and ask *"Why did my risk score change and what payments are upcoming?"*
10. **Inspect Backend-Grounded Explanation**: Expand **Inspect Backend-Grounded Calculations** to verify tool calls (`getFinancialSummary`, `getRiskAssessment`, `getUpcomingPayments`, `comparePaymentScenarios`, `explainRiskFactors`) and verify transparent deterministic notice when no live LLM key is configured.
11. **Export Scenario / Risk Report**: Open `/app/reports` (or click **Export Scenario PDF** in Digital Twin) to generate and download the PDF report.

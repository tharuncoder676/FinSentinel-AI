import { HelpCircle } from 'lucide-react';

export function HelpPage() {
  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center gap-2">
        <HelpCircle className="w-5 h-5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Methodology, Shortcuts & Support</h1>
      </div>

      <section className="surface-card p-5 space-y-3">
        <h2 className="text-base font-semibold m-0">Deterministic Calculation Contracts</h2>
        <ul className="text-sm space-y-2 m-0 pl-5" style={{ color: 'var(--text-secondary)' }}>
          <li><strong>Risk Policy (`risk-policy-v1.0`)</strong>: Weighted 0–100 vulnerability index across Debt Burden (25%), Cash-Flow Deficit (25%), Emergency Savings Coverage (20%), Expense Pressure (15%), Income Variability (10%), and Payment Urgency (5%).</li>
          <li><strong>Cash-Flow Forecast (`cashflow-deterministic-v1.0`)</strong>: BigDecimal HALF_UP daily ledger simulation with same-day ordering: Income → Savings → Essential Obligations → Essential Expenses → Discretionary Obligations → Discretionary Expenses.</li>
          <li><strong>Keyboard Shortcuts</strong>: Press <code>Ctrl+K</code> (or <code>Cmd+K</code>) anywhere inside the workspace to open the Command Palette.</li>
        </ul>
      </section>
    </div>
  );
}

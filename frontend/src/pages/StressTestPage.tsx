import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Flame, CheckCircle2 } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface StressTemplate {
  templateKey: string;
  title: string;
  description: string;
  defaultParameters: Record<string, unknown>;
}

interface SimResult {
  name: string;
  templateKey: string;
  ledgerMutated: boolean;
  baselineForecast: { minProjectedBalance: number; reserveThreshold: number };
  scenarioForecast: {
    minProjectedBalance: number;
    shortageDate: string | null;
    reserveBreachDate: string | null;
    recoveryDate: string | null;
    recoveryMessage: string;
    affectedObligations: Array<{ obligationId: string; title: string; dueDate: string; projectedBalanceOnDueDate: number }>;
  };
  riskBefore: { displayScore: number; category: string };
  riskAfter: { displayScore: number; category: string };
  impactSummary: string[];
}

export function StressTestPage() {
  const { formatMoney } = useApp();
  const [selectedKeys, setSelectedKeys] = useState<string[]>(['INCOME_REDUCED_20', 'SALARY_DELAYED_15', 'UNEXPECTED_MEDICAL_EXPENSE']);
  const [results, setResults] = useState<SimResult[]>([]);

  const { data: templates = [] } = useQuery({
    queryKey: ['stress-templates'],
    queryFn: () => apiRequest<StressTemplate[]>('/api/v1/stress-tests/templates'),
  });

  const runBatchMut = useMutation({
    mutationFn: () => {
      const cases = templates
        .filter((t) => selectedKeys.includes(t.templateKey))
        .map((t) => t.defaultParameters);
      return apiRequest<SimResult[]>('/api/v1/scenarios/compare', {
        method: 'POST',
        body: JSON.stringify({ cases }),
      });
    },
    onSuccess: (data) => setResults(data),
  });

  const toggleKey = (key: string) => {
    setSelectedKeys((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : prev.length < 5 ? [...prev, key] : prev
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Financial Stress Test Laboratory</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Select up to 5 adverse shock templates to compare shortage dates, reserve depletion, affected obligations, and recovery timelines.
          </p>
        </div>
        <button
          type="button"
          disabled={selectedKeys.length === 0 || runBatchMut.isPending}
          onClick={() => runBatchMut.mutate()}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-xs font-semibold cursor-pointer disabled:opacity-50"
          style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
        >
          <Flame className="w-4 h-4" aria-hidden="true" />
          {runBatchMut.isPending ? 'Running Stress Tests...' : `Run Comparison (${selectedKeys.length})`}
        </button>
      </div>

      <section aria-label="Stress Test Templates" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {templates.map((tpl) => {
          const active = selectedKeys.includes(tpl.templateKey);
          return (
            <button
              key={tpl.templateKey}
              type="button"
              onClick={() => toggleKey(tpl.templateKey)}
              aria-pressed={active}
              className="surface-card p-4 text-left cursor-pointer transition-colors"
              style={{
                borderColor: active ? 'var(--accent)' : 'var(--border)',
                background: active ? 'var(--elevated)' : 'var(--surface)',
              }}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-semibold">{tpl.title}</span>
                {active && <CheckCircle2 className="w-4 h-4 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />}
              </div>
              <p className="text-xs mt-1.5 mb-0" style={{ color: 'var(--text-secondary)' }}>{tpl.description}</p>
            </button>
          );
        })}
      </section>

      {results.length > 0 && (
        <section className="surface-card overflow-hidden" aria-label="Stress Test Comparison Results">
          <div className="p-4 border-b" style={{ borderColor: 'var(--border)' }}>
            <h2 className="text-base font-semibold m-0">Multi-Case Stress Comparison (Read-Only — Ledger Unchanged)</h2>
          </div>
          <div className="table-wrapper" tabIndex={0} role="region" aria-label="Stress test comparison table">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr style={{ background: 'var(--elevated)' }}>
                  <th scope="col" className="text-left p-3">Scenario Case</th>
                  <th scope="col" className="text-right p-3">Min Projected Bal</th>
                  <th scope="col" className="text-left p-3">Shortage Date</th>
                  <th scope="col" className="text-left p-3">Reserve Breach</th>
                  <th scope="col" className="text-left p-3">Risk Before → After</th>
                  <th scope="col" className="text-left p-3">Affected Payments & Recovery</th>
                </tr>
              </thead>
              <tbody>
                {results.map((r, i) => (
                  <tr key={i} className="border-t" style={{ borderColor: 'var(--border)' }}>
                    <td className="p-3 font-semibold text-sm">{r.name}</td>
                    <td className="p-3 text-right font-mono-num font-semibold" style={{ color: r.scenarioForecast.minProjectedBalance < 0 ? 'var(--critical)' : 'var(--warning)' }}>
                      {formatMoney(r.scenarioForecast.minProjectedBalance)}
                    </td>
                    <td className="p-3 font-mono-num">{r.scenarioForecast.shortageDate ?? 'None'}</td>
                    <td className="p-3 font-mono-num">{r.scenarioForecast.reserveBreachDate ?? 'None'}</td>
                    <td className="p-3 font-mono-num">
                      {r.riskBefore.displayScore} ({r.riskBefore.category}) → <strong>{r.riskAfter.displayScore} ({r.riskAfter.category})</strong>
                    </td>
                    <td className="p-3 space-y-1">
                      <div>{r.scenarioForecast.recoveryMessage}</div>
                      {r.scenarioForecast.affectedObligations.length > 0 && (
                        <div style={{ color: 'var(--text-secondary)' }}>
                          Affected: {r.scenarioForecast.affectedObligations.map((a) => a.title).join(', ')}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

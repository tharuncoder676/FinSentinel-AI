import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer, Legend
} from 'recharts';
import { RotateCcw, Save, FileDown, FolderOpen, Trash2, ShieldCheck } from 'lucide-react';
import { apiRequest, apiUrl } from '../lib/api';
import { useApp } from '../context/AppContext';

interface SimParams {
  name: string;
  scenarioType: string;
  templateKey?: string | null;
  horizonDays: number;
  monthlyIncomeMultiplier: number;
  incomeDelayDays: number;
  essentialExpenseMultiplier: number;
  discretionaryExpenseMultiplier: number;
  savingsMultiplier: number;
  unexpectedExpenseAmount: number;
  unexpectedExpenseDate: string;
  paymentDateShiftDays: number;
  clusterPaymentsTogether?: boolean;
}

interface SimResponse {
  savedSimulationId: string | null;
  name: string;
  modelVersion: string;
  ledgerMutated: boolean;
  baselineForecast: {
    minProjectedBalance: number;
    reserveThreshold: number;
    shortageDate: string | null;
    reserveBreachDate: string | null;
    recoveryMessage: string;
  };
  scenarioForecast: {
    minProjectedBalance: number;
    reserveThreshold: number;
    shortageDate: string | null;
    reserveBreachDate: string | null;
    recoveryMessage: string;
    affectedObligations: Array<{ obligationId: string; title: string; dueDate: string; remainingAmount: number; projectedBalanceOnDueDate: number }>;
  };
  riskBefore: { displayScore: number; category: string };
  riskAfter: { displayScore: number; category: string };
  comparisonSeries: Array<{
    date: string;
    baselineBalance: number;
    scenarioBalance: number;
    reserveThreshold: number;
  }>;
  impactSummary: string[];
}

interface SavedScenario {
  id: string;
  name: string;
  scenarioType: string;
  horizonDays: number;
  parameters: SimParams;
  updatedAt: string;
}

const DEFAULT_PARAMS = (): SimParams => ({
  name: 'Digital Twin Exploration',
  scenarioType: 'DIGITAL_TWIN',
  horizonDays: 90,
  monthlyIncomeMultiplier: 1.0,
  incomeDelayDays: 0,
  essentialExpenseMultiplier: 1.0,
  discretionaryExpenseMultiplier: 1.0,
  savingsMultiplier: 1.0,
  unexpectedExpenseAmount: 0,
  unexpectedExpenseDate: new Date(Date.now() + 10 * 86400_000).toISOString().slice(0, 10),
  paymentDateShiftDays: 0,
});

export function DigitalTwinPage() {
  const { formatMoney, notifyToast } = useApp();
  const qc = useQueryClient();

  const [params, setParams] = useState<SimParams>(DEFAULT_PARAMS);
  const [simData, setSimData] = useState<SimResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [scrubIndex, setScrubIndex] = useState<number>(0);
  const abortRef = useRef<AbortController | null>(null);

  const { data: savedList = [] } = useQuery({
    queryKey: ['saved-scenarios'],
    queryFn: () => apiRequest<SavedScenario[]>('/api/v1/scenarios'),
  });

  // Debounced & cancellable simulation run
  useEffect(() => {
    const timer = setTimeout(async () => {
      if (abortRef.current) abortRef.current.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      setLoading(true);
      try {
        const res = await apiRequest<SimResponse>('/api/v1/scenarios/simulate', {
          method: 'POST',
          body: JSON.stringify(params),
          signal: controller.signal,
        });
        setSimData(res);
        setScrubIndex(0);
      } catch (err) {
        if ((err as Error).name !== 'AbortError') {
          // keep prior state on transient cancellation
        }
      } finally {
        setLoading(false);
      }
    }, 220);

    return () => clearTimeout(timer);
  }, [params]);

  const saveMut = useMutation({
    mutationFn: () =>
      apiRequest<SimResponse>('/api/v1/scenarios', {
        method: 'POST',
        body: JSON.stringify(params),
      }),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ['saved-scenarios'] });
      setSimData(saved);
      notifyToast(`Saved scenario "${saved.name}".`);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/scenarios/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['saved-scenarios'] });
      notifyToast('Scenario removed.');
    },
  });

  const exportScenarioPdf = async () => {
    try {
      const saved = simData?.savedSimulationId
        ? { id: simData.savedSimulationId }
        : await apiRequest<SimResponse>('/api/v1/scenarios', { method: 'POST', body: JSON.stringify(params) }).then((s) => ({ id: s.savedSimulationId }));
      const rep = await apiRequest<{ id: string }>('/api/v1/reports/generate', {
        method: 'POST',
        body: JSON.stringify({ reportType: 'SCENARIO', scenarioId: saved.id, horizonDays: params.horizonDays }),
      });
      window.open(apiUrl(`/api/v1/reports/${rep.id}/download`), '_blank');
      notifyToast('Scenario PDF report generated.');
    } catch {
      notifyToast('Unable to export scenario PDF.');
    }
  };

  const scrubbedPoint = simData?.comparisonSeries[Math.min(scrubIndex, (simData?.comparisonSeries.length ?? 1) - 1)];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Financial Digital Twin</h1>
          <p className="text-xs m-0 mt-0.5 flex items-center gap-1.5" style={{ color: 'var(--text-secondary)' }}>
            <ShieldCheck className="w-3.5 h-3.5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
            Isolated simulation workspace — baseline ledger records are never mutated.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setParams(DEFAULT_PARAMS())}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md border text-xs font-medium cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
            Reset Sliders
          </button>
          <button
            type="button"
            onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md border text-xs font-medium cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            <Save className="w-3.5 h-3.5" aria-hidden="true" />
            Save Scenario
          </button>
          <button
            type="button"
            onClick={exportScenarioPdf}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md text-xs font-semibold cursor-pointer"
            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
          >
            <FileDown className="w-3.5 h-3.5" aria-hidden="true" />
            Export Scenario PDF
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Controls Panel */}
        <section className="lg:col-span-4 surface-card p-5 space-y-4" aria-label="Simulation Parameter Controls">
          <div>
            <label htmlFor="twin-name" className="block text-xs font-semibold mb-1">Scenario Name</label>
            <input
              id="twin-name"
              value={params.name}
              onChange={(e) => setParams({ ...params, name: e.target.value })}
              className="w-full px-3 py-2 rounded-md border text-sm"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <label htmlFor="sl-inc">Monthly Income Multiplier</label>
              <span className="font-mono-num font-semibold">{Math.round(params.monthlyIncomeMultiplier * 100)}%</span>
            </div>
            <input id="sl-inc" type="range" min={0} max={1.5} step={0.05} value={params.monthlyIncomeMultiplier}
              onChange={(e) => setParams({ ...params, monthlyIncomeMultiplier: Number(e.target.value) })} className="w-full" />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <label htmlFor="sl-delay">Salary / Income Delay</label>
              <span className="font-mono-num font-semibold">{params.incomeDelayDays} days</span>
            </div>
            <input id="sl-delay" type="range" min={0} max={45} step={1} value={params.incomeDelayDays}
              onChange={(e) => setParams({ ...params, incomeDelayDays: Number(e.target.value) })} className="w-full" />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <label htmlFor="sl-ess">Essential Expenses</label>
              <span className="font-mono-num font-semibold">{Math.round(params.essentialExpenseMultiplier * 100)}%</span>
            </div>
            <input id="sl-ess" type="range" min={0.5} max={1.6} step={0.05} value={params.essentialExpenseMultiplier}
              onChange={(e) => setParams({ ...params, essentialExpenseMultiplier: Number(e.target.value) })} className="w-full" />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <label htmlFor="sl-disc">Discretionary Expenses</label>
              <span className="font-mono-num font-semibold">{Math.round(params.discretionaryExpenseMultiplier * 100)}%</span>
            </div>
            <input id="sl-disc" type="range" min={0} max={2.0} step={0.05} value={params.discretionaryExpenseMultiplier}
              onChange={(e) => setParams({ ...params, discretionaryExpenseMultiplier: Number(e.target.value) })} className="w-full" />
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <label htmlFor="sl-sav">Savings Contributions</label>
              <span className="font-mono-num font-semibold">{Math.round(params.savingsMultiplier * 100)}%</span>
            </div>
            <input id="sl-sav" type="range" min={0} max={2.0} step={0.1} value={params.savingsMultiplier}
              onChange={(e) => setParams({ ...params, savingsMultiplier: Number(e.target.value) })} className="w-full" />
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <label htmlFor="sl-shock" className="block text-xs font-semibold mb-1">Unexpected Expense</label>
              <input id="sl-shock" type="number" min={0} step={1000} value={params.unexpectedExpenseAmount}
                onChange={(e) => setParams({ ...params, unexpectedExpenseAmount: Number(e.target.value) || 0 })}
                className="w-full px-2.5 py-1.5 rounded border text-sm font-mono-num"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="sl-shock-date" className="block text-xs font-semibold mb-1">Shock Date</label>
              <input id="sl-shock-date" type="date" value={params.unexpectedExpenseDate}
                onChange={(e) => setParams({ ...params, unexpectedExpenseDate: e.target.value })}
                className="w-full px-2.5 py-1.5 rounded border text-xs"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>

          <div>
            <div className="flex justify-between text-xs mb-1">
              <label htmlFor="sl-payshift">Hypothetical Payment Shift</label>
              <span className="font-mono-num font-semibold">{params.paymentDateShiftDays > 0 ? `+${params.paymentDateShiftDays}` : params.paymentDateShiftDays} days</span>
            </div>
            <input id="sl-payshift" type="range" min={-10} max={20} step={1} value={params.paymentDateShiftDays}
              onChange={(e) => setParams({ ...params, paymentDateShiftDays: Number(e.target.value) })} className="w-full" />
          </div>
        </section>

        {/* Trajectory Comparison Panel */}
        <section className="lg:col-span-8 space-y-5">
          {simData && (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="surface-card p-4">
                  <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Baseline vs Scenario Min</div>
                  <div className="text-sm font-mono-num mt-1">
                    {formatMoney(simData.baselineForecast.minProjectedBalance)} →{' '}
                    <strong style={{ color: simData.scenarioForecast.minProjectedBalance < 0 ? 'var(--critical)' : 'var(--warning)' }}>
                      {formatMoney(simData.scenarioForecast.minProjectedBalance)}
                    </strong>
                  </div>
                </div>
                <div className="surface-card p-4">
                  <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Risk Score Delta</div>
                  <div className="text-sm font-mono-num mt-1">
                    {simData.riskBefore.displayScore} ({simData.riskBefore.category}) →{' '}
                    <strong style={{ color: simData.riskAfter.displayScore > simData.riskBefore.displayScore ? 'var(--critical)' : 'var(--accent)' }}>
                      {simData.riskAfter.displayScore} ({simData.riskAfter.category})
                    </strong>
                  </div>
                </div>
                <div className="surface-card p-4">
                  <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Scenario Shortage Date</div>
                  <div className="text-sm font-mono-num font-bold mt-1" style={{ color: simData.scenarioForecast.shortageDate ? 'var(--critical)' : 'var(--accent)' }}>
                    {simData.scenarioForecast.shortageDate ?? 'No Shortfall'}
                  </div>
                </div>
              </div>

              <div className="surface-card p-5 space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="text-base font-semibold m-0">Synchronized Baseline vs Digital Twin Trajectory</h2>
                  {loading && <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>Updating...</span>}
                </div>
                <div className="h-72 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={simData.comparisonSeries}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} minTickGap={22} />
                      <YAxis tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} width={72} />
                      <Tooltip contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', fontSize: '12px' }} />
                      <Legend wrapperStyle={{ fontSize: '12px' }} />
                      <ReferenceLine y={simData.baselineForecast.reserveThreshold} stroke="var(--warning)" strokeDasharray="4 4" />
                      <ReferenceLine y={0} stroke="var(--critical)" />
                      <Line type="monotone" dataKey="baselineBalance" name="Baseline (Projected)" stroke="var(--accent)" strokeWidth={2} dot={false} animationDuration={180} />
                      <Line type="monotone" dataKey="scenarioBalance" name="Modified Twin (Hypothetical)" stroke="var(--critical)" strokeWidth={2.2} strokeDasharray="5 3" dot={false} animationDuration={180} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>

                {/* Timeline Scrubbing */}
                <div className="pt-2 border-t space-y-1.5" style={{ borderColor: 'var(--border)' }}>
                  <div className="flex justify-between text-xs">
                    <label htmlFor="twin-scrub">Timeline Scrubber</label>
                    {scrubbedPoint && (
                      <span className="font-mono-num">
                        {scrubbedPoint.date}: Baseline <strong>{formatMoney(scrubbedPoint.baselineBalance)}</strong> vs Twin <strong>{formatMoney(scrubbedPoint.scenarioBalance)}</strong>
                      </span>
                    )}
                  </div>
                  <input
                    id="twin-scrub"
                    type="range"
                    min={0}
                    max={Math.max(0, simData.comparisonSeries.length - 1)}
                    value={scrubIndex}
                    onChange={(e) => setScrubIndex(Number(e.target.value))}
                    className="w-full"
                  />
                </div>
              </div>

              <div className="surface-card p-5 space-y-2">
                <h2 className="text-base font-semibold m-0">Side-by-Side Impact Analysis</h2>
                <ul className="text-xs space-y-1 m-0 pl-4" style={{ color: 'var(--text-secondary)' }}>
                  {simData.impactSummary.map((line, i) => <li key={i}>{line}</li>)}
                </ul>
                {simData.scenarioForecast.affectedObligations.length > 0 && (
                  <div className="pt-2">
                    <div className="text-xs font-semibold mb-1">Affected Obligations under Scenario:</div>
                    <div className="flex flex-wrap gap-2">
                      {simData.scenarioForecast.affectedObligations.map((ob) => (
                        <span key={ob.obligationId} className="text-xs px-2.5 py-1 rounded border font-mono-num" style={{ borderColor: 'var(--warning)', background: 'var(--elevated)' }}>
                          {ob.title}: {formatMoney(ob.projectedBalanceOnDueDate)}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      </div>

      {/* Saved Scenarios */}
      <section className="surface-card p-5 space-y-3" aria-label="Saved Scenarios">
        <h2 className="text-base font-semibold m-0">Saved Scenarios</h2>
        {savedList.length === 0 ? (
          <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>No saved scenarios yet. Adjust sliders and click Save Scenario.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {savedList.map((s) => (
              <div key={s.id} className="p-3 rounded-md border flex items-center justify-between gap-2" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <div className="min-w-0">
                  <div className="text-sm font-semibold truncate">{s.name}</div>
                  <div className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                    {s.scenarioType} • {s.horizonDays}d • {new Date(s.updatedAt).toLocaleDateString()}
                  </div>
                </div>
                <div className="flex gap-1 shrink-0">
                  <button
                    type="button"
                    aria-label={`Reopen ${s.name}`}
                    onClick={() => {
                      setParams(s.parameters);
                      notifyToast(`Loaded "${s.name}".`);
                    }}
                    className="p-1.5 rounded border cursor-pointer"
                    style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
                  >
                    <FolderOpen className="w-3.5 h-3.5" aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    aria-label={`Delete ${s.name}`}
                    onClick={() => deleteMut.mutate(s.id)}
                    className="p-1.5 rounded border cursor-pointer"
                    style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--critical)' }}
                  >
                    <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

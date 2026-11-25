import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, Cell } from 'recharts';
import { RefreshCw, ShieldAlert } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface RiskAssessment {
  id: string;
  policyVersion: string;
  rawScore: number;
  displayScore: number;
  category: string;
  dataCompleteness: number;
  insufficientData: boolean;
  disclaimer: string;
  assumptions: string[];
  factors: Array<{
    factorKey: string;
    factorLabel: string;
    weight: number;
    normalizedValue: number | null;
    weightedContribution: number | null;
    measuredValue: string;
    unit: string;
    window: string;
    normalizationFormula: string;
    dataAvailable: boolean;
    explanation: string;
  }>;
  calculatedAt: string;
}

interface RiskHistoryItem {
  id: string;
  policyVersion: string;
  displayScore: number;
  category: string;
  calculatedAt: string;
}

export function RiskPage() {
  const { notifyToast } = useApp();
  const qc = useQueryClient();

  const { data: risk, isLoading } = useQuery({
    queryKey: ['risk-assessment'],
    queryFn: () => apiRequest<RiskAssessment>('/api/v1/risk/assessment'),
  });

  const { data: history = [] } = useQuery({
    queryKey: ['risk-history'],
    queryFn: () => apiRequest<RiskHistoryItem[]>('/api/v1/risk/history'),
  });

  const recalcMut = useMutation({
    mutationFn: () => apiRequest<RiskAssessment>('/api/v1/risk/assessment/recalculate', { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['risk-assessment'] });
      qc.invalidateQueries({ queryKey: ['risk-history'] });
      notifyToast('Risk assessment recalculated and snapshot persisted.');
    },
  });

  if (isLoading || !risk) {
    return <div className="h-72 skeleton-pulse" />;
  }

  const chartData = risk.factors.map((f) => ({
    name: f.factorLabel.split(' ').slice(0, 2).join(' '),
    contribution: f.weightedContribution ?? 0,
    maxPts: Math.round(f.weight * 100),
  }));

  const historySeries = [...history].reverse().map((h) => ({
    time: new Date(h.calculatedAt).toLocaleDateString(),
    score: h.displayScore,
  }));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Explainable Risk Anatomy</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Policy {risk.policyVersion} • Completeness: {Math.round(risk.dataCompleteness * 100)}% • {risk.insufficientData ? 'INSUFFICIENT DATA' : risk.category}
          </p>
        </div>
        <button
          type="button"
          onClick={() => recalcMut.mutate()}
          disabled={recalcMut.isPending}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md text-xs font-semibold cursor-pointer"
          style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
        >
          <RefreshCw className="w-3.5 h-3.5" aria-hidden="true" />
          Recalculate Snapshot
        </button>
      </div>

      {/* Score Hero & Disclaimer */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-4 surface-card p-5 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>Educational Vulnerability Score</span>
            <ShieldAlert className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
          </div>
          <div className="text-4xl font-bold font-mono-num">
            {risk.insufficientData ? 'N/A' : risk.displayScore}
            <span className="text-base font-normal ml-1" style={{ color: 'var(--text-secondary)' }}>/ 100</span>
          </div>
          <div className="inline-block px-2.5 py-1 rounded text-xs font-semibold" style={{ background: 'var(--elevated)' }}>
            Category: {risk.category.replace('_', ' ')} (Raw: {risk.rawScore})
          </div>
          <div className="w-full h-2.5 rounded-full overflow-hidden" style={{ background: 'var(--elevated)' }}>
            <div
              className="h-full transition-all duration-300"
              style={{
                width: `${risk.insufficientData ? 0 : risk.displayScore}%`,
                background: risk.displayScore > 80 ? 'var(--critical)' : risk.displayScore > 60 ? 'var(--warning)' : 'var(--accent)',
              }}
            />
          </div>
          <p className="text-[11px] m-0" style={{ color: 'var(--text-secondary)' }}>
            {risk.disclaimer}
          </p>
        </div>

        {/* Factor Contribution Chart */}
        <div className="lg:col-span-8 surface-card p-5 space-y-2">
          <h2 className="text-base font-semibold m-0">Factor Contribution Breakdown (Points toward 100)</h2>
          <div className="h-56 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} layout="vertical" margin={{ left: 20, right: 20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis type="number" domain={[0, 30]} tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                <YAxis type="category" dataKey="name" width={145} tick={{ fontSize: 11, fill: 'var(--text-primary)' }} />
                <Tooltip contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', fontSize: '12px' }} />
                <Bar dataKey="contribution" name="Weighted Contribution (pts)" radius={[0, 4, 4, 0]} animationDuration={180}>
                  {chartData.map((entry, idx) => (
                    <Cell key={idx} fill={entry.contribution > entry.maxPts * 0.6 ? 'var(--warning)' : 'var(--accent)'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Detailed Factor Table */}
      <div className="surface-card overflow-hidden">
        <div className="p-4 border-b" style={{ borderColor: 'var(--border)' }}>
          <h2 className="text-base font-semibold m-0">Factor Measurements, Windows & Normalization Formulas</h2>
        </div>
        <div className="table-wrapper" tabIndex={0} role="region" aria-label="Risk factors table">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr style={{ background: 'var(--elevated)' }}>
                <th scope="col" className="text-left p-3">Factor</th>
                <th scope="col" className="text-right p-3">Weight</th>
                <th scope="col" className="text-left p-3">Measured</th>
                <th scope="col" className="text-right p-3">Norm (0–1)</th>
                <th scope="col" className="text-right p-3">Contribution</th>
                <th scope="col" className="text-left p-3">Window & Formula</th>
              </tr>
            </thead>
            <tbody>
              {risk.factors.map((f) => (
                <tr key={f.factorKey} className="border-t" style={{ borderColor: 'var(--border)' }}>
                  <td className="p-3">
                    <div className="font-semibold text-sm">{f.factorLabel}</div>
                    <div style={{ color: 'var(--text-secondary)' }}>{f.explanation}</div>
                  </td>
                  <td className="p-3 text-right font-mono-num">{Math.round(f.weight * 100)}%</td>
                  <td className="p-3 font-mono-num">{f.measuredValue}</td>
                  <td className="p-3 text-right font-mono-num">{f.normalizedValue !== null ? f.normalizedValue : 'N/A'}</td>
                  <td className="p-3 text-right font-mono-num font-semibold">{f.weightedContribution !== null ? `${f.weightedContribution} pts` : 'Unavailable'}</td>
                  <td className="p-3">
                    <div>{f.window}</div>
                    <code className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>{f.normalizationFormula}</code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Historical Score Trend + Assumptions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="surface-card p-5 space-y-2">
          <h2 className="text-base font-semibold m-0">Risk Score Snapshot History</h2>
          <div className="h-48 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={historySeries}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="time" tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                <Tooltip contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', fontSize: '12px' }} />
                <Line type="monotone" dataKey="score" stroke="var(--accent)" strokeWidth={2} dot />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="surface-card p-5 space-y-2">
          <h2 className="text-base font-semibold m-0">Policy Assumptions & Missing-Data Safeguards</h2>
          <ul className="text-xs space-y-1.5 m-0 pl-4" style={{ color: 'var(--text-secondary)' }}>
            {risk.assumptions.map((a, idx) => <li key={idx}>{a}</li>)}
          </ul>
        </div>
      </div>
    </div>
  );
}

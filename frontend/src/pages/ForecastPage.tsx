import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer } from 'recharts';
import { BookmarkPlus, AlertTriangle } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface ForecastResult {
  modelVersion: string;
  startDate: string;
  horizonDays: number;
  openingBalance: number;
  reserveThreshold: number;
  minProjectedBalance: number;
  minBalanceDate: string;
  shortageDate: string | null;
  reserveBreachDate: string | null;
  recoveryDate: string | null;
  recoveryMessage: string;
  insufficientData: boolean;
  assumptions: string[];
  dailyPoints: Array<{
    date: string;
    openingBalance: number;
    totalCredits: number;
    totalDebits: number;
    closingBalance: number;
    shortfall: boolean;
    reserveBreach: boolean;
    events: Array<{ title: string; signedAmount: number; kind: string; overdue: boolean }>;
  }>;
  affectedObligations: Array<{
    obligationId: string;
    title: string;
    dueDate: string;
    remainingAmount: number;
    projectedBalanceOnDueDate: number;
    causesShortfall: boolean;
    breachesReserve: boolean;
  }>;
}

export function ForecastPage() {
  const { formatMoney, notifyToast } = useApp();
  const [horizon, setHorizon] = useState(90);

  const { data, isLoading } = useQuery({
    queryKey: ['forecast', horizon],
    queryFn: () => apiRequest<ForecastResult>(`/api/v1/forecast?horizonDays=${horizon}`),
  });

  const snapshotMut = useMutation({
    mutationFn: () => apiRequest(`/api/v1/forecast/snapshot?horizonDays=${horizon}`, { method: 'POST' }),
    onSuccess: () => notifyToast('Forecast snapshot archived.'),
  });

  if (isLoading || !data) return <div className="h-72 skeleton-pulse" />;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Deterministic Cash Flow Forecast</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Model {data.modelVersion} • Horizon: {data.horizonDays} days • Projected values are strictly distinct from posted ledger transactions.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="inline-flex rounded-md border p-0.5" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
            {[7, 30, 90, 180, 365].map((h) => (
              <button
                key={h}
                type="button"
                onClick={() => setHorizon(h)}
                aria-pressed={horizon === h}
                className="px-2.5 py-1 text-xs font-medium rounded cursor-pointer"
                style={{
                  background: horizon === h ? 'var(--accent)' : 'transparent',
                  color: horizon === h ? 'var(--accent-text)' : 'var(--text-primary)',
                }}
              >
                {h}d
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => snapshotMut.mutate()}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            <BookmarkPlus className="w-3.5 h-3.5" aria-hidden="true" />
            Save Snapshot
          </button>
        </div>
      </div>

      {/* KPI Summary */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="surface-card p-4">
          <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Opening Balance ({data.startDate})</div>
          <div className="text-xl font-bold font-mono-num mt-1">{formatMoney(data.openingBalance)}</div>
        </div>
        <div className="surface-card p-4">
          <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Minimum Projected Balance</div>
          <div className="text-xl font-bold font-mono-num mt-1" style={{ color: data.minProjectedBalance < 0 ? 'var(--critical)' : data.minProjectedBalance < data.reserveThreshold ? 'var(--warning)' : 'var(--accent)' }}>
            {formatMoney(data.minProjectedBalance)}
          </div>
          <div className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>on {data.minBalanceDate}</div>
        </div>
        <div className="surface-card p-4">
          <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Projected Cash Shortfall (&lt; 0)</div>
          <div className="text-xl font-bold font-mono-num mt-1" style={{ color: data.shortageDate ? 'var(--critical)' : 'var(--accent)' }}>
            {data.shortageDate ?? 'None'}
          </div>
        </div>
        <div className="surface-card p-4">
          <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Reserve Breach (&lt; {formatMoney(data.reserveThreshold)})</div>
          <div className="text-xl font-bold font-mono-num mt-1" style={{ color: data.reserveBreachDate ? 'var(--warning)' : 'var(--accent)' }}>
            {data.reserveBreachDate ?? 'None'}
          </div>
        </div>
      </div>

      <div className="surface-card p-5 space-y-3">
        <h2 className="text-base font-semibold m-0">Projected Balance Trajectory</h2>
        <div className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data.dailyPoints}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} minTickGap={24} />
              <YAxis tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} width={72} />
              <Tooltip contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', fontSize: '12px' }} />
              <ReferenceLine y={data.reserveThreshold} stroke="var(--warning)" strokeDasharray="4 4" />
              <ReferenceLine y={0} stroke="var(--critical)" />
              <Area type="monotone" dataKey="closingBalance" name="Projected Closing Balance" stroke="var(--accent)" fill="var(--accent)" fillOpacity={0.16} strokeWidth={2.2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
        <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>{data.recoveryMessage}</p>
      </div>

      {data.affectedObligations.length > 0 && (
        <div className="surface-card p-5 space-y-3 border-l-4" style={{ borderLeftColor: 'var(--warning)' }}>
          <h2 className="text-base font-semibold m-0 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" style={{ color: 'var(--warning)' }} aria-hidden="true" />
            Obligations Triggering Reserve Pressure or Shortfall
          </h2>
          <ul className="divide-y list-none p-0 m-0 text-sm" style={{ borderColor: 'var(--border)' }}>
            {data.affectedObligations.map((ob) => (
              <li key={ob.obligationId} className="py-2 flex justify-between gap-2">
                <span>{ob.title} (Due {ob.dueDate})</span>
                <span className="font-mono-num">
                  Amount: {formatMoney(ob.remainingAmount)} → Balance: <strong style={{ color: ob.causesShortfall ? 'var(--critical)' : 'var(--warning)' }}>{formatMoney(ob.projectedBalanceOnDueDate)}</strong>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="surface-card p-5 space-y-2">
        <h2 className="text-base font-semibold m-0">Forecast Assumptions & Contracts</h2>
        <ul className="text-xs space-y-1 m-0 pl-4" style={{ color: 'var(--text-secondary)' }}>
          {data.assumptions.map((a, idx) => <li key={idx}>{a}</li>)}
        </ul>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ComposedChart, Area, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer, Legend
} from 'recharts';
import {
  AlertTriangle, CheckCircle2, Info, ChevronDown, ChevronUp, ArrowUpRight, Table2, Plus
} from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';
import { AnimatedNumber } from '../components/AnimatedNumber';

interface DashboardData {
  period: string;
  lastUpdatedAt: string;
  emptyWorkspace: boolean;
  ledgerSummary: {
    totalAvailableBalance: number;
    periodIncome: number;
    periodExpenses: number;
    periodSavings: number;
    previousPeriodIncome: number;
    previousPeriodExpenses: number;
    hasPreviousComparableData: boolean;
  };
  riskAssessment: {
    policyVersion: string;
    displayScore: number;
    category: string;
    dataCompleteness: number;
    insufficientData: boolean;
    disclaimer: string;
    factors: Array<{
      factorKey: string;
      factorLabel: string;
      weight: number;
      normalizedValue: number | null;
      weightedContribution: number | null;
      measuredValue: string;
      dataAvailable: boolean;
      explanation: string;
    }>;
  };
  forecast: {
    modelVersion: string;
    horizonDays: number;
    reserveThreshold: number;
    minProjectedBalance: number;
    shortageDate: string | null;
    reserveBreachDate: string | null;
    recoveryMessage: string;
  };
  cashFlowChartSeries: Array<{
    date: string;
    projected: boolean;
    income: number;
    expense: number;
    projectedBalance: number | null;
    reserveThreshold: number;
    shortfall: boolean;
    reserveBreach: boolean;
  }>;
  upcomingPayments: Array<{
    id: string;
    title: string;
    dueDate: string;
    remainingAmount: number;
    status: string;
    affordabilityStatus: string;
    estimatedCashFlowImpact: number;
  }>;
  insightFeed: Array<{
    id: string;
    severity: string;
    title: string;
    summary: string;
    explanation: string;
    sourceReference: string;
  }>;
}

export function DashboardPage() {
  const { user, t, formatMoney } = useApp();
  const navigate = useNavigate();
  const [period, setPeriod] = useState<'7d' | '30d' | '90d' | '12m'>('30d');
  const [showTableView, setShowTableView] = useState(false);
  const [expandedFactor, setExpandedFactor] = useState<string | null>(null);
  const [expandedInsight, setExpandedInsight] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['dashboard', period],
    queryFn: () => apiRequest<DashboardData>(`/api/v1/dashboard?period=${period}`),
  });

  if (isLoading) {
    return (
      <div className="space-y-4" aria-busy="true">
        <div className="h-10 w-64 skeleton-pulse" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => <div key={i} className="h-28 skeleton-pulse" />)}
        </div>
        <div className="h-80 skeleton-pulse" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div role="alert" className="surface-card p-6 text-center space-y-3">
        <p className="text-sm font-medium m-0">Unable to load financial intelligence dashboard.</p>
        <button
          type="button"
          onClick={() => refetch()}
          className="px-4 py-2 rounded-md text-sm font-semibold cursor-pointer"
          style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
        >
          {t('retry')}
        </button>
      </div>
    );
  }

  const { ledgerSummary, riskAssessment, forecast, cashFlowChartSeries, upcomingPayments, insightFeed } = data;
  const riskColor = riskAssessment.insufficientData
    ? 'var(--text-secondary)'
    : riskAssessment.displayScore > 80
    ? 'var(--critical)'
    : riskAssessment.displayScore > 60
    ? 'var(--warning)'
    : 'var(--accent)';

  return (
    <div className="space-y-6">
      {/* Compact Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">
            Welcome back, {user?.fullName?.split(' ')[0]}
          </h1>
          <p className="text-xs mt-0.5 mb-0" style={{ color: 'var(--text-secondary)' }}>
            Period: {period} • Last updated: {new Date(data.lastUpdatedAt).toLocaleTimeString()} • Policy {riskAssessment.policyVersion}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex rounded-md border p-0.5" role="group" aria-label="Dashboard period selector" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
            {(['7d', '30d', '90d', '12m'] as const).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPeriod(p)}
                aria-pressed={period === p}
                className="px-2.5 py-1 text-xs font-medium rounded cursor-pointer"
                style={{
                  background: period === p ? 'var(--accent)' : 'transparent',
                  color: period === p ? 'var(--accent-text)' : 'var(--text-primary)',
                }}
              >
                {p}
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => navigate('/app/transactions?new=1')}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold cursor-pointer"
            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
          >
            <Plus className="w-3.5 h-3.5" aria-hidden="true" />
            {t('addTransaction')}
          </button>
        </div>
      </div>

      {/* Empty Workspace Banner for Fresh Accounts */}
      {data.emptyWorkspace && (
        <div className="surface-card p-6 flex flex-wrap items-center justify-between gap-4 border-l-4" style={{ borderLeftColor: 'var(--accent)' }}>
          <div className="space-y-1">
            <h2 className="text-base font-semibold m-0">{t('noDataYet')}</h2>
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>
              Your account starts clean with no fabricated figures. Add an account, transaction, or obligation to activate live projections.
            </p>
          </div>
          <div className="flex gap-2">
            <Link to="/app/transactions?new=1" className="px-3.5 py-2 rounded-md text-xs font-semibold no-underline" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
              Add First Account & Transaction
            </Link>
          </div>
        </div>
      )}

      {/* 4 Financial Overview Metrics */}
      <section aria-label="Financial Overview" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="surface-card p-4">
          <div className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>{t('availableBalance')}</div>
          <div className="text-2xl font-bold mt-1" style={{ color: ledgerSummary.totalAvailableBalance < 0 ? 'var(--critical)' : 'var(--text-primary)' }}>
            <AnimatedNumber value={ledgerSummary.totalAvailableBalance} formatter={(n) => formatMoney(n)} />
          </div>
          <div className="text-[11px] mt-1" style={{ color: 'var(--text-secondary)' }}>
            {ledgerSummary.totalAvailableBalance < 0 ? 'Negative balance (Deficit)' : `Reserve floor: ${formatMoney(forecast.reserveThreshold)}`}
          </div>
        </div>

        <div className="surface-card p-4">
          <div className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>{t('monthlyIncome')} ({period})</div>
          <div className="text-2xl font-bold mt-1" style={{ color: 'var(--accent)' }}>
            <AnimatedNumber value={ledgerSummary.periodIncome} formatter={(n) => formatMoney(n)} />
          </div>
          <div className="text-[11px] mt-1" style={{ color: 'var(--text-secondary)' }}>
            {ledgerSummary.hasPreviousComparableData
              ? `Prior period: ${formatMoney(ledgerSummary.previousPeriodIncome)}`
              : 'No prior comparable period yet'}
          </div>
        </div>

        <div className="surface-card p-4">
          <div className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>{t('monthlyExpenses')} ({period})</div>
          <div className="text-2xl font-bold mt-1">
            <AnimatedNumber value={ledgerSummary.periodExpenses} formatter={(n) => formatMoney(n)} />
          </div>
          <div className="text-[11px] mt-1" style={{ color: 'var(--text-secondary)' }}>
            {ledgerSummary.hasPreviousComparableData
              ? `Prior period: ${formatMoney(ledgerSummary.previousPeriodExpenses)}`
              : 'Transfers between accounts excluded'}
          </div>
        </div>

        <Link to="/app/risk" className="surface-card p-4 block no-underline hover:opacity-90" style={{ color: 'var(--text-primary)' }}>
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>{t('riskScore')}</span>
            <ArrowUpRight className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} aria-hidden="true" />
          </div>
          <div className="text-2xl font-bold mt-1 font-mono-num" style={{ color: riskColor }}>
            {riskAssessment.insufficientData ? 'N/A' : `${riskAssessment.displayScore}/100`}
            <span className="ml-2 text-xs font-semibold uppercase">{riskAssessment.category.replace('_', ' ')}</span>
          </div>
          <div className="text-[11px] mt-1" style={{ color: 'var(--text-secondary)' }}>
            Completeness: {Math.round(riskAssessment.dataCompleteness * 100)}% • Educational indicator
          </div>
        </Link>
      </section>

      {/* Primary Cash Flow Intelligence Chart + Risk Radar */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <section className="lg:col-span-8 surface-card p-5 space-y-3" aria-label="Cash Flow Intelligence Chart">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold m-0">Cash Flow Intelligence ({period} Observed & Projected)</h2>
              <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>
                Observed inflows/outflows vs deterministic projected balance ({forecast.modelVersion}). Min projected: <strong className="font-mono-num">{formatMoney(forecast.minProjectedBalance)}</strong>
              </p>
            </div>
            <button
              type="button"
              onClick={() => setShowTableView((v) => !v)}
              className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            >
              <Table2 className="w-3.5 h-3.5" aria-hidden="true" />
              {showTableView ? 'Show Chart' : 'Accessible Table'}
            </button>
          </div>

          {!showTableView ? (
            <div className="h-72 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={cashFlowChartSeries} margin={{ top: 10, right: 12, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} minTickGap={24} />
                  <YAxis tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} width={68} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'var(--surface)',
                      borderColor: 'var(--border)',
                      borderRadius: '8px',
                      fontSize: '12px',
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <ReferenceLine y={forecast.reserveThreshold} stroke="var(--warning)" strokeDasharray="4 4" label={{ value: 'Reserve', position: 'insideTopRight', fill: 'var(--warning)', fontSize: 11 }} />
                  <ReferenceLine y={0} stroke="var(--critical)" />
                  <Bar dataKey="income" name="Inflows (Actual/Proj)" fill="var(--accent-secondary)" radius={[3, 3, 0, 0]} animationDuration={220} />
                  <Bar dataKey="expense" name="Outflows (Actual/Proj)" fill="var(--warning)" radius={[3, 3, 0, 0]} animationDuration={220} />
                  <Area type="monotone" dataKey="projectedBalance" name="Projected Balance" stroke="var(--accent)" fill="var(--accent)" fillOpacity={0.14} strokeWidth={2.2} connectNulls animationDuration={220} />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="table-wrapper max-h-72 border rounded-md" style={{ borderColor: 'var(--border)' }} tabIndex={0} role="region" aria-label="Cash flow projection table">
              <table className="w-full text-xs border-collapse">
                <caption className="visually-hidden">Daily observed and projected cash-flow ledger series</caption>
                <thead>
                  <tr style={{ background: 'var(--elevated)' }}>
                    <th scope="col" className="text-left p-2">Date</th>
                    <th scope="col" className="text-left p-2">Type</th>
                    <th scope="col" className="text-right p-2">Inflow</th>
                    <th scope="col" className="text-right p-2">Outflow</th>
                    <th scope="col" className="text-right p-2">Projected Balance</th>
                    <th scope="col" className="text-left p-2">Flag</th>
                  </tr>
                </thead>
                <tbody>
                  {cashFlowChartSeries.slice(0, 45).map((row, idx) => (
                    <tr key={`${row.date}-${idx}`} className="border-t" style={{ borderColor: 'var(--border)' }}>
                      <td className="p-2 font-mono-num">{row.date}</td>
                      <td className="p-2">{row.projected ? 'Projected' : 'Observed'}</td>
                      <td className="p-2 text-right font-mono-num">{formatMoney(row.income)}</td>
                      <td className="p-2 text-right font-mono-num">{formatMoney(row.expense)}</td>
                      <td className="p-2 text-right font-mono-num">{row.projectedBalance !== null ? formatMoney(row.projectedBalance) : '—'}</td>
                      <td className="p-2">{row.shortfall ? 'Shortfall' : row.reserveBreach ? 'Reserve Breach' : 'OK'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="text-xs flex flex-wrap justify-between gap-2 pt-1" style={{ color: 'var(--text-secondary)' }}>
            <span>{forecast.recoveryMessage}</span>
            <Link to="/app/digital-twin" className="font-semibold no-underline" style={{ color: 'var(--accent)' }}>
              Test changes in Digital Twin →
            </Link>
          </div>
        </section>

        {/* Risk Radar Factors */}
        <section className="lg:col-span-4 surface-card p-5 space-y-3" aria-label="Risk Radar">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold m-0">Risk Radar (6 Factors)</h2>
            <Link to="/app/risk" className="text-xs font-semibold no-underline" style={{ color: 'var(--accent)' }}>Full Anatomy</Link>
          </div>

          <ul className="space-y-2.5 list-none p-0 m-0">
            {riskAssessment.factors.map((f) => {
              const isOpen = expandedFactor === f.factorKey;
              const pct = f.normalizedValue !== null ? Math.round(f.normalizedValue * 100) : 0;
              return (
                <li key={f.factorKey} className="p-2.5 rounded-md border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    onClick={() => setExpandedFactor(isOpen ? null : f.factorKey)}
                    className="w-full flex items-center justify-between gap-2 text-left cursor-pointer"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-semibold truncate">{f.factorLabel}</div>
                      <div className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                        Weight {Math.round(f.weight * 100)}% • {f.measuredValue}
                      </div>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <span className="text-xs font-mono-num font-semibold">
                        {f.weightedContribution !== null ? `${f.weightedContribution} pts` : 'N/A'}
                      </span>
                      {isOpen ? <ChevronUp className="w-3.5 h-3.5" aria-hidden="true" /> : <ChevronDown className="w-3.5 h-3.5" aria-hidden="true" />}
                    </div>
                  </button>
                  <div className="w-full h-1.5 rounded-full mt-2 overflow-hidden" style={{ background: 'var(--border)' }}>
                    <div
                      className="h-full"
                      style={{
                        width: `${pct}%`,
                        background: pct > 70 ? 'var(--critical)' : pct > 40 ? 'var(--warning)' : 'var(--accent)',
                      }}
                    />
                  </div>
                  {isOpen && (
                    <p className="text-xs mt-2 mb-0" style={{ color: 'var(--text-secondary)' }}>
                      {f.explanation}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      </div>

      {/* Upcoming Payments + Deterministic AI Insight Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <section className="lg:col-span-6 surface-card p-5 space-y-3" aria-label="Upcoming Payments">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold m-0">Upcoming Payments</h2>
            <Link to="/app/payments" className="text-xs font-semibold no-underline" style={{ color: 'var(--accent)' }}>Payment Planner →</Link>
          </div>
          {upcomingPayments.length === 0 ? (
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>No unpaid upcoming obligations.</p>
          ) : (
            <ul className="divide-y list-none p-0 m-0" style={{ borderColor: 'var(--border)' }}>
              {upcomingPayments.map((ob) => (
                <li key={ob.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-sm font-medium truncate">{ob.title}</div>
                    <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                      Due {ob.dueDate} • Status: {ob.status} • {ob.affordabilityStatus.replace('_', ' ')}
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <div className="text-sm font-bold font-mono-num">{formatMoney(ob.remainingAmount)}</div>
                    <Link to="/app/payments" className="text-[11px] font-semibold no-underline" style={{ color: 'var(--accent)' }}>
                      View Details
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="lg:col-span-6 surface-card p-5 space-y-3" aria-label="Deterministic Insight Feed">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-semibold m-0">Grounded Intelligence Feed</h2>
            <Link to="/app/assistant" className="text-xs font-semibold no-underline" style={{ color: 'var(--accent)' }}>Open AI Assistant →</Link>
          </div>
          <ul className="space-y-2.5 list-none p-0 m-0">
            {insightFeed.map((ins) => {
              const open = expandedInsight === ins.id;
              return (
                <li key={ins.id} className="p-3 rounded-md border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                  <button
                    type="button"
                    aria-expanded={open}
                    onClick={() => setExpandedInsight(open ? null : ins.id)}
                    className="w-full flex items-start justify-between gap-2 text-left cursor-pointer"
                  >
                    <div className="flex items-start gap-2">
                      {ins.severity === 'CRITICAL' ? (
                        <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" style={{ color: 'var(--critical)' }} aria-hidden="true" />
                      ) : ins.severity === 'POSITIVE' ? (
                        <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                      ) : (
                        <Info className="w-4 h-4 mt-0.5 shrink-0" style={{ color: 'var(--warning)' }} aria-hidden="true" />
                      )}
                      <div>
                        <div className="text-xs font-semibold">{ins.title}</div>
                        <div className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>{ins.summary}</div>
                      </div>
                    </div>
                    {open ? <ChevronUp className="w-4 h-4 shrink-0" aria-hidden="true" /> : <ChevronDown className="w-4 h-4 shrink-0" aria-hidden="true" />}
                  </button>
                  {open && (
                    <div className="mt-2 pt-2 border-t text-xs space-y-1" style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}>
                      <p className="m-0">{ins.explanation}</p>
                      <div className="text-[11px] font-mono-num">Source: {ins.sourceReference}</div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { Plus, Calendar, Calculator, CheckCircle2, AlertTriangle, RotateCcw, CreditCard } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';
import { ModalDialog } from '../components/ModalDialog';

interface Obligation {
  id: string;
  accountId: string | null;
  title: string;
  category: string;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  dueDate: string;
  frequency: string;
  essential: boolean;
  status: string;
  lateFee: number;
  graceDays: number;
  priorityRank: number;
  affordabilityStatus: string;
  estimatedCashFlowImpact: number;
}

interface PlanComparison {
  availableBalance: number;
  reserveThreshold: number;
  advisoryNotice: string;
  scenarios: Array<{
    strategy: string;
    scenarioName: string;
    feasible: boolean;
    respectsReserveThreshold: boolean;
    shortfallAmount: number;
    minProjectedBalance: number;
    totalLateFeesAssumed: number;
    consequences: string[];
    items: Array<{
      obligationId: string;
      title: string;
      contractualDueDate: string;
      hypotheticalPayDate: string;
      shiftedInSimulation: boolean;
      amount: number;
      projectedBalanceAfter: number;
      note: string;
    }>;
  }>;
}

interface EmiResult {
  supported: boolean;
  unsupportedReason: string | null;
  rateModel: string;
  monthlyEmi: number;
  totalInterest: number;
  totalPayableWithFee: number;
  reconciledPrincipalSum: number;
  assumptions: string[];
  schedule: Array<{
    installmentNumber: number;
    openingPrincipal: number;
    emiAmount: number;
    principalComponent: number;
    interestComponent: number;
    closingPrincipal: number;
  }>;
}

interface MethodComparisonRes {
  purchaseAmount: number;
  currentAvailableBalance: number;
  reserveThreshold: number;
  disclaimer: string;
  options: Array<{
    method: string;
    label: string;
    feeOrInterestCost: number;
    totalEstimatedCost: number;
    immediateCashFlowOutflow: number;
    futureMonthlyObligation: number;
    affordabilityImpact: string;
    assumptions: string;
  }>;
  emiDetails: EmiResult;
}

export function PaymentPlannerPage() {
  const { formatMoney, notifyToast } = useApp();
  const qc = useQueryClient();

  const [tab, setTab] = useState<'planner' | 'calendar' | 'emi'>('planner');
  const [obModal, setObModal] = useState(false);
  const [payTarget, setPayTarget] = useState<Obligation | null>(null);
  const [detailOb, setDetailOb] = useState<Obligation | null>(null);

  // Hypothetical drag-and-drop / shift overrides (never mutates contractual dates!)
  const [hypotheticalDates, setHypotheticalDates] = useState<Record<string, string>>({});
  const [draggedObId, setDraggedObId] = useState<string | null>(null);

  // New obligation form
  const [obTitle, setObTitle] = useState('');
  const [obCategory, setObCategory] = useState('Utilities');
  const [obAmount, setObAmount] = useState('');
  const [obDue, setObDue] = useState(new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 10));
  const [obFreq, setObFreq] = useState('MONTHLY');
  const [obEssential, setObEssential] = useState(true);
  const [obLateFee, setObLateFee] = useState('250');

  // Record manual payment form
  const [payAmt, setPayAmt] = useState('');
  const [payMethod, setPayMethod] = useState('UPI');
  const [payLinkTx, setPayLinkTx] = useState(true);

  // Method & EMI comparison state
  const [purchaseAmount, setPurchaseAmount] = useState('45000');
  const [emiRate, setEmiRate] = useState('14');
  const [emiTenure, setEmiTenure] = useState('6');
  const [emiFee, setEmiFee] = useState('500');
  const [rateModel, setRateModel] = useState<'FIXED_AMORTIZING' | 'ZERO_INTEREST' | 'VARIABLE_RATE'>('FIXED_AMORTIZING');
  const [methodRes, setMethodRes] = useState<MethodComparisonRes | null>(null);
  const [emiOnlyRes, setEmiOnlyRes] = useState<EmiResult | null>(null);

  const { data: obligations = [] } = useQuery({
    queryKey: ['obligations'],
    queryFn: () => apiRequest<Obligation[]>('/api/v1/obligations'),
  });

  const { data: planComparison } = useQuery({
    queryKey: ['payment-comparison', hypotheticalDates],
    queryFn: () =>
      apiRequest<PlanComparison>('/api/v1/payments/compare', {
        method: 'POST',
        body: JSON.stringify({ horizonDays: 60, hypotheticalDates }),
      }),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['obligations'] });
    qc.invalidateQueries({ queryKey: ['payment-comparison'] });
    qc.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const createObMut = useMutation({
    mutationFn: () =>
      apiRequest('/api/v1/obligations', {
        method: 'POST',
        body: JSON.stringify({
          title: obTitle,
          category: obCategory,
          totalAmount: Number(obAmount),
          dueDate: obDue,
          frequency: obFreq,
          essential: obEssential,
          lateFee: Number(obLateFee) || 0,
          graceDays: 3,
        }),
      }),
    onSuccess: () => {
      invalidate();
      setObModal(false);
      setObTitle('');
      setObAmount('');
      notifyToast('Obligation added.');
    },
  });

  const recordPayMut = useMutation({
    mutationFn: () =>
      apiRequest(`/api/v1/payments/${payTarget!.id}/record`, {
        method: 'POST',
        body: JSON.stringify({
          amount: Number(payAmt),
          paidOn: new Date().toISOString().slice(0, 10),
          paymentMethod: payMethod,
          recordLedgerTransaction: payLinkTx,
        }),
      }),
    onSuccess: () => {
      invalidate();
      setPayTarget(null);
      notifyToast('Manual payment recorded without double-counting.');
    },
  });

  const runMethodAndEmi = async (e: React.FormEvent) => {
    e.preventDefault();
    const effectiveRate = rateModel === 'ZERO_INTEREST' ? 0 : Number(emiRate);
    const [comp, emi] = await Promise.all([
      apiRequest<MethodComparisonRes>('/api/v1/payments/methods/compare', {
        method: 'POST',
        body: JSON.stringify({
          purchaseAmount: Number(purchaseAmount),
          upiFee: 0,
          debitCardFee: 0,
          creditCardFee: 0,
          creditCardMonthlyCarryRatePercent: 3.0,
          creditCardMonthsToPay: 1,
          netBankingFee: 15,
          emiAnnualInterestPercent: effectiveRate,
          emiTenureMonths: Number(emiTenure),
          emiProcessingFee: Number(emiFee),
        }),
      }),
      apiRequest<EmiResult>('/api/v1/calculators/emi', {
        method: 'POST',
        body: JSON.stringify({
          principal: Number(purchaseAmount),
          annualInterestRatePercent: effectiveRate,
          tenureMonths: Number(emiTenure),
          processingFee: Number(emiFee),
          rateModel,
        }),
      }),
    ]);
    setMethodRes(comp);
    setEmiOnlyRes(emi);
  };

  // Group obligations by upcoming week buckets for simulation calendar
  const calendarBuckets = [0, 7, 14, 21, 28].map((offset) => {
    const start = new Date(Date.now() + offset * 86400_000).toISOString().slice(0, 10);
    const end = new Date(Date.now() + (offset + 6) * 86400_000).toISOString().slice(0, 10);
    return { label: `Days +${offset} to +${offset + 6}`, dropDate: start, start, end };
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Adaptive Payment Planner & EMI Advisor</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            PriorityQueue obligation ordering, feasible scenario schedules, manual recording, and BigDecimal EMI amortization.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <div className="inline-flex rounded-md border p-0.5" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
            {[
              { id: 'planner', label: 'Priority & Schedules', icon: CheckCircle2 },
              { id: 'calendar', label: 'Simulation Calendar', icon: Calendar },
              { id: 'emi', label: 'Methods & EMI', icon: Calculator },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setTab(item.id as 'planner' | 'calendar' | 'emi')}
                  aria-pressed={tab === item.id}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium cursor-pointer"
                  style={{
                    background: tab === item.id ? 'var(--accent)' : 'transparent',
                    color: tab === item.id ? 'var(--accent-text)' : 'var(--text-primary)',
                  }}
                >
                  <Icon className="w-3.5 h-3.5" aria-hidden="true" />
                  {item.label}
                </button>
              );
            })}
          </div>
          <button
            type="button"
            onClick={() => setObModal(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md text-xs font-semibold cursor-pointer"
            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
          >
            <Plus className="w-3.5 h-3.5" aria-hidden="true" />
            Add Obligation
          </button>
        </div>
      </div>

      {tab === 'planner' && (
        <div className="space-y-6">
          {/* Obligations Priority Table */}
          <div className="surface-card overflow-hidden">
            <div className="p-4 border-b flex items-center justify-between" style={{ borderColor: 'var(--border)' }}>
              <h2 className="text-base font-semibold m-0">Priority-Ranked Upcoming & Overdue Obligations</h2>
              <span className="text-xs font-mono-num" style={{ color: 'var(--text-secondary)' }}>
                Available: {formatMoney(planComparison?.availableBalance)}
              </span>
            </div>
            <div className="table-wrapper" tabIndex={0} role="region" aria-label="Payment obligations table">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr style={{ background: 'var(--elevated)' }}>
                    <th scope="col" className="p-3 text-left text-xs">Rank</th>
                    <th scope="col" className="p-3 text-left text-xs">Obligation</th>
                    <th scope="col" className="p-3 text-left text-xs">Due Date</th>
                    <th scope="col" className="p-3 text-right text-xs">Remaining</th>
                    <th scope="col" className="p-3 text-left text-xs">Status</th>
                    <th scope="col" className="p-3 text-left text-xs">Affordability</th>
                    <th scope="col" className="p-3 text-right text-xs">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {obligations.map((ob) => (
                    <tr key={ob.id} className="border-t" style={{ borderColor: 'var(--border)' }}>
                      <td className="p-3 font-mono-num text-xs">#{ob.priorityRank}</td>
                      <td className="p-3">
                        <div className="font-semibold">{ob.title}</div>
                        <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                          {ob.category} • {ob.essential ? 'Essential' : 'Optional'} • Late fee {formatMoney(ob.lateFee)}
                        </div>
                      </td>
                      <td className="p-3 font-mono-num text-xs">{ob.dueDate}</td>
                      <td className="p-3 text-right font-mono-num font-semibold">{formatMoney(ob.remainingAmount)}</td>
                      <td className="p-3 text-xs">
                        <span className="px-2 py-0.5 rounded border text-[11px]" style={{ borderColor: ob.status === 'OVERDUE' ? 'var(--critical)' : 'var(--border)', color: ob.status === 'OVERDUE' ? 'var(--critical)' : 'var(--text-primary)' }}>
                          {ob.status}
                        </span>
                      </td>
                      <td className="p-3 text-xs font-medium" style={{ color: ob.affordabilityStatus === 'SHORTFALL' ? 'var(--critical)' : ob.affordabilityStatus === 'RESERVE_PRESSURE' ? 'var(--warning)' : 'var(--accent)' }}>
                        {ob.affordabilityStatus.replace('_', ' ')}
                      </td>
                      <td className="p-3 text-right whitespace-nowrap space-x-1.5">
                        <button
                          type="button"
                          onClick={() => setDetailOb(ob)}
                          className="px-2.5 py-1 rounded border text-xs cursor-pointer"
                          style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                        >
                          Details
                        </button>
                        {ob.remainingAmount > 0 && (
                          <button
                            type="button"
                            onClick={() => {
                              setPayTarget(ob);
                              setPayAmt(String(ob.remainingAmount));
                            }}
                            className="px-2.5 py-1 rounded text-xs font-semibold cursor-pointer"
                            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
                          >
                            Record Payment
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {obligations.length === 0 && (
                    <tr><td colSpan={7} className="p-6 text-center text-sm" style={{ color: 'var(--text-secondary)' }}>No obligations configured.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Feasible Scenario Schedules */}
          {planComparison && (
            <section className="space-y-3" aria-label="Alternative Payment Scenarios">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-base font-semibold m-0">Alternative Scenario Schedules</h2>
                <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>{planComparison.advisoryNotice}</span>
              </div>
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                {planComparison.scenarios.map((sc) => (
                  <div key={sc.strategy} className="surface-card p-4 space-y-2.5">
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="text-sm font-semibold m-0">{sc.scenarioName}</h3>
                      <span className="text-[11px] px-2 py-0.5 rounded font-semibold" style={{
                        background: 'var(--elevated)',
                        color: sc.feasible ? (sc.respectsReserveThreshold ? 'var(--accent)' : 'var(--warning)') : 'var(--critical)',
                      }}>
                        {sc.feasible ? (sc.respectsReserveThreshold ? 'Feasible' : 'Reserve Dip') : `Shortfall ${formatMoney(sc.shortfallAmount)}`}
                      </span>
                    </div>
                    <div className="text-xs font-mono-num">
                      Min Projected Balance: <strong>{formatMoney(sc.minProjectedBalance)}</strong>
                    </div>
                    <ul className="text-xs space-y-1 m-0 pl-4" style={{ color: 'var(--text-secondary)' }}>
                      {sc.consequences.map((c, idx) => <li key={idx}>{c}</li>)}
                    </ul>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {tab === 'calendar' && (
        <section className="surface-card p-5 space-y-4" aria-label="Simulation Payment Calendar">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold m-0">Hypothetical Payment Simulation Calendar</h2>
              <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>
                Drag obligations (or use the date selector for keyboard/touch accessibility) to test hypothetical timing. Actual contractual due dates never change.
              </p>
            </div>
            {Object.keys(hypotheticalDates).length > 0 && (
              <button
                type="button"
                onClick={() => setHypotheticalDates({})}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded border text-xs cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              >
                <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
                Reset Hypothetical Dates
              </button>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
            {calendarBuckets.map((bucket) => {
              const itemsInBucket = obligations.filter((ob) => {
                const effectiveDate = hypotheticalDates[ob.id] || ob.dueDate;
                return ob.remainingAmount > 0 && effectiveDate >= bucket.start && effectiveDate <= bucket.end;
              });
              return (
                <div
                  key={bucket.label}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={() => {
                    if (draggedObId) {
                      setHypotheticalDates((prev) => ({ ...prev, [draggedObId]: bucket.dropDate }));
                      setDraggedObId(null);
                      notifyToast('Simulated date updated (contractual due date untouched).');
                    }
                  }}
                  className="p-3 rounded-md border min-h-44 space-y-2"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                >
                  <div className="text-xs font-semibold">{bucket.label}</div>
                  <div className="text-[11px] font-mono-num" style={{ color: 'var(--text-secondary)' }}>{bucket.start}</div>
                  <div className="space-y-2">
                    {itemsInBucket.map((ob) => {
                      const simDate = hypotheticalDates[ob.id];
                      return (
                        <div
                          key={ob.id}
                          draggable
                          onDragStart={() => setDraggedObId(ob.id)}
                          className="p-2.5 rounded border text-xs space-y-1.5 cursor-grab"
                          style={{ borderColor: simDate ? 'var(--accent)' : 'var(--border)', background: 'var(--surface)' }}
                        >
                          <div className="font-semibold truncate">{ob.title}</div>
                          <div className="font-mono-num">{formatMoney(ob.remainingAmount)}</div>
                          <div className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                            Contractual: {ob.dueDate}
                            {simDate && <div style={{ color: 'var(--accent)' }}>Simulated: {simDate}</div>}
                          </div>
                          <label className="block text-[10px]" style={{ color: 'var(--text-secondary)' }}>
                            Simulate date:
                            <input
                              type="date"
                              value={simDate || ob.dueDate}
                              onChange={(e) => setHypotheticalDates((prev) => ({ ...prev, [ob.id]: e.target.value }))}
                              className="w-full mt-0.5 px-1.5 py-1 rounded border text-[11px]"
                              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                            />
                          </label>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {tab === 'emi' && (
        <div className="space-y-6">
          <form onSubmit={runMethodAndEmi} className="surface-card p-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end">
            <div>
              <label htmlFor="emi-amt" className="block text-xs font-semibold mb-1">Purchase / Principal</label>
              <input id="emi-amt" type="number" min="1" required value={purchaseAmount} onChange={(e) => setPurchaseAmount(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="emi-model" className="block text-xs font-semibold mb-1">Rate Model</label>
              <select id="emi-model" value={rateModel} onChange={(e) => setRateModel(e.target.value as 'FIXED_AMORTIZING' | 'ZERO_INTEREST' | 'VARIABLE_RATE')} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="FIXED_AMORTIZING">Fixed Amortizing</option>
                <option value="ZERO_INTEREST">Zero-Interest (0%)</option>
                <option value="VARIABLE_RATE">Variable Rate (Unsupported check)</option>
              </select>
            </div>
            <div>
              <label htmlFor="emi-rate" className="block text-xs font-semibold mb-1">Annual Rate (%)</label>
              <input id="emi-rate" type="number" min="0" step="0.1" disabled={rateModel === 'ZERO_INTEREST'} value={rateModel === 'ZERO_INTEREST' ? '0' : emiRate} onChange={(e) => setEmiRate(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="emi-ten" className="block text-xs font-semibold mb-1">Tenure (Months)</label>
              <input id="emi-ten" type="number" min="1" max="120" required value={emiTenure} onChange={(e) => setEmiTenure(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="emi-fee" className="block text-xs font-semibold mb-1">Processing Fee</label>
              <input id="emi-fee" type="number" min="0" value={emiFee} onChange={(e) => setEmiFee(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <button type="submit" className="px-4 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
              Compare & Calculate
            </button>
          </form>

          {methodRes && (
            <div className="surface-card overflow-hidden">
              <div className="p-4 border-b flex flex-wrap justify-between gap-2" style={{ borderColor: 'var(--border)' }}>
                <h2 className="text-base font-semibold m-0 flex items-center gap-2">
                  <CreditCard className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                  Payment Method Comparison
                </h2>
                <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>{methodRes.disclaimer}</span>
              </div>
              <div className="table-wrapper" tabIndex={0} role="region" aria-label="Payment method comparison table">
                <table className="w-full text-xs border-collapse">
                  <thead>
                    <tr style={{ background: 'var(--elevated)' }}>
                      <th scope="col" className="p-3 text-left">Method</th>
                      <th scope="col" className="p-3 text-right">Fee / Interest</th>
                      <th scope="col" className="p-3 text-right">Total Cost</th>
                      <th scope="col" className="p-3 text-right">Immediate Outflow</th>
                      <th scope="col" className="p-3 text-right">Monthly Commitment</th>
                      <th scope="col" className="p-3 text-left">Affordability & Assumptions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {methodRes.options.map((opt) => (
                      <tr key={opt.method} className="border-t" style={{ borderColor: 'var(--border)' }}>
                        <td className="p-3 font-semibold text-sm">{opt.label}</td>
                        <td className="p-3 text-right font-mono-num">{formatMoney(opt.feeOrInterestCost)}</td>
                        <td className="p-3 text-right font-mono-num font-semibold">{formatMoney(opt.totalEstimatedCost)}</td>
                        <td className="p-3 text-right font-mono-num">{formatMoney(opt.immediateCashFlowOutflow)}</td>
                        <td className="p-3 text-right font-mono-num">{formatMoney(opt.futureMonthlyObligation)}</td>
                        <td className="p-3">
                          <div>{opt.affordabilityImpact}</div>
                          <div style={{ color: 'var(--text-secondary)' }}>{opt.assumptions}</div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {emiOnlyRes && (
            <div className="surface-card p-5 space-y-4">
              {!emiOnlyRes.supported ? (
                <div role="alert" className="p-3 rounded border text-sm flex items-center gap-2" style={{ borderColor: 'var(--warning)', color: 'var(--warning)' }}>
                  <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
                  <span>{emiOnlyRes.unsupportedReason}</span>
                </div>
              ) : (
                <>
                  <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
                    <div className="p-3 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                      <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Monthly EMI</div>
                      <div className="text-lg font-bold font-mono-num">{formatMoney(emiOnlyRes.monthlyEmi)}</div>
                    </div>
                    <div className="p-3 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                      <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Total Interest</div>
                      <div className="text-lg font-bold font-mono-num">{formatMoney(emiOnlyRes.totalInterest)}</div>
                    </div>
                    <div className="p-3 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                      <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Total Payable (incl. Fee)</div>
                      <div className="text-lg font-bold font-mono-num">{formatMoney(emiOnlyRes.totalPayableWithFee)}</div>
                    </div>
                    <div className="p-3 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                      <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Reconciled Principal</div>
                      <div className="text-lg font-bold font-mono-num" style={{ color: 'var(--accent)' }}>{formatMoney(emiOnlyRes.reconciledPrincipalSum)}</div>
                    </div>
                  </div>

                  <div className="h-56 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={emiOnlyRes.schedule}>
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis dataKey="installmentNumber" tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                        <YAxis tick={{ fontSize: 11, fill: 'var(--text-secondary)' }} />
                        <Tooltip contentStyle={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', fontSize: '12px' }} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Bar dataKey="principalComponent" name="Principal" stackId="a" fill="var(--accent)" />
                        <Bar dataKey="interestComponent" name="Interest" stackId="a" fill="var(--warning)" />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {/* Add Obligation Modal */}
      <ModalDialog open={obModal} onClose={() => setObModal(false)} title="Create Financial Obligation">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createObMut.mutate();
          }}
          className="space-y-3"
        >
          <div>
            <label htmlFor="ob-title" className="block text-xs font-semibold mb-1">Title</label>
            <input id="ob-title" required value={obTitle} onChange={(e) => setObTitle(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="ob-cat" className="block text-xs font-semibold mb-1">Category</label>
              <input id="ob-cat" required value={obCategory} onChange={(e) => setObCategory(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="ob-amt" className="block text-xs font-semibold mb-1">Total Amount</label>
              <input id="ob-amt" type="number" step="0.01" min="0.01" required value={obAmount} onChange={(e) => setObAmount(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label htmlFor="ob-due" className="block text-xs font-semibold mb-1">Due Date</label>
              <input id="ob-due" type="date" required value={obDue} onChange={(e) => setObDue(e.target.value)} className="w-full px-2 py-2 rounded border text-xs" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="ob-freq" className="block text-xs font-semibold mb-1">Frequency</label>
              <select id="ob-freq" value={obFreq} onChange={(e) => setObFreq(e.target.value)} className="w-full px-2 py-2 rounded border text-xs" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="ONE_TIME">One-time</option>
                <option value="MONTHLY">Monthly</option>
                <option value="QUARTERLY">Quarterly</option>
                <option value="ANNUAL">Annual</option>
              </select>
            </div>
            <div>
              <label htmlFor="ob-fee" className="block text-xs font-semibold mb-1">Late Fee</label>
              <input id="ob-fee" type="number" min="0" value={obLateFee} onChange={(e) => setObLateFee(e.target.value)} className="w-full px-2 py-2 rounded border text-xs font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>
          <label className="inline-flex items-center gap-2 text-xs">
            <input type="checkbox" checked={obEssential} onChange={(e) => setObEssential(e.target.checked)} />
            Essential contractual commitment
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setObModal(false)} className="px-3 py-2 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="submit" className="px-4 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>Save Obligation</button>
          </div>
        </form>
      </ModalDialog>

      {/* Record Manual Payment Modal */}
      <ModalDialog open={Boolean(payTarget)} onClose={() => setPayTarget(null)} title={`Record Payment — ${payTarget?.title}`}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            recordPayMut.mutate();
          }}
          className="space-y-3"
        >
          <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>
            Manual record only (not a real bank transfer). Remaining balance: <strong className="font-mono-num">{formatMoney(payTarget?.remainingAmount)}</strong>.
          </p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="pay-amt" className="block text-xs font-semibold mb-1">Amount Paid</label>
              <input id="pay-amt" type="number" step="0.01" min="0.01" max={payTarget?.remainingAmount} required value={payAmt} onChange={(e) => setPayAmt(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="pay-method" className="block text-xs font-semibold mb-1">Method</label>
              <select id="pay-method" value={payMethod} onChange={(e) => setPayMethod(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <option value="UPI">UPI</option>
                <option value="DEBIT_CARD">Debit Card</option>
                <option value="NET_BANKING">Net Banking</option>
                <option value="CREDIT_CARD">Credit Card</option>
                <option value="MANUAL">Manual / Cash</option>
              </select>
            </div>
          </div>
          <label className="inline-flex items-center gap-2 text-xs">
            <input type="checkbox" checked={payLinkTx} onChange={(e) => setPayLinkTx(e.target.checked)} />
            Also record linked ledger transaction (prevents double-counting in projections)
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setPayTarget(null)} className="px-3 py-2 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="submit" className="px-4 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>Record Settlement</button>
          </div>
        </form>
      </ModalDialog>

      {/* Obligation Detail Drawer/Modal */}
      <ModalDialog open={Boolean(detailOb)} onClose={() => setDetailOb(null)} title={detailOb?.title || 'Obligation Details'}>
        {detailOb && (
          <div className="space-y-2 text-sm">
            <div className="flex justify-between"><span style={{ color: 'var(--text-secondary)' }}>Total Amount</span><span className="font-mono-num font-semibold">{formatMoney(detailOb.totalAmount)}</span></div>
            <div className="flex justify-between"><span style={{ color: 'var(--text-secondary)' }}>Paid So Far</span><span className="font-mono-num">{formatMoney(detailOb.paidAmount)}</span></div>
            <div className="flex justify-between"><span style={{ color: 'var(--text-secondary)' }}>Remaining</span><span className="font-mono-num font-bold">{formatMoney(detailOb.remainingAmount)}</span></div>
            <div className="flex justify-between"><span style={{ color: 'var(--text-secondary)' }}>Contractual Due Date</span><span className="font-mono-num">{detailOb.dueDate}</span></div>
            <div className="flex justify-between"><span style={{ color: 'var(--text-secondary)' }}>Late Fee / Grace Days</span><span className="font-mono-num">{formatMoney(detailOb.lateFee)} / {detailOb.graceDays}d</span></div>
            <div className="flex justify-between"><span style={{ color: 'var(--text-secondary)' }}>Priority Rank</span><span className="font-mono-num">#{detailOb.priorityRank}</span></div>
          </div>
        )}
      </ModalDialog>
    </div>
  );
}

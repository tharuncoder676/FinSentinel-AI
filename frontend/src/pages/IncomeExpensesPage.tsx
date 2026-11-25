import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, Check, X, Sparkles, AlertTriangle } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface IncomeSource {
  id: string;
  name: string;
  amount: number;
  frequency: string;
  expectedDay: number | null;
  nextExpectedDate: string | null;
  stabilityScore: number;
  active: boolean;
}

interface ExpenseTemplate {
  id: string;
  title: string;
  amount: number;
  frequency: string;
  essential: boolean;
  nextDueDate: string | null;
  active: boolean;
}

interface MlInsights {
  modelVersion: string;
  dataSufficiency: string;
  movingAverageMonthlyExpense: number;
  exponentialSmoothingNextMonthExpense: number;
  knownLimitations: string[];
  evaluation: {
    evaluationStatus: string;
    sampleSize: number;
    exponentialSmoothingMae: number | null;
    nextStep: string;
  };
  anomalies: Array<{
    transactionId: string;
    occurredOn: string;
    merchant: string;
    amount: number;
    baselineMean: number;
    zScore: number;
    status: string;
    explanation: string;
  }>;
  categorySuggestions: Array<{
    transactionId: string;
    merchant: string;
    suggestedCategoryId: string;
    suggestedCategoryName: string;
    basis: string;
  }>;
  recurringPatterns: Array<{
    merchant: string;
    typicalAmount: number;
    occurrences: number;
    estimatedCadence: string;
  }>;
}

export function IncomeExpensesPage() {
  const { formatMoney, notifyToast } = useApp();
  const qc = useQueryClient();

  const [incName, setIncName] = useState('');
  const [incAmt, setIncAmt] = useState('');
  const [incFreq, setIncFreq] = useState('MONTHLY');
  const [incDate, setIncDate] = useState('');

  const [expTitle, setExpTitle] = useState('');
  const [expAmt, setExpAmt] = useState('');
  const [expFreq, setExpFreq] = useState('MONTHLY');
  const [expEssential, setExpEssential] = useState(true);

  const { data: incomes = [] } = useQuery({
    queryKey: ['income'],
    queryFn: () => apiRequest<IncomeSource[]>('/api/v1/income'),
  });
  const { data: expenses = [] } = useQuery({
    queryKey: ['expenses'],
    queryFn: () => apiRequest<ExpenseTemplate[]>('/api/v1/expenses'),
  });
  const { data: ml } = useQuery({
    queryKey: ['ml-insights'],
    queryFn: () => apiRequest<MlInsights>('/api/v1/ml/insights'),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['income'] });
    qc.invalidateQueries({ queryKey: ['expenses'] });
    qc.invalidateQueries({ queryKey: ['dashboard'] });
    qc.invalidateQueries({ queryKey: ['ml-insights'] });
  };

  const addIncomeMut = useMutation({
    mutationFn: () =>
      apiRequest('/api/v1/income', {
        method: 'POST',
        body: JSON.stringify({
          name: incName,
          amount: Number(incAmt),
          frequency: incFreq,
          nextExpectedDate: incDate || null,
          stabilityScore: 0.9,
          active: true,
        }),
      }),
    onSuccess: () => {
      invalidate();
      setIncName('');
      setIncAmt('');
      notifyToast('Income source added.');
    },
  });

  const delIncomeMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/income/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });

  const addExpenseMut = useMutation({
    mutationFn: () =>
      apiRequest('/api/v1/expenses', {
        method: 'POST',
        body: JSON.stringify({
          title: expTitle,
          amount: Number(expAmt),
          frequency: expFreq,
          essential: expEssential,
          active: true,
        }),
      }),
    onSuccess: () => {
      invalidate();
      setExpTitle('');
      setExpAmt('');
      notifyToast('Recurring expense template added.');
    },
  });

  const delExpenseMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/expenses/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });

  const handleAnomaly = async (txId: string, action: 'confirm' | 'dismiss') => {
    await apiRequest(`/api/v1/ml/anomalies/${txId}/${action}`, { method: 'POST' });
    invalidate();
    notifyToast(`Anomaly marked ${action}ed.`);
  };

  const applyCategory = async (txId: string, categoryId: string) => {
    await apiRequest(`/api/v1/ml/categories/${txId}/correct`, {
      method: 'POST',
      body: JSON.stringify({ categoryId }),
    });
    invalidate();
    notifyToast('Category suggestion applied.');
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Income Sources & Recurring Expenses</h1>
        <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
          Configure expected cash inflows, recurring expense templates, and inspect statistical spending baselines.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Income Sources */}
        <section className="surface-card p-5 space-y-4">
          <h2 className="text-base font-semibold m-0">Income Streams</h2>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              addIncomeMut.mutate();
            }}
            className="grid grid-cols-1 sm:grid-cols-4 gap-2"
          >
            <input required placeholder="Source name" aria-label="Income source name" value={incName} onChange={(e) => setIncName(e.target.value)} className="sm:col-span-2 px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            <input required type="number" min="1" placeholder="Amount" aria-label="Income amount" value={incAmt} onChange={(e) => setIncAmt(e.target.value)} className="px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            <select aria-label="Income frequency" value={incFreq} onChange={(e) => setIncFreq(e.target.value)} className="px-2 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <option value="MONTHLY">Monthly</option>
              <option value="BIWEEKLY">Bi-weekly</option>
              <option value="WEEKLY">Weekly</option>
              <option value="ONE_TIME">One-time</option>
            </select>
            <input type="date" aria-label="Next expected income date" value={incDate} onChange={(e) => setIncDate(e.target.value)} className="sm:col-span-2 px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            <button type="submit" className="sm:col-span-2 inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
              <Plus className="w-3.5 h-3.5" aria-hidden="true" />
              Add Income Source
            </button>
          </form>

          <ul className="divide-y list-none p-0 m-0" style={{ borderColor: 'var(--border)' }}>
            {incomes.map((i) => (
              <li key={i.id} className="py-2.5 flex items-center justify-between gap-2">
                <div>
                  <div className="text-sm font-medium">{i.name}</div>
                  <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                    {i.frequency} {i.nextExpectedDate ? `• Next: ${i.nextExpectedDate}` : ''} • Stability: {Math.round((i.stabilityScore || 0.9) * 100)}%
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-mono-num font-semibold text-sm" style={{ color: 'var(--accent)' }}>{formatMoney(i.amount)}</span>
                  <button type="button" aria-label={`Remove ${i.name}`} onClick={() => delIncomeMut.mutate(i.id)} className="p-1.5 rounded border cursor-pointer" style={{ borderColor: 'var(--border)', color: 'var(--critical)' }}>
                    <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                  </button>
                </div>
              </li>
            ))}
            {incomes.length === 0 && <li className="py-4 text-xs" style={{ color: 'var(--text-secondary)' }}>No income streams recorded yet.</li>}
          </ul>
        </section>

        {/* Recurring Expenses */}
        <section className="surface-card p-5 space-y-4">
          <h2 className="text-base font-semibold m-0">Recurring Expense Templates</h2>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              addExpenseMut.mutate();
            }}
            className="grid grid-cols-1 sm:grid-cols-4 gap-2"
          >
            <input required placeholder="Expense title" aria-label="Expense title" value={expTitle} onChange={(e) => setExpTitle(e.target.value)} className="sm:col-span-2 px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            <input required type="number" min="1" placeholder="Amount" aria-label="Expense amount" value={expAmt} onChange={(e) => setExpAmt(e.target.value)} className="px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            <select aria-label="Expense frequency" value={expFreq} onChange={(e) => setExpFreq(e.target.value)} className="px-2 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <option value="MONTHLY">Monthly</option>
              <option value="WEEKLY">Weekly</option>
              <option value="QUARTERLY">Quarterly</option>
              <option value="ANNUAL">Annual</option>
            </select>
            <label className="sm:col-span-2 inline-flex items-center gap-2 text-xs px-1">
              <input type="checkbox" checked={expEssential} onChange={(e) => setExpEssential(e.target.checked)} />
              Essential living expense
            </label>
            <button type="submit" className="sm:col-span-2 inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
              <Plus className="w-3.5 h-3.5" aria-hidden="true" />
              Add Expense Template
            </button>
          </form>

          <ul className="divide-y list-none p-0 m-0" style={{ borderColor: 'var(--border)' }}>
            {expenses.map((e) => (
              <li key={e.id} className="py-2.5 flex items-center justify-between gap-2">
                <div>
                  <div className="text-sm font-medium">{e.title}</div>
                  <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                    {e.frequency} • {e.essential ? 'Essential' : 'Discretionary'}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-mono-num font-semibold text-sm">{formatMoney(e.amount)}</span>
                  <button type="button" aria-label={`Remove ${e.title}`} onClick={() => delExpenseMut.mutate(e.id)} className="p-1.5 rounded border cursor-pointer" style={{ borderColor: 'var(--border)', color: 'var(--critical)' }}>
                    <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                  </button>
                </div>
              </li>
            ))}
            {expenses.length === 0 && <li className="py-4 text-xs" style={{ color: 'var(--text-secondary)' }}>No recurring expense templates yet.</li>}
          </ul>
        </section>
      </div>

      {/* Statistical & ML Baseline Intelligence */}
      {ml && (
        <section className="surface-card p-5 space-y-4" aria-label="Statistical Baselines & Anomaly Detection">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold m-0 flex items-center gap-2">
                <Sparkles className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                Spending Baselines & Anomaly Detection ({ml.modelVersion})
              </h2>
              <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                Data Sufficiency: <strong>{ml.dataSufficiency}</strong> • Evaluation: <strong>{ml.evaluation.evaluationStatus}</strong>
                {ml.evaluation.exponentialSmoothingMae !== null && ` • One-step MAE: ${formatMoney(ml.evaluation.exponentialSmoothingMae)}`}
              </p>
            </div>
            <div className="text-xs font-mono-num">
              30d Moving Avg: <strong>{formatMoney(ml.movingAverageMonthlyExpense)}</strong> • Smoothed Next Mo: <strong>{formatMoney(ml.exponentialSmoothingNextMonthExpense)}</strong>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="p-3 rounded-md border space-y-2" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <h3 className="text-xs font-semibold uppercase tracking-wider m-0">Flagged Unusual Transactions</h3>
              {ml.anomalies.length === 0 ? (
                <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>No statistical spending anomalies flagged.</p>
              ) : (
                ml.anomalies.map((a) => (
                  <div key={a.transactionId} className="p-2.5 rounded border text-xs flex items-start justify-between gap-2" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                    <div>
                      <div className="font-semibold flex items-center gap-1.5">
                        <AlertTriangle className="w-3.5 h-3.5" style={{ color: 'var(--warning)' }} aria-hidden="true" />
                        {a.merchant} — <span className="font-mono-num">{formatMoney(a.amount)}</span>
                      </div>
                      <div style={{ color: 'var(--text-secondary)' }}>{a.occurredOn} • z={a.zScore} • {a.explanation}</div>
                    </div>
                    {a.status === 'FLAGGED' && (
                      <div className="flex gap-1 shrink-0">
                        <button type="button" onClick={() => handleAnomaly(a.transactionId, 'confirm')} title="Confirm valid spend" aria-label="Confirm anomaly" className="p-1 rounded border cursor-pointer" style={{ borderColor: 'var(--border)', color: 'var(--accent)' }}>
                          <Check className="w-3.5 h-3.5" aria-hidden="true" />
                        </button>
                        <button type="button" onClick={() => handleAnomaly(a.transactionId, 'dismiss')} title="Dismiss" aria-label="Dismiss anomaly" className="p-1 rounded border cursor-pointer" style={{ borderColor: 'var(--border)' }}>
                          <X className="w-3.5 h-3.5" aria-hidden="true" />
                        </button>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>

            <div className="p-3 rounded-md border space-y-2" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <h3 className="text-xs font-semibold uppercase tracking-wider m-0">Category Suggestions & Limitations</h3>
              {ml.categorySuggestions.length > 0 && (
                <div className="space-y-1.5">
                  {ml.categorySuggestions.map((s) => (
                    <div key={s.transactionId} className="p-2 rounded border text-xs flex items-center justify-between gap-2" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                      <div>
                        <strong>{s.merchant || 'Transaction'}</strong> → {s.suggestedCategoryName} <span style={{ color: 'var(--text-secondary)' }}>({s.basis})</span>
                      </div>
                      <button type="button" onClick={() => applyCategory(s.transactionId, s.suggestedCategoryId)} className="px-2 py-1 rounded text-[11px] font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
                        Apply
                      </button>
                    </div>
                  ))}
                </div>
              )}
              <ul className="text-[11px] space-y-1 m-0 pl-4" style={{ color: 'var(--text-secondary)' }}>
                {ml.knownLimitations.map((lim) => <li key={lim}>{lim}</li>)}
                <li>{ml.evaluation.nextStep}</li>
              </ul>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

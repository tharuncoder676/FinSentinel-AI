import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Target, Plus, Trash2, TrendingUp } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';
import { ModalDialog } from '../components/ModalDialog';

interface Goal {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string | null;
  monthlyContribution: number;
  priority: number;
  status: string;
  progressPercent: number;
}

interface RiskHistory {
  id: string;
  policyVersion: string;
  displayScore: number;
  category: string;
  dataCompleteness: number;
  calculatedAt: string;
}

export function GoalsPage() {
  const { formatMoney, notifyToast } = useApp();
  const qc = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [targetAmt, setTargetAmt] = useState('');
  const [currentAmt, setCurrentAmt] = useState('0');
  const [monthlyContrib, setMonthlyContrib] = useState('5000');
  const [targetDate, setTargetDate] = useState('');
  const [contribTarget, setContribTarget] = useState<Goal | null>(null);
  const [contribAmt, setContribAmt] = useState('5000');

  const { data: goals = [] } = useQuery({
    queryKey: ['goals'],
    queryFn: () => apiRequest<Goal[]>('/api/v1/goals'),
  });

  const { data: riskHistory = [] } = useQuery({
    queryKey: ['risk-history'],
    queryFn: () => apiRequest<RiskHistory[]>('/api/v1/risk/history'),
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['goals'] });
    qc.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const createMut = useMutation({
    mutationFn: () =>
      apiRequest('/api/v1/goals', {
        method: 'POST',
        body: JSON.stringify({
          name,
          targetAmount: Number(targetAmt),
          currentAmount: Number(currentAmt) || 0,
          monthlyContribution: Number(monthlyContrib) || 0,
          targetDate: targetDate || null,
          priority: 1,
        }),
      }),
    onSuccess: () => {
      invalidate();
      setModalOpen(false);
      setName('');
      setTargetAmt('');
      notifyToast('Financial goal added.');
    },
  });

  const contribMut = useMutation({
    mutationFn: () =>
      apiRequest(`/api/v1/goals/${contribTarget!.id}/contribute`, {
        method: 'POST',
        body: JSON.stringify({ amount: Number(contribAmt) }),
      }),
    onSuccess: () => {
      invalidate();
      setContribTarget(null);
      notifyToast('Goal contribution recorded.');
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/goals/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Financial Goals & Health History</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Progress reflects actual recorded contributions only (no return guarantees).
          </p>
        </div>
        <button
          type="button"
          onClick={() => setModalOpen(true)}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md text-xs font-semibold cursor-pointer"
          style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
        >
          <Plus className="w-3.5 h-3.5" aria-hidden="true" />
          New Goal
        </button>
      </div>

      <section className="grid grid-cols-1 md:grid-cols-2 gap-4" aria-label="Savings Goals">
        {goals.map((g) => (
          <div key={g.id} className="surface-card p-5 space-y-3">
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-center gap-2">
                <Target className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                <h2 className="text-base font-semibold m-0">{g.name}</h2>
              </div>
              <span className="text-[11px] px-2 py-0.5 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                {g.status}
              </span>
            </div>

            <div className="flex justify-between text-xs font-mono-num">
              <span>{formatMoney(g.currentAmount)} / {formatMoney(g.targetAmount)}</span>
              <strong>{g.progressPercent}%</strong>
            </div>

            <div className="w-full h-2.5 rounded-full overflow-hidden" style={{ background: 'var(--elevated)' }}>
              <div className="h-full transition-all duration-200" style={{ width: `${g.progressPercent}%`, background: 'var(--accent)' }} />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 text-xs pt-1" style={{ color: 'var(--text-secondary)' }}>
              <span>Monthly plan: <strong className="font-mono-num">{formatMoney(g.monthlyContribution)}</strong> {g.targetDate ? `• Target: ${g.targetDate}` : ''}</span>
              <div className="flex gap-1.5">
                <button
                  type="button"
                  onClick={() => setContribTarget(g)}
                  className="px-2.5 py-1 rounded text-xs font-semibold cursor-pointer"
                  style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
                >
                  + Contribute
                </button>
                <button
                  type="button"
                  aria-label={`Delete ${g.name}`}
                  onClick={() => deleteMut.mutate(g.id)}
                  className="p-1.5 rounded border cursor-pointer"
                  style={{ borderColor: 'var(--border)', color: 'var(--critical)' }}
                >
                  <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        ))}
        {goals.length === 0 && (
          <div className="surface-card p-6 md:col-span-2 text-sm" style={{ color: 'var(--text-secondary)' }}>
            No savings goals created yet.
          </div>
        )}
      </section>

      {/* Versioned Financial Health History */}
      <section className="surface-card p-5 space-y-3" aria-label="Financial Health Snapshot History">
        <div className="flex items-center gap-2">
          <TrendingUp className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
          <h2 className="text-base font-semibold m-0">Versioned Financial Health Snapshots</h2>
        </div>
        <div className="table-wrapper" tabIndex={0} role="region" aria-label="Health history table">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr style={{ background: 'var(--elevated)' }}>
                <th scope="col" className="p-2.5 text-left">Timestamp</th>
                <th scope="col" className="p-2.5 text-left">Policy Version</th>
                <th scope="col" className="p-2.5 text-right">Score</th>
                <th scope="col" className="p-2.5 text-left">Category</th>
                <th scope="col" className="p-2.5 text-right">Completeness</th>
              </tr>
            </thead>
            <tbody>
              {riskHistory.map((h) => (
                <tr key={h.id} className="border-t" style={{ borderColor: 'var(--border)' }}>
                  <td className="p-2.5 font-mono-num">{new Date(h.calculatedAt).toLocaleString()}</td>
                  <td className="p-2.5 font-mono-num">{h.policyVersion}</td>
                  <td className="p-2.5 text-right font-mono-num font-semibold">{h.displayScore}/100</td>
                  <td className="p-2.5">{h.category}</td>
                  <td className="p-2.5 text-right font-mono-num">{Math.round(h.dataCompleteness * 100)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <ModalDialog open={modalOpen} onClose={() => setModalOpen(false)} title="Create Financial Goal">
        <form onSubmit={(e) => { e.preventDefault(); createMut.mutate(); }} className="space-y-3">
          <div>
            <label htmlFor="goal-name" className="block text-xs font-semibold mb-1">Goal Name</label>
            <input id="goal-name" required value={name} onChange={(e) => setName(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="goal-target" className="block text-xs font-semibold mb-1">Target Amount</label>
              <input id="goal-target" type="number" min="1" required value={targetAmt} onChange={(e) => setTargetAmt(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="goal-curr" className="block text-xs font-semibold mb-1">Starting Amount</label>
              <input id="goal-curr" type="number" min="0" value={currentAmt} onChange={(e) => setCurrentAmt(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="goal-mo" className="block text-xs font-semibold mb-1">Planned Monthly Contribution</label>
              <input id="goal-mo" type="number" min="0" value={monthlyContrib} onChange={(e) => setMonthlyContrib(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
            <div>
              <label htmlFor="goal-date" className="block text-xs font-semibold mb-1">Optional Target Date</label>
              <input id="goal-date" type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setModalOpen(false)} className="px-3 py-2 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="submit" className="px-4 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>Save Goal</button>
          </div>
        </form>
      </ModalDialog>

      <ModalDialog open={Boolean(contribTarget)} onClose={() => setContribTarget(null)} title={`Record Contribution — ${contribTarget?.name}`}>
        <form onSubmit={(e) => { e.preventDefault(); contribMut.mutate(); }} className="space-y-3">
          <div>
            <label htmlFor="contrib-amt" className="block text-xs font-semibold mb-1">Contribution Amount</label>
            <input id="contrib-amt" type="number" min="1" required value={contribAmt} onChange={(e) => setContribAmt(e.target.value)} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setContribTarget(null)} className="px-3 py-2 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="submit" className="px-4 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>Record Contribution</button>
          </div>
        </form>
      </ModalDialog>
    </div>
  );
}

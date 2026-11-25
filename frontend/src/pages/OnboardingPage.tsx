import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Check, Sparkles } from 'lucide-react';
import { BrandMark } from '../components/BrandMark';
import { useApp } from '../context/AppContext';
import { apiRequest } from '../lib/api';

export function OnboardingPage() {
  const { refreshUser, notifyToast, formatMoney } = useApp();
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [monthlyIncome, setMonthlyIncome] = useState('');
  const [otherIncome, setOtherIncome] = useState('');
  const [incomeFrequency, setIncomeFrequency] = useState('MONTHLY');
  const [regularExpenses, setRegularExpenses] = useState('');
  const [liquidFunds, setLiquidFunds] = useState('');
  const [reserveThreshold, setReserveThreshold] = useState('15000');
  const [saving, setSaving] = useState(false);

  const submitOnboarding = async (skipped: boolean) => {
    setSaving(true);
    try {
      await apiRequest('/api/v1/onboarding', {
        method: 'POST',
        body: JSON.stringify({
          monthlyIncome: monthlyIncome ? Number(monthlyIncome) : null,
          otherIncome: otherIncome ? Number(otherIncome) : null,
          regularExpenses: regularExpenses ? Number(regularExpenses) : null,
          liquidFunds: liquidFunds ? Number(liquidFunds) : null,
          reserveThreshold: reserveThreshold ? Number(reserveThreshold) : 15000,
          incomeFrequency,
          skipped,
        }),
      });
      await refreshUser();
      notifyToast(skipped ? 'Entered workspace. Add records anytime to unlock full analytics.' : 'Initial baseline saved.');
      navigate('/app/overview');
    } finally {
      setSaving(false);
    }
  };

  const enterDemo = async () => {
    setSaving(true);
    try {
      await apiRequest('/api/v1/public/demo-session', { method: 'POST' });
      await refreshUser();
      navigate('/app/overview');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-center px-4 py-10" style={{ background: 'var(--bg)', color: 'var(--text-primary)' }}>
      <div className="max-w-xl w-full mx-auto surface-card p-6 sm:p-8 space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <BrandMark size={26} />
            <span className="font-semibold text-sm">Workspace Setup</span>
          </div>
          <span className="text-xs font-mono-num" style={{ color: 'var(--text-secondary)' }}>Step {step} of 3</span>
        </div>

        <div className="w-full h-1.5 rounded-full overflow-hidden" style={{ background: 'var(--elevated)' }}>
          <div className="h-full transition-all duration-200" style={{ width: `${(step / 3) * 100}%`, background: 'var(--accent)' }} />
        </div>

        {step === 1 && (
          <div className="space-y-4">
            <h1 className="text-xl font-bold m-0">Optional Income Baseline</h1>
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>
              Supply monthly and secondary income if you wish to generate an initial assessment immediately, or skip sensitive fields.
            </p>
            <div>
              <label htmlFor="ob-inc" className="block text-xs font-semibold mb-1">Primary Monthly Income</label>
              <input
                id="ob-inc"
                type="number"
                min="0"
                placeholder="Optional (e.g. 85000)"
                value={monthlyIncome}
                onChange={(e) => setMonthlyIncome(e.target.value)}
                className="w-full px-3 py-2.5 rounded-md border text-sm font-mono-num"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="ob-other" className="block text-xs font-semibold mb-1">Other Monthly Income</label>
                <input
                  id="ob-other"
                  type="number"
                  min="0"
                  placeholder="Optional"
                  value={otherIncome}
                  onChange={(e) => setOtherIncome(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm font-mono-num"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                />
              </div>
              <div>
                <label htmlFor="ob-freq" className="block text-xs font-semibold mb-1">Income Frequency</label>
                <select
                  id="ob-freq"
                  value={incomeFrequency}
                  onChange={(e) => setIncomeFrequency(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                >
                  <option value="MONTHLY">Monthly</option>
                  <option value="BIWEEKLY">Bi-weekly</option>
                  <option value="WEEKLY">Weekly</option>
                  <option value="IRREGULAR">Irregular</option>
                </select>
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <h1 className="text-xl font-bold m-0">Expenses & Liquidity Buffer</h1>
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>
              Define your regular monthly outflows, liquid funds, and safety reserve floor.
            </p>
            <div>
              <label htmlFor="ob-exp" className="block text-xs font-semibold mb-1">Regular Monthly Expenses</label>
              <input
                id="ob-exp"
                type="number"
                min="0"
                placeholder="Optional (e.g. 45000)"
                value={regularExpenses}
                onChange={(e) => setRegularExpenses(e.target.value)}
                className="w-full px-3 py-2.5 rounded-md border text-sm font-mono-num"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="ob-liq" className="block text-xs font-semibold mb-1">Available Liquid Funds</label>
                <input
                  id="ob-liq"
                  type="number"
                  min="0"
                  placeholder="Optional"
                  value={liquidFunds}
                  onChange={(e) => setLiquidFunds(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm font-mono-num"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                />
              </div>
              <div>
                <label htmlFor="ob-res" className="block text-xs font-semibold mb-1">Reserve Alert Threshold</label>
                <input
                  id="ob-res"
                  type="number"
                  min="0"
                  value={reserveThreshold}
                  onChange={(e) => setReserveThreshold(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm font-mono-num"
                  style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                />
              </div>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <h1 className="text-xl font-bold m-0">Review Initial Baseline</h1>
            <div className="p-4 rounded-lg border space-y-2 text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Monthly Income</span>
                <span className="font-mono-num font-semibold">{monthlyIncome ? formatMoney(monthlyIncome) : 'Not supplied (insufficient-data state)'}</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Regular Expenses</span>
                <span className="font-mono-num font-semibold">{regularExpenses ? formatMoney(regularExpenses) : 'Not supplied'}</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Available Liquid Funds</span>
                <span className="font-mono-num font-semibold">{liquidFunds ? formatMoney(liquidFunds) : 'Not supplied'}</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Reserve Threshold</span>
                <span className="font-mono-num font-semibold">{formatMoney(reserveThreshold || 15000)}</span>
              </div>
            </div>
            <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>
              FinSentinel AI computes assessments strictly from supplied values and displays insufficient-data states when inputs are absent.
            </p>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => submitOnboarding(true)}
              disabled={saving}
              className="px-3 py-2 rounded-md border text-xs font-medium cursor-pointer"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            >
              Skip for now
            </button>
            <button
              type="button"
              onClick={enterDemo}
              disabled={saving}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md border text-xs font-medium cursor-pointer"
              style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--accent)' }}
            >
              <Sparkles className="w-3.5 h-3.5" aria-hidden="true" />
              Enter Demo Workspace
            </button>
          </div>

          <div className="flex gap-2">
            {step > 1 && (
              <button
                type="button"
                onClick={() => setStep((s) => s - 1)}
                className="px-4 py-2 rounded-md border text-sm font-medium cursor-pointer"
                style={{ borderColor: 'var(--border)' }}
              >
                Back
              </button>
            )}
            {step < 3 ? (
              <button
                type="button"
                onClick={() => setStep((s) => s + 1)}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-sm font-semibold cursor-pointer"
                style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
              >
                Continue
                <ArrowRight className="w-4 h-4" aria-hidden="true" />
              </button>
            ) : (
              <button
                type="button"
                onClick={() => submitOnboarding(false)}
                disabled={saving}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-sm font-semibold cursor-pointer"
                style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
              >
                <Check className="w-4 h-4" aria-hidden="true" />
                {saving ? 'Saving...' : 'Open Dashboard'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

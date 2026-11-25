import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Eye, EyeOff, ArrowLeft, ShieldCheck, CheckCircle2, AlertCircle } from 'lucide-react';
import { BrandMark } from '../components/BrandMark';
import { useApp } from '../context/AppContext';
import { apiRequest, HttpApiError } from '../lib/api';

type AuthMode = 'login' | 'register' | 'forgot' | 'reset' | 'verify';

export function AuthPage({ mode }: { mode: AuthMode }) {
  const { refreshUser, notifyToast, t } = useApp();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [token, setToken] = useState(searchParams.get('token') || '');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setLoading(true);
    try {
      if (mode === 'login') {
        const user = await apiRequest<{ onboardingCompleted: boolean }>('/api/v1/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email, password }),
        });
        await refreshUser();
        navigate(user.onboardingCompleted ? '/app/overview' : '/onboarding');
      } else if (mode === 'register') {
        await apiRequest('/api/v1/auth/register', {
          method: 'POST',
          body: JSON.stringify({ fullName, email, password }),
        });
        await refreshUser();
        notifyToast('Account created. Verification email dispatched.');
        navigate('/onboarding');
      } else if (mode === 'forgot') {
        const res = await apiRequest<{ message: string }>('/api/v1/auth/forgot-password', {
          method: 'POST',
          body: JSON.stringify({ email }),
        });
        setSuccessMsg(res.message);
      } else if (mode === 'reset') {
        const res = await apiRequest<{ message: string }>('/api/v1/auth/reset-password', {
          method: 'POST',
          body: JSON.stringify({ token, newPassword: password }),
        });
        setSuccessMsg(res.message);
      } else if (mode === 'verify') {
        const res = await apiRequest<{ message: string }>('/api/v1/auth/verify-email', {
          method: 'POST',
          body: JSON.stringify({ token }),
        });
        await refreshUser();
        setSuccessMsg(res.message);
      }
    } catch (err) {
      if (err instanceof HttpApiError) {
        const fieldDetail = err.fieldErrors ? Object.values(err.fieldErrors).join(' ') : '';
        setErrorMsg(fieldDetail ? `${err.message}: ${fieldDetail}` : err.message);
      } else {
        setErrorMsg('Unable to complete request. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const enterDemo = async () => {
    setLoading(true);
    setErrorMsg(null);
    try {
      await apiRequest('/api/v1/public/demo-session', { method: 'POST' });
      await refreshUser();
      navigate('/app/overview');
    } catch {
      setErrorMsg('Could not open demo workspace.');
    } finally {
      setLoading(false);
    }
  };

  const heading = {
    login: 'Sign in to FinSentinel AI',
    register: 'Create your financial workspace',
    forgot: 'Reset your password',
    reset: 'Choose a new password',
    verify: 'Verify your email address',
  }[mode];

  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-12" style={{ background: 'var(--bg)', color: 'var(--text-primary)' }}>
      {/* Left Brand & Visualization Panel */}
      <div
        className="hidden lg:flex lg:col-span-5 flex-col justify-between p-10 border-r"
        style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}
      >
        <div>
          <Link to="/" className="inline-flex items-center gap-2.5 no-underline" style={{ color: 'var(--text-primary)' }}>
            <BrandMark size={30} />
            <span className="font-semibold text-base">{t('appName')}</span>
          </Link>

          <div className="mt-14 space-y-4">
            <div className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--accent)' }}>
              Predictive Financial Intelligence
            </div>
            <h2 className="text-2xl font-bold leading-snug m-0">
              See cash-flow risk weeks before an obligation strains your reserve.
            </h2>
            <p className="text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              Deterministic BigDecimal cash-flow trajectories, 6-factor explainable vulnerability scoring, and isolated scenario simulation.
            </p>
          </div>

          <div className="mt-8 p-4 rounded-lg border space-y-3" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
            <div className="flex items-center justify-between text-xs">
              <span style={{ color: 'var(--text-secondary)' }}>Policy Engine</span>
              <span className="font-mono-num font-semibold" style={{ color: 'var(--accent)' }}>risk-policy-v1.0</span>
            </div>
            <div className="grid grid-cols-3 gap-2 text-xs">
              <div className="p-2 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                <div style={{ color: 'var(--text-secondary)' }}>Security</div>
                <div className="font-semibold mt-0.5">BCrypt + CSRF</div>
              </div>
              <div className="p-2 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                <div style={{ color: 'var(--text-secondary)' }}>Precision</div>
                <div className="font-semibold mt-0.5">BigDecimal</div>
              </div>
              <div className="p-2 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                <div style={{ color: 'var(--text-secondary)' }}>Data Scope</div>
                <div className="font-semibold mt-0.5">Owner-Isolated</div>
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--text-secondary)' }}>
          <ShieldCheck className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
          <span>{t('educationalNotice')}</span>
        </div>
      </div>

      {/* Right Focused Form Panel */}
      <main className="lg:col-span-7 flex flex-col justify-center px-5 py-10 sm:px-12">
        <div className="max-w-md w-full mx-auto space-y-6">
          <div className="flex items-center justify-between">
            <Link to="/" className="inline-flex items-center gap-1.5 text-xs font-medium no-underline" style={{ color: 'var(--text-secondary)' }}>
              <ArrowLeft className="w-3.5 h-3.5" aria-hidden="true" />
              Back to landing
            </Link>
            <div className="lg:hidden flex items-center gap-2">
              <BrandMark size={22} />
              <span className="font-semibold text-sm">{t('appName')}</span>
            </div>
          </div>

          <div>
            <h1 className="text-2xl font-bold tracking-tight m-0">{heading}</h1>
            <p className="text-sm mt-1 mb-0" style={{ color: 'var(--text-secondary)' }}>
              {mode === 'login' && 'Access your ledger, risk anatomy, and predictive planner.'}
              {mode === 'register' && 'Start with an authentic empty workspace or explore the labeled demo profile.'}
              {mode === 'forgot' && 'Enter your account email to receive a single-use reset link.'}
              {mode === 'reset' && 'Enter the single-use reset token and your new password.'}
              {mode === 'verify' && 'Confirm your verification token to activate verified status.'}
            </p>
          </div>

          {errorMsg && (
            <div role="alert" className="p-3 rounded-md border text-sm flex items-start gap-2" style={{ borderColor: 'var(--critical)', color: 'var(--critical)', background: 'var(--surface)' }}>
              <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && (
            <div role="status" className="p-3 rounded-md border text-sm flex items-start gap-2" style={{ borderColor: 'var(--accent)', color: 'var(--accent)', background: 'var(--surface)' }}>
              <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
              <span>{successMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === 'register' && (
              <div>
                <label htmlFor="auth-name" className="block text-xs font-semibold mb-1.5">Full Name</label>
                <input
                  id="auth-name"
                  name="fullName"
                  type="text"
                  required
                  minLength={2}
                  autoComplete="name"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm"
                  style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
                />
              </div>
            )}

            {(mode === 'login' || mode === 'register' || mode === 'forgot') && (
              <div>
                <label htmlFor="auth-email" className="block text-xs font-semibold mb-1.5">Email Address</label>
                <input
                  id="auth-email"
                  name="email"
                  type="email"
                  inputMode="email"
                  autoComplete="email"
                  autoCorrect="off"
                  autoCapitalize="none"
                  spellCheck={false}
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm"
                  style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
                />
              </div>
            )}

            {(mode === 'reset' || mode === 'verify') && (
              <div>
                <label htmlFor="auth-token" className="block text-xs font-semibold mb-1.5">Single-Use Token</label>
                <input
                  id="auth-token"
                  name="token"
                  type="text"
                  required
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-md border text-sm font-mono-num"
                  style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
                />
              </div>
            )}

            {(mode === 'login' || mode === 'register' || mode === 'reset') && (
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label htmlFor="auth-password" className="text-xs font-semibold">
                    {mode === 'reset' ? 'New Password' : 'Password'}
                  </label>
                  {mode === 'login' && (
                    <Link to="/auth/forgot-password" className="text-xs no-underline" style={{ color: 'var(--accent)' }}>
                      Forgot password?
                    </Link>
                  )}
                </div>
                <div className="relative">
                  <input
                    id="auth-password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    required
                    minLength={mode === 'login' ? 1 : 8}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    aria-describedby={mode !== 'login' ? 'pw-hint' : undefined}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full pl-3 pr-11 py-2.5 rounded-md border text-sm"
                    style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
                  />
                  <button
                    type="button"
                    aria-pressed={showPassword}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded cursor-pointer"
                    style={{ color: 'var(--text-secondary)' }}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" aria-hidden="true" /> : <Eye className="w-4 h-4" aria-hidden="true" />}
                  </button>
                </div>
                {mode !== 'login' && (
                  <p id="pw-hint" className="text-[11px] mt-1 mb-0" style={{ color: 'var(--text-secondary)' }}>
                    Minimum 8 characters with at least one letter and one number.
                  </p>
                )}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-md text-sm font-semibold cursor-pointer disabled:opacity-50"
              style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
            >
              {loading ? 'Processing...' : heading}
            </button>
          </form>

          {(mode === 'login' || mode === 'register') && (
            <div className="space-y-3 pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
              <button
                type="button"
                onClick={enterDemo}
                disabled={loading}
                className="w-full py-2.5 rounded-md border text-sm font-medium cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
              >
                Explore Labeled Demo Workspace (Aarav Krishnan)
              </button>
              <div className="text-center text-xs" style={{ color: 'var(--text-secondary)' }}>
                {mode === 'login' ? (
                  <>
                    New to FinSentinel AI?{' '}
                    <Link to="/auth/register" className="font-semibold no-underline" style={{ color: 'var(--accent)' }}>
                      Create an account
                    </Link>
                  </>
                ) : (
                  <>
                    Already have an account?{' '}
                    <Link to="/auth/login" className="font-semibold no-underline" style={{ color: 'var(--accent)' }}>
                      Sign in
                    </Link>
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

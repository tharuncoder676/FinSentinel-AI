import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import {
  ShieldCheck, Sliders, CalendarClock, Bot, ArrowRight, CheckCircle2, Lock, Sparkles, TrendingUp
} from 'lucide-react';
import { BrandMark } from '../components/BrandMark';
import { useApp } from '../context/AppContext';
import { apiRequest } from '../lib/api';

export function LandingPage() {
  const { t, theme, setTheme, language, setLanguage, formatMoney, notifyToast, refreshUser } = useApp();
  const navigate = useNavigate();

  // Interactive preview state (strictly local fictional demo preview — never mutates any account)
  const [salaryDelayDays, setSalaryDelayDays] = useState(0);
  const [unexpectedExpense, setUnexpectedExpense] = useState(0);
  const [demoLoading, setDemoLoading] = useState(false);

  const previewMinBalance = 68000 - (salaryDelayDays > 0 ? 22000 : 0) - unexpectedExpense;
  const previewRiskScore = Math.min(96, 38 + Math.round(salaryDelayDays * 1.4) + Math.round(unexpectedExpense / 1800));
  const previewRiskCategory = previewRiskScore <= 30 ? 'LOW' : previewRiskScore <= 60 ? 'MODERATE' : previewRiskScore <= 80 ? 'HIGH' : 'CRITICAL';

  const handleEnterDemo = async () => {
    setDemoLoading(true);
    try {
      await apiRequest('/api/v1/public/demo-session', { method: 'POST' });
      await refreshUser();
      notifyToast('Entered labeled Demo Workspace.');
      navigate('/app/overview');
    } catch {
      notifyToast('Start backend to enter interactive demo workspace.');
    } finally {
      setDemoLoading(false);
    }
  };

  return (
    <div className="min-h-screen" style={{ background: 'var(--bg)', color: 'var(--text-primary)' }}>
      <header className="sticky top-0 z-30 border-b backdrop-blur-sm" style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
          <Link to="/" className="flex items-center gap-2.5 no-underline" style={{ color: 'var(--text-primary)' }}>
            <BrandMark size={30} />
            <div>
              <span className="font-semibold text-base tracking-tight block leading-none">{t('appName')}</span>
              <span className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>{t('tagline')}</span>
            </div>
          </Link>

          <nav aria-label="Landing Navigation" className="hidden md:flex items-center gap-6 text-sm font-medium">
            <a href="#predictive" className="no-underline hover:opacity-80" style={{ color: 'var(--text-secondary)' }}>Predictive Engine</a>
            <a href="#stress" className="no-underline hover:opacity-80" style={{ color: 'var(--text-secondary)' }}>Stress Testing</a>
            <a href="#security" className="no-underline hover:opacity-80" style={{ color: 'var(--text-secondary)' }}>Security</a>
            <a href="#faq" className="no-underline hover:opacity-80" style={{ color: 'var(--text-secondary)' }}>FAQ</a>
          </nav>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setLanguage(language === 'en' ? 'ta' : 'en')}
              className="px-2.5 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            >
              {language === 'en' ? 'தமிழ்' : 'English'}
            </button>
            <button
              type="button"
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              className="px-2.5 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
            >
              {theme === 'dark' ? 'Light' : 'Dark'}
            </button>
            <Link
              to="/auth/login"
              className="px-3 py-1.5 rounded-md border text-sm font-medium no-underline"
              style={{ borderColor: 'var(--border)', color: 'var(--text-primary)' }}
            >
              {t('signIn')}
            </Link>
            <Link
              to="/auth/register"
              className="px-3.5 py-1.5 rounded-md text-sm font-semibold no-underline"
              style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
            >
              {t('getStarted')}
            </Link>
          </div>
        </div>
      </header>

      <main>
        {/* Hero + Interactive Fictional Preview */}
        <section className="max-w-7xl mx-auto px-4 sm:px-6 pt-12 pb-16 lg:grid lg:grid-cols-12 lg:gap-10 items-center">
          <div className="lg:col-span-6 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border text-xs font-medium" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
              <TrendingUp className="w-3.5 h-3.5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              <span>Deterministic Cash-Flow Twin • Explainable Risk</span>
            </div>

            <h1 className="text-3xl sm:text-5xl font-bold tracking-tight leading-[1.12] m-0">
              {t('heroHeading')}
            </h1>

            <p className="text-base sm:text-lg leading-relaxed max-w-xl" style={{ color: 'var(--text-secondary)' }}>
              {t('heroSupport')}
            </p>

            <div className="flex flex-wrap gap-3 pt-1">
              <Link
                to="/auth/register"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold no-underline"
                style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
              >
                {t('getStarted')}
                <ArrowRight className="w-4 h-4" aria-hidden="true" />
              </Link>
              <Link
                to="/auth/login"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md border text-sm font-medium no-underline"
                style={{ borderColor: 'var(--border)', background: 'var(--surface)', color: 'var(--text-primary)' }}
              >
                {t('signIn')}
              </Link>
              <button
                type="button"
                onClick={handleEnterDemo}
                disabled={demoLoading}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-md border text-sm font-medium cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
              >
                <Sparkles className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                {demoLoading ? 'Opening...' : t('tryDemo')}
              </button>
            </div>
          </div>

          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.28 }}
            className="lg:col-span-6 mt-10 lg:mt-0 surface-card p-5 space-y-4"
          >
            <div className="flex items-center justify-between border-b pb-3" style={{ borderColor: 'var(--border)' }}>
              <div>
                <div className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--accent)' }}>
                  Interactive Preview (Fictional Sample Data)
                </div>
                <div className="text-sm font-medium">Digital Twin Sensitivity Sandbox</div>
              </div>
              <span className="text-xs px-2 py-0.5 rounded border font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                Read-Only Preview
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3 rounded-md border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Projected Minimum Balance</div>
                <div
                  className="text-xl font-bold font-mono-num mt-1"
                  style={{ color: previewMinBalance < 0 ? 'var(--critical)' : previewMinBalance < 25000 ? 'var(--warning)' : 'var(--accent)' }}
                >
                  {formatMoney(previewMinBalance)}
                </div>
                <div className="text-[11px] mt-1" style={{ color: 'var(--text-secondary)' }}>
                  {previewMinBalance < 0 ? 'Shortfall (< 0) triggered' : previewMinBalance < 25000 ? 'Below 25,000 reserve' : 'Reserve buffer healthy'}
                </div>
              </div>

              <div className="p-3 rounded-md border" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Educational Vulnerability Score</div>
                <div className="text-xl font-bold font-mono-num mt-1">
                  {previewRiskScore} / 100 <span className="text-xs font-semibold">({previewRiskCategory})</span>
                </div>
                <div className="w-full h-2 rounded-full mt-2 overflow-hidden" style={{ background: 'var(--border)' }}>
                  <div
                    className="h-full transition-all duration-200"
                    style={{
                      width: `${previewRiskScore}%`,
                      background: previewRiskScore > 60 ? 'var(--critical)' : previewRiskScore > 30 ? 'var(--warning)' : 'var(--accent)',
                    }}
                  />
                </div>
              </div>
            </div>

            <div className="space-y-3 pt-1">
              <div>
                <div className="flex justify-between text-xs mb-1">
                  <label htmlFor="preview-delay">Simulate Salary Delay</label>
                  <span className="font-mono-num font-medium">{salaryDelayDays} days</span>
                </div>
                <input
                  id="preview-delay"
                  type="range"
                  min={0}
                  max={25}
                  value={salaryDelayDays}
                  onChange={(e) => setSalaryDelayDays(Number(e.target.value))}
                  className="w-full"
                />
              </div>

              <div>
                <div className="flex justify-between text-xs mb-1">
                  <label htmlFor="preview-shock">Unexpected Expense Shock</label>
                  <span className="font-mono-num font-medium">{formatMoney(unexpectedExpense)}</span>
                </div>
                <input
                  id="preview-shock"
                  type="range"
                  min={0}
                  max={60000}
                  step={5000}
                  value={unexpectedExpense}
                  onChange={(e) => setUnexpectedExpense(Number(e.target.value))}
                  className="w-full"
                />
              </div>
            </div>

            {/* Animated Timeline Preview Bar */}
            <div className="pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
              <div className="text-xs font-medium mb-2">Sample 30-Day Event Timeline (Fictional)</div>
              <div className="grid grid-cols-4 gap-2 text-[11px]">
                {[
                  { day: 'Day +4', label: 'Rent', amt: '-22,000', tone: 'var(--text-primary)' },
                  { day: `Day +${6 + salaryDelayDays}`, label: 'Salary', amt: '+92,000', tone: 'var(--accent)' },
                  { day: 'Day +8', label: 'Auto EMI', amt: '-18,500', tone: 'var(--text-primary)' },
                  { day: 'Day +11', label: 'Card Bill', amt: '-10,000', tone: previewMinBalance < 25000 ? 'var(--warning)' : 'var(--text-primary)' },
                ].map((ev) => (
                  <div key={ev.label} className="p-2 rounded border" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
                    <div style={{ color: 'var(--text-secondary)' }}>{ev.day}</div>
                    <div className="font-semibold truncate">{ev.label}</div>
                    <div className="font-mono-num" style={{ color: ev.tone }}>{ev.amt}</div>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        </section>

        {/* Core Capabilities */}
        <section id="predictive" className="border-t py-14" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
          <div className="max-w-7xl mx-auto px-4 sm:px-6">
            <h2 className="text-2xl font-bold tracking-tight mb-8">Built for Deterministic Financial Clarity</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
              {[
                {
                  icon: ShieldCheck,
                  title: 'Explainable Risk Anatomy',
                  desc: 'Six weighted factors (0–100) with explicit normalization formulas, missing-data safeguards, and historical drift.',
                },
                {
                  icon: Sliders,
                  title: 'Financial Digital Twin',
                  desc: 'Test income delays, expense changes, and shock events side-by-side against your baseline without touching live records.',
                },
                {
                  icon: CalendarClock,
                  title: 'Adaptive Payment Planner',
                  desc: 'PriorityQueue scheduling, reserve-safe alternatives, explicit shortfall reporting, and BigDecimal EMI amortization.',
                },
                {
                  icon: Bot,
                  title: 'Grounded AI Explainer',
                  desc: 'Tool-verified explanations citing your exact Java calculations—with transparent deterministic fallback if offline.',
                },
              ].map((item) => {
                const Icon = item.icon;
                return (
                  <div key={item.title} className="p-5 rounded-lg border" style={{ borderColor: 'var(--border)', background: 'var(--bg)' }}>
                    <Icon className="w-5 h-5 mb-3" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                    <h3 className="text-base font-semibold mb-1.5">{item.title}</h3>
                    <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>{item.desc}</p>
                  </div>
                );
              })}
            </div>
          </div>
        </section>

        {/* Stress Testing & Security */}
        <section id="stress" className="max-w-7xl mx-auto px-4 sm:px-6 py-14 grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="surface-card p-6 space-y-3">
            <h2 className="text-xl font-bold m-0">Stress Test Laboratory</h2>
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>
              Evaluate six pre-built liquidity shocks—20% income reduction, 15-day salary delay, unexpected medical expense, essential cost inflation, clustered obligations, or temporary income loss—and pinpoint exact shortage dates and recovery horizons.
            </p>
            <ul className="space-y-2 pt-2 text-sm list-none p-0 m-0">
              {[
                'BigDecimal HALF_UP precision with documented same-day ordering',
                'Separate tracking for cash shortfall (< 0) vs reserve threshold breach',
                'Versioned PDF & CSV exports with formula-injection protection',
              ].map((pt) => (
                <li key={pt} className="flex items-start gap-2">
                  <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                  <span>{pt}</span>
                </li>
              ))}
            </ul>
          </div>

          <div id="security" className="surface-card p-6 space-y-3">
            <div className="flex items-center gap-2">
              <Lock className="w-5 h-5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
              <h2 className="text-xl font-bold m-0">Privacy & Security Architecture</h2>
            </div>
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>
              Designed with strict account isolation, BCrypt password hashing, HTTP-only session cookies, CSRF protection, rate limiting, and server-side AI tool authorization.
            </p>
            <ul className="space-y-2 pt-2 text-sm list-none p-0 m-0">
              {[
                'No bank credential collection or real money transfers',
                'Explicit external AI processing consent toggle in Settings',
                'One-click JSON data export and complete account deletion',
              ].map((pt) => (
                <li key={pt} className="flex items-start gap-2">
                  <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                  <span>{pt}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* FAQs */}
        <section id="faq" className="border-t py-14" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
          <div className="max-w-4xl mx-auto px-4 sm:px-6 space-y-4">
            <h2 className="text-2xl font-bold tracking-tight mb-4">Frequently Asked Questions</h2>
            {[
              {
                q: 'Is the vulnerability score a credit score or loan approval judgment?',
                a: 'No. FinSentinel AI computes an educational 0–100 liquidity and obligation vulnerability indicator using transparent rules. It is not a credit bureau score, loan eligibility judgment, or personalized investment recommendation.',
              },
              {
                q: 'Do Digital Twin or Payment Calendar simulations alter my real records?',
                a: 'Never. All sliders, stress tests, and drag-and-drop payment rescheduling run inside isolated simulation contexts. Your actual account balances and contractual due dates stay untouched unless you explicitly record a manual update.',
              },
              {
                q: 'What happens if no external AI API key is configured?',
                a: 'FinSentinel AI preserves full deterministic capability and transparently displays an unavailable-LLM notice while providing backend-grounded explanations directly from the Java risk and forecasting engines.',
              },
            ].map((item) => (
              <details key={item.q} name="landing-faq" className="p-4 rounded-lg border" style={{ borderColor: 'var(--border)', background: 'var(--bg)' }}>
                <summary className="font-semibold text-sm cursor-pointer">{item.q}</summary>
                <p className="text-sm mt-2 mb-0" style={{ color: 'var(--text-secondary)' }}>{item.a}</p>
              </details>
            ))}
          </div>
        </section>
      </main>

      <footer className="border-t py-8 px-4 sm:px-6 text-xs" style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}>
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <BrandMark size={20} />
            <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>FinSentinel AI</span>
            <span>• {t('educationalNotice')}</span>
          </div>
          <div className="flex gap-4">
            <Link to="/auth/login" className="no-underline" style={{ color: 'var(--text-primary)' }}>Sign In</Link>
            <Link to="/auth/register" className="no-underline" style={{ color: 'var(--text-primary)' }}>Create Account</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

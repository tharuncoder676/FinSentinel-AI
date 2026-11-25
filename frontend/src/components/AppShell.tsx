import React, { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, ArrowLeftRight, Wallet, ShieldAlert, TrendingUp, CalendarCheck2,
  Sliders, Flame, Bot, Clock, Target, FileText, Bell, Settings, HelpCircle, Shield,
  PanelLeftClose, PanelLeftOpen, Search, Menu, X, LogOut, RotateCcw, Plus, Sparkles
} from 'lucide-react';
import { motion } from 'motion/react';
import { useApp } from '../context/AppContext';
import { BrandMark } from './BrandMark';
import { CommandPalette } from './CommandPalette';
import { ContextualAiPanel } from './ContextualAiPanel';
import { apiRequest } from '../lib/api';

interface NavEntry {
  to: string;
  labelKey: string;
  icon: React.ComponentType<{ className?: string }>;
}

const WORKSPACE_NAV: NavEntry[] = [
  { to: '/app/overview', labelKey: 'overview', icon: LayoutDashboard },
  { to: '/app/transactions', labelKey: 'transactions', icon: ArrowLeftRight },
  { to: '/app/income-expenses', labelKey: 'incomeExpenses', icon: Wallet },
  { to: '/app/risk', labelKey: 'financialRisk', icon: ShieldAlert },
  { to: '/app/forecast', labelKey: 'cashFlowForecast', icon: TrendingUp },
  { to: '/app/payments', labelKey: 'paymentPlanner', icon: CalendarCheck2 },
];

const INTELLIGENCE_NAV: NavEntry[] = [
  { to: '/app/digital-twin', labelKey: 'digitalTwin', icon: Sliders },
  { to: '/app/stress-test', labelKey: 'stressTest', icon: Flame },
  { to: '/app/assistant', labelKey: 'aiAssistant', icon: Bot },
  { to: '/app/timeline', labelKey: 'financialTimeline', icon: Clock },
  { to: '/app/goals', labelKey: 'financialGoals', icon: Target },
];

const MANAGEMENT_NAV: NavEntry[] = [
  { to: '/app/reports', labelKey: 'reports', icon: FileText },
  { to: '/app/notifications', labelKey: 'notifications', icon: Bell },
  { to: '/app/settings', labelKey: 'settings', icon: Settings },
  { to: '/app/help', labelKey: 'helpSupport', icon: HelpCircle },
];

export function AppShell() {
  const { user, logout, t, language, setLanguage, theme, setTheme, toastMessage, notifyToast, refreshUser } = useApp();
  const location = useLocation();
  const navigate = useNavigate();

  const [collapsed, setCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [cmdOpen, setCmdOpen] = useState(false);
  const [aiPanelOpen, setAiPanelOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setCmdOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  useEffect(() => {
    setMobileMenuOpen(false);
    const active = [...WORKSPACE_NAV, ...INTELLIGENCE_NAV, ...MANAGEMENT_NAV].find((n) => location.pathname.startsWith(n.to));
    if (active) {
      document.title = `${t(active.labelKey)} | FinSentinel AI`;
    } else if (location.pathname.startsWith('/app/admin')) {
      document.title = `${t('adminOps')} | FinSentinel AI`;
    }
  }, [location.pathname, t]);

  useEffect(() => {
    apiRequest<Array<{ read: boolean }>>('/api/v1/notifications')
      .then((items) => setUnreadCount(items.filter((i) => !i.read).length))
      .catch(() => {});
  }, [location.pathname]);

  const handleResetDemo = async () => {
    try {
      await apiRequest('/api/v1/demo/reset', { method: 'POST' });
      notifyToast('Demo dataset reset to clean baseline.');
      await refreshUser();
      window.location.reload();
    } catch (e) {
      notifyToast('Unable to reset demo dataset.');
    }
  };

  const renderGroup = (title: string, entries: NavEntry[]) => (
    <div className="mb-4">
      {!collapsed && (
        <div className="px-3 mb-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: 'var(--text-secondary)' }}>
          {title}
        </div>
      )}
      <ul className="space-y-0.5 list-none m-0 p-0">
        {entries.map((entry) => {
          const Icon = entry.icon;
          return (
            <li key={entry.to}>
              <NavLink
                to={entry.to}
                title={collapsed ? t(entry.labelKey) : undefined}
                className={({ isActive }) =>
                  `flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-colors no-underline ${
                    isActive ? 'font-semibold' : 'hover:opacity-85'
                  }`
                }
                style={({ isActive }) => ({
                  backgroundColor: isActive ? 'var(--elevated)' : 'transparent',
                  color: isActive ? 'var(--accent)' : 'var(--text-primary)',
                  borderLeft: isActive ? '3px solid var(--accent)' : '3px solid transparent',
                })}
              >
                <Icon className="w-4 h-4 shrink-0" aria-hidden="true" />
                {!collapsed && <span className="truncate">{t(entry.labelKey)}</span>}
              </NavLink>
            </li>
          );
        })}
      </ul>
    </div>
  );

  return (
    <div className="min-h-screen flex flex-col" style={{ background: 'var(--bg)', color: 'var(--text-primary)' }}>
      <a href="#main-content" className="skip-link visually-hidden">Skip to main content</a>

      {user?.demoUser && (
        <div
          className="px-4 py-1.5 text-xs flex flex-wrap items-center justify-between gap-2 border-b"
          style={{ background: 'var(--elevated)', borderColor: 'var(--border)', color: 'var(--text-primary)' }}
        >
          <span>
            <strong style={{ color: 'var(--accent)' }}>DEMO</strong> — {t('demoBanner')}
          </span>
          <button
            type="button"
            onClick={handleResetDemo}
            className="inline-flex items-center gap-1 px-2.5 py-1 rounded border text-xs font-medium cursor-pointer"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            <RotateCcw className="w-3 h-3" aria-hidden="true" />
            {t('resetDemo')}
          </button>
        </div>
      )}

      <div className="flex flex-1 min-w-0">
        {/* Desktop Sidebar */}
        <aside
          aria-label="Primary Sidebar"
          className={`hidden lg:flex flex-col border-r shrink-0 transition-all duration-200 ${collapsed ? 'w-16' : 'w-64'}`}
          style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}
        >
          <div className="h-14 px-3 flex items-center justify-between border-b" style={{ borderColor: 'var(--border)' }}>
            <NavLink to="/app/overview" className="flex items-center gap-2 no-underline" style={{ color: 'var(--text-primary)' }}>
              <BrandMark size={26} />
              {!collapsed && <span className="font-semibold text-sm tracking-tight">{t('appName')}</span>}
            </NavLink>
            <button
              type="button"
              onClick={() => setCollapsed((c) => !c)}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              className="p-1.5 rounded-md hover:opacity-80 cursor-pointer"
              style={{ color: 'var(--text-secondary)' }}
            >
              {collapsed ? <PanelLeftOpen className="w-4 h-4" aria-hidden="true" /> : <PanelLeftClose className="w-4 h-4" aria-hidden="true" />}
            </button>
          </div>

          <nav aria-label="Primary" className="flex-1 overflow-y-auto py-3 px-2">
            {renderGroup(t('navWorkspace'), WORKSPACE_NAV)}
            {renderGroup(t('navIntelligence'), INTELLIGENCE_NAV)}
            {renderGroup(t('navManagement'), MANAGEMENT_NAV)}
            {user?.role === 'ADMIN' && renderGroup('Administration', [{ to: '/app/admin', labelKey: 'adminOps', icon: Shield }])}
          </nav>

          <div className="p-3 border-t text-xs" style={{ borderColor: 'var(--border)' }}>
            {!collapsed && (
              <div className="mb-2 truncate" style={{ color: 'var(--text-secondary)' }}>
                {user?.fullName}
              </div>
            )}
            <button
              type="button"
              onClick={async () => {
                await logout();
                navigate('/auth/login');
              }}
              className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
              style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
            >
              <LogOut className="w-3.5 h-3.5" aria-hidden="true" />
              {!collapsed && <span>{t('signOut')}</span>}
            </button>
          </div>
        </aside>

        {/* Mobile Drawer */}
        {mobileMenuOpen && (
          <div className="fixed inset-0 z-50 lg:hidden flex">
            <div className="fixed inset-0 bg-black/50" onClick={() => setMobileMenuOpen(false)} />
            <nav
              aria-label="Mobile Navigation"
              className="relative w-72 max-w-[85vw] h-full overflow-y-auto p-3 border-r flex flex-col"
              style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}
            >
              <div className="flex items-center justify-between pb-3 mb-3 border-b" style={{ borderColor: 'var(--border)' }}>
                <div className="flex items-center gap-2">
                  <BrandMark size={24} />
                  <span className="font-semibold text-sm">{t('appName')}</span>
                </div>
                <button
                  type="button"
                  onClick={() => setMobileMenuOpen(false)}
                  aria-label="Close navigation menu"
                  className="p-2 rounded-md border cursor-pointer"
                  style={{ borderColor: 'var(--border)' }}
                >
                  <X className="w-4 h-4" aria-hidden="true" />
                </button>
              </div>
              {renderGroup(t('navWorkspace'), WORKSPACE_NAV)}
              {renderGroup(t('navIntelligence'), INTELLIGENCE_NAV)}
              {renderGroup(t('navManagement'), MANAGEMENT_NAV)}
              {user?.role === 'ADMIN' && renderGroup('Administration', [{ to: '/app/admin', labelKey: 'adminOps', icon: Shield }])}
            </nav>
          </div>
        )}

        {/* Main Column */}
        <div className="flex-1 flex flex-col min-w-0">
          <header
            className="h-14 px-3 sm:px-6 border-b flex items-center justify-between gap-2 shrink-0"
            style={{ background: 'var(--surface)', borderColor: 'var(--border)' }}
          >
            <div className="flex items-center gap-2 min-w-0">
              <button
                type="button"
                onClick={() => setMobileMenuOpen(true)}
                aria-label="Open navigation menu"
                className="lg:hidden p-2 rounded-md border cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              >
                <Menu className="w-4 h-4" aria-hidden="true" />
              </button>
              <button
                type="button"
                onClick={() => setCmdOpen(true)}
                className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-md border text-xs cursor-pointer min-w-[200px]"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-secondary)' }}
              >
                <Search className="w-3.5 h-3.5" aria-hidden="true" />
                <span className="truncate">{t('commandPaletteHint')}</span>
              </button>
            </div>

            <div className="flex items-center gap-1.5 sm:gap-2">
              <button
                type="button"
                onClick={() => navigate('/app/transactions?new=1')}
                className="inline-flex items-center gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-md text-xs font-semibold cursor-pointer"
                style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
              >
                <Plus className="w-3.5 h-3.5" aria-hidden="true" />
                <span className="hidden sm:inline">{t('addTransaction')}</span>
              </button>

              <button
                type="button"
                onClick={() => setAiPanelOpen((v) => !v)}
                aria-expanded={aiPanelOpen}
                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
              >
                <Sparkles className="w-3.5 h-3.5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                <span className="hidden md:inline">Ask AI</span>
              </button>

              <button
                type="button"
                onClick={() => navigate('/app/notifications')}
                aria-label={`Notifications (${unreadCount} unread)`}
                className="relative p-2 rounded-md border cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
              >
                <Bell className="w-4 h-4" aria-hidden="true" />
                {unreadCount > 0 && (
                  <span
                    className="absolute -top-1 -right-1 text-[10px] font-bold px-1 rounded-full"
                    style={{ background: 'var(--critical)', color: '#fff' }}
                  >
                    {unreadCount}
                  </span>
                )}
              </button>

              <button
                type="button"
                onClick={() => setLanguage(language === 'en' ? 'ta' : 'en')}
                className="px-2 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                aria-label="Switch language"
              >
                {language === 'en' ? 'EN' : 'தமிழ்'}
              </button>

              <button
                type="button"
                onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
                className="px-2 py-1.5 rounded-md border text-xs font-medium cursor-pointer"
                style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}
                aria-label="Toggle theme"
              >
                {theme === 'dark' ? 'Dark' : theme === 'light' ? 'Light' : 'Auto'}
              </button>
            </div>
          </header>

          <main id="main-content" className="flex-1 p-3 sm:p-6 max-w-[1440px] w-full mx-auto min-w-0">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.16 }}
            >
              <Outlet />
            </motion.div>
          </main>

          <footer className="px-6 py-3 border-t text-[11px] flex flex-wrap justify-between gap-2" style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}>
            <span>{t('educationalNotice')}</span>
            <span>FinSentinel AI • See the risk. Plan the future.</span>
          </footer>
        </div>
      </div>

      <CommandPalette open={cmdOpen} onClose={() => setCmdOpen(false)} />
      <ContextualAiPanel open={aiPanelOpen} onClose={() => setAiPanelOpen(false)} />

      {toastMessage && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-4 right-4 z-50 px-4 py-2.5 rounded-md border text-sm font-medium"
          style={{ background: 'var(--surface)', borderColor: 'var(--accent)', boxShadow: 'var(--shadow-md)' }}
        >
          {toastMessage}
        </div>
      )}
    </div>
  );
}

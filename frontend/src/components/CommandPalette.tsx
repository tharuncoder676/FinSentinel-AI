import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, ArrowRight } from 'lucide-react';
import { ModalDialog } from './ModalDialog';
import { useApp } from '../context/AppContext';

export interface NavRouteItem {
  path: string;
  labelKey: string;
  group: string;
}

export const APP_ROUTES: NavRouteItem[] = [
  { path: '/app/overview', labelKey: 'overview', group: 'Workspace' },
  { path: '/app/transactions', labelKey: 'transactions', group: 'Workspace' },
  { path: '/app/income-expenses', labelKey: 'incomeExpenses', group: 'Workspace' },
  { path: '/app/risk', labelKey: 'financialRisk', group: 'Workspace' },
  { path: '/app/forecast', labelKey: 'cashFlowForecast', group: 'Workspace' },
  { path: '/app/payments', labelKey: 'paymentPlanner', group: 'Workspace' },
  { path: '/app/digital-twin', labelKey: 'digitalTwin', group: 'Intelligence' },
  { path: '/app/stress-test', labelKey: 'stressTest', group: 'Intelligence' },
  { path: '/app/assistant', labelKey: 'aiAssistant', group: 'Intelligence' },
  { path: '/app/timeline', labelKey: 'financialTimeline', group: 'Intelligence' },
  { path: '/app/goals', labelKey: 'financialGoals', group: 'Intelligence' },
  { path: '/app/reports', labelKey: 'reports', group: 'Management' },
  { path: '/app/notifications', labelKey: 'notifications', group: 'Management' },
  { path: '/app/settings', labelKey: 'settings', group: 'Management' },
  { path: '/app/help', labelKey: 'helpSupport', group: 'Management' },
];

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, user } = useApp();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');

  useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  const items = useMemo(() => {
    const all = user?.role === 'ADMIN'
      ? [...APP_ROUTES, { path: '/app/admin', labelKey: 'adminOps', group: 'Admin' }]
      : APP_ROUTES;
    const q = query.trim().toLowerCase();
    if (!q) return all;
    return all.filter((r) => t(r.labelKey).toLowerCase().includes(q) || r.path.toLowerCase().includes(q));
  }, [query, t, user]);

  return (
    <ModalDialog open={open} onClose={onClose} title="Command Palette">
      <div className="space-y-3">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--text-secondary)' }} aria-hidden="true" />
          <label htmlFor="cmd-search" className="visually-hidden">Search pages</label>
          <input
            id="cmd-search"
            type="search"
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('commandPaletteHint')}
            className="w-full pl-9 pr-3 py-2.5 text-sm rounded-md border"
            style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
          />
        </div>
        <ul className="max-h-72 overflow-y-auto divide-y" style={{ borderColor: 'var(--border)' }}>
          {items.map((item) => (
            <li key={item.path}>
              <button
                type="button"
                onClick={() => {
                  navigate(item.path);
                  onClose();
                }}
                className="w-full flex items-center justify-between px-3 py-2.5 text-left text-sm rounded-md hover:opacity-85 cursor-pointer"
                style={{ color: 'var(--text-primary)' }}
              >
                <div>
                  <span className="font-medium">{t(item.labelKey)}</span>
                  <span className="ml-2 text-xs" style={{ color: 'var(--text-secondary)' }}>{item.group}</span>
                </div>
                <ArrowRight className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} aria-hidden="true" />
              </button>
            </li>
          ))}
          {items.length === 0 && (
            <li className="py-6 text-center text-sm" style={{ color: 'var(--text-secondary)' }}>
              No matching routes found.
            </li>
          )}
        </ul>
      </div>
    </ModalDialog>
  );
}

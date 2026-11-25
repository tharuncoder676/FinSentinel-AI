import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck, RefreshCw } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface NotificationItem {
  id: string;
  notifType: string;
  severity: string;
  title: string;
  body: string;
  read: boolean;
  deliveryChannel: string;
  deliveryStatus: string;
  createdAt: string;
}

interface NotifPrefs {
  inAppEnabled: boolean;
  emailEnabled: boolean;
  reminderDaysBefore: number;
  shortfallAlertsEnabled: boolean;
}

export function NotificationsPage() {
  const { notifyToast } = useApp();
  const qc = useQueryClient();

  const { data: items = [] } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => apiRequest<NotificationItem[]>('/api/v1/notifications'),
  });

  const { data: prefs } = useQuery({
    queryKey: ['notification-prefs'],
    queryFn: () => apiRequest<NotifPrefs>('/api/v1/notifications/preferences'),
  });

  const [inApp, setInApp] = useState(true);
  const [email, setEmail] = useState(false);
  const [daysBefore, setDaysBefore] = useState(3);
  const [shortfallAlerts, setShortfallAlerts] = useState(true);

  useEffect(() => {
    if (prefs) {
      setInApp(prefs.inAppEnabled);
      setEmail(prefs.emailEnabled);
      setDaysBefore(prefs.reminderDaysBefore);
      setShortfallAlerts(prefs.shortfallAlertsEnabled);
    }
  }, [prefs]);

  const evalMut = useMutation({
    mutationFn: () => apiRequest<{ created: number }>('/api/v1/notifications/evaluate', { method: 'POST' }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
      notifyToast(`Evaluated reminders (${res.created} new notifications).`);
    },
  });

  const markReadMut = useMutation({
    mutationFn: (id: string) => apiRequest(`/api/v1/notifications/${id}/read`, { method: 'PATCH', body: JSON.stringify({ read: true }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const markAllMut = useMutation({
    mutationFn: () => apiRequest('/api/v1/notifications/read-all', { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const savePrefsMut = useMutation({
    mutationFn: () =>
      apiRequest('/api/v1/notifications/preferences', {
        method: 'PUT',
        body: JSON.stringify({ inAppEnabled: inApp, emailEnabled: email, reminderDaysBefore: daysBefore, shortfallAlertsEnabled: shortfallAlerts }),
      }),
    onSuccess: () => notifyToast('Notification preferences updated.'),
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Notification Center & Reminder Preferences</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Idempotent, timezone-aware reminder evaluation with deduplication and retry bounds.
          </p>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => evalMut.mutate()} className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md border text-xs font-medium cursor-pointer" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
            <RefreshCw className="w-3.5 h-3.5" aria-hidden="true" />
            Check Reminders Now
          </button>
          <button type="button" onClick={() => markAllMut.mutate()} className="inline-flex items-center gap-1.5 px-3 py-2 rounded-md text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
            <CheckCheck className="w-3.5 h-3.5" aria-hidden="true" />
            Mark All Read
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <section className="lg:col-span-8 surface-card p-5 space-y-3">
          <h2 className="text-base font-semibold m-0">Alerts & Reminders</h2>
          {items.length === 0 ? (
            <p className="text-sm m-0" style={{ color: 'var(--text-secondary)' }}>No notifications yet.</p>
          ) : (
            <ul className="space-y-2.5 list-none p-0 m-0">
              {items.map((n) => (
                <li
                  key={n.id}
                  className="p-3.5 rounded-md border flex items-start justify-between gap-3"
                  style={{
                    borderColor: n.read ? 'var(--border)' : 'var(--accent)',
                    background: n.read ? 'var(--surface)' : 'var(--elevated)',
                  }}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 text-xs">
                      <Bell className="w-3.5 h-3.5" style={{ color: n.severity === 'CRITICAL' ? 'var(--critical)' : 'var(--accent)' }} aria-hidden="true" />
                      <strong>{n.title}</strong>
                      {!n.read && <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>NEW</span>}
                    </div>
                    <p className="text-xs m-0" style={{ color: 'var(--text-secondary)' }}>{n.body}</p>
                    <div className="text-[11px] font-mono-num" style={{ color: 'var(--text-secondary)' }}>
                      {new Date(n.createdAt).toLocaleString()} • {n.deliveryChannel} ({n.deliveryStatus})
                    </div>
                  </div>
                  {!n.read && (
                    <button
                      type="button"
                      onClick={() => markReadMut.mutate(n.id)}
                      className="px-2.5 py-1 rounded border text-xs shrink-0 cursor-pointer"
                      style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
                    >
                      Mark Read
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="lg:col-span-4 surface-card p-5 space-y-4">
          <h2 className="text-base font-semibold m-0">Preferences</h2>
          <label className="flex items-center justify-between text-sm cursor-pointer">
            <span>In-app notifications</span>
            <input type="checkbox" checked={inApp} onChange={(e) => setInApp(e.target.checked)} />
          </label>
          <label className="flex items-center justify-between text-sm cursor-pointer">
            <span>Email alerts (non-sensitive summary)</span>
            <input type="checkbox" checked={email} onChange={(e) => setEmail(e.target.checked)} />
          </label>
          <label className="flex items-center justify-between text-sm cursor-pointer">
            <span>Shortfall early warnings</span>
            <input type="checkbox" checked={shortfallAlerts} onChange={(e) => setShortfallAlerts(e.target.checked)} />
          </label>
          <div>
            <label htmlFor="pref-days" className="block text-xs font-semibold mb-1">Lead Time (days before due date)</label>
            <input id="pref-days" type="number" min="1" max="30" value={daysBefore} onChange={(e) => setDaysBefore(Number(e.target.value))} className="w-full px-3 py-2 rounded border text-sm font-mono-num" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <button
            type="button"
            onClick={() => savePrefsMut.mutate()}
            className="w-full py-2 rounded-md text-xs font-semibold cursor-pointer"
            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
          >
            Save Preferences
          </button>
        </section>
      </div>
    </div>
  );
}

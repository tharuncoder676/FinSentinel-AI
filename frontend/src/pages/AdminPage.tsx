import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Shield, Save } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface AdminOverview {
  totalUsers: number;
  activeUsers: number;
  aiRequestsSinceBoot: number;
  aiProviderErrorsSinceBoot: number;
  totalAssistantMessagesStored: number;
  rateLimitEventsSinceBoot: number;
  failedLoginsLast24h: number;
  notificationsDelivered: number;
  privacyGuarantee: string;
  recentAuditLogs: Array<{
    action: string;
    actorRole: string;
    resourceType: string;
    outcome: string;
    requestId: string;
    createdAt: string;
  }>;
  configurations: Array<{
    configKey: string;
    configValue: string;
    description: string;
  }>;
}

export function AdminPage() {
  const { notifyToast } = useApp();
  const qc = useQueryClient();
  const [editingValues, setEditingValues] = useState<Record<string, string>>({});

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-overview'],
    queryFn: () => apiRequest<AdminOverview>('/api/v1/admin/overview'),
  });

  const saveCfgMut = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      apiRequest(`/api/v1/admin/config/${key}`, { method: 'PUT', body: JSON.stringify({ value }) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-overview'] });
      notifyToast('Configuration updated.');
    },
  });

  if (isLoading) return <div className="h-64 skeleton-pulse" />;
  if (isError || !data) {
    return (
      <div role="alert" className="surface-card p-6 text-sm" style={{ color: 'var(--critical)' }}>
        Administrator role required to view operational telemetry.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <Shield className="w-5 h-5" style={{ color: 'var(--accent)' }} aria-hidden="true" />
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Operations & Administration</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>{data.privacyGuarantee}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { label: 'Total / Active Users', val: `${data.totalUsers} / ${data.activeUsers}` },
          { label: 'AI Requests / Errors', val: `${data.aiRequestsSinceBoot} / ${data.aiProviderErrorsSinceBoot}` },
          { label: 'Rate Limit Events', val: String(data.rateLimitEventsSinceBoot) },
          { label: 'Notifications Delivered', val: String(data.notificationsDelivered) },
        ].map((m) => (
          <div key={m.label} className="surface-card p-4">
            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>{m.label}</div>
            <div className="text-xl font-bold font-mono-num mt-1">{m.val}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="surface-card p-5 space-y-3">
          <h2 className="text-base font-semibold m-0">System Configuration</h2>
          <div className="space-y-3">
            {data.configurations.map((c) => {
              const val = editingValues[c.configKey] ?? c.configValue;
              return (
                <div key={c.configKey} className="p-3 rounded border space-y-1.5" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
                  <div className="text-xs font-mono-num font-semibold">{c.configKey}</div>
                  <div className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>{c.description}</div>
                  <div className="flex gap-2">
                    <input
                      aria-label={c.configKey}
                      value={val}
                      onChange={(e) => setEditingValues((p) => ({ ...p, [c.configKey]: e.target.value }))}
                      className="flex-1 px-2.5 py-1.5 rounded border text-xs font-mono-num"
                      style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
                    />
                    <button
                      type="button"
                      onClick={() => saveCfgMut.mutate({ key: c.configKey, value: val })}
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded text-xs font-semibold cursor-pointer"
                      style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
                    >
                      <Save className="w-3 h-3" aria-hidden="true" />
                      Save
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <section className="surface-card p-5 space-y-3">
          <h2 className="text-base font-semibold m-0">Redacted Operational Audit Log</h2>
          <div className="table-wrapper max-h-80" tabIndex={0} role="region" aria-label="Audit logs table">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr style={{ background: 'var(--elevated)' }}>
                  <th scope="col" className="p-2 text-left">Action</th>
                  <th scope="col" className="p-2 text-left">Role</th>
                  <th scope="col" className="p-2 text-left">Outcome</th>
                  <th scope="col" className="p-2 text-left">Time</th>
                </tr>
              </thead>
              <tbody>
                {data.recentAuditLogs.map((l, idx) => (
                  <tr key={idx} className="border-t" style={{ borderColor: 'var(--border)' }}>
                    <td className="p-2 font-mono-num">{l.action}</td>
                    <td className="p-2">{l.actorRole}</td>
                    <td className="p-2">{l.outcome}</td>
                    <td className="p-2 font-mono-num">{new Date(l.createdAt).toLocaleTimeString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}

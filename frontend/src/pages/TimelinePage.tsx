import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Clock, ZoomIn, ZoomOut } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface TimelineEvent {
  id: string;
  date: string;
  eventType: string;
  title: string;
  signedAmount: number;
  actual: boolean;
  hypothetical: boolean;
  reserveBreach: boolean;
  shortfall: boolean;
  projectedBalanceAfter: number | null;
  categoryOrStatus: string;
}

interface TimelineData {
  from: string;
  to: string;
  historicalEvents: TimelineEvent[];
  projectedEvents: TimelineEvent[];
}

export function TimelinePage() {
  const { formatMoney } = useApp();
  const [mode, setMode] = useState<'all' | 'historical' | 'forecast'>('all');
  const [forecastDays, setForecastDays] = useState(60);
  const [selected, setSelected] = useState<TimelineEvent | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['timeline', forecastDays],
    queryFn: () => apiRequest<TimelineData>(`/api/v1/timeline?historyDays=30&forecastDays=${forecastDays}`),
  });

  if (isLoading || !data) return <div className="h-64 skeleton-pulse" />;

  const items = [
    ...(mode === 'forecast' ? [] : data.historicalEvents),
    ...(mode === 'historical' ? [] : data.projectedEvents),
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Advanced Financial Timeline</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Window {data.from} to {data.to} • Clear distinction between observed historical ledger entries and projected future events.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex rounded-md border p-0.5" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
            {(['all', 'historical', 'forecast'] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMode(m)}
                aria-pressed={mode === m}
                className="px-2.5 py-1 rounded text-xs font-medium capitalize cursor-pointer"
                style={{
                  background: mode === m ? 'var(--accent)' : 'transparent',
                  color: mode === m ? 'var(--accent-text)' : 'var(--text-primary)',
                }}
              >
                {m}
              </button>
            ))}
          </div>

          <div className="inline-flex items-center gap-1 border rounded-md px-2 py-1" style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}>
            <button type="button" aria-label="Zoom in horizon" onClick={() => setForecastDays((d) => Math.max(14, d - 30))} className="p-1 cursor-pointer">
              <ZoomIn className="w-3.5 h-3.5" aria-hidden="true" />
            </button>
            <span className="text-xs font-mono-num">{forecastDays}d</span>
            <button type="button" aria-label="Zoom out horizon" onClick={() => setForecastDays((d) => Math.min(180, d + 30))} className="p-1 cursor-pointer">
              <ZoomOut className="w-3.5 h-3.5" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      {/* Horizontal Scrollable Desktop / Vertical Mobile Timeline */}
      <div className="surface-card p-5">
        <div className="flex flex-col md:flex-row md:overflow-x-auto gap-3 pb-2" tabIndex={0} role="region" aria-label="Interactive financial event timeline">
          {items.map((ev) => (
            <button
              key={ev.id}
              type="button"
              onClick={() => setSelected(ev)}
              className="md:w-52 md:shrink-0 p-3 rounded-md border text-left space-y-1.5 cursor-pointer hover:opacity-90"
              style={{
                borderColor: ev.shortfall ? 'var(--critical)' : ev.reserveBreach ? 'var(--warning)' : selected?.id === ev.id ? 'var(--accent)' : 'var(--border)',
                borderStyle: ev.actual ? 'solid' : 'dashed',
                background: 'var(--elevated)',
              }}
            >
              <div className="flex items-center justify-between text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                <span className="font-mono-num">{ev.date}</span>
                <span>{ev.actual ? 'Observed' : 'Projected'}</span>
              </div>
              <div className="text-xs font-semibold truncate">{ev.title}</div>
              <div className="text-sm font-bold font-mono-num" style={{ color: ev.signedAmount >= 0 ? 'var(--accent)' : 'var(--text-primary)' }}>
                {ev.signedAmount !== 0 ? formatMoney(ev.signedAmount, true) : ev.eventType}
              </div>
              {ev.projectedBalanceAfter !== null && (
                <div className="text-[11px] font-mono-num" style={{ color: 'var(--text-secondary)' }}>
                  Bal: {formatMoney(ev.projectedBalanceAfter)}
                </div>
              )}
            </button>
          ))}
          {items.length === 0 && (
            <div className="py-8 text-sm text-center w-full" style={{ color: 'var(--text-secondary)' }}>
              No timeline events in the selected window.
            </div>
          )}
        </div>
      </div>

      {selected && (
        <div className="surface-card p-5 space-y-2" aria-live="polite">
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4" style={{ color: 'var(--accent)' }} aria-hidden="true" />
            <h2 className="text-base font-semibold m-0">{selected.title}</h2>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs pt-1">
            <div><span style={{ color: 'var(--text-secondary)' }}>Date:</span> <strong className="font-mono-num">{selected.date}</strong></div>
            <div><span style={{ color: 'var(--text-secondary)' }}>Classification:</span> <strong>{selected.actual ? 'Observed Transaction' : 'Projected Event'}</strong></div>
            <div><span style={{ color: 'var(--text-secondary)' }}>Amount:</span> <strong className="font-mono-num">{formatMoney(selected.signedAmount, true)}</strong></div>
            <div><span style={{ color: 'var(--text-secondary)' }}>Status:</span> <strong>{selected.categoryOrStatus}</strong></div>
          </div>
        </div>
      )}
    </div>
  );
}

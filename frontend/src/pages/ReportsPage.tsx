import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileDown, FileText, Plus } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { useApp } from '../context/AppContext';

interface ReportItem {
  id: string;
  reportType: string;
  title: string;
  fileName: string;
  status: string;
  policyVersion: string;
  createdAt: string;
}

export function ReportsPage() {
  const { notifyToast } = useApp();
  const qc = useQueryClient();
  const [reportType, setReportType] = useState('COMPREHENSIVE');

  const { data: reports = [] } = useQuery({
    queryKey: ['reports'],
    queryFn: () => apiRequest<ReportItem[]>('/api/v1/reports'),
  });

  const genMut = useMutation({
    mutationFn: () =>
      apiRequest<ReportItem>('/api/v1/reports/generate', {
        method: 'POST',
        body: JSON.stringify({ reportType, horizonDays: 90 }),
      }),
    onSuccess: (rep) => {
      qc.invalidateQueries({ queryKey: ['reports'] });
      notifyToast(`Generated ${rep.fileName}.`);
    },
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">Financial & Risk PDF Reports</h1>
          <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Server-generated OpenPDF exports with generation timestamps, policy versions, and observed vs projected distinctions.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <select
            aria-label="Report type"
            value={reportType}
            onChange={(e) => setReportType(e.target.value)}
            className="px-3 py-2 rounded-md border text-xs"
            style={{ borderColor: 'var(--border)', background: 'var(--surface)' }}
          >
            <option value="COMPREHENSIVE">Comprehensive Summary</option>
            <option value="RISK">Explainable Risk Report</option>
            <option value="PAYMENT">Adaptive Payment Schedule</option>
          </select>
          <button
            type="button"
            disabled={genMut.isPending}
            onClick={() => genMut.mutate()}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-md text-xs font-semibold cursor-pointer"
            style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}
          >
            <Plus className="w-3.5 h-3.5" aria-hidden="true" />
            {genMut.isPending ? 'Generating...' : 'Generate PDF Report'}
          </button>
        </div>
      </div>

      <div className="surface-card overflow-hidden">
        <div className="table-wrapper" tabIndex={0} role="region" aria-label="Generated reports table">
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--elevated)' }}>
                <th scope="col" className="p-3 text-left text-xs">Report Title</th>
                <th scope="col" className="p-3 text-left text-xs">Type</th>
                <th scope="col" className="p-3 text-left text-xs">Policy / Model</th>
                <th scope="col" className="p-3 text-left text-xs">Generated At</th>
                <th scope="col" className="p-3 text-right text-xs">Download</th>
              </tr>
            </thead>
            <tbody>
              {reports.map((r) => (
                <tr key={r.id} className="border-t" style={{ borderColor: 'var(--border)' }}>
                  <td className="p-3 font-medium flex items-center gap-2">
                    <FileText className="w-4 h-4 shrink-0" style={{ color: 'var(--accent)' }} aria-hidden="true" />
                    <span>{r.title}</span>
                  </td>
                  <td className="p-3 text-xs">{r.reportType}</td>
                  <td className="p-3 text-xs font-mono-num">{r.policyVersion}</td>
                  <td className="p-3 text-xs font-mono-num">{new Date(r.createdAt).toLocaleString()}</td>
                  <td className="p-3 text-right">
                    <a
                      href={`/api/v1/reports/${r.id}/download`}
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-semibold no-underline"
                      style={{ borderColor: 'var(--border)', background: 'var(--elevated)', color: 'var(--text-primary)' }}
                    >
                      <FileDown className="w-3.5 h-3.5" aria-hidden="true" />
                      Download PDF
                    </a>
                  </td>
                </tr>
              ))}
              {reports.length === 0 && (
                <tr><td colSpan={5} className="p-8 text-center text-sm" style={{ color: 'var(--text-secondary)' }}>No generated PDF reports yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

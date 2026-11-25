import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Download, Trash2, MailCheck } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { apiRequest, HttpApiError } from '../lib/api';
import { ModalDialog } from '../components/ModalDialog';

export function SettingsPage() {
  const { user, theme, setTheme, language, setLanguage, reducedMotion, setReducedMotion, refreshUser, logout, notifyToast, t } = useApp();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [currency, setCurrency] = useState(user?.preferences?.currency || 'INR');
  const [aiConsent, setAiConsent] = useState(Boolean(user?.preferences?.aiExternalConsent));
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    await apiRequest('/api/v1/auth/profile', {
      method: 'PATCH',
      body: JSON.stringify({ fullName }),
    });
    await apiRequest('/api/v1/auth/preferences', {
      method: 'PATCH',
      body: JSON.stringify({
        currency,
        locale: language === 'ta' ? 'ta-IN' : currency === 'INR' ? 'en-IN' : 'en-US',
        aiExternalConsent: aiConsent,
      }),
    });
    await refreshUser();
    notifyToast('Profile & preferences saved.');
  };

  const exportAccountJson = async () => {
    const data = await apiRequest<Record<string, unknown>>('/api/v1/auth/export');
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'finsentinel-account-export.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleDeleteAccount = async () => {
    try {
      await apiRequest('/api/v1/auth/account', { method: 'DELETE' });
      await logout();
      navigate('/');
    } catch (err) {
      notifyToast(err instanceof HttpApiError ? err.message : 'Unable to delete account.');
      setDeleteDialogOpen(false);
    }
  };

  const resendVerification = async () => {
    await apiRequest('/api/v1/auth/resend-verification', { method: 'POST' });
    notifyToast('Verification link dispatched.');
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight m-0">{t('settings')}</h1>
        <p className="text-xs m-0 mt-0.5" style={{ color: 'var(--text-secondary)' }}>
          Manage appearance, Tamil/English language, currency formatting, AI consent, and account privacy controls.
        </p>
      </div>

      <form onSubmit={saveProfile} className="surface-card p-5 space-y-4">
        <h2 className="text-base font-semibold m-0">Profile & Localization</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label htmlFor="set-name" className="block text-xs font-semibold mb-1">Full Name</label>
            <input id="set-name" required value={fullName} onChange={(e) => setFullName(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
          </div>
          <div>
            <label htmlFor="set-email" className="block text-xs font-semibold mb-1">Email Status</label>
            <div className="flex items-center gap-2">
              <input id="set-email" disabled value={user?.email || ''} className="flex-1 px-3 py-2 rounded border text-sm opacity-75" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }} />
              {!user?.emailVerified && (
                <button type="button" onClick={resendVerification} className="inline-flex items-center gap-1 px-2.5 py-2 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>
                  <MailCheck className="w-3.5 h-3.5" aria-hidden="true" />
                  Verify
                </button>
              )}
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label htmlFor="set-theme" className="block text-xs font-semibold mb-1">{t('themeLabel')}</label>
            <select id="set-theme" value={theme} onChange={(e) => setTheme(e.target.value as 'light' | 'dark' | 'system')} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <option value="system">{t('system')}</option>
              <option value="light">{t('light')}</option>
              <option value="dark">{t('dark')}</option>
            </select>
          </div>
          <div>
            <label htmlFor="set-lang" className="block text-xs font-semibold mb-1">{t('languageLabel')}</label>
            <select id="set-lang" value={language} onChange={(e) => setLanguage(e.target.value as 'en' | 'ta')} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <option value="en">{t('english')}</option>
              <option value="ta">{t('tamil')}</option>
            </select>
          </div>
          <div>
            <label htmlFor="set-curr" className="block text-xs font-semibold mb-1">Currency</label>
            <select id="set-curr" value={currency} onChange={(e) => setCurrency(e.target.value)} className="w-full px-3 py-2 rounded border text-sm" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
              <option value="INR">INR (₹)</option>
              <option value="USD">USD ($)</option>
              <option value="EUR">EUR (€)</option>
              <option value="GBP">GBP (£)</option>
            </select>
          </div>
        </div>

        <div className="space-y-2 pt-2">
          <label className="flex items-center gap-2.5 text-sm cursor-pointer">
            <input type="checkbox" checked={reducedMotion} onChange={(e) => setReducedMotion(e.target.checked)} />
            <span>Reduce motion & disable non-essential transitions</span>
          </label>
          <label className="flex items-center gap-2.5 text-sm cursor-pointer">
            <input type="checkbox" checked={aiConsent} onChange={(e) => setAiConsent(e.target.checked)} />
            <span>Consent to send minimized aggregated financial metrics to configured external LLM providers</span>
          </label>
        </div>

        <button type="submit" className="px-4 py-2 rounded-md text-xs font-semibold cursor-pointer" style={{ background: 'var(--accent)', color: 'var(--accent-text)' }}>
          Save Settings
        </button>
      </form>

      <div className="surface-card p-5 space-y-3">
        <h2 className="text-base font-semibold m-0">Data Portability & Account Deletion</h2>
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={exportAccountJson} className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md border text-xs font-medium cursor-pointer" style={{ borderColor: 'var(--border)', background: 'var(--elevated)' }}>
            <Download className="w-3.5 h-3.5" aria-hidden="true" />
            Export My Data (JSON)
          </button>
          <button
            type="button"
            onClick={() => setDeleteDialogOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md border text-xs font-semibold cursor-pointer"
            style={{ borderColor: 'var(--critical)', color: 'var(--critical)', background: 'var(--surface)' }}
          >
            <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
            Delete Account
          </button>
        </div>
      </div>

      <ModalDialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} title="Confirm Permanent Account Deletion">
        <div className="space-y-4">
          <p className="text-sm m-0">
            This permanently deletes your user record, sessions, ledger transactions, obligations, and reports. This cannot be undone.
          </p>
          <div className="flex justify-end gap-2">
            <button type="button" autoFocus onClick={() => setDeleteDialogOpen(false)} className="px-3 py-2 rounded border text-xs cursor-pointer" style={{ borderColor: 'var(--border)' }}>Cancel</button>
            <button type="button" onClick={handleDeleteAccount} className="px-4 py-2 rounded text-xs font-semibold cursor-pointer" style={{ background: 'var(--critical)', color: '#fff' }}>Delete Forever</button>
          </div>
        </div>
      </ModalDialog>
    </div>
  );
}

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiRequest, AuthUser } from '../lib/api';
import { Language, translations } from '../i18n/translations';

interface AppContextValue {
  user: AuthUser | null;
  authLoading: boolean;
  refreshUser: () => Promise<AuthUser | null>;
  setUser: React.Dispatch<React.SetStateAction<AuthUser | null>>;
  logout: () => Promise<void>;
  theme: 'light' | 'dark' | 'system';
  setTheme: (t: 'light' | 'dark' | 'system') => void;
  language: Language;
  setLanguage: (lang: Language) => void;
  currency: string;
  locale: string;
  reducedMotion: boolean;
  setReducedMotion: (v: boolean) => void;
  t: (key: string) => string;
  formatMoney: (value: number | string | null | undefined, showSign?: boolean) => string;
  toastMessage: string | null;
  notifyToast: (msg: string) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [theme, setThemeState] = useState<'light' | 'dark' | 'system'>(() => {
    try {
      const saved = localStorage.getItem('color-scheme');
      if (saved === 'light' || saved === 'dark') return saved;
    } catch {
      // ignore
    }
    return 'system';
  });
  const [language, setLanguageState] = useState<Language>(() => {
    try {
      const saved = localStorage.getItem('fs-lang');
      if (saved === 'ta' || saved === 'en') return saved;
    } catch {
      // ignore
    }
    return 'en';
  });
  const [reducedMotion, setReducedMotionState] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const applyThemeMeta = useCallback((t: 'light' | 'dark' | 'system') => {
    const meta = document.querySelector('meta[name="color-scheme"]');
    if (meta) {
      meta.setAttribute('content', t === 'system' ? 'light dark' : t);
    }
    try {
      if (t === 'system') localStorage.removeItem('color-scheme');
      else localStorage.setItem('color-scheme', t);
    } catch {
      // ignore
    }
  }, []);

  const setTheme = useCallback((t: 'light' | 'dark' | 'system') => {
    setThemeState(t);
    applyThemeMeta(t);
    if (user) {
      apiRequest('/api/v1/auth/preferences', {
        method: 'PATCH',
        body: JSON.stringify({ theme: t }),
      }).catch(() => {});
    }
  }, [applyThemeMeta, user]);

  const setLanguage = useCallback((lang: Language) => {
    setLanguageState(lang);
    document.documentElement.lang = lang;
    try {
      localStorage.setItem('fs-lang', lang);
    } catch {
      // ignore
    }
    if (user) {
      apiRequest('/api/v1/auth/preferences', {
        method: 'PATCH',
        body: JSON.stringify({ language: lang }),
      }).catch(() => {});
    }
  }, [user]);

  const setReducedMotion = useCallback((v: boolean) => {
    setReducedMotionState(v);
    document.documentElement.setAttribute('data-reduced-motion', String(v));
    if (user) {
      apiRequest('/api/v1/auth/preferences', {
        method: 'PATCH',
        body: JSON.stringify({ reducedMotion: v }),
      }).catch(() => {});
    }
  }, [user]);

  const refreshUser = useCallback(async (): Promise<AuthUser | null> => {
    try {
      const status = await apiRequest<{ authenticated: boolean; user?: AuthUser }>('/api/v1/auth/session-status');
      if (!status.authenticated || !status.user) {
        setUser(null);
        return null;
      }
      const me = status.user;
      setUser(me);
      if (me.preferences) {
        const prefTheme = me.preferences.theme || 'system';
        setThemeState(prefTheme);
        applyThemeMeta(prefTheme);
        const prefLang = (me.preferences.language === 'ta' ? 'ta' : 'en') as Language;
        setLanguageState(prefLang);
        document.documentElement.lang = prefLang;
        setReducedMotionState(Boolean(me.preferences.reducedMotion));
        document.documentElement.setAttribute('data-reduced-motion', String(Boolean(me.preferences.reducedMotion)));
      }
      return me;
    } catch {
      setUser(null);
      return null;
    } finally {
      setAuthLoading(false);
    }
  }, [applyThemeMeta]);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === 'color-scheme') {
        const next = e.newValue === 'light' || e.newValue === 'dark' ? e.newValue : 'system';
        setThemeState(next);
        applyThemeMeta(next);
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [applyThemeMeta]);

  const logout = useCallback(async () => {
    try {
      await apiRequest('/api/v1/auth/logout', { method: 'POST' });
    } finally {
      setUser(null);
    }
  }, []);

  const notifyToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((prev) => (prev === msg ? null : prev));
    }, 3500);
  }, []);

  const currency = user?.preferences?.currency || 'INR';
  const locale = user?.preferences?.locale || (language === 'ta' ? 'ta-IN' : 'en-IN');

  const formatMoney = useCallback((value: number | string | null | undefined, showSign = false) => {
    if (value === null || value === undefined || value === '') return '—';
    const num = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(num)) return '—';
    try {
      const formatted = new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
      }).format(Math.abs(num));
      if (num < 0) return `-${formatted}`;
      if (showSign && num > 0) return `+${formatted}`;
      return formatted;
    } catch {
      return `${currency} ${num.toFixed(2)}`;
    }
  }, [currency, locale]);

  const t = useCallback((key: string) => {
    return translations[language]?.[key] ?? translations.en[key] ?? key;
  }, [language]);

  const value = useMemo<AppContextValue>(() => ({
    user, authLoading, refreshUser, setUser, logout,
    theme, setTheme, language, setLanguage,
    currency, locale, reducedMotion, setReducedMotion,
    t, formatMoney, toastMessage, notifyToast,
  }), [user, authLoading, refreshUser, logout, theme, setTheme, language, setLanguage, currency, locale, reducedMotion, setReducedMotion, t, formatMoney, toastMessage, notifyToast]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}

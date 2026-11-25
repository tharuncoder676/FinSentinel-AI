export const API_BASE_URL = ((import.meta.env?.VITE_API_BASE_URL as string | undefined) || '').replace(/\/+$/, '');

export function apiUrl(path: string): string {
  if (!path) return API_BASE_URL;
  if (/^https?:\/\//i.test(path)) return path;
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE_URL}${normalized}`;
}

export interface ApiError {
  code: string;
  message: string;
  requestId?: string;
  fieldErrors?: Record<string, string>;
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfToken(): Promise<string | null> {
  let token = getCookie('XSRF-TOKEN');
  if (!token) {
    try {
      const res = await fetch(apiUrl('/api/v1/auth/csrf'), { credentials: 'include' });
      if (res.ok) {
        const data = await res.json();
        token = data.token ?? getCookie('XSRF-TOKEN');
      }
    } catch {
      return null;
    }
  }
  return token;
}

export class HttpApiError extends Error {
  status: number;
  code: string;
  fieldErrors?: Record<string, string>;

  constructor(status: number, err: ApiError) {
    super(err.message || 'Request failed');
    this.status = status;
    this.code = err.code || 'HTTP_ERROR';
    this.fieldErrors = err.fieldErrors;
  }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});

  if (!(options.body instanceof FormData) && !headers.has('Content-Type') && method !== 'GET') {
    headers.set('Content-Type', 'application/json');
  }

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await ensureCsrfToken();
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf);
    }
  }

  const res = await fetch(apiUrl(path), {
    ...options,
    credentials: 'include',
    headers,
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const contentType = res.headers.get('content-type') || '';
  if (!res.ok) {
    if (contentType.includes('application/json')) {
      const errBody = (await res.json()) as ApiError;
      throw new HttpApiError(res.status, errBody);
    }
    throw new HttpApiError(res.status, { code: 'HTTP_' + res.status, message: res.statusText || 'Request failed' });
  }

  if (contentType.includes('application/json')) {
    return (await res.json()) as T;
  }
  return (await res.text()) as unknown as T;
}

export interface UserPreferences {
  theme: 'light' | 'dark' | 'system';
  language: 'en' | 'ta';
  currency: string;
  locale: string;
  timezone: string;
  aiExternalConsent: boolean;
  reducedMotion: boolean;
}

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  role: 'USER' | 'ADMIN';
  emailVerified: boolean;
  demoUser: boolean;
  onboardingCompleted: boolean;
  preferences: UserPreferences;
  createdAt: string;
}

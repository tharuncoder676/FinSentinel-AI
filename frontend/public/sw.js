// FinSentinel Safe PWA Service Worker
// Never caches /api/ responses or authenticated private financial data.
const CACHE_NAME = 'finsentinel-shell-v2';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  // Never intercept API requests, auth routes, non-GET, or cross-origin requests
  if (
    url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/auth') ||
    url.pathname.startsWith('/app') ||
    url.origin !== self.location.origin ||
    event.request.method !== 'GET'
  ) {
    return;
  }
  event.respondWith(
    fetch(event.request).catch(async () => {
      const match = await caches.match(event.request);
      if (match) return match;
      return new Response('', { status: 408, statusText: 'Offline' });
    })
  );
});

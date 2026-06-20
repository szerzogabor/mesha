/* Mesha service worker — offline app-shell support for the installable PWA.
 *
 * Strategy:
 *  - Precache a minimal offline shell (icons + offline fallback page).
 *  - Navigations: network-first, falling back to the offline page when the
 *    network is unavailable. This keeps Mesha's live, auth-gated data fresh
 *    while still giving installed users a branded offline screen.
 *  - Static build assets (/_next/static/*): cache-first (they are content-hashed
 *    and therefore safe to cache immutably).
 *  - Everything else (API calls, auth, telemetry, cross-origin): passthrough.
 *
 * Bump CACHE_VERSION to invalidate old caches on deploy.
 */
const CACHE_VERSION = "mesha-v1";
const SHELL_CACHE = `${CACHE_VERSION}-shell`;
const ASSET_CACHE = `${CACHE_VERSION}-assets`;
const OFFLINE_URL = "/offline.html";

const SHELL_ASSETS = [
  OFFLINE_URL,
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  "/icons/icon.svg",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => !key.startsWith(CACHE_VERSION))
            .map((key) => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  );
});

function isStaticAsset(url) {
  return (
    url.origin === self.location.origin &&
    (url.pathname.startsWith("/_next/static/") ||
      url.pathname.startsWith("/icons/"))
  );
}

self.addEventListener("fetch", (event) => {
  const { request } = event;

  // Only handle same-origin GETs. Let the browser deal with auth/API/telemetry.
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (
    url.pathname.startsWith("/api/") ||
    url.pathname.startsWith("/otlp/") ||
    url.pathname.includes("/sign-in") ||
    url.pathname.includes("/sign-up")
  ) {
    return;
  }

  // App-shell navigations: network-first with offline fallback.
  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request).catch(() =>
        caches.match(OFFLINE_URL, { cacheName: SHELL_CACHE }).then(
          (cached) => cached || Response.error()
        )
      )
    );
    return;
  }

  // Content-hashed static assets: cache-first.
  if (isStaticAsset(url)) {
    event.respondWith(
      caches.open(ASSET_CACHE).then(async (cache) => {
        const cached = await cache.match(request);
        if (cached) return cached;
        const response = await fetch(request);
        if (response.ok) cache.put(request, response.clone());
        return response;
      })
    );
  }
});

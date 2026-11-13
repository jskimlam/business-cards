/* THE EXECUTIVE VAULT service worker — network-first for HTML, cache-first for static assets */
const CACHE = 'cards-cache-v15';
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './data/cards.json',
  './icons/icon-192.png',
  './icons/icon-512.png'
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  // HTML 문서는 네트워크 우선 (업데이트 즉시 반영, 오프라인 시 캐시 사용)
  // 버전 확인 파일은 항상 네트워크 (캐시 금지)
  if (url.pathname.endsWith('version.json')) return;

  // HTML·JSON 데이터는 네트워크 우선 + HTTP 캐시 우회 (업데이트 즉시 반영, 오프라인 시 캐시 사용)
  if (req.mode === 'navigate' || req.destination === 'document' || url.pathname.endsWith('.html') || url.pathname.endsWith('/') || url.pathname.endsWith('.json')) {
    e.respondWith(
      fetch(req.url, { cache:'no-store' }).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      }).catch(() => caches.match(req, { ignoreSearch:true }).then((hit) => hit || caches.match('./index.html')))
    );
    return;
  }

  // 기타 정적 파일(이미지, 아이콘, 폰트 등)은 캐시 우선
  e.respondWith(
    caches.match(req, { ignoreSearch: true }).then((hit) => {
      if (hit) return hit;
      return fetch(req).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      });
    })
  );
});

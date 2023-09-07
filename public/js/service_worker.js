const cacheName = 'clerk-browser-cache-v1';

const hosts = [
  'https://fonts.bunny.net',
  'https://cdn.tailwindcss.com',
  'https://storage.clerk.garden',
  'https://cdn.jsdelivr.net'
];

console.log("inside service worker");

self.addEventListener('install', function(event) {
  console.log('Service Worker: Installed');
});

self.addEventListener('activate', function(event) {
  console.log('Service Worker: Activated');

  // Remove unwanted caches
  event.waitUntil(
    caches.keys().then(function(cacheNames) {
      return Promise.all(
        cacheNames.map(function(cache) {
          if (cache !== cacheName) {
            console.log("Service Worker: Clearing old cache");
            return caches.delete(cache);
          }
        }));
    }));
});

self.addEventListener('fetch', function(event) {
  console.log("Service Worker: Fetching", event);

  event.respondWith(
    caches.match(event.request).then(function(response) {
        return response || fetch(event.request).then(function(response) {

        hosts.map(function(host) {
          if (event.request.url.indexOf(host) === 0) {
            var clonedResponse = response.clone();
            caches.open(cacheName).then(function(cache) {
              cache.put(event.request, clonedResponse);
            });
          }
        });
        return response;
      });
    })
  );
});
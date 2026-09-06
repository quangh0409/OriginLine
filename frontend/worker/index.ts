/// <reference lib="webworker" />
export {};
declare const self: ServiceWorkerGlobalScope;

/**
 * Custom service worker additions, merged into the @ducanh2912/next-pwa
 * generated public/sw.js at build time (see next.config.js
 * `customWorkerDir`).
 *
 * This is intentionally scaffolding only for F0: the two handlers below are
 * the "place already set up" for Web Push (approved into Giai đoạn 1, per
 * plan §1.3). Sprint 4 (F7) wires the actual subscription flow
 * (pushManager.subscribe + POST /api/v1/push/subscriptions) and will likely
 * extend the payload contract below — keep it in sync with the backend's
 * WebPushAdapter payload shape once that lands.
 */

interface GiaPhaPushPayload {
  title: string;
  body: string;
  /** Deep link to open on click, e.g. an event or person profile. */
  url?: string;
  notificationId?: string;
}

self.addEventListener("push", (event: PushEvent) => {
  let payload: GiaPhaPushPayload = {
    title: "Nhắc giỗ",
    body: "Bạn có một thông báo mới từ dòng họ.",
  };

  if (event.data) {
    try {
      payload = { ...payload, ...event.data.json() };
    } catch {
      // Fall back to a plain-text body if the payload isn't JSON.
      payload = { ...payload, body: event.data.text() };
    }
  }

  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: "/icons/icon-192.png",
      badge: "/icons/badge-72.png",
      data: { url: payload.url ?? "/", notificationId: payload.notificationId },
    })
  );
});

self.addEventListener("notificationclick", (event: NotificationEvent) => {
  event.notification.close();
  const targetUrl = (event.notification.data as { url?: string } | undefined)?.url ?? "/";

  event.waitUntil(
    self.clients
      .matchAll({ type: "window", includeUncontrolled: true })
      .then((clientList) => {
        for (const client of clientList) {
          if (client.url === targetUrl && "focus" in client) {
            return client.focus();
          }
        }
        if (self.clients.openWindow) {
          return self.clients.openWindow(targetUrl);
        }
      })
  );
});

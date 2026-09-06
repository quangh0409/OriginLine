/**
 * Browser capability probing for Web Push. Every function here is
 * client-only — call them from effects/handlers, never during render, or SSR
 * and hydration will disagree.
 *
 * The hard platform fact this module exists to expose: **iOS grants Web Push
 * only to a PWA that has been added to the Home Screen** (iOS 16.4+). On
 * Safari-in-a-tab `window.PushManager` is missing entirely, so a naive
 * "supported?" check silently reports "no" for a large share of diaspora
 * users on iPhones — who are exactly the people the giỗ reminders are for.
 * We therefore distinguish "cannot ever" from "cannot yet, install first"
 * and show the add-to-Home-Screen guide for the latter
 * (contracts/openapi.yaml `/push/subscriptions` platform notes).
 */

export type PushSupport =
  /** No service worker / PushManager, and installing wouldn't help. */
  | "UNSUPPORTED"
  /** iOS Safari in a browser tab: supported only once installed to Home Screen. */
  | "NEEDS_IOS_INSTALL"
  | "SUPPORTED";

export type PushPermission = NotificationPermission | "unsupported";

export function isBrowser(): boolean {
  return typeof window !== "undefined" && typeof navigator !== "undefined";
}

/** iPhone/iPad, including iPadOS which reports itself as a Mac with touch. */
export function isIos(): boolean {
  if (!isBrowser()) return false;
  const ua = navigator.userAgent;
  if (/iPad|iPhone|iPod/.test(ua)) return true;
  return navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1;
}

/** True when running as an installed PWA rather than inside a browser tab. */
export function isStandalonePwa(): boolean {
  if (!isBrowser()) return false;
  if (window.matchMedia?.("(display-mode: standalone)").matches) return true;
  // Safari's own, non-standard flag — the only reliable signal on iOS.
  return (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
}

export function detectPushSupport(): PushSupport {
  if (!isBrowser()) return "UNSUPPORTED";
  const hasApis =
    "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
  if (hasApis) return "SUPPORTED";
  if (isIos() && !isStandalonePwa()) return "NEEDS_IOS_INSTALL";
  return "UNSUPPORTED";
}

export function readPushPermission(): PushPermission {
  if (!isBrowser() || !("Notification" in window)) return "unsupported";
  return Notification.permission;
}

/**
 * Returns the ACTIVE service worker registration, or undefined when the app
 * has none.
 *
 * Deliberately checks `getRegistration()` before awaiting `ready`:
 * `navigator.serviceWorker.ready` never rejects and never times out, so
 * awaiting it where no worker was ever registered hangs the enable flow
 * forever with no error to show the user. In local `npm run dev:mock` the
 * next-pwa worker is disabled (next.config.js), and MSW's own worker is what
 * gets returned here — which is fine for exercising the subscribe →
 * POST /push/subscriptions round trip, though it obviously cannot receive a
 * real push.
 */
export async function getActiveRegistration(): Promise<ServiceWorkerRegistration | undefined> {
  if (!isBrowser() || !("serviceWorker" in navigator)) return undefined;
  const existing = await navigator.serviceWorker.getRegistration();
  if (!existing) return undefined;
  return navigator.serviceWorker.ready;
}

/**
 * base64url (no padding) VAPID key -> the `Uint8Array` PushManager wants.
 * The key comes from `GET /push/public-key`; never hard-code it, because dev,
 * staging and prod each have their own pair.
 */
export function urlBase64ToUint8Array(base64UrlKey: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64UrlKey.length % 4)) % 4);
  const base64 = (base64UrlKey + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = window.atob(base64);
  // Backed by a concrete ArrayBuffer (not the generic ArrayBufferLike a bare
  // `new Uint8Array(length)` infers), because `PushSubscriptionOptionsInit
  // .applicationServerKey` only accepts a BufferSource over a real ArrayBuffer.
  const output = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) output[i] = raw.charCodeAt(i);
  return output;
}

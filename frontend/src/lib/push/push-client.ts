import {
  detectPushSupport,
  getActiveRegistration,
  readPushPermission,
  urlBase64ToUint8Array,
  type PushPermission,
  type PushSupport,
} from "./support";
import {
  clearPushRecordId,
  readPushRecordId,
  writePushRecordId,
} from "./subscription-store";
import { ApiError } from "@/lib/api/http";
import type {
  PushSubscriptionCreateRequest,
  PushSubscriptionDto,
  VapidPublicKey,
} from "@/types/api";

/**
 * The browser side of Web Push, kept out of React so the ordering of the
 * steps is readable in one place and testable without a renderer.
 *
 * Order matters and is not arbitrary:
 *   permission -> service worker -> PushManager.subscribe -> POST to backend
 * Subscribing before permission throws; POSTing before subscribe has nothing
 * to send; and asking for permission at all is a one-shot action, so the
 * caller must only reach this after the user asked for it explicitly
 * (see subscription-store.ts for the timing rule).
 */

export type PushDeviceStatus =
  | "UNSUPPORTED"
  /** iOS Safari in a tab — offer the add-to-Home-Screen guide instead. */
  | "NEEDS_IOS_INSTALL"
  /** APIs exist but nothing registered a worker (dev build with PWA off). */
  | "NO_SERVICE_WORKER"
  /** Permission was refused. The browser will not ask again from script. */
  | "DENIED"
  /** Can be enabled, but isn't. */
  | "OFF"
  | "ON";

export interface PushDeviceState {
  status: PushDeviceStatus;
  support: PushSupport;
  permission: PushPermission;
  /** Server-side record id for this device, when we have ever registered one. */
  recordId: string | null;
}

export async function detectPushSubscriptionState(): Promise<PushDeviceState> {
  const support = detectPushSupport();
  const permission = readPushPermission();
  const recordId = readPushRecordId();

  if (support === "NEEDS_IOS_INSTALL") {
    return { status: "NEEDS_IOS_INSTALL", support, permission, recordId };
  }
  if (support === "UNSUPPORTED") {
    return { status: "UNSUPPORTED", support, permission, recordId };
  }

  const registration = await getActiveRegistration();
  if (!registration) {
    return { status: "NO_SERVICE_WORKER", support, permission, recordId };
  }
  if (permission === "denied") {
    return { status: "DENIED", support, permission, recordId };
  }

  const subscription = await registration.pushManager.getSubscription();
  return {
    status: subscription && permission === "granted" ? "ON" : "OFF",
    support,
    permission,
    recordId,
  };
}

export class PushEnableError extends Error {
  readonly reason:
    | "PERMISSION_DENIED"
    | "NO_SERVICE_WORKER"
    /** Máy chủ chưa cấu hình khoá VAPID (`WEBPUSH_NOT_CONFIGURED`). Không phải lỗi của người
     * dùng và bấm lại bao nhiêu lần cũng vậy — giao diện phải nói thẳng và cất công tắc đi. */
    | "SERVER_NOT_CONFIGURED"
    | "SUBSCRIBE_FAILED";

  constructor(reason: PushEnableError["reason"], cause?: unknown) {
    super(`Push enable failed: ${reason}`);
    this.name = "PushEnableError";
    this.reason = reason;
    if (cause instanceof Error) this.cause = cause;
  }
}

export interface EnablePushDeps {
  fetchPublicKey: () => Promise<VapidPublicKey>;
  register: (input: PushSubscriptionCreateRequest) => Promise<PushSubscriptionDto>;
  locale: "vi" | "en";
}

/**
 * Must be called from a user gesture — `Notification.requestPermission()` is
 * ignored (or auto-denied) otherwise in every current browser.
 */
export async function enablePush(deps: EnablePushDeps): Promise<PushSubscriptionDto> {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    throw new PushEnableError("PERMISSION_DENIED");
  }

  const registration = await getActiveRegistration();
  if (!registration) {
    throw new PushEnableError("NO_SERVICE_WORKER");
  }

  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    // Lấy khoá và đăng ký được tách làm hai bước có bắt lỗi riêng: "máy chủ chưa cấu hình khoá
    // VAPID" là một kết luận DỨT KHOÁT về máy chủ, còn "SUBSCRIBE_FAILED" là một lỗi mơ hồ đáng
    // thử lại. Gộp chung thì giao diện chỉ biết nói "không đăng ký được, thử lại đi" cho một thứ
    // thử lại chẳng bao giờ chạy.
    let publicKey: string;
    try {
      publicKey = (await deps.fetchPublicKey()).publicKey;
    } catch (error) {
      if (error instanceof ApiError && error.code === "WEBPUSH_NOT_CONFIGURED") {
        throw new PushEnableError("SERVER_NOT_CONFIGURED", error);
      }
      throw new PushEnableError("SUBSCRIBE_FAILED", error);
    }

    try {
      subscription = await registration.pushManager.subscribe({
        // Required by Chrome: every push MUST surface a visible notification.
        // It also matches what we actually do — reminders are user-facing.
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      });
    } catch (error) {
      throw new PushEnableError("SUBSCRIBE_FAILED", error);
    }
  }

  // `toJSON()` is exactly the shape PushSubscriptionCreateRequest models.
  const json = subscription.toJSON() as {
    endpoint?: string;
    expirationTime?: number | null;
    keys?: { p256dh?: string; auth?: string };
  };

  const dto = await deps.register({
    endpoint: json.endpoint ?? subscription.endpoint,
    keys: { p256dh: json.keys?.p256dh ?? "", auth: json.keys?.auth ?? "" },
    expirationTime: json.expirationTime ?? null,
    userAgent: typeof navigator !== "undefined" ? navigator.userAgent : null,
    locale: deps.locale,
  });

  // Keep the record id: it is the only handle for DELETE later.
  writePushRecordId(dto.id);
  return dto;
}

export interface DisablePushDeps {
  unregister: (id: string) => Promise<void>;
}

/**
 * Server first, browser second. If `unsubscribe()` ran first and the DELETE
 * then failed, the backend would keep pushing to an endpoint the browser has
 * already thrown away — the user would have "turned it off" and still get
 * notifications until the gateway 410s.
 */
export async function disablePush(deps: DisablePushDeps): Promise<void> {
  const recordId = readPushRecordId();
  if (recordId) {
    await deps.unregister(recordId);
  }

  const registration = await getActiveRegistration();
  const subscription = await registration?.pushManager.getSubscription();
  await subscription?.unsubscribe();

  clearPushRecordId();
}

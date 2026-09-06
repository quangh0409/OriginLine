import { apiFetch } from "./http";
import type {
  NotificationCategory,
  NotificationPage,
  NotificationReadResult,
  PushSubscriptionCreateRequest,
  PushSubscriptionDto,
  VapidPublicKey,
} from "@/types/api";

export interface ListNotificationsParams {
  status?: "ALL" | "UNREAD" | "READ";
  category?: NotificationCategory;
  page?: number;
  size?: number;
}

export const notificationsApi = {
  /** Always the CALLER's own inbox — there is intentionally no userId parameter. */
  list: (params: ListNotificationsParams = {}) =>
    apiFetch<NotificationPage>("/api/v1/notifications", { query: { ...params } }),

  /** Idempotent — repeat calls keep the original `readAt`, per contracts/README §7.2. */
  markRead: (id: string) =>
    apiFetch<NotificationReadResult>(`/api/v1/notifications/${id}/read`, {
      method: "POST",
    }),

  /** Public VAPID key for `PushManager.subscribe({ applicationServerKey })`. Never hard-code this. */
  getVapidPublicKey: () => apiFetch<VapidPublicKey>("/api/v1/push/public-key"),

  /** Idempotent by `endpoint` — resending the same endpoint updates, never duplicates. */
  subscribePush: (input: PushSubscriptionCreateRequest) =>
    apiFetch<PushSubscriptionDto>("/api/v1/push/subscriptions", {
      method: "POST",
      body: input,
    }),

  unsubscribePush: (id: string) =>
    apiFetch<void>(`/api/v1/push/subscriptions/${id}`, { method: "DELETE" }),
};

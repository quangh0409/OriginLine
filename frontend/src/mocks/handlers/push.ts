import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import type { PushSubscriptionCreateRequest, PushSubscriptionDto, VapidPublicKey } from "@/types/api";

// Not a real key — 65-byte uncompressed P-256 point, base64url, structurally
// valid shape only. Never use this outside local mock development.
const MOCK_VAPID_PUBLIC_KEY =
  "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";

const subscriptions = new Map<string, PushSubscriptionDto>();

export const pushHandlers = [
  http.get(`${API_BASE_URL}/api/v1/push/public-key`, () => {
    const body: VapidPublicKey = { publicKey: MOCK_VAPID_PUBLIC_KEY };
    return HttpResponse.json(body);
  }),

  http.post(`${API_BASE_URL}/api/v1/push/subscriptions`, async ({ request }) => {
    const input = (await request.json()) as PushSubscriptionCreateRequest;
    const isUpdate = subscriptions.has(input.endpoint);
    const dto: PushSubscriptionDto = {
      id: subscriptions.get(input.endpoint)?.id ?? `push-mock-${Date.now()}`,
      endpoint: input.endpoint,
      userAgent: input.userAgent,
      locale: input.locale,
      createdAt: subscriptions.get(input.endpoint)?.createdAt ?? new Date().toISOString(),
      isCurrentDevice: true,
    };
    subscriptions.set(input.endpoint, dto);
    // Idempotent by endpoint (contracts/README §7.11): 200 on update, 201 on new.
    return HttpResponse.json(dto, { status: isUpdate ? 200 : 201 });
  }),

  http.delete(`${API_BASE_URL}/api/v1/push/subscriptions/:id`, ({ params }) => {
    const id = params.id as string;
    for (const [endpoint, sub] of subscriptions) {
      if (sub.id === id) subscriptions.delete(endpoint);
    }
    return new HttpResponse(null, { status: 204 });
  }),
];

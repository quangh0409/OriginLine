import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  detectPushSubscriptionState,
  disablePush,
  enablePush,
  PushEnableError,
} from "@/lib/push/push-client";
import { readPushRecordId, writePushRecordId } from "@/lib/push/subscription-store";
import { ApiError } from "@/lib/api/http";
import type { PushSubscriptionDto, VapidPublicKey } from "@/types/api";

/**
 * F9 — luồng bật/tắt Web Push trên trình duyệt.
 *
 * Thứ tự các bước không phải tuỳ tiện:
 *   xin quyền -> service worker -> PushManager.subscribe -> POST lên máy chủ
 * Đăng ký trước khi có quyền sẽ ném lỗi; POST trước khi subscribe thì chẳng
 * có gì để gửi.
 *
 * Còn khi TẮT thì ngược lại: gọi máy chủ TRƯỚC, huỷ đăng ký ở trình duyệt
 * SAU. Nếu làm ngược, lỡ DELETE thất bại là máy chủ vẫn đẩy tới một endpoint
 * mà trình duyệt đã vứt đi — người dùng "đã tắt" nhưng vẫn nhận thông báo cho
 * tới khi cổng đẩy trả về 410.
 */

const VAPID: VapidPublicKey = {
  publicKey:
    "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
};

const DTO: PushSubscriptionDto = {
  id: "push-rec-1",
  endpoint: "https://fcm.example/abc",
  createdAt: "2026-08-31T00:00:00.000Z",
  isCurrentDevice: true,
};

function fakeSubscription(endpoint = DTO.endpoint) {
  return {
    endpoint,
    toJSON: () => ({
      endpoint,
      expirationTime: null,
      keys: { p256dh: "p256dh-key", auth: "auth-key" },
    }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  };
}

interface WorkerStub {
  registration: {
    pushManager: {
      getSubscription: ReturnType<typeof vi.fn>;
      subscribe: ReturnType<typeof vi.fn>;
    };
  } | null;
}

function stubServiceWorker({ registration }: WorkerStub): void {
  vi.stubGlobal("navigator", {
    ...navigator,
    userAgent: "vitest",
    serviceWorker: {
      getRegistration: vi.fn().mockResolvedValue(registration ?? undefined),
      ready: registration ? Promise.resolve(registration) : new Promise(() => {}),
    },
  });
}

function stubNotification(permission: NotificationPermission, requested = permission): void {
  vi.stubGlobal("Notification", {
    permission,
    requestPermission: vi.fn().mockResolvedValue(requested),
  });
}

beforeEach(() => {
  window.localStorage.clear();
  vi.stubGlobal("PushManager", class {});
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

describe("enablePush — thứ tự các bước", () => {
  it("xin quyền, đăng ký, rồi mới POST lên máy chủ", async () => {
    const subscribe = vi.fn().mockResolvedValue(fakeSubscription());
    stubServiceWorker({
      registration: {
        pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe },
      },
    });
    stubNotification("default", "granted");

    const fetchPublicKey = vi.fn().mockResolvedValue(VAPID);
    const register = vi.fn().mockResolvedValue(DTO);

    const result = await enablePush({ fetchPublicKey, register, locale: "vi" });

    expect(result).toEqual(DTO);
    expect(subscribe).toHaveBeenCalledWith(
      expect.objectContaining({ userVisibleOnly: true })
    );
    expect(register).toHaveBeenCalledWith(
      expect.objectContaining({
        endpoint: DTO.endpoint,
        keys: { p256dh: "p256dh-key", auth: "auth-key" },
        locale: "vi",
      })
    );
  });

  it("luôn đặt userVisibleOnly — Chrome bắt mọi push phải hiện thông báo", async () => {
    const subscribe = vi.fn().mockResolvedValue(fakeSubscription());
    stubServiceWorker({
      registration: {
        pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe },
      },
    });
    stubNotification("default", "granted");

    await enablePush({
      fetchPublicKey: vi.fn().mockResolvedValue(VAPID),
      register: vi.fn().mockResolvedValue(DTO),
      locale: "en",
    });

    expect(subscribe.mock.calls[0]![0].userVisibleOnly).toBe(true);
    expect(subscribe.mock.calls[0]![0].applicationServerKey).toBeInstanceOf(Uint8Array);
  });

  it("lưu id bản ghi — tay cầm DUY NHẤT để sau này gọi DELETE", async () => {
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(null),
          subscribe: vi.fn().mockResolvedValue(fakeSubscription()),
        },
      },
    });
    stubNotification("default", "granted");

    await enablePush({
      fetchPublicKey: vi.fn().mockResolvedValue(VAPID),
      register: vi.fn().mockResolvedValue(DTO),
      locale: "vi",
    });

    expect(readPushRecordId()).toBe("push-rec-1");
  });

  it("người dùng từ chối quyền thì dừng ngay, không đụng tới PushManager", async () => {
    const subscribe = vi.fn();
    stubServiceWorker({
      registration: {
        pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe },
      },
    });
    stubNotification("default", "denied");

    const error = (await enablePush({
      fetchPublicKey: vi.fn(),
      register: vi.fn(),
      locale: "vi",
    }).catch((e: unknown) => e)) as PushEnableError;

    expect(error).toBeInstanceOf(PushEnableError);
    expect(error.reason).toBe("PERMISSION_DENIED");
    expect(subscribe).not.toHaveBeenCalled();
  });

  it("chưa có service worker thì báo lỗi rõ ràng thay vì treo mãi mãi", async () => {
    stubServiceWorker({ registration: null });
    stubNotification("default", "granted");

    const error = (await enablePush({
      fetchPublicKey: vi.fn(),
      register: vi.fn(),
      locale: "vi",
    }).catch((e: unknown) => e)) as PushEnableError;

    expect(error.reason).toBe("NO_SERVICE_WORKER");
  });

  /**
   * Máy chủ chưa cấu hình khoá VAPID là một kết luận DỨT KHOÁT, không phải sự cố tạm thời. Gộp
   * nó vào SUBSCRIBE_FAILED thì giao diện chỉ biết mời người dùng "thử lại sau" cho một thứ thử
   * lại bao nhiêu lần cũng hỏng, và vẫn để nguyên cái công tắc bấm vào là gãy.
   */
  it("máy chủ thiếu khoá VAPID thì báo SERVER_NOT_CONFIGURED, không phải SUBSCRIBE_FAILED", async () => {
    const subscribe = vi.fn();
    stubServiceWorker({
      registration: {
        pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe },
      },
    });
    stubNotification("default", "granted");

    const error = (await enablePush({
      fetchPublicKey: vi.fn().mockRejectedValue(
        new ApiError(503, {
          type: "about:blank",
          title: "Web Push chưa được cấu hình",
          status: 503,
          code: "WEBPUSH_NOT_CONFIGURED",
        })
      ),
      register: vi.fn(),
      locale: "vi",
    }).catch((e: unknown) => e)) as PushEnableError;

    expect(error).toBeInstanceOf(PushEnableError);
    expect(error.reason).toBe("SERVER_NOT_CONFIGURED");
    // Và không đăng ký nửa vời khi chưa có khoá.
    expect(subscribe).not.toHaveBeenCalled();
  });

  it("lỗi mạng khi lấy khoá thì vẫn là SUBSCRIBE_FAILED — đó là thứ đáng thử lại", async () => {
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(null),
          subscribe: vi.fn(),
        },
      },
    });
    stubNotification("default", "granted");

    const error = (await enablePush({
      fetchPublicKey: vi.fn().mockRejectedValue(new Error("network down")),
      register: vi.fn(),
      locale: "vi",
    }).catch((e: unknown) => e)) as PushEnableError;

    expect(error.reason).toBe("SUBSCRIBE_FAILED");
  });

  it("subscribe hỏng thì gói lại thành SUBSCRIBE_FAILED, không POST nửa vời", async () => {
    const register = vi.fn();
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(null),
          subscribe: vi.fn().mockRejectedValue(new Error("AbortError")),
        },
      },
    });
    stubNotification("default", "granted");

    const error = (await enablePush({
      fetchPublicKey: vi.fn().mockResolvedValue(VAPID),
      register,
      locale: "vi",
    }).catch((e: unknown) => e)) as PushEnableError;

    expect(error.reason).toBe("SUBSCRIBE_FAILED");
    expect(register).not.toHaveBeenCalled();
  });

  it("đã có đăng ký sẵn thì dùng lại, chỉ POST lại cho máy chủ (idempotent)", async () => {
    const subscribe = vi.fn();
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(fakeSubscription()),
          subscribe,
        },
      },
    });
    stubNotification("granted");
    const register = vi.fn().mockResolvedValue(DTO);

    await enablePush({ fetchPublicKey: vi.fn(), register, locale: "vi" });

    expect(subscribe).not.toHaveBeenCalled();
    expect(register).toHaveBeenCalledTimes(1);
  });
});

describe("disablePush — máy chủ trước, trình duyệt sau", () => {
  it("gọi DELETE trước rồi mới huỷ đăng ký ở trình duyệt", async () => {
    const order: string[] = [];
    const subscription = fakeSubscription();
    subscription.unsubscribe = vi.fn(async () => {
      order.push("unsubscribe");
      return true;
    });
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(subscription),
          subscribe: vi.fn(),
        },
      },
    });
    writePushRecordId("push-rec-1");

    const unregister = vi.fn(async () => {
      order.push("delete");
    });
    await disablePush({ unregister });

    expect(order).toEqual(["delete", "unsubscribe"]);
    expect(unregister).toHaveBeenCalledWith("push-rec-1");
    expect(readPushRecordId()).toBeNull();
  });

  it("không có id bản ghi thì vẫn dọn sạch phía trình duyệt", async () => {
    const subscription = fakeSubscription();
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(subscription),
          subscribe: vi.fn(),
        },
      },
    });
    const unregister = vi.fn();

    await disablePush({ unregister });

    expect(unregister).not.toHaveBeenCalled();
    expect(subscription.unsubscribe).toHaveBeenCalled();
  });
});

describe("detectPushSubscriptionState", () => {
  it("NO_SERVICE_WORKER khi chưa đăng ký worker nào (bản dev tắt PWA)", async () => {
    stubServiceWorker({ registration: null });
    stubNotification("default");

    await expect(detectPushSubscriptionState()).resolves.toMatchObject({
      status: "NO_SERVICE_WORKER",
    });
  });

  it("DENIED khi trình duyệt đã chặn — không hỏi lại được từ script", async () => {
    stubServiceWorker({
      registration: {
        pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe: vi.fn() },
      },
    });
    stubNotification("denied");

    await expect(detectPushSubscriptionState()).resolves.toMatchObject({ status: "DENIED" });
  });

  it("OFF khi có đủ điều kiện nhưng chưa đăng ký", async () => {
    stubServiceWorker({
      registration: {
        pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe: vi.fn() },
      },
    });
    stubNotification("default");

    await expect(detectPushSubscriptionState()).resolves.toMatchObject({ status: "OFF" });
  });

  it("ON khi đã có quyền và đã đăng ký", async () => {
    stubServiceWorker({
      registration: {
        pushManager: {
          getSubscription: vi.fn().mockResolvedValue(fakeSubscription()),
          subscribe: vi.fn(),
        },
      },
    });
    stubNotification("granted");

    await expect(detectPushSubscriptionState()).resolves.toMatchObject({ status: "ON" });
  });
});

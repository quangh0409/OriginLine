import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * F9 — service worker: hai trình xử lý Web Push.
 *
 * `worker/index.ts` được @ducanh2912/next-pwa trộn vào `public/sw.js` lúc
 * build (xem `customWorkerDir` trong next.config.js). Ở mức unit, ta thay
 * `self` bằng một ServiceWorkerGlobalScope giả, nạp module vào đó rồi gọi
 * thẳng trình xử lý đã đăng ký — cách duy nhất để kiểm hành vi này khi jsdom
 * không có service worker thật.
 *
 * Ba tính chất phải giữ:
 *  - Push RỖNG hoặc hỏng vẫn phải hiện MỘT thông báo. Chrome bắt buộc mỗi lần
 *    push phải hiện thông báo thấy được (`userVisibleOnly: true`); im lặng
 *    nhiều lần thì trình duyệt sẽ tự thu hồi đăng ký của cả thiết bị.
 *  - Bấm vào thông báo phải nhảy tới đúng nơi và ƯU TIÊN tab đang mở, thay vì
 *    mở thêm tab mới mỗi lần nhắc giỗ.
 *  - Mọi thao tác bất đồng bộ phải nằm trong `event.waitUntil`, nếu không
 *    service worker có thể bị dừng giữa chừng.
 */

type Listener = (event: unknown) => void;

interface FakeSelf {
  addEventListener: (type: string, listener: Listener) => void;
  registration: { showNotification: ReturnType<typeof vi.fn> };
  clients: { matchAll: ReturnType<typeof vi.fn>; openWindow: ReturnType<typeof vi.fn> };
}

let listeners: Map<string, Listener[]>;
let showNotification: ReturnType<typeof vi.fn>;
let matchAll: ReturnType<typeof vi.fn>;
let openWindow: ReturnType<typeof vi.fn>;
let waited: unknown[];

/** Nạp lại worker vào một `self` giả hoàn toàn mới cho mỗi test. */
async function loadWorker(): Promise<void> {
  listeners = new Map();
  showNotification = vi.fn().mockResolvedValue(undefined);
  matchAll = vi.fn().mockResolvedValue([]);
  openWindow = vi.fn().mockResolvedValue(undefined);
  waited = [];

  const fakeSelf: FakeSelf = {
    addEventListener: (type, listener) => {
      const bucket = listeners.get(type) ?? [];
      bucket.push(listener);
      listeners.set(type, bucket);
    },
    registration: { showNotification },
    clients: { matchAll, openWindow },
  };

  vi.stubGlobal("self", fakeSelf);
  vi.resetModules();
  await import("../../../worker/index");
}

function dispatch(type: string, event: Record<string, unknown>): void {
  const bucket = listeners.get(type) ?? [];
  expect(bucket.length, `worker không đăng ký trình xử lý '${type}'`).toBe(1);
  bucket[0]!({
    ...event,
    waitUntil: (promise: unknown) => {
      waited.push(promise);
    },
  });
}

function firePush(data?: { json: () => unknown; text: () => string }): void {
  dispatch("push", { data });
}

function fireNotificationClick(url?: string): ReturnType<typeof vi.fn> {
  const close = vi.fn();
  dispatch("notificationclick", {
    notification: { close, data: url === undefined ? undefined : { url } },
  });
  return close;
}

beforeEach(async () => {
  await loadWorker();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("sự kiện push", () => {
  it("hiện thông báo với tiêu đề và nội dung máy chủ gửi", async () => {
    firePush({
      json: () => ({
        title: "Còn 3 ngày tới giỗ Thủy tổ",
        body: "Ngày 22 tháng 9 âm lịch.",
        url: "/events?event=ev-001",
        notificationId: "nt-001",
      }),
      text: () => "",
    });
    await Promise.all(waited);

    expect(showNotification).toHaveBeenCalledTimes(1);
    const [title, options] = showNotification.mock.calls[0]!;
    expect(title).toBe("Còn 3 ngày tới giỗ Thủy tổ");
    expect(options.body).toBe("Ngày 22 tháng 9 âm lịch.");
    expect(options.data).toEqual({ url: "/events?event=ev-001", notificationId: "nt-001" });
  });

  it("gắn biểu tượng và huy hiệu để thông báo không hiện ra trống trơn trên Android", async () => {
    firePush({ json: () => ({ title: "Nhắc giỗ", body: "…" }), text: () => "" });
    await Promise.all(waited);

    const [, options] = showNotification.mock.calls[0]!;
    expect(options.icon).toBe("/icons/icon-192.png");
    expect(options.badge).toBe("/icons/badge-72.png");
  });

  it("push KHÔNG có dữ liệu vẫn hiện một thông báo tiếng Việt mặc định", async () => {
    // Chrome thu hồi đăng ký của thiết bị nếu worker nhận push mà im lặng.
    firePush(undefined);
    await Promise.all(waited);

    expect(showNotification).toHaveBeenCalledTimes(1);
    expect(showNotification.mock.calls[0]![0]).toBe("Nhắc giỗ");
    expect(showNotification.mock.calls[0]![1].body).toMatch(/dòng họ/);
  });

  it("payload không phải JSON thì lùi về dùng nguyên văn bản, không nuốt sự kiện", async () => {
    firePush({
      json: () => {
        throw new SyntaxError("not json");
      },
      text: () => "Giỗ cụ Hiển ngày mai",
    });
    await Promise.all(waited);

    expect(showNotification).toHaveBeenCalledTimes(1);
    expect(showNotification.mock.calls[0]![1].body).toBe("Giỗ cụ Hiển ngày mai");
  });

  it("payload thiếu url thì mặc định về trang chủ, không để undefined", async () => {
    firePush({ json: () => ({ title: "Nhắc giỗ", body: "…" }), text: () => "" });
    await Promise.all(waited);

    expect(showNotification.mock.calls[0]![1].data.url).toBe("/");
  });

  it("giữ sống worker bằng waitUntil trong suốt lúc hiện thông báo", () => {
    firePush({ json: () => ({ title: "x", body: "y" }), text: () => "" });

    expect(waited).toHaveLength(1);
  });
});

describe("bấm vào thông báo", () => {
  it("đóng thông báo rồi mở đúng liên kết sâu", async () => {
    const close = fireNotificationClick("/events?event=ev-001");
    await Promise.all(waited);

    expect(close).toHaveBeenCalledTimes(1);
    expect(openWindow).toHaveBeenCalledWith("/events?event=ev-001");
  });

  it("ưu tiên hội tụ vào tab đang mở đúng trang, thay vì mở thêm tab mới", async () => {
    const focus = vi.fn();
    matchAll.mockResolvedValue([{ url: "/events?event=ev-001", focus }]);

    fireNotificationClick("/events?event=ev-001");
    await Promise.all(waited);

    expect(focus).toHaveBeenCalledTimes(1);
    expect(openWindow).not.toHaveBeenCalled();
  });

  it("tab đang mở ở trang khác thì vẫn mở cửa sổ mới tới đúng đích", async () => {
    matchAll.mockResolvedValue([{ url: "/tree", focus: vi.fn() }]);

    fireNotificationClick("/events?event=ev-001");
    await Promise.all(waited);

    expect(openWindow).toHaveBeenCalledWith("/events?event=ev-001");
  });

  it("thông báo không mang url thì đưa về trang chủ", async () => {
    fireNotificationClick(undefined);
    await Promise.all(waited);

    expect(openWindow).toHaveBeenCalledWith("/");
  });

  it("tính cả các tab chưa bị worker kiểm soát (includeUncontrolled)", async () => {
    fireNotificationClick("/");
    await Promise.all(waited);

    expect(matchAll).toHaveBeenCalledWith({ type: "window", includeUncontrolled: true });
  });

  it("giữ sống worker bằng waitUntil trong suốt lúc điều hướng", () => {
    fireNotificationClick("/tree");

    expect(waited).toHaveLength(1);
  });
});

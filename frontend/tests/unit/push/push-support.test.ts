import { afterEach, describe, expect, it, vi } from "vitest";
import {
  detectPushSupport,
  getActiveRegistration,
  isIos,
  isStandalonePwa,
  readPushPermission,
  urlBase64ToUint8Array,
} from "@/lib/push/support";

/**
 * F9 — dò khả năng hỗ trợ Web Push của trình duyệt.
 *
 * Sự thật nền tảng khiến module này tồn tại: **iOS chỉ cho Web Push khi ứng
 * dụng đã được thêm vào màn hình chính** (iOS 16.4+). Trong tab Safari,
 * `window.PushManager` không tồn tại, nên một phép kiểm tra "có hỗ trợ
 * không?" ngây thơ sẽ âm thầm trả lời "không" cho đúng nhóm kiều bào dùng
 * iPhone — chính là những người mà tính năng nhắc giỗ sinh ra để phục vụ.
 * Vì thế phải phân biệt "không bao giờ được" với "chưa được, cài đặt trước
 * đã".
 *
 * jsdom không có `PushManager`, `serviceWorker` hay `maxTouchPoints`, nên mỗi
 * test tự dựng lấy `navigator`/`window` mà nó muốn mô tả.
 */

interface FakeNavigator {
  userAgent: string;
  platform?: string;
  maxTouchPoints?: number;
  serviceWorker?: unknown;
}

const SAFARI_IPHONE =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1";
const CHROME_ANDROID =
  "Mozilla/5.0 (Linux; Android 13; Pixel 5) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";

function stubNavigator(nav: FakeNavigator): void {
  vi.stubGlobal("navigator", nav);
}

/** Gỡ hẳn một khoá khỏi window (jsdom không có PushManager sẵn). */
function withoutGlobal(key: string, run: () => void): void {
  const target = window as unknown as Record<string, unknown>;
  const had = key in target;
  const previous = target[key];
  delete target[key];
  try {
    run();
  } finally {
    if (had) target[key] = previous;
  }
}

function stubDisplayMode(standalone: boolean): void {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: standalone && query.includes("standalone"),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("nhận diện iOS", () => {
  it("nhận ra iPhone qua user agent", () => {
    stubNavigator({ userAgent: SAFARI_IPHONE });
    expect(isIos()).toBe(true);
  });

  it("nhận ra iPadOS, thứ vốn tự xưng mình là máy Mac có cảm ứng", () => {
    stubNavigator({
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15",
      platform: "MacIntel",
      maxTouchPoints: 5,
    });

    expect(isIos()).toBe(true);
  });

  it("không nhầm máy Mac thật (không cảm ứng) thành iPad", () => {
    stubNavigator({
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15",
      platform: "MacIntel",
      maxTouchPoints: 0,
    });

    expect(isIos()).toBe(false);
  });

  it("không nhầm Android thành iOS", () => {
    stubNavigator({ userAgent: CHROME_ANDROID, platform: "Linux armv8l", maxTouchPoints: 5 });
    expect(isIos()).toBe(false);
  });
});

describe("nhận diện đã cài như PWA", () => {
  it("đúng khi display-mode là standalone", () => {
    stubNavigator({ userAgent: SAFARI_IPHONE });
    stubDisplayMode(true);

    expect(isStandalonePwa()).toBe(true);
  });

  it("đúng khi Safari bật cờ riêng navigator.standalone", () => {
    stubNavigator({ userAgent: SAFARI_IPHONE, ...({ standalone: true } as object) });
    stubDisplayMode(false);

    expect(isStandalonePwa()).toBe(true);
  });

  it("sai khi đang chạy trong một tab bình thường", () => {
    stubNavigator({ userAgent: SAFARI_IPHONE });
    stubDisplayMode(false);

    expect(isStandalonePwa()).toBe(false);
  });
});

describe("detectPushSupport", () => {
  it("SUPPORTED khi có đủ serviceWorker + PushManager + Notification", () => {
    stubNavigator({ userAgent: CHROME_ANDROID, serviceWorker: {} });
    vi.stubGlobal("PushManager", class {});
    vi.stubGlobal("Notification", class {});

    expect(detectPushSupport()).toBe("SUPPORTED");
  });

  it("NEEDS_IOS_INSTALL cho Safari trên iPhone chưa cài ra màn hình chính", () => {
    // Đây là ca quan trọng nhất: trả "UNSUPPORTED" ở đây là bỏ rơi cả nhóm
    // người dùng iPhone, trong khi họ chỉ cần thêm ứng dụng vào màn hình chính.
    stubNavigator({ userAgent: SAFARI_IPHONE });
    stubDisplayMode(false);

    withoutGlobal("PushManager", () => {
      withoutGlobal("Notification", () => {
        expect(detectPushSupport()).toBe("NEEDS_IOS_INSTALL");
      });
    });
  });

  it("UNSUPPORTED cho trình duyệt cũ không phải iOS", () => {
    stubNavigator({ userAgent: "Mozilla/5.0 (Windows NT 6.1) Trident/7.0", platform: "Win32" });
    stubDisplayMode(false);

    withoutGlobal("PushManager", () => {
      withoutGlobal("Notification", () => {
        expect(detectPushSupport()).toBe("UNSUPPORTED");
      });
    });
  });
});

describe("readPushPermission", () => {
  it("đọc trạng thái quyền hiện tại", () => {
    vi.stubGlobal("Notification", { permission: "granted" });
    expect(readPushPermission()).toBe("granted");

    vi.stubGlobal("Notification", { permission: "denied" });
    expect(readPushPermission()).toBe("denied");
  });

  it("trả 'unsupported' chứ không phải 'denied' khi trình duyệt không có Notification", () => {
    // Phân biệt này rất quan trọng: 'denied' nghĩa là người dùng đã từ chối
    // và không hỏi lại được; 'unsupported' thì phải hiện hướng dẫn khác.
    withoutGlobal("Notification", () => {
      expect(readPushPermission()).toBe("unsupported");
    });
  });
});

describe("getActiveRegistration", () => {
  it("trả undefined khi chưa có service worker nào được đăng ký", async () => {
    // Điểm mấu chốt: `navigator.serviceWorker.ready` KHÔNG BAO GIỜ bị từ chối
    // và cũng không hết giờ. Nếu chờ nó ở nơi chưa từng đăng ký worker thì
    // luồng bật thông báo treo vĩnh viễn, không lỗi nào để hiện cho người dùng.
    const neverResolves = new Promise<ServiceWorkerRegistration>(() => {});
    stubNavigator({
      userAgent: CHROME_ANDROID,
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue(undefined),
        ready: neverResolves,
      },
    });

    await expect(getActiveRegistration()).resolves.toBeUndefined();
  });

  it("trả bản đăng ký đang hoạt động khi đã có worker", async () => {
    const registration = { scope: "/" } as ServiceWorkerRegistration;
    stubNavigator({
      userAgent: CHROME_ANDROID,
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue(registration),
        ready: Promise.resolve(registration),
      },
    });

    await expect(getActiveRegistration()).resolves.toBe(registration);
  });

  it("trả undefined khi trình duyệt không hỗ trợ service worker", async () => {
    stubNavigator({ userAgent: "Mozilla/5.0 (Windows NT 6.1) Trident/7.0" });

    await expect(getActiveRegistration()).resolves.toBeUndefined();
  });
});

describe("urlBase64ToUint8Array — khoá VAPID", () => {
  it("giải mã base64url không đệm thành đúng chuỗi byte", () => {
    // "hello" -> base64url "aGVsbG8" (thiếu đệm '=')
    const bytes = urlBase64ToUint8Array("aGVsbG8");

    expect([...bytes]).toEqual([104, 101, 108, 108, 111]);
  });

  it("đổi đúng hai ký tự riêng của base64url ('-' -> '+', '_' -> '/')", () => {
    const bytes = urlBase64ToUint8Array("-_8");

    expect([...bytes]).toEqual([251, 255]);
  });

  it("cho ra 65 byte với khoá P-256 thật (điều PushManager đòi hỏi)", () => {
    const key =
      "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";

    const bytes = urlBase64ToUint8Array(key);

    expect(bytes.byteLength).toBe(65);
    expect(bytes[0]).toBe(0x04); // điểm P-256 dạng không nén
    expect(bytes.buffer).toBeInstanceOf(ArrayBuffer);
  });
});

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getAuthBridge,
  getAuthToken,
  refreshAuthToken,
  setAuthBridge,
  type AuthBridge,
} from "@/lib/auth/token-bridge";

/**
 * F8 — cầu nối giữa phiên Keycloak và lớp API.
 *
 * `apiFetch` chạy trong `queryFn` của React Query, tức là NGOÀI cây React, nên
 * nó không đọc được context. Cầu nối ở phạm vi module là cách duy nhất để nó
 * biết token hiện tại — và cũng là chỗ dễ hỏng âm thầm nhất:
 *
 *  1. Chưa đăng ký cầu nối (chế độ MSW, hoặc trước khi provider dựng) phải trả
 *     `null` chứ không ném lỗi: khách vẫn phải gọi được API.
 *  2. Một lần làm mới token thất bại KHÔNG được phép làm hỏng lời gọi API —
 *     nó chỉ có nghĩa "yêu cầu này đi với tư cách khách".
 *  3. Làm mới thất bại phải kéo theo `onSessionLost`, nếu không giao diện sẽ
 *     mắc kẹt ở trạng thái "đã đăng nhập" mà mọi yêu cầu đều 401.
 */

function fakeBridge(overrides: Partial<AuthBridge> = {}): AuthBridge {
  return {
    getFreshToken: vi.fn(async () => "token-moi"),
    forceRefresh: vi.fn(async () => "token-lam-moi"),
    onSessionLost: vi.fn(),
    ...overrides,
  };
}

afterEach(() => {
  setAuthBridge(null);
});

describe("khi chưa có cầu nối (chế độ khách / chạy trên MSW)", () => {
  it("getAuthToken trả null chứ không ném lỗi", async () => {
    setAuthBridge(null);
    await expect(getAuthToken()).resolves.toBeNull();
  });

  it("refreshAuthToken cũng trả null, không ném lỗi", async () => {
    setAuthBridge(null);
    await expect(refreshAuthToken()).resolves.toBeNull();
  });
});

describe("khi đã có phiên", () => {
  it("chuyển tiếp token do cầu nối cung cấp", async () => {
    const bridge = fakeBridge();
    setAuthBridge(bridge);

    await expect(getAuthToken()).resolves.toBe("token-moi");
    expect(bridge.getFreshToken).toHaveBeenCalledTimes(1);
  });

  it("nuốt lỗi khi làm mới hỏng — yêu cầu vẫn đi, chỉ là với tư cách khách", async () => {
    setAuthBridge(
      fakeBridge({
        getFreshToken: vi.fn(async () => {
          throw new Error("Keycloak khong tra loi");
        }),
      })
    );

    await expect(getAuthToken()).resolves.toBeNull();
  });

  it("setAuthBridge(null) đưa về đúng trạng thái khách", () => {
    setAuthBridge(fakeBridge());
    expect(getAuthBridge()).not.toBeNull();
    setAuthBridge(null);
    expect(getAuthBridge()).toBeNull();
  });
});

describe("làm mới sau 401", () => {
  it("trả token mới khi cứu được phiên, và KHÔNG báo mất phiên", async () => {
    const bridge = fakeBridge();
    setAuthBridge(bridge);

    await expect(refreshAuthToken()).resolves.toBe("token-lam-moi");
    expect(bridge.onSessionLost).not.toHaveBeenCalled();
  });

  it("báo mất phiên khi làm mới trả rỗng", async () => {
    const bridge = fakeBridge({ forceRefresh: vi.fn(async () => null) });
    setAuthBridge(bridge);

    await expect(refreshAuthToken()).resolves.toBeNull();
    expect(bridge.onSessionLost).toHaveBeenCalledTimes(1);
  });

  it("báo mất phiên khi làm mới ném lỗi (refresh token đã hết hạn)", async () => {
    const bridge = fakeBridge({
      forceRefresh: vi.fn(async () => {
        throw new Error("refresh token het han");
      }),
    });
    setAuthBridge(bridge);

    await expect(refreshAuthToken()).resolves.toBeNull();
    expect(bridge.onSessionLost).toHaveBeenCalledTimes(1);
  });
});

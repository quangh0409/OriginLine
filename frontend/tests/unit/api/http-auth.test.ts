import { afterEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL, ApiError, apiFetch } from "@/lib/api/http";
import { setAuthBridge, type AuthBridge } from "@/lib/auth/token-bridge";

/**
 * F8 — header `Authorization` trên dây.
 *
 * Đây chính là chỗ đứt của Giai đoạn 1: `http.ts` từng có đúng một dòng
 * `// TODO(F8 - auth)` ở chỗ đáng lẽ phải gắn token, nên giao diện gọi backend
 * thật với tư cách khách và mọi màn hình chỉ hiện "Không tải được".
 *
 * Bốn tính chất được chốt ở đây:
 *  1. Có token thì mọi yêu cầu mang `Authorization: Bearer`.
 *  2. KHÔNG có token thì tuyệt đối không gắn header rỗng — khách phải gọi
 *     được các điểm cuối công khai (BA v2 §10).
 *  3. 401 giữa chừng: làm mới ĐÚNG một lần rồi gọi lại. Không lặp vô hạn.
 *  4. Làm mới hỏng: rơi về chế độ khách và ném ApiError — không treo giao diện.
 */

const PROBE = "/api/v1/__probe-auth";

interface ProbeEcho {
  authorization: string | null;
  attempt: number;
}

function fakeBridge(overrides: Partial<AuthBridge> = {}): AuthBridge {
  return {
    getFreshToken: vi.fn(async () => "token-hop-le"),
    forceRefresh: vi.fn(async () => "token-vua-lam-moi"),
    onSessionLost: vi.fn(),
    ...overrides,
  };
}

afterEach(() => {
  setAuthBridge(null);
});

describe("gắn Bearer khi đã đăng nhập", () => {
  it("mọi yêu cầu mang đúng token của phiên", async () => {
    setAuthBridge(fakeBridge());
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, ({ request }) =>
        HttpResponse.json({ authorization: request.headers.get("authorization"), attempt: 1 })
      )
    );

    const echo = await apiFetch<ProbeEcho>(PROBE);
    expect(echo.authorization).toBe("Bearer token-hop-le");
  });

  it("gắn cho cả POST, không riêng GET", async () => {
    setAuthBridge(fakeBridge());
    server.use(
      http.post(`${API_BASE_URL}${PROBE}`, ({ request }) =>
        HttpResponse.json({ authorization: request.headers.get("authorization"), attempt: 1 })
      )
    );

    const echo = await apiFetch<ProbeEcho>(PROBE, { method: "POST", body: { a: 1 } });
    expect(echo.authorization).toBe("Bearer token-hop-le");
  });

  it("người gọi vẫn ghi đè được header cho một yêu cầu riêng lẻ", async () => {
    setAuthBridge(fakeBridge());
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, ({ request }) =>
        HttpResponse.json({ authorization: request.headers.get("authorization"), attempt: 1 })
      )
    );

    const echo = await apiFetch<ProbeEcho>(PROBE, {
      headers: { Authorization: "Bearer token-rieng" },
    });
    expect(echo.authorization).toBe("Bearer token-rieng");
  });
});

describe("chế độ khách", () => {
  it("không gắn header Authorization nào cả khi chưa đăng nhập", async () => {
    setAuthBridge(null);
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, ({ request }) =>
        HttpResponse.json({ authorization: request.headers.get("authorization"), attempt: 1 })
      )
    );

    const echo = await apiFetch<ProbeEcho>(PROBE);
    expect(echo.authorization).toBeNull();
  });

  it("401 của khách KHÔNG kích hoạt vòng làm mới — đó là câu trả lời đúng theo luật", async () => {
    const bridge = fakeBridge({ getFreshToken: vi.fn(async () => null) });
    setAuthBridge(bridge);
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, () =>
        HttpResponse.json({ code: "UNAUTHORIZED", status: 401 }, { status: 401 })
      )
    );

    const error = (await apiFetch(PROBE).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(bridge.forceRefresh).not.toHaveBeenCalled();
  });
});

describe("token hết hạn giữa chừng", () => {
  it("làm mới rồi gọi lại đúng MỘT lần và trả về kết quả thành công", async () => {
    const bridge = fakeBridge();
    setAuthBridge(bridge);

    let attempt = 0;
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, ({ request }) => {
        attempt += 1;
        const authorization = request.headers.get("authorization");
        if (authorization === "Bearer token-hop-le") {
          return HttpResponse.json({ code: "UNAUTHORIZED", status: 401 }, { status: 401 });
        }
        return HttpResponse.json({ authorization, attempt });
      })
    );

    const echo = await apiFetch<ProbeEcho>(PROBE);
    expect(echo.authorization).toBe("Bearer token-vua-lam-moi");
    expect(echo.attempt).toBe(2);
    expect(bridge.forceRefresh).toHaveBeenCalledTimes(1);
    expect(bridge.onSessionLost).not.toHaveBeenCalled();
  });

  it("không thử lại lần hai — 401 dai dẳng phải nổi lên thành lỗi, không thành vòng lặp", async () => {
    const bridge = fakeBridge();
    setAuthBridge(bridge);

    let attempt = 0;
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, () => {
        attempt += 1;
        return HttpResponse.json({ code: "UNAUTHORIZED", status: 401 }, { status: 401 });
      })
    );

    const error = (await apiFetch(PROBE).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(attempt).toBe(2);
    expect(bridge.forceRefresh).toHaveBeenCalledTimes(1);
  });

  it("làm mới hỏng: báo mất phiên và ném ApiError chứ không treo", async () => {
    const bridge = fakeBridge({ forceRefresh: vi.fn(async () => null) });
    setAuthBridge(bridge);

    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, () =>
        HttpResponse.json({ code: "UNAUTHORIZED", status: 401 }, { status: 401 })
      )
    );

    const error = (await apiFetch(PROBE).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(bridge.onSessionLost).toHaveBeenCalledTimes(1);
  });
});

describe("cơ chế giả lập vai chỉ sống ở chế độ MSW", () => {
  it("x-mock-role và Authorization loại trừ nhau theo cấu hình, không bao giờ do người dùng chọn", async () => {
    // Bộ test chạy với NEXT_PUBLIC_API_MOCKING=enabled (xem vitest.config.mts),
    // nên đây là bản chạy MSW: header giả lập có mặt, và AUTH_ENABLED tắt.
    const { MOCKING_ENABLED } = await import("@/lib/api/dev-role");
    const { AUTH_ENABLED } = await import("@/lib/auth/keycloak");

    expect(MOCKING_ENABLED).toBe(true);
    expect(AUTH_ENABLED).toBe(false);
    // Bất biến cốt lõi: hai cờ này KHÔNG BAO GIỜ cùng bật.
    expect(MOCKING_ENABLED && AUTH_ENABLED).toBe(false);
  });
});

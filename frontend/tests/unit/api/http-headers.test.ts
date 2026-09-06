import { afterEach, describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import {
  API_BASE_URL,
  ApiError,
  apiFetch,
  apiFetchWithMeta,
  getApiLocale,
  setApiLocale,
} from "@/lib/api/http";
import { setDevRole } from "@/lib/api/dev-role";

/**
 * F8 — lớp API dùng chung.
 *
 * Mọi màn hình đều đi qua `apiFetch`, nên header mà nó gắn là hợp đồng thật
 * giữa frontend và backend. Ba thứ được kiểm ở đây:
 *
 *  1. `Accept-Language` — backend soạn sẵn chữ hiển thị cho người dùng
 *     (tiêu đề sự kiện, `Problem.title` theo RFC 7807) theo header này. Trước
 *     bản vá F8 header này KHÔNG được gửi, nên một người đang xem giao diện
 *     tiếng Anh vẫn nhận lỗi tiếng Việt.
 *  2. `x-mock-role` — chỉ tồn tại khi `NEXT_PUBLIC_API_MOCKING=enabled`; nó
 *     đóng vai claim vai trò của JWT thật để phân tầng riêng tư chạy được
 *     trước khi Keycloak lên.
 *  3. Lỗi phải được phân nhánh theo `code` (enum đóng), không theo `detail`
 *     (chữ đã bản địa hoá) — contracts/README §3.
 */

/** Điểm cuối giả chỉ dùng để soi header thật sự đi trên dây. */
const PROBE = "/api/v1/__probe";

interface ProbeEcho {
  headers: Record<string, string>;
  method: string;
  url: string;
}

function probeHandler() {
  return http.all(`${API_BASE_URL}${PROBE}`, ({ request }) => {
    const headers: Record<string, string> = {};
    request.headers.forEach((value, key) => {
      headers[key.toLowerCase()] = value;
    });
    const echo: ProbeEcho = { headers, method: request.method, url: request.url };
    return HttpResponse.json(echo, { headers: { ETag: '"probe-1"' } });
  });
}

async function probe(options?: Parameters<typeof apiFetch>[1]): Promise<ProbeEcho> {
  server.use(probeHandler());
  return apiFetch<ProbeEcho>(PROBE, options);
}

afterEach(() => {
  // Biến ngôn ngữ nằm ở phạm vi module — trả về mặc định để thứ tự test
  // không quyết định kết quả.
  setApiLocale("vi");
});

describe("Accept-Language (F8)", () => {
  it("mặc định gửi 'vi' — ngôn ngữ mặc định của cổng thông tin", async () => {
    const echo = await probe();
    expect(echo.headers["accept-language"]).toBe("vi");
  });

  it("gửi 'en' sau khi giao diện chuyển sang tiếng Anh", async () => {
    setApiLocale("en");
    expect(getApiLocale()).toBe("en");

    const echo = await probe();
    expect(echo.headers["accept-language"]).toBe("en");
  });

  it("gắn header cho MỌI phương thức, không riêng GET", async () => {
    setApiLocale("en");
    const echo = await probe({ method: "POST", body: { hello: "world" } });

    expect(echo.method).toBe("POST");
    expect(echo.headers["accept-language"]).toBe("en");
  });

  it("để người gọi ghi đè khi cần một ngôn ngữ khác cho một yêu cầu riêng lẻ", async () => {
    const echo = await probe({ headers: { "Accept-Language": "en-GB" } });
    expect(echo.headers["accept-language"]).toBe("en-GB");
  });

  it("không đổi ngôn ngữ toàn cục khi một yêu cầu tự ghi đè header", async () => {
    await probe({ headers: { "Accept-Language": "en-GB" } });
    const echo = await probe();

    expect(echo.headers["accept-language"]).toBe("vi");
  });
});

describe("header dùng chung khác", () => {
  it("luôn xin cả application/problem+json để đọc được lỗi RFC 7807", async () => {
    const echo = await probe();
    expect(echo.headers["accept"]).toContain("application/problem+json");
  });

  it("chuyển tiếp vai trò dev mặc định là 'guest' — mặc định an toàn theo BA v2 §10", async () => {
    const echo = await probe();
    expect(echo.headers["x-mock-role"]).toBe("guest");
  });

  it("chuyển tiếp vai trò đã chọn để phân tầng riêng tư chạy được", async () => {
    setDevRole("branch-head");
    const echo = await probe();
    expect(echo.headers["x-mock-role"]).toBe("branch-head");
  });

  it("trả kèm ETag để lần PATCH sau còn gửi If-Match", async () => {
    server.use(probeHandler());
    const { etag } = await apiFetchWithMeta<ProbeEcho>(PROBE);
    expect(etag).toBe('"probe-1"');
  });

  it("gửi If-Match khi người gọi đưa ETag vào (khoá lạc quan)", async () => {
    const echo = await probe({ ifMatch: '"v3"' });
    expect(echo.headers["if-match"]).toBe('"v3"');
  });
});

describe("ánh xạ lỗi", () => {
  it("ném ApiError mang code máy đọc được chứ không phải chữ hiển thị", async () => {
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Không tìm thấy nhân khẩu",
            status: 404,
            code: "NOT_FOUND",
            instance: PROBE,
          },
          { status: 404 }
        )
      )
    );

    const error = await apiFetch(PROBE).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).code).toBe("NOT_FOUND");
  });

  it("gán code UNKNOWN khi phản hồi lỗi không phải problem+json", async () => {
    server.use(
      http.get(`${API_BASE_URL}${PROBE}`, () => new HttpResponse("boom", { status: 500 }))
    );

    const error = (await apiFetch(PROBE).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("UNKNOWN");
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/",
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/",
  useRouter: () => routerMock,
}));

const { HomeHero } = await import("@/components/home/home-hero");

/**
 * **Trang chủ thật — khối "Bài mới"** (checklist §2).
 *
 * Dữ liệu mồi ở `src/mocks/handlers/posts.ts`: `post-2` ("Xin ý kiến cả họ…")
 * là `PENDING`, tác giả `p-102`; `post-3`/`post-4` là `PUBLISHED`. Khối "Bài
 * mới" đọc `GET /posts/feed`, và hợp đồng nói lối đọc ấy chỉ trả bài đã đăng —
 * khoá đúng bằng cách xem `post-2` KHÔNG hiện ra dù thành viên `p-102` (chính
 * tác giả của nó) đang xem trang chủ.
 */
describe("trang chủ — khối Bài mới", () => {
  it("bài PENDING không hiện trên trang chủ, kể cả với chính tác giả", async () => {
    renderWithProviders(<HomeHero />, { role: "member" });

    await waitFor(() => {
      expect(screen.getByText("Đã hoàn thành trùng tu nhà thờ họ")).toBeInTheDocument();
    });
    expect(screen.getByText("Ra mắt cổng thông tin dòng họ")).toBeInTheDocument();
    expect(screen.queryByText("Xin ý kiến cả họ về ngày họp mặt cuối năm")).not.toBeInTheDocument();
  });

  it("lỗi thật (5xx) KHÔNG được đọc thành 'chưa có bài viết nào'", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/posts/feed`, () =>
        HttpResponse.json(
          { type: "about:blank", title: "Lỗi", status: 500, code: "INTERNAL_ERROR" },
          { status: 500 }
        )
      )
    );
    renderWithProviders(<HomeHero />, { role: "member" });

    await waitFor(
      () => expect(screen.getByText("Chưa tải được phần này. Xin tải lại trang sau ít phút.")).toBeInTheDocument(),
      { timeout: 15_000 }
    );
    expect(screen.queryByText("Chưa có bài viết nào được đăng.")).not.toBeInTheDocument();
  });
});

/**
 * **Khách trên trang chủ.**
 *
 * Bản đầu của tệp này ghim "khách vãng lai cũng thấy đúng các bài đã đăng" — sự
 * thật của MOCK, không của máy chủ: `/posts` đòi token, máy thật trả `401`. Bản
 * thứ hai ghim cả ba khối đều là "cần đăng nhập", vì khi ấy `/honours` và
 * `/events` cũng bị chuỗi bảo mật chặn. Quyết định 26/09/2026 mở hai đường ấy cho
 * khách, CHỈ phần về người đã khuất, nên nay chỉ còn khối Bài viết là "cần đăng
 * nhập"; Vinh danh và Việc họ gọi thẳng và hiện tập đã lọc.
 */
describe("trang chủ — khách chưa đăng nhập", () => {
  let urls: string[] = [];
  beforeEach(() => {
    urls = [];
    server.events.on("request:start", ({ request }) => {
      urls.push(new URL(request.url).pathname);
    });
  });
  afterEach(() => {
    server.events.removeAllListeners("request:start");
  });

  it("không gọi /me hay /posts/feed; có gọi /honours và /events (đã mở cho khách)", async () => {
    renderWithProviders(<HomeHero />, { role: "guest" });

    await waitFor(() => expect(screen.getAllByTestId("home-members-only")).toHaveLength(1));
    await waitFor(() => expect(urls).toContain("/api/v1/honours"));
    await waitFor(() => expect(urls).toContain("/api/v1/events"));
    // Chờ thêm một nhịp để một lượt gọi muộn (nếu có) kịp lộ ra.
    await new Promise((r) => setTimeout(r, 200));

    for (const path of ["/api/v1/me", "/api/v1/posts/feed"]) {
      expect(urls, `khách không được gọi ${path}`).not.toContain(path);
    }
  });

  it("khối Bài viết nói thật là cần đăng nhập; khối Vinh danh hiện vinh danh của cụ đã khuất", async () => {
    renderWithProviders(<HomeHero />, { role: "guest" });

    await waitFor(() => expect(screen.getAllByTestId("home-members-only")).toHaveLength(1));
    expect(screen.queryByText("Chưa có bài viết nào được đăng.")).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Đăng nhập để xem/ })).toHaveLength(1);

    // `honour-1` là của cụ Thủy Tổ (đã khuất) — khách thấy.
    expect(await screen.findByText("Đỗ Hương cống khoa Kỷ Mão")).toBeInTheDocument();
    // `honour-2`/`honour-4` là của người còn sống, đã duyệt — khách KHÔNG thấy, dù đã duyệt.
    expect(screen.queryByText("Giải Nhất Toán học cấp Thành phố")).not.toBeInTheDocument();
    expect(screen.queryByText("Bằng khen Hội Chữ thập đỏ")).not.toBeInTheDocument();
    // Không có banner "đang chờ duyệt" — banner ấy chỉ dành cho người đã có tài khoản (NT-KHACH-01).
    expect(screen.queryByText("Hồ sơ của ông/bà đang chờ duyệt")).not.toBeInTheDocument();
  });

  it("mock /events trả cho khách đúng tập đã lọc: chỉ người đã khuất hoặc lễ cúng tổ tiên", async () => {
    const res = await fetch(`${API_BASE_URL}/api/v1/events?upcomingDays=400&size=100`, {
      headers: { "x-mock-role": "guest" },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      items: { eventType: string; person?: { isAlive?: boolean } | null }[];
    };
    const ancestral = new Set(["GIO_THUONG", "GIO_TO", "GIO_HO", "GIO_CHI", "TIEU_TUONG", "DAI_TUONG", "CHAP_MA"]);
    for (const item of body.items) {
      expect(["SINH_NHAT", "MUNG_THO", "CUOI_HOI"]).not.toContain(item.eventType);
      if (item.person) expect(item.person.isAlive).toBe(false);
      else expect(ancestral.has(item.eventType)).toBe(true);
    }
    // Đối chứng: thành viên thấy nhiều hơn — tức bộ lọc thật sự có việc để làm.
    const member = await fetch(`${API_BASE_URL}/api/v1/events?upcomingDays=400&size=100`, {
      headers: { "x-mock-role": "member" },
    });
    const memberBody = (await member.json()) as { items: unknown[] };
    expect(memberBody.items.length).toBeGreaterThan(body.items.length);
  });

  it("mock /honours trả cho khách chỉ vinh danh đã duyệt của người đã khuất, không khoá người duyệt", async () => {
    const res = await fetch(`${API_BASE_URL}/api/v1/honours?size=100`, {
      headers: { "x-mock-role": "guest" },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      items: { id: string; reviewedBy?: string | null }[];
      page: { totalElements: number };
    };
    expect(body.items.map((h) => h.id)).toEqual(["honour-1"]);
    expect(body.items[0]?.reviewedBy).toBeNull();
    expect(body.page.totalElements).toBe(1);
  });
});

import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse, delay } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { resetClanMockState } from "@/mocks/handlers/clan";
import type { ClanProfilePublicDto } from "@/lib/api/clan-profile";

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

const { Header } = await import("@/components/layout/header");
const { HomeHero } = await import("@/components/home/home-hero");

const HO_KHOI_TAO: ClanProfilePublicDto = {
  initialized: true,
  name: "Họ Nguyễn Đình",
  shortName: "Nguyễn Đình",
  rootBranchPath: "ho_nguyen_dinh",
  region: "BAC",
};

/**
 * **Tên dòng họ đọc từ hồ sơ, và `useClanProfile` không được chặn render.**
 *
 * Ba luật khoá ở đây:
 *  1. chưa khởi tạo (hoặc chưa tải xong) → thanh đầu trang hiện chuỗi thương
 *     hiệu chung, KHÔNG có khung xương, KHÔNG chờ;
 *  2. đã khởi tạo → thanh đầu trang đổi sang tên/tên rút gọn thật;
 *  3. banner "dòng họ chưa khởi tạo" ở trang chủ CHỈ hiện cho `ADMIN`, và chỉ
 *     khi `initialized === false` — mọi vai khác không thấy gì, kể cả khi
 *     dòng họ thật sự chưa khởi tạo.
 */
describe("Tên dòng họ đọc từ hồ sơ công khai", () => {
  beforeEach(() => {
    resetRouterMock();
    resetClanMockState();
  });

  it("chưa khởi tạo: thanh đầu trang hiện chuỗi thương hiệu chung, không nháy", async () => {
    renderWithProviders(<Header />, { role: "guest" });
    // Hiện NGAY, không đợi mạng trả lời — đây chính là "không chặn render".
    expect(await screen.findByText("Cổng Thông Tin Dòng Họ")).toBeInTheDocument();
  });

  it("đang tải (mạng chậm): vẫn hiện chuỗi chung ngay lập tức, không có khung xương", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/public/clan-profile`, async () => {
        await delay(2000);
        return HttpResponse.json(HO_KHOI_TAO);
      })
    );
    renderWithProviders(<Header />, { role: "guest" });
    // Không `findBy` (điều đó sẽ chờ) — `getBy` phải ăn khớp NGAY ở lượt vẽ đầu tiên.
    expect(screen.getByText("Cổng Thông Tin Dòng Họ")).toBeInTheDocument();
  });

  it("đã khởi tạo: thanh đầu trang đổi sang tên dòng họ thật", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/public/clan-profile`, () => HttpResponse.json(HO_KHOI_TAO))
    );
    renderWithProviders(<Header />, { role: "guest" });

    await waitFor(() => {
      expect(screen.getByText("Nguyễn Đình")).toBeInTheDocument();
    });
  });

  it("trang chủ — ADMIN thấy banner 'chưa khởi tạo', dẫn thẳng tới /quan-tri", async () => {
    renderWithProviders(<HomeHero />, { role: "admin" });

    const banner = await screen.findByTestId("clan-uninitialized-banner");
    expect(banner).toHaveTextContent("Dòng họ chưa được khởi tạo");
    const link = screen.getByRole("link", { name: "Khởi tạo dòng họ" });
    expect(link).toHaveAttribute("href", "/quan-tri");
  });

  it("trang chủ — ADMIN không thấy banner khi dòng họ ĐÃ khởi tạo", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/public/clan-profile`, () => HttpResponse.json(HO_KHOI_TAO))
    );
    renderWithProviders(<HomeHero />, { role: "admin" });

    await waitFor(() => {
      expect(screen.queryByTestId("clan-uninitialized-banner")).not.toBeInTheDocument();
    });
  });

  it("trang chủ — thành viên thường KHÔNG thấy banner dù dòng họ thật sự chưa khởi tạo", async () => {
    renderWithProviders(<HomeHero />, { role: "member" });

    // Đợi một nhịp để chắc chắn truy vấn hồ sơ công khai đã trả lời rồi mới
    // khẳng định "không có" — nếu không, một khẳng định `queryBy` chạy quá sớm
    // sẽ xanh vì lý do sai (chưa kịp render, không phải vì luật đúng).
    await screen.findByRole("heading", { level: 1, name: "Gia Phả Dòng Họ" });
    expect(screen.queryByTestId("clan-uninitialized-banner")).not.toBeInTheDocument();
  });
});

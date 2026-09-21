import { describe, expect, it, vi } from "vitest";
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

  it("khách vãng lai cũng thấy đúng các bài đã đăng, không hơn không kém", async () => {
    renderWithProviders(<HomeHero />, { role: "guest" });

    await waitFor(() => {
      expect(screen.getByText("Đã hoàn thành trùng tu nhà thờ họ")).toBeInTheDocument();
    });
    expect(screen.queryByText("Xin ý kiến cả họ về ngày họp mặt cuối năm")).not.toBeInTheDocument();
  });
});

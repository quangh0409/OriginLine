import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { server } from "@/mocks/server";
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

const { Header } = await import("@/components/layout/header");
const { MobileNav } = await import("@/components/layout/mobile-nav");

/**
 * Chuông thông báo và huy hiệu tab "Thông báo" tự hỏi `/notifications` mỗi phút.
 *
 * Đo trên hệ thật 24/09: MỌI trang một vị khách mở đều có `401 GET /notifications`
 * (và `401 GET /me`) ngay khi tải — tài khoản chờ duyệt thì `403
 * ACCOUNT_NOT_LINKED` lặp lại mỗi phút. Hộp thư chỉ tồn tại cho tài khoản đã gắn
 * vào phả; những người khác vẫn có lối vào `/notifications`, nhưng thanh điều
 * hướng không được bắn lỗi định kỳ thay họ.
 */
describe("thanh điều hướng — khách không hỏi hộp thư", () => {
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

  it("thanh đầu trang của khách: có lối vào Thông báo, không gọi /notifications hay /me", async () => {
    renderWithProviders(<Header />, { role: "guest" });

    const link = await screen.findByTestId("header-notifications-link");
    expect(link).toHaveAttribute("href", "/notifications");
    expect(screen.queryByTestId("notification-bell")).not.toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 200));
    expect(urls).not.toContain("/api/v1/notifications");
    expect(urls).not.toContain("/api/v1/me");
  });

  it("thành viên đã gắn phả: vẫn là chuông thật, có hỏi hộp thư", async () => {
    renderWithProviders(<Header />, { role: "member" });

    expect(await screen.findByTestId("notification-bell")).toBeInTheDocument();
    await waitFor(() => expect(urls).toContain("/api/v1/notifications"));
  });

  it("thanh tab đáy của khách: đủ năm ô, không gọi /notifications", async () => {
    renderWithProviders(<MobileNav />, { role: "guest" });

    expect(screen.getAllByRole("link")).toHaveLength(5);
    await new Promise((r) => setTimeout(r, 200));
    expect(urls).not.toContain("/api/v1/notifications");
  });
});

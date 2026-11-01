import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
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

const { SystemAdminLink } = await import("@/components/layout/system-admin-link");

/**
 * **Lối vào `/quan-tri` trên thanh đầu trang máy tính** — CHỈ vai `ADMIN`.
 *
 * Khác `ManagementQueueLink` (Trưởng chi/Hội đồng/Admin): khởi tạo dòng họ là
 * việc kỹ thuật một lần của vai quản trị, không phải danh xưng dòng họ. Bộ
 * giả lập không có vai `COUNCIL` riêng (mã `admin` mang cả hai ở tầng
 * `useAuth()`, nhưng `useMe()` phía máy chủ giả trả đúng `ADMIN`), nên ca
 * "Hội đồng không thấy" không kiểm được tách khỏi ca "không phải Admin thì
 * không thấy" — bốn vai còn lại (khách, thành viên, Trưởng chi) đã đủ khoá
 * luật "chỉ ADMIN".
 */
describe("SystemAdminLink", () => {
  beforeEach(() => {
    resetClanMockState();
  });

  it("Khách không thấy gì", () => {
    renderWithProviders(<SystemAdminLink />, { role: "guest" });
    expect(screen.queryByTestId("system-admin-link")).toBeNull();
  });

  it("Thành viên thường không thấy", () => {
    renderWithProviders(<SystemAdminLink />, { role: "member" });
    expect(screen.queryByTestId("system-admin-link")).toBeNull();
  });

  it("Trưởng chi không thấy — khởi tạo dòng họ không phải việc của Trưởng chi", () => {
    renderWithProviders(<SystemAdminLink />, { role: "branch-head" });
    expect(screen.queryByTestId("system-admin-link")).toBeNull();
  });

  it("Quản trị hệ thống thấy liên kết, trỏ đúng /quan-tri", async () => {
    renderWithProviders(<SystemAdminLink />, { role: "admin" });
    const link = await screen.findByTestId("system-admin-link");
    expect(link).toHaveAttribute("href", "/quan-tri");
  });

  it("chấm đỏ khi dòng họ CHƯA khởi tạo", async () => {
    renderWithProviders(<SystemAdminLink />, { role: "admin" });
    await screen.findByTestId("system-admin-link");

    await waitFor(() => {
      expect(document.querySelector(".ant-badge-dot")).toBeInTheDocument();
    });
  });

  it("không chấm đỏ khi dòng họ ĐÃ khởi tạo", async () => {
    // Dựng render RIÊNG (QueryClient riêng) thay vì `rerender` trên cùng một
    // lượt: `useClanProfile` khai `staleTime` 24 giờ có chủ ý (đổi tên dòng
    // họ là chuyện hiếm — javadoc hook), nên dữ liệu của lượt render trước sẽ
    // không tự tải lại chỉ vì `server.use` vừa đổi phản hồi.
    const initialized: ClanProfilePublicDto = {
      initialized: true,
      name: "Họ Nguyễn Đình",
      rootBranchPath: "ho_nguyen_dinh",
      region: "BAC",
    };
    server.use(
      http.get(`${API_BASE_URL}/api/v1/public/clan-profile`, () => HttpResponse.json(initialized))
    );

    renderWithProviders(<SystemAdminLink />, { role: "admin" });
    await screen.findByTestId("system-admin-link");

    await waitFor(() => {
      expect(document.querySelector(".ant-badge-dot")).not.toBeInTheDocument();
    });
  });
});

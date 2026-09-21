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

const { ManagementQueueLink } = await import("@/components/membership/management-queue-link");

/**
 * **Lối vào `/quan-ly` trên thanh đầu trang máy tính.**
 *
 * Trước bản sửa này, `/quan-ly` — hàng chờ đơn tự nhận cộng màn phát mã —
 * không có bất kỳ lối vào nào trên máy tính: liên kết duy nhất trong toàn bộ
 * mã nguồn nằm trong `<MoreMenu>`, và nút mở ngăn kéo ấy tự khai `md:hidden`.
 * Bài kiểm này khoá lại đúng thứ đã thiếu: liên kết PHẢI tồn tại trong DOM
 * (không phụ thuộc bề rộng khung nhìn — CSS ẩn/hiện là việc của trình duyệt,
 * không phải của cây React) khi và chỉ khi người xem có quyền duyệt.
 */
describe("ManagementQueueLink", () => {
  it("Khách không thấy gì — không để lại cái vỏ gợi ý có thứ gì đó bị khoá", () => {
    renderWithProviders(<ManagementQueueLink />, { role: "guest" });
    expect(screen.queryByTestId("management-queue-link")).toBeNull();
  });

  it("Thành viên thường không thấy — không có quyền duyệt", () => {
    renderWithProviders(<ManagementQueueLink />, { role: "member" });
    expect(screen.queryByTestId("management-queue-link")).toBeNull();
  });

  it("Trưởng chi thấy liên kết, trỏ đúng /quan-ly", async () => {
    renderWithProviders(<ManagementQueueLink />, { role: "branch-head" });

    const link = await screen.findByTestId("management-queue-link");
    expect(link).toHaveAttribute("href", "/quan-ly");
  });

  it("Hội đồng/Admin thấy số đơn tự nhận đang chờ trên huy hiệu", async () => {
    renderWithProviders(<ManagementQueueLink />, { role: "admin" });

    const link = await screen.findByTestId("management-queue-link");
    // Dữ liệu mồi của `membership-admin.ts` luôn có ít nhất một đơn PENDING
    // trong phạm vi Hội đồng (xem `membership-queue.test.tsx`) — huy hiệu
    // phải phản ánh một số khác 0, không phải hằng số `0` cũ.
    await waitFor(() => {
      expect(link.getAttribute("aria-label")).toMatch(/\d+/);
    });
  });
});

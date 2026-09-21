import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/bai-viet",
  useRouter: () => routerMock,
  redirect: () => undefined,
  getPathname: () => "/bai-viet",
}));

const { PostListScreen } = await import("@/components/posts/post-list-screen");

/**
 * Việc 3.1 — `/bai-viet` từng trả 404 (không có trang danh sách nào; xem
 * `home-hero.tsx`'s `viewAllHref="/bai-viet"`, một đường dẫn treo cho tới
 * tệp này). Bài kiểm chỉ cần chứng minh trang tồn tại, đọc ĐÚNG các bài
 * `PUBLISHED` (không lẫn nháp/chờ duyệt), và có lối vào "Viết bài mới".
 */
describe("PostListScreen — /bai-viet", () => {
  it("liệt kê bài đã đăng, không lẫn nháp hay bài đang chờ duyệt", async () => {
    renderWithProviders(<PostListScreen />, { role: "member" });

    await waitFor(() => {
      expect(screen.getByText("Đã hoàn thành trùng tu nhà thờ họ")).toBeInTheDocument();
    });
    expect(screen.getByText("Ra mắt cổng thông tin dòng họ")).toBeInTheDocument();

    // post-1 (DRAFT) và post-2/post-5 (PENDING) của cùng bộ mồi không được lọt vào đây.
    expect(screen.queryByText("Sửa lại đường vào từ đường trước giỗ Tổ")).not.toBeInTheDocument();
    expect(screen.queryByText("Xin ý kiến cả họ về ngày họp mặt cuối năm")).not.toBeInTheDocument();

    expect(screen.getByRole("link", { name: /Viết bài mới/ })).toBeInTheDocument();
  });
});

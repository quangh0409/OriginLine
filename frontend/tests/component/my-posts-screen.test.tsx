import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/bai-viet/cua-toi",
  useRouter: () => routerMock,
  redirect: () => undefined,
  getPathname: () => "/bai-viet/cua-toi",
}));

const { MyPostsScreen } = await import("@/components/posts/my-posts-screen");

/**
 * Việc 3.2 — "bài của tôi / nháp của tôi". Trước tệp này, một bản nháp đang
 * viết dở chỉ mở lại được nếu người dùng còn giữ đường dẫn. `member`
 * (`p-102`) là tác giả của `post-1` (DRAFT) và `post-2` (PENDING) trong bộ
 * mồi `src/mocks/handlers/posts.ts` — cả hai phải xuất hiện, đúng nhóm.
 */
describe("MyPostsScreen — /bai-viet/cua-toi", () => {
  it("tách nháp đang viết dở khỏi bài đã gửi đi, cả hai đều thấy được", async () => {
    renderWithProviders(<MyPostsScreen />, { role: "member" });

    await waitFor(() => {
      expect(screen.getByText("Sửa lại đường vào từ đường trước giỗ Tổ")).toBeInTheDocument();
    });
    expect(screen.getByText("Xin ý kiến cả họ về ngày họp mặt cuối năm")).toBeInTheDocument();

    // Bài PENDING của người KHÁC (post-5, tác giả branch-head) không lọt vào đây.
    expect(screen.queryByText("Xin ý kiến sửa cổng chi")).not.toBeInTheDocument();
  });

  it("người chưa được duyệt vào phả: không có bài nào ở đây, nói rõ vì sao", async () => {
    renderWithProviders(<MyPostsScreen />, { role: "admin" });

    await waitFor(() => {
      expect(
        screen.getByText("Chỉ người đã được duyệt vào phả mới có bài viết ở đây.")
      ).toBeInTheDocument();
    });
  });
});

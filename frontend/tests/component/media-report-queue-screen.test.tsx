import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import { setDevRole } from "@/lib/api/dev-role";
import { setApiLocale } from "@/lib/api/http";
import { mediaApi } from "@/lib/api/media";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/quan-ly/bao-cao-anh",
  useRouter: () => routerMock,
  redirect: () => undefined,
  getPathname: () => "/quan-ly/bao-cao-anh",
}));

const { MediaReportQueueScreen } = await import(
  "@/components/posts/media/media-report-queue-screen"
);

/**
 * Màn duyệt báo cáo ảnh/video — điều kiện an toàn của "ảnh đi theo quyền của
 * bài": không có ai xem/duyệt báo cáo thì `MediaReportButton` chỉ là một hộp
 * thư không đáy.
 *
 * `media-seed-1` (mồi ở `src/mocks/handlers/posts.ts`, gắn với `post-3`, chi
 * `b-chi1` → `root.chi_nhat`) đã có sẵn — báo cáo nó với vai `member`, rồi mở
 * màn duyệt bằng vai `branch-head` (quản đúng `root.chi_nhat`).
 */
async function seedOneReport(): Promise<void> {
  setApiLocale("vi");
  setDevRole("member");
  await mediaApi.report("media-seed-1", { reason: "KHONG_PHU_HOP" });
}

describe("MediaReportQueueScreen — /quan-ly/bao-cao-anh", () => {
  it("Trưởng chi trong phạm vi thấy báo cáo, và gỡ đòi xác nhận hai bước", async () => {
    await seedOneReport();

    const { user } = renderWithProviders(<MediaReportQueueScreen />, { role: "branch-head" });

    await waitFor(() => {
      expect(screen.getByText("Đã hoàn thành trùng tu nhà thờ họ")).toBeInTheDocument();
    });
    expect(screen.getByText("Không phù hợp với bài viết")).toBeInTheDocument();

    // Bấm "Gỡ vĩnh viễn" lần một CHƯA gọi API — chỉ mở khối cảnh báo.
    const takedownButton = screen.getByRole("button", { name: "Gỡ vĩnh viễn" });
    await user.click(takedownButton);

    expect(
      screen.getByText(/Gỡ là xoá tệp NGAY LẬP TỨC.*không có nút hoàn tác/)
    ).toBeInTheDocument();

    // Xác nhận thật — báo cáo biến mất khỏi hàng chờ (đã xử lý).
    await user.click(screen.getByRole("button", { name: "Xác nhận gỡ, không thể hoàn tác" }));

    await waitFor(() => {
      expect(screen.queryByText("Đã hoàn thành trùng tu nhà thờ họ")).not.toBeInTheDocument();
    });
  });

  it("thành viên thường không có quyền duyệt: nói rõ, không hiện danh sách", async () => {
    renderWithProviders(<MediaReportQueueScreen />, { role: "member" });

    await waitFor(() => {
      expect(
        screen.getByText("Ông/bà không có quyền duyệt báo cáo ảnh/video.")
      ).toBeInTheDocument();
    });
  });
});

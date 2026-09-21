import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/quan-ly/bai-viet",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/quan-ly/bai-viet",
}));

const { PostReviewQueueScreen } = await import("@/components/posts/post-review-queue-screen");
const { HonoursListScreen } = await import("@/components/honours/honours-list-screen");

/**
 * **Không ai tự duyệt bài hay vinh danh của chính mình** (`SELF_REVIEW_FORBIDDEN`,
 * theo tiền lệ `ChangeRequest`).
 *
 * `post-5` và `honour-5` (mồi ở `src/mocks/handlers/{posts,honours}.ts`) đều
 * đứng tên `p-103` — chính nhân khẩu của tài khoản Trưởng chi trong bộ giả
 * lập — để khoá đúng ca "đúng phạm vi chi vẫn không tự duyệt được".
 */
describe("không ai tự duyệt bài/vinh danh của chính mình", () => {
  it("hàng chờ duyệt bài: bài của chính Trưởng chi không có nút Duyệt/Trả lại, bài của người khác thì có", async () => {
    renderWithProviders(<PostReviewQueueScreen />, { role: "branch-head" });

    const ownHeading = await screen.findByText("Xin ý kiến sửa cổng chi");
    const ownCard = ownHeading.closest("article");
    if (!ownCard) throw new Error("Không tìm thấy thẻ bài của chính Trưởng chi");
    expect(within(ownCard).getByText(/Đây là bài viết của chính ông\/bà/)).toBeInTheDocument();
    expect(within(ownCard).queryByRole("button", { name: "Duyệt" })).not.toBeInTheDocument();
    expect(within(ownCard).queryByRole("button", { name: "Trả lại" })).not.toBeInTheDocument();

    const othersHeading = screen.getByText("Xin ý kiến cả họ về ngày họp mặt cuối năm");
    const othersCard = othersHeading.closest("article");
    if (!othersCard) throw new Error("Không tìm thấy thẻ bài của thành viên khác");
    expect(within(othersCard).getByRole("button", { name: "Duyệt" })).toBeInTheDocument();
  });

  it("vinh danh: bản ghi về chính Trưởng chi không có nút Duyệt/Từ chối, bản ghi của người khác thì có", async () => {
    renderWithProviders(<HonoursListScreen />, { role: "branch-head" });

    const ownHeading = await screen.findByText("Bằng Lương y cấp tỉnh");
    const ownCard = ownHeading.closest("article");
    if (!ownCard) throw new Error("Không tìm thấy thẻ vinh danh của chính Trưởng chi");
    await waitFor(() => {
      expect(within(ownCard).getByText(/vinh danh về chính ông\/bà/)).toBeInTheDocument();
    });
    expect(within(ownCard).queryByRole("button", { name: "Duyệt" })).not.toBeInTheDocument();

    const othersHeading = screen.getByText("Bí thư chi bộ thôn");
    const othersCard = othersHeading.closest("article");
    if (!othersCard) throw new Error("Không tìm thấy thẻ vinh danh của người khác");
    expect(within(othersCard).getByRole("button", { name: "Duyệt" })).toBeInTheDocument();
  });
});

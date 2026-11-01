import { describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders, expectNoHiddenFieldPlaceholders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/vinh-danh",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/vinh-danh",
}));

const { HonourCard } = await import("@/components/honours/honour-card");
const { HonoursListScreen } = await import("@/components/honours/honours-list-screen");

/**
 * **Vinh danh của người còn sống thiếu trường thì màn không vỡ** (checklist §2).
 *
 * `honour-4` (mồi ở `src/mocks/handlers/honours.ts`) là bản ghi của `p-103`
 * (còn sống) với `nameOpenToClan: false` — chính chủ chưa mở nhóm trường thứ
 * sáu cho cả họ, nên với một thành viên thường CẢ BẢN GHI vắng mặt (khớp máy
 * chủ thật). Luật là "vắng mặt ⇒ không vẽ gì thay vào chỗ đó", không phải một
 * dấu ẩn.
 */
describe("vinh danh — trường vắng mặt không làm vỡ màn hình", () => {
  it("HonourCard tự đứng vững khi personDisplayName/issuer/description đều vắng mặt", () => {
    renderWithProviders(
      <HonourCard
        honour={{
          id: "honour-x",
          personId: "p-999",
          kind: "THANH_TICH",
          title: "Một thành tích không tên",
          status: "PUBLISHED",
          createdAt: "2026-01-01T00:00:00.000Z",
          version: 1,
        }}
      />
    );

    expect(screen.getByText("Một thành tích không tên")).toBeInTheDocument();
    // Không có dòng "Người được vinh danh" nào được vẽ ra khi tên vắng mặt.
    expect(screen.queryByText(/Người được vinh danh/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nơi cấp/)).not.toBeInTheDocument();
  });

  it("danh sách vinh danh: vinh danh ĐÃ DUYỆT của người còn sống chưa mở chia sẻ KHÔNG hiện với thành viên thường, và không để lại chỗ trống", async () => {
    const { container } = renderWithProviders(<HonoursListScreen />, { role: "member" });

    // Chờ danh sách thật sự tải xong (một vinh danh công khai khác đã hiện).
    await waitFor(() => {
      expect(screen.getByText("Giải Nhất Toán học cấp Thành phố")).toBeInTheDocument();
    });
    // Khớp máy chủ thật (`HonourService.disclosable`): honour-4 của p-103 (còn sống, nhóm trường
    // thứ sáu đóng) vắng mặt HẲN — không phải hiện với tên rỗng như bản mock trước. Nghiệm thu
    // NT-TV-15 chấp nhận cả hai cách, nhưng mock phải nói đúng cách máy chủ thật làm.
    expect(screen.queryByText("Bằng khen Hội Chữ thập đỏ")).not.toBeInTheDocument();
    expect(screen.queryByText("Nguyễn Văn Cẩn")).not.toBeInTheDocument();
    expectNoHiddenFieldPlaceholders(container);
  });

  it("chính chủ vẫn thấy tên của mình trên chính bản ghi đó", async () => {
    renderWithProviders(<HonoursListScreen />, { role: "branch-head" });

    const heading = await screen.findByText("Bằng khen Hội Chữ thập đỏ");
    const card = heading.closest("article");
    if (!card) throw new Error("Không tìm thấy thẻ vinh danh");
    expect(within(card).getByText(/Nguyễn Văn Cẩn/)).toBeInTheDocument();
  });
});

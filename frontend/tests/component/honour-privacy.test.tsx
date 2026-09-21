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
 * sáu cho cả họ, nên `personDisplayName` VẮNG MẶT với một thành viên thường.
 * Luật là "vắng mặt ⇒ không vẽ gì thay vào chỗ đó", không phải một dấu ẩn.
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

  it("danh sách vinh danh: bản ghi thiếu tên vẫn hiện, không đặt chỗ trống gợi ý dữ liệu bị ẩn", async () => {
    const { container } = renderWithProviders(<HonoursListScreen />, { role: "member" });

    await waitFor(() => {
      expect(screen.getByText("Bằng khen Hội Chữ thập đỏ")).toBeInTheDocument();
    });
    // p-103 = Nguyễn Văn Cẩn, nhưng nhóm trường thứ sáu của honour-4 đang đóng
    // với cả họ — thành viên thường không thấy tên đi kèm bản ghi ấy.
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

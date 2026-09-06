import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TreeToolbar } from "@/components/tree/tree-toolbar";

/**
 * F2 — nút "Thu toàn cây" trên thanh công cụ phả đồ.
 *
 * Phả đồ mở ra ở sàn phóng 0.75 để còn đọc và bấm được trên điện thoại. Nút này là đường thoát
 * CHỦ Ý cho người muốn nhìn toàn cảnh — với nhiều dòng họ, khoảnh khắc thấy trọn cả họ mình
 * (chiếu lên màn hình ngày giỗ Tổ, họp họ) mới là giá trị của sản phẩm.
 *
 * <TreeToolbar> cố ý không tự gọi `useReactFlow()`: nó nhận `onFitWholeTree` qua prop, nên ở đây
 * kiểm được đúng phần giao diện mà không phải dựng cả canvas. Việc "gọi fitView không áp sàn"
 * được kiểm ở tests/unit/tree/fit-view.test.ts (tham số) và ở e2e/mobile.spec.ts (mức phóng thật
 * sau khi bấm, trong trình duyệt thật).
 */
function props(overrides: Partial<React.ComponentProps<typeof TreeToolbar>> = {}) {
  return {
    viewMode: "hierarchical" as const,
    onViewModeChange: vi.fn(),
    direction: "DESCENDANTS" as const,
    onDirectionChange: vi.fn(),
    nodeCount: 47,
    ...overrides,
  };
}

describe('nút "Thu toàn cây"', () => {
  it("gọi đúng lệnh canh khung khi được bấm", async () => {
    const onFitWholeTree = vi.fn();
    const { user } = renderWithProviders(
      <TreeToolbar {...props({ onFitWholeTree })} />
    );

    await user.click(screen.getByRole("button", { name: /Thu toàn cây/ }));

    expect(onFitWholeTree).toHaveBeenCalledTimes(1);
  });

  it("không đụng tới chế độ xem hay chiều duyệt cây", async () => {
    const onViewModeChange = vi.fn();
    const onDirectionChange = vi.fn();
    const { user } = renderWithProviders(
      <TreeToolbar {...props({ onViewModeChange, onDirectionChange, onFitWholeTree: vi.fn() })} />
    );

    await user.click(screen.getByRole("button", { name: /Thu toàn cây/ }));

    expect(onViewModeChange).not.toHaveBeenCalled();
    expect(onDirectionChange).not.toHaveBeenCalled();
  });

  it("hiển thị song ngữ theo ngôn ngữ đang chọn", () => {
    renderWithProviders(<TreeToolbar {...props({ onFitWholeTree: vi.fn() })} />, { locale: "en" });

    expect(screen.getByRole("button", { name: /Fit whole tree/ })).toBeInTheDocument();
    // Không được lọt khoá i18n ra màn hình.
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });

  it("không vẽ nút khi thanh công cụ chưa được nối với canvas", () => {
    // Thà không có nút còn hơn có một cái nút bấm vào không xảy ra gì.
    renderWithProviders(<TreeToolbar {...props()} />);

    expect(screen.queryByRole("button", { name: /Thu toàn cây/ })).not.toBeInTheDocument();
  });

  it("vẫn giữ nguyên số nhân khẩu đang hiển thị bên cạnh", () => {
    renderWithProviders(<TreeToolbar {...props({ onFitWholeTree: vi.fn() })} />);

    expect(screen.getByTestId("tree-loaded-count")).toHaveTextContent("47");
  });
});

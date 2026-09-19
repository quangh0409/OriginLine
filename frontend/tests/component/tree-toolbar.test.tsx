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
    visibleCount: 47,
    loadedCount: 312,
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

    expect(screen.getByTestId("tree-node-count")).toHaveTextContent("47");
  });
});

/**
 * Thẻ đếm phải nói ĐÚNG thứ nó đang đếm.
 *
 * <p>Lỗi cũ: thanh công cụ nhận số đã TẢI rồi in ra dưới nhãn “{count} nhân khẩu đang hiển thị”.
 * Thu một nhánh lại thì màn hình vơi đi mà con số đứng im — nhãn nói sai, và nói sai theo đúng
 * hướng khiến người dùng tưởng thao tác thu gọn của mình không có tác dụng.</p>
 *
 * <p>Cách chữa: in ra số ĐANG HIỂN THỊ (thứ người dùng kiểm chứng được bằng mắt), còn số đã tải
 * vẫn để lộ qua thuộc tính máy đọc được và qua `title` — nó là bằng chứng của giao kèo “không bao
 * giờ tải cả cây”, mất nó là mất luôn chỗ quan sát duy nhất.</p>
 */
describe("thẻ đếm nhân khẩu", () => {
  it("in ra số ĐANG HIỂN THỊ, không phải số đã tải", () => {
    renderWithProviders(
      <TreeToolbar {...props({ visibleCount: 12, loadedCount: 300, onFitWholeTree: vi.fn() })} />
    );

    const chip = screen.getByTestId("tree-node-count");
    expect(chip).toHaveTextContent("12 nhân khẩu đang hiển thị");
    expect(chip).not.toHaveTextContent("300");
  });

  it("vẫn để lộ số đã tải cho phép đo và cho lời giải thích", () => {
    renderWithProviders(
      <TreeToolbar {...props({ visibleCount: 12, loadedCount: 300, onFitWholeTree: vi.fn() })} />
    );

    const chip = screen.getByTestId("tree-node-count");
    expect(chip).toHaveAttribute("data-visible-count", "12");
    expect(chip).toHaveAttribute("data-loaded-count", "300");
    expect(chip.getAttribute("title")).toContain("300");
  });

  it("thu một nhánh lại thì con số phải vơi theo, còn số đã tải thì không", () => {
    const { rerender } = renderWithProviders(
      <TreeToolbar {...props({ visibleCount: 47, loadedCount: 300, onFitWholeTree: vi.fn() })} />
    );
    expect(screen.getByTestId("tree-node-count")).toHaveTextContent("47");

    rerender(
      <TreeToolbar {...props({ visibleCount: 20, loadedCount: 300, onFitWholeTree: vi.fn() })} />
    );

    const chip = screen.getByTestId("tree-node-count");
    expect(chip).toHaveTextContent("20 nhân khẩu đang hiển thị");
    expect(chip).toHaveAttribute("data-loaded-count", "300");
  });

  it("hiển thị song ngữ, không lọt khoá i18n", () => {
    renderWithProviders(
      <TreeToolbar {...props({ visibleCount: 12, loadedCount: 300, onFitWholeTree: vi.fn() })} />,
      { locale: "en" }
    );

    expect(screen.getByTestId("tree-node-count")).toHaveTextContent("12 people shown");
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });
});

import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TreeRootBanner } from "@/components/tree/tree-root-banner";
import { node } from "../setup/tree-fixtures";

/**
 * **"Đang mở Chi Nhất, từ cụ …"**
 *
 * <p>Máy chủ chọn gốc theo vai + phạm vi chi khi `rootId` vắng mặt. Hành vi ấy đúng, nhưng nó im
 * lặng: một Trưởng chi mở phả đồ, thấy một cụ đời 2, và không có gì giải thích vì sao lại là cụ
 * ấy. Cùng chỗ trống đó cũng là lý do người vừa đăng nhập cảm thấy "gia phả co lại" — khách thấy
 * cây từ Thuỷ tổ, thành viên thấy cây từ gốc ngành mình.</p>
 */

const rootNode = node("p-to", 0, {
  person: {
    id: "p-to",
    displayName: "Nguyễn Phúc Thiện",
    isAlive: false,
    generation: 2,
    primaryBranch: { id: "b1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
  },
});

describe("dải ngữ cảnh gốc", () => {
  it("nói rõ CHI nào và CỤ nào — đủ để trả lời câu 'vì sao lại là cụ ấy'", () => {
    renderWithProviders(<TreeRootBanner rootNode={rootNode} />);
    const banner = screen.getByTestId("tree-root-banner");
    expect(banner).toHaveTextContent("Đang mở Chi Nhất, từ cụ Nguyễn Phúc Thiện");
    expect(banner).toHaveTextContent("Đời 2");
    // Thuộc tính máy đọc được, để e2e không phải dò chuỗi tiếng Việt.
    expect(banner).toHaveAttribute("data-branch", "Chi Nhất");
  });

  it("thiếu chi thì câu RÚT NGẮN, không dựng chỗ giữ chỗ", () => {
    const noBranch = node("p-to", 0, {
      person: { id: "p-to", displayName: "Nguyễn Phúc Thiện", isAlive: false, generation: 1 },
    });
    renderWithProviders(<TreeRootBanner rootNode={noBranch} />);
    const banner = screen.getByTestId("tree-root-banner");
    expect(banner).toHaveTextContent("Đang mở phả đồ từ cụ Nguyễn Phúc Thiện");
    // Một "Chi —" đọc ra như dữ liệu hỏng, và nói sai: chi không thiếu, nó không áp dụng.
    expect(banner.textContent).not.toContain("—");
    expect(banner).not.toHaveAttribute("data-branch");
  });

  it("chưa có dữ liệu thì không vẽ gì — không có dải rỗng nhấp nháy lúc tải", () => {
    const { container } = renderWithProviders(<TreeRootBanner />);
    expect(container.querySelector('[data-testid="tree-root-banner"]')).toBeNull();
  });

  it("đặt lối đổi chi ngay cạnh câu trả lời, và chỉ khi có chỗ để đi tới", async () => {
    const onChangeRoot = vi.fn();
    const { user } = renderWithProviders(
      <TreeRootBanner rootNode={rootNode} onChangeRoot={onChangeRoot} />
    );
    await user.click(screen.getByRole("button", { name: "Đổi chi khác" }));
    expect(onChangeRoot).toHaveBeenCalledTimes(1);
  });

  it("không có lối đổi chi thì không vẽ nút bấm vào không xảy ra gì", () => {
    renderWithProviders(<TreeRootBanner rootNode={rootNode} />);
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("song ngữ, không lọt khoá i18n", () => {
    renderWithProviders(<TreeRootBanner rootNode={rootNode} />, { locale: "en" });
    expect(screen.getByTestId("tree-root-banner")).toHaveTextContent(
      "Showing Chi Nhất, from Nguyễn Phúc Thiện"
    );
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });
});

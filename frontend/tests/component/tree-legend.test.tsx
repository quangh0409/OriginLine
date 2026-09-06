import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TreeLegend } from "@/components/tree/tree-legend";
import { TREE_STROKES } from "@/lib/tree/to-flow-elements";

/**
 * B4 — chú giải phải phủ đủ quy ước nét mới của phả đồ, song ngữ VI–EN, mà KHÔNG phình thành một
 * bảng tra chiếm nửa canvas điện thoại.
 */

function legendCard(): HTMLElement {
  return screen.getByTestId("tree-legend");
}

function swatchFor(label: string): SVGLineElement {
  const row = screen.getByText(label).closest("li");
  if (!row) throw new Error(`Không tìm thấy dòng chú giải cho "${label}"`);
  const line = row.querySelector("line");
  if (!line) throw new Error(`Dòng "${label}" không có mẫu nét nào`);
  return line as SVGLineElement;
}

describe("<TreeLegend> — quy ước nét", () => {
  it("luôn hiển thị bốn quy ước hay dùng nhất", () => {
    renderWithProviders(<TreeLegend />);

    expect(screen.getByText("Con đẻ")).toBeInTheDocument();
    expect(screen.getByText("Con nuôi")).toBeInTheDocument();
    expect(screen.getByText("Vợ chồng")).toBeInTheDocument();
    // Còn sống / đã khuất đi chung một dòng để nhường chỗ cho các quy ước nét.
    expect(legendCard().textContent).toContain("Người còn sống · Người đã khuất");
  });

  /**
   * Chú giải nằm ĐÈ lên phả đồ ở góc dưới-trái. Tám dòng quy ước đổ hết ra màn hình Pixel 5 thì
   * thẻ chú giải cao gần nửa canvas — đúng thứ mà bản trước đã phải chữa. Bốn quy ước ít gặp hơn
   * chỉ mở ra khi được hỏi.
   */
  it("giấu các quy ước ít gặp cho tới khi người dùng hỏi tới", () => {
    renderWithProviders(<TreeLegend />);

    expect(screen.queryByText("Kế tự / thừa tự")).not.toBeInTheDocument();
    expect(screen.queryByText("Tuyệt tự")).not.toBeInTheDocument();
    expect(screen.queryByText("Hôn phối đã kết thúc")).not.toBeInTheDocument();
    expect(screen.queryByText("Quan hệ vẽ vòng")).not.toBeInTheDocument();
  });

  it("mở ra đủ quy ước đặc thù khi bấm, và thu lại được", async () => {
    const { user } = renderWithProviders(<TreeLegend />);

    const toggle = screen.getByRole("button", { name: /Xem đủ quy ước/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await user.click(toggle);

    expect(screen.getByText("Hôn phối đã kết thúc")).toBeInTheDocument();
    expect(screen.getByText("Kế tự / thừa tự")).toBeInTheDocument();
    expect(screen.getByText("Tuyệt tự")).toBeInTheDocument();
    expect(screen.getByText("Quan hệ vẽ vòng")).toBeInTheDocument();

    const collapse = screen.getByRole("button", { name: /Thu gọn chú giải/ });
    expect(collapse).toHaveAttribute("aria-expanded", "true");
    await user.click(collapse);
    expect(screen.queryByText("Kế tự / thừa tự")).not.toBeInTheDocument();
  });

  /**
   * Mẫu nét trong chú giải được vẽ bằng CHÍNH bảng quy ước mà canvas dùng. Nếu chú giải tự dựng
   * lại mẫu bằng viền CSS thì hai bên trôi khỏi nhau: đổi màu đường huyết thống trên canvas mà
   * chú giải vẫn khoe màu cũ, và người đọc phả tin vào chú giải.
   */
  it("vẽ mẫu nét bằng đúng quy ước mà canvas dùng", async () => {
    const { user } = renderWithProviders(<TreeLegend />);

    expect(swatchFor("Con đẻ").style.stroke).toBe(TREE_STROKES.bioChild.stroke);
    expect(swatchFor("Con đẻ").style.strokeDasharray).toBe("");
    expect(swatchFor("Con nuôi").style.strokeDasharray.replace(/,/g, "")).toBe(
      TREE_STROKES.adoptedChild.strokeDasharray
    );
    expect(swatchFor("Vợ chồng").style.stroke).toBe(TREE_STROKES.marriage.stroke);

    await user.click(screen.getByRole("button", { name: /Xem đủ quy ước/ }));

    // Kế tự là dòng cha–con trên danh nghĩa: nét LIỀN, mảnh hơn, màu đỏ trầm.
    expect(swatchFor("Kế tự / thừa tự").style.stroke).toBe(TREE_STROKES.heir.stroke);
    expect(swatchFor("Kế tự / thừa tự").style.strokeDasharray).toBe("");
    // Hôn phối đã kết thúc: vẫn màu hôn phối, chỉ đổi sang nét đứt.
    expect(swatchFor("Hôn phối đã kết thúc").style.stroke).toBe(TREE_STROKES.marriage.stroke);
    expect(swatchFor("Hôn phối đã kết thúc").style.strokeDasharray).not.toBe("");
  });

  it("vẽ tuyệt tự bằng một gạch NGẮN hơn hẳn các mẫu nét khác", async () => {
    const { user } = renderWithProviders(<TreeLegend />);
    await user.click(screen.getByRole("button", { name: /Xem đủ quy ước/ }));

    const lineageEnd = swatchFor("Tuyệt tự");
    const bioChild = swatchFor("Con đẻ");
    const length = (line: SVGLineElement) =>
      Number(line.getAttribute("x2")) - Number(line.getAttribute("x1"));

    expect(length(lineageEnd)).toBeLessThan(length(bioChild) / 2);
  });

  /**
   * BUG WATCH — chú giải nuốt thao tác chạm.
   *
   * Thẻ chú giải phủ lên góc dưới-trái của phả đồ. Nếu nó nhận sự kiện chuột/chạm thì mọi nhân
   * khẩu lọt vào góc đó đều bấm không được. Chỉ nút mở/thu được phép nhận sự kiện, vì đó là thứ
   * người ta CẦN bấm.
   */
  it("cho thao tác chạm xuyên qua thân chú giải, trừ đúng nút mở/thu", () => {
    renderWithProviders(<TreeLegend />);

    expect(legendCard().className).toContain("!pointer-events-none");
    expect(screen.getByRole("button", { name: /Xem đủ quy ước/ }).className).toContain(
      "pointer-events-auto"
    );
  });

  it("không nói gì về dữ liệu bị ẩn theo phân tầng riêng tư", () => {
    const { container } = renderWithProviders(<TreeLegend />);
    expect(container.textContent).not.toMatch(/riêng tư|bị ẩn|T[123]\b/i);
  });
});

describe("<TreeLegend> — song ngữ", () => {
  it("hiển thị đầy đủ bằng tiếng Anh, không sót khoá dịch nào", async () => {
    const { user, container } = renderWithProviders(<TreeLegend />, { locale: "en" });

    expect(screen.getByText("Biological child")).toBeInTheDocument();
    expect(screen.getByText("Adopted child (con nuôi)")).toBeInTheDocument();
    expect(screen.getByText("Spouses")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Show all marks/ }));

    expect(screen.getByText("Marriage ended")).toBeInTheDocument();
    expect(screen.getByText("Heirship (kế tự)")).toBeInTheDocument();
    expect(screen.getByText("Line ended (tuyệt tự)")).toBeInTheDocument();
    expect(screen.getByText("Re-routed relationship")).toBeInTheDocument();
    expect(container.textContent).not.toContain("MISSING_MESSAGE");
  });

  it("giữ nguyên thuật ngữ gia phả trong bản tiếng Việt, dấu thanh đầy đủ", async () => {
    const { user, container } = renderWithProviders(<TreeLegend />, { locale: "vi" });
    await user.click(screen.getByRole("button", { name: /Xem đủ quy ước/ }));

    for (const term of ["Con nuôi", "Kế tự", "Tuyệt tự", "Hôn phối"]) {
      expect(container.textContent).toContain(term);
    }
    expect(container.textContent).not.toContain("MISSING_MESSAGE");
  });
});

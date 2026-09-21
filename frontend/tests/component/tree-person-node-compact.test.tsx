import { describe, expect, it, vi } from "vitest";
import { ReactFlowProvider } from "@xyflow/react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { PersonNode, type PersonFlowNode } from "@/components/tree/person-node";
import {
  TreeCanvasContext,
  type TreeCanvasContextValue,
} from "@/components/tree/tree-canvas-context";
import { NODE_HEIGHT, NODE_WIDTH } from "@/lib/tree/layout-constants";
import { node } from "../setup/tree-fixtures";
import type { TreeNode } from "@/types/api";

/**
 * **Thẻ nhân khẩu sau khi thu gọn** — ba thứ mới, mỗi thứ chữa một câu than có thật.
 *
 * <ol>
 *   <li><b>Hẹp hơn.</b> 208 → 160px. Cỡ thẻ không còn chép tay trong component; nó đến từ
 *       {@code layout-constants}, nên bố cục và phần vẽ không thể lệch nhau.</li>
 *   <li><b>Hai mức chi tiết.</b> Thu nhỏ thì chỉ còn cái tên, ở cỡ chữ LỚN HƠN — ở mức phóng 0.5
 *       đó là chênh lệch giữa "đoán được tên" và "một đám chấm". Phóng to thì hiện đủ.</li>
 *   <li><b>Số người con đang ẩn</b> hiện ngay trong vòng tròn của nút bung; câu đầy đủ nằm ở
 *       {@code title} và {@code data-hidden-children}, còn TÊN GỌI của nút thì giữ nguyên câu cũ —
 *       bốn tệp e2e đang tìm nút bằng đúng câu ấy, và Playwright so tên khớp trọn.</li>
 * </ol>
 */

function renderNode(
  treeNode: TreeNode,
  context: Partial<TreeCanvasContextValue> = {},
  { locale = "vi", hasLoadableChildren = false }: { locale?: "vi" | "en"; hasLoadableChildren?: boolean } = {}
) {
  const value: TreeCanvasContextValue = {
    rootId: "goc-khac",
    expandedIds: new Set(),
    loadingIds: new Set(),
    toggle: vi.fn(),
    selfPersonId: null,
    focusId: null,
    detail: "full",
    ...context,
  };

  const props = {
    id: treeNode.id,
    data: { treeNode, hasLoadableChildren },
    selected: false,
    type: "person",
    dragging: false,
    zIndex: 0,
    isConnectable: false,
    positionAbsoluteX: 0,
    positionAbsoluteY: 0,
  } as unknown as import("@xyflow/react").NodeProps<PersonFlowNode>;

  return renderWithProviders(
    <ReactFlowProvider>
      <TreeCanvasContext.Provider value={value}>
        <PersonNode {...props} />
      </TreeCanvasContext.Provider>
    </ReactFlowProvider>,
    { locale }
  );
}

describe("cỡ thẻ", () => {
  it("vẽ đúng cỡ mà các thuật toán bố cục đã tính chỗ cho", () => {
    renderNode(node("p-1", 0));
    const card = screen.getByTestId("person-node");
    expect(card.style.width).toBe(`${NODE_WIDTH}px`);
    expect(card.style.minHeight).toBe(`${NODE_HEIGHT}px`);
  });

  it("thẻ đã hẹp lại thật — đây là con số chủ dự án cảm thấy", () => {
    // Ghim con số, không chỉ ghim "bằng hằng số": nếu ai đó nới hằng số trở lại 208 thì ca này
    // phải đỏ, kèm phép tính ở layout-constants.ts để đọc lại vì sao là 160.
    expect(NODE_WIDTH).toBe(160);
  });
});

describe("hai mức chi tiết", () => {
  const person = node("p-1", 0, {
    person: {
      id: "p-1",
      displayName: "Nguyễn Văn Trường",
      isAlive: false,
      generation: 5,
      birthYear: 1902,
      deathYear: 1971,
      primaryBranch: { id: "b1", name: "Chi Nhất", path: "root.chi_nhat", region: "BAC" },
    },
  });

  it('ở mức "full" hiện đủ tên · đời · chi · khoảng đời', () => {
    renderNode(person, { detail: "full" });
    expect(screen.getByTestId("person-node-name")).toHaveTextContent("Nguyễn Văn Trường");
    expect(screen.getByText(/Đời 5 · Chi Nhất/)).toBeInTheDocument();
    expect(screen.getByText("1902 – 1971")).toBeInTheDocument();
  });

  it('ở mức "compact" chỉ còn cái tên — không phải vì thiếu dữ liệu mà vì đang thu nhỏ', () => {
    renderNode(person, { detail: "compact" });
    expect(screen.getByTestId("person-node-name")).toHaveTextContent("Nguyễn Văn Trường");
    expect(screen.queryByText(/Đời 5 · Chi Nhất/)).toBeNull();
    expect(screen.queryByText("1902 – 1971")).toBeNull();
  });

  /**
   * Bất biến riêng tư: phép ẩn ở mức `compact` chỉ nhìn MỨC PHÓNG, không nhìn dữ liệu của ai.
   * Nên hai tấm thẻ — một người còn sống chưa chia sẻ ngày, một cụ đã khuất chưa ai ghi ngày —
   * phải rút gọn y hệt nhau. Nếu một trong hai còn sót lại một dòng chữ thì chính phép thu gọn
   * trở thành kênh trả lời "hồ sơ này có dữ liệu hay không".
   */
  it("rút gọn giống hệt nhau cho người còn sống và người đã khuất", () => {
    const { unmount } = renderNode(
      node("p-song", 0, {
        person: { id: "p-song", displayName: "Người Sống", isAlive: true, generation: 8 },
      }),
      { detail: "compact" }
    );
    expect(screen.queryByTestId("person-node-dates-unshared")).toBeNull();
    unmount();

    renderNode(
      node("p-khuat", 0, {
        person: { id: "p-khuat", displayName: "Người Khuất", isAlive: false, generation: 2 },
      }),
      { detail: "compact" }
    );
    expect(screen.queryByTestId("person-node-dates-unrecorded")).toBeNull();
  });

  it('tên dùng cỡ chữ LỚN HƠN khi thu nhỏ, không nhỏ hơn', () => {
    const { unmount } = renderNode(node("p-1", 0), { detail: "full" });
    expect(screen.getByTestId("person-node-name").className).toContain("text-the-ten");
    unmount();

    renderNode(node("p-1", 0), { detail: "compact" });
    // `text-dan` = 17px, so với `text-the-ten` = 13px. Ở mức phóng 0.5 đó là 8,5px thật thay vì
    // 6,5px — chênh lệch quyết định việc có đọc ra tên hay không.
    expect(screen.getByTestId("person-node-name").className).toContain("text-dan");
  });
});

describe("nút bung nhánh nói rõ còn bao nhiêu người ở dưới", () => {
  const withChildren = (childCount: number | null) =>
    node("p-cha", 0, { childCount, hasMoreDescendants: true });

  it("in số con đang ẩn lên nút, và câu đầy đủ vào phần mô tả", () => {
    renderNode(withChildren(3), {}, { hasLoadableChildren: true });
    const toggle = screen.getByTestId("tree-node-toggle");
    expect(toggle).toHaveAttribute("data-hidden-children", "3");
    expect(screen.getByTestId("tree-node-toggle-knob")).toHaveTextContent("3");
    expect(toggle).toHaveAttribute("title", "Mở rộng nhánh này — 3 người con");
  });

  /**
   * TÊN GỌI của nút là một hợp đồng, không phải một chuỗi.
   *
   * Bốn tệp e2e tìm nút này bằng `getByRole("button", { name: "Mở rộng nhánh này" })`, và
   * Playwright so tên **khớp trọn**. Nhét số con vào tên là làm cả bốn ngừng tìm thấy nút, với
   * một thông báo lỗi nói về "không tìm thấy phần tử" chứ không nói gì về nhãn. Số con đi đường
   * khác — chữ số nhìn thấy, `title`, và `data-hidden-children`.
   */
  it("giữ TÊN GỌI ổn định, không nhét số con vào đó", () => {
    renderNode(withChildren(3), {}, { hasLoadableChildren: true });
    expect(screen.getByTestId("tree-node-toggle")).toHaveAccessibleName("Mở rộng nhánh này");
  });

  it("máy chủ không nói số con thì giữ dấu +, KHÔNG bịa số 0", () => {
    renderNode(withChildren(null), {}, { hasLoadableChildren: true });
    const toggle = screen.getByTestId("tree-node-toggle");
    expect(toggle).not.toHaveAttribute("data-hidden-children");
    expect(toggle).toHaveAccessibleName("Mở rộng nhánh này");
    expect(screen.getByTestId("tree-node-toggle-knob")).not.toHaveTextContent("0");
  });

  it("số quá lớn thì quay về dấu + — hai chữ số trong vòng tròn 24px đọc ra vô nghĩa", () => {
    renderNode(withChildren(47), {}, { hasLoadableChildren: true });
    // Con số đầy đủ vẫn còn, ở chỗ nó đọc được: phần mô tả và thuộc tính dữ liệu.
    expect(screen.getByTestId("tree-node-toggle")).toHaveAttribute(
      "title",
      "Mở rộng nhánh này — 47 người con"
    );
    expect(screen.getByTestId("tree-node-toggle")).toHaveAttribute("data-hidden-children", "47");
    expect(screen.getByTestId("tree-node-toggle-knob")).not.toHaveTextContent("47");
  });

  it("nhánh đang mở thì không in số con — số ấy không còn đang ẩn", () => {
    renderNode(
      withChildren(3),
      { expandedIds: new Set(["p-cha"]) },
      { hasLoadableChildren: true }
    );
    const toggle = screen.getByTestId("tree-node-toggle");
    // `data-hidden-children` đọc là "còn bao nhiêu người ĐANG BỊ GIẤU", nên nhánh đã mở thì nó
    // vắng mặt — không phải "3 người con" mà là "không giấu ai".
    expect(toggle).not.toHaveAttribute("data-hidden-children");
    expect(toggle).toHaveAccessibleName("Thu gọn nhánh này");
    expect(screen.getByTestId("tree-node-toggle-knob")).not.toHaveTextContent("3");
  });
});

describe("tô đậm node của chính người dùng", () => {
  it("không chỉ đổi màu viền — còn có chip CHỮ, đọc được kể cả khi không phân biệt được màu", () => {
    renderNode(node("p-toi", 0), { selfPersonId: "p-toi" });
    const card = screen.getByTestId("person-node");
    expect(card).toHaveAttribute("data-self", "true");
    expect(card.className).toContain("border-primary");
    expect(screen.getByTestId("person-node-self")).toHaveTextContent("Tôi");
  });

  it("song ngữ, và không lọt khoá i18n ra màn hình", () => {
    renderNode(node("p-toi", 0), { selfPersonId: "p-toi" }, { locale: "en" });
    expect(screen.getByTestId("person-node-self")).toHaveTextContent("Me");
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
  });

  it("người khác thì không mang dấu hiệu nào của 'chính tôi'", () => {
    renderNode(node("p-nguoi-khac", 0), { selfPersonId: "p-toi" });
    expect(screen.getByTestId("person-node")).not.toHaveAttribute("data-self");
    expect(screen.queryByTestId("person-node-self")).toBeNull();
  });
});

describe("người vừa được nhảy tới", () => {
  it("mang vòng ngoài riêng, tách khỏi viền 'chính tôi' để hai dấu hiệu cùng đọc được", () => {
    renderNode(node("p-tim", 0), { focusId: "p-tim", selfPersonId: "p-tim" });
    const card = screen.getByTestId("person-node");
    expect(card).toHaveAttribute("data-focused", "true");
    expect(card).toHaveAttribute("data-self", "true");
    expect(card.className).toContain("outline-primary");
    expect(card.className).toContain("border-primary");
  });
});

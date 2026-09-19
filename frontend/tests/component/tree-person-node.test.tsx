import { describe, expect, it } from "vitest";
import { ReactFlowProvider } from "@xyflow/react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { PersonNode, type PersonFlowNode } from "@/components/tree/person-node";
import type { TreeNode } from "@/types/api";

/**
 * Thẻ nhân khẩu trên phả đồ — **ô ngày trống phải trông CÓ CHỦ ĐÍCH**.
 *
 * Sau mô hình đồng thuận V8, năm sinh của người còn sống nằm trong nhóm
 * `birthDetailAndPhoto` và nhóm ấy mặc định đóng, kể cả với người cùng chi.
 * Hệ quả: thẻ của người còn sống trống dòng ngày rất thường xuyên. Trước đợt
 * sửa này nó trông y hệt thẻ của một cụ tổ mà **chưa ai ghi** năm sinh — hai
 * chuyện dẫn tới hai hành động hoàn toàn khác nhau:
 *
 *  - *chưa ai ghi* → một lời mời bổ sung, việc của con cháu;
 *  - *chưa chia sẻ* → chuyện bình thường, không cần làm gì.
 *
 * Ranh giới an toàn, y như `privacy-tier-notice`: câu chữ chỉ được phụ thuộc
 * **người này còn sống hay đã khuất**. Nó không đếm trường bị lọc, không nêu
 * tên trường, và **không** nói mức chia sẻ chủ thể đã đặt.
 */

function treeNode(overrides: Partial<TreeNode["person"]> = {}): TreeNode {
  return {
    id: overrides.id ?? "p-x",
    person: {
      id: overrides.id ?? "p-x",
      displayName: overrides.displayName ?? "Nguyễn Văn A",
      isAlive: overrides.isAlive ?? false,
      generation: overrides.generation ?? 5,
      birthYear: overrides.birthYear,
      deathYear: overrides.deathYear,
    },
    depth: 0,
    parentIds: [],
    spouseIds: [],
    hasMoreDescendants: false,
    badges: [],
  };
}

function renderNode(node: TreeNode, locale: "vi" | "en" = "vi") {
  const props = {
    id: node.id,
    data: { treeNode: node, hasLoadableChildren: false },
    // React Flow truyền thêm nhiều trường mà thành phần không đọc tới.
    selected: false,
    type: "person",
    dragging: false,
    zIndex: 0,
    isConnectable: false,
    positionAbsoluteX: 0,
    positionAbsoluteY: 0,
  } as unknown as Parameters<typeof PersonNode>[0] & { data: { treeNode: TreeNode } };

  return renderWithProviders(
    <ReactFlowProvider>
      <PersonNode {...(props as unknown as import("@xyflow/react").NodeProps<PersonFlowNode>)} />
    </ReactFlowProvider>,
    { locale }
  );
}

describe("ô ngày có dữ liệu", () => {
  it("người đã khuất hiện trọn khoảng đời", () => {
    renderNode(treeNode({ birthYear: 1780, deathYear: 1852 }));
    expect(screen.getByText("1780 – 1852")).toBeInTheDocument();
  });

  it("người còn sống đã chia sẻ thì hiện năm sinh, không có ô trạng thái nào", () => {
    const { container } = renderNode(treeNode({ isAlive: true, birthYear: 1985 }));

    expect(screen.getByText("1985 –")).toBeInTheDocument();
    expect(container.querySelector('[data-testid="person-node-dates-unshared"]')).toBeNull();
    expect(container.querySelector('[data-testid="person-node-dates-unrecorded"]')).toBeNull();
  });
});

describe("hai ô trống khác nhau, và người xem đọc ra được là khác nhau", () => {
  it("người đã khuất thiếu ngày đọc ra một lời mời bổ sung", () => {
    const { container } = renderNode(treeNode({ isAlive: false }));

    expect(screen.getByText("Chưa ghi năm sinh")).toBeInTheDocument();
    expect(container.querySelector('[data-testid="person-node-dates-unshared"]')).toBeNull();
  });

  it("người còn sống thiếu ngày đọc ra một trạng thái bình thường", () => {
    const { container } = renderNode(treeNode({ isAlive: true }));

    expect(screen.getByText("Chưa chia sẻ ngày sinh")).toBeInTheDocument();
    expect(container.querySelector('[data-testid="person-node-dates-unrecorded"]')).toBeNull();
  });

  it("hai câu KHÁC nhau — nếu trùng thì cả phép phân biệt là vô nghĩa", () => {
    const deceased = renderNode(treeNode({ id: "a", isAlive: false }));
    const deceasedText = deceased.container.textContent ?? "";
    deceased.unmount();

    const living = renderNode(treeNode({ id: "b", isAlive: true }));
    const livingText = living.container.textContent ?? "";

    expect(deceasedText).not.toBe(livingText);
  });
});

describe("ô trống không rò rỉ gì về chính hồ sơ", () => {
  const dateLine = (container: HTMLElement) =>
    (container.querySelector('[data-testid="person-node-dates-unshared"]') ??
      container.querySelector('[data-testid="person-node-dates-unrecorded"]'))!;

  it("hai người còn sống khác nhau đọc ra CÙNG một câu, kể cả phần chú thích", () => {
    // Bất biến quan trọng nhất, mượn nguyên từ `privacy-tier-notice`: một người
    // thật sự chưa ai ghi ngày và một người có ngày nhưng đóng chia sẻ phải đọc
    // ra y hệt. Khác nhau là câu chữ tự nó trả lời "hồ sơ này có dữ liệu không".
    const a = renderNode(treeNode({ id: "a", isAlive: true, displayName: "Người Một" }));
    const aNode = dateLine(a.container);
    const aText = aNode.textContent;
    const aHint = aNode.getAttribute("title");
    a.unmount();

    const b = renderNode(
      treeNode({ id: "b", isAlive: true, displayName: "Người Hai", generation: 9 })
    );
    const bNode = dateLine(b.container);

    expect(bNode.textContent).toBe(aText);
    expect(bNode.getAttribute("title")).toBe(aHint);
  });

  it("không đếm trường nào và không nêu tên mức chia sẻ", () => {
    const { container } = renderNode(treeNode({ isAlive: true }));
    const node = dateLine(container);
    const text = `${node.textContent ?? ""} ${node.getAttribute("title") ?? ""}`;

    // Đếm đã là tiết lộ; tên mức ("Riêng tư"/"Chỉ trong chi") là tiết lộ lựa
    // chọn của chính chủ.
    expect(text).not.toMatch(/\d/);
    for (const banned of [/riêng tư/i, /hạn chế/i, /bị ẩn/i, /không có quyền/i, /RESTRICTED/i, /PRIVATE/i, /chi \/ ngành/i]) {
      expect(text, `câu "${text}" khớp mẫu cấm ${banned}`).not.toMatch(banned);
    }
  });

  it("không mượn hình dạng của một ô bị che: không khoá, không ba chấm, không N/A", () => {
    const { container } = renderNode(treeNode({ isAlive: true }));
    const text = container.textContent ?? "";

    expect(text).not.toMatch(/🔒|🔓/);
    expect(text).not.toMatch(/•\s*•\s*•|\*\*\*/);
    expect(text).not.toMatch(/\bN\/A\b/i);
  });
});

describe("song ngữ", () => {
  it("cả hai trạng thái đều có bản tiếng Anh", () => {
    const living = renderNode(treeNode({ isAlive: true }), "en");
    expect(living.container.textContent).toContain("Birth date not shared");
    expect(living.container.textContent).not.toContain("MISSING_MESSAGE");
    living.unmount();

    const deceased = renderNode(treeNode({ isAlive: false }), "en");
    expect(deceased.container.textContent).toContain("Birth year not recorded");
    expect(deceased.container.textContent).not.toContain("MISSING_MESSAGE");
  });
});

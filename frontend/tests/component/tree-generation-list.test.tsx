import { describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TreeGenerationList } from "@/components/tree/tree-generation-list";
import { threeGenerationFamily } from "../setup/tree-fixtures";
import type { TreeEdge, TreeNode } from "@/types/api";

/**
 * **Danh sách theo đời** — kiểu xem cho điện thoại.
 *
 * <p>Bài toán, nói bằng số đo: một đời tám gia đình rộng 3.296px sau khi đã thu thẻ; ô cửa của
 * Pixel 5 rộng 390px. Kéo bức tranh ấy bằng hai ngón là thao tác khó với người lớn tuổi, và phần
 * lớn người trong họ ở xa thì dùng điện thoại. Danh sách này đi **từng đời một** thay vì cố vẽ
 * lại cây.</p>
 *
 * <p>Ba điều được ghim ở đây, vì cả ba hỏng một cách im lặng:</p>
 *
 * <ol>
 *   <li>bấm xuống một người thì danh sách phải xuống ĐỜI CỦA NGƯỜI ẤY, và đường đã đi phải quay
 *       lên được — trên điện thoại đó là lối quay lui duy nhất;</li>
 *   <li>đi xuống phải gọi `expand()`, nếu không thì danh sách chỉ đi được trong phần đã tải và
 *       cụt ở đời thứ ba mà không báo gì;</li>
 *   <li>một hàng có HAI nghĩa (mở hồ sơ / xuống đời) thì phải là HAI nút, mỗi nút một nhãn.</li>
 * </ol>
 */

function childrenFrom(nodes: TreeNode[], edges: TreeEdge[]) {
  return (personId: string): TreeNode[] =>
    edges
      .filter(
        (e) =>
          e.source === personId &&
          (e.relType === "PARENT_BIO" || e.relType === "PARENT_ADOPT")
      )
      .map((e) => nodes.find((n) => n.id === e.target))
      .filter((n): n is TreeNode => Boolean(n));
}

function renderList(overrides: Partial<Parameters<typeof TreeGenerationList>[0]> = {}) {
  const { nodes, edges, nodesById } = threeGenerationFamily();
  const props = {
    rootId: "root",
    nodesById,
    childrenOf: childrenFrom(nodes, edges),
    expand: vi.fn(),
    loadingIds: new Set<string>(),
    focusPath: null,
    selfPersonId: null,
    onSelectPerson: vi.fn(),
    ...overrides,
  };
  return { ...renderWithProviders(<TreeGenerationList {...props} />), props };
}

describe("đi xuống từng đời", () => {
  it("mở ra ở gốc và liệt kê đúng con cái trực tiếp của gốc", () => {
    renderList();
    const rows = screen.getAllByTestId("tree-generation-list-row");
    // Đúng ba người con trực tiếp của gốc, không hơn: con nuôi cũng là con (BA v2 §12), và
    // vợ/chồng của gốc KHÔNG phải một hàng trong danh sách đời dưới.
    expect(rows).toHaveLength(3);
    const text = rows.map((r) => r.textContent ?? "").join(" ");
    expect(text).toContain("adopted");
    expect(text).toContain("sonA");
    expect(text).toContain("sonB");
    expect(text).not.toContain("rootWife");
    // Thứ tự là việc của `useTreeCanvas.childrenOf` (sắp theo tên tiếng Việt), không phải của
    // component — nên ca này cố ý không ghim thứ tự, để không ghim nhầm chỗ.
  });

  it("bấm 'Xuống đời' thì danh sách chuyển sang con cái của người ấy", async () => {
    const { user } = renderList();

    const sonARow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonA"))!;
    await user.click(within(sonARow).getByTestId("tree-generation-list-descend"));

    const rows = screen.getAllByTestId("tree-generation-list-row");
    expect(rows).toHaveLength(2);
    expect(rows.map((r) => r.textContent).join(" ")).toContain("grandA1");
    expect(rows.map((r) => r.textContent).join(" ")).toContain("grandA2");
  });

  it("gọi expand() khi đi xuống, nên danh sách không bao giờ cụt ở phần đã tải sẵn", async () => {
    const expand = vi.fn();
    const { user } = renderList({ expand });

    const sonARow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonA"))!;
    await user.click(within(sonARow).getByTestId("tree-generation-list-descend"));

    expect(expand).toHaveBeenCalledWith("sonA");
  });

  it("đường đã đi quay lên được — lối lùi duy nhất trên điện thoại", async () => {
    const { user } = renderList();

    const sonARow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonA"))!;
    await user.click(within(sonARow).getByTestId("tree-generation-list-descend"));
    expect(screen.getByText(/grandA1/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Người root/ }));
    expect(screen.queryByText(/grandA1/)).toBeNull();
    expect(screen.getAllByTestId("tree-generation-list-row")).toHaveLength(3);
  });

  it("mở sẵn ở đúng chỗ người đang xem khi đã biết đường tới họ", () => {
    renderList({ focusPath: ["root", "sonA"] });
    // Đang đứng ở sonA ⇒ danh sách hiện con của sonA, không phải con của gốc.
    expect(screen.getAllByTestId("tree-generation-list-row")).toHaveLength(2);
    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent("Người sonA");
  });
});

describe("một hàng, hai nghĩa, hai nút", () => {
  it("bấm vào hàng mở hồ sơ — cùng nghĩa với chạm vào một tấm thẻ trên canvas", async () => {
    const onSelectPerson = vi.fn();
    const { user } = renderList({ onSelectPerson });

    const sonBRow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonB"))!;
    await user.click(within(sonBRow).getByRole("button", { name: /Người sonB/ }));

    expect(onSelectPerson).toHaveBeenCalledWith("sonB");
  });

  it("người chưa có con thì KHÔNG có nút xuống đời — không dựng nút bấm vào không xảy ra gì", () => {
    renderList();
    const sonBRow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonB"))!;
    expect(within(sonBRow).queryByTestId("tree-generation-list-descend")).toBeNull();
  });

  it("nút xuống đời mang nhãn CHỮ, không chỉ một mũi tên", async () => {
    renderList();
    const sonARow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonA"))!;
    const descend = within(sonARow).getByTestId("tree-generation-list-descend");
    expect(descend).toHaveTextContent("Xuống đời");
    expect(descend).toHaveAccessibleName("Xem con cháu của Người sonA");
  });
});

describe("chính người dùng trong danh sách", () => {
  it("được đánh dấu bằng chữ, y như trên canvas", () => {
    renderList({ selfPersonId: "sonB" });
    const sonBRow = screen
      .getAllByTestId("tree-generation-list-row")
      .find((r) => r.textContent?.includes("sonB"))!;
    expect(sonBRow).toHaveAttribute("data-self", "true");
    expect(within(sonBRow).getByText("Tôi")).toBeInTheDocument();
  });
});

describe("song ngữ", () => {
  it("không lọt khoá i18n ra màn hình ở bản tiếng Anh", () => {
    renderWithProviders(
      <TreeGenerationList
        rootId="root"
        nodesById={threeGenerationFamily().nodesById}
        childrenOf={childrenFrom(
          threeGenerationFamily().nodes,
          threeGenerationFamily().edges
        )}
        expand={vi.fn()}
        loadingIds={new Set()}
        selfPersonId={null}
        onSelectPerson={vi.fn()}
      />,
      { locale: "en" }
    );
    expect(document.body.textContent).not.toContain("MISSING_MESSAGE");
    expect(screen.getByRole("button", { name: /See the descendants of/ })).toBeInTheDocument();
  });
});

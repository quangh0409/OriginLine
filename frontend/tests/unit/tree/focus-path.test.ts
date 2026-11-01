import { describe, expect, it } from "vitest";
import {
  buildParentMap,
  expansionForFocus,
  inLawBridgeIds,
  pathFromRootTo,
} from "@/lib/tree/focus-path";
import { computeVisibleSubgraph } from "@/lib/tree/visible-subgraph";
import { edge, node, threeGenerationFamily, toMaps } from "../../setup/tree-fixtures";
import type { TreeEdge, TreeNode } from "@/types/api";

/**
 * **Đường thẳng từ gốc tới người đang xem** — vế "gập nhánh mặc định" của checklist mục 5.
 *
 * <p>Phép tính này quyết định phả đồ mở ra rộng bao nhiêu, nên nó đáng được ghim ở tầng thuần
 * tuý: một lỗi ở đây không hiện ra thành lỗi, nó hiện ra thành *một màn hình rộng vài nghìn pixel*
 * hoặc *một người dùng không tìm thấy chính mình* — cả hai đều trông như "chắc là do dữ liệu".</p>
 */

describe("buildParentMap", () => {
  it("chỉ nhận cạnh huyết thống, bỏ cạnh hôn phối", () => {
    const { edges } = threeGenerationFamily();
    const parents = buildParentMap(edges);

    expect(parents.get("sonA")).toEqual(["root"]);
    // Vợ KHÔNG phải con của chồng. Trộn SPOUSE vào đây là dựng ra một "đời" giả.
    expect(parents.has("rootWife")).toBe(false);
    expect(parents.has("sonAWife")).toBe(false);
  });

  it("coi cạnh nhận nuôi ngang cạnh sinh thành", () => {
    const { edges } = threeGenerationFamily();
    expect(buildParentMap(edges).get("adopted")).toEqual(["root"]);
  });
});

describe("pathFromRootTo", () => {
  it("trả về đường có thứ tự từ gốc xuống, kể cả người ở tận đời cháu", () => {
    const { nodesById, edges } = threeGenerationFamily();
    expect(pathFromRootTo(nodesById, edges, "root", "grandA2")).toEqual([
      "root",
      "sonA",
      "grandA2",
    ]);
  });

  it("gốc trỏ vào chính nó là một đường dài đúng một người", () => {
    const { nodesById, edges } = threeGenerationFamily();
    expect(pathFromRootTo(nodesById, edges, "root", "root")).toEqual(["root"]);
  });

  it("người dâu nối về gốc QUA CHỒNG — không đổi gốc sang cô (đo trên hệ thật 24/09)", () => {
    const { nodesById, edges } = threeGenerationFamily();
    // Bản cũ trả `null` ở đây và <TreeCanvas> đổi gốc sang chính người dâu: phả đồ còn đúng hai
    // ô (cô và chồng). Tài khoản demo `thanhvien` là một người dâu đời 7, nên "Về chỗ tôi" đưa
    // mọi người thử nghiệm ra KHỎI dòng họ. Nay bước đầu được đi sang chồng.
    expect(pathFromRootTo(nodesById, edges, "root", "sonAWife")).toEqual([
      "root",
      "sonA",
      "sonAWife",
    ]);
  });

  it("chỉ bước ĐẦU được đi ngang qua hôn phối — người trong họ vẫn đi theo cha/mẹ", () => {
    const { nodesById, edges } = threeGenerationFamily();
    // grandA1 có cha trong projection ⇒ không được rẽ sang vợ/chồng dù có.
    expect(pathFromRootTo(nodesById, edges, "root", "grandA1")).toEqual([
      "root",
      "sonA",
      "grandA1",
    ]);
  });

  it("vẫn trả null khi người dâu có chồng nhưng chồng không nối được về gốc", () => {
    const nodes: TreeNode[] = [node("root", 0), node("x", 5), node("xWife", 5)];
    const edges: TreeEdge[] = [edge("e-x-w", "x", "xWife", "SPOUSE")];
    const { nodesById } = toMaps(nodes, edges);
    expect(pathFromRootTo(nodesById, edges, "root", "xWife")).toBeNull();
  });

  it("trả null cho id không có trong projection, không ném lỗi", () => {
    const { nodesById, edges } = threeGenerationFamily();
    expect(pathFromRootTo(nodesById, edges, "root", "khong-co-ai")).toBeNull();
    expect(pathFromRootTo(nodesById, edges, "khong-co-goc", "sonA")).toBeNull();
  });

  it("trả null cho chuỗi rỗng — trạng thái 'chưa biết gốc' của useTreeCanvas", () => {
    const { nodesById, edges } = threeGenerationFamily();
    expect(pathFromRootTo(nodesById, edges, "", "sonA")).toBeNull();
    expect(pathFromRootTo(nodesById, edges, "root", "")).toBeNull();
  });

  /**
   * Con nuôi có CẢ cha đẻ lẫn cha nuôi, nên phép đi ngược phải là BFS chứ không phải một vòng
   * `while` bám vào `parentIds[0]`. Chọn đường NGẮN NHẤT: đó là đường người đọc phả nhận ra.
   */
  it("chọn đường ngắn nhất khi một người có hai cha mẹ trong dữ liệu", () => {
    const nodes: TreeNode[] = [
      node("to", 0),
      node("con", 1, { parentIds: ["to"] }),
      node("chau", 2, { parentIds: ["con"] }),
      // `chat` vừa là con của `chau` (đường dài) vừa là con nuôi của `con` (đường ngắn).
      node("chat", 3, { parentIds: ["chau", "con"] }),
    ];
    const edges: TreeEdge[] = [
      edge("e1", "to", "con"),
      edge("e2", "con", "chau"),
      edge("e3", "chau", "chat"),
      edge("e4", "con", "chat", "PARENT_ADOPT"),
    ];
    const { nodesById } = toMaps(nodes, edges);

    expect(pathFromRootTo(nodesById, edges, "to", "chat")).toEqual(["to", "con", "chat"]);
  });

  it("không treo khi dữ liệu có vòng — phả sai vẫn phải mở được màn hình", () => {
    const nodes: TreeNode[] = [node("a", 0), node("b", 1), node("c", 2)];
    const edges: TreeEdge[] = [
      edge("e1", "a", "b"),
      edge("e2", "b", "c"),
      // Cạnh phi lý: cháu làm cha của ông. Không được phép có trong dữ liệu thật, nhưng một
      // phép duyệt không chống vòng sẽ treo hẳn tab trình duyệt thay vì báo gì đó.
      edge("e3", "c", "a"),
    ];
    const { nodesById } = toMaps(nodes, edges);

    expect(pathFromRootTo(nodesById, edges, "a", "c")).toEqual(["a", "b", "c"]);
  });

  it("bỏ qua mắt xích không nằm trong projection — cha mẹ bị lọc riêng tư", () => {
    // `cha` có cạnh trỏ tới `con` nhưng KHÔNG có node: đúng hình dạng mà bộ lọc riêng tư để lại
    // nếu ai đó đi theo `parentIds` thay vì đi theo cạnh có đủ hai đầu.
    const nodes: TreeNode[] = [node("to", 0), node("con", 2, { parentIds: ["cha"] })];
    const edges: TreeEdge[] = [edge("e1", "cha", "con")];
    const { nodesById } = toMaps(nodes, edges);

    expect(pathFromRootTo(nodesById, edges, "to", "con")).toBeNull();
  });
});

describe("expansionForFocus", () => {
  it("trả về đúng tập phải mở, gồm cả chính người ấy để hiện con cái trực tiếp", () => {
    const { nodesById, edges } = threeGenerationFamily();
    const expansion = expansionForFocus(nodesById, edges, "root", "sonA");
    expect([...expansion].sort()).toEqual(["root", "sonA"]);
  });

  it("trả tập rỗng thay vì ném lỗi khi không nối được", () => {
    const { nodesById, edges } = threeGenerationFamily();
    expect(expansionForFocus(nodesById, edges, "root", "khong-co-ai").size).toBe(0);
  });

  it("người dâu: mở đường xuống CHỒNG, và cô hiện ra cạnh chồng", () => {
    const { nodesById, edgesById, edges } = threeGenerationFamily();
    const expanded = expansionForFocus(nodesById, edges, "root", "sonAWife");
    expect([...expanded].sort()).toEqual(["root", "sonA", "sonAWife"]);
    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", expanded);
    expect(visible.nodes.map((n) => n.id)).toContain("sonAWife");
  });

  /**
   * Đây là ca nói lên toàn bộ mục đích của việc này: mở đúng ĐƯỜNG, **không** mở các nhánh bên
   * cạnh. Nếu phép mở rộng làm lộ luôn anh em của người trên đường thì phả đồ lại rộng ra đúng
   * như cũ, chỉ khác là nay có thêm mã để bảo trì.
   */
  it("mở đường mà KHÔNG bung các nhánh anh em bên cạnh", () => {
    const { nodesById, edgesById, edges } = threeGenerationFamily();
    const expanded = expansionForFocus(nodesById, edges, "root", "grandA1");
    expect([...expanded].sort()).toEqual(["grandA1", "root", "sonA"]);

    const visible = computeVisibleSubgraph(nodesById, edgesById, "root", expanded);
    const ids = visible.nodes.map((n) => n.id).sort();

    // Có: đường xuống người ấy, vợ/chồng của những người trên đường (vợ chồng đi theo bạn đời,
    // không phải một nhánh để bung), và anh chị em ruột của người ấy — họ là CON của một người
    // trên đường, tức đã ở trong tầm nhìn một đời.
    expect(ids).toContain("root");
    expect(ids).toContain("sonA");
    expect(ids).toContain("grandA1");

    // KHÔNG có: con cháu của các nhánh khác. `sonB` và `adopted` hiện ra (con trực tiếp của gốc)
    // nhưng cây con của họ thì không — và đó mới là chỗ một dòng họ 1.500 người phình ra.
    expect(visible.nodes.every((n) => n.depth <= 2)).toBe(true);
  });
});

describe("inLawBridgeIds — lượt hỏi thêm tổ tiên của người phối ngẫu", () => {
  it("người dâu (không có cạnh cha/mẹ) ⇒ hỏi chuỗi tổ tiên của chồng", () => {
    // Đúng hình dạng máy chủ thật trả cho ANCESTORS từ `thanhvien`: 2 node, 1 cạnh SPOUSE.
    const edges: TreeEdge[] = [edge("e", "husband", "wife", "SPOUSE")];
    expect(inLawBridgeIds(edges, "wife")).toEqual(["husband"]);
  });

  it("người trong họ ⇒ không tốn thêm lượt gọi nào", () => {
    const { edges } = threeGenerationFamily();
    expect(inLawBridgeIds(edges, "sonA")).toEqual([]);
  });

  it("chưa có dữ liệu hoặc chưa có người được nhắm ⇒ rỗng", () => {
    expect(inLawBridgeIds(undefined, "wife")).toEqual([]);
    expect(inLawBridgeIds([], null)).toEqual([]);
  });
});

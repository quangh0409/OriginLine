import type { TreeEdge, TreeNode } from "@/types/api";

/**
 * **Đường thẳng từ gốc tới người đang xem.**
 *
 * <h2>Vì sao phép tính này tồn tại</h2>
 *
 * Phả đồ mở ra mặc định chỉ bung đúng nút gốc, nên nó hiện gốc + con cái trực tiếp. Với một dòng
 * họ thật, đời ấy đã rộng vài nghìn pixel, và người vừa đăng ký thì **không có mình ở đó** — họ ở
 * đời 8, 9, 12. Checklist mục 5 chốt cách chữa rẻ nhất và hiệu quả nhất: mở ra chỉ hiện *đường
 * thẳng từ gốc tới người đang xem* cộng con cái trực tiếp, mọi nhánh khác gập lại.
 *
 * Hàm này là vế "đường thẳng" ấy. Nó **không** quyết định vẽ gì — nó chỉ trả về danh sách người
 * phải ở trạng thái MỞ để người kia lộ ra. `computeVisibleSubgraph` vẫn là chỗ duy nhất quyết định
 * node nào được vẽ.
 *
 * <h2>Vì sao đi NGƯỢC từ người ấy lên, chứ không tìm xuôi từ gốc</h2>
 *
 * Tìm xuôi là duyệt toàn bộ cây con của gốc — đúng thứ mà cả màn hình này sinh ra để tránh. Đi
 * ngược thì mỗi bước chỉ nhìn cha/mẹ của một người, nên chi phí tỉ lệ với **chiều sâu** (14 đời),
 * không với **số người** (1.500).
 *
 * <h2>Đa hệ: một người có thể có hai cha mẹ trong dữ liệu</h2>
 *
 * Con nuôi có cả cha mẹ đẻ lẫn cha mẹ nuôi; người kế tự có cha sinh và cha thừa tự. Nên phép đi
 * ngược là BFS chứ không phải một vòng `while`, và nó trả về **đường ngắn nhất** tới gốc — đường
 * mà người đọc phả nhận ra là "chi của người ấy". Đường dài hơn vẫn bung được bằng tay.
 */

/** Cạnh huyết thống (kể cả nhận nuôi) — hai loại duy nhất nối cha/mẹ xuống con. */
function isDescentEdge(edge: TreeEdge): boolean {
  return edge.relType === "PARENT_BIO" || edge.relType === "PARENT_ADOPT";
}

/**
 * Bản đồ **con → các cha/mẹ**, dựng từ CẠNH chứ không từ `TreeNode.parentIds`.
 *
 * `parentIds` của hợp đồng có thể trỏ tới người **không nằm trong projection** (cha mẹ bị lọc
 * riêng tư, hoặc ngoài độ sâu đã tải). Đi theo nó sẽ dựng ra một đường có mắt xích không tồn tại
 * trên canvas, và `expandedIds` nhận một id vô nghĩa. Cạnh thì hợp đồng bảo đảm **hai đầu đều có
 * trong `nodes`**, nên đi theo cạnh là đi trên đúng đồ thị đang vẽ.
 */
export function buildParentMap(edges: Iterable<TreeEdge>): Map<string, string[]> {
  const parents = new Map<string, string[]>();
  for (const edge of edges) {
    if (!isDescentEdge(edge)) continue;
    const list = parents.get(edge.target);
    if (list) list.push(edge.source);
    else parents.set(edge.target, [edge.source]);
  }
  return parents;
}

/**
 * Đường ngắn nhất `[gốc, …, người ấy]`, hoặc `null` nếu **không nối được** trong dữ liệu đã tải.
 *
 * `null` là một câu trả lời bình thường chứ không phải lỗi: người ấy có thể thuộc một chi khác,
 * hoặc chuỗi tổ tiên của họ dài hơn độ sâu mà máy chủ vừa trả về. Người gọi xử lý bằng cách **đổi
 * gốc** sang chính người ấy — xem `<TreeCanvas>`.
 */
export function pathFromRootTo(
  nodesById: ReadonlyMap<string, TreeNode>,
  edges: Iterable<TreeEdge>,
  rootId: string,
  targetId: string
): string[] | null {
  if (!rootId || !targetId) return null;
  if (!nodesById.has(rootId) || !nodesById.has(targetId)) return null;
  if (rootId === targetId) return [rootId];

  const parents = buildParentMap(edges);

  /** `cameFrom[x]` = người con mà ta vừa đi lên TỪ đó, để dựng lại đường sau khi chạm gốc. */
  const cameFrom = new Map<string, string>();
  const seen = new Set<string>([targetId]);
  const queue: string[] = [targetId];

  while (queue.length > 0) {
    const current = queue.shift()!;
    if (current === rootId) {
      const path: string[] = [rootId];
      let step = rootId;
      while (step !== targetId) {
        step = cameFrom.get(step)!;
        path.push(step);
      }
      return path;
    }
    for (const parentId of parents.get(current) ?? []) {
      if (seen.has(parentId) || !nodesById.has(parentId)) continue;
      seen.add(parentId);
      cameFrom.set(parentId, current);
      queue.push(parentId);
    }
  }

  return null;
}

/**
 * Tập id phải **mở** để một người lộ ra trên canvas, cộng chính người ấy (để hiện con cái trực
 * tiếp của họ — vế thứ hai của yêu cầu).
 *
 * Trả về tập RỖNG khi không nối được, chứ không ném lỗi: màn hình phải mở ra được trong mọi ca.
 */
export function expansionForFocus(
  nodesById: ReadonlyMap<string, TreeNode>,
  edges: Iterable<TreeEdge>,
  rootId: string,
  focusId: string
): ReadonlySet<string> {
  const path = pathFromRootTo(nodesById, edges, rootId, focusId);
  return path === null ? new Set<string>() : new Set(path);
}

"use client";

import { createContext, useContext } from "react";

/**
 * Mức chi tiết của tấm thẻ nhân khẩu, suy từ **mức phóng của máy quay**.
 *
 * <p>Tính một lần ở `<TreeCanvasInner>` và phát xuống qua context, KHÔNG đọc ở từng thẻ: đọc mức
 * phóng trong `<PersonNode>` sẽ dựng lại hàng nghìn nút mỗi nấc lăn chuột. Giá trị này chỉ đổi khi
 * **vượt ngưỡng**, tức vài lần trong cả một phiên làm việc.</p>
 */
export type TreeDetailLevel = "compact" | "full";

/**
 * Lets <PersonNode> (a React Flow custom node, re-rendered independently by
 * React Flow's internal store) reach the expand/collapse controller without
 * a callback function living inside every node's `data` — that would change
 * node object identity on every render and defeat React Flow's own memoized
 * node rendering.
 */
export interface TreeCanvasContextValue {
  /**
   * Gốc của cây đang xem. <PersonNode> cần biết để KHÔNG vẽ nút thu gọn trên
   * chính nút gốc: `useTreeCanvas.collapse()` bỏ qua nút gốc (thu gọn nó thì
   * canvas trống trơn), nên một cái nút bấm vào không xảy ra gì là điều khiển
   * hỏng chứ không phải điều khiển có chủ đích.
   */
  rootId: string;
  expandedIds: ReadonlySet<string>;
  /** Ids currently being fetched via expand() — drives the small spinner on a node's toggle button. */
  loadingIds: ReadonlySet<string>;
  toggle: (nodeId: string) => void;
  /**
   * Hồ sơ nhân khẩu của **chính người đang đăng nhập** (`/me` → `personId`).
   *
   * `null` với khách, và với thành viên chưa được ghép vào phả — hai trạng thái hợp lệ, không phải
   * lỗi. Dùng để tô đậm đúng một tấm thẻ; không dùng để quyết định quyền gì cả.
   */
  selfPersonId: string | null;
  /**
   * Người đang là **tâm điểm**: vừa được nhảy tới bằng ô tìm trên canvas, hoặc bằng nút
   * "Về chỗ tôi". Khác `selfPersonId` ở chỗ nó đổi theo thao tác, còn `selfPersonId` thì không.
   */
  focusId: string | null;
  detail: TreeDetailLevel;
}

const noop = () => {};

export const TreeCanvasContext = createContext<TreeCanvasContextValue>({
  rootId: "",
  expandedIds: new Set(),
  loadingIds: new Set(),
  toggle: noop,
  selfPersonId: null,
  focusId: null,
  detail: "full",
});

export function useTreeCanvasContext(): TreeCanvasContextValue {
  return useContext(TreeCanvasContext);
}

"use client";

import { createContext, useContext } from "react";

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
}

const noop = () => {};

export const TreeCanvasContext = createContext<TreeCanvasContextValue>({
  rootId: "",
  expandedIds: new Set(),
  loadingIds: new Set(),
  toggle: noop,
});

export function useTreeCanvasContext(): TreeCanvasContextValue {
  return useContext(TreeCanvasContext);
}

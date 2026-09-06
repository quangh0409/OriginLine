/**
 * Canvas-only UI types — never mirrors of the API contract (those live in
 * src/types/api.ts). Keeping this file separate makes it obvious at a
 * glance which types are "ours to change freely" vs. "change only via
 * contracts/".
 */
export type TreeViewMode = "hierarchical" | "radial" | "matrix";

export const TREE_VIEW_MODES: readonly TreeViewMode[] = ["hierarchical", "radial", "matrix"];

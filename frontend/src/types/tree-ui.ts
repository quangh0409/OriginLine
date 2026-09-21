/**
 * Canvas-only UI types — never mirrors of the API contract (those live in
 * src/types/api.ts). Keeping this file separate makes it obvious at a
 * glance which types are "ours to change freely" vs. "change only via
 * contracts/".
 */
export type TreeViewMode = "hierarchical" | "radial" | "matrix" | "list";

export const TREE_VIEW_MODES: readonly TreeViewMode[] = [
  "hierarchical",
  "radial",
  "matrix",
  "list",
];

/**
 * Ba chế độ vẽ trên **canvas**. `"list"` không nằm trong số đó — nó thay hẳn canvas bằng một danh
 * sách HTML, nên mọi chỗ hỏi "thuật toán bố cục nào" phải loại nó ra trước.
 */
export const CANVAS_VIEW_MODES: readonly TreeViewMode[] = ["hierarchical", "radial", "matrix"];

export function isCanvasViewMode(mode: TreeViewMode): boolean {
  return mode !== "list";
}

/**
 * Mã chế độ xem dùng trên URL (`/tree?view=…`).
 *
 * <h2>Vì sao phải nằm trên URL</h2>
 * Trước đây `viewMode` là `useState` thuần trong `<TreeCanvas>`. Hệ quả: một chế
 * độ xem là "thứ chỉ mình tôi thấy" — không gửi được qua Zalo, không đánh dấu
 * được, không mở lại được sau khi tải lại trang. Với một sản phẩm mà đường lan
 * truyền chính là người trong họ gửi nhau một liên kết, đó là mất mát thật, và
 * design/03 ghi FR-1.4 chỉ đạt "một phần" đúng vì lý do này.
 *
 * <h2>Vì sao mã tiếng Việt chứ không phải tên kỹ thuật</h2>
 * Liên kết được gửi cho người trong họ đọc, không cho máy đọc. `?view=toa` nói
 * được điều gì đó với người nhận; `?view=radial` thì không. Đây cũng là nguyên
 * tắc 5 của định hướng 00 ("nói tiếng của dòng họ, không nói tiếng của phần
 * mềm") áp cho URL — bề mặt duy nhất của sản phẩm được sao chép và dán bằng tay.
 */
export const VIEW_MODE_SLUG: Record<TreeViewMode, string> = {
  hierarchical: "doc",
  radial: "toa",
  matrix: "matran",
  /** Danh sách theo đời — kiểu xem dành cho điện thoại. `?view=doi`. */
  list: "doi",
};

const SLUG_TO_MODE = new Map<string, TreeViewMode>(
  (Object.entries(VIEW_MODE_SLUG) as [TreeViewMode, string][]).map(([mode, slug]) => [slug, mode])
);

/**
 * Đọc `?view=` thành chế độ xem. Giá trị lạ hoặc thiếu ⇒ phân cấp.
 *
 * KHÔNG báo lỗi với giá trị lạ: URL là thứ người ta gõ tay và sửa tay: một chữ
 * sai không được biến phả đồ thành màn hình lỗi.
 */
export function parseViewMode(raw: string | null | undefined): TreeViewMode {
  if (!raw) return "hierarchical";
  return SLUG_TO_MODE.get(raw.trim().toLowerCase()) ?? "hierarchical";
}

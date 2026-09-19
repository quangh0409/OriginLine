/**
 * Các mức phóng và tham số canh khung của phả đồ, tách riêng khỏi component để vừa dùng được
 * ở `<TreeCanvasInner>` (nơi gọi `fitView`) vừa kiểm chứng được bằng unit test.
 */

/** Mức thu nhỏ tối đa canvas cho phép (trùng `minZoom` của `<ReactFlow>`). */
export const CANVAS_MIN_ZOOM = 0.03;

/** Mức phóng to tối đa canvas cho phép. */
export const CANVAS_MAX_ZOOM = 2;

/**
 * Sàn thu nhỏ cho lần canh khung đầu tiên.
 *
 * `fitView` không có sàn sẽ thu cho VỪA HẾT nhánh đã tải. Trên Pixel 5 (393px) với nhánh mặc
 * định, mức đó là 0.21: thẻ nhân khẩu còn 44px, tên người còn cỡ chữ 3px, và nút mở rộng nhánh
 * 24px co lại còn 5px — dưới ngưỡng chạm 24px của WCAG 2.5.8, tức không đọc được và không bấm
 * được. Phả đồ mở ra như một đám chấm.
 *
 * 0.75 là mức thấp nhất còn giữ được thẻ ở 156px và tên ở cỡ ~11px, tức vẫn đọc được trên điện
 * thoại — đúng nhóm người dùng chính (kiều bào, và các cụ cao tuổi trong họ). Người dùng vẫn thu
 * nhỏ thêm được bằng cử chỉ chụm hai ngón hoặc nút "Thu toàn cây"; đây chỉ là điểm MỞ RA ban đầu.
 * Trên màn hình rộng phép canh khung thường ra khoảng 0.79 nên sàn này không đụng tới.
 */
export const MIN_INITIAL_ZOOM = 0.75;

/** Canh khung lúc mở phả đồ: có sàn, vì người dùng chưa chủ động yêu cầu gì cả. */
export const INITIAL_FIT_VIEW_OPTIONS = {
  padding: 0.3,
  duration: 300,
  minZoom: MIN_INITIAL_ZOOM,
} as const;

/**
 * Canh khung theo nút "Thu toàn cây" — CỐ Ý không áp sàn {@link MIN_INITIAL_ZOOM}.
 *
 * Hội đồng giữ sàn cho lần mở đầu tiên, nhưng vẫn muốn có đường xem toàn cảnh: với nhiều dòng
 * họ, khoảnh khắc thấy trọn cả họ mình (chiếu lên màn hình ngày giỗ Tổ, họp họ) mới là giá trị
 * của sản phẩm. Đây là hành động CHỦ Ý của người dùng, nên đánh đổi "chữ nhỏ, khó bấm" là do họ
 * chọn — khác hẳn với việc mặc định ném họ vào mức 0.21 ngay khi mở trang.
 *
 * Sàn duy nhất còn lại là {@link CANVAS_MIN_ZOOM}, tức chính giới hạn của canvas.
 */
export const WHOLE_TREE_FIT_VIEW_OPTIONS = {
  padding: 0.12,
  duration: 400,
  minZoom: CANVAS_MIN_ZOOM,
} as const;

/* --------------------------------------------------------------------------
   GIẢM CHUYỂN ĐỘNG (prefers-reduced-motion)

   Hai phép canh khung trên đây chạy bằng JAVASCRIPT — React Flow tự nội suy ma trận máy quay qua
   `duration`, không qua CSS transition. Nên bản vá `prefers-reduced-motion` ở tầng biểu định kiểu
   KHÔNG với tới được: nó cắt được mọi chuyển tiếp CSS mà vẫn để cả mặt phẳng phả đồ trượt và phóng
   trong 300–400ms. Với người rối loạn tiền đình thì đúng thứ đó gây chóng mặt và buồn nôn — và một
   mặt phẳng lớn trượt-phóng là hoạt ảnh nặng nhất còn lại trong sản phẩm.
--------------------------------------------------------------------------- */

/** Đổi "400ms" / "0.4s" / "0ms" thành số mili giây; null nếu không đọc được. */
function parseCssMilliseconds(raw: string): number | null {
  const text = raw.trim();
  if (text === "") return null;
  const match = /^(-?[\d.]+)(ms|s)$/.exec(text);
  if (!match) return null;
  const value = Number(match[1]);
  if (!Number.isFinite(value)) return null;
  return match[2] === "s" ? value * 1000 : value;
}

/**
 * Người dùng có đang xin giảm chuyển động không.
 *
 * Hỏi HAI nguồn, vì mỗi nguồn hỏng một kiểu:
 *
 * 1. `matchMedia` — nguồn gốc, đúng cả khi biểu định kiểu chưa tải xong.
 * 2. Biến CSS `--thoi-luong-canh-khung` (globals.css) — tầng CSS đã ép nó về `0ms` trong khối
 *    `prefers-reduced-motion`. Đọc nó nghĩa là mã TS và mã CSS không thể nói hai điều khác nhau;
 *    nếu ai đó sau này quyết định tắt hoạt ảnh vì lý do khác (máy yếu, chế độ trình chiếu), chỉ
 *    cần ép biến ấy về 0 là phả đồ nghe theo ngay.
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === "undefined") return false;
  try {
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches) return true;
    const raw = window
      .getComputedStyle(document.documentElement)
      .getPropertyValue("--thoi-luong-canh-khung");
    return parseCssMilliseconds(raw) === 0;
  } catch {
    // Môi trường không có matchMedia/getComputedStyle (test dựng tay, kết xuất phía máy chủ):
    // giữ nguyên hoạt ảnh chứ không tự ý tắt.
    return false;
  }
}

/**
 * Áp nguyện vọng chuyển động của người dùng lên một bộ tham số canh khung.
 *
 * Trả về CHÍNH đối tượng cũ khi không phải giảm chuyển động — để hai hằng số trên vẫn là hai đối
 * tượng ổn định, không sinh tham chiếu mới mỗi lần dựng lại (chúng đi vào `useCallback`/`useEffect`).
 */
export function withMotionPreference<T extends { readonly duration: number }>(
  options: T
): T | (Omit<T, "duration"> & { duration: 0 }) {
  return prefersReducedMotion() ? { ...options, duration: 0 as const } : options;
}

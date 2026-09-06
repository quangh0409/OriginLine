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

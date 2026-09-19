package vn.giapha.dataimport.api.rest.dto;

/**
 * Thân yêu cầu của {@code POST /import/batches/{batchId}/duplicates/{pairId}/decision}.
 *
 * <h2>Chỉ nhận khoá, không nhận dữ liệu nhân khẩu — và đây là một quyết định về an toàn</h2>
 * Yêu cầu <b>không</b> mang {@code personId}. Đích của phép gộp đã nằm sẵn trong chính cặp mà máy
 * chủ dò ra, nên người gọi chỉ chọn được trong số những cặp bộ dò đề xuất. Nhận {@code personId}
 * từ client sẽ biến lối gọi này thành "gộp dòng X vào bất kỳ ai trong dòng họ", tức một lối ghi
 * vượt phạm vi chi mà không phép kiểm nào ở đây thấy được.
 *
 * @param decision {@code MERGED} (đây là cùng một người) · {@code DISTINCT} (hai người khác nhau) ·
 *        {@code DEFERRED} (chưa chắc, để lại sau — <b>vẫn chặn cổng duyệt</b>).
 *        {@code PENDING} <b>không</b> nhận được: rút lại một lời khai đã ghi không phải là xoá nó
 *        đi, và chưa ai cần nghiệp vụ ấy.
 * @param note ghi chú tuỳ chọn của người quyết ("đối chiếu với sổ chi Giáp bản 1998"). Không bắt
 *        buộc; bắt buộc thì người ta gõ một dấu chấm.
 */
public record ImportDuplicateDecisionRequest(String decision, String note) {
}

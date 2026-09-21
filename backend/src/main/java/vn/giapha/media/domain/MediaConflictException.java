package vn.giapha.media.domain;

import vn.giapha.shared.exception.DomainException;

/**
 * <b>Trạng thái phía máy chủ</b> không cho phép thao tác này — HTTP {@code 409}.
 *
 * <h2>Vì sao 409 chứ không 422</h2>
 * 422 nói "bạn gửi sai, hãy sửa body"; ở đây thân yêu cầu hoàn toàn hợp lệ và thứ đã đổi là trạng
 * thái của bản ghi. Hành động đúng của client là <b>tải lại rồi xem</b>, không phải sửa JSON — hai
 * mã trạng thái ấy dẫn tới hai màn hình khác nhau. Cùng lập luận, cùng chữ với
 * {@code content.ContentConflictException}; lớp này tồn tại riêng vì bắt kiểu cha
 * {@code DomainException} trong một {@code @RestControllerAdvice} sẽ nuốt luôn 404/403 của
 * <i>mọi</i> context (xem {@code MediaExceptionHandler}).
 *
 * <p>Bốn tình huống dùng nó, và cả bốn đều là "người kia đã làm gì đó trước":</p>
 * <ul>
 *   <li>{@code MEDIA_NOT_UPLOADED} — xác nhận khi kho chưa có tệp nào;</li>
 *   <li>{@code MEDIA_TICKET_CLOSED} — phiếu quá hạn hoặc đã xử lý;</li>
 *   <li>{@code REPORT_CLOSED} — đơn báo gỡ đã được người khác quyết;</li>
 *   <li>{@code REPORT_DUPLICATE} — chính người này đã có một đơn đang mở.</li>
 * </ul>
 */
public class MediaConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public MediaConflictException(String code, String message) {
        super(code, message);
    }
}

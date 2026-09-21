package vn.giapha.content.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Xung đột trạng thái — ánh xạ sang HTTP <b>409</b>.
 *
 * <p>Tách khỏi {@link DomainException} thường (vốn ra 422) vì 409 mang một thông điệp khác hẳn cho
 * client: dữ liệu <b>đã đổi</b> hoặc <b>đã ở trạng thái đó</b>, nên hãy <i>nạp lại rồi xem</i>, chứ
 * không phải "bạn gửi sai, hãy sửa body". Hai trường hợp dùng nó:</p>
 * <ul>
 *   <li>{@code CONTENT_CLOSED} — bài/bản ghi đã ở trạng thái cuối, hoặc mũi tên chuyển trạng thái
 *       không tồn tại. Gần như luôn là do hai người mở cùng một bài và người kia bấm trước;</li>
 *   <li>{@code OPTIMISTIC_LOCK_CONFLICT} — hai người cùng sửa một bản ghi.</li>
 * </ul>
 *
 * <p>Đối xứng với {@code genealogy.application.GenealogyConflictException} — cùng lý do, cùng mã
 * trạng thái, để client không phải học hai luật.</p>
 */
public class ContentConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ContentConflictException(String code, String message) {
        super(code, message);
    }

    public ContentConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}

package vn.giapha.genealogy.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Xung đột trạng thái - ánh xạ sang HTTP <b>409</b>.
 *
 * <p>Tách khỏi {@link DomainException} thường (vốn ra 422) vì 409 mang một thông điệp khác hẳn cho
 * client: dữ liệu <b>đã đổi</b> hoặc <b>đã ở trạng thái đó</b>, nên hãy nạp lại rồi thử lại, chứ
 * không phải "bạn gửi sai, hãy sửa body". Ba trường hợp dùng nó:</p>
 * <ul>
 *   <li>{@code OPTIMISTIC_LOCK_CONFLICT} - hai người cùng sửa một hồ sơ;</li>
 *   <li>{@code PERSON_ALREADY_DELETED} - đã ở trạng thái xoá mềm;</li>
 *   <li>{@code RELATIONSHIP_CYCLE} - quan hệ cha-con sẽ tạo chu trình trong đồ thị.</li>
 * </ul>
 *
 * <p>Va chạm kỵ húy cũng là 409 nhưng có lớp riêng vì nó mang theo danh sách va chạm và
 * <b>ghi đè được</b> - xem {@link TabooNameConflictException}.</p>
 */
public class GenealogyConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public GenealogyConflictException(String code, String message) {
        super(code, message);
    }

    public GenealogyConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}

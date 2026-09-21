package vn.giapha.events.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Xung đột trạng thái khi ghi sự kiện — ánh xạ sang HTTP <b>409</b>.
 *
 * <p>Tách khỏi {@link DomainException} trần (vốn là 422) vì 409 nói một điều khác hẳn với người
 * dùng: yêu cầu <i>hợp lệ</i>, nhưng trạng thái hiện tại của dữ liệu không cho nó đi qua — tải lại
 * rồi thử lại là hành động đúng. 422 thì tải lại bao nhiêu lần cũng vô ích.</p>
 */
public class EventConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EventConflictException(String code, String message) {
        super(code, message);
    }
}

package vn.giapha.clan.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Xung đột trạng thái trên hồ sơ dòng họ — HTTP <b>409</b>: {@code CLAN_ROOT_LOCKED} hoặc
 * {@code OPTIMISTIC_LOCK_CONFLICT}.
 *
 * <p>Tách khỏi {@link DomainException} thường (ra 422) vì lời khuyên cho client khác hẳn: thân yêu
 * cầu hợp lệ, thứ chặn là <b>trạng thái phía máy chủ</b> — tải lại rồi xem, không phải sửa body.</p>
 */
public class ClanConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ClanConflictException(String code, String message) {
        super(code, message);
    }
}

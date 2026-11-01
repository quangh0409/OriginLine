package vn.giapha.clan.application;

import vn.giapha.shared.exception.DomainException;

/**
 * Sửa hồ sơ <b>đã có</b> mà thiếu hoặc sai {@code If-Match} — HTTP <b>412</b>
 * {@code PRECONDITION_REQUIRED}, đúng như {@code PATCH /persons/{id}}.
 *
 * <p>Nằm ở {@code application} (khác {@code genealogy}, nơi nó ở tầng api) vì chỉ tầng này biết
 * hồ sơ đã tồn tại hay chưa: {@code PUT} lần đầu là <i>tạo</i> và không cần {@code If-Match},
 * {@code PUT} lần sau là <i>sửa</i> và bắt buộc có. Controller không tự trả lời được câu ấy mà
 * không đọc CSDL lần thứ hai ngoài transaction.</p>
 */
public class ClanPreconditionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ClanPreconditionException(String message) {
        super(ClanProblemCodes.PRECONDITION_REQUIRED, message);
    }
}

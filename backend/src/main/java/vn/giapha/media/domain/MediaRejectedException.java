package vn.giapha.media.domain;

import vn.giapha.shared.exception.DomainException;

/**
 * Tệp bị từ chối vì <b>chính nó</b>: sai chữ ký byte, vượt trần dung lượng, vượt trần thời lượng,
 * hoặc không đọc được thời lượng.
 *
 * <p>Tách khỏi {@code DomainException} chung để tầng api trả <b>422</b> chứ không phải 400: thân
 * yêu cầu hoàn toàn hợp lệ về cú pháp, thứ sai nằm ở <i>đối tượng đã nằm trên kho</i>. Client sửa
 * bằng cách chọn/xuất lại tệp, không phải bằng cách sửa JSON.</p>
 */
public class MediaRejectedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public MediaRejectedException(String code, String message) {
        super(code, message);
    }
}

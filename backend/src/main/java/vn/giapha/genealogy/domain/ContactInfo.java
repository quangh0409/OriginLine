package vn.giapha.genealogy.domain;

import vn.giapha.shared.domain.ValueObject;

/**
 * Thông tin liên hệ — <b>toàn khối là dữ liệu Tầng 3</b> theo Nghị định 13/2023.
 *
 * <p>Không bao giờ được ghi vào {@code audit_log}, log ứng dụng, hay {@code rejectedValue} của
 * thông điệp lỗi. Khi ẩn danh hoá theo yêu cầu hợp pháp thì đây chính là phần bị xoá, còn node
 * phả hệ vẫn ở lại để cây không gãy.</p>
 */
public record ContactInfo(String phone, String email, String zaloId) implements ValueObject {

    public static final ContactInfo EMPTY = new ContactInfo(null, null, null);

    public ContactInfo {
        phone = blankToNull(phone);
        email = blankToNull(email);
        zaloId = blankToNull(zaloId);
    }

    public boolean isEmpty() {
        return phone == null && email == null && zaloId == null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

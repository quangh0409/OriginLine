package vn.giapha.membership.domain;

import java.util.Locale;

/**
 * Loại yêu cầu đính chính, khớp {@code ck_change_request_type} của V5.
 *
 * <p>Danh sách đóng và cố ý hẹp: mỗi loại tương ứng với đúng một use case ghi bên
 * {@code genealogy}. Thêm một loại mới mà không có use case tương ứng thì lúc duyệt sẽ không biết
 * áp dụng {@code payload} vào đâu.</p>
 */
public enum ChangeRequestType {

    CREATE_PERSON,
    UPDATE_PERSON,
    SOFT_DELETE_PERSON,
    ADD_RELATIONSHIP,
    REMOVE_RELATIONSHIP,
    ADD_NAME,
    MOVE_BRANCH,
    OTHER;

    /**
     * {@code true} nếu loại này bắt buộc phải trỏ tới một nhân khẩu có sẵn.
     *
     * <p>Khớp ràng buộc {@code ck_change_request_person}: chỉ {@link #CREATE_PERSON} được phép
     * không có {@code person_id}.</p>
     */
    public boolean requiresExistingPerson() {
        return this != CREATE_PERSON;
    }

    public static ChangeRequestType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Loai yeu cau dinh chinh khong duoc rong");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Loai yeu cau dinh chinh khong hop le: " + raw, ex);
        }
    }
}

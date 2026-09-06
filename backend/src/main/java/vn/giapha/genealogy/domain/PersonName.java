package vn.giapha.genealogy.domain;

import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.domain.ValueObject;

/**
 * Một lớp tên của nhân khẩu (bảng {@code person_name}).
 *
 * <p>{@code name_unaccented} <b>không</b> có mặt ở đây: nó là generated column của Postgres
 * ({@code V6__search.sql}) nên domain không được phép tự sinh — sinh tay là mở đường cho lệch
 * giữa {@code full_name} và bản không dấu.</p>
 *
 * @param id       id bản ghi; {@code null} khi tên chưa được lưu
 * @param type     lớp tên
 * @param fullName tên Quốc ngữ có dấu, đã trim, không rỗng
 * @param hanNom   tên chữ Hán–Nôm nếu có (Unicode CJK, không phải phiên âm)
 * @param primary  tên hiển thị mặc định — mỗi người có đúng một
 * @param note     ghi chú xuất xứ, ví dụ chép theo bia mộ
 */
public record PersonName(UUID id, NameType type, String fullName, String hanNom, boolean primary, String note)
        implements ValueObject {

    public PersonName {
        Objects.requireNonNull(type, "PersonName.type khong duoc null");
        Objects.requireNonNull(fullName, "PersonName.fullName khong duoc null");
        fullName = fullName.trim();
        if (fullName.isEmpty()) {
            throw new IllegalArgumentException("PersonName.fullName khong duoc rong");
        }
        if (hanNom != null && hanNom.isBlank()) {
            hanNom = null;
        }
        if (note != null && note.isBlank()) {
            note = null;
        }
    }

    public static PersonName of(NameType type, String fullName, boolean primary) {
        return new PersonName(null, type, fullName, null, primary, null);
    }

    public PersonName asPrimary(boolean value) {
        return new PersonName(id, type, fullName, hanNom, value, note);
    }

    public PersonName withId(UUID value) {
        return new PersonName(value, type, fullName, hanNom, primary, note);
    }

    /** {@code true} nếu đây là tên húy — lớp tên duy nhất kích hoạt cảnh báo kỵ húy (FR-1.6). */
    public boolean isTabooName() {
        return type == NameType.HUY;
    }
}

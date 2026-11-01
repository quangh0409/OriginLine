package vn.giapha.clan.domain;

/**
 * Chuẩn hoá chuỗi tự khai: cắt khoảng trắng, rỗng thành {@code null}, chặn độ dài.
 *
 * <p>Gom một chỗ để "rỗng" chỉ có một nghĩa trong cả hồ sơ: một ô trống gửi lên là
 * <b>xoá giá trị</b>, không phải một chuỗi rỗng lưu vào CSDL rồi hiện thành một dòng trắng.</p>
 */
final class Texts {

    private Texts() {
    }

    static String optional(String raw, int max, String field) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " dài quá " + max + " ký tự");
        }
        return trimmed;
    }

    static String required(String raw, int max, String field) {
        String value = optional(raw, max, field);
        if (value == null) {
            throw new IllegalArgumentException("Thiếu " + field);
        }
        return value;
    }
}

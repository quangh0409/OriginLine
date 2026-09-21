package vn.giapha.content.api.rest;

import vn.giapha.content.application.ContentPreconditionException;

/**
 * Đọc header {@code If-Match} thành số phiên bản, và {@code version} thành {@code ETag}.
 *
 * <h2>Vì sao ở tầng api chứ không ở application</h2>
 * Cơ chế chống ghi đè của tầng dưới là {@code expectedVersion} — một con số. Cách client mang con
 * số ấy lên ({@code ETag} / {@code If-Match}) thuần tuý là chuyện của giao thức. Cùng lý do mà
 * {@code genealogy.api.rest.PreconditionRequiredException} nằm ở tầng api chứ không sâu hơn.
 *
 * <h2>Bắt buộc có {@code If-Match}, không cho ghi mù</h2>
 * Một bản nháp hoàn toàn có thể đang mở trên điện thoại <i>và</i> máy tính của cùng một người —
 * §2 còn yêu cầu <b>lưu nháp tự động</b>, tức hai tab cùng ghi là hành vi bình thường chứ không
 * phải ca hiếm. Im lặng ghi đè ở đó là mất dữ liệu mà không ai phát hiện ra.
 */
final class IfMatch {

    private IfMatch() {
    }

    /**
     * @throws ContentPreconditionException (→ HTTP 412) khi thiếu hoặc sai định dạng
     */
    static long parse(String header) {
        if (header == null || header.isBlank()) {
            throw new ContentPreconditionException(
                    "Thieu header If-Match; hay lay ETag tu lan GET gan nhat truoc khi sua");
        }
        String value = header.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "").trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ContentPreconditionException("If-Match khong hop le: " + header);
        }
    }

    /** {@code ETag} ở đây là <b>khoá lạc quan</b>, không phải khoá bộ nhớ đệm. */
    static String etag(long version) {
        return "\"" + version + "\"";
    }
}

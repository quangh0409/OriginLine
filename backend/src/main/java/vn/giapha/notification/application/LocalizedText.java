package vn.giapha.notification.application;

/**
 * Một đoạn chữ ở cả hai ngôn ngữ giao diện (VI/EN).
 *
 * <p>Nội dung thông báo được dựng <b>một lần</b> lúc đẩy tin, nhưng người nhận thì mỗi người một
 * ngôn ngữ ({@code app_user.locale}) — kiều bào đời thứ hai thường đọc tiếng Anh. Mang cả hai bản
 * trong tin và để consumer chọn theo người nhận, thay vì nhân đôi tin hoặc bắt người nhận đọc thứ
 * tiếng mà máy chủ chọn hộ.</p>
 *
 * <p>Thiếu bản tiếng Anh thì rơi về tiếng Việt: hiển thị tiếng Việt vẫn hơn là hiển thị chuỗi
 * rỗng.</p>
 */
public record LocalizedText(String vi, String en) {

    public static LocalizedText of(String vi, String en) {
        return new LocalizedText(vi, en);
    }

    public static LocalizedText same(String value) {
        return new LocalizedText(value, value);
    }

    /** @param locale mã ngôn ngữ của người nhận ({@code vi} / {@code en}) */
    public String forLocale(String locale) {
        if ("en".equalsIgnoreCase(locale) && en != null && !en.isBlank()) {
            return en;
        }
        return vi != null && !vi.isBlank() ? vi : en;
    }
}

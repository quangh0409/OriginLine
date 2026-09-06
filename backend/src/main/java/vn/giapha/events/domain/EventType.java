package vn.giapha.events.domain;

/**
 * Loại sự kiện dòng họ — <b>khớp một-một</b> với ràng buộc {@code ck_event_type} của
 * {@code V4__events.sql}.
 *
 * <p>Enum này là <b>ngôn ngữ của cơ sở dữ liệu</b>, không phải ngôn ngữ của API. Hợp đồng
 * {@code contracts/openapi.yaml} dùng một tập mã khác ({@code GIO_THUONG}, {@code GIO_HO},
 * {@code CHAP_MA}, {@code MUNG_THO}, …) — hai tập này lệch nhau và việc quy đổi nằm ở
 * {@code api/rest/EventTypeApiMapper}. Migration thuộc sở hữu của W1 nên tầng dưới giữ nguyên
 * mã của migration; tầng API mới là nơi nhượng bộ.</p>
 */
public enum EventType {

    /** Giỗ cá nhân — nguồn chân lý là {@code person.death_lunar}. */
    GIO(true),
    /** Giỗ Tổ / tế Tổ toàn họ. */
    GIO_TO(true),
    /** Tế lễ tại từ đường. */
    TE_LE(true),
    /** Chạp mộ / tảo mộ cuối năm. */
    TAO_MO(true),
    /** Khánh thành, tu bổ từ đường. */
    KHANH_THANH(false),
    /** Họp họ. */
    HOP_HO(false),
    /** Sinh nhật / mừng thọ — chủ thể là người <b>còn sống</b>, chịu phân tầng riêng tư. */
    SINH_NHAT(false),
    /** Cưới hỏi. */
    CUOI_HOI(false),
    /** Loại khác. */
    KHAC(false);

    private final boolean ancestralRite;

    EventType(boolean ancestralRite) {
        this.ancestralRite = ancestralRite;
    }

    /** {@code true} với các lễ cúng tổ tiên — dùng để chọn văn phong của thông báo. */
    public boolean isAncestralRite() {
        return ancestralRite;
    }

    /** Chủ thể là người còn sống ⇒ sự kiện phải chịu phân tầng riêng tư khi hiển thị. */
    public boolean isAboutLivingPerson() {
        return this == SINH_NHAT || this == CUOI_HOI;
    }

    public static EventType fromDbValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return KHAC;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return KHAC;
        }
    }
}

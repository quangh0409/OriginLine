package vn.giapha.events.domain;

/**
 * Loại sự kiện dòng họ — <b>khớp một-một</b> với ràng buộc {@code ck_event_type} của
 * {@code V4__events.sql}.
 *
 * <p>Enum này là <b>ngôn ngữ của cơ sở dữ liệu</b>, không phải ngôn ngữ của API. Hợp đồng
 * {@code contracts/openapi.yaml} dùng một tập mã khác ({@code GIO_THUONG}, {@code GIO_HO},
 * {@code CHAP_MA}, …) — hai tập này lệch nhau ở một số mã và việc quy đổi nằm ở
 * {@code api/rest/EventTypeApiMapper}.</p>

 * <p>Từ {@code V10} trở đi, phép quy đổi ấy là <b>song ánh</b>: không còn giá trị nào của enum bị
 * gộp vào một mã API chung. Thêm hằng số vào đây <b>bắt buộc</b> phải kèm một migration mở rộng
 * {@code ck_event_type}, nếu không thì giá trị mới không bao giờ ghi được và chỉ tạo ra một bộ lọc
 * chết trên giao diện.</p>
 */
public enum EventType {

    /** Giỗ cá nhân — nguồn chân lý là {@code person.death_lunar}. */
    GIO(true),
    /** Giỗ Tổ / tế Tổ toàn họ. */
    GIO_TO(true),
    /** Tế lễ tại từ đường. */
    TE_LE(true),
    /** Giỗ đầu — tròn một năm ngày mất, mốc tang lễ lớn hơn hẳn giỗ thường. */
    TIEU_TUONG(true),
    /** Giỗ hết — tròn hai năm ngày mất, kết thúc đại tang. */
    DAI_TUONG(true),
    /** Chạp mộ / tảo mộ cuối năm. */
    TAO_MO(true),
    /** Khánh thành, tu bổ từ đường. */
    KHANH_THANH(false),
    /** Họp họ. */
    HOP_HO(false),
    /** Sinh nhật — chủ thể là người <b>còn sống</b>, chịu phân tầng riêng tư. */
    SINH_NHAT(false),
    /**
     * Mừng thọ bậc cao niên (mốc 60/70/80/90 tuổi) — <b>không</b> đồng nhất với sinh nhật.
     *
     * <p>Gộp hai loại này làm một là mất thông tin thật: mừng thọ là việc của cả họ và có nghi lễ
     * riêng, còn sinh nhật là việc của một nhà. Chủ thể còn sống nên vẫn chịu phân tầng riêng tư.</p>
     */
    MUNG_THO(false),
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
        return this == SINH_NHAT || this == MUNG_THO || this == CUOI_HOI;
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

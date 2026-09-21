package vn.giapha.events.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.LunarDate;

/**
 * Sự kiện dòng họ: giỗ, tế lễ, chạp mả, họp họ… (FR-2.3).
 *
 * <p>POJO thuần — không annotation Spring/JPA. Ánh xạ sang bảng {@code event} nằm ở
 * {@code events.infrastructure.jpa}.</p>
 *
 * <h2>Ngày âm là gốc, ngày dương là dẫn xuất</h2>
 * Với sự kiện theo âm lịch, {@link #lunarDate()} lưu {@code day}/{@code month}/{@code leap} và
 * <b>năm âm chỉ mang tính lịch sử</b> (năm mất của cụ) — mỗi năm sự kiện lặp lại vào cùng ngày–tháng
 * âm ấy. Ngày dương tương ứng đổi theo từng năm nên không bao giờ được lưu như dữ liệu gốc.
 *
 * <h2>Phạm vi người nhận</h2>
 * {@code clanLevel = true} ⇒ cả họ; ngược lại chỉ thành viên thuộc {@link #targetBranchId()} và
 * <b>các nhánh con</b> (so bằng {@code ltree}). Ràng buộc {@code ck_event_scope} cấm đặt đồng thời
 * hai thứ này.
 */
public final class Event {

    private final UUID id;
    private final UUID personId;
    private final EventType type;
    private final String title;
    private final String description;
    private final LunarDate lunarDate;
    private final LocalDate solarDate;
    private final boolean lunarBased;
    private final boolean recurring;
    private final UUID targetBranchId;
    private final boolean clanLevel;
    private final String location;
    private final boolean deleted;
    private final long version;

    private Event(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Event.id khong duoc null");
        this.personId = builder.personId;
        this.type = builder.type == null ? EventType.KHAC : builder.type;
        this.title = builder.title;
        this.description = builder.description;
        this.lunarDate = builder.lunarDate;
        this.solarDate = builder.solarDate;
        this.lunarBased = builder.lunarBased;
        this.recurring = builder.recurring;
        this.targetBranchId = builder.targetBranchId;
        this.clanLevel = builder.clanLevel;
        this.location = builder.location;
        this.deleted = builder.deleted;
        this.version = builder.version;
        if (this.lunarBased && this.lunarDate == null) {
            throw new IllegalArgumentException(
                    "Su kien theo am lich bat buoc co lunar_date (ck_event_date_source): " + this.id);
        }
        if (!this.lunarBased && this.solarDate == null) {
            throw new IllegalArgumentException(
                    "Su kien theo duong lich bat buoc co solar_date (ck_event_date_source): " + this.id);
        }
        if (this.clanLevel && this.targetBranchId != null) {
            throw new IllegalArgumentException(
                    "Su kien cap dong ho khong duoc gan chi/nganh (ck_event_scope): " + this.id);
        }
        // Bản sao Java của ck_event_oneoff_lunar_year (V18). Một lễ khánh thành xảy ra đúng một
        // lần: "ngày 12 tháng 2 âm" mà không có năm thì không quy đổi được sang ngày dương nào, và
        // cái sai ấy không lộ ra lúc ghi — nó lộ ra dưới dạng một sự kiện lặp lại tới vô tận.
        if (this.lunarBased && !this.recurring && this.lunarDate.year() <= 0) {
            throw new IllegalArgumentException(
                    "Su kien mot lan theo am lich bat buoc co nam am (ck_event_oneoff_lunar_year): "
                            + this.id);
        }
    }

    public static Builder builder(UUID id) {
        return new Builder(id);
    }

    /**
     * Builder nạp sẵn mọi giá trị hiện tại — nền của một lần sửa từng phần ({@code PATCH}).
     *
     * <p>Sửa bằng cách dựng lại nguyên bản ghi giữ được tính bất biến của lớp này, và quan trọng
     * hơn: mọi ràng buộc trong constructor ({@code ck_event_scope}, {@code ck_event_date_source},
     * {@code ck_event_oneoff_lunar_year}) chạy lại trên <b>trạng thái sau khi sửa</b>. Một hàm
     * {@code setTargetBranchId()} sẽ bỏ qua chính những phép kiểm ấy.</p>
     */
    public Builder toBuilder() {
        return new Builder(id)
                .personId(personId)
                .type(type)
                .title(title)
                .description(description)
                .lunarDate(lunarDate)
                .solarDate(solarDate)
                .lunarBased(lunarBased)
                .recurring(recurring)
                .targetBranchId(targetBranchId)
                .clanLevel(clanLevel)
                .location(location)
                .deleted(deleted)
                .version(version);
    }

    public UUID id() {
        return id;
    }

    public UUID personId() {
        return personId;
    }

    public EventType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public LunarDate lunarDate() {
        return lunarDate;
    }

    public LocalDate solarDate() {
        return solarDate;
    }

    public boolean isLunarBased() {
        return lunarBased;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public UUID targetBranchId() {
        return targetBranchId;
    }

    public boolean isClanLevel() {
        return clanLevel;
    }

    public String location() {
        return location;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /** Phiên bản khoá lạc quan ({@code event.version}) — cũng là giá trị sinh {@code ETag}. */
    public long version() {
        return version;
    }

    /**
     * Sự kiện có sinh lịch nhắc hay không.
     *
     * <p>Sự kiện đã xoá mềm thì không: xoá mềm là để giữ liên kết dữ liệu, không phải để tiếp tục
     * làm phiền cả họ.</p>
     *
     * <p><b>Sự kiện MỘT LẦN cũng được nhắc.</b> Trước lối ghi thủ công, bảng này chỉ chứa giỗ nên
     * "lặp hằng năm" và "có lịch nhắc" là một. Nay một buổi họp họ vào Chủ nhật tới là sự kiện cần
     * được nhắc nhất trong cả bảng, và nó không lặp lại năm nào. Khác biệt duy nhất nằm ở chỗ tính
     * ra <i>lần xảy ra</i>: lặp thì quét nhiều năm âm, một lần thì lấy đúng năm chép trong sổ.</p>
     */
    public boolean generatesReminders() {
        return !deleted;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Event event && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Event[" + id + " " + type + " " + (lunarBased ? String.valueOf(lunarDate) : String.valueOf(solarDate)) + "]";
    }

    /** Builder vì bảng {@code event} có 12 trường và constructor 12 tham số là chỗ để lẫn tham số. */
    public static final class Builder {
        private final UUID id;
        private UUID personId;
        private EventType type = EventType.KHAC;
        private String title;
        private String description;
        private LunarDate lunarDate;
        private LocalDate solarDate;
        private boolean lunarBased = true;
        private boolean recurring = true;
        private UUID targetBranchId;
        private boolean clanLevel;
        private String location;
        private boolean deleted;
        private long version;

        private Builder(UUID id) {
            this.id = id;
        }

        public Builder personId(UUID value) {
            this.personId = value;
            return this;
        }

        public Builder type(EventType value) {
            this.type = value;
            return this;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder lunarDate(LunarDate value) {
            this.lunarDate = value;
            return this;
        }

        public Builder solarDate(LocalDate value) {
            this.solarDate = value;
            return this;
        }

        public Builder lunarBased(boolean value) {
            this.lunarBased = value;
            return this;
        }

        public Builder recurring(boolean value) {
            this.recurring = value;
            return this;
        }

        public Builder targetBranchId(UUID value) {
            this.targetBranchId = value;
            return this;
        }

        public Builder clanLevel(boolean value) {
            this.clanLevel = value;
            return this;
        }

        public Builder location(String value) {
            this.location = value;
            return this;
        }

        public Builder deleted(boolean value) {
            this.deleted = value;
            return this;
        }

        public Builder version(long value) {
            this.version = value;
            return this;
        }

        public Event build() {
            return new Event(this);
        }
    }
}

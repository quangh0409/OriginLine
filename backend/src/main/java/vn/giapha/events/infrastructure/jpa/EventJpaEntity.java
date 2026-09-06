package vn.giapha.events.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bản chiếu của bảng {@code event} (V4 §4.1).
 *
 * <h2>Vài chỗ dễ sai, đã chốt ở đây</h2>
 * <ul>
 *   <li>{@code version} do Hibernate quản; trigger {@code tg_event_touch} cố ý <b>không</b> chạm cột
 *       này. Trigger tăng thêm một lần nữa thì lần cập nhật kế tiếp ném {@code OptimisticLockException}
 *       giả.</li>
 *   <li>{@code created_at}/{@code updated_at} do cơ sở dữ liệu đặt ({@code DEFAULT now()} + trigger),
 *       nên khai báo {@code insertable = false, updatable = false} — để Hibernate ghi đè thì thời gian
 *       sẽ theo đồng hồ của máy ứng dụng, và hai instance lệch giờ là đủ để nhật ký vô nghĩa.</li>
 *   <li>{@code lunar_date} là {@code jsonb} {@code {year, month, day, leap}} — cùng hình dạng với
 *       {@code person.death_lunar} để hai bên so được với nhau mà không phải chuyển đổi.</li>
 * </ul>
 */
@Entity
@Table(name = "event")
public class EventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "event_type", nullable = false, length = 24)
    private String eventType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = EventJsonbConverter.class)
    @Column(name = "lunar_date")
    private Map<String, Object> lunarDate;

    @Column(name = "solar_date")
    private LocalDate solarDate;

    @Column(name = "is_lunar_based", nullable = false)
    private boolean lunarBased = true;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = true;

    @Column(name = "target_branch_id")
    private UUID targetBranchId;

    @Column(name = "is_clan_level", nullable = false)
    private boolean clanLevel;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EventJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getLunarDate() {
        return lunarDate;
    }

    public void setLunarDate(Map<String, Object> lunarDate) {
        this.lunarDate = lunarDate;
    }

    public LocalDate getSolarDate() {
        return solarDate;
    }

    public void setSolarDate(LocalDate solarDate) {
        this.solarDate = solarDate;
    }

    public boolean isLunarBased() {
        return lunarBased;
    }

    public void setLunarBased(boolean lunarBased) {
        this.lunarBased = lunarBased;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public UUID getTargetBranchId() {
        return targetBranchId;
    }

    public void setTargetBranchId(UUID targetBranchId) {
        this.targetBranchId = targetBranchId;
    }

    public boolean isClanLevel() {
        return clanLevel;
    }

    public void setClanLevel(boolean clanLevel) {
        this.clanLevel = clanLevel;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

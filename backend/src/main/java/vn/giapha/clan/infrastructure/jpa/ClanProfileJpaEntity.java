package vn.giapha.clan.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Bản chiếu bảng {@code clan_profile} (V23). {@code ddl-auto=validate}: tên và kiểu cột phải khớp
 * từng chữ, nên ngày/tháng giỗ tổ là {@link Short} ({@code SMALLINT}) chứ không {@code Integer}.
 *
 * <p>Cột {@code singleton} không được ánh xạ: CSDL tự điền {@code TRUE} và không ai được đổi nó.</p>
 */
@Entity
@Table(name = "clan_profile")
public class ClanProfileJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "short_name", length = 60)
    private String shortName;

    /**
     * {@code ltree}: đọc bằng {@code getString()} (kiểu VARCHAR mặc định — xem javadoc trường
     * {@code path} của {@code BranchJpaEntity} về cái bẫy {@code OTHER → byte[]}), ghi thì ép kiểu
     * trong chính câu SQL. Khác {@code BranchJpaEntity} (chỉ đọc), bảng này CÓ đường ghi qua JPA
     * nên phép ép chiều ghi là bắt buộc — thiếu nó Postgres từ chối
     * "column is of type ltree but expression is of type character varying".
     */
    @Column(name = "root_branch_path", nullable = false, columnDefinition = "ltree")
    @ColumnTransformer(write = "CAST(? AS ltree)")
    private String rootBranchPath;

    @Column(name = "region", nullable = false, length = 10)
    private String region;

    @Column(name = "ancestral_hall_name", length = 200)
    private String ancestralHallName;

    @Column(name = "ancestral_hall_address", length = 500)
    private String ancestralHallAddress;

    @Column(name = "ancestor_name", length = 200)
    private String ancestorName;

    @Column(name = "founded_note", length = 2000)
    private String foundedNote;

    @Column(name = "gio_to_lunar_day")
    private Short gioToLunarDay;

    @Column(name = "gio_to_lunar_month")
    private Short gioToLunarMonth;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ClanProfileJpaEntity() {
    }

    public ClanProfileJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getRootBranchPath() {
        return rootBranchPath;
    }

    public void setRootBranchPath(String rootBranchPath) {
        this.rootBranchPath = rootBranchPath;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAncestralHallName() {
        return ancestralHallName;
    }

    public void setAncestralHallName(String ancestralHallName) {
        this.ancestralHallName = ancestralHallName;
    }

    public String getAncestralHallAddress() {
        return ancestralHallAddress;
    }

    public void setAncestralHallAddress(String ancestralHallAddress) {
        this.ancestralHallAddress = ancestralHallAddress;
    }

    public String getAncestorName() {
        return ancestorName;
    }

    public void setAncestorName(String ancestorName) {
        this.ancestorName = ancestorName;
    }

    public String getFoundedNote() {
        return foundedNote;
    }

    public void setFoundedNote(String foundedNote) {
        this.foundedNote = foundedNote;
    }

    public Short getGioToLunarDay() {
        return gioToLunarDay;
    }

    public void setGioToLunarDay(Short gioToLunarDay) {
        this.gioToLunarDay = gioToLunarDay;
    }

    public Short getGioToLunarMonth() {
        return gioToLunarMonth;
    }

    public void setGioToLunarMonth(Short gioToLunarMonth) {
        this.gioToLunarMonth = gioToLunarMonth;
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

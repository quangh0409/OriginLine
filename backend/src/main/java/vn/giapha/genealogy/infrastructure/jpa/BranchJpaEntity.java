package vn.giapha.genealogy.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code branch}.
 *
 * <p>Cột {@code path} có kiểu {@code ltree} — kiểu Hibernate không biết. Cách xử lý và lý do nằm ở
 * javadoc của chính trường {@code path}; đọc trước khi đổi phần đó.</p>
 */
@Entity
@Table(name = "branch")
public class BranchJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    /**
     * Ép kiểu <b>hai chiều</b> ngay trong câu SQL. Đây là chỗ dễ sai và đã từng sai:
     *
     * <p>Bản trước dùng {@code @JdbcTypeCode(SqlTypes.OTHER)}. Cách đó ghi thì được — driver gửi
     * tham số ở dạng {@code unknown} và Postgres tự ép sang {@code ltree}. Nhưng <b>đọc thì hỏng</b>:
     * với kiểu {@code OTHER}, pgjdbc trả giá trị về dưới dạng {@code byte[]}, và Hibernate chết với
     * một thông báo không hề nhắc tới ltree:</p>
     *
     * <pre>JpaSystemException: Could not convert '[B' to 'java.lang.String'
     *   using 'org.hibernate.type.descriptor.java.StringJavaType' to wrap</pre>
     *
     * <p>Lỗi này <b>không thể lộ ra khi bảng {@code branch} còn rỗng</b> — nó chỉ xuất hiện ở lần
     * đầu tiên thực sự nạp một Branch, tức là ngay khi có dữ liệu thật và {@code GET /api/v1/tree}
     * được gọi. Compile xanh, unit test xanh, ứng dụng khởi động xanh, rồi 500 ở đúng tính năng
     * chủ lực.</p>
     *
     * <p><b>Vì sao không cast {@code ::text} trong SQL cho xong:</b> đã thử, không ăn thua. Lỗi
     * không nằm ở phía SQL mà ở phía <i>đọc</i> — với {@code @JdbcTypeCode(SqlTypes.OTHER)} đặt
     * trên một trường {@link String}, Hibernate 6 phân giải ra {@code VarbinaryJdbcType}, tức là
     * gọi {@code rs.getBytes()}. Cột có được cast sang {@code text} hay không cũng vô nghĩa: nó
     * vẫn đọc ra {@code byte[]} rồi chết ở {@code StringJavaType.wrap}. Stack trace chỉ thẳng vào
     * {@code VarbinaryJdbcType$2.doExtract}.</p>
     *
     * <p><b>Lời giải:</b> để nguyên kiểu {@code VARCHAR} mặc định, tức Hibernate gọi
     * {@code rs.getString()} — mà {@code getString()} của pgjdbc trả về đúng dạng chữ của
     * {@code ltree} cho cả truy vấn dẫn xuất lẫn native {@code SELECT *}.
     * {@code columnDefinition = "ltree"} để {@code ddl-auto=validate} biết kiểu thật của cột và
     * không đòi {@code varchar(255)}.</p>
     *
     * <p>Cổng {@code BranchRepository} là <b>chỉ đọc</b> (không có {@code save}) nên không cần lo
     * phép ép kiểu chiều ghi ở đây; chi được tạo bằng migration và bộ nạp dữ liệu qua JDBC thuần.
     * Nếu sau này thêm đường ghi qua JPA thì phải bổ sung
     * {@code @ColumnTransformer(write = "?::ltree")}.</p>
     */
    @Column(name = "path", nullable = false, columnDefinition = "ltree")
    private String path;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "branch_kind", nullable = false, length = 10)
    private String branchKind;

    @Column(name = "region", length = 10)
    private String region;

    @Column(name = "head_person_id")
    private UUID headPersonId;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "note")
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BranchJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getPath() {
        return path;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getBranchKind() {
        return branchKind;
    }

    public String getRegion() {
        return region;
    }

    public UUID getHeadPersonId() {
        return headPersonId;
    }

    public Integer getFoundedYear() {
        return foundedYear;
    }

    public String getNote() {
        return note;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getVersion() {
        return version;
    }
}

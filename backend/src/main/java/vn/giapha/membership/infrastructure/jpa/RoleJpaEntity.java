package vn.giapha.membership.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Bản chiếu bảng {@code role} — <b>danh mục</b> vai trò, do V5 seed sẵn năm dòng.
 *
 * <p>Không có phương thức ghi: bảng này là dữ liệu tham chiếu bất biến trong Giai đoạn 1. Thêm một
 * vai mới đồng nghĩa với sửa {@code ck_role_code}, tức là một migration, chứ không phải một câu
 * INSERT lúc chạy.</p>
 */
@Entity
@Table(name = "role")
public class RoleJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "rank", nullable = false)
    private int rank;

    protected RoleJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getRank() {
        return rank;
    }
}

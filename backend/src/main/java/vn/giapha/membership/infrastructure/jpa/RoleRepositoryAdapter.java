package vn.giapha.membership.infrastructure.jpa;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vn.giapha.membership.domain.Role;
import vn.giapha.membership.domain.RoleCode;
import vn.giapha.membership.domain.port.RoleRepository;

/**
 * Hiện thực {@link RoleRepository}.
 *
 * <p>Một dòng có {@code code} không nằm trong {@link RoleCode} bị <b>bỏ qua</b> kèm WARN thay vì
 * làm hỏng cả truy vấn: nếu {@code ck_role_code} được nới ra trong một migration mà enum chưa kịp
 * theo, vai chưa biết phải được coi là không có quyền gì — không được ném lỗi, và tuyệt đối không
 * được coi là quyền cao nhất.</p>
 */
@Repository
public class RoleRepositoryAdapter implements RoleRepository {

    private static final Logger log = LoggerFactory.getLogger(RoleRepositoryAdapter.class);

    private final RoleJpaRepository jpa;

    public RoleRepositoryAdapter(RoleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Role> all() {
        return jpa.findAll().stream()
                .map(RoleRepositoryAdapter::toDomain)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Role::rank).reversed())
                .toList();
    }

    @Override
    public Optional<Role> byCode(RoleCode code) {
        if (code == null) {
            return Optional.empty();
        }
        return jpa.findByCode(code.name()).map(RoleRepositoryAdapter::toDomain);
    }

    private static Role toDomain(RoleJpaEntity entity) {
        RoleCode code = RoleCode.parse(entity.getCode());
        if (code == null) {
            log.warn("Bo qua dong danh muc role co ma khong nhan dien duoc: {}", entity.getCode());
            return null;
        }
        return new Role(entity.getId(), code, entity.getName(), entity.getDescription(),
                entity.getRank());
    }
}

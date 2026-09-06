package vn.giapha.genealogy.infrastructure.jpa;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy cập bảng {@code person_name}.
 *
 * <p>Xoá <b>được</b> phép ở đây và chỉ ở đây: một lớp tên bị gỡ khỏi hồ sơ không làm đứt cây, khác
 * hẳn với nhân khẩu. {@code PATCH names} có ngữ nghĩa thay thế toàn bộ danh sách nên adapter cần
 * xoá những dòng không còn.</p>
 */
public interface PersonNameJpaRepository extends JpaRepository<PersonNameJpaEntity, UUID> {

    List<PersonNameJpaEntity> findByPersonId(UUID personId);

    List<PersonNameJpaEntity> findByPersonIdIn(Collection<UUID> personIds);

    void deleteByPersonId(UUID personId);
}

package vn.giapha.membership.domain.port;

import java.util.List;
import java.util.Optional;
import vn.giapha.membership.domain.Role;
import vn.giapha.membership.domain.RoleCode;

/**
 * Đọc danh mục vai trò. <b>Chỉ đọc</b> — năm dòng được V5 seed và ràng buộc bởi
 * {@code ck_role_code}; thêm vai mới là việc của một migration.
 */
public interface RoleRepository {

    /** Toàn bộ danh mục, xếp theo thứ bậc giảm dần. */
    List<Role> all();

    Optional<Role> byCode(RoleCode code);
}

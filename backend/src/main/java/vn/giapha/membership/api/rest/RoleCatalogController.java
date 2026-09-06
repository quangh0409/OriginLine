package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.domain.Role;
import vn.giapha.membership.domain.port.RoleRepository;

/**
 * Danh mục vai trò — {@code /api/v1/roles}. Chỉ đọc.
 *
 * <p>Tồn tại để màn hình phân quyền hiển thị được tên và mô tả tiếng Việt của từng vai mà không
 * phải hard-code chuỗi trong frontend. Đây là dữ liệu tham chiếu, không phải dữ liệu cá nhân, nên
 * mọi tài khoản đã đăng nhập đều đọc được.</p>
 *
 * <p><b>Nhắc lại điều dễ nhầm:</b> vai trong danh mục này là vai <i>kỹ thuật</i>. Chức danh dòng
 * tộc — Tộc trưởng, Trưởng chi theo huyết thống/đích tôn — nằm ở {@code branch.head_person_id} của
 * context {@code genealogy} và là một dữ kiện hoàn toàn độc lập.</p>
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "Danh mục vai trò kỹ thuật")
public class RoleCatalogController {

    private final RoleRepository roles;

    public RoleCatalogController(RoleRepository roles) {
        this.roles = roles;
    }

    @GetMapping
    @Operation(summary = "Danh mục vai trò, xếp theo thứ bậc giảm dần")
    public List<Role> all() {
        return roles.all();
    }
}

package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.MeDto;
import vn.giapha.membership.application.AppUserProvisioningService;
import vn.giapha.membership.application.MemberScopeService;

/**
 * Hồ sơ phiên làm việc — {@code /api/v1/me}.
 *
 * <h2>Đây cũng là chỗ tài khoản được khởi tạo</h2>
 * Không có bước "đăng ký" riêng. Frontend gọi endpoint này ngay sau khi Keycloak trả token, và lần
 * gọi đầu tiên tạo ra dòng {@code app_user} ở trạng thái {@code PENDING}. Gộp hai việc vào một
 * endpoint tránh được tình trạng token hợp lệ mà mọi API khác trả 403 vì chưa ai bấm "tạo tài
 * khoản".
 *
 * <p>Trạng thái {@code PENDING} chưa gắn với nhân khẩu nào nên chưa thấy được gì ngoài dữ liệu
 * công khai. Ghép vào cây là quyết định của Hội đồng Tộc biểu, không phải hệ quả của một lần đăng
 * nhập.</p>
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me", description = "Hồ sơ phiên làm việc và phạm vi phân quyền")
public class MeController {

    private final MemberScopeService scopes;
    private final AppUserProvisioningService provisioning;

    public MeController(MemberScopeService scopes, AppUserProvisioningService provisioning) {
        this.scopes = scopes;
        this.provisioning = provisioning;
    }

    /**
     * Vai trò <b>và</b> phạm vi chi/ngành của người đang đăng nhập.
     *
     * <p>Giao diện dùng {@code managedBranches} để quyết định hiện hay ẩn nút "Duyệt". Đây thuần
     * tuý là gợi ý cho giao diện — mọi phép kiểm quyền thật nằm ở backend.</p>
     */
    @GetMapping
    @Operation(summary = "Tài khoản, nhân khẩu tương ứng và phạm vi chi/ngành")
    public MeDto me() {
        provisioning.ensureCurrentUser();
        return MeDto.from(scopes.currentScope());
    }
}

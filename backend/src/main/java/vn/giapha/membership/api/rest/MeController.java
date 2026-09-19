package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.MeDto;
import vn.giapha.membership.api.rest.dto.MePersonDto;
import vn.giapha.membership.application.AppUserProvisioningService;
import vn.giapha.membership.application.MemberScopeService;
import vn.giapha.membership.application.MemberScopeView;
import vn.giapha.membership.application.MembershipProblemCodes;
import vn.giapha.shared.exception.NotFoundException;

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

    /**
     * Nhân khẩu của chính người đang đăng nhập — {@code 409} nếu chưa được ghép vào phả.
     *
     * <h2>Vì sao {@code 409} chứ không phải {@code 404}</h2>
     * "Đăng nhập được nhưng chưa ai ghép vào phả" là một trạng thái <b>bình thường và có thật</b>:
     * người vừa được mời vào, Trưởng chi chưa kịp gắn họ với một nhân khẩu. Trả {@code 404} khiến
     * giao diện vẽ màn "không tìm thấy trang" cho một người vừa nhập đúng mật khẩu; trả {@code 403}
     * thì nói sai rằng họ bị từ chối, trong khi họ <b>sẽ</b> được phép ngay khi có người bấm nút
     * ghép. Lập luận đầy đủ nằm ở {@code GlobalExceptionHandler.accountNotProvisioned}.
     *
     * <p>Ném {@link NotFoundException} mang mã {@code ACCOUNT_NOT_PROVISIONED} là <b>đúng lối đã
     * có</b>, không phải lách: {@code AppUserProvisioningService.requireCurrentUser} ném y hệt như
     * vậy, và {@code GlobalExceptionHandler} nhận ra mã ấy rồi dịch sang {@code 409} cho <i>mọi</i>
     * nơi ném — nên hai chỗ không thể lệch nhau. Mã HTTP là quyết định của tầng lỗi, còn ở đây chỉ
     * nói đúng chuyện gì đã xảy ra.</p>
     */
    @GetMapping("/person")
    @Operation(summary = "Nhân khẩu ứng với tài khoản đang đăng nhập",
            description = "409 ACCOUNT_NOT_PROVISIONED khi tài khoản chưa được ghép vào phả.")
    public MePersonDto myPerson() {
        provisioning.ensureCurrentUser();
        MemberScopeView scope = scopes.currentScope();
        if (scope.personId() == null) {
            throw new NotFoundException(MembershipProblemCodes.ACCOUNT_NOT_PROVISIONED,
                    "Tai khoan chua duoc ghep voi mot nhan khau trong pha");
        }
        return MePersonDto.from(scope);
    }
}

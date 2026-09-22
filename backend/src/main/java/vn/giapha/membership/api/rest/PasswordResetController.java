package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.IssuePasswordResetRequest;
import vn.giapha.membership.api.rest.dto.IssuedPasswordResetDto;
import vn.giapha.membership.application.PasswordResetGrantService;
import vn.giapha.membership.application.command.IssuePasswordResetCommand;

/**
 * <b>Cấp lại liên kết đặt mật khẩu</b> — {@code /api/v1/password-resets}.
 *
 * <h2>Vì sao có endpoint này</h2>
 * Realm bật {@code resetPasswordAllowed: true} nhưng đường phục hồi duy nhất là <b>thư điện tử</b>,
 * trong khi sản phẩm cho đăng ký bằng email <i>hoặc</i> số điện thoại — và lối số điện thoại sinh
 * ra chính vì các cụ không có email. Người đăng ký bằng số máy mà quên mật khẩu thì trước đây không
 * có đường nào tự lấy lại; giao diện phải bảo họ "gọi Trưởng chi". Endpoint này là cái mà cuộc gọi
 * ấy dẫn tới.
 *
 * <h2>Chỉ có MỘT nửa ở đây, và đó là nửa quản trị</h2>
 * Nửa còn lại — người quên mật khẩu bấm liên kết rồi tự đặt mật khẩu — <b>không nằm ở controller
 * này</b>. Nó đi vào {@code POST /api/v1/invitations/set-password}, cùng endpoint và cùng màn
 * {@code /dat-mat-khau} với luồng mời, vì người dùng chỉ có một màn ấy và khuôn địa chỉ đã nằm
 * trong cấu hình {@code giapha.keycloak.admin.set-password-url}. Hai loại token phân biệt nhau bằng
 * <b>khoá ký</b> — xem {@code PasswordResetRedeemer}.
 *
 * <p><b>Đừng thêm một endpoint đổi mật khẩu thứ hai ở đây</b> cho "đối xứng". Hai đường nhận mật
 * khẩu người dùng gõ là hai chỗ phải cùng đúng về ghi log, về không-lưu-mật-khẩu và về thông điệp
 * lỗi; chúng sẽ lệch nhau ở lần sửa đầu tiên.</p>
 *
 * <h2>Phân quyền: vai <i>và</i> phạm vi chi, như mọi thao tác ghi khác</h2>
 * {@code BranchScopeGuard.requireWriteAccess} trên chi của nhân khẩu đích — Trưởng chi trong chi
 * mình, Hội đồng toàn họ. Cộng một luật riêng của đường này: <b>không ai tự cấp cho chính mình</b>
 * ({@code 403 SELF_PASSWORD_RESET_FORBIDDEN}).
 *
 * <h2>Không có {@code GET} liệt kê, và đó là một quyết định</h2>
 * Một danh sách "ai đã cấp lại mật khẩu cho ai" là đúng thứ dùng để <i>chọn mục tiêu</i>: nó nói
 * thẳng ra tài khoản nào không có email, tức tài khoản nào cấp lại được. Câu hỏi tra soát thật thì
 * {@code audit_log} trả lời đầy đủ hơn và đã có sẵn đường đọc có kiểm quyền. Thêm {@code GET} ở đây
 * khi nào có một màn hình thật cần tới nó, và lúc ấy nó phải lọc theo phạm vi chi.
 */
@RestController
@RequestMapping("/api/v1/password-resets")
@Tag(name = "password-resets",
        description = "Trưởng chi/Hội đồng cấp lại liên kết đặt mật khẩu cho thành viên")
public class PasswordResetController {

    private final PasswordResetGrantService grants;

    public PasswordResetController(PasswordResetGrantService grants) {
        this.grants = grants;
    }

    /**
     * Cấp lại liên kết đặt mật khẩu cho một thành viên. Phản hồi chứa liên kết, và đó là
     * <b>lần duy nhất</b> nó xuất hiện.
     *
     * <p>Trả {@code 201} nhưng <b>không kèm {@code Location}</b>: không có endpoint nào đọc lại một
     * lượt cấp, và một {@code Location} trỏ vào chỗ không tồn tại là một lời hứa suông trong hợp
     * đồng API. (Ở {@code POST /invitations} thì khác — bản ghi lời mời đọc lại được.)</p>
     *
     * <p>Các mã lỗi: {@code 403 BRANCH_SCOPE_VIOLATION} (đúng vai, sai chi) ·
     * {@code 403 SELF_PASSWORD_RESET_FORBIDDEN} (tự cấp cho mình) ·
     * {@code 422 PASSWORD_RESET_NOT_ALLOWED} (chưa có tài khoản / tài khoản không ACTIVE / tài
     * khoản <b>có email</b> nên tự lấy lại được) · {@code 429 RATE_LIMITED} ·
     * {@code 503 IDENTITY_PROVIDER_UNAVAILABLE}.</p>
     */
    @PostMapping
    @Operation(summary = "Cấp lại liên kết đặt mật khẩu cho một thành viên trong phạm vi chi/ngành")
    public ResponseEntity<IssuedPasswordResetDto> issue(
            @Valid @RequestBody IssuePasswordResetRequest body) {
        IssuedPasswordResetDto issued = IssuedPasswordResetDto.from(grants.issue(
                new IssuePasswordResetCommand(body.personId(), body.note())));
        return ResponseEntity.status(201).body(issued);
    }
}

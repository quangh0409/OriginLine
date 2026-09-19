package vn.giapha.membership.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.giapha.membership.api.rest.dto.AcceptedInvitationDto;
import vn.giapha.membership.api.rest.dto.AcceptInvitationRequest;
import vn.giapha.membership.api.rest.dto.InvitationDto;
import vn.giapha.membership.api.rest.dto.InvitationPreviewDto;
import vn.giapha.membership.api.rest.dto.IssueInvitationRequest;
import vn.giapha.membership.api.rest.dto.IssuedInvitationDto;
import vn.giapha.membership.api.rest.dto.RedeemInvitationRequest;
import vn.giapha.membership.api.rest.dto.SetPasswordRequest;
import vn.giapha.membership.application.InvitationService;
import vn.giapha.membership.application.SetPasswordService;
import vn.giapha.membership.application.command.AcceptInvitationCommand;
import vn.giapha.membership.application.command.IssueInvitationCommand;

/**
 * Luồng mời người vào hệ thống — {@code /api/v1/invitations}.
 *
 * <h2>Hai nửa với hai chế độ xác thực khác hẳn nhau</h2>
 * <ul>
 *   <li><b>Nửa quản trị</b> ({@code POST /}, {@code GET /}, {@code DELETE /&#123;id&#125;}) đòi
 *       token và đi qua {@code BranchScopeGuard}: Trưởng chi thao tác trong chi mình, Hội đồng toàn
 *       họ.</li>
 *   <li><b>Nửa của người được mời</b> ({@code /lookup}, {@code /accept}, {@code /decline},
 *       {@code /set-password}) <b>không đòi token</b> — người bấm chính là người chưa có tài khoản.
 *       Thẩm quyền ở bốn đường này là <b>việc sở hữu bí mật</b>: mã mời ở ba đường đầu, token của
 *       liên kết đặt mật khẩu ở đường cuối.</li>
 * </ul>
 *
 * <p>{@code /accept} từng đòi token, và đó là chỗ đứt của cả luồng: người được mời phải <i>đã</i>
 * có tài khoản mới nhận được lời mời, trong khi realm đặt {@code registrationAllowed: false} nên
 * không ai lập được tài khoản ấy cho họ. Nay nó lập tài khoản Keycloak ngay tại chỗ và trả về
 * {@code setPasswordUrl}.</p>
 *
 * <h2>Vì sao cả ba đường dùng mã đều là {@code POST}, kể cả đường chỉ đọc</h2>
 * Mã mời là bí mật. Đặt nó vào đường dẫn là đặt nó vào access log của reverse proxy, lịch sử trình
 * duyệt, header {@code Referer} và mọi hệ thống giám sát đang gom URL. Thân của một {@code POST}
 * không đi vào chỗ nào trong số đó — xem {@link RedeemInvitationRequest}.
 *
 * <h2>{@code /lookup} trả về tên một người đang sống, và điều đó được chốt là chấp nhận được</h2>
 * Đây là ngoại lệ duy nhất của quy tắc "người sống ẩn mặc định" (BA v2 §10) trong context này, đã
 * cân nhắc ở design 06 §5.3 và chọn có ý thức. Ba lớp chống đỡ bắt buộc đi kèm: mã dùng một lần ·
 * hạn ngắn · giới hạn tần suất theo người gọi.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "invitations", description = "Mời người vào hệ thống — phát, nhận, thu hồi")
public class InvitationController {

    private final InvitationService invitations;
    private final SetPasswordService setPasswords;

    public InvitationController(InvitationService invitations, SetPasswordService setPasswords) {
        this.invitations = invitations;
        this.setPasswords = setPasswords;
    }

    // -------------------------------------------------------------------------------------
    // Nửa quản trị — đòi token
    // -------------------------------------------------------------------------------------

    /**
     * Phát một lời mời. Phản hồi chứa mã thô, và đó là <b>lần duy nhất</b> mã xuất hiện.
     *
     * <p>Trả {@code 201} kèm {@code Location} trỏ tới bản ghi lời mời — bản ghi ấy <i>không</i>
     * chứa mã, nên đường dẫn trong {@code Location} an toàn để ghi log.</p>
     */
    @PostMapping
    @Operation(summary = "Phát lời mời cho một nhân khẩu (Trưởng chi trong phạm vi chi mình; Hội đồng toàn họ)")
    public ResponseEntity<IssuedInvitationDto> issue(@Valid @RequestBody IssueInvitationRequest body) {
        IssuedInvitationDto issued = IssuedInvitationDto.from(invitations.issue(
                new IssueInvitationCommand(body.personId(), body.ttlDays(), body.note())));
        return ResponseEntity
                .created(URI.create("/api/v1/invitations/" + issued.invitation().id()))
                .body(issued);
    }

    @GetMapping
    @Operation(summary = "Lời mời trong phạm vi chi/ngành của người gọi")
    public List<InvitationDto> inScope(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return invitations.inScope(page, size).stream().map(InvitationDto::from).toList();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Thu hồi một lời mời còn mở")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                       @RequestParam(required = false) String reason) {
        invitations.revoke(id, reason);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------------------
    // Nửa của người được mời — mã là thẩm quyền
    // -------------------------------------------------------------------------------------

    /**
     * "Lời mời này dành cho ai" — không cần token.
     *
     * <p>Bắt đăng nhập ở bước này là bắt người nhận đặt mật khẩu cho một thứ họ chưa xác nhận là
     * của mình, và là đúng chỗ mất người của nhóm 70 tuổi.</p>
     */
    @PostMapping("/lookup")
    @SecurityRequirements
    @Operation(summary = "Xem lời mời (không cần token)")
    public InvitationPreviewDto lookup(@Valid @RequestBody RedeemInvitationRequest body,
                                       HttpServletRequest request) {
        return InvitationPreviewDto.from(invitations.preview(body.code(), clientIdOf(request)));
    }

    /**
     * "Đúng là tôi — nhận lời mời". <b>Không cần token</b>; thẩm quyền là việc sở hữu mã, và quyền
     * đã được kiểm một lần rồi, lúc phát.
     *
     * <p>Người <b>chưa có tài khoản</b> khai địa chỉ thư của mình: hệ thống lập tài khoản Keycloak
     * cho họ và trả về {@code setPasswordUrl} — một liên kết một lần để chính họ đặt mật khẩu.
     * Người <b>đã đăng nhập</b> gửi kèm token thì email bị bỏ qua và không có liên kết nào được
     * phát: họ đã có cách vào hệ thống.</p>
     *
     * <p>Phản hồi cho thấy tài khoản đã {@code ACTIVE} và đã có {@code personId} — không có bước
     * chờ duyệt nào ở giữa.</p>
     *
     * <p>Endpoint này chạm vào <b>hai hệ thống</b> (Keycloak và Postgres) mà không có transaction
     * chung. Thứ tự thao tác được chọn sao cho mọi hỏng hóc đều rơi về một trạng thái lành: mã mời
     * chưa bị đánh dấu đã dùng, bấm lại là được. Lập luận đầy đủ nằm ở
     * {@code InvitationService.accept}.</p>
     */
    @PostMapping("/accept")
    @SecurityRequirements
    @Operation(summary = "Nhận lời mời — lập tài khoản nếu chưa có, gắn ngay với nhân khẩu")
    public AcceptedInvitationDto accept(@Valid @RequestBody AcceptInvitationRequest body,
                                        HttpServletRequest request) {
        return AcceptedInvitationDto.from(invitations.accept(new AcceptInvitationCommand(
                body.code(), body.email(), body.displayName(), clientIdOf(request))));
    }

    /**
     * Người được mời tự đặt mật khẩu qua liên kết một lần — <b>không cần token</b>.
     *
     * <p>Đòi token ở đây là vô nghĩa: người bấm chính là người chưa đăng nhập được, vì họ chưa có
     * mật khẩu. Thẩm quyền là token trong {@code setPasswordUrl} — ký HMAC, hạn nửa giờ, và chỉ
     * dùng được khi tài khoản đích còn chưa có mật khẩu nào.</p>
     *
     * <p>Trả {@code 204}: không có gì để trả về, và nhất là không trả lại bất cứ mảnh nào của thứ
     * vừa nhận. Sau bước này người dùng đăng nhập bằng Keycloak như mọi thành viên khác.</p>
     */
    @PostMapping("/set-password")
    @SecurityRequirements
    @Operation(summary = "Đặt mật khẩu lần đầu qua liên kết một lần (không cần token)")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest body) {
        setPasswords.setPassword(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * "Không phải tôi" — huỷ mã, không cần token.
     *
     * <p>Đòi token ở đây là vô hiệu hoá nút: người bấm chính là người <i>không</i> có tài khoản.</p>
     */
    @PostMapping("/decline")
    @SecurityRequirements
    @Operation(summary = "Từ chối lời mời — mã chết ngay (không cần token)")
    public ResponseEntity<Void> decline(@Valid @RequestBody RedeemInvitationRequest body,
                                        HttpServletRequest request) {
        invitations.decline(body.code(), clientIdOf(request));
        return ResponseEntity.noContent().build();
    }

    /**
     * Định danh người gọi cho bộ đếm giới hạn tần suất.
     *
     * <p>Lấy {@code getRemoteAddr()} chứ không tự đọc {@code X-Forwarded-For}: một header do client
     * gửi lên thì client cũng bịa được, và tin nó nghĩa là ai cũng bỏ qua được giới hạn bằng một
     * header. Khi hệ thống chạy sau reverse proxy thật, chỗ đúng để dịch header ấy là
     * {@code ForwardedHeaderFilter} / {@code server.forward-headers-strategy}, nơi có thể khai báo
     * proxy nào đáng tin.</p>
     *
     * <p>Giá trị được băm ngay ở tầng application trước khi chạm CSDL — IP là dữ liệu cá nhân theo
     * Nghị định 13/2023.</p>
     */
    private static String clientIdOf(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }
}

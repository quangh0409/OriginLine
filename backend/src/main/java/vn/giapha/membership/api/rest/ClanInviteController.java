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
import vn.giapha.membership.api.rest.dto.ClanInviteListDto;
import vn.giapha.membership.api.rest.dto.ClanInvitePreviewDto;
import vn.giapha.membership.api.rest.dto.ClanInviteRedemptionDto;
import vn.giapha.membership.api.rest.dto.ClanRegistrationDto;
import vn.giapha.membership.api.rest.dto.IssueClanInviteRequest;
import vn.giapha.membership.api.rest.dto.IssuedClanInviteDto;
import vn.giapha.membership.api.rest.dto.RedeemClanInviteRequest;
import vn.giapha.membership.api.rest.dto.RegisterWithClanInviteRequest;
import vn.giapha.membership.application.ClanInviteService;
import vn.giapha.membership.application.command.IssueClanInviteCommand;
import vn.giapha.membership.application.command.RegisterWithClanInviteCommand;

/**
 * Mã mời <b>dòng họ</b> — {@code /api/v1/clan-invites}.
 *
 * <h2>Hai nửa với hai chế độ xác thực khác hẳn nhau</h2>
 * <ul>
 *   <li><b>Nửa của Hội đồng</b> ({@code POST /}, {@code GET /}, {@code GET /&#123;id&#125;/redemptions},
 *       {@code DELETE /&#123;id&#125;}) đòi token và đòi <b>phạm vi toàn dòng họ</b>. Khác
 *       {@code InvitationController}, nơi Trưởng chi phát được trong chi mình: một mã cá nhân rò ra
 *       mở đúng một hồ sơ, còn một mã dòng họ rò ra mở <b>cả dòng họ</b> — quyền phát phải đi cùng
 *       phạm vi hậu quả.</li>
 *   <li><b>Nửa của người cầm mã</b> ({@code /lookup}, {@code /register}) <b>không đòi token</b> —
 *       người bấm chính là người chưa có tài khoản. Thẩm quyền ở đây là <b>việc sở hữu mã</b>.</li>
 * </ul>
 *
 * <h2>Cả hai đường dùng mã đều là {@code POST}, kể cả đường chỉ đọc</h2>
 * Mã là bí mật. Đặt nó vào đường dẫn là đặt nó vào access log của reverse proxy, lịch sử trình
 * duyệt, header {@code Referer} và mọi hệ thống giám sát đang gom URL. Thân của một {@code POST}
 * không đi vào chỗ nào trong số đó.
 *
 * <h2>{@code /register} KHÔNG gắn tài khoản vào nhân khẩu nào</h2>
 * Đây là khác biệt lớn nhất với {@code POST /invitations/accept}. Mã dòng họ không trỏ vào ai nên
 * không có gì để ghép, và <b>không được suy đoán</b>: gắn theo trùng tên là cách nhanh nhất để trao
 * cho một người quyền xem dữ liệu Tầng 3 của người khác. Người đăng ký xem được phả đồ ngay, rồi
 * gửi đơn qua {@link PersonClaimController} và chờ Trưởng chi duyệt.
 *
 * <h2>Đặt mật khẩu dùng lại {@code POST /invitations/set-password}</h2>
 * Cố ý không có đường {@code /clan-invites/set-password}. Liên kết đặt mật khẩu là cùng một token
 * ký HMAC, cùng hạn, cùng luật "chỉ dùng được khi tài khoản chưa có mật khẩu". Hai endpoint cho
 * cùng một việc là hai chỗ để lệch nhau.
 */
@RestController
@RequestMapping("/api/v1/clan-invites")
@Tag(name = "clan-invites", description = "Mã mời dòng họ — phát, thu hồi, đăng ký bằng mã")
public class ClanInviteController {

    private final ClanInviteService clanInvites;

    public ClanInviteController(ClanInviteService clanInvites) {
        this.clanInvites = clanInvites;
    }

    // -------------------------------------------------------------------------------------
    // Nửa của Hội đồng — đòi token và phạm vi toàn dòng họ
    // -------------------------------------------------------------------------------------

    /**
     * Phát một mã dòng họ. Phản hồi chứa mã thô, và đó là <b>lần duy nhất</b> mã xuất hiện.
     *
     * <p>Trả {@code 201} kèm {@code Location} trỏ tới bản ghi mã — bản ghi ấy <i>không</i> chứa mã,
     * nên đường dẫn trong {@code Location} an toàn để ghi log.</p>
     */
    @PostMapping
    @Operation(summary = "Phát mã mời cho cả dòng họ (chỉ Hội đồng Tộc biểu / Quản trị hệ thống)")
    public ResponseEntity<IssuedClanInviteDto> issue(
            @Valid @RequestBody(required = false) IssueClanInviteRequest body) {
        IssueClanInviteCommand command = body == null
                ? new IssueClanInviteCommand(null, null, null, null)
                : new IssueClanInviteCommand(body.label(), body.ttlDays(), body.maxUses(),
                        body.note());
        IssuedClanInviteDto issued = IssuedClanInviteDto.from(clanInvites.issue(command));
        return ResponseEntity
                .created(URI.create("/api/v1/clan-invites/" + issued.invite().id()))
                .body(issued);
    }

    /**
     * Danh sách mã <b>kèm bộ đếm</b> — màn hình làm chốt "đếm lượt dùng" có tác dụng.
     *
     * <p>Giao diện nên hiển thị {@code usability} chứ không phải {@code status}: {@code ACTIVE} của
     * một mã đã quá hạn hoặc đã hết lượt không nói lên điều gì cho người đang nhìn danh sách.</p>
     *
     * <p>Phản hồi <b>bọc</b> danh sách trong {@code { invites, clanLivingPersonCount }}. Con số thứ
     * hai là <b>mẫu số</b> của bộ đếm: lập luận của quyết định "cấp mã cho cả họ" là <i>"mã đã dùng
     * 400 lần trong khi dòng họ có 600 người"</i>, và không có vế thứ hai thì vế thứ nhất không nói
     * lên điều gì — chốt được gọi là quan trọng nhất trở thành một con số trang trí. Nó không thuộc
     * về mã nào nên không nằm trong phần tử nào.</p>
     */
    @GetMapping
    @Operation(summary = "Danh sách mã mời dòng họ, kèm bộ đếm lượt dùng và mẫu số của nó")
    public ClanInviteListDto list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return ClanInviteListDto.from(clanInvites.list(page, size));
    }

    /**
     * <b>Ai đã dùng mã này</b> — thứ truy được người đưa mã ra ngoài.
     *
     * <p>Bộ đếm nói <i>bao nhiêu</i> lượt; danh sách này nói <i>ai</i>. Khi bộ đếm nhảy bất thường
     * thì đây là câu hỏi tiếp theo.</p>
     */
    @GetMapping("/{id}/redemptions")
    @Operation(summary = "Ai đã dùng mã này (chỉ Hội đồng Tộc biểu / Quản trị hệ thống)")
    public List<ClanInviteRedemptionDto> redemptions(@PathVariable UUID id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return clanInvites.redemptionsOf(id, page, size).stream()
                .map(ClanInviteRedemptionDto::from)
                .toList();
    }

    /**
     * Thu hồi một mã.
     *
     * <p><b>Người đã vào bằng mã này không bị ảnh hưởng</b>: tài khoản của họ đã tồn tại và không
     * treo vào mã. Và không có phép mở lại — cần mã mới thì phát mã mới; mã cũ ở lại với bộ đếm của
     * nó, vì chính con số ấy là thứ đáng giữ.</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Thu hồi một mã mời dòng họ")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                       @RequestParam(required = false) String reason) {
        clanInvites.revoke(id, reason);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------------------
    // Nửa của người cầm mã — mã là thẩm quyền
    // -------------------------------------------------------------------------------------

    /**
     * "Mã này của dòng họ nào" — không cần token.
     *
     * <p>Màn đăng ký gọi đường này <b>trước</b> khi thu thập email: mã phải được kiểm trước khi tài
     * khoản được tạo, không phải tạo rồi mới hỏi. Tạo trước thì một lần gõ sai mã cũng để lại một
     * tài khoản Keycloak mồ côi, và realm đặt {@code duplicateEmailsAllowed: false} nên lần thử
     * lại của chính người ấy sẽ hỏng.</p>
     */
    @PostMapping("/lookup")
    @SecurityRequirements
    @Operation(summary = "Kiểm mã mời dòng họ (không cần token)")
    public ClanInvitePreviewDto lookup(@Valid @RequestBody RedeemClanInviteRequest body,
                                       HttpServletRequest request) {
        return ClanInvitePreviewDto.from(
                clanInvites.preview(body.code(), clientIdOf(request)));
    }

    /**
     * Đăng ký tài khoản bằng mã dòng họ — <b>không cần token</b>.
     *
     * <p>Trả về {@code setPasswordUrl}, một liên kết một lần dẫn tới
     * {@code POST /api/v1/invitations/set-password}. Liên kết <b>vắng mặt</b> khi tài khoản đã có
     * mật khẩu — lúc ấy đưa người dùng tới màn đăng nhập.</p>
     *
     * <p>Tài khoản ra khỏi đây ở trạng thái {@code PENDING} và <b>không có {@code personId}</b>.
     * Đó là trạng thái đúng, không phải bước dang dở: bước tiếp theo là xem phả đồ rồi tự nhận
     * mình.</p>
     *
     * <p><b>Gọi kèm token cũng hợp lệ</b>, và đó là lối của người đăng nhập Google/Zalo: realm cho
     * phép social login, nên một người con cháu hoàn toàn có thể có tài khoản Keycloak trước khi có
     * mã mời. Lúc ấy danh tính lấy từ token, {@code email} trong thân bị bỏ qua, và
     * {@code setPasswordUrl} <b>vắng mặt</b> — họ vừa đăng nhập được thì đã có cách đăng nhập, và
     * phát liên kết đổi mật khẩu cho một tài khoản đang dùng được là mở lối ấy cho bất kỳ ai cầm mã
     * dòng họ. Mà mã ấy thì cả họ đang cầm.</p>
     */
    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Đăng ký tài khoản bằng mã mời dòng họ (không cần token)")
    public ClanRegistrationDto register(@Valid @RequestBody RegisterWithClanInviteRequest body,
                                        HttpServletRequest request) {
        return ClanRegistrationDto.from(clanInvites.register(new RegisterWithClanInviteCommand(
                body.code(), body.dinhDanhDangNhap(), body.displayName(), clientIdOf(request))));
    }

    /**
     * Định danh người gọi cho bộ đếm giới hạn tần suất.
     *
     * <p>Lấy {@code getRemoteAddr()} chứ không tự đọc {@code X-Forwarded-For}: một header do client
     * gửi lên thì client cũng bịa được, và tin nó nghĩa là ai cũng bỏ qua được giới hạn bằng một
     * header — ở đây hậu quả nặng hơn luồng mã cá nhân, vì mã dòng họ <b>sống suốt hạn</b> chứ
     * không chết sau một lần dùng. Khi hệ thống chạy sau reverse proxy thật, chỗ đúng để dịch
     * header ấy là {@code ForwardedHeaderFilter} / {@code server.forward-headers-strategy}.</p>
     */
    private static String clientIdOf(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }
}

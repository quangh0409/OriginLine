package vn.giapha.membership.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.ClanRegistration;
import vn.giapha.membership.domain.AppUser;

/**
 * Tài khoản sau khi đăng ký bằng mã dòng họ.
 *
 * <p><b>Không có {@code personId}</b>, và {@code status} là {@code PENDING} — đó là điểm phân biệt
 * căn bản với {@code AcceptedInvitationDto}. Người này xem được phả đồ ngay, rồi phải tự nhận mình
 * qua {@code POST /person-claims/...} và chờ Trưởng chi duyệt.</p>
 *
 * <h2>{@code status} nói đúng MỘT câu ở lối không đăng nhập</h2>
 * Ở lối ấy phản hồi này chỉ dựng được từ một tài khoản <b>vừa lập</b>, nên {@code status} luôn là
 * {@code PENDING}. Một định danh đã có chủ không còn đi tới đây nữa: nó bị từ chối bằng
 * {@code IDENTITY_ALREADY_REGISTERED} — xem {@code ClanInviteService#register}. Điều đó là <i>bắt
 * buộc</i>, không phải tình cờ: nếu một tài khoản đã gắn nhân khẩu lọt tới đây thì nó trả
 * {@code ACTIVE}, và endpoint công khai này thành máy trả lời câu "địa chỉ thư kia đã là thành
 * viên chưa".
 *
 * <p>Ở lối <b>đã có token</b>, người đọc phản hồi chính là chủ tài khoản, nên {@code status} phản
 * ánh trạng thái thật của họ ({@code ACTIVE} nếu họ đã được ghép nhân khẩu từ trước) — không lộ gì
 * cho ai khác.</p>
 */
@Schema(description = "Tài khoản sau khi đăng ký bằng mã dòng họ — CHƯA gắn nhân khẩu")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClanRegistrationDto(

        @Schema(description = "Tài khoản vừa lập")
        UUID appUserId,

        @Schema(description = "PENDING với mọi lượt đăng ký không đăng nhập (tài khoản vừa lập,"
                + " chưa gắn nhân khẩu). Người đã có token thì thấy trạng thái thật của chính"
                + " mình.", example = "PENDING")
        String status,

        @Schema(description = "Mã dòng họ đã dùng")
        UUID clanInviteId,

        @Schema(description = "Liên kết một lần để tự đặt mật khẩu. VẮNG khi tài khoản đã có mật"
                + " khẩu — lúc ấy đưa người dùng tới màn đăng nhập.")
        String setPasswordUrl,

        @Schema(description = "Hạn của setPasswordUrl; vắng cùng lúc với nó")
        Instant setPasswordExpiresAt) {

    public static ClanRegistrationDto from(ClanRegistration registration) {
        AppUser user = registration.user();
        return new ClanRegistrationDto(user.id(), user.status().name(),
                registration.clanInviteId(),
                registration.setPasswordLink().map(link -> link.url()).orElse(null),
                registration.setPasswordLink().map(link -> link.expiresAt()).orElse(null));
    }
}

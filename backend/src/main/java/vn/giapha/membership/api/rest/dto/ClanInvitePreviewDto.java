package vn.giapha.membership.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.giapha.membership.application.ClanInvitePreview;

/**
 * Nội dung màn "mã này của dòng họ nào"; không cần token.
 *
 * <p>Hai trường, và đó là toàn bộ. Khác hẳn {@code InvitationPreviewDto}, vốn trả về tên một người
 * đang sống: mã dòng họ không trỏ vào ai nên không có gì để trả. Bộ đếm và nhãn mã <b>cố ý vắng
 * mặt</b> — chúng là công cụ giám sát của Hội đồng, không phải thông tin cho người cầm mã, và nói
 * "mã này còn 3 lượt" cho một người chưa đăng nhập là nói cho kẻ dò biết mình đang ở đâu.</p>
 */
@Schema(description = "Mã này của dòng họ nào; không cần token")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClanInvitePreviewDto(

        @Schema(description = "Tên cả dòng họ, lấy từ gốc cây chi",
                example = "Dòng họ Nguyễn — Đại Lan")
        String clanName,

        @Schema(description = "Thời điểm mã hết hạn (GMT+7)")
        Instant expiresAt) {

    public static ClanInvitePreviewDto from(ClanInvitePreview preview) {
        return new ClanInvitePreviewDto(preview.clanName(), preview.expiresAt());
    }
}

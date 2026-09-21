package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.domain.ClanInviteRedemption;

/**
 * Một lượt dùng mã: ai, lúc nào.
 *
 * <p>Cố ý <b>không</b> mang email hay tên: {@code appUserId} trỏ tới đủ mọi thứ ấy và bản thân nó
 * đi qua bộ lọc riêng tư khi hiển thị. Chép lại ở đây là tạo một bản sao dữ liệu cá nhân nằm ngoài
 * mọi bộ lọc — và một bản sao thì không ai nhớ mà xoá khi có yêu cầu xoá dữ liệu theo Nghị định
 * 13/2023.</p>
 */
@Schema(description = "Một lượt dùng mã mời dòng họ — ai, lúc nào")
public record ClanInviteRedemptionDto(UUID appUserId, Instant at) {

    public static ClanInviteRedemptionDto from(ClanInviteRedemption redemption) {
        return new ClanInviteRedemptionDto(redemption.appUserId(), redemption.at());
    }
}

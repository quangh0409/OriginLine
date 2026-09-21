package vn.giapha.membership.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import vn.giapha.membership.application.ClanInviteList;

/**
 * Màn phát mã của Hội đồng: danh sách mã <b>cộng mẫu số của bộ đếm</b>.
 *
 * <p>Bọc thay vì trả mảng phẳng vì {@code clanLivingPersonCount} không thuộc về bất kỳ mã nào —
 * cùng lập luận với {@link MyPersonClaimsDto}.</p>
 */
@Schema(description = "Danh sách mã mời dòng họ, kèm mẫu số để đọc bộ đếm")
public record ClanInviteListDto(

        List<ClanInviteDto> invites,

        @Schema(description = "Số người **đang sống** trong cả dòng họ. Lập luận của quyết định"
                + " \"cấp mã cho cả họ\" là *\"mã đã dùng 400 lần trong khi dòng họ có 600"
                + " người\"* — vế thứ hai mới làm vế thứ nhất có nghĩa. **Một bộ đếm không có mẫu"
                + " số thì không ai phán xét được**, và chốt được gọi là quan trọng nhất trở thành"
                + " một con số trang trí.",
                example = "612")
        int clanLivingPersonCount) {

    public static ClanInviteListDto from(ClanInviteList list) {
        return new ClanInviteListDto(
                list.invites().stream().map(ClanInviteDto::from).toList(),
                list.clanLivingPersonCount());
    }
}

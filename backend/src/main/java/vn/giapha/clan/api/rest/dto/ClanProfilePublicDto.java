package vn.giapha.clan.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.giapha.clan.application.ClanProfileView;

/**
 * {@code GET /api/v1/public/clan-profile} — hồ sơ dòng họ cho <b>khách</b>.
 *
 * <p>{@code NON_NULL}: chưa khởi tạo thì chỉ có {@code initialized: false}, các trường khác
 * <b>vắng</b> (không phải {@code null}) — đúng như hợp đồng hứa, và giao diện kiểm một cờ thay vì
 * đoán từ một loạt giá trị rỗng.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClanProfilePublicDto(boolean initialized, String name, String shortName,
                                   String rootBranchPath, String region,
                                   AncestralHallDto ancestralHall, String ancestorName,
                                   String foundedNote, GioToDto gioTo) {

    public static ClanProfilePublicDto notInitialized() {
        return new ClanProfilePublicDto(false, null, null, null, null, null, null, null, null);
    }

    public static ClanProfilePublicDto from(ClanProfileView v) {
        return new ClanProfilePublicDto(true, v.name(), v.shortName(), v.rootBranchPath(),
                v.region(), AncestralHallDto.of(v), v.ancestorName(), v.foundedNote(),
                GioToDto.of(v));
    }
}

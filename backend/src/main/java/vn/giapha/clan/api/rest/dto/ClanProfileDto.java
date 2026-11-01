package vn.giapha.clan.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import vn.giapha.clan.application.ClanProfileView;

/**
 * {@code GET/PUT /api/v1/admin/clan-profile} — hồ sơ công khai cộng ba trường quản trị.
 *
 * <p>{@code version} cũng đi ở header {@code ETag}; có ở thân để giao diện giữ được nó trong trạng
 * thái biểu mẫu mà không phải đọc header. Hai chỗ luôn cùng một giá trị.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClanProfileDto(boolean initialized, String name, String shortName,
                             String rootBranchPath, String region,
                             AncestralHallDto ancestralHall, String ancestorName,
                             String foundedNote, GioToDto gioTo, Instant createdAt,
                             Instant updatedAt, long version) {

    public static ClanProfileDto from(ClanProfileView v) {
        return new ClanProfileDto(true, v.name(), v.shortName(), v.rootBranchPath(), v.region(),
                AncestralHallDto.of(v), v.ancestorName(), v.foundedNote(), GioToDto.of(v),
                v.createdAt(), v.updatedAt(), v.version());
    }
}

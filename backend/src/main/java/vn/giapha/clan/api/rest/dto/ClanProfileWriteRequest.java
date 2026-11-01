package vn.giapha.clan.api.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.giapha.clan.application.WriteClanProfileCommand;

/**
 * Thân {@code PUT /api/v1/admin/clan-profile} ({@code ClanProfileWrite} trong hợp đồng).
 *
 * <p>Ràng buộc ở đây chỉ là lớp chặn sớm cho lỗi hình dạng ({@code 400}); luật thật — một nhãn
 * {@code ltree}, vùng hợp lệ, từ đường có tên — nằm ở value object của domain và ra
 * {@code 422 VALIDATION_FAILED}. Hai lớp cùng một giới hạn độ dài để không bao giờ có chuyện lớp
 * này cho qua còn domain từ chối vì một con số khác.</p>
 *
 * <p>Ngữ nghĩa <b>PUT</b>: trường tuỳ chọn vắng mặt là <b>xoá</b> giá trị đang có.</p>
 */
public record ClanProfileWriteRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 60) String shortName,
        @NotBlank String region,
        @NotBlank @Size(max = 64) String rootBranchPath,
        @Valid AncestralHallDto ancestralHall,
        @Size(max = 200) String ancestorName,
        @Size(max = 2000) String foundedNote,
        @Valid GioToDto gioTo) {

    public WriteClanProfileCommand toCommand(Long expectedVersion) {
        return new WriteClanProfileCommand(name, shortName, rootBranchPath, region,
                ancestralHall == null ? null
                        : new WriteClanProfileCommand.Hall(ancestralHall.name(),
                                ancestralHall.address()),
                ancestorName, foundedNote,
                gioTo == null ? null
                        : new WriteClanProfileCommand.LunarDayMonth(gioTo.lunarDay(),
                                gioTo.lunarMonth()),
                expectedVersion);
    }
}

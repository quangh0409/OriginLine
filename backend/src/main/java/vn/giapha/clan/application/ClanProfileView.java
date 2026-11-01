package vn.giapha.clan.application;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.clan.domain.ClanProfile;
import vn.giapha.clan.domain.ClanProfileContent;

/**
 * Hồ sơ dòng họ ở dạng đọc được — nguồn của cả DTO công khai lẫn DTO quản trị.
 *
 * <p>Một view, hai DTO: DTO công khai <b>bỏ</b> {@code createdAt/updatedAt/version} ở tầng api.
 * Không có trường nào ở đây là dữ liệu riêng tư — hồ sơ dòng họ là thông tin chung của cả họ, và
 * khách xem được qua {@code /public/clan-profile}. Thêm một trường riêng tư vào đây thì phải tách
 * view, không phải thêm một câu {@code if} ở DTO công khai.</p>
 */
public record ClanProfileView(UUID id, String name, String shortName, String rootBranchPath,
                              String region, String ancestralHallName,
                              String ancestralHallAddress, String ancestorName, String foundedNote,
                              Integer gioToLunarDay, Integer gioToLunarMonth, Instant createdAt,
                              Instant updatedAt, long version) {

    static ClanProfileView of(ClanProfile profile) {
        ClanProfileContent c = profile.content();
        return new ClanProfileView(profile.id(), c.name(), c.shortName(), c.root().value(),
                c.region().name(),
                c.hall() == null ? null : c.hall().name(),
                c.hall() == null ? null : c.hall().address(),
                c.ancestorName(), c.foundedNote(),
                c.gioTo() == null ? null : c.gioTo().lunarDay(),
                c.gioTo() == null ? null : c.gioTo().lunarMonth(),
                profile.createdAt(), profile.updatedAt(), profile.version());
    }
}

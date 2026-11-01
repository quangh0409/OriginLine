package vn.giapha.clan.application;

import java.util.List;
import vn.giapha.clan.domain.ClanReadiness;

/**
 * Bảng "dòng họ còn thiếu bước nào" — nguồn của {@code ClanReadinessDto}.
 *
 * @param persons       số nhân khẩu chưa xoá mềm
 * @param branches      số chi/ngành dưới gốc, không tính chính chi gốc
 * @param activeInvites số mã mời dòng họ còn dùng được
 */
public record ClanReadinessView(ProfilePart profile, RootPart rootBranch, RuleSetPart kinshipRuleSet,
                                CouncilPart council, long persons, long branches,
                                int activeInvites, List<ClanReadiness.Step> steps) {

    public record ProfilePart(boolean done, String name) {
    }

    public record RootPart(boolean done, String path) {
    }

    /** @param region vùng mà bộ tầng {@code CLAN} đang gắn; {@code customized} = Hội đồng đã khai luật riêng */
    public record RuleSetPart(boolean done, String region, boolean customized) {
    }

    public record CouncilPart(boolean done, int count) {
    }
}

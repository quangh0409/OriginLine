package vn.giapha.clan.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import vn.giapha.clan.application.ClanReadinessView;

/**
 * {@code GET /api/v1/admin/clan-profile/readiness} — "dòng họ còn thiếu bước nào".
 *
 * <p>{@code steps[].key} ở dạng chữ thường ({@code profile|council|import|invite|done}), và
 * {@code steps[].hint} là câu tiếng Việt có dấu nói việc cần làm tiếp — giao diện hiện nguyên văn.</p>
 */
public record ClanReadinessDto(ProfilePart profile, RootBranchPart rootBranch,
                               KinshipRuleSetPart kinshipRuleSet, CouncilPart council,
                               CountPart persons, CountPart branches, InviteCodesPart inviteCodes,
                               List<Step> steps) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfilePart(boolean done, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RootBranchPart(boolean done, String path) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KinshipRuleSetPart(boolean done, String region, boolean customized) {
    }

    public record CouncilPart(boolean done, int count) {
    }

    public record CountPart(long count) {
    }

    public record InviteCodesPart(int active) {
    }

    public record Step(String key, boolean done, String hint) {
    }

    public static ClanReadinessDto from(ClanReadinessView v) {
        return new ClanReadinessDto(
                new ProfilePart(v.profile().done(), v.profile().name()),
                new RootBranchPart(v.rootBranch().done(), v.rootBranch().path()),
                new KinshipRuleSetPart(v.kinshipRuleSet().done(), v.kinshipRuleSet().region(),
                        v.kinshipRuleSet().customized()),
                new CouncilPart(v.council().done(), v.council().count()),
                new CountPart(v.persons()),
                new CountPart(v.branches()),
                new InviteCodesPart(v.activeInvites()),
                v.steps().stream()
                        .map(s -> new Step(s.key().wire(), s.done(), s.hint()))
                        .toList());
    }
}

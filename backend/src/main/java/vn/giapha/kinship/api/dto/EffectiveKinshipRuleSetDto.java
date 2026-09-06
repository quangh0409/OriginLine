package vn.giapha.kinship.api.dto;

import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.domain.RuleScope;

/**
 * Bộ luật đã hợp nhất {@code DEFAULT -> REGION -> CLAN -> BRANCH} cho một chi — hợp đồng
 * {@code EffectiveKinshipRuleSet}.
 *
 * <p>Đây chính là bộ mà {@code GET /api/v1/kinship} thực sự dùng. Mỗi luật kèm
 * {@code inheritedFromScope} và cờ {@code overridden} để giao diện tô màu "chi này đã sửa gì so với
 * mặc định" — thứ duy nhất giúp Hội đồng tranh luận về danh xưng bằng dữ liệu thay vì bằng trí
 * nhớ.</p>
 *
 * @param chain các bộ luật trong chuỗi kế thừa, theo thứ tự áp dụng (gốc {@code DEFAULT} trước)
 */
public record EffectiveKinshipRuleSetDto(
        UUID branchId,
        UUID leafRuleSetId,
        List<ChainEntry> chain,
        List<KinshipRuleDto> rules) {

    /** Một mắt xích của chuỗi kế thừa. */
    public record ChainEntry(UUID ruleSetId, RuleScope scope, String code, String name, long version) {
    }
}

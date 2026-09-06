package vn.giapha.kinship.application;

import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.domain.KinshipRule;
import vn.giapha.kinship.domain.RuleScope;

/**
 * Bộ luật <b>đã hợp nhất</b> {@code DEFAULT -> REGION -> CLAN -> BRANCH} cho một chi — đúng bộ mà
 * {@code /api/v1/kinship} thực sự dùng.
 *
 * <p>Trả kèm cả các luật <b>đã bị ghi đè</b> ({@code overridden = true}) là có chủ ý: màn hình thử
 * luật của Hội đồng cần tô màu "chi này đã sửa những gì so với mặc định". Không có phần đó thì
 * người dùng không phân biệt được "chi cố tình đổi" với "hệ thống vốn dĩ như vậy", và mọi tranh
 * luận về danh xưng sẽ quay về cãi nhau bằng trí nhớ.</p>
 *
 * @param chain các bộ luật trong chuỗi kế thừa, theo <b>thứ tự áp dụng</b> (gốc DEFAULT trước)
 * @param rules luật đang hiệu lực trước, luật đã bị ghi đè sau
 */
public record EffectiveRuleSetView(
        UUID branchId,
        UUID leafRuleSetId,
        List<RuleSetRef> chain,
        List<EffectiveRule> rules) {

    public EffectiveRuleSetView {
        chain = chain == null ? List.of() : List.copyOf(chain);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /** Một mắt xích của chuỗi kế thừa. */
    public record RuleSetRef(UUID ruleSetId, RuleScope scope, String code, String name, long version) {
    }

    /**
     * Một luật sau khi hợp nhất, kèm nguồn gốc.
     *
     * @param overridden {@code true} nghĩa là luật này đã bị một cấp thấp hơn ghi đè và
     *                   <b>không</b> còn hiệu lực; trả về chỉ để giao diện hiện lịch sử ghi đè
     */
    public record EffectiveRule(
            KinshipRule rule,
            RuleScope inheritedFromScope,
            UUID inheritedFromRuleSetId,
            boolean overridden) {
    }
}

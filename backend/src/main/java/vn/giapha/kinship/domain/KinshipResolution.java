package vn.giapha.kinship.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Kết quả một lần suy luận danh xưng, kèm <b>bằng chứng</b> để người dùng và Hội đồng Tộc biểu tự
 * kiểm chứng.
 *
 * <p>{@code ruleId} + {@code ruleSetScope} là thứ đáng giá nhất khi dòng họ báo "gọi sai": biết
 * ngay phải sửa luật nào, ở bộ luật cấp nào.</p>
 *
 * @param title            danh xưng — ego gọi alter bằng gì
 * @param egoSelfTerm      ego tự xưng là gì khi nói với alter (gọi "bác" thì xưng "cháu")
 * @param reciprocalTitle  chiều ngược lại — alter gọi ego bằng gì
 * @param relationCode     mã luật đã khớp
 * @param facts            dữ kiện đã dùng để so khớp; luôn trả kể cả khi không khớp luật nào, để
 *                         Hội đồng biết cần viết luật cho tổ hợp nào
 */
public record KinshipResolution(
        KinshipStatus status,
        String title,
        String titleShort,
        String egoSelfTerm,
        String reciprocalTitle,
        RelationCode relationCode,
        UUID ruleId,
        UUID ruleSetId,
        RuleScope ruleSetScope,
        RelationFacts facts,
        LcaResult lca) {

    public static KinshipResolution self(RelationFacts facts) {
        return new KinshipResolution(KinshipStatus.SELF, null, null, null, null, null, null, null,
                null, facts, null);
    }

    public static KinshipResolution noRule(KinshipStatus status, RelationFacts facts, LcaResult lca) {
        return new KinshipResolution(status, null, null, null, null, null, null, null, null, facts, lca);
    }

    public static KinshipResolution matched(KinshipStatus status, KinshipRule rule,
            String reciprocalTitle, RelationFacts facts, LcaResult lca) {
        return new KinshipResolution(status, rule.title(), rule.titleShort(), rule.egoSelfTerm(),
                reciprocalTitle, rule.relationCode(), rule.id(),
                rule.originRuleSetId() != null ? rule.originRuleSetId() : rule.ruleSetId(),
                rule.originScope(), facts, lca);
    }

    public Optional<String> titleOpt() {
        return Optional.ofNullable(title);
    }

    public boolean isResolved() {
        return status == KinshipStatus.RESOLVED;
    }
}

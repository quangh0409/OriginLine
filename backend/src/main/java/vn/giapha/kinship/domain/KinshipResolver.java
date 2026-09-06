package vn.giapha.kinship.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vn.giapha.shared.vo.PersonId;

/**
 * Engine suy luận danh xưng — <b>POJO thuần</b>: không Spring, không CSDL, không I/O.
 *
 * <p>Nhờ vậy hàng trăm ca danh xưng chạy được trong một bộ test không cần Spring context, dưới một
 * giây. Mọi thứ chạm hạ tầng đều nằm sau {@link LcaPort} / {@link PersonLookupPort} /
 * {@link RuleSetRepository} và được ghép ở tầng application.</p>
 *
 * <p><b>Lớp này không chứa một danh xưng nào.</b> Nó chỉ so khớp {@link RelationFacts} với các dòng
 * của bảng {@code kinship_rule} theo {@code priority} tăng dần (FR-1.3a). Muốn "chú" thành "dượng"
 * ở miền Trung thì sửa dữ liệu, không sửa lớp này.</p>
 */
public final class KinshipResolver {

    private final RelationFactsFactory factsFactory;

    public KinshipResolver() {
        this(new RelationFactsFactory());
    }

    public KinshipResolver(RelationFactsFactory factsFactory) {
        this.factsFactory = Objects.requireNonNull(factsFactory);
    }

    /**
     * Suy danh xưng ego gọi alter, kèm danh xưng chiều ngược lại.
     *
     * @param context bằng chứng đồ thị đã gom sẵn cho cặp người này
     * @param rules   bộ luật đã hợp nhất DEFAULT → REGION → CLAN → BRANCH
     */
    public KinshipResolution resolve(RelationContext context, KinshipRuleSet rules) {
        Objects.requireNonNull(context, "context khong duoc null");
        Objects.requireNonNull(rules, "rules khong duoc null");

        RelationFacts facts = factsFactory.from(context);
        if (context.isSelf()) {
            return KinshipResolution.self(facts);
        }

        Optional<KinshipRule> matched = rules.match(facts);
        String reciprocal = reciprocalTitle(context, rules);

        // Không có tổ chung và cũng không có cạnh nối nào: vẫn trả danh xưng của luật vét nếu bộ
        // luật có khai (bộ mặc định có KHONG_XAC_DINH), nhưng status phải nói thật cho giao diện.
        KinshipStatus status = context.hasNoConnection()
                ? KinshipStatus.NO_COMMON_ANCESTOR
                : KinshipStatus.RESOLVED;

        return matched
                .map(rule -> KinshipResolution.matched(status, rule, reciprocal, facts, context.lca()))
                .orElseGet(() -> KinshipResolution.noRule(
                        context.hasNoConnection() ? KinshipStatus.NO_COMMON_ANCESTOR
                                : KinshipStatus.NO_MATCHING_RULE,
                        facts, context.lca()));
    }

    /**
     * Chỉ so khớp — dùng khi dữ kiện đã có sẵn (test bảng ca biên, màn hình thử luật của Hội đồng).
     */
    public Optional<KinshipRule> match(RelationFacts facts, KinshipRuleSet rules) {
        return rules.match(facts);
    }

    /**
     * Dạng gọn theo TDD §7: chỉ có kết quả LCA và hai bản chiếu người, thêm bản đồ tra cứu các
     * nhân khẩu trung gian trên đường đi (cần cho {@code side} và {@code is_elder}).
     */
    public KinshipResolution resolve(LcaResult lca, PersonView ego, PersonView alter,
            Map<PersonId, PersonView> pathPeople, KinshipRuleSet rules) {
        return resolve(RelationContext.bloodOnly(ego, alter, lca, pathPeople), rules);
    }

    /**
     * Danh xưng chiều ngược lại. Tính bằng cách đảo vai trên cùng bộ bằng chứng — <b>không</b> đệ
     * quy và <b>không</b> hỏi lại đồ thị. Không khớp luật nào thì lùi về {@code ego_self_term} của
     * luật thuận (ví dụ luật "bác" khai sẵn xưng "cháu").
     */
    private String reciprocalTitle(RelationContext context, KinshipRuleSet rules) {
        if (context.isSelf()) {
            return null;
        }
        RelationFacts reverseFacts = factsFactory.from(context.reversed());
        Optional<KinshipRule> reverse = rules.match(reverseFacts);

        // Khớp thật ở chiều ngược thì dùng luôn — nó cụ thể hơn ego_self_term (luật CHA khai
        // xưng "con", nhưng khớp ngược ra được "con trai"/"con gái").
        if (reverse.isPresent() && !reverse.get().isCatchAll()) {
            return reverse.get().title();
        }

        // Rơi vào luật vét nghĩa là chiều ngược KHÔNG suy ra được gì. Khi đó ego_self_term của
        // luật thuận là câu trả lời tốt hơn hẳn: nó do Hội đồng khai tay trên chính luật này
        // (luật "thím" khai sẵn xưng "cháu"), không phải suy đoán.
        //
        // Không kiểm tra isCatchAll() ở đây thì nhánh dưới KHÔNG BAO GIO chay: bộ mặc định có luật
        // vét khớp mọi dữ kiện, nên rules.match(reverseFacts) luôn trả về một luật và mọi cặp
        // dâu/rể đều nhận "chưa xác định quan hệ" thay vì "cháu".
        String egoSelfTerm = rules.match(factsFactory.from(context))
                .map(KinshipRule::egoSelfTerm)
                .filter(term -> term != null && !term.isBlank())
                .orElse(null);
        if (egoSelfTerm != null) {
            return egoSelfTerm;
        }
        return reverse.map(KinshipRule::title).orElse(null);
    }
}

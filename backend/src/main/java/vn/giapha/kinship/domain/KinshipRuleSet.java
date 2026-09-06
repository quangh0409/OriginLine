package vn.giapha.kinship.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bộ quy tắc danh xưng và chuỗi kế thừa của nó.
 *
 * <p><b>Kế thừa &amp; ghi đè: DEFAULT → REGION → CLAN → BRANCH.</b> Bộ con giữ một tham chiếu tới
 * bộ cha; khi hợp nhất, luật của bộ con <b>thay thế</b> luật của bộ cha có cùng
 * {@link RelationCode} (CSDL đã bảo đảm mỗi mã chỉ xuất hiện một lần trong một bộ bằng unique
 * index). Luật mang mã mới thì được cộng thêm.</p>
 *
 * <p>Nhờ vậy một chi chỉ cần khai đúng những luật họ muốn khác đi — ví dụ chi dùng "thầy" thay cho
 * "bố" chỉ cần một dòng {@code CHA}, phần còn lại vẫn thừa hưởng bộ mặc định miền Bắc.</p>
 *
 * <p>Đây là POJO thuần: không Spring, không JPA. Toàn bộ việc đọc CSDL nằm sau
 * {@link RuleSetRepository}.</p>
 */
public final class KinshipRuleSet {

    /** Chặn chuỗi kế thừa hỏng (CSDL cũng có trigger chống chu trình). */
    private static final int MAX_INHERITANCE_DEPTH = 32;

    private static final Comparator<KinshipRule> BY_PRIORITY =
            Comparator.comparingInt(KinshipRule::priority)
                    .thenComparing(rule -> rule.relationCode().value());

    private final UUID id;
    private final String code;
    private final String name;
    private final RuleScope scope;
    private final Region region;
    private final UUID branchId;
    private final KinshipRuleSet parent;
    private final List<KinshipRule> ownRules;
    private final boolean active;
    private final long version;

    private List<KinshipRule> effectiveCache;

    public KinshipRuleSet(UUID id, String code, String name, RuleScope scope, Region region,
            UUID branchId, KinshipRuleSet parent, List<KinshipRule> ownRules, boolean active,
            long version) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.scope = Objects.requireNonNull(scope, "scope khong duoc null");
        this.region = region;
        this.branchId = branchId;
        this.parent = parent;
        this.ownRules = ownRules == null ? List.of() : List.copyOf(ownRules);
        this.active = active;
        this.version = version;
    }

    /** Bộ luật đơn lẻ, không kế thừa — tiện cho unit test. */
    public static KinshipRuleSet of(RuleScope scope, List<KinshipRule> rules) {
        return new KinshipRuleSet(UUID.randomUUID(), scope.name(), scope.name(), scope, null, null,
                null, rules, true, 0L);
    }

    /** Bộ luật con kế thừa từ {@code parent}. */
    public KinshipRuleSet child(RuleScope childScope, String childCode, List<KinshipRule> rules) {
        return new KinshipRuleSet(UUID.randomUUID(), childCode, childCode, childScope, null, null,
                this, rules, true, 0L);
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public RuleScope scope() {
        return scope;
    }

    public Region region() {
        return region;
    }

    public UUID branchId() {
        return branchId;
    }

    public KinshipRuleSet parent() {
        return parent;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }

    /** Luật khai báo trực tiếp trong bộ này, chưa hợp nhất với bộ cha. */
    public List<KinshipRule> ownRules() {
        return ownRules;
    }

    /** Chuỗi kế thừa theo <b>thứ tự áp dụng</b>: gốc (DEFAULT) trước, bộ này sau cùng. */
    public List<KinshipRuleSet> chain() {
        Deque<KinshipRuleSet> stack = new ArrayDeque<>();
        KinshipRuleSet current = this;
        int hops = 0;
        while (current != null) {
            if (++hops > MAX_INHERITANCE_DEPTH) {
                throw new IllegalStateException(
                        "Chuoi ke thua kinship_rule_set qua sau hoac co chu trinh tai " + current.code);
            }
            stack.push(current);
            current = current.parent;
        }
        return new ArrayList<>(stack);
    }

    /**
     * Toàn bộ luật đang hiệu lực sau khi hợp nhất kế thừa, sắp theo {@code priority} tăng dần
     * (số nhỏ được xét trước), phá hoà bằng {@code relation_code} để kết quả luôn tất định.
     */
    public List<KinshipRule> effectiveRules() {
        if (effectiveCache == null) {
            Map<RelationCode, KinshipRule> merged = new LinkedHashMap<>();
            for (KinshipRuleSet set : chain()) {
                if (!set.active) {
                    continue;
                }
                for (KinshipRule rule : set.ownRules) {
                    // put() ghi đè luật cùng mã của bộ cha — đây chính là cơ chế override.
                    merged.put(rule.relationCode(), rule.withOrigin(set.scope, set.id));
                }
            }
            List<KinshipRule> result = new ArrayList<>(merged.values());
            result.sort(BY_PRIORITY);
            effectiveCache = List.copyOf(result);
        }
        return effectiveCache;
    }

    /**
     * Các luật của bộ cha đã <b>bị ghi đè</b> và không còn hiệu lực. Giao diện quản trị dùng để tô
     * màu "chi này đã sửa luật nào" ({@code EffectiveKinshipRuleSet.overridden} trong contract).
     */
    public List<KinshipRule> overriddenRules() {
        Map<RelationCode, KinshipRule> seen = new LinkedHashMap<>();
        List<KinshipRule> shadowed = new ArrayList<>();
        for (KinshipRuleSet set : chain()) {
            if (!set.active) {
                continue;
            }
            for (KinshipRule rule : set.ownRules) {
                KinshipRule previous = seen.put(rule.relationCode(), rule.withOrigin(set.scope, set.id));
                if (previous != null) {
                    shadowed.add(previous);
                }
            }
        }
        return List.copyOf(shadowed);
    }

    /**
     * Luật đầu tiên khớp dữ kiện, theo {@code priority} tăng dần.
     *
     * <p>Không dùng {@code stream().filter().findFirst()} trên danh sách đã sắp là cố ý: thứ tự đã
     * được bảo đảm ở {@link #effectiveRules()}, và vòng lặp thường giữ chi phí thấp cho hàng trăm
     * lần gọi trong một request dựng cây.</p>
     */
    public Optional<KinshipRule> match(RelationFacts facts) {
        for (KinshipRule rule : effectiveRules()) {
            if (rule.matches(facts)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /** Mọi luật khớp dữ kiện, theo thứ tự ưu tiên — dùng cho màn hình gỡ rối của Hội đồng. */
    public List<KinshipRule> matchAll(RelationFacts facts) {
        return effectiveRules().stream().filter(rule -> rule.matches(facts)).toList();
    }

    @Override
    public String toString() {
        return "KinshipRuleSet[" + scope + " " + code + ", " + ownRules.size() + " luat rieng]";
    }
}

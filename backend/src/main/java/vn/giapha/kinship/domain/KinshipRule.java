package vn.giapha.kinship.domain;

import java.util.Objects;
import java.util.UUID;
import vn.giapha.shared.vo.Gender;

/**
 * Một luật danh xưng — đúng một dòng của bảng {@code kinship_rule}.
 *
 * <p><b>Danh xưng là dữ liệu, không phải code (FR-1.3a).</b> Lớp này không chứa một chữ "bác",
 * "chú", "thím" nào; nó chỉ biết cách so khớp {@link RelationFacts} với các chiều đã lưu và trả về
 * {@code title} lấy từ CSDL.</p>
 *
 * <p>Quy tắc so khớp — mọi chiều {@code null} nghĩa là "không xét":</p>
 * <ul>
 *   <li>{@code genDelta}/{@code collateralDegree}: khớp giá trị chính xác và/hoặc khoảng min–max.
 *       Dữ kiện thiếu (null) thì luật có ràng buộc chiều đó KHÔNG khớp.</li>
 *   <li>{@code side}: {@code BLOOD} phủ cả PATERNAL lẫn MATERNAL; {@code IN_LAW} khớp khi quan hệ
 *       đi qua hôn nhân.</li>
 *   <li>{@code isElder}: luật ghi TRUE/FALSE mà dữ kiện chưa rõ vai thì không khớp — nhờ vậy các
 *       luật "bác/chú" ({@code is_elder IS NULL}, priority lớn hơn) mới đỡ được ca thiếu
 *       {@code birth_order}.</li>
 *   <li>{@code directLink}: luật có ràng buộc cạnh thì bắt buộc phải có cạnh đó còn hiệu lực.
 *       Luật không ràng buộc cạnh vẫn khớp bình thường dù có cạnh — {@code priority} phân định
 *       (ví dụ BO_NUOI priority 8 thắng CHA priority 10).</li>
 * </ul>
 *
 * @param originScope     cấp của bộ luật đã cấp ra luật này sau khi hợp nhất kế thừa
 * @param originRuleSetId id bộ luật gốc — để truy vết "phải sửa luật nào" khi dòng họ báo sai
 */
public record KinshipRule(
        UUID id,
        UUID ruleSetId,
        RelationCode relationCode,
        Integer genDelta,
        Integer genDeltaMin,
        Integer genDeltaMax,
        Integer collateralDegree,
        Integer collateralDegreeMin,
        Integer collateralDegreeMax,
        RelationSide side,
        Gender gender,
        Boolean isElder,
        RelationSide linkSide,
        Gender linkGender,
        InLawDirection inLawDirection,
        DirectLinkType directLink,
        String directLinkSubtype,
        boolean directLinkReversed,
        String title,
        String titleShort,
        String egoSelfTerm,
        String description,
        int priority,
        boolean active,
        RuleScope originScope,
        UUID originRuleSetId) {

    public KinshipRule {
        Objects.requireNonNull(relationCode, "relationCode khong duoc null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title khong duoc rong (luat " + relationCode + ")");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority phai >= 0 (luat " + relationCode + ")");
        }
    }

    /** {@code true} nếu luật này phủ được dữ kiện quan hệ. */
    /**
     * Luật <b>vét</b>: không ràng buộc một chiều so khớp nào nên khớp với mọi dữ kiện.
     *
     * <p>Bộ mặc định có một luật như vậy ({@code KHONG_XAC_DINH}, priority 999) để giao diện luôn
     * có chữ để hiển thị thay vì ô trống. Tác dụng phụ là mọi phép "thử khớp lại" đều thành công,
     * kể cả khi thực chất không suy ra được gì — {@link KinshipResolver} cần biết điều đó để phân
     * biệt "khớp thật" với "rơi vào luật vét".</p>
     *
     * <p>Nhận diện theo <b>cấu trúc</b> chứ không theo {@code relationCode}: mã luật là dữ liệu do
     * dòng họ đặt, hard-code nó vào đây là vi phạm FR-1.3a. Một chi tự khai luật vét riêng với mã
     * khác vẫn được nhận ra đúng.</p>
     */
    public boolean isCatchAll() {
        return genDelta == null && genDeltaMin == null && genDeltaMax == null
                && collateralDegree == null && collateralDegreeMin == null && collateralDegreeMax == null
                && side == null && gender == null && isElder == null
                && linkSide == null && linkGender == null && inLawDirection == null
                && directLink == null;
    }

    public boolean matches(RelationFacts facts) {
        if (!active || facts == null) {
            return false;
        }
        if (directLink != null && !facts.hasDirectLink(directLink, directLinkSubtype, directLinkReversed)) {
            return false;
        }
        if (!matchesNumber(genDelta, genDeltaMin, genDeltaMax, facts.genDelta())) {
            return false;
        }
        if (!matchesNumber(collateralDegree, collateralDegreeMin, collateralDegreeMax,
                facts.collateralDegree())) {
            return false;
        }
        if (!matchesSide(facts)) {
            return false;
        }
        if (gender != null && gender != facts.targetGender()) {
            return false;
        }
        if (isElder != null && !isElder.equals(facts.isElder())) {
            return false;
        }
        if (linkSide != null && !linkSide.covers(facts.linkSide())) {
            return false;
        }
        if (linkGender != null && linkGender != facts.linkGender()) {
            return false;
        }
        return inLawDirection == null || inLawDirection == facts.inLawDirection();
    }

    private boolean matchesSide(RelationFacts facts) {
        if (side == null) {
            return true;
        }
        if (side == RelationSide.IN_LAW) {
            // Hai anh em họ lấy nhau (chuyện có thật trong gia phả cũ) vừa có bên huyết thống vừa
            // có cạnh hôn nhân; luật vợ/chồng vẫn phải khớp nên xét thêm cờ throughMarriage.
            return facts.side() == RelationSide.IN_LAW || facts.throughMarriage();
        }
        return side.covers(facts.side());
    }

    private static boolean matchesNumber(Integer exact, Integer min, Integer max, Integer actual) {
        if (exact == null && min == null && max == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (exact != null && !exact.equals(actual)) {
            return false;
        }
        if (min != null && actual < min) {
            return false;
        }
        return max == null || actual <= max;
    }

    /** Bản sao gắn nguồn gốc sau khi hợp nhất kế thừa DEFAULT -> REGION -> CLAN -> BRANCH. */
    public KinshipRule withOrigin(RuleScope scope, UUID setId) {
        return new KinshipRule(id, ruleSetId, relationCode, genDelta, genDeltaMin, genDeltaMax,
                collateralDegree, collateralDegreeMin, collateralDegreeMax, side, gender, isElder,
                linkSide, linkGender, inLawDirection, directLink, directLinkSubtype,
                directLinkReversed, title, titleShort, egoSelfTerm, description, priority, active,
                scope, setId);
    }

    public static Builder builder(String relationCode, String title) {
        return new Builder(RelationCode.of(relationCode), title);
    }

    /**
     * Builder cho test và cho adapter. Mặc định: priority 100, {@code active = true}, mọi chiều
     * so khớp để trống (nghĩa là "không xét").
     */
    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private UUID ruleSetId;
        private final RelationCode relationCode;
        private Integer genDelta;
        private Integer genDeltaMin;
        private Integer genDeltaMax;
        private Integer collateralDegree;
        private Integer collateralDegreeMin;
        private Integer collateralDegreeMax;
        private RelationSide side;
        private Gender gender;
        private Boolean isElder;
        private RelationSide linkSide;
        private Gender linkGender;
        private InLawDirection inLawDirection;
        private DirectLinkType directLink;
        private String directLinkSubtype;
        private boolean directLinkReversed;
        private final String title;
        private String titleShort;
        private String egoSelfTerm;
        private String description;
        private int priority = 100;
        private boolean active = true;
        private RuleScope originScope;
        private UUID originRuleSetId;

        private Builder(RelationCode relationCode, String title) {
            this.relationCode = relationCode;
            this.title = title;
        }

        public Builder id(UUID value) {
            this.id = value;
            return this;
        }

        public Builder ruleSetId(UUID value) {
            this.ruleSetId = value;
            return this;
        }

        public Builder genDelta(Integer value) {
            this.genDelta = value;
            return this;
        }

        public Builder genDeltaMin(Integer value) {
            this.genDeltaMin = value;
            return this;
        }

        public Builder genDeltaMax(Integer value) {
            this.genDeltaMax = value;
            return this;
        }

        public Builder collateralDegree(Integer value) {
            this.collateralDegree = value;
            return this;
        }

        public Builder collateralDegreeMin(Integer value) {
            this.collateralDegreeMin = value;
            return this;
        }

        public Builder collateralDegreeMax(Integer value) {
            this.collateralDegreeMax = value;
            return this;
        }

        public Builder side(RelationSide value) {
            this.side = value;
            return this;
        }

        public Builder gender(Gender value) {
            this.gender = value;
            return this;
        }

        public Builder isElder(Boolean value) {
            this.isElder = value;
            return this;
        }

        public Builder linkSide(RelationSide value) {
            this.linkSide = value;
            return this;
        }

        public Builder linkGender(Gender value) {
            this.linkGender = value;
            return this;
        }

        public Builder inLawDirection(InLawDirection value) {
            this.inLawDirection = value;
            return this;
        }

        public Builder directLink(DirectLinkType value) {
            this.directLink = value;
            return this;
        }

        public Builder directLinkSubtype(String value) {
            this.directLinkSubtype = value;
            return this;
        }

        public Builder directLinkReversed(boolean value) {
            this.directLinkReversed = value;
            return this;
        }

        public Builder titleShort(String value) {
            this.titleShort = value;
            return this;
        }

        public Builder egoSelfTerm(String value) {
            this.egoSelfTerm = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder priority(int value) {
            this.priority = value;
            return this;
        }

        public Builder active(boolean value) {
            this.active = value;
            return this;
        }

        public Builder origin(RuleScope scope, UUID setId) {
            this.originScope = scope;
            this.originRuleSetId = setId;
            return this;
        }

        public KinshipRule build() {
            return new KinshipRule(id, ruleSetId, relationCode, genDelta, genDeltaMin, genDeltaMax,
                    collateralDegree, collateralDegreeMin, collateralDegreeMax, side, gender,
                    isElder, linkSide, linkGender, inLawDirection, directLink, directLinkSubtype,
                    directLinkReversed, title, titleShort, egoSelfTerm, description, priority,
                    active, originScope, originRuleSetId);
        }
    }
}

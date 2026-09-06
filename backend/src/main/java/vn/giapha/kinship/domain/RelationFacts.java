package vn.giapha.kinship.domain;

import java.util.List;
import java.util.Optional;
import vn.giapha.shared.vo.Gender;

/**
 * Dữ kiện quan hệ đã chuẩn hoá — <b>đầu vào duy nhất</b> của rule engine danh xưng.
 *
 * <p>Mọi chiều ở đây khớp một-một với các cột so khớp của bảng {@code kinship_rule} (migration V3).
 * {@code null} luôn có nghĩa "không xác định được", và một luật đòi hỏi chiều đó sẽ <b>không</b>
 * khớp — đó chính là cơ chế đẩy các ca thiếu dữ liệu về luật "chưa rõ vai".</p>
 *
 * @param genDelta         {@code dist_a - dist_b}. Dương: alter thuộc đời TRÊN ego (bố, bác, ông).
 *                         Bằng 0: cùng đời. Âm: đời DƯỚI (con, cháu, chắt)
 * @param collateralDegree {@code min(dist_a, dist_b)} — bậc bàng hệ. 0 trực hệ · 1 ruột · 2 họ ·
 *                         từ 3 là họ xa. Đây là chiều phân biệt "bác ruột" với "bác họ"
 * @param side             bên nội/ngoại/huyết thống/hôn nhân
 * @param targetGender     giới tính của <b>alter</b> (người được gọi)
 * @param isElder          đường của alter là vai trên đường của ego hay không; {@code null} = chưa rõ
 * @param linkSide         chỉ khi {@code side = IN_LAW}: bên nội/ngoại của NGƯỜI NỐI
 * @param linkGender       chỉ khi {@code side = IN_LAW}: giới tính của NGƯỜI NỐI
 * @param inLawDirection   chỉ khi {@code side = IN_LAW}: ai là người lấy vào họ
 * @param directLinks      các cạnh nối trực tiếp ego–alter
 * @param throughMarriage  quan hệ đi qua hôn nhân
 * @param throughAdoption  đường quan hệ có ít nhất một cạnh nhận nuôi
 */
public record RelationFacts(
        Integer genDelta,
        Integer collateralDegree,
        RelationSide side,
        Gender targetGender,
        Boolean isElder,
        RelationSide linkSide,
        Gender linkGender,
        InLawDirection inLawDirection,
        List<DirectLink> directLinks,
        boolean throughMarriage,
        boolean throughAdoption) {

    public RelationFacts {
        directLinks = directLinks == null ? List.of() : List.copyOf(directLinks);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Dữ kiện rỗng: hai người không có tổ chung, không cạnh nối nào — chỉ luật vét mới khớp. */
    public static RelationFacts unrelated(Gender targetGender) {
        return builder().targetGender(targetGender).build();
    }

    public Optional<Boolean> elder() {
        return Optional.ofNullable(isElder);
    }

    /** {@code true} nếu tồn tại cạnh trực tiếp còn hiệu lực thoả yêu cầu của luật. */
    public boolean hasDirectLink(DirectLinkType type, String subtype, boolean reversed) {
        return directLinks.stream().anyMatch(link -> link.satisfies(type, subtype, reversed));
    }

    public boolean hasAnyDirectLink() {
        return directLinks.stream().anyMatch(DirectLink::active);
    }

    public static final class Builder {
        private Integer genDelta;
        private Integer collateralDegree;
        private RelationSide side;
        private Gender targetGender = Gender.UNKNOWN;
        private Boolean isElder;
        private RelationSide linkSide;
        private Gender linkGender;
        private InLawDirection inLawDirection;
        private List<DirectLink> directLinks = List.of();
        private boolean throughMarriage;
        private boolean throughAdoption;

        public Builder genDelta(Integer value) {
            this.genDelta = value;
            return this;
        }

        public Builder collateralDegree(Integer value) {
            this.collateralDegree = value;
            return this;
        }

        public Builder side(RelationSide value) {
            this.side = value;
            return this;
        }

        public Builder targetGender(Gender value) {
            this.targetGender = value == null ? Gender.UNKNOWN : value;
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

        public Builder directLinks(List<DirectLink> value) {
            this.directLinks = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public Builder throughMarriage(boolean value) {
            this.throughMarriage = value;
            return this;
        }

        public Builder throughAdoption(boolean value) {
            this.throughAdoption = value;
            return this;
        }

        public RelationFacts build() {
            return new RelationFacts(genDelta, collateralDegree, side, targetGender, isElder,
                    linkSide, linkGender, inLawDirection, directLinks, throughMarriage, throughAdoption);
        }
    }
}

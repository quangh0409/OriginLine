package vn.giapha.kinship.application;

import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.domain.InLawDirection;
import vn.giapha.kinship.domain.KinshipStatus;
import vn.giapha.kinship.domain.RelationSide;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.shared.vo.Gender;

/**
 * Kết quả một lần tra danh xưng ở tầng application — <b>bản đã phẳng hoá và tuần tự hoá được</b>.
 *
 * <p>Vì sao không cache thẳng {@code KinshipResolution} của domain: nó ôm cả
 * {@code RelationFacts.directLinks} (kèm {@code PersonId}) và {@code LcaResult} với hai đường đi
 * đầy đủ — nặng, và ràng cache vào hình dạng nội bộ của domain. Bản ghi này chỉ giữ đúng những gì
 * hợp đồng {@code KinshipResult} cần, dùng {@link UUID} trần nên JSON gọn và ổn định qua các lần
 * đổi domain.</p>
 *
 * @param ruleSetChain chuỗi kế thừa đã hợp nhất, theo thứ tự áp dụng (DEFAULT trước)
 * @param cached       {@code true} khi lấy từ Redis; không nằm trong giá trị được cache
 */
public record KinshipQueryResult(
        KinshipStatus status,
        UUID fromPersonId,
        UUID toPersonId,
        String title,
        String titleShort,
        String egoSelfTerm,
        String reciprocalTitle,
        String relationCode,
        UUID ruleId,
        UUID ruleSetId,
        RuleScope ruleSetScope,
        List<RuleScope> ruleSetChain,
        Facts facts,
        Lca lca,
        List<RelationPathStep> path,
        boolean cached) {

    public KinshipQueryResult {
        ruleSetChain = ruleSetChain == null ? List.of() : List.copyOf(ruleSetChain);
        path = path == null ? List.of() : List.copyOf(path);
    }

    public KinshipQueryResult withCached(boolean value) {
        return new KinshipQueryResult(status, fromPersonId, toPersonId, title, titleShort,
                egoSelfTerm, reciprocalTitle, relationCode, ruleId, ruleSetId, ruleSetScope,
                ruleSetChain, facts, lca, path, value);
    }

    public KinshipQueryResult withPath(List<RelationPathStep> value, Lca maskedLca) {
        return new KinshipQueryResult(status, fromPersonId, toPersonId, title, titleShort,
                egoSelfTerm, reciprocalTitle, relationCode, ruleId, ruleSetId, ruleSetScope,
                ruleSetChain, facts, maskedLca, value, cached);
    }

    /**
     * Dữ kiện đã dùng để so khớp luật.
     *
     * <p><b>Quy ước dấu của {@code genDelta} là quy ước của domain và của migration V3:</b>
     * {@code genDelta = dist_ego - dist_alter}, <b>dương</b> nghĩa là alter thuộc đời TRÊN (bố,
     * bác, ông). Toàn bộ 111 luật đã seed dùng quy ước này (ví dụ {@code CHA} có
     * {@code gen_delta = 1}). Phần mô tả trong {@code contracts/openapi.yaml} nói ngược lại —
     * xem ghi chú ở {@code KinshipController}.</p>
     */
    public record Facts(
            Integer genDelta,
            Integer collateralDegree,
            RelationSide side,
            Gender targetGender,
            Boolean isElder,
            RelationSide linkSide,
            Gender linkGender,
            InLawDirection inLawDirection,
            boolean throughMarriage,
            boolean throughAdoption) {
    }

    /** Tổ chung gần nhất kèm hai khoảng cách — bằng chứng chính của danh xưng huyết thống. */
    public record Lca(
            UUID personId,
            String displayName,
            Integer generation,
            int distanceFrom,
            int distanceTo) {

        public Lca withDisplayName(String value) {
            return new Lca(personId, value, generation, distanceFrom, distanceTo);
        }
    }
}

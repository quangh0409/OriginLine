package vn.giapha.kinship.api.dto;

import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.application.PathDirection;
import vn.giapha.kinship.domain.DirectLinkType;
import vn.giapha.kinship.domain.InLawDirection;
import vn.giapha.kinship.domain.KinshipStatus;
import vn.giapha.kinship.domain.RelationSide;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.shared.vo.Gender;

/**
 * Kết quả tra danh xưng trả cho client — hợp đồng {@code KinshipResult} của
 * {@code contracts/openapi.yaml} và type cùng tên của {@code contracts/schema.graphqls}.
 *
 * <p><b>Không tìm được vẫn là {@code 200}</b>, phân biệt bằng {@code status}. Giao diện tuyệt đối
 * không được coi {@code title} vắng mặt là lỗi hệ thống: {@code NO_MATCHING_RULE} nghĩa là Hội đồng
 * chưa viết luật cho tổ hợp này, và {@code facts} trả về chính là để họ biết phải viết luật cho tổ
 * hợp nào.</p>
 *
 * <p>{@code ruleId} + {@code ruleSetId} + {@code ruleSetScope} là <b>bằng chứng</b>: khi dòng họ
 * báo "gọi sai", ba trường này chỉ thẳng ra phải sửa luật nào ở bộ luật cấp nào.</p>
 *
 * @param egoSelfTerm      ego tự xưng là gì khi nói với alter — gọi "bác" thì xưng "cháu"
 * @param titleEn          luôn {@code null} ở Giai đoạn 1: bảng {@code kinship_rule} chưa có cột
 *                         nào chứa bản tiếng Anh, và bịa ra một ánh xạ trong code sẽ vi phạm đúng
 *                         nguyên tắc "danh xưng là dữ liệu" (FR-1.3a)
 */
public record KinshipResultDto(
        KinshipStatus status,
        UUID fromPersonId,
        UUID toPersonId,
        String title,
        String titleShort,
        String egoSelfTerm,
        String reciprocalTitle,
        String titleEn,
        String relationCode,
        RelationFactsDto facts,
        LcaInfoDto lca,
        List<KinshipPathStepDto> path,
        UUID ruleSetId,
        RuleScope ruleSetScope,
        UUID ruleId,
        List<RuleScope> ruleSetChain,
        boolean cached) {

    /**
     * Dữ kiện quan hệ đã chuẩn hoá — hợp đồng {@code RelationFacts}.
     *
     * <p><b>Quy ước dấu {@code genDelta}:</b> {@code dist_from - dist_to}, <b>dương</b> nghĩa là
     * người được gọi thuộc đời TRÊN. Đây là quy ước của migration V3, của
     * {@code R__seed_kinship_rules_default.sql} (luật {@code CHA} có {@code gen_delta = 1}) và của
     * {@code KinshipRuleDto.genDelta} ở ngay dưới đây. Phần mô tả trong {@code openapi.yaml} nói
     * ngược lại — xem ghi chú ở {@code KinshipController}.</p>
     */
    public record RelationFactsDto(
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

    /** Tổ chung gần nhất — hợp đồng {@code LcaInfo}. */
    public record LcaInfoDto(
            UUID personId,
            String displayName,
            Integer generation,
            int distanceFrom,
            int distanceTo) {
    }

    /**
     * Một chặng trên đường quan hệ — hợp đồng {@code KinshipPathStep}.
     *
     * <p>Người còn sống bị ẩn theo phân tầng vẫn <b>giữ nguyên chặng</b>, chỉ bị thay
     * {@code displayName} bằng nhãn chung. Bỏ chặng đi sẽ làm sai độ dài đường quan hệ mà người
     * dùng đọc được — tức là phá đúng cái giá trị mà đường quan hệ tồn tại để phục vụ.</p>
     */
    public record KinshipPathStepDto(
            UUID personId,
            String displayName,
            Integer generation,
            PathDirection direction,
            DirectLinkType viaRelType) {
    }
}

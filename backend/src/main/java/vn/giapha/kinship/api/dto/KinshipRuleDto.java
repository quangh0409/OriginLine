package vn.giapha.kinship.api.dto;

import java.util.UUID;
import vn.giapha.kinship.domain.DirectLinkType;
import vn.giapha.kinship.domain.InLawDirection;
import vn.giapha.kinship.domain.RelationSide;
import vn.giapha.kinship.domain.RuleScope;
import vn.giapha.shared.vo.Gender;

/**
 * Một luật danh xưng — hợp đồng {@code KinshipRuleDto} của {@code contracts/openapi.yaml}.
 *
 * <p><b>Đây là bản mở rộng của hợp đồng: cộng thêm, không đổi.</b> Mọi trường trong hợp đồng đều
 * còn nguyên tên và nguyên nghĩa; phần bổ sung là bắt buộc phải có vì {@code PUT} mang ngữ nghĩa
 * <i>ghi đè trọn vẹn</i> — gửi lên đúng các trường của hợp đồng thôi thì lần lưu đầu tiên sẽ
 * <b>xoá trắng</b> những cột mà 111 luật đã seed đang dựa vào. Cụ thể:</p>
 *
 * <ul>
 *   <li>{@code relationCode} — <b>bắt buộc</b>. Đây là khoá ghi đè giữa các cấp bộ luật và là cột
 *       {@code NOT NULL} của bảng {@code kinship_rule}. Hợp đồng thiếu hẳn trường này, nên
 *       {@code PUT} theo đúng hợp đồng là việc không thực hiện được. <b>Cần bổ sung vào
 *       contract.</b></li>
 *   <li>{@code genDeltaMin}/{@code genDeltaMax}, {@code collateralDegreeMin}/{@code Max} — các luật
 *       mở (ví dụ "tổ tiên từ đời thứ 6 trở lên") dùng khoảng thay vì giá trị chính xác.</li>
 *   <li>{@code titleShort} — dạng gọn để vẽ trên canvas phả đồ.</li>
 *   <li>{@code linkSide}, {@code linkGender}, {@code inLawDirection} — ba chiều <b>duy nhất</b>
 *       phân biệt được thím với mợ, con dâu với con rể. Mất chúng là mất toàn bộ nhóm dâu/rể.</li>
 *   <li>{@code directLink}, {@code directLinkSubtype}, {@code directLinkReversed} — cho các quan hệ
 *       mà LCA vô nghĩa (vợ/chồng) hoặc cho câu trả lời sai (bố nuôi vẫn là "bố nuôi"), và cho
 *       đích tôn/thừa tự/kế tự.</li>
 *   <li>{@code active} — tắt một luật mà không xoá, giữ lịch sử.</li>
 * </ul>
 *
 * <p><b>Hai chỗ đổi tên so với domain, cố ý giữ tên của hợp đồng:</b></p>
 * <ul>
 *   <li>{@code reciprocalTitle} ↔ {@code KinshipRule.egoSelfTerm}. Cách ego tự xưng khi nói với
 *       alter <i>chính là</i> cách alter gọi ego (gọi "bác" thì xưng "cháu", và bác gọi lại là
 *       "cháu"). Một trường, hai cách gọi tên.</li>
 *   <li>{@code note} ↔ {@code KinshipRule.description}.</li>
 * </ul>
 *
 * <p>{@code throughMarriage} không phải một cột riêng ở domain: quan hệ qua hôn nhân được biểu diễn
 * bằng {@code side = IN_LAW}. Đọc ra thì suy từ {@code side}; ghi vào thì {@code true} kéo theo
 * {@code side = IN_LAW} khi {@code side} bỏ trống.</p>
 *
 * <p>Ba trường {@code inheritedFrom*} / {@code overridden} chỉ có mặt trong phản hồi
 * {@code EffectiveKinshipRuleSet} (hợp đồng gộp chúng vào luật bằng {@code allOf}); ở các phản hồi
 * khác chúng để trống và khi gửi lên thì bị bỏ qua.</p>
 */
public record KinshipRuleDto(
        UUID id,
        String relationCode,
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
        Boolean directLinkReversed,
        Boolean throughMarriage,
        String title,
        String titleShort,
        String reciprocalTitle,
        Integer priority,
        Boolean active,
        String note,
        RuleScope inheritedFromScope,
        UUID inheritedFromRuleSetId,
        Boolean overridden) {
}

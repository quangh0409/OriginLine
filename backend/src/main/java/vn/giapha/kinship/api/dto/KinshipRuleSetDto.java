package vn.giapha.kinship.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RuleScope;

/**
 * Một bộ quy tắc danh xưng <b>thô</b> (chưa hợp nhất kế thừa) — hợp đồng
 * {@code KinshipRuleSetDto}.
 *
 * <p><b>Ba trường của hợp đồng chưa có nơi lưu trong migration V3</b> — trả về {@code null} chứ
 * không bịa:</p>
 * <ul>
 *   <li>{@code clanId} — bảng {@code kinship_rule_set} chỉ có {@code region} và {@code branch_id};
 *       bộ cấp {@code CLAN} hiện chỉ phân biệt được bằng {@code code}. Hệ thống Giai đoạn 1 phục vụ
 *       một dòng họ nên chưa vỡ, nhưng cần chốt lại: thêm cột hay bỏ trường.</li>
 *   <li>{@code updatedAt} / {@code updatedBy} — cột {@code updated_at} có trong bảng nhưng
 *       {@code KinshipRuleSet} ở domain không mang nó ra ngoài, và người sửa gần nhất thì thuộc về
 *       {@code audit_log} (W6).</li>
 * </ul>
 *
 * @param isSystemDefault suy từ {@code scope == DEFAULT} — bộ do hệ thống seed, {@code PUT} vào sẽ
 *                        nhận {@code 403 SYSTEM_RULE_SET_IMMUTABLE}
 */
public record KinshipRuleSetDto(
        UUID id,
        String code,
        RuleScope scope,
        String name,
        Region region,
        UUID clanId,
        UUID branchId,
        UUID parentRuleSetId,
        boolean isSystemDefault,
        boolean active,
        List<KinshipRuleDto> rules,
        long version,
        OffsetDateTime updatedAt,
        String updatedBy) {
}

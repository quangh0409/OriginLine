package vn.giapha.kinship.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.giapha.kinship.domain.Region;
import vn.giapha.kinship.domain.RuleScope;

/**
 * Yêu cầu <b>ghi đè trọn vẹn</b> một bộ quy tắc danh xưng — hợp đồng
 * {@code KinshipRuleSetUpdateRequest}. Chỉ {@code COUNCIL} / {@code ADMIN}.
 *
 * <p>Mảng {@code rules} <b>thay thế toàn bộ</b> danh sách luật hiện có của bộ đó, không phải patch
 * từng luật. Gửi mảng rỗng = xoá hết luật riêng của bộ này (bộ vẫn tồn tại và kế thừa nguyên vẹn
 * từ bộ cha). Muốn xoá một luật thì gửi danh sách thiếu luật ấy.</p>
 *
 * @param ruleSetId       bỏ trống ⇒ tạo bộ mới ({@code 201}); có giá trị ⇒ ghi đè bộ đó ({@code 200})
 * @param expectedVersion phiên bản đọc được từ {@code GET}; bắt buộc khi sửa bộ có sẵn, sai ⇒
 *                        {@code 409 OPTIMISTIC_LOCK_CONFLICT}
 * @param description     mô tả của chính bộ luật (cột {@code kinship_rule_set.description}).
 *                        <b>Mở rộng ngoài hợp đồng</b>: hợp đồng chỉ có {@code note}, mà
 *                        {@code note} là <i>lý do thay đổi</i> — hai thứ khác nhau. Không tách ra
 *                        thì mỗi lần Hội đồng ghi kèm lý do là mô tả bộ luật bị đè mất, đúng cái
 *                        bẫy mà ngữ nghĩa "ghi đè trọn vẹn" hay gây ra
 * @param note            lý do thay đổi, ghi vào {@code audit_log} — <b>không</b> lưu vào bộ luật
 */
public record KinshipRuleSetUpdateRequest(
        UUID ruleSetId,
        String code,
        @NotNull(message = "scope khong duoc bo trong") RuleScope scope,
        String name,
        Region region,
        UUID clanId,
        UUID branchId,
        UUID parentRuleSetId,
        @Size(max = 1000, message = "description toi da 1000 ky tu") String description,
        @NotNull(message = "rules khong duoc bo trong; gui mang rong neu muon xoa het luat")
        List<KinshipRuleDto> rules,
        Long expectedVersion,
        @Size(max = 1000, message = "note toi da 1000 ky tu") String note) {
}

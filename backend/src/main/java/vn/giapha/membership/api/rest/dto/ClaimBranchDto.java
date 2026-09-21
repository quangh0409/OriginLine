package vn.giapha.membership.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.giapha.membership.domain.BranchSummary;

/**
 * Chi đích của một lá đơn — câu trả lời cho <b>"đang chờ ai"</b>.
 *
 * <p>{@code clanTitle} là một <b>vai</b> ("Trưởng Chi Giáp", "Tộc trưởng"), không phải một con
 * người. Tên và số điện thoại của Trưởng chi <b>không</b> đi ra màn này (design 06 §7): người đọc
 * là một tài khoản vừa đăng ký, chưa được duyệt, chưa ở trong phả — cho họ đọc danh bạ ban quản
 * trị là một bề mặt không ai xin. Tên chi thì công khai; nó đã nằm trong {@code BranchRef} mà cổng
 * công khai trả cho Khách.</p>
 */
@Schema(description = "Chi đích của đơn — trả lời \"đang chờ ai\", bằng chức danh chứ không bằng tên người")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaimBranchDto(
        UUID id,

        @Schema(example = "Chi Giáp")
        String name,

        @Schema(description = "Loại chi", example = "CHI")
        String kind,

        @Schema(description = "Chức danh dòng tộc của người đứng đầu chi — một **vai**, không phải"
                + " một con người. Vắng mặt là câu trả lời đúng khi không dựng được.",
                example = "Trưởng Chi Giáp")
        String clanTitle) {

    public static ClaimBranchDto from(BranchSummary summary) {
        return summary == null ? null
                : new ClaimBranchDto(summary.id(), summary.name(), summary.kind(),
                        summary.clanTitle());
    }
}

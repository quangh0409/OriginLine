package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import vn.giapha.content.domain.HonourKind;

/**
 * Thân {@code PATCH /api/v1/honours/{id}}. Trường vắng mặt = giữ nguyên.
 *
 * <p>{@code personId} <b>không</b> sửa được: đổi chủ thể của một vinh danh đã duyệt là biến lời phê
 * duyệt cho người này thành lời phê duyệt cho người khác. Cần đổi thì gỡ rồi khai lại.</p>
 */
public record UpdateHonourRequest(
        HonourKind kind,

        @Size(max = 250, message = "Tieu de vinh danh toi da 250 ky tu")
        String title,

        @Min(value = 1000, message = "Nam vinh danh khong hop le")
        @Max(value = 2200, message = "Nam vinh danh khong hop le")
        Integer year,

        @Size(max = 250, message = "Noi cap toi da 250 ky tu")
        String issuer,

        @Size(max = 4000, message = "Mo ta toi da 4000 ky tu")
        String description) {
}

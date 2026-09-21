package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.Size;

/**
 * Thân {@code PATCH /api/v1/posts/{id}}.
 *
 * <p>Trường vắng mặt ({@code null}) = <b>giữ nguyên</b>, đúng ngữ nghĩa PATCH của cả sản phẩm.</p>
 */
public record UpdatePostRequest(
        @Size(max = 250, message = "Tieu de bai viet toi da 250 ky tu")
        String title,

        @Size(max = 200_000, message = "Than bai viet qua dai")
        String body) {
}

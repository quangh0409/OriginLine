package vn.giapha.clan.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Thân {@code POST /api/v1/admin/clan-profile/council-accounts}.
 *
 * @param loginId số điện thoại Việt Nam <b>hoặc</b> email — cùng luật với
 *                {@code /clan-invites/register}; khuôn dạng thật được kiểm ở {@code LoginIdentifier}
 * @param personId nhân khẩu (còn sống, chưa có tài khoản) để gắn ngay; vắng = gắn sau
 */
public record CreateCouncilAccountRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 254) String loginId,
        UUID personId) {
}

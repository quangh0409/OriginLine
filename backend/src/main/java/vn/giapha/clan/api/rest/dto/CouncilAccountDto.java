package vn.giapha.clan.api.rest.dto;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.membership.application.CouncilAccountCreated;

/**
 * Phản hồi {@code 201} của lượt lập tài khoản Hội đồng.
 *
 * <p>{@code setPasswordUrl} là <b>bí mật một lần</b> và đây là <b>lần duy nhất</b> nó xuất hiện:
 * không lưu, không ghi log, không có endpoint đọc lại. Giao diện hiện cho quản trị để trao tận tay.</p>
 */
public record CouncilAccountDto(UUID appUserId, String keycloakSub, String role,
                                String setPasswordUrl, Instant setPasswordExpiresAt) {

    public static CouncilAccountDto from(CouncilAccountCreated created) {
        return new CouncilAccountDto(created.appUserId(), created.keycloakSub(), created.role(),
                created.setPasswordUrl(), created.setPasswordExpiresAt());
    }
}

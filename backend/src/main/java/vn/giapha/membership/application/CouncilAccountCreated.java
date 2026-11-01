package vn.giapha.membership.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Kết quả lập tài khoản Hội đồng Tộc biểu.
 *
 * <p><b>{@link #setPasswordUrl} là bí mật một lần</b> — trao tận tay người ấy, không ghi log,
 * không ghi {@code audit_log}. Nó là cùng loại liên kết với luồng mời
 * ({@code SetPasswordTokenCodec}, màn {@code /dat-mat-khau}, endpoint
 * {@code POST /invitations/set-password}), không phải một cơ chế mới.</p>
 *
 * @param role luôn {@code "COUNCIL"} — trường có mặt để hợp đồng tự mô tả được
 */
public record CouncilAccountCreated(UUID appUserId, String keycloakSub, String role,
                                    String setPasswordUrl, Instant setPasswordExpiresAt) {
}

package vn.giapha.membership.application.command;

import java.util.UUID;

/**
 * Trưởng chi (hoặc vai cao hơn) xử một đơn tự nhận.
 *
 * @param claimId đơn cần xử
 * @param approve {@code true} = duyệt. Với đơn {@code EXISTING} thì gắn tài khoản vào nhân khẩu;
 *                với đơn {@code NEW_PERSON} thì <b>tạo nhân khẩu mới</b>, nối vào người thân được
 *                chỉ ra, rồi mới gắn tài khoản — tất cả trong một transaction
 * @param note    lý do. <b>Bắt buộc khi từ chối</b> — người gửi có quyền biết vì sao để khai lại
 *                cho đúng, và không nói lý do là cách chắc chắn nhất để họ gửi lại đúng cái đơn ấy
 */
public record ReviewPersonClaimCommand(UUID claimId, boolean approve, String note) {
}

package vn.giapha.content.application.command;

import java.util.UUID;

/**
 * Duyệt hoặc trả lại một bài viết.
 *
 * @param approve {@code true} = duyệt và đăng; {@code false} = trả về bản nháp
 * @param note    lý do. <b>Bắt buộc khi trả lại</b> — người viết có quyền biết vì sao, nếu không họ
 *                sẽ gửi lại y nguyên và cả hai bên cùng mất thời gian lần thứ hai
 */
public record ReviewPostCommand(UUID postId, boolean approve, String note) {
}

package vn.giapha.content.application.command;

import java.util.UUID;

/**
 * Duyệt hoặc từ chối một vinh danh.
 *
 * @param approve {@code true} = duyệt; {@code false} = từ chối (chuyển hẳn sang {@code WITHDRAWN})
 * @param note    lý do. <b>Bắt buộc khi từ chối.</b>
 */
public record ReviewHonourCommand(UUID honourId, boolean approve, String note) {
}

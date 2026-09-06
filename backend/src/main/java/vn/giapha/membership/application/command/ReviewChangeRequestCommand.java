package vn.giapha.membership.application.command;

import java.util.UUID;

/**
 * Quyết định của người duyệt trên một yêu cầu đính chính.
 *
 * @param changeRequestId yêu cầu cần xử lý
 * @param approve         {@code true} là duyệt, {@code false} là từ chối
 * @param note            ghi chú; <b>bắt buộc</b> khi từ chối — người gửi có quyền biết vì sao
 */
public record ReviewChangeRequestCommand(UUID changeRequestId, boolean approve, String note) {
}

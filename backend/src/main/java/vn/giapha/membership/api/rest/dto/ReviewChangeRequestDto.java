package vn.giapha.membership.api.rest.dto;

import jakarta.validation.constraints.Size;

/**
 * Thân yêu cầu {@code POST /api/v1/change-requests/{id}/review}.
 *
 * @param approve {@code true} duyệt, {@code false} từ chối
 * @param note    ghi chú; <b>bắt buộc</b> khi từ chối — người gửi có quyền biết vì sao
 */
public record ReviewChangeRequestDto(boolean approve, @Size(max = 2000) String note) {
}

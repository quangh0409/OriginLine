package vn.giapha.content.application.command;

import java.util.UUID;
import vn.giapha.content.domain.HonourKind;

/**
 * Sửa một vinh danh. Trường {@code null} = giữ nguyên.
 *
 * @param expectedVersion từ {@code If-Match}; bắt buộc, cùng lý do với bài viết
 */
public record UpdateHonourCommand(UUID honourId, HonourKind kind, String title, Integer year,
                                  String issuer, String description, Long expectedVersion) {
}

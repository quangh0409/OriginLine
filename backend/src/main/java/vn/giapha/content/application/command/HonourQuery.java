package vn.giapha.content.application.command;

import java.util.UUID;
import vn.giapha.content.domain.ContentStatus;
import vn.giapha.content.domain.HonourKind;

/**
 * Tham số tra cứu vinh danh.
 *
 * @param branchId lọc theo chi — tính cả <b>cây con</b> ({@code ltree}), vì "Chi Giáp có bao nhiêu
 *        người đỗ đạt" phải gồm các ngành/cành bên dưới
 */
public record HonourQuery(UUID personId, HonourKind kind, UUID branchId, ContentStatus status,
                          int page, int size) {
}

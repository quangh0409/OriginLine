package vn.giapha.content.application.command;

import java.util.UUID;
import vn.giapha.content.domain.HonourKind;

/**
 * Khai một vinh danh.
 *
 * <p>Khác {@code CreatePostCommand}, ở đây <b>có</b> trường {@code personId}: vinh danh là dữ liệu
 * <i>về</i> một người, và việc con cháu ghi công cho các cụ đã khuất chính là ca thường gặp nhất.
 * Đúng vì thế mà bước duyệt tồn tại — xem {@code Honour}.</p>
 */
public record CreateHonourCommand(UUID personId, HonourKind kind, String title, Integer year,
                                  String issuer, String description) {
}

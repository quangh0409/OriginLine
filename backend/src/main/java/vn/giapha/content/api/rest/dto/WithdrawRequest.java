package vn.giapha.content.api.rest.dto;

import jakarta.validation.constraints.Size;

/**
 * Thân {@code POST /api/v1/posts/{id}/withdraw}. Thân rỗng cũng hợp lệ.
 *
 * @param reason lý do gỡ. Không bắt buộc — tác giả bỏ bản nháp của chính mình thì không nợ ai một
 *        lời giải thích. Nhưng khi <i>người duyệt</i> gỡ một bài đã đăng thì lý do là thứ duy nhất
 *        tác giả còn đọc được, nên giao diện nên hỏi ở ca đó
 */
public record WithdrawRequest(
        @Size(max = 2000, message = "Ly do toi da 2000 ky tu")
        String reason) {
}

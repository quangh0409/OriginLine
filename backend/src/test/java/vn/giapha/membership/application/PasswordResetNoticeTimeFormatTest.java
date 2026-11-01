package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Giờ hết hạn trong thông báo "có người vừa cấp liên kết đặt lại mật khẩu" — câu mà CHÍNH CHỦ
 * đọc trên hộp thư. Đọc thật ngày 24/09 thấy "hiệu lực tới 02:04 ngay 24/09/2026": chữ "ngày"
 * mất dấu vì định dạng được viết theo thói quen của chuỗi log.
 */
@DisplayName("Thông báo cấp lại mật khẩu — định dạng giờ tiếng Việt")
class PasswordResetNoticeTimeFormatTest {

    @Test
    @DisplayName("giờ Việt Nam (GMT+7) và chữ 'ngày' có dấu")
    void ngayCoDau() {
        // 19:04Z = 02:04 ngay hom sau o GMT+7
        String s = PasswordResetGrantWriter.GIO_VN.format(Instant.parse("2026-09-23T19:04:00Z"));
        assertThat(s).isEqualTo("02:04 ngày 24/09/2026");
    }
}

package vn.giapha.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Dấu vết HTTP: IP, {@code User-Agent}, mã tương quan.
 *
 * <p>Ba cột này chỉ điền được khi có {@code HttpServletRequest}, mà cổng audit còn được gọi từ job
 * nền vốn không có request nào — nên {@link RequestFingerprint#none()} là trạng thái <b>hợp lệ</b>,
 * không phải lỗi.</p>
 */
class RequestFingerprintTest {

    @Test
    @DisplayName("Không có ngữ cảnh HTTP là trạng thái hợp lệ")
    void khongCoNguCanhHttp() {
        assertThat(RequestFingerprint.none().isEmpty()).isTrue();
        assertThat(RequestFingerprint.none().ipAddress()).isNull();
        assertThat(new RequestFingerprint(null, null, null).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Chuỗi rỗng và khoảng trắng thành null, không thành chuỗi rỗng")
    void rongThanhNull() {
        RequestFingerprint dau = new RequestFingerprint("   ", "", "  ");

        assertThat(dau.isEmpty()).isTrue();
        assertThat(dau.ipAddress()).isNull();
        assertThat(dau.userAgent()).isNull();
        assertThat(dau.requestId()).isNull();
    }

    @Test
    @DisplayName("User-Agent và request_id quá dài bị cắt ngay tại đây, không để chạm CSDL")
    void catChuoiQuaDai() {
        // user_agent la VARCHAR(255), request_id la VARCHAR(64). Mot chuoi UA dai qua co khong duoc
        // phep lam hong chinh giao dich nghiep vu ma no dang ghi vet.
        RequestFingerprint dau = new RequestFingerprint("10.0.0.1",
                "U".repeat(400), "R".repeat(100));

        assertThat(dau.userAgent()).hasSize(RequestFingerprint.MAX_USER_AGENT);
        assertThat(dau.requestId()).hasSize(RequestFingerprint.MAX_REQUEST_ID);
    }

    @Test
    @DisplayName("Bỏ khoảng trắng thừa quanh giá trị")
    void catKhoangTrang() {
        RequestFingerprint dau = new RequestFingerprint(" 10.0.0.1 ", " Mozilla ", " req-1 ");

        assertThat(dau.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(dau.userAgent()).isEqualTo("Mozilla");
        assertThat(dau.requestId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("Gộp: giữ giá trị của bản này, bù trường trống từ bản kia")
    void gopHaiBan() {
        RequestFingerprint tuInterceptor = new RequestFingerprint("10.0.0.1", "Mozilla", null);
        RequestFingerprint tuMdc = new RequestFingerprint(null, null, "trace-abc");

        RequestFingerprint gop = tuInterceptor.mergeWith(tuMdc);

        assertThat(gop.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(gop.userAgent()).isEqualTo("Mozilla");
        assertThat(gop.requestId()).isEqualTo("trace-abc");
    }

    @Test
    @DisplayName("Gộp không ghi đè giá trị đã có")
    void gopKhongGhiDe() {
        RequestFingerprint co = new RequestFingerprint("10.0.0.1", "Mozilla", "req-1");

        RequestFingerprint gop = co.mergeWith(new RequestFingerprint("9.9.9.9", "Curl", "req-2"));

        assertThat(gop).isEqualTo(co);
    }

    @Test
    @DisplayName("Gộp với null trả lại chính nó")
    void gopVoiNull() {
        RequestFingerprint co = new RequestFingerprint("10.0.0.1", null, null);

        assertThat(co.mergeWith(null)).isEqualTo(co);
    }
}

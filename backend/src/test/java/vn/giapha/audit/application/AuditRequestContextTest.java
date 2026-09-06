package vn.giapha.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import vn.giapha.audit.domain.RequestFingerprint;

/**
 * Ngữ cảnh HTTP theo thread, sống từ đầu request tới lúc luồng ghi audit cần nó.
 *
 * <p>Điều quan trọng nhất được kiểm ở đây là <b>dọn sạch</b>. Servlet container tái sử dụng thread;
 * quên dọn thì request sau sẽ được ghi audit với IP của request trước — vừa sai dữ liệu vừa sai về
 * pháp lý.</p>
 */
class AuditRequestContextTest {

    @AfterEach
    void don() {
        AuditRequestContext.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Không có ngữ cảnh thì trả none(), KHÔNG trả null")
    void khongCoNguCanh() {
        AuditRequestContext.clear();

        assertThat(AuditRequestContext.current()).isNotNull();
        assertThat(AuditRequestContext.current().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Đặt rồi đọc lại đúng ba mảnh siêu dữ liệu")
    void datRoiDocLai() {
        AuditRequestContext.set(new RequestFingerprint("10.0.0.1", "Mozilla", "req-1"));

        RequestFingerprint hienTai = AuditRequestContext.current();

        assertThat(hienTai.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(hienTai.userAgent()).isEqualTo("Mozilla");
        assertThat(hienTai.requestId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("clear() xoá sạch — request kế tiếp trên cùng thread không thừa hưởng IP")
    void donSachSauRequest() {
        AuditRequestContext.set(new RequestFingerprint("10.0.0.1", "Mozilla", "req-1"));

        AuditRequestContext.clear();

        assertThat(AuditRequestContext.current().ipAddress()).isNull();
        assertThat(AuditRequestContext.current().userAgent()).isNull();
    }

    @Test
    @DisplayName("Đặt dấu vết rỗng cũng là dọn")
    void datRongLaDon() {
        AuditRequestContext.set(new RequestFingerprint("10.0.0.1", null, null));

        AuditRequestContext.set(RequestFingerprint.none());

        assertThat(AuditRequestContext.current().ipAddress()).isNull();
        AuditRequestContext.set(null);
        assertThat(AuditRequestContext.current().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Không có interceptor thì vẫn lấy được request_id từ MDC của OpenTelemetry")
    void layRequestIdTuMdc() {
        AuditRequestContext.clear();
        MDC.put("traceId", "otel-trace-abc");

        assertThat(AuditRequestContext.current().requestId()).isEqualTo("otel-trace-abc");
    }

    @Test
    @DisplayName("Có ngữ cảnh mà thiếu request_id thì bù từ MDC")
    void buRequestIdTuMdc() {
        AuditRequestContext.set(new RequestFingerprint("10.0.0.1", "Mozilla", null));
        MDC.put("traceId", "otel-trace-abc");

        RequestFingerprint hienTai = AuditRequestContext.current();

        assertThat(hienTai.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(hienTai.requestId()).isEqualTo("otel-trace-abc");
    }

    @Test
    @DisplayName("KHÔNG kế thừa sang thread con — rò ngữ cảnh giữa hai người dùng là điều tệ nhất")
    void khongKeThuaSangThreadCon() throws Exception {
        // Khong dung InheritableThreadLocal: luong ghi audit chay trong cung thread voi nghiep vu,
        // con viec ke thua sang thread con cua mot pool la cach chac chan nhat de ro ngu canh.
        AuditRequestContext.set(new RequestFingerprint("10.0.0.1", "Mozilla", "req-1"));
        AtomicReference<RequestFingerprint> oThreadCon = new AtomicReference<>();
        CountDownLatch xong = new CountDownLatch(1);

        Thread con = new Thread(() -> {
            oThreadCon.set(AuditRequestContext.current());
            xong.countDown();
        });
        con.start();

        assertThat(xong.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(oThreadCon.get().ipAddress()).isNull();
    }
}

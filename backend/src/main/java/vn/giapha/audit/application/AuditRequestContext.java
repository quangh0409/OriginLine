package vn.giapha.audit.application;

import org.slf4j.MDC;
import vn.giapha.audit.domain.RequestFingerprint;

/**
 * Giữ dấu vết HTTP của request đang chạy cho tới khi luồng ghi audit cần tới nó.
 *
 * <h2>Vì sao là ThreadLocal chứ không phải bean {@code @RequestScope}</h2>
 * Cổng audit được gọi từ tận đáy tầng infrastructure, và trong nhiều trường hợp <b>không</b> có
 * request nào (job nhắc giỗ, consumer RabbitMQ, migration dữ liệu). Một bean request-scope sẽ ném
 * {@code ScopeNotActiveException} ở đúng những chỗ đó; ThreadLocal chỉ trả {@link
 * RequestFingerprint#none()} và đi tiếp.
 *
 * <p><b>Bắt buộc {@link #clear()} trong {@code finally}.</b> Servlet container tái sử dụng thread;
 * quên dọn thì request sau sẽ được ghi audit với IP của request trước — một lỗi vừa sai dữ liệu vừa
 * sai về pháp lý. {@code AuditInterceptor#afterCompletion} là nơi dọn.</p>
 *
 * <p>Không dùng {@code InheritableThreadLocal}: luồng ghi audit chạy trong cùng thread với nghiệp
 * vụ, còn việc kế thừa sang thread con của một pool là cách chắc chắn nhất để rò ngữ cảnh giữa các
 * người dùng.</p>
 */
public final class AuditRequestContext {

    /** Khoá MDC do OpenTelemetry/Micrometer đặt; lấy cái nào có để nối audit với trace. */
    static final String[] MDC_REQUEST_ID_KEYS = {"requestId", "traceId", "trace_id"};

    private static final ThreadLocal<RequestFingerprint> CURRENT = new ThreadLocal<>();

    private AuditRequestContext() {
    }

    /** Không bao giờ trả {@code null} — thiếu ngữ cảnh HTTP là trạng thái hợp lệ. */
    public static RequestFingerprint current() {
        RequestFingerprint held = CURRENT.get();
        RequestFingerprint fromMdc = new RequestFingerprint(null, null, mdcRequestId());
        if (held == null) {
            return fromMdc.isEmpty() ? RequestFingerprint.none() : fromMdc;
        }
        return held.mergeWith(fromMdc);
    }

    public static void set(RequestFingerprint fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            CURRENT.remove();
        } else {
            CURRENT.set(fingerprint);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static String mdcRequestId() {
        for (String key : MDC_REQUEST_ID_KEYS) {
            String value = MDC.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

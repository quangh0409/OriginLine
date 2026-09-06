package vn.giapha.audit.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.giapha.audit.application.AuditRequestContext;
import vn.giapha.audit.domain.RequestFingerprint;

/**
 * Nạp dấu vết HTTP cho mỗi request để {@code AuditTrailService} điền được ba cột mà W2 đành để
 * trống: {@code ip_address}, {@code user_agent}, {@code request_id}.
 *
 * <h2>IP thật sau reverse proxy</h2>
 * {@code getRemoteAddr()} sau Nginx/Traefik luôn là IP của chính proxy. Thứ tự ưu tiên ở đây là
 * {@code X-Forwarded-For} (phần tử <b>đầu tiên</b> — client gốc) rồi {@code X-Real-IP} rồi
 * {@code getRemoteAddr()}.
 *
 * <p><b>Cảnh báo tin cậy:</b> hai header đó do client gửi lên và <i>giả được</i>. Ở đây chúng chỉ
 * phục vụ truy vết, không bao giờ được dùng để ra quyết định phân quyền. Khi triển khai thật, proxy
 * phải ghi đè chúng, và {@code server.forward-headers-strategy=framework} trong
 * {@code application.yml} đã bật sẵn cơ chế chuẩn của Spring cho phần scheme/host.</p>
 *
 * <h2>{@code request_id} nối audit với log</h2>
 * Nếu client gửi {@code X-Request-Id} thì tôn trọng, không thì sinh một UUID. Giá trị được đặt vào
 * MDC dưới khoá {@code requestId} nên hai thứ cùng hưởng: mọi dòng log của request có cùng mã, và
 * {@code AuditJdbcAdapter} của context {@code genealogy} — vốn đã đọc MDC — bắt đầu ghi được
 * {@code request_id} mà <b>không cần sửa một dòng nào bên đó</b>. Mã cũng được trả lại trong header
 * phản hồi để người dùng báo lỗi kèm được mã.
 *
 * <h2>Không log giá trị Tầng 3</h2>
 * Interceptor này cố ý <b>không</b> đọc thân request, không đọc tham số truy vấn, không đọc header
 * {@code Authorization}. Nó chỉ lấy ba mẩu siêu dữ liệu ở trên. Số điện thoại, email và ảnh không
 * bao giờ đi qua đây.
 */
public class AuditInterceptor implements HandlerInterceptor {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_REAL_IP = "X-Real-IP";
    private static final String HEADER_USER_AGENT = "User-Agent";

    /** Đường dẫn không cần dấu vết: thăm dò sức khoẻ gọi liên tục, ghi vết chỉ tạo nhiễu. */
    private static final List<String> SKIPPED_PREFIXES = List.of("/actuator/health", "/actuator/prometheus");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        for (String prefix : SKIPPED_PREFIXES) {
            if (path != null && path.startsWith(prefix)) {
                return true;
            }
        }

        String requestId = firstNonBlank(request.getHeader(HEADER_REQUEST_ID),
                MDC.get(MDC_REQUEST_ID));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        requestId = truncate(requestId, RequestFingerprint.MAX_REQUEST_ID);

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        AuditRequestContext.set(new RequestFingerprint(
                clientIp(request), request.getHeader(HEADER_USER_AGENT), requestId));
        return true;
    }

    /**
     * Dọn <b>luôn</b>, kể cả khi handler ném ngoại lệ.
     *
     * <p>Servlet container tái sử dụng thread. Quên dọn thì request kế tiếp trên cùng thread sẽ
     * được ghi audit với IP và {@code User-Agent} của người dùng trước — vừa sai dữ liệu vừa sai
     * về pháp lý.</p>
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuditRequestContext.clear();
        MDC.remove(MDC_REQUEST_ID);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded;
        }
        String realIp = request.getHeader(HEADER_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second != null && !second.isBlank() ? second.trim() : null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

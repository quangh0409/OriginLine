package vn.giapha.audit.domain;


/**
 * Dấu vết HTTP của một request: địa chỉ IP, {@code User-Agent} và mã tương quan.
 *
 * <p>Ba cột {@code ip_address} / {@code user_agent} / {@code request_id} của {@code audit_log}
 * chỉ điền được khi có {@code HttpServletRequest}, mà cổng audit còn được gọi từ job nền vốn không
 * có request nào — nên chúng nằm ở một value object riêng, và {@link #none()} là trạng thái hợp lệ,
 * không phải lỗi.</p>
 *
 * <p><b>Giới hạn độ dài được cắt ngay tại đây</b>, không để tới lúc chạm CSDL: {@code user_agent}
 * là {@code VARCHAR(255)} còn {@code request_id} là {@code VARCHAR(64)}, và một chuỗi UA dài quá
 * cỡ không được phép làm hỏng chính giao dịch nghiệp vụ mà nó đang ghi vết.</p>
 *
 * @param ipAddress địa chỉ IP dạng chuỗi, sẽ được ép sang {@code inet}; {@code null} nếu không rõ
 * @param userAgent header {@code User-Agent}, đã cắt còn tối đa 255 ký tự
 * @param requestId mã tương quan để nối audit với log/trace, tối đa 64 ký tự
 */
public record RequestFingerprint(String ipAddress, String userAgent, String requestId) {

    public static final int MAX_USER_AGENT = 255;
    public static final int MAX_REQUEST_ID = 64;

    private static final RequestFingerprint NONE = new RequestFingerprint(null, null, null);

    public RequestFingerprint {
        ipAddress = blankToNull(ipAddress);
        userAgent = truncate(blankToNull(userAgent), MAX_USER_AGENT);
        requestId = truncate(blankToNull(requestId), MAX_REQUEST_ID);
    }

    /** Không có ngữ cảnh HTTP — job nền, consumer RabbitMQ, scheduler. */
    public static RequestFingerprint none() {
        return NONE;
    }

    public boolean isEmpty() {
        return ipAddress == null && userAgent == null && requestId == null;
    }

    /** Giữ {@code requestId} của bản này, bù hai trường còn lại từ bản khác nếu đang trống. */
    public RequestFingerprint mergeWith(RequestFingerprint other) {
        if (other == null) {
            return this;
        }
        return new RequestFingerprint(
                ipAddress != null ? ipAddress : other.ipAddress,
                userAgent != null ? userAgent : other.userAgent,
                requestId != null ? requestId : other.requestId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

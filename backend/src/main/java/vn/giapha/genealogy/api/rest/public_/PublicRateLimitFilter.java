package vn.giapha.genealogy.api.rest.public_;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hạn mức tần suất cho bề mặt <b>không cần đăng nhập</b> {@code /api/v1/public/**}.
 *
 * <h2>Đếm theo chi phí, không theo lượt gọi</h2>
 * Ba endpoint công khai không đắt như nhau và cũng không nguy hiểm như nhau:
 * <table border="1">
 *   <caption>Bảng giá</caption>
 *   <tr><th>Endpoint</th><th>Điểm</th><th>Vì sao</th></tr>
 *   <tr><td>{@code /public/persons/{id}}</td><td>1</td>
 *       <td>Một bản ghi, và người gọi phải <b>biết trước</b> định danh.</td></tr>
 *   <tr><td>{@code /public/tree}</td><td>3</td>
 *       <td>Trả hàng trăm node một lượt — đây là công cụ quét phả hiệu quả nhất.</td></tr>
 *   <tr><td>{@code /public/persons/search}</td><td>5</td>
 *       <td>Là lối <b>liệt kê</b> duy nhất: đổi từ khoá là dò ra định danh mới mà không cần biết
 *           trước gì cả. Đắt nhất là đúng.</td></tr>
 * </table>
 *
 * <h2>Hai cửa sổ thời gian, chặn hai kiểu lạm dụng khác nhau</h2>
 * Hạn mức <b>phút</b> chặn cú quét dồn dập; hạn mức <b>ngày</b> chặn kiểu quét chậm mà đều — thứ
 * mà chỉ có giới hạn theo phút thì không bao giờ bắt được, vì mỗi phút nó chỉ gọi vài lượt nhưng
 * chạy suốt 24 giờ là đủ kéo hết cả dòng họ.
 *
 * <h2>Những gì lớp này KHÔNG làm, và làm ở đâu mới đúng</h2>
 * <ul>
 *   <li><b>Bộ đếm nằm trong bộ nhớ của một tiến trình.</b> Chạy nhiều bản sao thì hạn mức thực tế
 *       nhân lên bấy nhiêu lần. Đó là đánh đổi có chủ ý: đặt bộ đếm vào Redis nghĩa là cổng thông
 *       tin công khai <i>chết theo Redis</i>, trong khi phòng tuyến đúng đắn ở quy mô đó là CDN/WAF
 *       phía trước chứ không phải một vòng lặp đếm trong ứng dụng.</li>
 *   <li><b>Không chống được botnet.</b> Hạn mức theo IP chỉ nâng giá của việc quét, không cấm được
 *       nó. Trần độ sâu, trần số node và chặn phân trang sâu ở
 *       {@link PublicPortalProperties} mới là thứ giới hạn <i>khối lượng</i> lấy được mỗi lượt.</li>
 * </ul>
 *
 * <p>{@code getRemoteAddr()} là nguồn địa chỉ, <b>không</b> tự đọc {@code X-Forwarded-For}:
 * {@code server.forward-headers-strategy: framework} đã để Spring xử lý header đó theo cấu hình
 * proxy tin cậy. Tự đọc header là mời kẻ quét đổi IP giả sau mỗi lượt gọi.</p>
 */
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicRateLimitFilter.class);

    /** Tiền tố mà bộ lọc này canh; khớp luật {@code permitAll} trong {@code SecurityConfig}. */
    static final String PUBLIC_PREFIX = "/api/v1/public";

    private static final int COST_SEARCH = 5;
    private static final int COST_TREE = 3;
    private static final int COST_DEFAULT = 1;

    /** Entry không được chạm tới trong khoảng này sẽ bị dọn khi bảng đầy. */
    private static final long SWEEP_IDLE_SECONDS = 120;

    private final PublicPortalProperties properties;
    private final Map<String, Budget> budgets = new ConcurrentHashMap<>();

    public PublicRateLimitFilter(PublicPortalProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.isRateLimitEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        int cost = costOf(request.getRequestURI());
        Instant now = Instant.now();
        Decision decision = charge(client, cost, now);

        if (!decision.allowed()) {
            // Khong log dia chi IP o muc INFO: chinh no cung la du lieu ca nhan theo Nghi dinh 13.
            log.info("429 {} - vuot han muc cong thong tin cong khai ({})",
                    request.getRequestURI(), decision.reason());
            writeTooManyRequests(request, response, decision.retryAfterSeconds());
            return;
        }
        response.setHeader("X-RateLimit-Remaining-Minute", String.valueOf(decision.remainingMinute()));
        chain.doFilter(request, response);
    }

    private int costOf(String uri) {
        if (uri.startsWith(PUBLIC_PREFIX + "/persons/search")) {
            return COST_SEARCH;
        }
        if (uri.startsWith(PUBLIC_PREFIX + "/tree")) {
            return COST_TREE;
        }
        return COST_DEFAULT;
    }

    /**
     * Trừ hạn mức của một địa chỉ, nguyên tử theo từng khoá.
     *
     * <p>Dùng {@code compute} chứ không đọc–sửa–ghi: hai request song song của cùng một IP là
     * chuyện bình thường, và đọc–sửa–ghi sẽ để lọt đúng thứ mà bộ đếm sinh ra để chặn.</p>
     */
    private Decision charge(String client, int cost, Instant now) {
        long minuteWindow = now.getEpochSecond() / 60;
        long dayWindow = now.getEpochSecond() / 86_400;

        if (!budgets.containsKey(client)) {
            makeRoomFor(client, now);
        }

        Budget updated = budgets.compute(client, (key, current) -> {
            Budget budget = current == null ? new Budget() : current;
            if (budget.minuteWindow != minuteWindow) {
                budget.minuteWindow = minuteWindow;
                budget.minuteCost = 0;
            }
            if (budget.dayWindow != dayWindow) {
                budget.dayWindow = dayWindow;
                budget.dayCost = 0;
            }
            budget.minuteCost += cost;
            budget.dayCost += cost;
            budget.lastSeenEpochSecond = now.getEpochSecond();
            return budget;
        });

        if (updated.dayCost > properties.getCostPerDay()) {
            long retry = 86_400 - (now.getEpochSecond() % 86_400);
            return new Decision(false, "han muc ngay", retry, 0);
        }
        if (updated.minuteCost > properties.getCostPerMinute()) {
            long retry = 60 - (now.getEpochSecond() % 60);
            return new Decision(false, "han muc phut", retry, 0);
        }
        return new Decision(true, null, 0,
                Math.max(0, properties.getCostPerMinute() - updated.minuteCost));
    }

    /**
     * Giữ bảng đếm không phình vô hạn.
     *
     * <p>Bản thân bộ đếm cũng là một bề mặt tấn công: mỗi IP mới là một entry, và một kẻ giả mạo
     * hàng triệu IP sẽ làm cạn bộ nhớ bằng chính cơ chế bảo vệ. Khi bảng đầy thì dọn các entry lâu
     * không dùng; dọn xong vẫn đầy thì <b>cho qua</b> và ghi cảnh báo — thà mất hạn mức trong một
     * cơn tấn công còn hơn đóng cổng thông tin dòng họ với mọi người.</p>
     */
    private void makeRoomFor(String client, Instant now) {
        if (budgets.size() < properties.getMaxTrackedClients()) {
            return;
        }
        Iterator<Map.Entry<String, Budget>> it = budgets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Budget> entry = it.next();
            if (now.getEpochSecond() - entry.getValue().lastSeenEpochSecond > SWEEP_IDLE_SECONDS) {
                it.remove();
            }
        }
        if (budgets.size() >= properties.getMaxTrackedClients()) {
            log.warn("Bang han muc cong thong tin cong khai da day ({} muc) - tam thoi cho qua."
                    + " Neu tinh trang nay keo dai thi phong tuyen dung phai la CDN/WAF phia truoc.",
                    budgets.size());
        }
    }

    /**
     * Thân lỗi theo RFC 7807, viết tay vì bộ lọc chạy <b>trước</b> {@code @ControllerAdvice}.
     *
     * <p>Không có mảnh dữ liệu nào do người dùng nhập lọt vào chuỗi JSON này, nên không cần thoát
     * ký tự — nếu về sau thêm giá trị động vào đây thì phải chuyển sang {@code ObjectMapper}.</p>
     */
    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                      long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.getWriter().write("{"
                + "\"type\":\"https://giapha.vn/problems/rate-limited\","
                + "\"title\":\"Vượt quá hạn mức truy cập\","
                + "\"status\":429,"
                + "\"detail\":\"Cong thong tin cong khai gioi han tan suat truy cap."
                + " Vui long thu lai sau, hoac dang nhap de tra cuu day du.\","
                + "\"instance\":\"" + sanitizeInstance(request.getRequestURI()) + "\","
                + "\"code\":\"RATE_LIMITED\","
                + "\"timestamp\":\"" + Instant.now() + "\"}");
    }

    /** Chỉ giữ ký tự an toàn của đường dẫn — đề phòng ai đó nhét dấu nháy vào URI. */
    private String sanitizeInstance(String uri) {
        return uri == null ? "" : uri.replaceAll("[^A-Za-z0-9/_.\\-]", "");
    }

    /** Trạng thái hạn mức của một địa chỉ. Chỉ đọc/ghi bên trong {@code compute}. */
    private static final class Budget {
        private long minuteWindow;
        private int minuteCost;
        private long dayWindow;
        private int dayCost;
        private long lastSeenEpochSecond;
    }

    private record Decision(boolean allowed, String reason, long retryAfterSeconds,
                            int remainingMinute) {
    }
}

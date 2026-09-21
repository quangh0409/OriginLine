package vn.giapha.membership.application;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.giapha.membership.domain.InvitationCode;
import vn.giapha.membership.domain.port.InviteThrottlePort;

/**
 * Giới hạn tần suất thử mã mời — <b>lớp chống đỡ thứ ba</b>, ngang hàng với "dùng một lần" và
 * "hết hạn ngắn".
 *
 * <h2>Vì sao ba lớp, không phải hai</h2>
 * Màn nhận lời mời hiện tên của một người đang sống. Mã mời vì thế là một khoá mở dữ liệu Tầng 1
 * (BA v2 §10) và nó đi qua SMS hoặc Zalo cá nhân — kênh kém an toàn nhất, và là kênh dòng họ không
 * kiểm soát. Mã 50 bit thì không dò ra được bằng vét cạn, đúng; nhưng nếu không đếm thì ta cũng
 * <b>không bao giờ biết có người đang dò</b>, và một lời mời cụ thể bị chuyển tiếp qua nhiều tay
 * sẽ không để lại dấu vết nào. Đếm mới là thứ đáng giá ở đây, chặn chỉ là hệ quả.
 *
 * <h2>Khoá đếm là người gọi, không phải lời mời</h2>
 * Người dò mã không trúng lời mời nào, nên một bộ đếm gắn trên dòng {@code invitation} chỉ đếm được
 * những lần đoán <i>đúng</i> — tức là đếm sau khi mất bò.
 *
 * <h2>Chỉ đếm lần THẤT BẠI</h2>
 * Một người được mời gõ sai vài lần rồi gõ đúng thì không bị coi là đang tấn công. Đếm cả lần thành
 * công sẽ phạt đúng người mà luồng này phục vụ.
 */
@Service
public class InviteThrottle {

    private static final Logger log = LoggerFactory.getLogger(InviteThrottle.class);

    static final String OUTCOME_FAILED = "FAILED";
    static final String OUTCOME_PREVIEW_OK = "PREVIEW_OK";
    static final String OUTCOME_ACCEPTED_OK = "ACCEPTED_OK";

    /**
     * Hai kết quả của luồng <b>mã mời dòng họ</b> (V16) — cùng bảng, cùng bộ đếm, cùng ngưỡng.
     *
     * <p><b>Không dựng bộ đếm thứ hai</b>, và đó là quyết định chứ không phải tiết kiệm: câu hỏi mà
     * cả hai luồng hỏi là một — "người gọi này đã thử sai mã bao nhiêu lần trong một giờ qua?". Hai
     * bộ đếm tách rời nghĩa là kẻ dò được cấp ngưỡng gấp đôi chỉ bằng cách xen kẽ hai endpoint, và
     * không ai nhìn ra điều đó khi đọc riêng từng bảng.</p>
     *
     * <p>Với mã dòng họ, giới hạn tần suất nặng hơn một bậc so với mã cá nhân: mã cá nhân chết sau
     * một lần dùng, còn mã dòng họ <b>sống suốt hạn</b> — dò trúng là mở được cửa cho tới khi Hội
     * đồng thu hồi. Ở luồng cá nhân đây là lớp chống đỡ thứ ba; ở đây nó là lớp thứ nhất.</p>
     */
    static final String OUTCOME_CLAN_PREVIEW_OK = "CLAN_PREVIEW_OK";

    static final String OUTCOME_CLAN_REGISTER_OK = "CLAN_REGISTER_OK";

    private final InviteThrottlePort attempts;
    private final int maxFailures;
    private final Duration window;

    public InviteThrottle(InviteThrottlePort attempts,
                          @Value("${giapha.membership.invitation.max-failed-attempts:10}") int maxFailures,
                          @Value("${giapha.membership.invitation.attempt-window-minutes:60}") long windowMinutes) {
        this.attempts = attempts;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /**
     * Chặn trước khi tra mã.
     *
     * <p>Gọi <b>trước</b> chứ không phải sau: nếu chỉ đếm sau khi tra thì mỗi lần dò vẫn tốn đúng
     * một lượt tra CSDL và kẻ dò vẫn đo được thời gian phản hồi.</p>
     *
     * @param clientId định danh thô của người gọi (địa chỉ IP); được băm ngay tại đây
     * @throws TooManyAttemptsException khi vượt ngưỡng
     */
    public void guard(String clientId) {
        String key = keyOf(clientId);
        Instant since = Instant.now().minus(window);
        int failures = attempts.failuresSince(key, since);
        if (failures >= maxFailures) {
            log.warn("Chan tra ma moi: {} lan that bai trong {} phut tu mot nguoi goi",
                    failures, window.toMinutes());
            throw new TooManyAttemptsException(
                    "Da thu ma moi qua nhieu lan, hay doi roi thu lai hoac goi Truong chi",
                    window.toSeconds());
        }
    }

    /** Ghi nhận một lần thử thất bại. */
    public void recordFailure(String clientId) {
        attempts.record(keyOf(clientId), OUTCOME_FAILED);
    }

    /** Ghi nhận một lần thử thành công — không tính vào ngưỡng, nhưng vẫn để lại vết. */
    public void recordSuccess(String clientId, boolean accepted) {
        attempts.record(keyOf(clientId), accepted ? OUTCOME_ACCEPTED_OK : OUTCOME_PREVIEW_OK);
    }

    /**
     * Lần thử thành công của luồng <b>mã mời dòng họ</b>.
     *
     * <p>Tách kết quả chứ không tách bộ đếm: ngưỡng vẫn chung (xem
     * {@link #OUTCOME_CLAN_PREVIEW_OK}), chỉ nhãn trong nhật ký là khác — để khi đọc
     * {@code invitation_attempt} còn phân biệt được lượt nào của luồng nào.</p>
     */
    public void recordClanSuccess(String clientId, boolean registered) {
        attempts.record(keyOf(clientId),
                registered ? OUTCOME_CLAN_REGISTER_OK : OUTCOME_CLAN_PREVIEW_OK);
    }

    /**
     * Băm định danh người gọi.
     *
     * <p>Địa chỉ IP là dữ liệu cá nhân theo Nghị định 13/2023, và câu hỏi duy nhất bảng đếm cần trả
     * lời là "có phải cùng một người gọi không" — một câu hỏi mà băm trả lời trọn vẹn.</p>
     *
     * <p>{@code null} (không lấy được IP: proxy lạ, test, lời gọi nội bộ) được gộp vào một khoá
     * chung thay vì bỏ qua phép đếm. Bỏ qua sẽ biến "giấu được IP" thành "miễn giới hạn".</p>
     */
    private static String keyOf(String clientId) {
        return InvitationCode.sha256Hex(clientId == null || clientId.isBlank()
                ? "unknown-client" : clientId.trim());
    }
}

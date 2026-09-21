package vn.giapha.membership.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Đồng hồ dời được, cho những bài kiểm về <b>cửa sổ thời gian</b>.
 *
 * <p>Dùng chung một thể hiện cho realm giả và cho {@code IdentityReclaimPolicy}: "tài khoản được
 * tạo lúc nào" và "bây giờ là lúc nào" phải nằm trên cùng một trục, nếu không thì cửa sổ mồ côi
 * đúng hay sai tuỳ vào tốc độ máy chạy test.</p>
 */
public final class MutableClock extends Clock {

    private Instant now = Instant.parse("2026-09-21T09:00:00Z");
    private final ZoneId zone;

    public MutableClock() {
        this(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    /** Đẩy đồng hồ tới trước — dựng ca "đã quá hạn". */
    public void tien(Duration amount) {
        this.now = this.now.plus(amount);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId replacement) {
        return new MutableClock(replacement);
    }
}

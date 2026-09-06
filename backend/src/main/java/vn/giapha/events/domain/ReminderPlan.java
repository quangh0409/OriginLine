package vn.giapha.events.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Chính sách "nhắc trước bao nhiêu ngày, vào lúc mấy giờ" (FR-2.2: mốc <b>7 / 3 / 1</b>).
 *
 * <p>POJO thuần, không Spring, không DB — nhờ vậy toàn bộ luật sinh lịch nhắc kiểm thử được mà
 * không cần khởi động context. Cấu hình thật (số ngày, giờ bắn) nạp ở tầng application.</p>
 *
 * <h2>Vì sao giờ bắn là một phần của chính sách</h2>
 * Nhắc giỗ là việc của người trong họ chứ không phải của máy chủ: một thông báo rơi vào 2 giờ sáng
 * sẽ bị vuốt đi cùng đống thông báo rác. Mốc mặc định {@code 07:00} giờ Việt Nam ({@code GMT+7}) là
 * lúc người ta mở điện thoại lần đầu trong ngày. Múi giờ phải truyền vào tường minh — dựa vào múi
 * giờ mặc định của JVM là cách chắc chắn nhất để môi trường chạy khác môi trường phát triển.
 */
public final class ReminderPlan {

    /** Mốc nhắc mặc định theo BA v2 / FR-2.2. */
    public static final List<Integer> DEFAULT_OFFSET_DAYS = List.of(7, 3, 1);

    /** Giờ bắn mặc định trong ngày, theo múi giờ nghiệp vụ. */
    public static final LocalTime DEFAULT_FIRE_TIME = LocalTime.of(7, 0);

    private final List<Integer> offsetDays;
    private final LocalTime fireTime;
    private final ZoneId zone;

    public ReminderPlan(Collection<Integer> offsetDays, LocalTime fireTime, ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone khong duoc null");
        this.fireTime = fireTime == null ? DEFAULT_FIRE_TIME : fireTime;
        // TreeSet: bỏ trùng và sắp tăng dần. Cấu hình lặp "7,7,3" không được sinh hai job trùng
        // khoá rồi để cơ sở dữ liệu từ chối — đây là chỗ dọn, không phải chỗ để lộ ra ngoài.
        TreeSet<Integer> normalized = new TreeSet<>();
        Collection<Integer> source = offsetDays == null || offsetDays.isEmpty()
                ? DEFAULT_OFFSET_DAYS : offsetDays;
        for (Integer offset : source) {
            if (offset != null && offset >= 0) {
                normalized.add(offset);
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(DEFAULT_OFFSET_DAYS);
        }
        this.offsetDays = List.copyOf(normalized);
    }

    public static ReminderPlan defaultPlan(ZoneId zone) {
        return new ReminderPlan(DEFAULT_OFFSET_DAYS, DEFAULT_FIRE_TIME, zone);
    }

    public List<Integer> offsetDays() {
        return offsetDays;
    }

    public LocalTime fireTime() {
        return fireTime;
    }

    public ZoneId zone() {
        return zone;
    }

    /**
     * Các mốc nhắc cho một lần xảy ra, <b>chỉ giữ mốc còn ở tương lai</b>.
     *
     * <p>Bỏ mốc đã qua là có chủ ý: thêm một sự kiện vào hai ngày trước ngày giỗ mà vẫn sinh cả mốc
     * D-7 lẫn D-3 thì người dùng nhận ba thông báo cùng lúc, nội dung mâu thuẫn nhau ("còn 7 ngày",
     * "còn 3 ngày", "còn 1 ngày"). Mốc D-1 vẫn kịp, và đó là mốc quan trọng nhất.</p>
     *
     * @param occurrence lần xảy ra đã quy đổi xong ngày dương
     * @param now        thời điểm hiện tại
     */
    public List<PlannedReminder> planFor(EventOccurrence occurrence, Instant now) {
        Objects.requireNonNull(occurrence, "occurrence khong duoc null");
        Objects.requireNonNull(now, "now khong duoc null");
        List<PlannedReminder> planned = new ArrayList<>(offsetDays.size());
        for (int offset : offsetDays) {
            Instant fireAt = occurrence.dueSolarDate().minusDays(offset).atTime(fireTime).atZone(zone).toInstant();
            if (!fireAt.isBefore(now)) {
                planned.add(new PlannedReminder(offset, fireAt));
            }
        }
        return List.copyOf(planned);
    }

    /** Một mốc nhắc đã tính xong thời điểm bắn. */
    public record PlannedReminder(int offsetDays, Instant fireAt) {
    }
}

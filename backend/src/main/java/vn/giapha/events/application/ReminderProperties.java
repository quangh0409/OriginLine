package vn.giapha.events.application;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import vn.giapha.config.SchedulerConfig;
import vn.giapha.events.domain.ReminderPlan;

/**
 * Cấu hình lịch nhắc giỗ, tiền tố {@code giapha.reminders}.
 *
 * <p>Mặc định ngay trong lớp này chứ không nằm ở {@code application.yml}: hệ thống phải chạy đúng
 * FR-2.2 (mốc 7/3/1) kể cả khi ai đó dựng môi trường mới và quên chép cấu hình. Tệp YAML chỉ để
 * <b>ghi đè</b>.</p>
 */
@Component
@ConfigurationProperties(prefix = "giapha.reminders")
public class ReminderProperties {

    /** Các mốc nhắc trước, tính bằng ngày (FR-2.2). */
    private List<Integer> offsets = ReminderPlan.DEFAULT_OFFSET_DAYS;

    /** Giờ bắn thông báo trong ngày, theo múi giờ nghiệp vụ GMT+7. */
    private int fireHour = ReminderPlan.DEFAULT_FIRE_TIME.getHour();

    /** Số lượng năm âm lịch sinh trước. 2 = năm nay và năm tới, đủ để vượt qua giao thừa. */
    private int horizonYears = 2;

    /** Kích thước lô khi quét bảng {@code event}. */
    private int batchSize = 500;

    /** Số job lấy ra mỗi lần đẩy lên RabbitMQ. */
    private int dispatchBatchSize = 200;

    /** Bật/tắt toàn bộ job định kỳ — dùng cho môi trường test và cho instance chỉ phục vụ web. */
    private boolean schedulerEnabled = true;

    public ZoneId zone() {
        return ZoneId.of(SchedulerConfig.BUSINESS_TIMEZONE);
    }

    public ReminderPlan toPlan() {
        return new ReminderPlan(offsets, LocalTime.of(Math.floorMod(fireHour, 24), 0), zone());
    }

    public List<Integer> getOffsets() {
        return offsets;
    }

    public void setOffsets(List<Integer> offsets) {
        this.offsets = offsets;
    }

    public int getFireHour() {
        return fireHour;
    }

    public void setFireHour(int fireHour) {
        this.fireHour = fireHour;
    }

    public int getHorizonYears() {
        return Math.max(1, horizonYears);
    }

    public void setHorizonYears(int horizonYears) {
        this.horizonYears = horizonYears;
    }

    public int getBatchSize() {
        return Math.max(1, batchSize);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getDispatchBatchSize() {
        return Math.max(1, dispatchBatchSize);
    }

    public void setDispatchBatchSize(int dispatchBatchSize) {
        this.dispatchBatchSize = dispatchBatchSize;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }
}

package vn.giapha.events.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.LunarDate;

/**
 * Luật "nhắc trước 7 / 3 / 1 ngày, lúc 07:00 giờ Việt Nam" (FR-2.2).
 *
 * <p>{@link ReminderPlan} là POJO thuần nên toàn bộ luật này kiểm được mà không cần Spring, không
 * cần cơ sở dữ liệu — đó chính là lý do nó nằm ở tầng domain.</p>
 */
class ReminderPlanTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Giỗ vào 20/10/2026, ngày âm kèm theo chỉ để bản ghi đầy đủ. */
    private static final EventOccurrence GIO = new EventOccurrence(
            LocalDate.of(2026, 10, 20), 2026, LunarDate.of(2026, 9, 10), OccurrenceAdjustment.EXACT);

    /** Một thời điểm chắc chắn nằm trước mọi mốc nhắc của {@link #GIO}. */
    private static final Instant TRUOC_MOI_MOC =
            ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, VN).toInstant();

    @Test
    @DisplayName("Mac dinh dung ba moc 7 / 3 / 1 ngay theo FR-2.2")
    void macDinhBaMoc() {
        assertThat(ReminderPlan.defaultPlan(VN).offsetDays()).containsExactly(1, 3, 7);
    }

    @Test
    @DisplayName("Sinh dung ba lan nhac, moi lan vao 07:00 gio Viet Nam cua ngay D-n")
    void banVaoBayGioSangGioVietNam() {
        List<ReminderPlan.PlannedReminder> planned =
                ReminderPlan.defaultPlan(VN).planFor(GIO, TRUOC_MOI_MOC);

        assertThat(planned).hasSize(3);
        assertThat(planned).extracting(ReminderPlan.PlannedReminder::offsetDays)
                .containsExactly(1, 3, 7);
        assertThat(planned).extracting(ReminderPlan.PlannedReminder::fireAt)
                .containsExactly(
                        bayGioSang(LocalDate.of(2026, 10, 19)),
                        bayGioSang(LocalDate.of(2026, 10, 17)),
                        bayGioSang(LocalDate.of(2026, 10, 13)));
    }

    @Test
    @DisplayName("Mui gio phai la GMT+7, khong phai mui gio cua may chu")
    void muiGioLaGmt7() {
        Instant fireAt = ReminderPlan.defaultPlan(VN).planFor(GIO, TRUOC_MOI_MOC).get(0).fireAt();

        // 07:00 GMT+7 = 00:00 UTC. Chạy trên máy chủ UTC mà quên truyền múi giờ thì mốc này thành
        // 07:00 UTC = 14:00 giờ Việt Nam - thông báo tới lúc người ta đang ngủ trưa.
        assertThat(fireAt).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    @DisplayName("Moc da troi qua bi bo, khong ban don ba tin mau thuan nhau cung luc")
    void boMocDaQua() {
        // Thêm sự kiện vào 18/10, tức sau cả mốc D-7 lẫn D-3.
        Instant now = ZonedDateTime.of(2026, 10, 18, 9, 0, 0, 0, VN).toInstant();

        List<ReminderPlan.PlannedReminder> planned = ReminderPlan.defaultPlan(VN).planFor(GIO, now);

        assertThat(planned).extracting(ReminderPlan.PlannedReminder::offsetDays).containsExactly(1);
    }

    @Test
    @DisplayName("Ngay gio da qua thi khong con moc nao")
    void gioDaQuaThiKhongConMoc() {
        Instant now = ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, VN).toInstant();

        assertThat(ReminderPlan.defaultPlan(VN).planFor(GIO, now)).isEmpty();
    }

    @Test
    @DisplayName("Cau hinh lap va so am duoc don sach truoc khi cham vao co so du lieu")
    void cauHinhLapVaSoAmDuocDon() {
        ReminderPlan plan = new ReminderPlan(List.of(7, 7, 3, -1, 0), LocalTime.of(8, 0), VN);

        // Trùng bị khử (nếu không, hai job cùng khoá sẽ đâm vào chỉ mục duy nhất và sinh log nhiễu),
        // số âm bị loại, thứ tự tăng dần, và 0 (nhắc đúng ngày) là hợp lệ.
        assertThat(plan.offsetDays()).containsExactly(0, 3, 7);
    }

    @Test
    @DisplayName("Cau hinh rong roi ve mac dinh 7/3/1 chu khong tat lich nhac")
    void cauHinhRongRoiVeMacDinh() {
        assertThat(new ReminderPlan(List.of(), null, VN).offsetDays()).containsExactly(1, 3, 7);
        assertThat(new ReminderPlan(null, null, VN).offsetDays()).containsExactly(1, 3, 7);
        assertThat(new ReminderPlan(List.of(-5), null, VN).offsetDays()).containsExactly(1, 3, 7);
    }

    private static Instant bayGioSang(LocalDate date) {
        return date.atTime(7, 0).atZone(VN).toInstant();
    }
}

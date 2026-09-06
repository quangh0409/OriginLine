package vn.giapha.events.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.domain.Event;
import vn.giapha.events.domain.EventOccurrence;
import vn.giapha.events.domain.EventSubject;
import vn.giapha.events.domain.OccurrenceAdjustment;
import vn.giapha.events.domain.ReminderJob;
import vn.giapha.notification.application.LocalizedText;
import vn.giapha.shared.vo.LunarDate;

/**
 * Nội dung một lượt nhắc giỗ.
 *
 * <p>Ba ràng buộc: <b>song ngữ</b> (kiều bào đời hai đọc tiếng Anh), <b>cả hai lịch trong một câu</b>
 * (người lớn tuổi nhớ ngày âm), và <b>tuyệt đối không có dữ liệu Tầng 3</b> — thông báo hiện trên
 * màn hình khoá và đi qua hạ tầng bên thứ ba (Nghị định 13/2023).</p>
 */
class ReminderMessageFactoryTest {

    private final LunarCalendarService calendar = new LunarCalendarService();
    private final LunarProbe probe = new LunarProbe(calendar);
    private final ReminderMessageFactory factory =
            new ReminderMessageFactory(new OccurrenceResolver(calendar));

    private static ReminderJob jobFor(Event event, LocalDate ngayGio, int offsetDays) {
        EventOccurrence occurrence = new EventOccurrence(ngayGio, ngayGio.getYear(), null,
                OccurrenceAdjustment.EXACT);
        return ReminderJob.pending(UUID.randomUUID(), event.id(), occurrence, offsetDays,
                Instant.now());
    }

    @Test
    @DisplayName("Song ngu VI/EN, dem nguoc dung tung moc 7 / 3 / 1")
    void songNguVaDemNguoc() {
        Event gio = EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10));
        LocalDate ngayGio = probe.solarOf(2026, 3, 10, false);

        LocalizedText bay = factory.title(gio, null, jobFor(gio, ngayGio, 7));
        LocalizedText ba = factory.title(gio, null, jobFor(gio, ngayGio, 3));
        LocalizedText mot = factory.title(gio, null, jobFor(gio, ngayGio, 1));

        assertThat(bay.vi()).startsWith("Con 7 ngay toi");
        assertThat(bay.en()).startsWith("7 days until");
        assertThat(ba.vi()).startsWith("Con 3 ngay toi");
        assertThat(mot.vi()).startsWith("Ngay mai la");
        assertThat(mot.en()).startsWith("Tomorrow is");
    }

    @Test
    @DisplayName("Noi dung mang ca ngay duong lan ngay am, cong dia diem")
    void mangCaHaiLich() {
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        LocalDate ngayGio = probe.solarOf(2026, 3, 10, false);

        LocalizedText body = factory.body(gio, null, jobFor(gio, ngayGio, 3));

        assertThat(body.vi()).contains("duong lich").contains("am lich")
                .contains("Dia diem: Tu duong ho Nguyen");
        assertThat(body.en()).contains("lunar").contains("Venue:");
    }

    @Test
    @DisplayName("Ngay bi lui vi thang thieu thi PHAI noi ro ly do trong noi dung")
    void noiRoLyDoKhiNgayBiLui() {
        int namThangThieu = probe.yearWithShortMonth(12);
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 12, 30), null);
        EventSubject subject = EventFixtures.deceased(personId, LunarDate.of(1985, 12, 30), null);
        LocalDate ngayGioThucTe = probe.solarOf(namThangThieu, 12, 29, false);

        LocalizedText body = factory.body(gio, subject, jobFor(gio, ngayGioThucTe, 1));

        // Đưa ra một ngày khác với sổ mà không giải thích là cách nhanh nhất để cả họ mất tin.
        assertThat(body.vi()).contains("Nam nay khong co ngay 30 thang 12");
        assertThat(body.en()).contains("does not occur this year");
    }

    @Test
    @DisplayName("Ngay khop voi so thi khong bia them cau giai thich nao")
    void khongBiaCauGiaiThichKhiKhongLech() {
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        EventSubject subject = EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null);
        LocalDate ngayGio = probe.solarOf(2026, 3, 10, false);

        LocalizedText body = factory.body(gio, subject, jobFor(gio, ngayGio, 1));

        assertThat(body.vi()).doesNotContain("Nam nay khong co");
    }

    @Test
    @DisplayName("Tieu de uu tien title do nguoi trong ho tu dat")
    void uuTienTenDoNguoiTrongHoDat() {
        UUID personId = UUID.randomUUID();
        Event gio = EventFixtures.gio(UUID.randomUUID(), personId, LunarDate.of(1985, 3, 10), null);
        EventSubject subject = EventFixtures.deceased(personId, LunarDate.of(1985, 3, 10), null);

        LocalizedText title = factory.title(gio, subject, jobFor(gio, LocalDate.of(2026, 4, 26), 3));

        assertThat(title.vi()).contains("Gio cu Nguyen Van Duc");
    }

    @Test
    @DisplayName("deepLink tro toi trang chi tiet su kien - dung ca cho in-app lan Web Push")
    void deepLinkTroToiTrangSuKien() {
        Event gio = EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10));

        assertThat(factory.deepLink(gio)).isEqualTo("/events/" + gio.id());
    }

    @Test
    @DisplayName("Khong co nhan khau chu the thi van dung duoc noi dung, khong no")
    void khongCoNhanKhauVanChay() {
        Event hopHo = EventFixtures.solar(UUID.randomUUID(), LocalDate.of(2026, 6, 1), true);

        assertThat(factory.title(hopHo, null, jobFor(hopHo, LocalDate.of(2026, 6, 1), 7)).vi())
                .isNotBlank();
        assertThat(factory.body(hopHo, null, jobFor(hopHo, LocalDate.of(2026, 6, 1), 7)).vi())
                .contains("01/06/2026");
    }

    @Test
    @DisplayName("Ngay duong duoc dinh dang dd/MM/yyyy - dinh dang nguoi Viet doc quen")
    void dinhDangNgayKieuViet() {
        Event gio = EventFixtures.gioTo(UUID.randomUUID(), LunarDate.of(1700, 3, 10));

        assertThat(factory.body(gio, null, jobFor(gio, LocalDate.of(2026, 10, 20), 1)).vi())
                .contains("20/10/2026");
    }
}

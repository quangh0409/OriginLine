package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.events.application.GenerateRemindersService;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.UpdatePersonCommands;
import vn.giapha.genealogy.application.UpdatePersonService;
import vn.giapha.genealogy.application.VisibleTier;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>Hệ quả dây chuyền của việc báo mất</b>, chạy trên hạ tầng thật.
 *
 * <p>Đánh dấu một người là đã mất không phải là lật một cờ boolean. Nó kéo theo hai chuyện mà
 * người sửa {@code UpdatePersonService} về sau rất dễ phá mà không biết:</p>
 * <ol>
 *   <li><b>{@code person.death_lunar} trở thành nguồn chân lý của ngày giỗ.</b> Context
 *       {@code events} đọc thẳng cột này qua {@code EventSubjectJdbcAdapter}; nếu ngày mất không
 *       xuống được tới cột ấy (hoặc xuống sai bố cục jsonb) thì lịch nhắc giỗ 7/3/1 ngày <b>im
 *       lặng không sinh ra</b> — không exception, không log lỗi, chỉ là một cái giỗ bị bỏ quên.</li>
 *   <li><b>Hồ sơ chuyển sang tầng hiển thị {@code PUBLIC}</b> (BA v2 §10): người đã khuất là dữ
 *       liệu công khai, kể cả với Khách vãng lai. Đây chính là cái giá của Phương án B — suy diễn
 *       "có ngày mất tức là đã mất" cũng đồng thời mở hồ sơ ra cho cả thiên hạ, nên giao diện phải
 *       hỏi lại người dùng trước khi gửi.</li>
 * </ol>
 *
 * <p>Cả hai chỉ quan sát được với CSDL thật: một cái đi qua cột {@code jsonb} và bộ quy đổi âm
 * lịch, cái kia đi qua chuỗi {@code keycloak_sub → app_user → branch_assignment → ltree}.</p>
 */
@DisplayName("Hệ quả dây chuyền khi báo mất (giỗ + phân tầng riêng tư)")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class DeathPatchConsequencesIT extends AbstractIntegrationTest {

    /** Múi giờ nghiệp vụ: mọi mốc giỗ tính theo GMT+7, không theo giờ máy chủ. */
    private static final ZoneId GMT7 = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Ngày giỗ âm lịch của cụ: 15 tháng 8 (rằm Trung thu) — giữa năm, xa mọi mốc giao thừa. */
    private static final int GIO_THANG = 8;
    private static final int GIO_NGAY = 15;

    @Autowired
    private UpdatePersonService updatePerson;

    @Autowired
    private PersonQueryService personQuery;

    @Autowired
    private GenerateRemindersService generateReminders;

    @Autowired
    private LunarCalendarService lunarCalendar;

    /** Cổng mà context {@code events} dùng để đọc ngày mất — nơi nguồn chân lý của giỗ lộ ra. */
    @Autowired
    private vn.giapha.events.domain.port.EventSubjectPort eventSubjects;

    private UUID chiGiap;
    private UUID cuOng;

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        cuOng = seed(PersonFixtures.living("Nguyễn Văn Cả")
                .birthYear(1940).branch(chiGiap).generation(3));
        authenticateAs("sub-admin", "ADMIN");
    }

    /** Đúng cú {@code PATCH} gây lỗi mất dữ liệu: có {@code death}, không có {@code isAlive}. */
    private PersonView baoMatBangCachChiGuiNgayMat(int namAm) {
        return updatePerson.update(UpdatePersonCommands.cho(cuOng)
                .ngayMat(LifeDate.of(lunarCalendar.toSolar(LunarDate.of(namAm, GIO_THANG, GIO_NGAY)),
                        LunarDate.of(namAm, GIO_THANG, GIO_NGAY), DatePrecision.DAY))
                .ghiChu("Theo bia mộ tại từ đường chi Giáp")
                .build());
    }

    // -------------------------------------------------------------------------------------
    // Hệ quả 1 — death_lunar là nguồn chân lý của giỗ
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Ngày mất suy diễn xuống tới cột death_lunar đúng bố cục jsonb mà events đọc")
    void ngayMatXuongToiCotDeathLunar() {
        baoMatBangCachChiGuiNgayMat(2024);

        assertThat(jdbc.queryForObject("SELECT is_alive FROM person WHERE id = ?", Boolean.class, cuOng))
                .as("Phuong an B: co ngay mat tuc la da mat")
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT death_lunar->>'month' || '/' || (death_lunar->>'day') FROM person WHERE id = ?",
                String.class, cuOng))
                .as("ck_person_alive_vs_death doi is_alive=false, con EventSubjectJdbcAdapter doi "
                        + "dung hai khoa day/month - sai mot trong hai thi gio bien mat khong tieng dong")
                .isEqualTo(GIO_THANG + "/" + GIO_NGAY);
    }

    @Test
    @DisplayName("Giỗ sinh lịch nhắc 7/3/1 ngày theo death_lunar, KHÔNG theo ngày chép trong bảng event")
    void gioSinhLichNhacTheoDeathLunar() {
        int namAmHienTai = namAmCuaHomNay();
        baoMatBangCachChiGuiNgayMat(namAmHienTai);

        // Bản ghi sự kiện cố ý chép SAI ngày âm (20/9 thay vì 15/8) - đúng tình huống mà
        // EffectiveLunarDate sinh ra để xử lý: hồ sơ nhân khẩu thắng.
        UUID suKienGio = insertGio(cuOng, 20, 9);

        LocalDate ngayGioDung = lunarCalendar.toSolar(
                LunarDate.of(namAmHienTai, GIO_THANG, GIO_NGAY));
        LocalDate ngayGioSai = lunarCalendar.toSolar(LunarDate.of(namAmHienTai, 9, 20));
        LocalDate homNay = ngayGioDung.minusDays(30);

        generateReminders.generateFor(homNay, homNay.atStartOfDay(GMT7).toInstant());

        assertThat(offsetsCua(suKienGio, ngayGioDung))
                .as("FR-2.2: nhac truoc 7 / 3 / 1 ngay")
                .containsExactly(1, 3, 7);
        assertThat(offsetsCua(suKienGio, ngayGioSai))
                .as("ngay chep trong bang event bi bo qua - person.death_lunar la nguon chan ly (BA v2)")
                .isEmpty();
    }

    @Test
    @DisplayName("Mốc nhắc bắn đúng D-7 / D-3 / D-1 tính theo giờ Việt Nam")
    void mocNhacBanDungTruocNgayGio() {
        int namAmHienTai = namAmCuaHomNay();
        baoMatBangCachChiGuiNgayMat(namAmHienTai);
        UUID suKienGio = insertGio(cuOng, GIO_NGAY, GIO_THANG);
        LocalDate ngayGio = lunarCalendar.toSolar(LunarDate.of(namAmHienTai, GIO_THANG, GIO_NGAY));
        LocalDate homNay = ngayGio.minusDays(30);

        generateReminders.generateFor(homNay, homNay.atStartOfDay(GMT7).toInstant());

        List<Integer> lech = jdbc.queryForList(
                "SELECT (due_solar_date - (fire_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) AS d"
                        + " FROM reminder_job WHERE event_id = ? AND due_solar_date = ?"
                        + " ORDER BY offset_days", Integer.class, suKienGio, java.sql.Date.valueOf(ngayGio));
        assertThat(lech)
                .as("fire_at phai roi dung so ngay truoc gio; lech mot ngay o vung bien mui gio la "
                        + "trieu chung cua viec tinh theo UTC thay vi GMT+7")
                .containsExactly(1, 3, 7);
    }

    @Test
    @DisplayName("Báo mất làm death_lunar hiện ra ở cổng mà context events đọc")
    void deathLunarHienRaOCongCuaEvents() {
        int namAmHienTai = namAmCuaHomNay();
        baoMatBangCachChiGuiNgayMat(namAmHienTai);

        var subject = eventSubjects.findPerson(cuOng).orElseThrow();

        assertThat(subject.deathLunar())
                .as("EventSubject.deathLunar la thu EffectiveLunarDate lay lam nguon chan ly cua gio")
                .isEqualTo(LunarDate.of(namAmHienTai, GIO_THANG, GIO_NGAY));
    }

    @Test
    @DisplayName("Đính chính 'thật ra còn sống' gỡ sạch ngày mất, giỗ mất nguồn chân lý")
    void dinhChinhConSongThiGoDeathLunar() {
        int namAmHienTai = namAmCuaHomNay();
        baoMatBangCachChiGuiNgayMat(namAmHienTai);
        long phienBan = phienBanCua(cuOng);

        updatePerson.update(UpdatePersonCommands.cho(cuOng).phienBan(phienBan).conSong(true).build());

        assertThat(jdbc.queryForObject("SELECT death_lunar IS NULL AND death_solar IS NULL"
                + " FROM person WHERE id = ?", Boolean.class, cuOng))
                .as("ck_person_alive_vs_death: con song thi khong duoc con dau vet ngay mat nao")
                .isTrue();
        assertThat(eventSubjects.findPerson(cuOng).orElseThrow().deathLunar())
                .as("ho so khong con ngay mat thi cong cua events cung phai het nguon chan ly. "
                        + "LUU Y CHO W5: ban ghi `event` van giu ngay am cua chinh no, nen mot su "
                        + "kien GIO da tao truoc do van tiep tuc sinh lich nhac - viec don dep no "
                        + "thuoc context events, khong phai genealogy.")
                .isNull();
    }

    // -------------------------------------------------------------------------------------
    // Hệ quả 2 — phân tầng riêng tư (BA v2 §10)
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Trước khi báo mất, Khách vãng lai KHÔNG thấy người còn sống")
    void truocKhiBaoMatKhachKhongThayGi() {
        authenticateAsGuest();

        assertThat(personQuery.find(cuOng))
                .as("BA v2 §10: khach khong thay bat ky nguoi song nao - khong phai 'thay moi ten'")
                .isEmpty();
    }

    @Test
    @DisplayName("Sau khi suy diễn 'đã mất', hồ sơ chuyển sang PUBLIC và Khách thấy được")
    void sauKhiBaoMatThiKhachThayDuoc() {
        baoMatBangCachChiGuiNgayMat(2024);

        authenticateAsGuest();
        PersonView view = personQuery.find(cuOng).orElseThrow();

        assertThat(view.access().visibleTier())
                .as("nguoi da khuat la du lieu cong khai theo dung muc dich cua gia pha")
                .isEqualTo(VisibleTier.PUBLIC);
        assertThat(view.displayName()).isEqualTo("Nguyễn Văn Cả");
        assertThat(view.contact())
                .as("PUBLIC khong co nghia la mo khoi lien he: so dien thoai ghi trong ho so mot cu "
                        + "da mat tren thuc te la so cua nguoi than dang song")
                .isNull();
    }

    @Test
    @DisplayName("Đính chính 'thật ra còn sống' đóng lại cánh cửa riêng tư vừa mở")
    void dinhChinhConSongThiDongLaiCanhCua() {
        baoMatBangCachChiGuiNgayMat(2024);
        authenticateAsGuest();
        assertThat(personQuery.find(cuOng)).isPresent();

        authenticateAs("sub-admin", "ADMIN");
        updatePerson.update(UpdatePersonCommands.cho(cuOng)
                .phienBan(phienBanCua(cuOng)).conSong(true).build());

        authenticateAsGuest();
        assertThat(personQuery.find(cuOng))
                .as("mot cu bam nham roi sua lai phai keo duoc ho so ra khoi tam mat Khach")
                .isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích
    // -------------------------------------------------------------------------------------

    /** Năm âm lịch của hôm nay — mốc để mọi ca test bám vào dải quy đổi thật của bộ lịch. */
    private int namAmCuaHomNay() {
        return lunarCalendar.toLunar(LocalDate.now(GMT7)).year();
    }

    /** Một bản ghi giỗ cá nhân, lặp hằng năm, phạm vi chi Giáp. */
    private UUID insertGio(UUID personId, int ngayAm, int thangAm) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO event (id, person_id, event_type, title, lunar_date,"
                        + " is_lunar_based, is_recurring, target_branch_id, is_clan_level)"
                        + " VALUES (?, ?, 'GIO', ?, CAST(? AS jsonb), TRUE, TRUE, ?, FALSE)",
                id, personId, "Giỗ cụ Nguyễn Văn Cả",
                "{\"month\":" + thangAm + ",\"day\":" + ngayAm + ",\"leap\":false}", chiGiap);
        return id;
    }

    private List<Integer> offsetsCua(UUID eventId, LocalDate ngayGio) {
        return jdbc.queryForList("SELECT offset_days FROM reminder_job"
                        + " WHERE event_id = ? AND due_solar_date = ? ORDER BY offset_days",
                Integer.class, eventId, java.sql.Date.valueOf(ngayGio));
    }

    private long phienBanCua(UUID personId) {
        Long version = jdbc.queryForObject("SELECT version FROM person WHERE id = ?", Long.class,
                personId);
        return version == null ? 0L : version;
    }
}

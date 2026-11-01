package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.core.io.ClassPathResource;

/**
 * {@code V22__notification_inbox_co_dau.sql} — lời nhắc giỗ sinh trước bản vá 24/09 được chuyển
 * sang chữ có dấu, và <b>chỉ</b> chúng.
 *
 * <h2>Chạy lại chính tệp migration, không chép câu SQL vào test</h2>
 * Flyway đã chạy V22 lúc dựng container — trên một bảng rỗng, nên lần ấy không chứng minh được gì.
 * Ca test gieo dòng kiểu cũ rồi chạy lại <b>nguyên văn tệp</b> từ classpath: thứ được kiểm là
 * migration thật. Làm được vì migration được viết để chạy lại vô hại (chỉ UPDATE dòng khớp mẫu
 * cũ), và ca {@link #dongDaCoDau_khongDoi_keCaUpdatedAt()} ghim đúng tính chất đó.
 *
 * <h2>Chuỗi đích viết tay, khớp từng ký tự với {@code ReminderMessageFactory}</h2>
 * Cố ý không gọi factory để sinh chuỗi mong đợi: factory cần một {@code Event} và một
 * {@code OccurrenceResolver} thật, và một phép so tự sinh cả hai vế thì xanh cả khi cả hai cùng
 * sai. Đổi câu chữ ở factory thì phải đổi ở đây và ở V22 — ba chỗ, có chủ ý.
 */
@DisplayName("V22 — lời nhắc giỗ cũ trong hộp thư được chuyển sang có dấu")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class NotificationInboxCoDauMigrationIT extends AbstractIntegrationTest {

    private UUID nguoiNhan;

    @BeforeEach
    void setUp() {
        nguoiNhan = seed(PersonFixtures.living("Nguyễn Văn Nhận"));
    }

    @Test
    @DisplayName("tiêu đề: ba dạng đếm ngược cũ đổi sang có dấu, tên sự kiện giữ nguyên văn")
    void tieuDe_baDangDemNguoc() throws IOException {
        UUID bay = hopThu("Con 7 ngay toi Gio cu Nguyen Van Duc", "Ngay 16/09/2026 duong lich.");
        UUID mai = hopThu("Ngay mai la Giỗ cụ Tổ", "Ngay 16/09/2026 duong lich.");
        UUID nay = hopThu("Hom nay la Chạp mả", "Ngay 16/09/2026 duong lich.");

        chayLaiV22();

        // "Gio cu Nguyen Van Duc" la chu NGUOI viet (event.title) — khong duoc "sua dau" ho.
        assertThat(tieuDe(bay)).isEqualTo("Còn 7 ngày tới Gio cu Nguyen Van Duc");
        assertThat(tieuDe(mai)).isEqualTo("Ngày mai là Giỗ cụ Tổ");
        assertThat(tieuDe(nay)).isEqualTo("Hôm nay là Chạp mả");
    }

    @Test
    @DisplayName("thân: câu song lịch, địa điểm và câu lệch ngày (có tháng nhuận) đổi đúng từng ký tự")
    void than_dayDuMoiPhan() throws IOException {
        UUID id = hopThu("Con 3 ngay toi Giỗ cụ",
                "Ngay 16/09/2026 duong lich (ngay 6 thang 8 am lich)."
                        + " Dia diem: Nha tho ho, xom 3. Am lich."
                        + " Nam nay khong co ngay 30 thang 4 nhuan nen gio duoc tinh vao"
                        + " ngay 29 thang 4 nhuan am lich.");

        chayLaiV22();

        assertThat(than(id)).isEqualTo(
                "Ngày 16/09/2026 dương lịch (ngày 6 tháng 8 âm lịch)."
                        // Dia diem la chu nguoi viet — ke ca "Am lich." o trong no cung giu nguyen.
                        + " Địa điểm: Nha tho ho, xom 3. Am lich."
                        + " Năm nay không có ngày 30 tháng 4 nhuận nên giỗ được tính vào"
                        + " ngày 29 tháng 4 nhuận âm lịch.");
    }

    @Test
    @DisplayName("thân: tháng nhuận trong ngoặc âm lịch, và câu lệch ngày chỉ nhuận một vế")
    void than_nhuanTungVe() throws IOException {
        UUID ngoac = hopThu("Ngay mai la Giỗ",
                "Ngay 01/06/2028 duong lich (ngay 8 thang 5 nhuan am lich).");
        UUID veTruoc = hopThu("Ngay mai la Giỗ",
                "Ngay 01/06/2028 duong lich (ngay 29 thang 5 am lich)."
                        + " Nam nay khong co ngay 30 thang 5 nhuan nen gio duoc tinh vao"
                        + " ngay 29 thang 5 am lich.");

        chayLaiV22();

        assertThat(than(ngoac)).isEqualTo("Ngày 01/06/2028 dương lịch (ngày 8 tháng 5 nhuận âm lịch).");
        assertThat(than(veTruoc)).isEqualTo(
                "Ngày 01/06/2028 dương lịch (ngày 29 tháng 5 âm lịch)."
                        + " Năm nay không có ngày 30 tháng 5 nhuận nên giỗ được tính vào"
                        + " ngày 29 tháng 5 âm lịch.");
    }

    @Test
    @DisplayName("dòng đã có dấu không đổi, kể cả updated_at; chạy lại lần hai cũng không đổi")
    void dongDaCoDau_khongDoi_keCaUpdatedAt() throws IOException {
        String tieuDe = "Còn 7 ngày tới Giỗ cụ Tổ";
        String noiDung = "Ngày 16/09/2026 dương lịch (ngày 6 tháng 8 âm lịch). Địa điểm: Từ đường.";
        UUID moi = hopThu(tieuDe, noiDung);
        UUID cu = hopThu("Con 7 ngay toi Giỗ cụ Tổ", "Ngay 16/09/2026 duong lich (ngay 6 thang 8 am lich).");
        Timestamp truoc = capNhatLuc(moi);

        chayLaiV22();
        chayLaiV22();

        assertThat(tieuDe(moi)).isEqualTo(tieuDe);
        assertThat(than(moi)).isEqualTo(noiDung);
        assertThat(capNhatLuc(moi)).as("dong khong khop mau cu thi khong bi UPDATE").isEqualTo(truoc);
        assertThat(tieuDe(cu)).isEqualTo("Còn 7 ngày tới Giỗ cụ Tổ");
        assertThat(than(cu)).isEqualTo("Ngày 16/09/2026 dương lịch (ngày 6 tháng 8 âm lịch).");
    }

    @Test
    @DisplayName("chỉ đụng hộp thư loại REMINDER — thông báo hệ thống và bản tiếng Anh giữ nguyên")
    void chiDungReminder() throws IOException {
        UUID heThong = hopThu("Con 7 ngay toi han", "Ngay 16/09/2026 duong lich.", "SYSTEM");
        UUID tiengAnh = hopThu("7 days until Gio To", "On 16/09/2026 (lunar 6/8).");

        chayLaiV22();

        assertThat(tieuDe(heThong)).isEqualTo("Con 7 ngay toi han");
        assertThat(than(heThong)).isEqualTo("Ngay 16/09/2026 duong lich.");
        assertThat(tieuDe(tiengAnh)).isEqualTo("7 days until Gio To");
        assertThat(than(tiengAnh)).isEqualTo("On 16/09/2026 (lunar 6/8).");
    }

    // -------------------------------------------------------------------------------------

    private void chayLaiV22() throws IOException {
        String sql = new ClassPathResource("db/migration/V22__notification_inbox_co_dau.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }

    private UUID hopThu(String title, String body) {
        return hopThu(title, body, "REMINDER");
    }

    private UUID hopThu(String title, String body, String category) {
        UUID id = UUID.randomUUID();
        // updated_at lui ve qua khu de phep so "khong bi UPDATE" khong trung khop vi cung mot nhip.
        jdbc.update("INSERT INTO notification_inbox (id, recipient_person_id, title, body, category,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, now() - interval '1 day')",
                id, nguoiNhan, title, body, category);
        return id;
    }

    private String tieuDe(UUID id) {
        return jdbc.queryForObject("SELECT title FROM notification_inbox WHERE id = ?", String.class, id);
    }

    private String than(UUID id) {
        return jdbc.queryForObject("SELECT body FROM notification_inbox WHERE id = ?", String.class, id);
    }

    private Timestamp capNhatLuc(UUID id) {
        return jdbc.queryForObject("SELECT updated_at FROM notification_inbox WHERE id = ?",
                Timestamp.class, id);
    }
}

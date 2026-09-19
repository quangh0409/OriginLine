package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.application.DuplicateMatch;
import vn.giapha.genealogy.application.DuplicatePersonChecker;
import vn.giapha.genealogy.application.DuplicateProbe;
import vn.giapha.genealogy.application.DuplicateReport;
import vn.giapha.genealogy.application.DuplicateSignal;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * Phép dò trùng nhân khẩu chạy trên <b>PostgreSQL thật</b>.
 *
 * <h2>Vì sao phần này bắt buộc phải có CSDL thật</h2>
 * Trái tim của cửa lọc là hàm {@code vn_unaccent} và cột generated
 * {@code person_name.name_unaccented} (V6). Không có bản cài lại nào trong Java được phép tồn tại
 * song song — hai bản bỏ dấu khác nhau sẽ lệch đúng ở những ký tự hiếm (đ/Đ, nguyên âm có hai dấu)
 * và sinh ra cảnh báo lúc có lúc không mà không ai truy ra được vì sao. Test unit dùng một bản bỏ
 * dấu giả nên nó chứng minh được logic chấm điểm chứ <b>không</b> chứng minh được phép bỏ dấu.
 *
 * <p>Phần thứ hai chỉ CSDL thật mới kiểm được: ngày giỗ nằm trong cột {@code death_lunar} kiểu
 * {@code jsonb}, và adapter phải bóc đúng {@code month}/{@code day}/{@code leap} ra khỏi đó.</p>
 *
 * <h2>Cân cả hai vế</h2>
 * Chứng minh bộ dò biết kêu thì dễ. Bộ test này ghim cả vế ngược lại — <b>trùng tên hợp lệ thì
 * phải im</b> — vì trùng tên trong dòng họ Việt là chuyện bình thường, và một bộ dò kêu quá tay sẽ
 * dạy người dùng bấm "vẫn ghi" theo phản xạ.
 */
@DisplayName("Dò trùng nhân khẩu trên Postgres thật (vn_unaccent + death_lunar jsonb)")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class DuplicatePersonDetectionIT extends AbstractIntegrationTest {

    @Autowired
    private DuplicatePersonChecker duplicates;

    private UUID chiGiap;
    private UUID chiAt;

    @BeforeEach
    void dungChi() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
    }

    // -------------------------------------------------------------------------------------
    // Vế "phải kêu"
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Gõ không dấu \"Nguyen Van Tuan\" khớp được \"Nguyễn Văn Tuân\" đã có trong gia phả")
    void goKhongDauVanKhopTenCoDau() {
        UUID cu = seedPerson("Nguyễn Văn Tuân", Gender.MALE, 5, chiGiap, 1920, null, null);

        List<DuplicateMatch> matches = duplicates.check(probe("Nguyen Van Tuan", 5, chiGiap, 1920));

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.personId()).isEqualTo(cu);
            assertThat(match.signals())
                    .as("khop sau khi bo dau, khong phai khop nguyen van")
                    .contains(DuplicateSignal.TEN_TRUNG_KHONG_DAU, DuplicateSignal.NAM_SINH_KHOP,
                            DuplicateSignal.CUNG_CHI);
            assertThat(match.matchedName()).isEqualTo("Nguyễn Văn Tuân");
        });
    }

    @Test
    @DisplayName("Chữ đ có gạch ngang cũng phải bỏ dấu đúng: \"Nguyen Dinh Duc\" khớp \"Nguyễn Đình Đức\"")
    void chuDGachNgangCungBoDauDung() {
        seedPerson("Nguyễn Đình Đức", Gender.MALE, 4, chiGiap, 1901, null, null);

        assertThat(duplicates.check(probe("Nguyen Dinh Duc", 4, chiGiap, 1901))).hasSize(1);
    }

    @Test
    @DisplayName("Ngày giỗ đọc từ cột death_lunar jsonb: trùng ngày trùng tháng thì kêu, dù khác năm mất")
    void gioDocTuJsonbVaTrungKhit() {
        seedPerson("Nguyễn Văn Cả", Gender.MALE, 3, chiGiap, null, 1975, new LunarDate(1975, 5, 10, false));

        DuplicateProbe probe = new DuplicateProbe(null,
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Cả", true)),
                Gender.MALE, 3, chiAt, null,
                LifeDate.of(null, new LunarDate(1974, 5, 10, false), DatePrecision.DAY), null, null);

        assertThat(duplicates.check(probe)).singleElement().satisfies(match -> {
            assertThat(match.signals()).contains(DuplicateSignal.GIO_TRUNG_KHIT);
            assertThat(match.signals()).doesNotContain(DuplicateSignal.CUNG_CHI);
            // Cau giai thich la NHAN tin hieu, khong mang gia tri truong: ngay gio cua ho so
            // trong pha khong duoc in ra, vi ho so ay co the la nguoi con song o mot chi khac.
            assertThat(match.hint()).contains("trùng ngày giỗ").doesNotContain("10/5");
        });
    }

    @Test
    @DisplayName("Khớp chéo lớp tên: tên húy đã lưu bị đụng bởi tên thường gọi đang nhập")
    void khopCheoLopTenTrenCsdlThat() {
        UUID cu = seedPerson("Nguyễn Văn Cả", Gender.MALE, 4, chiGiap, 1901, null, null);
        insertName(cu, "HUY", "Nguyễn Văn Tuân");

        assertThat(duplicates.check(probe("Nguyễn Văn Tuân", 4, chiGiap, 1901)))
                .singleElement()
                .extracting(DuplicateMatch::personId)
                .isEqualTo(cu);
    }

    // -------------------------------------------------------------------------------------
    // Vế "phải im" — quan trọng không kém
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Cùng tên, cùng chi, nhưng khác đời — chuyện bình thường của dòng họ, phải im")
    void trungTenKhacDoiThiIm() {
        seedPerson("Nguyễn Văn Tuân", Gender.MALE, 3, chiGiap, 1890, null, null);

        assertThat(duplicates.check(probe("Nguyễn Văn Tuân", 5, chiGiap, 1890)))
                .as("hai nguoi khac doi thi khong the la mot nguoi, du trung ten va trung nam sinh")
                .isEmpty();
    }

    @Test
    @DisplayName("Ba người cùng tên ở ba đời khác nhau — thêm người thứ tư ở đời khác vẫn không bị hỏi")
    void nhieuNguoiCungTenKhacDoiVanIm() {
        seedPerson("Nguyễn Văn Tuân", Gender.MALE, 2, chiGiap, 1850, null, null);
        seedPerson("Nguyễn Văn Tuân", Gender.MALE, 4, chiGiap, 1910, null, null);
        seedPerson("Nguyễn Văn Tuân", Gender.MALE, 6, chiAt, 1970, null, null);

        assertThat(duplicates.check(probe("Nguyen Van Tuan", 7, chiGiap, 1998))).isEmpty();
    }

    @Test
    @DisplayName("Trùng tên, cùng đời, nhưng không có một mẩu bằng chứng ngày tháng nào thì im")
    void khongCoNgayThangThiIm() {
        seedPerson("Nguyễn Văn Tuân", Gender.MALE, 5, chiGiap, null, null, null);

        assertThat(duplicates.check(probe("Nguyễn Văn Tuân", 5, chiGiap, null))).isEmpty();
    }

    @Test
    @DisplayName("Nhân khẩu đã xoá mềm không được đưa vào diện nghi trùng")
    void xoaMemThiKhongPhaiUngVien() {
        UUID cu = seedPerson("Nguyễn Văn Tuân", Gender.MALE, 5, chiGiap, 1920, null, null);
        jdbc.update("UPDATE person SET is_deleted = TRUE, deleted_at = now() WHERE id = ?", cu);

        assertThat(duplicates.check(probe("Nguyễn Văn Tuân", 5, chiGiap, 1920))).isEmpty();
    }

    // -------------------------------------------------------------------------------------
    // Gọi theo lô — đường ống nhập liệu hàng loạt dùng lại đúng bộ dò này
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Một lô ba dòng: báo cáo đúng thứ tự, và bắt được cả bản trùng nằm ngay trong lô")
    void loBaDongVaTuSoiTrongLo() {
        seedPerson("Nguyễn Văn Tuân", Gender.MALE, 5, chiGiap, 1920, null, null);

        List<DuplicateReport> reports = duplicates.check(List.of(
                new DuplicateProbe("dong-1", names("Nguyen Van Tuan"), Gender.MALE, 5, chiGiap,
                        birth(1920), null, null, null),
                new DuplicateProbe("dong-2", names("Nguyễn Thị Lành"), Gender.FEMALE, 5, chiGiap,
                        birth(1925), null, null, null),
                new DuplicateProbe("dong-3", names("Nguyễn Thị Lành"), Gender.FEMALE, 5, chiGiap,
                        birth(1925), null, null, null)));

        assertThat(reports).extracting(DuplicateReport::ref)
                .containsExactly("dong-1", "dong-2", "dong-3");
        assertThat(reports.get(0).matches()).singleElement()
                .extracting(DuplicateMatch::trongCungLo).isEqualTo(false);
        assertThat(reports.get(1).coNghiNgo())
                .as("dong dau tien cua mot cap trung trong lo thi chua co gi de doi chieu")
                .isFalse();
        assertThat(reports.get(2).matches()).singleElement().satisfies(match -> {
            assertThat(match.trongCungLo()).isTrue();
            assertThat(match.ref()).isEqualTo("dong-2");
        });
    }

    // -------------------------------------------------------------------------------------
    // Gieo dữ liệu
    // -------------------------------------------------------------------------------------

    /**
     * Gieo thẳng bằng SQL để kiểm soát được ngày giỗ âm lịch — thứ mà bộ dựng dùng chung
     * {@code PersonFixtures} cố định sẵn cho mọi nhân khẩu đã khuất.
     */
    private UUID seedPerson(String fullName, Gender gender, Integer generation, UUID branchId,
                            Integer birthYear, Integer deathYear, LunarDate gio) {
        UUID id = UUID.randomUUID();
        boolean alive = deathYear == null && gio == null;
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, death_solar,"
                        + " death_lunar, is_alive, native_place, primary_branch_id, attributes)"
                        + " VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, '{}'::jsonb)",
                id, gender.name(), generation,
                birthYear == null ? null : java.sql.Date.valueOf(LocalDate.of(birthYear, 3, 20)),
                deathYear == null ? null : java.sql.Date.valueOf(LocalDate.of(deathYear, 6, 15)),
                gio == null ? null : lunarJson(gio),
                alive, "Bắc Ninh", branchId);
        insertName(id, "THUONG_GOI", fullName);
        graph.createPersonNode(id, gender.name(), generation);
        return id;
    }

    private void insertName(UUID personId, String type, String fullName) {
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), personId, type, fullName, "THUONG_GOI".equals(type));
    }

    /** {@code ck_person_death_lunar} đòi có cả {@code day} lẫn {@code month}. */
    private static String lunarJson(LunarDate lunar) {
        return "{\"year\":" + lunar.year() + ",\"month\":" + lunar.month() + ",\"day\":"
                + lunar.day() + ",\"leap\":" + lunar.leapMonth() + "}";
    }

    private static List<PersonName> names(String fullName) {
        return List.of(PersonName.of(NameType.THUONG_GOI, fullName, true));
    }

    private static LifeDate birth(Integer year) {
        return year == null ? null : LifeDate.of(LocalDate.of(year, 3, 20), null, DatePrecision.DAY);
    }

    private DuplicateProbe probe(String fullName, Integer generation, UUID branchId, Integer birthYear) {
        return DuplicateProbe.of(names(fullName), Gender.MALE, generation, branchId,
                birth(birthYear), null, "Bắc Ninh");
    }
}

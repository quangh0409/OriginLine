package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.application.RowMapper;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.LunarDeathDate;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.dataimport.domain.MarriageRow;
import vn.giapha.dataimport.domain.ParsedWorkbook;
import vn.giapha.dataimport.domain.PersonRow;
import vn.giapha.dataimport.domain.RawRow;
import vn.giapha.dataimport.infrastructure.excel.XlsxWorkbookReader;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>Vòng khép kín</b>: sinh mẫu → đọc lại bằng chính {@link XlsxWorkbookReader} của đường nhập
 * liệu.
 *
 * <h2>Vì sao đây là bài kiểm quan trọng nhất của gói này</h2>
 * Một mẫu mà <b>bộ đọc của chính hệ thống từ chối</b> là lỗi tệ nhất có thể có ở đây: người dùng
 * tải tệp về từ hệ thống, điền đúng, nộp lại, và bị báo sai định dạng. Họ không có cách nào sửa,
 * vì họ đã làm đúng mọi thứ được bảo. Mọi bảo đảm khác của gói này đều vô nghĩa nếu bảo đảm này
 * không đứng vững.
 *
 * <p>Vòng này cũng là thứ khoá <b>hai</b> ràng buộc mà không bên nào một mình canh được: dấu
 * {@code *} trên tiêu đề cột bắt buộc phải sống sót qua phép chuẩn hoá của
 * {@code ImportColumn.khoa()}, và tên trang có dấu tiếng Việt phải khớp phép dò trang đã bỏ dấu
 * của bộ đọc.</p>
 */
class ImportTemplateRoundTripTest {

    private final XlsxWorkbookReader reader = new XlsxWorkbookReader();
    private final RowMapper rowMapper = new RowMapper();

    @Test
    @DisplayName("Mẫu trắng (chi chưa có ai) đọc lại được, không lỗi, không dòng rác")
    void mauTrangDocLaiDuoc() {
        byte[] file = sinh(new FakeBranchRoster().chi("Chi Giáp", "goc.chi_giap"));

        ParsedWorkbook wb = doc(file);

        assertThat(wb.personRows()).isEmpty();
        assertThat(wb.marriageRows()).isEmpty();
        // Tieu de van phai duoc nhan ra — neu khong, kiemCotBatBuoc da nem truoc khi toi day.
        assertThat(wb.personHeaders()).hasSize(ImportColumn.values().length);
    }

    @Test
    @DisplayName("Mọi cột của hai enum đều được bộ đọc nhận ra từ tiêu đề mẫu sinh")
    void moiCotDeuDuocNhanRa() {
        ParsedWorkbook wb = doc(sinh(mauDayDu()));

        for (String header : wb.personHeaders()) {
            assertThat(ImportColumn.bangTra().get(ImportColumn.khoa(header)))
                    .as("tieu de '%s' phai ghep duoc ve mot ImportColumn", header)
                    .isNotNull();
        }
        for (String header : wb.marriageHeaders()) {
            assertThat(MarriageColumn.bangTra().get(ImportColumn.khoa(header)))
                    .as("tieu de '%s' phai ghep duoc ve mot MarriageColumn", header)
                    .isNotNull();
        }
        // Du 17 + 6 cot, khong sot cot nao.
        assertThat(wb.personHeaders()).hasSize(ImportColumn.values().length);
        assertThat(wb.marriageHeaders()).hasSize(MarriageColumn.values().length);
    }

    @Test
    @DisplayName("Tên tiếng Việt có dấu đi trọn vòng sinh → đọc, và về ở dạng NFC")
    void tenTiengVietDiTronVong() {
        ParsedWorkbook wb = doc(sinh(mauDayDu()));

        RawRow cu = dongCoMa(wb, "AT-02-001");
        assertThat(cu.get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Văn Đức");
        assertThat(cu.get(ImportColumn.THUY_HIEU)).isEqualTo("Phúc Trung");
        assertThat(cu.get(ImportColumn.TEN_HAN_NOM)).isEqualTo("阮文德");
        assertThat(cu.get(ImportColumn.NGUYEN_QUAN)).isEqualTo("Làng Đông Ngạc, huyện Từ Liêm");

        assertThat(Normalizer.isNormalized(cu.get(ImportColumn.HO_TEN), Normalizer.Form.NFC))
                .as("chuoi phai ve o dang NFC — NFD la cai bay lam hai chuoi trong giong nhau ma"
                        + " khong bang nhau")
                .isTrue();
    }

    @Test
    @DisplayName("Dòng điền sẵn đi tiếp được vào RowMapper: mã, giới, ngày giỗ âm đều hiểu đúng")
    void dongDienSanHieuDuocTiep() {
        ParsedWorkbook wb = doc(sinh(mauDayDu()));

        Optional<PersonRow> cu = rowMapper.toPersonRow(dongCoMa(wb, "AT-02-001"));
        assertThat(cu).isPresent();
        assertThat(cu.get().externalCode()).isEqualTo("AT-02-001");
        assertThat(cu.get().gender()).isEqualTo(vn.giapha.shared.vo.Gender.MALE);
        assertThat(cu.get().generation()).isEqualTo(2);
        assertThat(cu.get().alive()).isFalse();
        assertThat(cu.get().birthYear()).isEqualTo(1915);
        // Ngay gio: chinh la cho ma dinh dang o dan toi mot ngay duong lich im lang neu lam sai.
        assertThat(cu.get().death()).isEqualTo(new LunarDeathDate(1945, 8, 15, false));
        assertThat(cu.get().nativePlaceCode()).isEqualTo("VN");

        Optional<PersonRow> con = rowMapper.toPersonRow(dongCoMa(wb, "AT-03-005"));
        assertThat(con).isPresent();
        assertThat(con.get().fatherCode()).isEqualTo("AT-02-001");
        assertThat(con.get().parentRel()).isEqualTo(
                vn.giapha.dataimport.domain.CellCodec.ParentRel.ADOPT);
    }

    @Test
    @DisplayName("Cờ tháng nhuận sống sót qua vòng sinh → đọc")
    void thangNhuanSongSot() {
        FakeBranchRoster roster = new FakeBranchRoster()
                .chi("Chi Ất", "goc.chi_at")
                .them(daKhuat("AT-02-009", "Nguyễn Thị Nhuận")
                        .ngayMat(LunarDate.ofLeap(1902, 6, 3)).xong());

        ParsedWorkbook wb = doc(sinh(roster));
        PersonRow row = rowMapper.toPersonRow(dongCoMa(wb, "AT-02-009")).orElseThrow();

        assertThat(row.death()).isEqualTo(new LunarDeathDate(1902, 6, 3, true));
        assertThat(row.death().leap())
                .as("mat co nhuan thi ngay gio lech ca mot thang ma khong loi nao duoc bao")
                .isTrue();
    }

    @Test
    @DisplayName("Trang Hôn phối đọc lại được, giữ nguyên bậc và lý do kết thúc")
    void trangHonPhoiDocLaiDuoc() {
        ParsedWorkbook wb = doc(sinh(mauDayDu()));

        assertThat(wb.marriageRows()).hasSize(1);
        MarriageRow m = rowMapper.toMarriageRow(wb.marriageRows().get(0)).orElseThrow();
        assertThat(m.husbandCode()).isEqualTo("AT-02-001");
        assertThat(m.wifeCode()).isEqualTo("AT-02-014");
        assertThat(m.spouseOrder()).isEqualTo(1);
        assertThat(m.endReason()).isEqualTo(
                vn.giapha.dataimport.domain.CellCodec.EndReason.DEATH);
    }

    @Test
    @DisplayName("Họ tên bắt đầu bằng dấu = không trở thành công thức khi quay lại Excel")
    void khongChenCongThuc() {
        FakeBranchRoster roster = new FakeBranchRoster()
                .chi("Chi Ất", "goc.chi_at")
                .them(daKhuat("AT-02-077", "=HYPERLINK(\"http://x\")").xong());

        ParsedWorkbook wb = doc(sinh(roster));
        String hoTen = dongCoMa(wb, "AT-02-077").get(ImportColumn.HO_TEN);

        assertThat(hoTen).startsWith("'=");
    }

    @Test
    @DisplayName("Danh mục địa danh rỗng: vẫn sinh được và vẫn đọc lại được — không chặn ai")
    void danhMucDiaDanhRongVanSinhDuoc() {
        // Khong goi .diaDanh(...) — danh muc 34 tinh la van ban phap quy, co y chua nap.
        FakeBranchRoster roster = new FakeBranchRoster().chi("Chi Bính", "goc.chi_binh");

        assertThatCode(() -> doc(sinh(roster))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Không có chi nào khớp id: vẫn ra một mẫu trắng hợp lệ, không nổ")
    void chiKhongTonTaiVanRaMauTrang() {
        ParsedWorkbook wb = doc(sinh(new FakeBranchRoster().khongCoChi()));

        assertThat(wb.personRows()).isEmpty();
        assertThat(wb.personHeaders()).isNotEmpty();
    }

    // -------------------------------------------------------------------------------------

    static byte[] sinh(BranchRosterPort roster) {
        return new ImportTemplateGenerator(roster).sinh(UUID.randomUUID()).noiDung();
    }

    private ParsedWorkbook doc(byte[] file) {
        return reader.read(new ByteArrayInputStream(file), "mau.xlsx");
    }

    private static RawRow dongCoMa(ParsedWorkbook wb, String ma) {
        return wb.personRows().stream()
                .filter(r -> ma.equals(r.get(ImportColumn.MA)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong thay dong co ma " + ma));
    }

    /** Một chi có một cụ đã khuất khai đủ, một người con nuôi, và một cuộc hôn phối. */
    static FakeBranchRoster mauDayDu() {
        return new FakeBranchRoster()
                .chi("Chi Ất", "goc.chi_at")
                .diaDanh(new MaDiaDanh("VN", "Việt Nam"), new MaDiaDanh("US", "Hoa Kỳ"))
                .them(new NhanKhauDaCo(
                        HoSoGia.daKhuat("Nguyễn Văn Đức").doi(2).namSinh(1915)
                                .ngayGio(LunarDate.of(1945, 8, 15)).huy("Đức").thuy("Phúc Trung")
                                .hanNom("阮文德").nguyenQuan("Làng Đông Ngạc, huyện Từ Liêm").xong(),
                        "AT-02-001", null, null, null, "VN", null, null))
                .them(new NhanKhauDaCo(
                        HoSoGia.daKhuat("Nguyễn Văn Bảo").doi(3).namSinh(1940)
                                .ngayGio(LunarDate.of(1999, 3, 2)).xong(),
                        "AT-03-005", "AT-02-001", "AT-02-014", "Con nuôi", null,
                        "AT-02-001", "Đích tôn"))
                .them(new NhanKhauDaCo(
                        HoSoGia.daKhuat("Trần Thị Mão").gioi(Gender.FEMALE).doi(2).namSinh(1918)
                                .ngayGio(LunarDate.of(1988, 7, 7)).xong(),
                        "AT-02-014", null, null, null, null, null, null))
                .them(new HonPhoiDaCo("AT-02-001", "AT-02-014", "1", "1938", "1945", "Qua đời",
                        true));
    }

    private static Builder daKhuat(String ma, String hoTen) {
        return new Builder(ma, hoTen);
    }

    /** Chỉ để các ca ngắn không phải đếm hết tham số. */
    private static final class Builder {
        private final String ma;
        private final String hoTen;
        private LunarDate ngayMat;

        private Builder(String ma, String hoTen) {
            this.ma = ma;
            this.hoTen = hoTen;
        }

        Builder ngayMat(LunarDate value) {
            this.ngayMat = value;
            return this;
        }

        NhanKhauDaCo xong() {
            return new NhanKhauDaCo(HoSoGia.daKhuat(hoTen).ngayGio(ngayMat).xong(),
                    ma, null, null, null, null, null, null);
        }
    }
}

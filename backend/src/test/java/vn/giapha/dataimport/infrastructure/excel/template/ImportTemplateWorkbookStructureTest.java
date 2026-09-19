package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportColumn;

/**
 * Kiểm <b>cấu trúc</b> của tệp sinh ra: những thứ Excel dùng mà bộ đọc không nhìn thấy — định dạng
 * cột, danh sách thả xuống, vùng đặt tên, trang ẩn, khoá dòng tiêu đề.
 *
 * <p>Vòng khép kín ở {@link ImportTemplateRoundTripTest} chứng minh tệp <b>đọc lại được</b>; bộ
 * này chứng minh tệp <b>dùng được</b>. Hai chuyện khác nhau: một tệp không có định dạng Văn bản ở
 * cột Ngày mất âm vẫn đọc lại tốt trong test, rồi hỏng ngay lần đầu có người gõ {@code 15/8} vào
 * Excel thật.</p>
 */
class ImportTemplateWorkbookStructureTest {

    private XSSFWorkbook wb;

    @BeforeEach
    void sinhVaMo() {
        byte[] file = ImportTemplateRoundTripTest.sinh(ImportTemplateRoundTripTest.mauDayDu());
        try {
            wb = new XSSFWorkbook(new ByteArrayInputStream(file));
        } catch (Exception ex) {
            throw new AssertionError("Khong mo lai duoc tep vua sinh", ex);
        }
    }

    @AfterEach
    void dong() throws Exception {
        if (wb != null) {
            wb.close();
        }
    }

    @Test
    @DisplayName("Năm trang, tên tiếng Việt có dấu ở dạng NFC, Danh mục bị ẩn")
    void namTrangDungTen() {
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            ten.add(wb.getSheetName(i));
        }

        assertThat(ten).containsExactly("Hướng dẫn", "Nhân khẩu", "Hôn phối", "Ví dụ", "Danh mục");
        assertThat(ten).allMatch(s -> Normalizer.isNormalized(s, Normalizer.Form.NFC));
        assertThat(wb.isSheetHidden(wb.getSheetIndex("Danh mục"))).isTrue();

        // Ba trang phu KHONG duoc chua chu "nhan khau"/"hon phoi" sau khi bo dau, neu khong bo doc
        // se doc chung nhu du lieu.
        for (String s : List.of("Hướng dẫn", "Ví dụ", "Danh mục")) {
            String khoa = ImportColumn.khoa(s);
            assertThat(khoa).doesNotContain("nhan kh").doesNotContain("hon phoi");
        }
    }

    @Test
    @DisplayName("Mọi cột dữ liệu định dạng Văn bản — 15/8 không bị Excel nuốt thành ngày dương")
    void moiCotDinhDangVanBan() {
        XSSFSheet nk = wb.getSheet("Nhân khẩu");
        for (int i = 0; i < ImportColumn.values().length; i++) {
            assertThat(nk.getColumnStyle(i))
                    .as("cot %d phai co kieu mac dinh", i)
                    .isNotNull();
            assertThat(nk.getColumnStyle(i).getDataFormatString())
                    .as("cot '%s' phai la dinh dang Van ban", ImportColumn.values()[i].tieuDe())
                    .isEqualTo("@");
        }
    }

    @Test
    @DisplayName("Mỗi cột có một kiểm tra dữ liệu; năm cột tập đóng dùng vùng đặt tên")
    void kiemTraDuLieuDuCot() {
        XSSFSheet nk = wb.getSheet("Nhân khẩu");
        List<? extends DataValidation> dv = nk.getDataValidations();

        assertThat(dv).hasSize(ImportColumn.values().length);

        long soDanhSach = dv.stream()
                .filter(d -> d.getValidationConstraint().getFormula1() != null
                        && d.getValidationConstraint().getFormula1().startsWith("DM_"))
                .count();
        // Gioi, Quan he, Con song, Loai ke tu, Ma nguyen quan (co danh muc trong mauDayDu).
        assertThat(soDanhSach).isEqualTo(5);

        // Canh bao, khong chan: CellCodec co y rong rai voi tu vung, va dan (Ctrl+V) bo qua het
        // data validation nen dung no lam hang rao la tu lua minh.
        dv.stream()
                .filter(d -> d.getValidationConstraint().getFormula1() != null
                        && d.getValidationConstraint().getFormula1().startsWith("DM_"))
                .forEach(d -> assertThat(d.getErrorStyle())
                        .isEqualTo(DataValidation.ErrorStyle.WARNING));
    }

    @Test
    @DisplayName("Vùng đặt tên tồn tại và trỏ vào trang Danh mục")
    void vungDatTenTonTai() {
        for (TuVung tv : TemplateVocabulary.tatCa()) {
            assertThat(wb.getName(tv.tenVungDatTen()))
                    .as("thieu vung dat ten %s", tv.tenVungDatTen())
                    .isNotNull();
            assertThat(wb.getName(tv.tenVungDatTen()).getRefersToFormula())
                    .contains("Danh mục");
        }
        assertThat(wb.getName("DM_MA_NGUYEN_QUAN")).isNotNull();
    }

    @Test
    @DisplayName("Dòng tiêu đề được đóng băng cùng cột Mã")
    void dongTieuDeDuocDongBang() {
        assertThat(wb.getSheet("Nhân khẩu").getPaneInformation()).isNotNull();
        assertThat(wb.getSheet("Nhân khẩu").getPaneInformation().isFreezePane()).isTrue();
    }

    @Test
    @DisplayName("Cột bắt buộc được tô khác cột thường ngay trên dòng tiêu đề")
    void cotBatBuocNhinThayDuoc() {
        XSSFSheet nk = wb.getSheet("Nhân khẩu");
        var oMa = nk.getRow(0).getCell(ImportColumn.MA.ordinal());
        var oDoi = nk.getRow(0).getCell(ImportColumn.DOI.ordinal());

        assertThat(oMa.getStringCellValue()).isEqualTo("Mã *");
        assertThat(oDoi.getStringCellValue()).isEqualTo("Đời");
        assertThat(oMa.getCellStyle().getFillForegroundColor())
                .isNotEqualTo(oDoi.getCellStyle().getFillForegroundColor());
    }

    @Test
    @DisplayName("Danh mục địa danh rỗng: không có vùng đặt tên giả, và cột vẫn gõ tay được")
    void danhMucRongThiKhongCoVungGia() throws Exception {
        byte[] file = ImportTemplateRoundTripTest.sinh(
                new FakeBranchRoster().chi("Chi Bính", "goc.chi_binh"));
        try (XSSFWorkbook rong = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(rong.getName("DM_MA_NGUYEN_QUAN"))
                    .as("danh muc chua nap thi KHONG duoc dung mot danh sach gia")
                    .isNull();
            // Cot van con, van co hop nhac — nguoi dien khong bi chan vi mot bang chua duoc nap.
            assertThat(rong.getSheet("Nhân khẩu").getDataValidations())
                    .hasSize(ImportColumn.values().length);
        }
    }
}

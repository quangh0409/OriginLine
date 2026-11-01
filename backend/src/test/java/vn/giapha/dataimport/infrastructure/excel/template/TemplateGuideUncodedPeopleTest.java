package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Trang "Hướng dẫn" không được tuyên bố "chưa có ai" với một chi đã có người trong phả.
 *
 * <p>Đo trên hệ thật 24/09: mẫu của Chi Bính (hàng trăm người, nhập trước khi có luồng Excel nên
 * không ai mang mã) in "Đã có sẵn trong phả: 0 người" và "Chưa có ai. Đây là lần nhập đầu của chi —
 * bạn khai từ đầu." — tức mời Trưởng chi gõ lại cả chi từ sổ giấy.</p>
 */
@DisplayName("Mẫu Excel — trang Hướng dẫn nói thật về người chưa có mã")
class TemplateGuideUncodedPeopleTest {

    private static String huongDan(BranchRosterPort roster) throws Exception {
        byte[] file = new ImportTemplateGenerator(roster).sinh(UUID.randomUUID()).noiDung();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            XSSFSheet s = wb.getSheet("Hướng dẫn");
            StringBuilder sb = new StringBuilder();
            for (Row row : s) {
                for (Cell c : row) {
                    sb.append(c.getStringCellValue()).append('\n');
                }
            }
            return sb.toString();
        }
    }

    @Test
    @DisplayName("chi có 380 người chưa mang mã → nói ra con số, không bảo 'khai từ đầu'")
    void chiCoNguoiChuaCoMa() throws Exception {
        String t = huongDan(new FakeBranchRoster().chiCoNguoiChuaCoMa("Chi Bính", "goc.chi_binh", 380));

        assertThat(t).contains("0 người mang mã, và 380 người chưa có mã.");
        assertThat(t).contains("Người đã có trong phả nhưng CHƯA có mã");
        assertThat(t).contains("dò trùng");
        assertThat(t).doesNotContain("bạn khai từ đầu");
    }

    @Test
    @DisplayName("chi thật sự trống → vẫn là lần nhập đầu, không có mục thừa")
    void chiTrong() throws Exception {
        String t = huongDan(new FakeBranchRoster().chi("Chi Ất", "goc.chi_at"));

        assertThat(t).contains("Đây là lần nhập đầu của chi — bạn khai từ đầu.");
        assertThat(t).doesNotContain("CHƯA có mã");
    }
}

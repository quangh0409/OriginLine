package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tên tệp tiếng Việt có dấu phải <b>sống sót qua HTTP rồi qua Excel trên Windows</b>.
 *
 * <p>Bên trong {@code .xlsx} là XML UTF-8 nên nội dung không hỏng; chỗ hỏng là <b>tên tệp</b>, vì
 * nó đi qua một tiêu đề HTTP vốn là ISO-8859-1. Xem {@link ExcelTemplateFile}.</p>
 */
class ExcelTemplateFileTest {

    @Test
    @DisplayName("Content-Disposition có cả bản ASCII lẫn bản UTF-8 phần trăm-hoá")
    void coCaHaiDangTenTep() {
        ExcelTemplateFile f = new ExcelTemplateFile(new byte[]{1, 2},
                "Mẫu nhập gia phả - Chi Ất - 2026-09-12.xlsx");

        String cd = f.contentDisposition();

        assertThat(cd).startsWith("attachment; filename=\"");
        assertThat(cd).contains("filename*=UTF-8''");

        String ascii = cd.substring(cd.indexOf('"') + 1, cd.indexOf('"', cd.indexOf('"') + 1));
        assertThat(ascii).isEqualTo("Mau nhap gia pha - Chi At - 2026-09-12.xlsx");
        assertThat(ascii.chars().allMatch(c -> c < 128))
                .as("ban du phong phai thuan ASCII — mot ky tu ngoai ASCII o day la du de hong ca"
                        + " tieu de")
                .isTrue();
    }

    @Test
    @DisplayName("Bản UTF-8 giải mã ngược lại đúng tên gốc, ở dạng NFC")
    void banUtf8GiaiNguocDung() {
        String ten = "Mẫu nhập gia phả - Chi Đông - 2026-09-12.xlsx";
        ExcelTemplateFile f = new ExcelTemplateFile(new byte[0], ten);

        String maHoa = f.contentDisposition().substring(
                f.contentDisposition().indexOf("UTF-8''") + "UTF-8''".length());
        String giai = URLDecoder.decode(maHoa, StandardCharsets.UTF_8);

        assertThat(giai).isEqualTo(Normalizer.normalize(ten, Normalizer.Form.NFC));
    }

    @Test
    @DisplayName("Chữ đ/Đ bị ép về d/D ở bản ASCII — Normalizer một mình không tách được nó")
    void chuDGachNgangDuocEp() {
        ExcelTemplateFile f = new ExcelTemplateFile(new byte[0], "Chi Đông Đoài.xlsx");

        assertThat(ExcelTemplateFile.boDau(f.tenTep())).isEqualTo("Chi Dong Doai.xlsx");
    }

    @Test
    @DisplayName("Ký tự Windows cấm trong tên tệp bị thay, không bị bỏ lửng")
    void kyTuCamBiThay() {
        ExcelTemplateFile f = new ExcelTemplateFile(new byte[0], "Chi Ất/Giáp: bản *mới*?.xlsx");

        assertThat(f.tenTep()).doesNotContain("/", ":", "*", "?");
        assertThat(f.tenTep()).contains("Chi Ất");
    }

    @Test
    @DisplayName("Tên tệp rỗng vẫn ra một tên dùng được thay vì một tiêu đề hỏng")
    void tenRongVanDungDuoc() {
        ExcelTemplateFile f = new ExcelTemplateFile(new byte[0], "   ");

        assertThat(f.tenTep()).isEqualTo("Mau nhap lieu.xlsx");
        assertThat(f.contentDisposition()).contains("Mau nhap lieu.xlsx");
    }

    @Test
    @DisplayName("Nội dung được sao chép — người gọi sửa mảng của mình không đụng tới tệp")
    void noiDungDuocSaoChep() {
        byte[] goc = {1, 2, 3};
        ExcelTemplateFile f = new ExcelTemplateFile(goc, "a.xlsx");

        goc[0] = 99;
        assertThat(f.noiDung()[0]).isEqualTo((byte) 1);
        assertThat(f.kichThuoc()).isEqualTo(3);
    }
}

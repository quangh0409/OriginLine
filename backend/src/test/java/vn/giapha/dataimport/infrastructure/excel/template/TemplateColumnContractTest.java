package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.MarriageColumn;

/**
 * Ghim điều làm cho <b>bộ sinh và bộ đọc không thể lệch nhau</b>.
 *
 * <h2>Cái lệch mà bộ test này tồn tại để chặn</h2>
 * Có người thêm một cột vào {@link ImportColumn} cho bộ đọc, rồi mẫu Excel vẫn phát ra bản cũ.
 * Người dùng tải mẫu về, điền đúng, nộp lên — và được báo sai. Không ai truy ra được vì sao, vì cả
 * hai phía đều khớp tài liệu của riêng mình.
 *
 * <p>Cách chặn không phải là "nhớ cập nhật cả hai chỗ" mà là <b>chỉ có một chỗ</b>: mẫu sinh từ
 * chính enum. Bộ test này khẳng định điều đó vẫn đúng, chứ không tự chép lại danh sách cột — một
 * test chép tay danh sách cột sẽ hỏng theo đúng cách mà bộ sinh chép tay hỏng.</p>
 */
class TemplateColumnContractTest {

    @Test
    @DisplayName("Số cột và thứ tự cột của mẫu bằng đúng enum, không thừa không thiếu")
    void cotSinhTuEnum() {
        List<TemplateColumn> nhanKhau = TemplateColumns.nhanKhau();
        List<TemplateColumn> honPhoi = TemplateColumns.honPhoi();

        assertThat(nhanKhau).hasSameSizeAs(ImportColumn.values());
        assertThat(honPhoi).hasSameSizeAs(MarriageColumn.values());

        for (ImportColumn cot : ImportColumn.values()) {
            assertThat(nhanKhau.get(cot.ordinal()).tieuDe()).isEqualTo(cot.tieuDe());
        }
        for (MarriageColumn cot : MarriageColumn.values()) {
            assertThat(honPhoi.get(cot.ordinal()).tieuDe()).isEqualTo(cot.tieuDe());
        }
    }

    @Test
    @DisplayName("Cờ bắt buộc lấy thẳng từ enum — không có danh sách bắt buộc thứ hai ở tầng mẫu")
    void batBuocLayTuEnum() {
        List<TemplateColumn> nhanKhau = TemplateColumns.nhanKhau();
        for (ImportColumn cot : ImportColumn.values()) {
            assertThat(nhanKhau.get(cot.ordinal()).batBuoc()).isEqualTo(cot.batBuoc());
        }
        // Va cot bat buoc phai NHIN THAY duoc la bat buoc: nguoi dien khong doc tai lieu.
        assertThat(nhanKhau.get(ImportColumn.MA.ordinal()).tieuDeHienThi()).isEqualTo("Mã *");
        assertThat(nhanKhau.get(ImportColumn.GIOI.ordinal()).tieuDeHienThi()).isEqualTo("Giới");
    }

    @Test
    @DisplayName("Dấu * trên tiêu đề bắt buộc vẫn ghép về đúng cột sau khi chuẩn hoá")
    void dauSaoKhongPhaGhepCot() {
        for (TemplateColumn c : TemplateColumns.nhanKhau()) {
            ImportColumn ghep = ImportColumn.bangTra().get(ImportColumn.khoa(c.tieuDeHienThi()));
            assertThat(ghep)
                    .as("tieu de hien thi '%s' phai ghep ve dung cot", c.tieuDeHienThi())
                    .isNotNull();
            assertThat(ghep.tieuDe()).isEqualTo(c.tieuDe());
        }
        for (TemplateColumn c : TemplateColumns.honPhoi()) {
            MarriageColumn ghep = MarriageColumn.bangTra().get(ImportColumn.khoa(c.tieuDeHienThi()));
            assertThat(ghep).isNotNull();
            assertThat(ghep.tieuDe()).isEqualTo(c.tieuDe());
        }
    }

    @Test
    @DisplayName("Mọi cột đều có câu hướng dẫn — cột không có hướng dẫn là cột bị bỏ trống")
    void moiCotDeuCoHuongDan() {
        for (TemplateColumn c : TemplateColumns.nhanKhau()) {
            assertThat(c.moTa()).as("cot %s", c.tieuDe()).isNotBlank();
            assertThat(c.viDu()).as("cot %s", c.tieuDe()).isNotNull();
        }
        for (TemplateColumn c : TemplateColumns.honPhoi()) {
            assertThat(c.moTa()).as("cot %s", c.tieuDe()).isNotBlank();
            assertThat(c.viDu()).as("cot %s", c.tieuDe()).isNotNull();
        }
    }
}

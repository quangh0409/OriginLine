package vn.giapha.dataimport.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.ImportWorkbooks;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.ImportRejectedException;
import vn.giapha.dataimport.domain.ParsedWorkbook;
import vn.giapha.dataimport.domain.RawRow;
import vn.giapha.dataimport.domain.TextNormalizer;

/** Bộ đọc .xlsx: đường đi đúng, và mọi cách một tệp có thể không đúng. */
class XlsxWorkbookReaderTest {

    private final XlsxWorkbookReader reader = new XlsxWorkbookReader();

    @Test
    @DisplayName("Tên tiếng Việt có dấu đi trọn đường từ tệp tới dòng chờ, và ở dạng NFC")
    void tenTiengVietCoDauGiuNguyen() {
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "Húy Cẩn", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8")
                .nhanKhau("AT-02-001", "Nguyễn Thị Lựu", "", "Nữ", "2", "AT-01-001", "", "", "Không",
                        "1915", "3/2 nhuận")
                .build();

        ParsedWorkbook parsed = reader.read(new ByteArrayInputStream(file), "chi-at.xlsx");

        assertThat(parsed.personRows()).hasSize(2);
        RawRow r1 = parsed.personRows().get(0);
        assertThat(r1.get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Văn Cẩn");
        assertThat(r1.get(ImportColumn.TEN_HUY)).isEqualTo("Húy Cẩn");
        assertThat(parsed.personRows().get(1).get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Thị Lựu");

        for (RawRow row : parsed.personRows()) {
            for (String v : row.normalized().values()) {
                assertThat(TextNormalizer.laNfc(v)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Tệp viết bằng NFD (macOS) được chuẩn hoá về NFC khi đọc")
    void nfdThanhNfc() {
        String nfd = Normalizer.normalize("Nguyễn Văn Cẩn", Normalizer.Form.NFD);
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", nfd, "", "Nam", "1", "", "", "", "Không", "1890", "15/8")
                .build();

        ParsedWorkbook parsed = reader.read(new ByteArrayInputStream(file), "macos.xlsx");
        assertThat(parsed.personRows().get(0).get(ImportColumn.HO_TEN))
                .isEqualTo("Nguyễn Văn Cẩn");
    }

    @Test
    @DisplayName("Số dòng đếm theo Excel: dòng dữ liệu đầu tiên là dòng 2, không phải 0")
    void soDongDemTheoExcel() {
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "A", "", "Nam", "1", "", "", "", "Không", "1890", "15/8")
                .build();
        assertThat(reader.read(new ByteArrayInputStream(file), "x.xlsx").personRows().get(0).rowNo())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Ghép cột theo TÊN: chèn thêm một cột ghi chú ở giữa không làm lệch gì")
    void ghepCotTheoTen() {
        List<String> tieuDe = new ArrayList<>(ImportWorkbooks.TIEU_DE_NHAN_KHAU);
        tieuDe.add(2, "Ghi chú của tôi");

        byte[] file = ImportWorkbooks.builder()
                .tieuDeNhanKhau(tieuDe)
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "ghi chú lung tung", "Húy Cẩn", "Nam", "1")
                .build();

        RawRow row = reader.read(new ByteArrayInputStream(file), "x.xlsx").personRows().get(0);
        assertThat(row.get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Văn Cẩn");
        assertThat(row.get(ImportColumn.TEN_HUY)).isEqualTo("Húy Cẩn");
        assertThat(row.get(ImportColumn.GIOI)).isEqualTo("Nam");
    }

    @Test
    @DisplayName("Tiêu đề gõ hoa/thường/không dấu khác nhau vẫn ghép được")
    void tieuDeKhongDauVanGhepDuoc() {
        byte[] file = ImportWorkbooks.builder()
                .tieuDeNhanKhau(List.of("MA", "ho ten", "TÊN HUÝ", "gioi"))
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "Cẩn", "Nam")
                .build();
        RawRow row = reader.read(new ByteArrayInputStream(file), "x.xlsx").personRows().get(0);
        assertThat(row.get(ImportColumn.MA)).isEqualTo("AT-01-001");
        assertThat(row.get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Văn Cẩn");
    }

    @Test
    @DisplayName("Ô trống ở giữa dòng KHÔNG làm dồn cột")
    void oTrongKhongDonCot() {
        // SAX chi goi lai cho o co noi dung. Neu chi noi tiep cac lan goi thi mot dong thieu o se
        // bi don cot va "Khong" (Con song) nhay sang o Quan he.
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-02-001", "Nguyễn Văn B", "", "Nam", "2", "AT-01-001", "", "", "Không",
                        "1920", "1/3")
                .build();
        RawRow row = reader.read(new ByteArrayInputStream(file), "x.xlsx").personRows().get(0);
        assertThat(row.get(ImportColumn.CON_SONG)).isEqualTo("Không");
        assertThat(row.get(ImportColumn.QUAN_HE)).isNull();
        assertThat(row.get(ImportColumn.MA_ME)).isNull();
    }

    @Test
    @DisplayName("Ô Ngày mất âm bị Excel đổi thành ngày dương được ĐÁNH DẤU, không bị đoán ngược")
    void oNgayMatAmBiExcelNuot() {
        byte[] file = ImportWorkbooks.builder()
                .ngayMatAmBiExcelDoiThanhNgayDuong()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8")
                .build();

        RawRow row = reader.read(new ByteArrayInputStream(file), "x.xlsx").personRows().get(0);
        assertThat(row.get(ImportColumn.NGAY_MAT_AM))
                .startsWith(vn.giapha.dataimport.domain.LunarDeathDate.DAU_NGAY_DUONG);
    }

    @Test
    @DisplayName("Trang Hôn phối đọc được, kể cả ba cột thêm")
    void trangHonPhoi() {
        byte[] file = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1")
                .honPhoi("AT-01-001", "AT-01-002", "Vợ cả", "1912", "", "")
                .honPhoi("AT-01-001", "AT-01-003", "Vợ hai", "1930", "1945", "Ly hôn")
                .build();
        ParsedWorkbook parsed = reader.read(new ByteArrayInputStream(file), "x.xlsx");
        assertThat(parsed.marriageRows()).hasSize(2);
    }

    @Test
    @DisplayName("Tệp .xls đổi đuôi thành .xlsx bị từ chối bằng mã lỗi rõ ràng")
    void xlsDoiDuoi() {
        assertThatThrownBy(() -> reader.read(
                new ByteArrayInputStream(ImportWorkbooks.gioLaXlsNhungDatTenXlsx()), "phagoc.xlsx"))
                .isInstanceOf(ImportRejectedException.class)
                .extracting(e -> ((ImportRejectedException) e).code())
                .isEqualTo(ImportRejectedException.BAD_FORMAT);
    }

    @Test
    @DisplayName("Tệp không phải ZIP bị từ chối trước khi POI chạm vào")
    void khongPhaiZip() {
        assertThatThrownBy(() -> reader.read(
                new ByteArrayInputStream("Mã,Họ tên\nAT-01-001,Nguyễn Văn Cẩn".getBytes()), "a.xlsx"))
                .isInstanceOf(ImportRejectedException.class)
                .extracting(e -> ((ImportRejectedException) e).code())
                .isEqualTo(ImportRejectedException.BAD_FORMAT);
    }

    @Test
    @DisplayName("ZIP hợp lệ nhưng không phải workbook bị từ chối, không làm sập tiến trình")
    void zipKhongPhaiWorkbook() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("khong phai workbook".getBytes());
            zip.closeEntry();
        }
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(out.toByteArray()), "a.xlsx"))
                .isInstanceOf(ImportRejectedException.class);
    }

    @Test
    @DisplayName("Thiếu trang Nhân khẩu thì báo đúng chuyện đó")
    void thieuTrangNhanKhau() throws Exception {
        byte[] file;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Linh tinh").createRow(0).createCell(0).setCellValue("x");
            wb.write(out);
            file = out.toByteArray();
        }
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(file), "a.xlsx"))
                .isInstanceOf(ImportRejectedException.class)
                .extracting(e -> ((ImportRejectedException) e).code())
                .isEqualTo(ImportRejectedException.MISSING_SHEET);
    }

    @Test
    @DisplayName("Thiếu cột bắt buộc thì báo đúng tên cột thiếu")
    void thieuCotBatBuoc() {
        byte[] file = ImportWorkbooks.builder()
                .tieuDeNhanKhau(List.of("Họ tên", "Giới", "Đời"))
                .nhanKhau("Nguyễn Văn Cẩn", "Nam", "1")
                .build();
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(file), "a.xlsx"))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("Mã");
    }

    @Test
    @DisplayName("Vượt trần số dòng thì từ chối, không cố đọc hết rồi mới báo")
    void vuotTranSoDong() {
        ImportWorkbooks b = ImportWorkbooks.builder();
        for (int i = 0; i < 5_001; i++) {
            b.nhanKhau("AT-01-" + i, "Người " + i, "", "Nam", "1");
        }
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(b.build()), "to.xlsx"))
                .isInstanceOf(ImportRejectedException.class)
                .extracting(e -> ((ImportRejectedException) e).code())
                .isEqualTo(ImportRejectedException.TOO_MANY_ROWS);
    }

    @Test
    @DisplayName("Tệp có DOCTYPE bị từ chối — ghim mặc định XXE của POI, vì nó đổi theo phiên bản")
    void chanXxe() throws Exception {
        byte[] goc = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1")
                .build();
        byte[] doc = chenDoctypeVaoSheet(goc);

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(doc), "xxe.xlsx"))
                .isInstanceOf(ImportRejectedException.class);
    }

    /** Chèn một khai báo DOCTYPE vào XML của trang tính đầu tiên, giữ nguyên phần còn lại. */
    private static byte[] chenDoctypeVaoSheet(byte[] xlsx) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream in =
                     new java.util.zip.ZipInputStream(new ByteArrayInputStream(xlsx));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] data = in.readAllBytes();
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    String xml = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    int i = xml.indexOf("?>");
                    xml = xml.substring(0, i + 2)
                            + "<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                            + xml.substring(i + 2);
                    data = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(data);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}

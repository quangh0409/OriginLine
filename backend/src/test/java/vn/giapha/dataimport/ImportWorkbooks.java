package vn.giapha.dataimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Dựng tệp {@code .xlsx} trong bộ nhớ cho test.
 *
 * <p>Cố ý dùng {@code XSSFWorkbook} (API nạp cả workbook) để <b>sinh</b>, trong khi bộ đọc thật
 * dùng SAX để <b>đọc</b>. Nhờ thế mỗi ca test là một vòng khép kín đi qua đúng định dạng tệp thật,
 * chứ không phải một bản giả của nó — và mọi khác biệt giữa hai API sẽ lộ ra ở đây thay vì ở máy
 * của Trưởng chi.</p>
 */
public final class ImportWorkbooks {

    /** 11 cột đã chốt, cộng các cột thêm. Test nào cần cột khác thì tự dựng tiêu đề riêng. */
    public static final List<String> TIEU_DE_NHAN_KHAU = List.of(
            "Mã", "Họ tên", "Tên huý", "Giới", "Đời", "Mã cha", "Mã mẹ", "Quan hệ", "Còn sống",
            "Năm sinh", "Ngày mất âm", "Nguyên quán", "Mã nguyên quán", "Thuỵ hiệu",
            "Tên chữ Hán", "Kế tự cho ai (Mã)", "Loại kế tự");

    public static final List<String> TIEU_DE_HON_PHOI = List.of(
            "Mã chồng", "Mã vợ", "Bậc", "Từ năm", "Đến năm", "Lý do kết thúc");

    private final List<List<String>> nhanKhau = new ArrayList<>();
    private final List<List<String>> honPhoi = new ArrayList<>();
    private List<String> tieuDeNhanKhau = TIEU_DE_NHAN_KHAU;
    private int cotNgayMatAmLaNgayDuong = -1;
    private int soDongDem;

    private ImportWorkbooks() {
    }

    public static ImportWorkbooks builder() {
        return new ImportWorkbooks();
    }

    public ImportWorkbooks tieuDeNhanKhau(List<String> value) {
        this.tieuDeNhanKhau = value;
        return this;
    }

    /**
     * Thêm một dòng Nhân khẩu. Thiếu ô thì để trống — đúng như người nhập thật hay làm.
     */
    public ImportWorkbooks nhanKhau(String... cells) {
        nhanKhau.add(List.of(cells));
        return this;
    }

    public ImportWorkbooks honPhoi(String... cells) {
        honPhoi.add(List.of(cells));
        return this;
    }

    /**
     * Mô phỏng cái bẫy lớn nhất của trang Nhân khẩu: ô Ngày mất âm để định dạng mặc định, Excel
     * nuốt {@code 15/8} thành một ngày <b>dương lịch</b> và gửi đi một số sê-ri.
     */
    public ImportWorkbooks ngayMatAmBiExcelDoiThanhNgayDuong() {
        this.cotNgayMatAmLaNgayDuong = tieuDeNhanKhau.indexOf("Ngày mất âm");
        return this;
    }

    /**
     * Thêm một trang phụ chứa {@code soDong} dòng chữ <b>ngẫu nhiên</b>, chỉ để tệp nặng lên.
     *
     * <h2>Vì sao phải có, và vì sao phải là chữ ngẫu nhiên</h2>
     * Trần multipart chặn theo <b>số byte của tệp gửi lên</b>, nên muốn kiểm nó thì phải có một
     * tệp .xlsx <i>thật</i>, <i>hợp lệ</i>, và đủ nặng. Nhân bản một dòng lên vài nghìn lần không
     * làm được việc đó: .xlsx là một kho nén, và văn bản lặp lại co gần về không.
     *
     * <p>Trang này <b>không</b> tên là Nhân khẩu hay Hôn phối nên bộ đọc bỏ qua hoàn toàn — tệp
     * vẫn đúng là tệp hợp lệ của một chi, chỉ nặng hơn.</p>
     */
    public ImportWorkbooks demChoNang(int soDong) {
        this.soDongDem = soDong;
        return this;
    }

    public byte[] build() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle ngay = wb.createCellStyle();
            ngay.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));

            Sheet s1 = wb.createSheet("Nhân khẩu");
            ghiTieuDe(s1, tieuDeNhanKhau);
            for (int i = 0; i < nhanKhau.size(); i++) {
                Row row = s1.createRow(i + 1);
                List<String> cells = nhanKhau.get(i);
                for (int c = 0; c < cells.size(); c++) {
                    String v = cells.get(c);
                    if (v == null || v.isEmpty()) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (c == cotNgayMatAmLaNgayDuong) {
                        // 45889 = mot so se-ri ngay thang bat ky; noi dung khong quan trong, kieu o
                        // moi quan trong.
                        cell.setCellValue(45889d);
                        cell.setCellStyle(ngay);
                    } else {
                        cell.setCellValue(v);
                    }
                }
            }

            Sheet s2 = wb.createSheet("Hôn phối");
            ghiTieuDe(s2, TIEU_DE_HON_PHOI);
            for (int i = 0; i < honPhoi.size(); i++) {
                Row row = s2.createRow(i + 1);
                List<String> cells = honPhoi.get(i);
                for (int c = 0; c < cells.size(); c++) {
                    if (cells.get(c) != null && !cells.get(c).isEmpty()) {
                        row.createCell(c).setCellValue(cells.get(c));
                    }
                }
            }
            demVaoTrangPhu(wb);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Chữ ngẫu nhiên, hạt giống cố định: tệp phải nặng bằng nhau giữa hai lần chạy CI. */
    private void demVaoTrangPhu(Workbook wb) {
        if (soDongDem <= 0) {
            return;
        }
        java.util.Random rng = new java.util.Random(20250912L);
        char[] bang = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        Sheet phu = wb.createSheet("Ghi chú");
        for (int r = 0; r < soDongDem; r++) {
            Row row = phu.createRow(r);
            for (int c = 0; c < 8; c++) {
                StringBuilder sb = new StringBuilder(250);
                for (int i = 0; i < 250; i++) {
                    sb.append(bang[rng.nextInt(bang.length)]);
                }
                row.createCell(c).setCellValue(sb.toString());
            }
        }
    }

    private static void ghiTieuDe(Sheet sheet, List<String> tieuDe) {
        Row header = sheet.createRow(0);
        for (int c = 0; c < tieuDe.size(); c++) {
            header.createCell(c).setCellValue(tieuDe.get(c));
        }
    }

    /** Một tệp .xls giả: chữ ký OLE2, đuôi .xlsx. Ca hay gặp khi người dùng đổi tên tệp tay. */
    public static byte[] gioLaXlsNhungDatTenXlsx() {
        byte[] ole2 = new byte[512];
        ole2[0] = (byte) 0xD0;
        ole2[1] = (byte) 0xCF;
        ole2[2] = (byte) 0x11;
        ole2[3] = (byte) 0xE0;
        return ole2;
    }
}

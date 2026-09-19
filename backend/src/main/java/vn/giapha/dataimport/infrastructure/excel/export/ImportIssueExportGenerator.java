package vn.giapha.dataimport.infrastructure.excel.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.infrastructure.excel.template.ExcelTemplateFile;

/**
 * <b>Bộ xuất danh sách lỗi ra Excel</b> — đường thoát mà người nhập liệu thật sự cần.
 *
 * <h2>Ba trang, và vì sao đúng ba</h2>
 * <ol>
 *   <li><b>Tổng quan</b> — trang mở ra đầu tiên. Nói lô này của chi nào, xuất lúc nào, và hai con
 *       số <b>tách bạch</b>: bao nhiêu lỗi <i>phải sửa</i>, bao nhiêu điều <i>nên xem</i>. Cộng một
 *       đoạn ba bước nói rõ phải làm gì tiếp.</li>
 *   <li><b>Lỗi phải sửa</b> — chỉ {@code BLOCKING}.</li>
 *   <li><b>Nên xem lại</b> — chỉ {@code WARNING}.</li>
 * </ol>
 *
 * <h2>Vì sao hai trang riêng, chứ không một trang có cột "Mức"</h2>
 * Đây là quyết định về <b>con người</b>, không phải về kỹ thuật, và nó đã được chốt ở
 * {@code IssueSeverity}: một danh sách "15 vấn đề" nghe như hỏng cả tệp, và người nhập — Trưởng chi
 * 45–65 tuổi, không phải dân nhập liệu chuyên nghiệp — sẽ bấm bừa cho xong. "4 lỗi phải sửa, 11
 * điều nên xem" nghe là việc làm được. Cùng một dữ liệu, hai kết cục khác hẳn nhau.
 *
 * <p>Một cột "Mức" trên cùng một trang <b>không</b> đủ để giữ sự tách bạch ấy: bộ lọc của Excel
 * mặc định tắt, thanh trạng thái vẫn đếm gộp, và thứ người ta thấy khi cuộn là một danh sách dài.
 * Hai tab thì con số nằm ngay trên tên tab của chính họ, và không có cách nào vô tình gộp lại.</p>
 *
 * <h2>Trang trống vẫn có mặt</h2>
 * Lô không còn lỗi chặn thì trang "Lỗi phải sửa" chỉ có dòng tiêu đề — <b>không</b> bị bỏ đi. Sự
 * vắng mặt của một trang là thông tin mơ hồ ("bộ xuất hỏng?" hay "hết lỗi rồi?"); một trang rỗng
 * dưới cái tên ấy thì rõ ràng, và trang Tổng quan nói thẳng "0 lỗi phải sửa".
 *
 * <h2>Bộ nhớ</h2>
 * {@code XSSFWorkbook} nạp cả workbook, và điều đó an toàn ở đây vì kích thước do <b>ta</b> quyết
 * định: trần lô là {@code ImportLimits.MAX_PERSON_ROWS} dòng, mỗi dòng cùng lắm vài vấn đề. Khác
 * hẳn chiều <b>đọc</b>, nơi tệp là của người lạ và bộ đọc phải dùng SAX.
 */
@Component
public class ImportIssueExportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ImportIssueExportGenerator.class);

    static final String TRANG_TONG_QUAN = "Tổng quan";
    static final String TRANG_LOI_CHAN = "Lỗi phải sửa";
    static final String TRANG_CANH_BAO = "Nên xem lại";

    /**
     * Sinh tệp {@code .xlsx} cho một lô.
     *
     * <p>Trả về {@link ExcelTemplateFile} — cùng kiểu bộ sinh mẫu dùng — chứ không dựng một kiểu
     * thứ hai. Nó đã lo phần khó và dễ hỏng nhất: {@code Content-Disposition} theo RFC 5987 cho tên
     * tệp tiếng Việt có dấu. Một bản sao thứ hai của đoạn mã hoá đó sẽ lệch, và kết quả là Trưởng
     * chi nhận về "Danh sÃ¡ch cáº§n sá»­a.xlsx".</p>
     */
    public ExcelTemplateFile sinh(LoBaoLoi lo) {
        List<LoiXuatExcel> chan = lo.loiChan();
        List<LoiXuatExcel> canhBao = lo.canhBao();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            IssueExportStyles styles = new IssueExportStyles(wb);
            IssueSheetWriter writer = new IssueSheetWriter(styles);

            // Tao theo dung thu tu tab nguoi dung se thay.
            Sheet sTongQuan = wb.createSheet(TRANG_TONG_QUAN);
            Sheet sChan = wb.createSheet(TRANG_LOI_CHAN);
            Sheet sCanhBao = wb.createSheet(TRANG_CANH_BAO);

            writer.ghi(sChan, chan, true);
            writer.ghi(sCanhBao, canhBao, false);
            tongQuan(sTongQuan, styles, lo, chan.size(), canhBao.size());

            wb.setActiveSheet(wb.getSheetIndex(sTongQuan));

            wb.write(out);
            ExcelTemplateFile tep = new ExcelTemplateFile(out.toByteArray(), tenTep(lo));
            // Hai con so ghi TACH BACH ca trong log: mot dong log "15 van de" thi sau nay khong ai
            // tra lai duoc lo do co chan duyet hay khong.
            log.info("Xuat danh sach loi lo {} (chi {}): {} loi chan, {} canh bao, {} byte",
                    lo.maLo(), lo.tenChi(), chan.size(), canhBao.size(), tep.kichThuoc());
            return tep;

        } catch (IOException ex) {
            throw new UncheckedIOException("Khong ghi duoc ban xuat loi cho lo " + lo.maLo(), ex);
        }
    }

    // -------------------------------------------------------------------------------------
    // Trang Tong quan
    // -------------------------------------------------------------------------------------

    /**
     * Trang mở ra đầu tiên: lô này là gì, còn bao nhiêu việc, và <b>làm gì tiếp</b>.
     *
     * <p>Đoạn "làm gì tiếp" không phải phần trang trí. Không có nó, người nhận một tệp lỗi sẽ làm
     * điều tự nhiên nhất và cũng sai nhất: <b>sửa ngay trên bản xuất này rồi tải nó lên</b>. Bản
     * xuất không có cột nào của mẫu nhập liệu, nên bộ đọc sẽ từ chối — và họ sẽ kết luận hệ thống
     * hỏng, đúng vào lúc họ đang cần nó nhất.</p>
     */
    private static void tongQuan(Sheet sheet, IssueExportStyles styles, LoBaoLoi lo,
                                 int soChan, int soCanhBao) {
        int[] r = {0};

        tieuDe(sheet, styles, r, "Danh sách cần sửa — lô nhập liệu");
        trong(sheet, r);

        doi(sheet, styles, r, "Chi / ngành", lo.tenChi());
        doi(sheet, styles, r, "Tệp đã nộp", lo.tenTepGoc());
        doi(sheet, styles, r, "Mã lô", lo.maLo());
        doi(sheet, styles, r, "Ngày xuất", lo.ngayXuat().toString());
        trong(sheet, r);

        // Hai con so, hai dong, KHONG co dong tong. Xem javadoc lop.
        nhanManh(sheet, styles, r, soChan + " lỗi phải sửa"
                + (soChan == 0 ? " — không còn gì chặn lô này." : " — xem trang \""
                        + TRANG_LOI_CHAN + "\". Chưa sửa hết thì chưa bấm duyệt được."));
        thuong(sheet, styles, r, soCanhBao + " điều nên xem lại — xem trang \"" + TRANG_CANH_BAO
                + "\". Đây không phải lỗi: máy nghi ngờ và nhờ người quyết. Xem xong, tick"
                + " \"tôi đã xem\" trên màn hình là đi tiếp được.");
        trong(sheet, r);

        tieuDe(sheet, styles, r, "Làm gì tiếp");
        thuong(sheet, styles, r, "1. Mở tệp gia phả của mình cạnh tệp này (hai cửa sổ Excel song"
                + " song). Bốn cột đầu — Trang, Dòng, Mã, Cột — chỉ đúng ô cần sửa.");
        thuong(sheet, styles, r, "2. Sửa trong TỆP GIA PHẢ, không sửa trong tệp này. Tệp này là"
                + " bản báo cáo: nó không có các cột của mẫu nhập liệu nên tải nó lên sẽ bị từ"
                + " chối.");
        thuong(sheet, styles, r, "3. Tải tệp gia phả đã sửa lên lại. Bộ kiểm chạy lại từ đầu và"
                + " sinh danh sách mới — lỗi cũ không còn lưu lại, nên danh sách ngắn đi đúng bằng"
                + " phần đã sửa.");
        trong(sheet, r);

        thuong(sheet, styles, r, "Cột \"Gợi ý mã gần giống\" là những mã có thật trong tệp hoặc"
                + " trong phả, gần giống mã đã gõ. Gõ nhầm chữ O thành số 0 là lỗi hay gặp nhất —"
                + " chép thẳng mã gợi ý sang là xong.");
        thuong(sheet, styles, r, "Cột \"Mã lỗi\" dành cho lúc nhờ hỗ trợ: đọc chuỗi ấy qua điện"
                + " thoại chính xác hơn đọc lại cả câu.");
        trong(sheet, r);

        // Loi canh bao cuoi cung, va no thuoc ve nguoi cam tep chu khong thuoc ve he thong.
        nhanManh(sheet, styles, r, "Tệp này có ghi mã hồ sơ của người trong phả, nhưng KHÔNG ghi"
                + " tên, năm sinh hay thông tin liên lạc của ai. Dù vậy nó vẫn là dữ liệu nội bộ"
                + " của chi — gửi ra ngoài thì không thu lại được.");

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 110 * 256);
    }

    private static void tieuDe(Sheet sheet, IssueExportStyles styles, int[] r, String text) {
        Cell cell = sheet.createRow(r[0]).createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(styles.tieuDeTrang());
        r[0]++;
    }

    private static void doi(Sheet sheet, IssueExportStyles styles, int[] r, String nhan,
                            String giaTri) {
        Row row = sheet.createRow(r[0]);
        Cell a = row.createCell(0);
        a.setCellValue(nhan);
        a.setCellStyle(styles.chuThuong());
        Cell b = row.createCell(1);
        b.setCellStyle(styles.oVanBan());
        if (giaTri != null && !giaTri.isBlank()) {
            b.setCellValue(giaTri);
        }
        r[0]++;
    }

    private static void nhanManh(Sheet sheet, IssueExportStyles styles, int[] r, String text) {
        cauDaiTronMotDong(sheet, styles.chuNhanManh(), r, text);
    }

    private static void thuong(Sheet sheet, IssueExportStyles styles, int[] r, String text) {
        cauDaiTronMotDong(sheet, styles.chuThuong(), r, text);
    }

    /**
     * Một câu dài trải qua hai cột đã gộp, cao theo nội dung.
     *
     * <p>Không gộp thì câu bị cột B cắt cụt ở chỗ nào tuỳ độ rộng cửa sổ của từng máy, và lời hướng
     * dẫn quan trọng nhất lại là lời bị cắt mất.</p>
     */
    private static void cauDaiTronMotDong(Sheet sheet, org.apache.poi.ss.usermodel.CellStyle style,
                                          int[] r, String text) {
        Row row = sheet.createRow(r[0]);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        Cell b = row.createCell(1);
        b.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(r[0], r[0], 0, 1));
        row.setHeightInPoints(Math.min(72, 15 * (1 + text.length() / 110)));
        r[0]++;
    }

    private static void trong(Sheet sheet, int[] r) {
        sheet.createRow(r[0]);
        r[0]++;
    }

    /**
     * Tên tệp <b>có dấu tiếng Việt</b>, kèm tên chi và ngày xuất.
     *
     * <p>Ngày không phải trang trí: Trưởng chi sẽ có ba bốn bản trong thư mục Tải về sau vài vòng
     * sửa, và "(1)", "(2)" của trình duyệt không nói được bản nào mới hơn. Xem
     * {@link ExcelTemplateFile} về phần tên tệp đi qua HTTP.</p>
     */
    private static String tenTep(LoBaoLoi lo) {
        String ten = lo.tenChi() == null || lo.tenChi().isBlank() ? "chưa gắn chi" : lo.tenChi();
        return "Danh sách cần sửa - " + ten + " - " + lo.ngayXuat() + ".xlsx";
    }
}

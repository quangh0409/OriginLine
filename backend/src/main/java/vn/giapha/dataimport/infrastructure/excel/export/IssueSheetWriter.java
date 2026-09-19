package vn.giapha.dataimport.infrastructure.excel.export;

import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.infrastructure.excel.XlsxGuards;

/**
 * Ghi <b>một</b> trang lỗi: dòng tiêu đề, định dạng cột, rồi các dòng vấn đề.
 *
 * <p>Lớp này không biết trang nó đang ghi là lỗi chặn hay cảnh báo — bên gọi quyết. Đó là chủ ý:
 * nếu nó tự phân loại thì sẽ có ngày một ai đó thêm "trang thứ ba" cho tiện, và
 * {@code IssueSeverity} nói rất rõ vì sao không bao giờ được có nhóm thứ ba.</p>
 */
final class IssueSheetWriter {

    /**
     * Tên trang trong <b>tệp gia phả gốc</b>, tra từ khoá của {@code ImportIssue}.
     *
     * <p>Hiện tên có dấu chứ không hiện {@code NHAN_KHAU}, vì đây là toạ độ để người dùng tìm lại
     * dòng: thứ họ thấy trên thanh tab Excel là "Nhân khẩu". Ba tên này phải khớp từng ký tự với
     * {@code ImportTemplateGenerator} — một bản xuất chỉ tới "Nhân sự" thì không chỉ tới đâu cả.</p>
     */
    private static final Map<String, String> TEN_TRANG = Map.of(
            ImportIssue.SHEET_NHAN_KHAU, "Nhân khẩu",
            ImportIssue.SHEET_HON_PHOI, "Hôn phối",
            ImportIssue.SHEET_LO, "Cả lô");

    private final IssueExportStyles styles;

    IssueSheetWriter(IssueExportStyles styles) {
        this.styles = styles;
    }

    void ghi(Sheet sheet, List<LoiXuatExcel> loi, boolean chan) {
        ghiTieuDe(sheet, chan);
        datDinhDangCot(sheet);

        for (int r = 0; r < loi.size(); r++) {
            ghiDong(sheet.createRow(r + 1), loi.get(r));
        }

        // Dong tieu de luon nhin thay khi cuon. O dong thu 30 cua mot bang loi, khong biet minh
        // dang doc cot nao la ly do de sua nham o.
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, loi.size()),
                0, IssueExportColumn.values().length - 1));
    }

    private void ghiTieuDe(Sheet sheet, boolean chan) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(26);
        for (IssueExportColumn cot : IssueExportColumn.values()) {
            Cell cell = header.createCell(cot.ordinal());
            cell.setCellValue(cot.tieuDe());
            cell.setCellStyle(styles.tieuDe(chan));
        }
    }

    /** Xem {@link IssueExportStyles} — mọi cột định dạng Văn bản, một quy tắc không ngoại lệ. */
    private void datDinhDangCot(Sheet sheet) {
        for (IssueExportColumn cot : IssueExportColumn.values()) {
            sheet.setDefaultColumnStyle(cot.ordinal(), styles.oVanBan());
            sheet.setColumnWidth(cot.ordinal(), cot.doRong() * 256);
        }
    }

    private void ghiDong(Row row, LoiXuatExcel loi) {
        dat(row, IssueExportColumn.TRANG, TEN_TRANG.getOrDefault(loi.sheet(), loi.sheet()));
        dat(row, IssueExportColumn.DONG, loi.rowNo() == null ? null : String.valueOf(loi.rowNo()));
        dat(row, IssueExportColumn.MA, loi.externalCode());
        dat(row, IssueExportColumn.COT, loi.field());
        dat(row, IssueExportColumn.VAN_DE, loi.message());
        dat(row, IssueExportColumn.GOI_Y, ContextChoPhep.goiY(loi.code(), loi.context()));
        dat(row, IssueExportColumn.CHI_TIET, ContextChoPhep.chiTiet(loi.code(), loi.context()));
        dat(row, IssueExportColumn.MA_LOI, loi.code());
    }

    /**
     * Ô luôn được tạo, kể cả khi rỗng — nếu không, {@code getCell(i)} trả {@code null} và mọi phép
     * quét (kể cả bài kiểm rò rỉ) phải nhớ phòng hờ ở từng chỗ.
     *
     * <p>Mọi giá trị đi qua {@link XlsxGuards#chongChenCongThuc}: dữ liệu của chính người dùng đang
     * đi <b>ngược ra</b> một tệp Excel, và một ô họ gõ nhầm thành {@code =cmd|...} sẽ là công thức
     * khi tệp được mở. Bản xuất lỗi là bề mặt nguy hiểm nhất cho chuyện này, vì nội dung của nó
     * gần như toàn bộ là <b>những ô đã bị bộ kiểm chê</b> — tức những ô bất thường nhất trong
     * tệp.</p>
     */
    private void dat(Row row, IssueExportColumn cot, String giaTri) {
        Cell cell = row.createCell(cot.ordinal());
        cell.setCellStyle(styles.o(cot));
        if (giaTri != null && !giaTri.isBlank()) {
            cell.setCellValue(XlsxGuards.chongChenCongThuc(giaTri));
        }
    }
}

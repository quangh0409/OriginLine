package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationConstraint.OperatorType;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import vn.giapha.dataimport.domain.ImportLimits;
import vn.giapha.dataimport.infrastructure.excel.XlsxGuards;

/**
 * Ghi một <b>trang dữ liệu</b> (Nhân khẩu hoặc Hôn phối): dòng tiêu đề, định dạng cột, kiểm tra dữ
 * liệu, và các dòng đã có sẵn trong phả.
 *
 * <h2>Đúng một dòng tiêu đề, ở dòng 1</h2>
 * Bộ đọc quét 10 dòng đầu để tìm tiêu đề nên về lý thuyết có thể chèn một dòng tựa ở trên. Không
 * làm thế: dòng tựa là thứ người điền hay xoá, hay gộp ô, hay sao chép nhầm, và mỗi biến thể lại
 * là một cách để phép dò tiêu đề trượt. Mọi lời giải thích đi vào <b>hộp nhắc</b> của từng ô và
 * trang Hướng dẫn — chúng không chiếm dòng nào của bảng.
 *
 * <h2>Kiểm tra dữ liệu ở mức CẢNH BÁO, không chặn</h2>
 * Xem {@link TemplateVocabulary}. Ngoài ra chọn cảnh báo còn vì một lý do thực tế: dán (Ctrl+V) đè
 * lên một ô <b>bỏ qua toàn bộ</b> data validation của Excel, mà dán là cách Trưởng chi chuyển 400
 * dòng từ tệp cũ sang. Một cơ chế bị vô hiệu hoá bởi thao tác phổ biến nhất thì không nên được
 * dựng như một hàng rào.
 */
final class DataSheetWriter {

    /** Trần vùng gắn kiểm tra dữ liệu — đúng bằng trần dòng mà bộ đọc chấp nhận. */
    private final int soDongVung;

    private final TemplateStyles styles;

    DataSheetWriter(TemplateStyles styles, int soDongVung) {
        this.styles = styles;
        this.soDongVung = soDongVung;
    }

    static DataSheetWriter nhanKhau(TemplateStyles styles) {
        return new DataSheetWriter(styles, ImportLimits.MAX_PERSON_ROWS);
    }

    static DataSheetWriter honPhoi(TemplateStyles styles) {
        return new DataSheetWriter(styles, ImportLimits.MAX_MARRIAGE_ROWS);
    }

    /**
     * @param cot      hợp đồng cột, luôn đến từ {@link TemplateColumns}
     * @param dongSan  các dòng điền sẵn; mỗi dòng là một hàm tra "cột thứ i ra giá trị gì"
     * @param conSong  dòng thứ i có phải người còn sống không — chỉ để tô màu
     */
    void ghi(Sheet sheet, List<TemplateColumn> cot, List<Function<Integer, String>> dongSan,
             List<Boolean> conSong) {
        ghiTieuDe(sheet, cot);
        datDinhDangCot(sheet, cot);
        ghiDongSan(sheet, cot, dongSan, conSong);
        ganKiemTraDuLieu(sheet, cot);

        // Dong tieu de va cot Ma luon nhin thay khi cuon — o dong thu 300 ma khong biet minh dang
        // o cot nao la cach nhanh nhat de dien lech cot.
        sheet.createFreezePane(1, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, dongSan.size()), 0, cot.size() - 1));
    }

    private void ghiTieuDe(Sheet sheet, List<TemplateColumn> cot) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(30);
        for (int i = 0; i < cot.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cot.get(i).tieuDeHienThi());
            cell.setCellStyle(styles.tieuDe(cot.get(i).batBuoc()));
        }
    }

    /** Xem {@link TemplateStyles} — mọi cột đều định dạng Văn bản, một quy tắc không ngoại lệ. */
    private void datDinhDangCot(Sheet sheet, List<TemplateColumn> cot) {
        for (int i = 0; i < cot.size(); i++) {
            sheet.setDefaultColumnStyle(i, styles.oTrong());
            sheet.setColumnWidth(i, doRong(cot.get(i)) * 256);
        }
    }

    private void ghiDongSan(Sheet sheet, List<TemplateColumn> cot,
                            List<Function<Integer, String>> dongSan, List<Boolean> conSong) {
        for (int r = 0; r < dongSan.size(); r++) {
            Row row = sheet.createRow(r + 1);
            boolean song = r < conSong.size() && Boolean.TRUE.equals(conSong.get(r));
            for (int c = 0; c < cot.size(); c++) {
                Cell cell = row.createCell(c);
                cell.setCellStyle(styles.oDaCo(song));
                String v = dongSan.get(r).apply(c);
                if (v != null && !v.isBlank()) {
                    // Du lieu cua chinh nguoi dung di nguoc ra mot tep Excel: mot ho ten bat dau
                    // bang "=" tro thanh cong thuc khi ho mo tep. Xem XlsxGuards.
                    cell.setCellValue(XlsxGuards.chongChenCongThuc(v));
                }
            }
        }
    }

    private void ganKiemTraDuLieu(Sheet sheet, List<TemplateColumn> cot) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (int i = 0; i < cot.size(); i++) {
            TemplateColumn c = cot.get(i);
            CellRangeAddressList vung = new CellRangeAddressList(1, soDongVung, i, i);
            DataValidationConstraint rang = c.coTuVung()
                    ? helper.createFormulaListConstraint(c.tuVung().tenVungDatTen())
                    // Khong phai rang buoc that — chi la cai moc de treo HOP NHAC. Tran do dai
                    // trung voi ImportLimits.MAX_CELL_CHARS de con so noi ra o hai noi la mot.
                    : helper.createTextLengthConstraint(OperatorType.LESS_OR_EQUAL,
                            String.valueOf(ImportLimits.MAX_CELL_CHARS), null);

            DataValidation dv = helper.createValidation(rang, vung);
            dv.setEmptyCellAllowed(true);
            dv.setSuppressDropDownArrow(c.coTuVung());
            dv.setShowPromptBox(true);
            dv.createPromptBox(c.tieuDeHienThi(), nhac(c));
            if (c.coTuVung()) {
                dv.setShowErrorBox(true);
                dv.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                dv.createErrorBox("Giá trị ngoài danh sách",
                        "Bạn vừa gõ một giá trị không có trong danh sách. Hệ thống vẫn nhận nếu"
                                + " hiểu được (ví dụ \"Trai\" hiểu là Nam), nhưng chọn trong danh"
                                + " sách thì chắc chắn hơn.");
            }
            sheet.addValidationData(dv);
        }
    }

    /** Câu trong hộp nhắc: mô tả cột, rồi một ví dụ thật nếu có. */
    private static String nhac(TemplateColumn c) {
        String moTa = c.moTa() == null ? "" : c.moTa();
        if (c.viDu() == null || c.viDu().isBlank()) {
            return moTa;
        }
        return moTa + "\nVí dụ: " + c.viDu();
    }

    private static int doRong(TemplateColumn c) {
        int theoTieuDe = Math.max(10, c.tieuDeHienThi().length() + 2);
        int theoViDu = c.viDu() == null ? 0 : c.viDu().length() + 2;
        return Math.min(40, Math.max(theoTieuDe, theoViDu));
    }
}

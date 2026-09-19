package vn.giapha.dataimport.infrastructure.excel.export;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.infrastructure.excel.template.ExcelTemplateFile;

/**
 * Đồ nghề dùng chung của các bài kiểm bản xuất: dựng đầu vào, sinh tệp, và <b>quét mọi ô của mọi
 * trang</b>.
 *
 * <p>Phép quét là thứ đáng nói nhất ở đây. Kiểm "cột X không có dữ liệu Y" chỉ bắt được rò rỉ ở chỗ
 * người viết test đã nghĩ tới; còn rò rỉ thật thì xuất hiện ở chỗ không ai nghĩ tới — một câu trong
 * trang Tổng quan, một nhãn cột, tên của chính trang tính. Vì vậy phép quét gom cả <b>tên trang</b>
 * lẫn mọi ô của mọi dòng vào một chuỗi duy nhất, rồi bài kiểm khẳng định trên chuỗi ấy.</p>
 */
final class XuatFixtures {

    private XuatFixtures() {
    }

    static final String CHI = "Chi Ất";

    static ExcelTemplateFile sinh(List<LoiXuatExcel> loi) {
        return sinh(loi, CHI);
    }

    static ExcelTemplateFile sinh(List<LoiXuatExcel> loi, String tenChi) {
        LoBaoLoi lo = new LoBaoLoi("11111111-2222-3333-4444-555555555555", tenChi,
                "Gia phả chi Ất - bản chép tay.xlsx", java.time.LocalDate.of(2026, 9, 12), loi);
        return new ImportIssueExportGenerator().sinh(lo);
    }

    static XSSFWorkbook moi(ExcelTemplateFile tep) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(tep.noiDung()));
        } catch (Exception ex) {
            throw new AssertionError("Khong mo lai duoc tep vua sinh", ex);
        }
    }

    /** Tên mọi trang cộng nội dung mọi ô của mọi trang, nối bằng xuống dòng. */
    static String moiO(XSSFWorkbook wb) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            sb.append(wb.getSheetName(i)).append('\n');
            Sheet sheet = wb.getSheetAt(i);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    sb.append(doc(cell)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** Các ô của một dòng trên một trang, theo đúng thứ tự cột. */
    static List<String> dong(XSSFWorkbook wb, String tenTrang, int chiSoDong) {
        Sheet sheet = wb.getSheet(tenTrang);
        Row row = sheet.getRow(chiSoDong);
        if (row == null) {
            return List.of();
        }
        List<String> o = new ArrayList<>();
        for (IssueExportColumn cot : IssueExportColumn.values()) {
            o.add(doc(row.getCell(cot.ordinal())));
        }
        return o;
    }

    /** Giá trị một ô theo cột, trên dòng dữ liệu thứ {@code chiSoDong} (0 = dòng ngay dưới tiêu đề). */
    static String o(XSSFWorkbook wb, String tenTrang, int chiSoDong, IssueExportColumn cot) {
        return dong(wb, tenTrang, chiSoDong + 1).get(cot.ordinal());
    }

    /** Số dòng dữ liệu của một trang lỗi (không tính dòng tiêu đề). */
    static int soDong(XSSFWorkbook wb, String tenTrang) {
        Sheet sheet = wb.getSheet(tenTrang);
        return sheet.getLastRowNum();
    }

    private static String doc(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    // -------------------------------------------------------------------------------------
    // Dung dong loi
    // -------------------------------------------------------------------------------------

    static LoiXuatExcel chan(String code, int rowNo, String field, String message,
                             Map<String, Object> context) {
        return new LoiXuatExcel("BLOCKING", code, ImportIssue.SHEET_NHAN_KHAU, rowNo, field,
                "AT-04-001", message, context);
    }

    static LoiXuatExcel canhBao(String code, int rowNo, String field, String message,
                                Map<String, Object> context) {
        return new LoiXuatExcel("WARNING", code, ImportIssue.SHEET_NHAN_KHAU, rowNo, field,
                "AT-04-001", message, context);
    }
}

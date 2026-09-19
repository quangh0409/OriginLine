package vn.giapha.dataimport.infrastructure.excel.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Bộ định dạng ô của bản xuất lỗi. Dựng <b>một lần cho cả workbook</b> — Excel giới hạn 64.000 kiểu
 * ô, và tạo kiểu trong vòng lặp ghi dòng là cách quen thuộc nhất để một tệp vài trăm dòng vượt trần.
 *
 * <h2>Vì sao MỌI ô dữ liệu vẫn định dạng Văn bản ({@code "@"}), dù đây chỉ là một bản báo cáo</h2>
 * Chính vì nó là bản báo cáo <b>của lỗi định dạng</b>. Cột {@code Chi tiết} chở nguyên văn những ô
 * người nhập đã gõ — và đó thường là {@code 15/8} (ngày mất âm) hay {@code 001} (mã có số 0 đầu),
 * tức đúng hai giá trị mà Excel sẽ tự diễn giải lại nếu ô không phải kiểu Văn bản. Một bảng lỗi
 * hiện "Ngày mất âm: 15/08/2026" thay vì "15/8" thì người đọc không thể hiểu vì sao bộ kiểm kêu,
 * và sẽ kết luận bộ kiểm hỏng. Bộ sinh mẫu đã học bài này ({@code TemplateStyles}); bản xuất kế
 * thừa nguyên vẹn.
 *
 * <h2>Màu ở đây nói một điều, và chỉ một điều</h2>
 * Tiêu đề trang <b>Lỗi phải sửa</b> đỏ sẫm, trang <b>Nên xem lại</b> xám. Không tô màu từng dòng:
 * trong một trang thì mọi dòng cùng một hạng, và tô thêm chỉ làm mắt tìm sai chỗ. Sự tách bạch nằm
 * ở chỗ chúng là <b>hai trang khác nhau</b>, không nằm ở sắc độ.
 */
final class IssueExportStyles {

    private final CellStyle tieuDeChan;
    private final CellStyle tieuDeCanhBao;
    private final CellStyle oVanBan;
    private final CellStyle oGoiY;
    private final CellStyle tieuDeTrang;
    private final CellStyle chuThuong;
    private final CellStyle chuNhanManh;

    IssueExportStyles(Workbook wb) {
        short chuVanBan = wb.getCreationHelper().createDataFormat().getFormat("@");

        Font fTrang = wb.createFont();
        fTrang.setBold(true);
        fTrang.setColor(IndexedColors.WHITE.getIndex());

        Font fDam = wb.createFont();
        fDam.setBold(true);

        Font fTo = wb.createFont();
        fTo.setBold(true);
        fTo.setFontHeightInPoints((short) 14);

        Font fNhanManh = wb.createFont();
        fNhanManh.setBold(true);
        fNhanManh.setColor(IndexedColors.DARK_RED.getIndex());

        tieuDeChan = wb.createCellStyle();
        tieuDeChan.setFont(fTrang);
        tieuDeChan.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        tieuDeChan.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        vienVaCanGiua(tieuDeChan);

        tieuDeCanhBao = wb.createCellStyle();
        tieuDeCanhBao.setFont(fDam);
        tieuDeCanhBao.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        tieuDeCanhBao.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        vienVaCanGiua(tieuDeCanhBao);

        // Xem javadoc lop: "@" cho moi o, khong ngoai le.
        oVanBan = wb.createCellStyle();
        oVanBan.setDataFormat(chuVanBan);
        oVanBan.setWrapText(true);
        oVanBan.setVerticalAlignment(VerticalAlignment.TOP);

        // Cot goi y la o duy nhat nguoi dung SAO CHEP tu ban xuat sang tep goc, nen no duoc in dam
        // de mat tim thay ngay. Van la dinh dang Van ban — ma "001" chep sang ma mat so 0 dau thi
        // goi y con hai hon khong co.
        oGoiY = wb.createCellStyle();
        oGoiY.setDataFormat(chuVanBan);
        oGoiY.setFont(fDam);
        oGoiY.setWrapText(true);
        oGoiY.setVerticalAlignment(VerticalAlignment.TOP);

        tieuDeTrang = wb.createCellStyle();
        tieuDeTrang.setFont(fTo);

        chuThuong = wb.createCellStyle();
        chuThuong.setWrapText(true);
        chuThuong.setVerticalAlignment(VerticalAlignment.TOP);

        chuNhanManh = wb.createCellStyle();
        chuNhanManh.setFont(fNhanManh);
        chuNhanManh.setWrapText(true);
        chuNhanManh.setVerticalAlignment(VerticalAlignment.TOP);
    }

    private static void vienVaCanGiua(CellStyle style) {
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    CellStyle tieuDe(boolean chan) {
        return chan ? tieuDeChan : tieuDeCanhBao;
    }

    CellStyle o(IssueExportColumn cot) {
        return cot == IssueExportColumn.GOI_Y ? oGoiY : oVanBan;
    }

    CellStyle oVanBan() {
        return oVanBan;
    }

    CellStyle tieuDeTrang() {
        return tieuDeTrang;
    }

    CellStyle chuThuong() {
        return chuThuong;
    }

    CellStyle chuNhanManh() {
        return chuNhanManh;
    }
}

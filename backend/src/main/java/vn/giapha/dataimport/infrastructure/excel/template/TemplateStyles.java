package vn.giapha.dataimport.infrastructure.excel.template;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Bộ định dạng ô của mẫu. Dựng <b>một lần cho cả workbook</b> — Excel giới hạn 64.000 kiểu ô, và
 * tạo kiểu trong vòng lặp ghi dòng là cách quen thuộc nhất để một tệp 400 dòng vượt trần.
 *
 * <h2>Vì sao MỌI ô dữ liệu đều định dạng Văn bản ({@code "@"})</h2>
 * Một quy tắc, không ngoại lệ, vì hai lý do đắt tiền và một lý do rẻ:
 * <ol>
 *   <li><b>Ngày giỗ.</b> Để định dạng mặc định thì Excel nuốt {@code 15/8} thành một ngày
 *       <i>dương lịch</i> của năm hiện tại và gửi đi một số sê-ri. Bộ đọc có
 *       {@code XlsxGuards.DAU_NGAY_DUONG} để bắt chuyện này — nhưng bắt được nghĩa là người điền
 *       phải sửa lại. Định dạng sẵn thì họ không bao giờ rơi vào đó.</li>
 *   <li><b>Mã có số 0 ở đầu.</b> {@code 001} ở ô kiểu số thành {@code 1}, và mã là khoá bất biến
 *       của cả đường ống: lệch một ký tự là lần nhập sau tạo người mới thay vì cập nhật.</li>
 *   <li>Ô kiểu số trả về {@code "1945.0"} chứ không phải {@code "1945"} — {@code CellCodec.so}
 *       chịu được, nhưng không phải bắt nó chịu thì hơn.</li>
 * </ol>
 * Đánh đổi: Excel hiện tam giác xanh "số lưu dạng văn bản" ở các cột năm. Chấp nhận — nó là một
 * lời nhắc vô hại, còn một ngày giỗ sai thì không ai phát hiện ra.
 */
final class TemplateStyles {

    private final CellStyle tieuDeBatBuoc;
    private final CellStyle tieuDeThuong;
    private final CellStyle oTrong;
    private final CellStyle oDaCo;
    private final CellStyle oDaCoConSong;
    private final CellStyle oViDu;
    private final CellStyle tieuDeTrang;
    private final CellStyle chuThuong;
    private final CellStyle chuNhanManh;

    TemplateStyles(Workbook wb) {
        short chuVanBan = wb.getCreationHelper().createDataFormat().getFormat("@");

        Font fBold = wb.createFont();
        fBold.setBold(true);

        Font fTieuDeBatBuoc = wb.createFont();
        fTieuDeBatBuoc.setBold(true);
        fTieuDeBatBuoc.setColor(IndexedColors.WHITE.getIndex());

        Font fViDu = wb.createFont();
        fViDu.setItalic(true);
        fViDu.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        Font fTo = wb.createFont();
        fTo.setBold(true);
        fTo.setFontHeightInPoints((short) 14);

        Font fNhanManh = wb.createFont();
        fNhanManh.setBold(true);
        fNhanManh.setColor(IndexedColors.DARK_RED.getIndex());

        // Cot bat buoc: nen do sam, chu trang. Nguoi dien khong doc trang Huong dan, ho doc cai
        // bang truoc mat — nen dau hieu "phai dien" phai nam tren chinh tieu de.
        tieuDeBatBuoc = wb.createCellStyle();
        tieuDeBatBuoc.setFont(fTieuDeBatBuoc);
        tieuDeBatBuoc.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        tieuDeBatBuoc.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        vienVaCanGiua(tieuDeBatBuoc);

        tieuDeThuong = wb.createCellStyle();
        tieuDeThuong.setFont(fBold);
        tieuDeThuong.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        tieuDeThuong.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        vienVaCanGiua(tieuDeThuong);

        oTrong = wb.createCellStyle();
        oTrong.setDataFormat(chuVanBan);

        // Dong dien san (nguoi da co trong pha): nen vang nhat. Khong phai trang tri — no tra loi
        // cau hoi dau tien Truong chi dat ra khi mo tep: "phan nao la cua toi?"
        oDaCo = wb.createCellStyle();
        oDaCo.setDataFormat(chuVanBan);
        oDaCo.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        oDaCo.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Nguoi con song: nen xanh nhat, vi nhung o trong o day KHONG phai thieu du lieu ma la
        // du lieu bi che (xem NhanKhauDaCo). Khong phan biet thi Truong chi se "dien bo sung"
        // nam sinh cua ho tu tri nho — tuc la du lieu Tang 2 quay lai tep bang mot loi khac.
        oDaCoConSong = wb.createCellStyle();
        oDaCoConSong.setDataFormat(chuVanBan);
        oDaCoConSong.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        oDaCoConSong.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        oViDu = wb.createCellStyle();
        oViDu.setDataFormat(chuVanBan);
        oViDu.setFont(fViDu);
        oViDu.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        oViDu.setFillPattern(FillPatternType.SOLID_FOREGROUND);

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

    CellStyle tieuDe(boolean batBuoc) {
        return batBuoc ? tieuDeBatBuoc : tieuDeThuong;
    }

    CellStyle oTrong() {
        return oTrong;
    }

    CellStyle oDaCo(boolean conSong) {
        return conSong ? oDaCoConSong : oDaCo;
    }

    CellStyle oViDu() {
        return oViDu;
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

package vn.giapha.dataimport.infrastructure.excel.template;

import java.util.List;
import java.util.function.IntFunction;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import vn.giapha.dataimport.domain.CellCodec;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.genealogy.application.DisclosedPerson;
import vn.giapha.shared.vo.Gender;

/**
 * Ba trang <b>không phải dữ liệu</b>: "Danh mục" (nguồn của các danh sách chọn), "Ví dụ", và
 * "Hướng dẫn".
 *
 * <h2>Vì sao dòng ví dụ nằm ở trang riêng chứ không nằm ngay trên trang Nhân khẩu</h2>
 * Dòng ví dụ điền sẵn ngay trên trang dữ liệu là cách trình bày tốt hơn — cho tới lần đầu có người
 * quên xoá nó. Lúc ấy "Nguyễn Văn Đức, đời 3, mã AT-03-005" đi thẳng vào phả như một con người
 * thật, và trong một hệ thống <b>chỉ xoá mềm</b> thì cái nút giả ấy ở lại trong cây vĩnh viễn:
 * gỡ ra được, nhưng không bao giờ biến mất.
 *
 * <p>Trang riêng thì rủi ro ấy <b>không tồn tại</b>: bộ đọc chỉ nhận hai trang tên "Nhân khẩu" và
 * "Hôn phối", nên nội dung ở đây không có đường nào vào được đường ống. Đổi lại, người điền phải
 * bấm sang một tab — cái giá rẻ hơn nhiều.</p>
 *
 * <h2>Tên trang là một phần của hợp đồng</h2>
 * Bộ đọc tìm trang theo tên đã bỏ dấu, nên "Danh mục", "Ví dụ", "Hướng dẫn" phải <b>không</b> chứa
 * chữ "nhân khẩu" hay "hôn phối". Nghe hiển nhiên, nhưng một trang tên "Hướng dẫn nhập Nhân khẩu"
 * sẽ được đọc như dữ liệu và cả tệp hỏng theo cách rất khó hiểu.
 */
final class HelperSheetWriter {

    private final TemplateStyles styles;

    HelperSheetWriter(TemplateStyles styles) {
        this.styles = styles;
    }

    /**
     * Dựng trang "Danh mục" và đăng ký <b>vùng đặt tên</b> cho từng tập giá trị.
     *
     * <p>Dùng vùng đặt tên thay vì nhúng danh sách thẳng vào ô kiểm tra dữ liệu vì hai lẽ: danh
     * sách nhúng có trần 255 ký tự (danh mục 34 tỉnh vượt ngay), và một vùng đặt tên sửa được ở
     * một chỗ thay vì ở 5.000 ô.</p>
     *
     * @param diaDanh danh mục mã tỉnh/quốc gia; rỗng thì <b>không</b> đăng ký vùng nào cho nó
     * @return từ vựng cho cột "Mã nguyên quán", hoặc {@code null} khi danh mục còn rỗng
     */
    TuVung danhMuc(Workbook wb, XSSFSheet sheet, List<MaDiaDanh> diaDanh) {
        List<TuVung> tuVung = TemplateVocabulary.tatCa();
        int cot = 0;
        for (TuVung tv : tuVung) {
            ghiCot(sheet, cot, tv.tenVungDatTen(), tv.nhan());
            datVung(wb, sheet, tv.tenVungDatTen(), cot, tv.nhan().size());
            cot++;
        }

        TuVung tvDiaDanh = null;
        if (!diaDanh.isEmpty()) {
            List<String> ma = diaDanh.stream().map(MaDiaDanh::ma).toList();
            // Danh sach chon chi chua MA. Ten day du nam o cot ke ben de tra — xem MaDiaDanh.
            ghiCot(sheet, cot, "DM_MA_NGUYEN_QUAN", ma);
            datVung(wb, sheet, "DM_MA_NGUYEN_QUAN", cot, ma.size());
            ghiCot(sheet, cot + 1, "Tra cứu mã nguyên quán",
                    diaDanh.stream().map(MaDiaDanh::dongTraCuu).toList());
            tvDiaDanh = new TuVung("DM_MA_NGUYEN_QUAN", s -> s,
                    diaDanh.stream().map(d -> new TuVung.Muc(d.ma(), d.ma())).toList());
            sheet.setColumnWidth(cot + 1, 40 * 256);
        }

        for (int i = 0; i <= cot; i++) {
            if (sheet.getColumnWidth(i) < 18 * 256) {
                sheet.setColumnWidth(i, 18 * 256);
            }
        }
        // Khoa trang: khong phai bao mat (go khoa khong mat khau mat ba giay), ma de mot cu keo
        // chuot nham khong lam hong nguon cua moi danh sach chon trong tep.
        sheet.enableLocking();
        return tvDiaDanh;
    }

    /** Trang "Ví dụ": một dòng người đã khuất khai đủ, một dòng người còn sống, một dòng hôn phối. */
    void viDu(XSSFSheet sheet, List<TemplateColumn> cotNhanKhau,
              List<TemplateColumn> cotHonPhoi) {
        int r = 0;
        r = tua(sheet, r, "Ví dụ — ĐỪNG chép cả dòng sang trang Nhân khẩu",
                "Trang này chỉ để xem. Bộ đọc của hệ thống KHÔNG đọc trang này, nên dù quên xoá thì"
                        + " cũng không ai bị thêm vào gia phả. Nhìn cách gõ rồi tự điền sang trang"
                        + " Nhân khẩu.");
        r++;

        // ImportColumn.values()[i] ghep dung cot thu i vi TemplateColumns.nhanKhau() duyet enum
        // theo dung thu tu khai bao — cung mot nguon, nen khong lech duoc.
        r = bang(sheet, r, "Trang Nhân khẩu", cotNhanKhau, List.<IntFunction<String>>of(
                i -> cotNhanKhau.get(i).viDu(),
                i -> VI_DU_CON_SONG.giaTri(ImportColumn.values()[i])));
        r++;
        bang(sheet, r, "Trang Hôn phối", cotHonPhoi,
                List.<IntFunction<String>>of(i -> cotHonPhoi.get(i).viDu()));

        for (int i = 0; i < cotNhanKhau.size(); i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
        sheet.enableLocking();
    }

    /**
     * Một người <b>còn sống</b> làm ví dụ — dựng qua đúng {@link NhanKhauDaCo} mà tệp thật dùng,
     * nên các ô trống ở đây trống vì cùng một lý do với tệp thật. Người điền nhìn thấy ngay rằng
     * ô năm sinh trống ở dòng màu xanh là <b>cố ý</b>, không phải sót.
     *
     * <p>Hồ sơ đi kèm là một {@code DisclosedPerson} <b>hư cấu</b> ở mức mà một thành viên thường
     * nhận được cho người còn sống: tên, giới, đời — hết. Không phải một người thật, nên không có
     * gì để rò; nhưng nó phải đi qua cùng một lối dựng, vì một dòng ví dụ gõ tay là chỗ dễ nhất để
     * lọt một mẩu dữ liệu thật vào tệp (và {@code TemplateLivingPersonLeakTest} quét cả trang này).</p>
     *
     * <p>{@code "VN"} ở ô mã nguyên quán là <b>cố ý</b>: nó phải biến mất khi ghi ra, vì hồ sơ nói
     * người gọi không được xem khối dữ liệu ấy. Ví dụ vì thế cũng là một phép thử nhỏ của chính
     * phép che.</p>
     */
    private static final NhanKhauDaCo VI_DU_CON_SONG = new NhanKhauDaCo(
            new DisclosedPerson(null, "Nguyễn Thị Hoa", null, null, null, Gender.FEMALE, 4, true,
                    null, null, null, false),
            "AT-04-012", "AT-03-005", "AT-03-006",
            TemplateVocabulary.nhan(ImportColumn.QUAN_HE, CellCodec.ParentRel.BIO),
            "VN", null, null);

    /** Trang "Hướng dẫn" — trang đầu tiên người điền nhìn thấy khi mở tệp. */
    void huongDan(XSSFSheet sheet, BranchRosterPort.Chi chi, int soNguoiDienSan, int soHonPhoi,
                  boolean coDanhMucDiaDanh) {
        int r = 0;
        r = tua(sheet, r, "Mẫu nhập gia phả — " + (chi == null ? "chi chưa xác định" : chi.ten()),
                chi == null ? "Tệp này chưa gắn với chi nào."
                        : "Chi: " + chi.ten() + "  ·  Đường dẫn: " + chi.path()
                                + "  ·  Đã có sẵn trong phả: " + chi.soNguoi() + " người.");
        r++;

        r = muc(sheet, r, "Tệp này đã điền sẵn gì cho bạn",
                soNguoiDienSan == 0
                        ? "Chưa có ai. Đây là lần nhập đầu của chi — bạn khai từ đầu."
                        : "Trang Nhân khẩu đã có " + soNguoiDienSan + " người và trang Hôn phối đã"
                                + " có " + soHonPhoi + " cặp, kèm đúng mã của họ."
                                + " ĐỪNG xoá các dòng đó và ĐỪNG đổi mã: hệ thống tra theo mã để"
                                + " CẬP NHẬT người cũ. Đổi mã thì lần nộp sau sẽ tạo ra một người"
                                + " thứ hai y hệt.");

        r = muc(sheet, r, "Các dòng nền xanh nhạt — người còn sống",
                "Ở những dòng ấy, các ô Năm sinh, Tên huý, Thuỵ hiệu, Nguyên quán, Ngày mất âm được"
                        + " để trống CÓ CHỦ Ý, không phải vì hệ thống thiếu dữ liệu. Theo Nghị định"
                        + " 13/2023, dữ liệu của người còn sống không được đưa ra ngoài hệ thống"
                        + " trong một tệp tải về. Bạn không cần điền hộ họ; nếu có sẵn trong sổ thì"
                        + " điền cũng được, hệ thống sẽ hỏi ý người đó ở bước duyệt.");

        r = muc(sheet, r, "Cột có dấu * là bắt buộc",
                "Tiêu đề nền đỏ, chữ trắng. Thiếu chúng thì cả dòng không dùng được."
                        + " Các cột còn lại: biết tới đâu điền tới đó, đừng bịa.");

        r = muc(sheet, r, "Ngày mất âm — chỗ hay sai nhất",
                "Gõ theo ngày ÂM LỊCH:  15/8  ·  15/8/1945  ·  15/8 nhuận  ·  15/8 nhuận 1945."
                        + " Không rõ năm thì bỏ năm, đó là chuyện bình thường trong sổ cũ."
                        + " Cột này đã được đặt định dạng Văn bản sẵn — ĐỪNG đổi sang định dạng"
                        + " Ngày, vì khi đó Excel sẽ tự biến 15/8 thành một ngày dương lịch của năm"
                        + " nay và ngày giỗ sẽ sai mà không ai báo lỗi.");

        r = muc(sheet, r, "Mã nguyên quán",
                coDanhMucDiaDanh
                        ? "Chọn trong danh sách. Không thấy nơi cần tìm thì để trống và chỉ điền"
                                + " cột Nguyên quán bằng chữ."
                        : "Danh mục tỉnh/thành CHƯA được nạp vào hệ thống, nên cột này chưa có danh"
                                + " sách để chọn. Cứ để trống — chỉ cần điền cột Nguyên quán bằng"
                                + " chữ như trong sổ. Không ai bị chặn vì chuyện này.");

        r = muc(sheet, r, "Đừng làm ba việc sau",
                "1) Đổi tên trang \"Nhân khẩu\" / \"Hôn phối\" — hệ thống tìm trang theo tên."
                        + "  2) Xoá hoặc sửa dòng tiêu đề — cột được ghép theo tên tiêu đề, không"
                        + " theo vị trí, nên thêm một cột ghi chú của riêng bạn thì KHÔNG sao, mà"
                        + " sửa tiêu đề thì hỏng."
                        + "  3) Lưu thành .xls hay .csv — chỉ nhận .xlsx.");

        muc(sheet, r, "Nộp xong rồi thì sao",
                "Hệ thống đọc tệp vào KHU VỰC CHỜ và trả lại một bảng lỗi/cảnh báo. Không một dòng"
                        + " nào vào gia phả trước khi bạn xem bảng đó và bấm duyệt. Sửa tệp rồi nộp"
                        + " lại bao nhiêu lần cũng được — nhờ cột Mã, nộp lại KHÔNG sinh người"
                        + " trùng.");

        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 90 * 256);
        sheet.enableLocking();
    }

    // ---------------------------------------------------------------------------------------

    private int tua(Sheet sheet, int r, String tieuDe, String phu) {
        Row r0 = sheet.createRow(r++);
        Cell c0 = r0.createCell(0);
        c0.setCellValue(tieuDe);
        c0.setCellStyle(styles.tieuDeTrang());

        Row r1 = sheet.createRow(r++);
        Cell c1 = r1.createCell(0);
        c1.setCellValue(phu);
        c1.setCellStyle(styles.chuThuong());
        return r;
    }

    private int muc(Sheet sheet, int r, String tieuDe, String noiDung) {
        Row row = sheet.createRow(r++);
        row.setHeightInPoints(46);
        Cell a = row.createCell(0);
        a.setCellValue(tieuDe);
        a.setCellStyle(styles.chuNhanManh());
        Cell b = row.createCell(1);
        b.setCellValue(noiDung);
        b.setCellStyle(styles.chuThuong());
        return r;
    }

    private int bang(Sheet sheet, int r, String nhan, List<TemplateColumn> cot,
                     List<IntFunction<String>> dong) {
        Row nhanRow = sheet.createRow(r++);
        Cell c = nhanRow.createCell(0);
        c.setCellValue(nhan);
        c.setCellStyle(styles.chuNhanManh());

        Row header = sheet.createRow(r++);
        for (int i = 0; i < cot.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cot.get(i).tieuDeHienThi());
            cell.setCellStyle(styles.tieuDe(cot.get(i).batBuoc()));
        }
        for (IntFunction<String> d : dong) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < cot.size(); i++) {
                Cell cell = row.createCell(i);
                cell.setCellStyle(styles.oViDu());
                String v = d.apply(i);
                if (v != null && !v.isBlank()) {
                    cell.setCellValue(v);
                }
            }
        }
        return r;
    }

    private static void ghiCot(Sheet sheet, int cot, String tieuDe, List<String> giaTri) {
        Row header = sheet.getRow(0) == null ? sheet.createRow(0) : sheet.getRow(0);
        header.createCell(cot).setCellValue(tieuDe);
        for (int i = 0; i < giaTri.size(); i++) {
            Row row = sheet.getRow(i + 1) == null ? sheet.createRow(i + 1) : sheet.getRow(i + 1);
            row.createCell(cot).setCellValue(giaTri.get(i));
        }
    }

    /**
     * Đăng ký vùng đặt tên trỏ vào một cột của trang "Danh mục".
     *
     * <p>Tên trang có dấu cách và dấu tiếng Việt nên <b>phải</b> bọc trong dấu nháy đơn trong công
     * thức. Thiếu dấu nháy thì Excel không mở được tệp và báo "nội dung không đọc được" — một lỗi
     * nhìn như tệp hỏng chứ không như một công thức sai.</p>
     */
    private static void datVung(Workbook wb, Sheet sheet, String ten, int cot, int soDong) {
        if (soDong <= 0) {
            return;
        }
        String chuCot = CellReference.convertNumToColString(cot);
        String tenTrang = "'" + sheet.getSheetName().replace("'", "''") + "'";
        Name name = wb.createName();
        name.setNameName(ten);
        name.setRefersToFormula(tenTrang + "!$" + chuCot + "$2:$" + chuCot + "$" + (soDong + 1));
    }
}

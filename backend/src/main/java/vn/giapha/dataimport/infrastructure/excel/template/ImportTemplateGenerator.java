package vn.giapha.dataimport.infrastructure.excel.template;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.genealogy.application.DisclosureAudience;

/**
 * <b>Bộ sinh mẫu Excel cho một chi cụ thể</b> (hạng mục N1).
 *
 * <h2>Năm trang, và vì sao đúng năm</h2>
 * <ol>
 *   <li><b>Hướng dẫn</b> — trang mở ra đầu tiên. Nói về <i>chi này</i>, không nói chung chung.</li>
 *   <li><b>Nhân khẩu</b> — trang dữ liệu, đã điền sẵn những người đã có trong phả.</li>
 *   <li><b>Hôn phối</b> — trang dữ liệu thứ hai.</li>
 *   <li><b>Ví dụ</b> — dòng mẫu, ở trang riêng để không có đường vào gia phả
 *       ({@link HelperSheetWriter}).</li>
 *   <li><b>Danh mục</b> (ẩn) — nguồn của mọi danh sách chọn qua vùng đặt tên.</li>
 * </ol>
 *
 * <h2>Bất biến được test canh</h2>
 * <ul>
 *   <li>Tệp sinh ra <b>đọc lại được bằng chính {@code XlsxWorkbookReader}</b> của đường nhập liệu,
 *       không một lỗi định dạng nào. Một mẫu mà bộ đọc của chính hệ thống từ chối là lỗi tệ nhất
 *       có thể có ở đây, nên đó là bài kiểm quan trọng nhất của gói này.</li>
 *   <li>Mọi tiêu đề đến từ {@link TemplateColumns}, tức từ hai enum mà bộ đọc dùng.</li>
 *   <li>Không dòng nào chở dữ liệu ngoài Tầng 1 của một người còn sống
 *       ({@link NhanKhauDaCo}).</li>
 * </ul>
 *
 * <h2>Bộ nhớ</h2>
 * Chiều ghi dùng {@code XSSFWorkbook} (nạp cả workbook) trong khi chiều đọc dùng SAX. Khác biệt ấy
 * là có lý do: kích thước tệp <b>ghi ra</b> do ta quyết định — trần 5.000 dòng của
 * {@code ImportLimits} là vài MB heap, còn tệp <b>đọc vào</b> là của người lạ.
 */
@Component
public class ImportTemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(ImportTemplateGenerator.class);

    private static final String TRANG_HUONG_DAN = "Hướng dẫn";
    private static final String TRANG_VI_DU = "Ví dụ";
    private static final String TRANG_DANH_MUC = "Danh mục";

    /**
     * Tên hai trang dữ liệu — <b>phải</b> khớp thứ {@code XlsxWorkbookReader} đi tìm.
     *
     * <p>Đặt ở đây thay vì lấy từ bộ đọc vì bộ đọc nhận diện bằng một phép so khớp đã bỏ dấu
     * ("nhan khau"), không bằng một hằng số dùng chung. Vòng khép kín sinh → đọc trong
     * {@code ImportTemplateRoundTripTest} là thứ ghim hai bên lại với nhau; nếu bộ đọc đổi cách
     * nhận trang thì test đỏ ngay, chứ không phải người dùng phát hiện hộ.</p>
     */
    private static final String TRANG_NHAN_KHAU = "Nhân khẩu";
    private static final String TRANG_HON_PHOI = "Hôn phối";

    private final BranchRosterPort roster;

    public ImportTemplateGenerator(BranchRosterPort roster) {
        this.roster = roster;
    }

    /**
     * Sinh mẫu cho một chi.
     *
     * <p><b>Tệp sinh ra phụ thuộc người đang gọi.</b> Danh tính ấy được chốt <b>một lần</b> ở
     * dòng đầu tiên rồi dùng cho cả hai trang dữ liệu: hỏi lại giữa chừng thì trang Nhân khẩu và
     * trang Hôn phối của cùng một tệp có thể bị lọc theo hai ngữ cảnh khác nhau.</p>
     *
     * @param branchId chi cần sinh mẫu; {@code null} hoặc chi không tồn tại vẫn cho ra một mẫu
     *        trắng hợp lệ — mẫu trắng là thứ dùng được, còn một lỗi 500 lúc tải mẫu thì không
     */
    public ExcelTemplateFile sinh(UUID branchId) {
        DisclosureAudience nguoiTaiVe = roster.nguoiTaiVe();
        BranchRosterPort.Chi chi = roster.chi(branchId);
        List<NhanKhauDaCo> nguoi = chi == null ? List.of() : roster.nhanKhau(branchId, nguoiTaiVe);
        List<HonPhoiDaCo> honPhoi = chi == null ? List.of() : roster.honPhoi(branchId, nguoiTaiVe);
        List<MaDiaDanh> diaDanh = roster.danhMucDiaDanh();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            TemplateStyles styles = new TemplateStyles(wb);
            HelperSheetWriter helper = new HelperSheetWriter(styles);

            // Tao truoc theo dung thu tu tab nguoi dung se thay.
            var sHuongDan = wb.createSheet(TRANG_HUONG_DAN);
            var sNhanKhau = wb.createSheet(TRANG_NHAN_KHAU);
            var sHonPhoi = wb.createSheet(TRANG_HON_PHOI);
            var sViDu = wb.createSheet(TRANG_VI_DU);
            var sDanhMuc = wb.createSheet(TRANG_DANH_MUC);

            // Vung dat ten phai ton tai TRUOC khi gan kiem tra du lieu tro vao no.
            TuVung tvDiaDanh = helper.danhMuc(wb, sDanhMuc, diaDanh);

            List<TemplateColumn> cotNhanKhau = capNhatDiaDanh(TemplateColumns.nhanKhau(), tvDiaDanh);
            List<TemplateColumn> cotHonPhoi = TemplateColumns.honPhoi();

            DataSheetWriter.nhanKhau(styles).ghi(sNhanKhau, cotNhanKhau,
                    dongNhanKhau(nguoi), nguoi.stream().map(NhanKhauDaCo::phaiCheTangTren).toList());
            DataSheetWriter.honPhoi(styles).ghi(sHonPhoi, cotHonPhoi,
                    dongHonPhoi(honPhoi), honPhoi.stream().map(HonPhoiDaCo::phaiCheTangTren).toList());

            helper.viDu(sViDu, cotNhanKhau, cotHonPhoi);
            helper.huongDan(sHuongDan, chi, nguoi.size(), honPhoi.size(), tvDiaDanh != null);

            // An trang Danh muc: no la co khi cua tep, khong phai thu de doc. Van phai co mat, vi
            // vung dat ten tro vao no.
            wb.setSheetHidden(wb.getSheetIndex(sDanhMuc), true);
            wb.setActiveSheet(wb.getSheetIndex(sHuongDan));

            wb.write(out);
            ExcelTemplateFile file = new ExcelTemplateFile(out.toByteArray(), tenTep(chi));
            // Ghi ca VAI nguoi tai ve: noi dung tep phu thuoc vai, nen mot dong log khong co vai
            // thi khong tra lai duoc "vi sao ban tep hom ay thieu nam sinh".
            log.info("Sinh mau Excel cho chi {} (vai {}): {} nguoi dien san, {} hon phoi,"
                            + " {} ma dia danh, {} byte",
                    chi == null ? "(khong xac dinh)" : chi.ten(),
                    nguoiTaiVe == null ? "(khong ro)" : nguoiTaiVe.vai(),
                    nguoi.size(), honPhoi.size(), diaDanh.size(), file.kichThuoc());
            return file;

        } catch (IOException ex) {
            throw new UncheckedIOException("Khong ghi duoc mau Excel cho chi " + branchId, ex);
        }
    }

    /**
     * Gắn danh sách mã địa danh vào cột "Mã nguyên quán" — <b>chỉ khi</b> danh mục có dữ liệu.
     *
     * <p>Danh mục rỗng là trạng thái hợp lệ: 34 tỉnh sau sáp nhập là văn bản pháp quy do quản trị
     * nạp, không phải thứ chép tay vào migration, và một danh mục sai làm báo cáo dân số sai một
     * cách im lặng. Khi rỗng thì cột vẫn có mặt, vẫn gõ tay được, chỉ không có danh sách để chọn —
     * <b>không chặn ai</b>, và cũng không dựng một danh sách giả để trông cho đủ.</p>
     */
    private static List<TemplateColumn> capNhatDiaDanh(List<TemplateColumn> cot, TuVung tv) {
        if (tv == null) {
            return cot;
        }
        List<TemplateColumn> ket = new ArrayList<>(cot);
        int i = TemplateColumns.viTri(ImportColumn.MA_NGUYEN_QUAN);
        TemplateColumn cu = ket.get(i);
        ket.set(i, new TemplateColumn(cu.tieuDe(), cu.batBuoc(), cu.moTa(), cu.viDu(), tv));
        return List.copyOf(ket);
    }

    private static List<Function<Integer, String>> dongNhanKhau(List<NhanKhauDaCo> nguoi) {
        List<Function<Integer, String>> dong = new ArrayList<>(nguoi.size());
        for (NhanKhauDaCo n : nguoi) {
            dong.add(i -> n.giaTri(ImportColumn.values()[i]));
        }
        return dong;
    }

    private static List<Function<Integer, String>> dongHonPhoi(List<HonPhoiDaCo> honPhoi) {
        List<Function<Integer, String>> dong = new ArrayList<>(honPhoi.size());
        for (HonPhoiDaCo h : honPhoi) {
            dong.add(i -> h.giaTri(MarriageColumn.values()[i]));
        }
        return dong;
    }

    /**
     * Tên tệp <b>có dấu tiếng Việt</b>, kèm tên chi và ngày sinh mẫu.
     *
     * <p>Ngày trong tên tệp không phải trang trí: Trưởng chi sẽ có ba bản trong thư mục Tải về sau
     * ba lần thử, và "(1)", "(2)" của trình duyệt không nói được bản nào mới hơn. Xem
     * {@link ExcelTemplateFile} về phần tên tệp đi qua HTTP.</p>
     */
    private static String tenTep(BranchRosterPort.Chi chi) {
        String ten = chi == null ? "chưa gắn chi" : chi.ten();
        return "Mẫu nhập gia phả - " + ten + " - " + LocalDate.now() + ".xlsx";
    }
}

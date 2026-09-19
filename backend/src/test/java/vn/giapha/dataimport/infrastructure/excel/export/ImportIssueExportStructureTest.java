package vn.giapha.dataimport.infrastructure.excel.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;
import vn.giapha.dataimport.infrastructure.excel.template.ExcelTemplateFile;

/**
 * Cấu trúc của bản xuất: những thứ <b>Excel</b> dùng và mắt người dùng thấy — số trang, tên trang,
 * định dạng ô, khoá dòng tiêu đề, và trên hết là <b>sự tách bạch giữa lỗi chặn và cảnh báo</b>.
 *
 * <p>Đây không phải kiểm cho vui hình thức. Một bản xuất gộp hai nhóm vào một trang vẫn "chạy
 * đúng" theo mọi phép kiểm chức năng, rồi hỏng ở chỗ không đo được bằng code: người nhập thấy "15
 * vấn đề", kết luận tệp mình hỏng, và bấm bừa. Vì vậy sự tách bạch ấy được ghim bằng test như một
 * yêu cầu chức năng, chứ không để nó là một thói quen của người viết.</p>
 */
class ImportIssueExportStructureTest {

    private static final String MA_LO_MAT = "AT-01-OO3";

    // -------------------------------------------------------------------------------------
    // Ba trang, tach bach
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Ba trang: Tổng quan, Lỗi phải sửa, Nên xem lại — tên có dấu ở dạng NFC")
    void baTrangDungTen() {
        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(mauDayDu()));

        List<String> ten = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            ten.add(wb.getSheetName(i));
        }

        assertThat(ten).containsExactly("Tổng quan", "Lỗi phải sửa", "Nên xem lại");
        assertThat(ten).allMatch(s -> Normalizer.isNormalized(s, Normalizer.Form.NFC));
        // Trang mo ra dau tien phai la Tong quan: no la cho noi "lam gi tiep".
        assertThat(wb.getActiveSheetIndex()).isZero();
    }

    @Test
    @DisplayName("Lỗi chặn và cảnh báo nằm ở hai trang khác nhau, không dòng nào lẫn sang trang kia")
    void haiNhomKhongLanNhau() {
        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(mauDayDu()));

        List<String> maTrangChan = maLoi(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN);
        List<String> maTrangCanhBao = maLoi(wb, ImportIssueExportGenerator.TRANG_CANH_BAO);

        assertThat(maTrangChan)
                .containsExactlyInAnyOrder("IMP_PARENT_NOT_FOUND", "IMP_DUP_CODE",
                        "IMP_LUNAR_DATE_IS_SERIAL");
        assertThat(maTrangCanhBao)
                .containsExactlyInAnyOrder("IMP_MISSING_GIO", "IMP_TABOO_COLLISION");
        assertThat(maTrangChan).doesNotContainAnyElementsOf(maTrangCanhBao);
    }

    @Test
    @DisplayName("Không lỗi chặn thì trang vẫn còn, chỉ có dòng tiêu đề — vắng mặt là thông tin mơ hồ")
    void trangRongVanConMat() {
        List<LoiXuatExcel> chiCanhBao = List.of(
                XuatFixtures.canhBao("IMP_LONE_NODE", 12, "Mã cha", "Dòng 12 không có cha, mẹ,"
                        + " vợ/chồng nào.", Map.of()));

        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(chiCanhBao));

        assertThat(wb.getSheet(ImportIssueExportGenerator.TRANG_LOI_CHAN)).isNotNull();
        assertThat(XuatFixtures.soDong(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN))
                .as("chi con dong tieu de")
                .isZero();
        assertThat(XuatFixtures.moiO(wb)).contains("0 lỗi phải sửa");
    }

    @Test
    @DisplayName("Trang Tổng quan nêu hai con số tách bạch, không bao giờ nêu một tổng gộp")
    void tongQuanKhongCoConSoGop() {
        String tatCa = XuatFixtures.moiO(XuatFixtures.moi(XuatFixtures.sinh(mauDayDu())));

        assertThat(tatCa).contains("3 lỗi phải sửa");
        assertThat(tatCa).contains("2 điều nên xem lại");
        // 3 + 2 = 5. Mot dong "5 van de" la dung cai lam nguoi nhap bam bua.
        assertThat(tatCa).doesNotContain("5 vấn đề").doesNotContain("5 lỗi");
    }

    // -------------------------------------------------------------------------------------
    // Toa do va noi dung tung dong
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Tám cột, đúng thứ tự, và bốn cột đầu đủ để tìm lại dòng trong tệp gốc")
    void hopDongCot() {
        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(mauDayDu()));

        assertThat(XuatFixtures.dong(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN, 0))
                .containsExactly("Trang", "Dòng", "Mã", "Cột", "Vấn đề", "Gợi ý mã gần giống",
                        "Chi tiết", "Mã lỗi");

        int dongParent = viTri(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN,
                "IMP_PARENT_NOT_FOUND");
        List<String> o = XuatFixtures.dong(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN,
                dongParent);

        // Ten trang hien dung nhu tab trong tep gia pha, khong hien khoa NHAN_KHAU.
        assertThat(o.get(IssueExportColumn.TRANG.ordinal())).isEqualTo("Nhân khẩu");
        assertThat(o.get(IssueExportColumn.DONG.ordinal())).isEqualTo("7");
        assertThat(o.get(IssueExportColumn.MA.ordinal())).isEqualTo("AT-04-001");
        assertThat(o.get(IssueExportColumn.COT.ordinal())).isEqualTo("Mã cha");
        assertThat(o.get(IssueExportColumn.MA_LOI.ordinal())).isEqualTo("IMP_PARENT_NOT_FOUND");
    }

    @Test
    @DisplayName("Vấn đề của cả lô hiện \"Cả lô\" và bỏ trống số dòng, không bịa ra dòng 0")
    void vanDeCuaCaLo() {
        LoiXuatExcel caLo = new LoiXuatExcel("WARNING", "IMP_ROW_DISAPPEARED",
                ImportIssue.SHEET_LO, null, null, null,
                "Có 3 mã từng nhập lần trước nhưng vắng trong tệp lần này. Không xoá gì cả.",
                // LinkedHashMap chu khong phai Map.of: cot Chi tiet giu NGUYEN thu tu khoa cua
                // context, va moi luat deu dung LinkedHashMap. Map.of thi thu tu ngau nhien theo
                // ham bam, nen bai kiem se luc xanh luc do — dung cai kieu that bai khong ai truy
                // ra duoc.
                dayDu("soMaVang", 3, "maVang", List.of("AT-04-009", "AT-04-010")));

        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(List.of(caLo)));
        List<String> o = XuatFixtures.dong(wb, ImportIssueExportGenerator.TRANG_CANH_BAO, 1);

        assertThat(o.get(IssueExportColumn.TRANG.ordinal())).isEqualTo("Cả lô");
        assertThat(o.get(IssueExportColumn.DONG.ordinal())).isEmpty();
        assertThat(o.get(IssueExportColumn.CHI_TIET.ordinal()))
                .isEqualTo("Số mã vắng: 3 · Mã vắng: AT-04-009, AT-04-010");
    }

    @Test
    @DisplayName("Gợi ý mã gần giống có cột riêng — thứ duy nhất sửa được lỗi mà không phải mở sổ")
    void goiYCoCotRieng() {
        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(mauDayDu()));
        int dong = viTri(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN, "IMP_PARENT_NOT_FOUND");

        assertThat(XuatFixtures.dong(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN, dong)
                .get(IssueExportColumn.GOI_Y.ordinal()))
                .isEqualTo("AT-01-003, AT-01-013");

        // Va no KHONG bi lap lai trong cot Chi tiet — mot gia tri o hai cho thi nguoi doc phai
        // doan cai nao moi la cai dung.
        assertThat(XuatFixtures.dong(wb, ImportIssueExportGenerator.TRANG_LOI_CHAN, dong)
                .get(IssueExportColumn.CHI_TIET.ordinal()))
                .doesNotContain("AT-01-013")
                .contains(MA_LO_MAT);
    }

    // -------------------------------------------------------------------------------------
    // Dinh dang: bai hoc cua bo sinh mau
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Mọi cột định dạng Văn bản — 15/8 không bị Excel nuốt thành ngày dương lịch")
    void moiCotDinhDangVanBan() {
        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(mauDayDu()));

        for (String trang : List.of(ImportIssueExportGenerator.TRANG_LOI_CHAN,
                ImportIssueExportGenerator.TRANG_CANH_BAO)) {
            XSSFSheet sheet = wb.getSheet(trang);
            for (IssueExportColumn cot : IssueExportColumn.values()) {
                assertThat(sheet.getColumnStyle(cot.ordinal()))
                        .as("cot '%s' cua trang '%s' phai co kieu mac dinh", cot.tieuDe(), trang)
                        .isNotNull();
                assertThat(sheet.getColumnStyle(cot.ordinal()).getDataFormatString())
                        .as("cot '%s' cua trang '%s' phai la dinh dang Van ban", cot.tieuDe(), trang)
                        .isEqualTo("@");
            }
        }
    }

    @Test
    @DisplayName("Ô gốc 15/8 và mã 001 đi ra nguyên vẹn, không thành ngày dương cũng không mất số 0")
    void giuNguyenOGoc() {
        List<LoiXuatExcel> loi = List.of(
                XuatFixtures.chan("IMP_LUNAR_DATE_IS_SERIAL", 31, "Ngày mất âm",
                        "Ô Ngày mất âm đến server dưới dạng số sê-ri của Excel.",
                        Map.of("oGoc", "15/8")),
                XuatFixtures.chan("IMP_DUP_CODE", 44, "Mã", "Hai dòng cùng một mã.",
                        Map.of("ma", "001", "cacDong", List.of(44, 45))));

        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(loi));
        String tatCa = XuatFixtures.moiO(wb);

        assertThat(tatCa).contains("Ô gốc trong tệp: 15/8");
        assertThat(tatCa).contains("Mã: 001");
        assertThat(tatCa).doesNotContain("15/08/2026").doesNotContain("Mã: 1 ");
    }

    @Test
    @DisplayName("Dòng tiêu đề bị khoá và có bộ lọc — ở dòng thứ 30 vẫn biết mình đang đọc cột nào")
    void khoaDongTieuDe() {
        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(mauDayDu()));
        XSSFSheet sheet = wb.getSheet(ImportIssueExportGenerator.TRANG_LOI_CHAN);

        assertThat(sheet.getPaneInformation()).isNotNull();
        assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
        assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
    }

    @Test
    @DisplayName("Ô bắt đầu bằng dấu = không trở thành công thức khi người dùng mở tệp")
    void chongChenCongThuc() {
        List<LoiXuatExcel> loi = List.of(new LoiXuatExcel("BLOCKING", "IMP_MISSING_NAME",
                ImportIssue.SHEET_NHAN_KHAU, 9, "Họ tên", "=HYPERLINK(\"http://xau\",\"bấm\")",
                "=cmd", Map.of("ma", "=1+1")));

        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(loi));
        XSSFSheet sheet = wb.getSheet(ImportIssueExportGenerator.TRANG_LOI_CHAN);

        sheet.forEach(row -> row.forEach(cell -> assertThat(cell.getCellType())
                .as("o [%d,%d] khong duoc la cong thuc", cell.getRowIndex(),
                        cell.getColumnIndex())
                .isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA)));
    }

    @Test
    @DisplayName("Một ô trống trong context không làm đổ cả bản xuất")
    void giaTriNullTrongContextKhongLamDoBanXuat() {
        // Map.copyOf nem NPE khi co gia tri null, va context rat hay mang mot truong chua co gia
        // tri. O day cai bay ay khong chi lam hong mot dong — no lam hong CA TEP, tuc lam nguoi
        // nhap mat duong thoat, vi dung mot o trong.
        Map<String, Object> coNull = new LinkedHashMap<>();
        coNull.put("namSinhCon", 1940);
        coNull.put("namSinhCha", null);
        coNull.put("maCha", "AT-01-003");

        XSSFWorkbook wb = XuatFixtures.moi(XuatFixtures.sinh(List.of(
                XuatFixtures.chan("IMP_CHILD_BEFORE_PARENT", 9, "Năm sinh",
                        "Dòng 9 sinh trước cha.", coNull))));

        assertThat(XuatFixtures.moiO(wb))
                .contains("Năm sinh con: 1940")
                .contains("Mã cha/mẹ: AT-01-003");
    }

    // -------------------------------------------------------------------------------------
    // Tieng Viet co dau: trong noi dung VA trong ten tep
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Tên tiếng Việt có dấu sống sót trong nội dung và trong tên tệp")
    void tiengVietCoDauSongSot() {
        String ten = "Nguyễn Thị Đường";
        String tenHuy = "Đệ";
        List<LoiXuatExcel> loi = List.of(
                XuatFixtures.canhBao("IMP_SUSPECT_DUPLICATE", 18, "Họ tên",
                        "Dòng 18 (" + ten + ") có thể trùng với dòng AT-04-002 trong chính tệp này.",
                        Map.of("nghiNgo", List.of(new LinkedHashMap<>(Map.of(
                                "ref", "AT-04-002", "ten", ten, "doi", 4, "diem", 82,
                                "giaiThich", "trùng họ tên khi bỏ dấu"))))),
                XuatFixtures.canhBao("IMP_TABOO_COLLISION", 19, "Tên huý",
                        "Tên huý " + tenHuy + " trùng tên huý của một bậc trên đã có trong phả.",
                        Map.of("tenHuy", tenHuy,
                                "bacTrenId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));

        ExcelTemplateFile tep = XuatFixtures.sinh(loi, "Chi Đông Ngạc");
        String tatCa = XuatFixtures.moiO(XuatFixtures.moi(tep));

        // --- Trong noi dung ---
        assertThat(tatCa)
                .contains(ten)
                .contains("Tên huý đã gõ: " + tenHuy)
                .contains("trùng họ tên khi bỏ dấu")
                .contains("Chi Đông Ngạc");

        // --- Trong ten tep: ca hai dang cua Content-Disposition (RFC 5987) ---
        assertThat(tep.tenTep()).isEqualTo("Danh sách cần sửa - Chi Đông Ngạc - 2026-09-12.xlsx");
        String cd = tep.contentDisposition();
        // Ban du phong phai THUAN ASCII, ke ca chu "Đ" von khong tach duoc bang Normalizer.
        assertThat(cd).contains("filename=\"Danh sach can sua - Chi Dong Ngac - 2026-09-12.xlsx\"");
        assertThat(cd.substring(0, cd.indexOf("filename*=")))
                .as("phan truoc filename*= phai thuan ASCII, neu khong ca tieu de hong")
                .matches("\\p{ASCII}*");
        // Ban that: UTF-8 phan tram-hoa. "Đ" = D0 90 -> %C4%90.
        assertThat(cd).contains("filename*=UTF-8''").contains("%C4%90");
    }

    // -------------------------------------------------------------------------------------
    // Tien ich
    // -------------------------------------------------------------------------------------

    /** Ba lỗi chặn + hai cảnh báo, đủ các hình dạng {@code context} hay gặp. */
    private static List<LoiXuatExcel> mauDayDu() {
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("maKhongTimThay", MA_LO_MAT);
        parent.put("vaiTro", "cha");
        parent.put("goiY", List.of("AT-01-003", "AT-01-013"));

        return List.of(
                XuatFixtures.chan("IMP_PARENT_NOT_FOUND", 7, "Mã cha",
                        "Dòng 7 khai mã cha " + MA_LO_MAT + " nhưng không có dòng nào mang mã ấy.",
                        parent),
                XuatFixtures.chan("IMP_DUP_CODE", 12, "Mã",
                        "Mã AT-04-001 xuất hiện ở hai dòng.",
                        Map.of("ma", "AT-04-001", "cacDong", List.of(12, 13))),
                XuatFixtures.chan("IMP_LUNAR_DATE_IS_SERIAL", 31, "Ngày mất âm",
                        "Ô Ngày mất âm đến server dưới dạng số sê-ri của Excel.",
                        Map.of("oGoc", "45123")),
                XuatFixtures.canhBao("IMP_MISSING_GIO", 21, "Ngày mất âm",
                        "Người đã mất mà trống Ngày mất âm — sẽ không bao giờ được nhắc giỗ.",
                        Map.of()),
                XuatFixtures.canhBao("IMP_TABOO_COLLISION", 22, "Tên huý",
                        "Tên huý Đệ trùng tên huý của một bậc trên đã có trong phả.",
                        Map.of("tenHuy", "Đệ",
                                "bacTrenId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")));
    }

    /** {@link LinkedHashMap} giữ thứ tự khai báo — xem ghi chú ở {@link #vanDeCuaCaLo()}. */
    private static Map<String, Object> dayDu(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static List<String> maLoi(XSSFWorkbook wb, String trang) {
        List<String> ma = new ArrayList<>();
        for (int r = 1; r <= XuatFixtures.soDong(wb, trang); r++) {
            ma.add(XuatFixtures.dong(wb, trang, r).get(IssueExportColumn.MA_LOI.ordinal()));
        }
        return ma;
    }

    private static int viTri(XSSFWorkbook wb, String trang, String code) {
        for (int r = 1; r <= XuatFixtures.soDong(wb, trang); r++) {
            if (code.equals(XuatFixtures.dong(wb, trang, r)
                    .get(IssueExportColumn.MA_LOI.ordinal()))) {
                return r;
            }
        }
        throw new AssertionError("Khong tim thay " + code + " trong trang " + trang);
    }
}

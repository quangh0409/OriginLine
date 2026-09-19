package vn.giapha.dataimport.infrastructure.excel.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportIssue;

/**
 * <b>Không một giá trị đọc từ phả nào có mặt trong tệp tải về — kể cả khi dòng dữ liệu do một bản
 * nhị phân cũ hơn ghi ra.</b>
 *
 * <h2>Bài kiểm này đo cái gì, và vì sao nó không trùng với {@code ImportIssuePrivacyIT}</h2>
 * Bộ kia chứng minh <b>bên ghi</b> đã sạch: {@code import_issue} trên PostgreSQL thật không mang
 * tên hay năm sinh người trong phả. Bộ này chứng minh <b>bên đọc</b> không phụ thuộc vào điều đó.
 *
 * <p>Hai bên bị ngăn cách bởi một cơ sở dữ liệu, tức bởi <b>thời gian</b>: một lô đã kiểm từ trước
 * lần vá riêng tư gần nhất, một bản khôi phục từ sao lưu, một triển khai cuốn chiếu — cả ba đều cho
 * ra những dòng {@code context} mang hình dạng <b>cũ</b>. Vì vậy mọi ca ở đây <b>cố tình nhồi khoá
 * cũ vào</b>: {@code ten}, {@code doi}, {@code namSinh}, {@code tenBacTren}, {@code giaiThich} trên
 * vế "hồ sơ trong phả" — đúng những khoá bộ kiểm hôm nay không còn ghi nữa. Nếu bộ xuất chỉ duyệt
 * map rồi nối thành chữ, chúng sẽ có mặt trong tệp.</p>
 *
 * <h2>Và phép quét đi qua MỌI ô của MỌI trang</h2>
 * Không kiểm "cột Chi tiết không chứa X". Giữa một map đã lọc và một tệp trên máy Trưởng chi còn
 * nhiều chỗ để rò: một câu trong trang Tổng quan, một nhãn cột, tên của chính trang tính. Quét toàn
 * bộ bắt được cả ba mà không cần biết trước chúng ở đâu.
 *
 * <h2>Ca đối chứng là bắt buộc</h2>
 * Mọi khẳng định "không thấy tên" đều có thể xanh vì <b>lý do sai</b> — tệp rỗng, hàm ném ngoại lệ
 * bị nuốt, mã lỗi viết sai nên không dòng nào được ghi. Vì vậy mỗi ca đều kèm một khẳng định
 * <b>khẳng định</b>: {@code personId} và dữ liệu của chính người nhập <b>phải</b> có mặt.
 *
 * <p>Và lý do bài kiểm này gắt hơn mức thường thấy: <b>một tệp Excel đã tải về không có đường thu
 * hồi.</b> Nó đi tiếp qua Zalo, qua email, qua USB, và nằm lại trên máy người khác.</p>
 */
class IssueExportLeakTest {

    // Nhung manh du lieu doc TU PHA, gieo vao context duoi hinh dang CU. Khong mot cai nao duoc
    // xuat hien trong tep.
    private static final String TEN_TRONG_PHA = "Nguyễn Văn Tuân";
    private static final String TEN_HUY_TRONG_PHA = "Nguyễn Đình Lựu";
    private static final String NAM_SINH_TRONG_PHA = "1937";
    private static final String NGUYEN_QUAN_TRONG_PHA = "Kinh Bắc Thượng";
    private static final String DIEN_THOAI_TRONG_PHA = "0900000001";
    private static final String GIAI_THICH_CU = "trùng năm sinh 1937";

    /** Khoá trỏ sang phả. Nó <b>phải</b> có mặt — không có nó thì cảnh báo thành một câu cụt. */
    private static final String PERSON_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String BAC_TREN_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    // Du lieu cua CHINH NGUOI NHAP — phai giu, giau di chi lam ho khong doi chieu duoc.
    private static final String TEN_NGUOI_NHAP_GO = "Nguyen Van Tuan";
    private static final String MA_NGUOI_NHAP_GO = "AT-04-001";

    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Nghi trùng: vế trong phả ra đúng khoá và điểm; tên, đời, giải thích cũ đều bị cắt")
    void nghiTrungVeTrongPhaChiRaKhoa() {
        // Hinh dang CU cua mot ung vien trong pha: co ca ten, doi, va mot loi giai thich mang
        // gia tri truong. Bo kiem hom nay khong con ghi nhu vay — nhung dong du lieu nay thi co.
        Map<String, Object> trongPha = new LinkedHashMap<>();
        trongPha.put("personId", PERSON_ID);
        trongPha.put("diem", 86);
        trongPha.put("nguon", "TREE");
        trongPha.put("tinHieu", "trùng họ tên khi bỏ dấu");
        trongPha.put("ten", TEN_TRONG_PHA);
        trongPha.put("doi", 4);
        trongPha.put("namSinh", NAM_SINH_TRONG_PHA);
        trongPha.put("nguyenQuan", NGUYEN_QUAN_TRONG_PHA);
        trongPha.put("dienThoai", DIEN_THOAI_TRONG_PHA);
        trongPha.put("giaiThich", GIAI_THICH_CU);

        // Ve trong TEP: toan bo la thu nguoi nhap vua go, nen giu nguyen.
        Map<String, Object> trongTep = new LinkedHashMap<>();
        trongTep.put("ref", "AT-04-002");
        trongTep.put("ten", TEN_NGUOI_NHAP_GO);
        trongTep.put("doi", 4);
        trongTep.put("diem", 74);
        trongTep.put("giaiThich", "trùng họ tên khi bỏ dấu");
        trongTep.put("nguon", "FILE");

        String tatCa = xuat(XuatFixtures.canhBao("IMP_SUSPECT_DUPLICATE", 18, "Họ tên",
                "Dòng 18 (" + TEN_NGUOI_NHAP_GO + ") có thể trùng với 1 hồ sơ đã có trong phả.",
                Map.of("nghiNgo", List.of(trongPha, trongTep))));

        khongMotManhNaoCuaPha(tatCa);

        // --- ...nhung KHOA thi co, va du lieu cua chinh nguoi nhap cung co ---
        assertThat(tatCa)
                .as("khong co personId thi nguoi doi chieu khong mo duoc ho so de xem phan minh"
                        + " duoc phep xem")
                .contains(PERSON_ID)
                .contains("86 điểm")
                .contains(TEN_NGUOI_NHAP_GO)
                .contains("AT-04-002");
    }

    @Test
    @DisplayName("Kỵ húy: tên huý người nhập gõ thì hiện; tên và đời của bậc trên thì không")
    void kyHuyChiRaOCuaNguoiNhap() {
        Map<String, Object> cu = new LinkedHashMap<>();
        cu.put("tenHuy", "Đệ");
        cu.put("bacTrenId", BAC_TREN_ID);
        // Hinh dang CU: ten va doi cua bac tren nam thang trong context.
        cu.put("tenBacTren", TEN_TRONG_PHA);
        cu.put("tenHuyBacTren", TEN_HUY_TRONG_PHA);
        cu.put("doiBacTren", 2);

        String tatCa = xuat(XuatFixtures.canhBao("IMP_TABOO_COLLISION", 22, "Tên huý",
                "Tên huý Đệ trùng tên huý của một bậc trên đã có trong phả.", cu));

        khongMotManhNaoCuaPha(tatCa);
        assertThat(tatCa)
                .contains("Tên huý đã gõ: Đệ")
                .contains("Hồ sơ bậc trên: " + BAC_TREN_ID);
    }

    @Test
    @DisplayName("Mã lỗi thường: khoá lạ trong context bị bỏ, khoá đã khai vẫn ra đủ")
    void khoaLaBiBoDuChuaTungAiLuongTruoc() {
        Map<String, Object> cu = new LinkedHashMap<>();
        cu.put("maKhongTimThay", "AT-01-OO3");
        cu.put("vaiTro", "cha");
        cu.put("goiY", List.of("AT-01-003"));
        // Mot khoa "tien ich" mot ban cu tung them vao cho de doc — day chinh la lop loi vua sua.
        cu.put("tenCha", TEN_TRONG_PHA);
        cu.put("namSinhCha", NAM_SINH_TRONG_PHA);

        String tatCa = xuat(XuatFixtures.chan("IMP_PARENT_NOT_FOUND", 7, "Mã cha",
                "Dòng 7 khai mã cha AT-01-OO3 nhưng không có dòng nào mang mã ấy.", cu));

        khongMotManhNaoCuaPha(tatCa);
        assertThat(tatCa)
                .contains("Mã không tìm thấy: AT-01-OO3")
                .contains("Vai trò: cha")
                .contains("AT-01-003");
    }

    @Test
    @DisplayName("Mã lỗi CHƯA được khai ra cột Chi tiết rỗng — danh sách trắng, không phải danh sách đen")
    void maChuaKhaiRaRong() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("ten", TEN_TRONG_PHA);
        context.put("namSinh", NAM_SINH_TRONG_PHA);

        String tatCa = xuat(new LoiXuatExcel("WARNING", "IMP_MA_MOI_CHUA_AI_KHAI",
                ImportIssue.SHEET_NHAN_KHAU, 5, "Họ tên", MA_NGUOI_NHAP_GO,
                "Một luật mới, chưa ai khai khoá context của nó.", context));

        khongMotManhNaoCuaPha(tatCa);
        // Doi chung: dong VAN duoc ghi ra, chi rieng cot Chi tiet trong. Neu ca dong bien mat thi
        // ca tren xanh vi ly do sai.
        assertThat(tatCa)
                .contains("IMP_MA_MOI_CHUA_AI_KHAI")
                .contains("Một luật mới, chưa ai khai khoá context của nó.")
                .contains(MA_NGUOI_NHAP_GO);
    }

    @Test
    @DisplayName("Khoá ngoài danh sách bị bỏ LẶNG LẼ — không ghi \"đã ẩn 1 trường\"")
    void batLangLeKhongThongBao() {
        Map<String, Object> cu = new LinkedHashMap<>();
        cu.put("ma", "AT-04-001");
        cu.put("dienThoai", DIEN_THOAI_TRONG_PHA);

        String tatCa = xuat(XuatFixtures.chan("IMP_UNKNOWN_PLACE_CODE", 15, "Mã nguyên quán",
                "Mã tỉnh không có trong danh mục.", cu));

        khongMotManhNaoCuaPha(tatCa);
        // Mot cau "da an" tu no la mot ro ri: no xac nhan rang ho so ben kia CO truong do.
        assertThat(tatCa)
                .doesNotContain("đã ẩn")
                .doesNotContain("bị ẩn")
                .doesNotContain("(ẩn)");
        assertThat(tatCa).contains("Mã: AT-04-001");
    }

    // -------------------------------------------------------------------------------------

    /** Sinh tệp từ đúng một dòng lỗi rồi trả về <b>mọi ô của mọi trang</b>, nối thành một chuỗi. */
    private static String xuat(LoiXuatExcel loi) {
        return XuatFixtures.moiO(XuatFixtures.moi(XuatFixtures.sinh(List.of(loi))));
    }

    private static void khongMotManhNaoCuaPha(String tatCa) {
        assertThat(tatCa)
                .as("khong mot truong nhan khau nao doc tu pha duoc ra khoi he thong")
                .doesNotContain(TEN_TRONG_PHA)
                .doesNotContain(TEN_HUY_TRONG_PHA)
                .doesNotContain(NAM_SINH_TRONG_PHA)
                .doesNotContain(NGUYEN_QUAN_TRONG_PHA)
                .doesNotContain(DIEN_THOAI_TRONG_PHA)
                .doesNotContain(GIAI_THICH_CU);
    }
}

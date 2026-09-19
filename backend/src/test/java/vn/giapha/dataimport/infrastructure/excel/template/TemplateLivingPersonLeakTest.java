package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.shared.vo.Gender;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>Không một mảnh dữ liệu ngoài thứ bộ lọc riêng tư giao ra được có mặt trong tệp tải về.</b>
 *
 * <h2>Bài kiểm này đo cái gì, sau khi phép lọc chuyển sang {@code genealogy}</h2>
 * Trước đây {@code NhanKhauDaCo} tự che theo một hằng số {@code COT_TANG_1} chép tay, nên bộ này
 * kiểm chính phép che ấy. Nay phép che nằm ở {@code PrivacyTierService} — một nơi duy nhất cho cả
 * hệ thống — và kiểm nó là việc của {@code PersonDisclosureServiceTest} (đơn vị) và
 * {@code ImportTemplateBranchIT} (CSDL thật, người gọi thật).
 *
 * <p>Việc còn lại của bộ này, và nó không hề nhỏ: chứng minh <b>bộ sinh mẫu không thêm gì vào</b>.
 * Giữa "hồ sơ đã lọc" và "tệp trên máy Trưởng chi" vẫn còn nhiều chỗ để rò — một trang phụ chép
 * lại dữ liệu, một dòng ví dụ dựng từ hồ sơ thật, một ô ghi chú "sinh 1962" ở trang Hướng dẫn, và
 * nhất là <b>những cột mà {@code dataimport} tự chở bên ngoài hồ sơ</b> (mã nguyên quán, năm cưới,
 * lý do ly hôn). Quét mọi ô của mọi trang bắt được cả bốn, và không cần biết trước chúng xuất hiện
 * ở đâu.</p>
 *
 * <p>Một tệp Excel tải về không có đường thu hồi: nó đi tiếp qua Zalo, qua email, qua USB. Đây là
 * lý do bài kiểm này gắt hơn mức thường thấy.</p>
 */
class TemplateLivingPersonLeakTest {

    /**
     * Những cột <b>duy nhất</b> còn dữ liệu khi hồ sơ đi vào là hồ sơ của một người còn sống nhìn
     * bởi một thành viên/Trưởng chi.
     *
     * <p><b>Đây không phải một bản luật.</b> Nó là <i>hệ quả quan sát được</i> của việc hồ sơ ấy
     * không chở gì ngoài tên/giới/đời, cộng với các cột mã do chính {@code dataimport} sinh ra
     * (mã, mã cha, mã mẹ, quan hệ, kế tự). Danh sách nằm trong <b>test</b> chứ không trong mã sản
     * phẩm, nên thêm một cột vào {@link ImportColumn} mà quên nghĩ về riêng tư thì bộ này đỏ — chứ
     * không phải tệp tải về lặng lẽ có thêm một cột dữ liệu.</p>
     */
    private static final Set<ImportColumn> COT_CON_DU_LIEU = EnumSet.of(
            ImportColumn.MA,
            ImportColumn.HO_TEN,
            ImportColumn.GIOI,
            ImportColumn.DOI,
            ImportColumn.MA_CHA,
            ImportColumn.MA_ME,
            ImportColumn.QUAN_HE,
            ImportColumn.CON_SONG,
            ImportColumn.KE_TU_CHO_AI,
            ImportColumn.LOAI_KE_TU);

    /** Những mảnh dữ liệu Tầng 2/3 được cố tình nhồi vào đầu vào để xem chúng có lọt không. */
    private static final String NAM_SINH_MAT = "1962";
    private static final String NGUYEN_QUAN_MAT = "Làng Kim Bảng, phủ Lý Nhân";
    private static final String TEN_HUY_MAT = "Tên huý tuyệt mật";
    private static final String THUY_HIEU_MAT = "Thuỵ hiệu tuyệt mật";
    private static final String HAN_NOM_MAT = "秘密";
    private static final String MA_QUAN_MAT = "ZZ";

    @Test
    @DisplayName("Hồ sơ Tầng 1: mọi cột ngoài phần mã và tên/giới/đời đều trống, không cần ai nhớ gì")
    void hoSoTang1ThiKhongOtNaoNgoaiTang1CoDuLieu() {
        // Ma nguyen quan "ZZ" di BEN NGOAI ho so — no la cot cua dataimport (V9), khong phai cua
        // aggregate genealogy. Day chinh la duong ro con lai, va constructor bit no.
        NhanKhauDaCo song = new NhanKhauDaCo(
                HoSoGia.conSongTang1("Nguyễn Thị Sống").gioi(Gender.FEMALE).doi(5).xong(),
                "AT-05-001", "AT-04-001", "AT-04-002", "Con ruột", MA_QUAN_MAT, null, null);

        for (ImportColumn cot : ImportColumn.values()) {
            if (COT_CON_DU_LIEU.contains(cot)) {
                continue;
            }
            assertThat(song.giaTri(cot))
                    .as("cot %s KHONG duoc co du lieu khi bo loc khong giao gi cho no", cot.tieuDe())
                    .isNull();
        }
        assertThat(song.giaTri(ImportColumn.MA_NGUYEN_QUAN))
                .as("ma nguyen quan di ngoai ho so, nhung van phai che theo ket luan cua bo loc")
                .isNull();
        // Tang 1 thi giu — neu khong, Truong chi phai khai lai tu dau va moi ma deu lech.
        assertThat(song.giaTri(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Thị Sống");
        assertThat(song.giaTri(ImportColumn.GIOI)).isEqualTo("Nữ");
        assertThat(song.giaTri(ImportColumn.CON_SONG)).isEqualTo("Có");
        assertThat(song.giaTri(ImportColumn.MA_CHA)).isEqualTo("AT-04-001");
    }

    @Test
    @DisplayName("Hồ sơ đầy đủ thì mọi cột đều ra — nếu không, ca trên xanh vì lý do sai")
    void hoSoDayDuThiMoiCotDeuRa() {
        NhanKhauDaCo khuat = new NhanKhauDaCo(
                HoSoGia.daKhuat("Nguyễn Văn Đức").doi(2).namSinh(1962)
                        .ngayGio(LunarDate.of(2001, 12, 29)).huy(TEN_HUY_MAT).thuy(THUY_HIEU_MAT)
                        .hanNom(HAN_NOM_MAT).nguyenQuan(NGUYEN_QUAN_MAT).xong(),
                "AT-02-001", "AT-01-001", "AT-01-002", "Con ruột", MA_QUAN_MAT,
                "AT-01-001", "Đích tôn");

        for (ImportColumn cot : ImportColumn.values()) {
            assertThat(khuat.giaTri(cot))
                    .as("nguoi da khuat la du lieu cong khai (BA v2 §10) — cot %s phai co", cot.tieuDe())
                    .isNotNull();
        }
        assertThat(khuat.giaTri(ImportColumn.NAM_SINH)).isEqualTo(NAM_SINH_MAT);
        assertThat(khuat.giaTri(ImportColumn.NGAY_MAT_AM)).isEqualTo("29/12/2001");
        assertThat(khuat.giaTri(ImportColumn.MA_NGUYEN_QUAN)).isEqualTo(MA_QUAN_MAT);
        assertThat(khuat.phaiCheTangTren()).isFalse();
    }

    @Test
    @DisplayName("Không có hồ sơ đã lọc thì không dựng nổi một dòng — không còn lối vào dữ liệu thô")
    void khongCoHoSoThiKhongDungDuocDong() {
        // Truoc day co the goi new NhanKhauDaCo("AT-05-001", "Nguyen Thi Song", ..., "1962", ...)
        // voi du lieu doc thang tu SQL. Loi dung ay khong con ton tai: phan du lieu cua pha chi vao
        // duoc qua DisclosedPerson, ma chi bo loc moi phat ra duoc.
        assertThatThrownBy(() -> new NhanKhauDaCo(null, "AT-05-001", null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("DA LOC");
    }

    @Test
    @DisplayName("Hôn phối: chi tiết bị che theo kết luận của bộ lọc, không theo luật riêng của tệp")
    void honPhoiCheTheoKetLuanCuaBoLoc() {
        HonPhoiDaCo che = new HonPhoiDaCo("AT-05-001", "AT-05-002", "2", "1988", "2005",
                "Ly hôn", false);

        assertThat(che.tuNam()).isNull();
        assertThat(che.denNam()).isNull();
        assertThat(che.lyDoKetThuc()).isNull();
        // Quan he loi o lai: thieu bac thi danh xung con cua cac ba tinh sai.
        assertThat(che.maChong()).isEqualTo("AT-05-001");
        assertThat(che.bac()).isEqualTo("2");
        assertThat(che.phaiCheTangTren()).isTrue();

        HonPhoiDaCo hien = new HonPhoiDaCo("AT-02-001", "AT-02-014", "1", "1938", "1945",
                "Qua đời", true);
        assertThat(hien.tuNam()).isEqualTo("1938");
        assertThat(hien.phaiCheTangTren()).isFalse();
    }

    @Test
    @DisplayName("Quét TOÀN BỘ ô của TOÀN BỘ trang trong tệp sinh ra: không mảnh nào lọt")
    void quetCaTepKhongThayMotManhNao() {
        FakeBranchRoster roster = new FakeBranchRoster()
                .chi("Chi Ất", "goc.chi_at")
                .diaDanh(new MaDiaDanh("VN", "Việt Nam"))
                .them(new NhanKhauDaCo(
                        HoSoGia.conSongTang1("Nguyễn Thị Sống").gioi(Gender.FEMALE).doi(5).xong(),
                        "AT-05-001", "AT-04-001", "AT-04-002", "Con ruột", MA_QUAN_MAT, null, null))
                .them(new HonPhoiDaCo("AT-05-001", "AT-05-002", "2", "1988", "2005", "Ly hôn",
                        false));

        List<String> oChu = moiOChu(ImportTemplateRoundTripTest.sinh(roster));

        assertThat(oChu).isNotEmpty();
        for (String mat : List.of(NAM_SINH_MAT, NGUYEN_QUAN_MAT, TEN_HUY_MAT, THUY_HIEU_MAT,
                HAN_NOM_MAT, MA_QUAN_MAT, "1988", "2005")) {
            assertThat(oChu)
                    .as("manh du lieu '%s' KHONG duoc bo loc giao ra, nen no KHONG duoc co mat o"
                            + " bat ky o nao cua tep tai ve", mat)
                    .noneMatch(o -> o.contains(mat));
        }
        // Va Tang 1 thi phai co mat, neu khong bai kiem tren "xanh" vi ly do sai.
        assertThat(oChu).anyMatch(o -> o.contains("Nguyễn Thị Sống"));
        assertThat(oChu).anyMatch(o -> o.contains("AT-05-001"));
    }

    @Test
    @DisplayName("Dòng ví dụ người còn sống trên trang Ví dụ cũng đi qua đúng lối dựng ấy")
    void dongViDuCungBiChe() {
        // VI_DU_CON_SONG dung qua NhanKhauDaCo voi mot ho so Tang 1 hu cau, va ma nguyen quan "VN"
        // kem theo no bi che ngay trong constructor — dong vi du vi the trong dung o nhung o ma
        // tep that cung trong.
        List<String> oChu = moiOChu(ImportTemplateRoundTripTest.sinh(
                new FakeBranchRoster().chi("Chi Ất", "goc.chi_at")));

        assertThat(oChu).anyMatch(o -> o.contains("Nguyễn Thị Hoa"));
        assertThat(oChu).noneMatch(o -> o.contains("1978"));
    }

    // -------------------------------------------------------------------------------------

    /** Mọi ô chữ của mọi trang, kể cả trang ẩn và trang hướng dẫn. */
    private static List<String> moiOChu(byte[] file) {
        List<String> ket = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                ket.add(sheet.getSheetName());
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            ket.add(cell.getStringCellValue());
                        } else if (cell.getCellType() == CellType.NUMERIC) {
                            ket.add(String.valueOf(cell.getNumericCellValue()));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new AssertionError("Khong doc lai duoc tep vua sinh", ex);
        }
        return ket;
    }
}

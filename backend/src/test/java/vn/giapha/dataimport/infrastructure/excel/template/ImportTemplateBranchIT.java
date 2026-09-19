package vn.giapha.dataimport.infrastructure.excel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.dataimport.domain.ImportColumn;
import vn.giapha.dataimport.domain.MarriageColumn;
import vn.giapha.dataimport.domain.ParsedWorkbook;
import vn.giapha.dataimport.domain.RawRow;
import vn.giapha.dataimport.infrastructure.excel.XlsxWorkbookReader;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * Mẫu <b>cho một chi cụ thể</b>, dựng trên cơ sở dữ liệu thật.
 *
 * <p>{@link ImportTemplateRoundTripTest} đã chứng minh hợp đồng cột và mã hoá ký tự bằng một cổng
 * giả. Bộ này kiểm chuyện mà cổng giả không kiểm được: truy vấn có lấy <b>đúng người</b> không, và
 * ranh giới riêng tư có đứng vững khi dữ liệu đi thẳng từ bảng {@code person} ra tệp không.</p>
 *
 * <p><b>Người gọi là một phần của bài kiểm.</b> Từ khi phép lọc chuyển sang
 * {@code PersonDisclosureService}, nội dung tệp phụ thuộc vai của người bấm nút Tải mẫu — nên mọi
 * ca ở đây đều chạy dưới một danh tính cụ thể, và {@link #haiVaiKhacNhauNhanMauKhacNhau()} chứng
 * minh hai vai khác nhau trên <b>cùng một chi</b> nhận về hai tệp khác nhau.</p>
 */
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportTemplateBranchIT extends AbstractIntegrationTest {

    @Autowired
    private ImportTemplateGenerator generator;

    @Autowired
    private BranchRosterPort roster;

    private final XlsxWorkbookReader reader = new XlsxWorkbookReader();

    /**
     * Trưởng chi là người tải mẫu về trong đời thật, nên đó là danh tính mặc định của bộ này.
     *
     * <p>Chạy như Khách thì <b>không một người còn sống nào</b> có mặt trong tệp (BA v2 §10) và
     * các ca dưới sẽ đỏ vì một lý do đúng nhưng không phải lý do đang được kiểm. Lớp cha xoá
     * {@code SecurityContext} trước mỗi ca, nên phải đặt lại ở đây.</p>
     */
    @org.junit.jupiter.api.BeforeEach
    void dangNhapTruongChi() {
        authenticateAs("sub-truong-chi", "BRANCH_HEAD");
    }

    @Test
    @DisplayName("Người đã khuất ra đủ dữ liệu; người còn sống chỉ ra Tầng 1")
    void chiRaTang1ChoNguoiConSong() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID cu = nguoi(chi, "AT-02-001", "Nguyễn Văn Đức", "MALE", 2, false, 1915,
                "Làng Đông Ngạc, huyện Từ Liêm", "VN");
        huy(cu, "Đức");
        thuy(cu, "Phúc Trung");
        UUID song = nguoi(chi, "AT-05-010", "Nguyễn Thị Sống", "FEMALE", 5, true, 1962,
                "Làng Kim Bảng, phủ Lý Nhân", "VN");
        huy(song, "Tên huý của người sống");

        ParsedWorkbook wb = doc(chi);

        RawRow rCu = dong(wb, "AT-02-001");
        assertThat(rCu.get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Văn Đức");
        assertThat(rCu.get(ImportColumn.NAM_SINH)).isEqualTo("1915");
        assertThat(rCu.get(ImportColumn.NGUYEN_QUAN)).isEqualTo("Làng Đông Ngạc, huyện Từ Liêm");
        assertThat(rCu.get(ImportColumn.MA_NGUYEN_QUAN)).isEqualTo("VN");
        assertThat(rCu.get(ImportColumn.TEN_HUY)).isEqualTo("Đức");
        assertThat(rCu.get(ImportColumn.THUY_HIEU)).isEqualTo("Phúc Trung");
        assertThat(rCu.get(ImportColumn.NGAY_MAT_AM)).isEqualTo("10/5/1980");
        assertThat(rCu.get(ImportColumn.CON_SONG)).isEqualTo("Không");

        RawRow rSong = dong(wb, "AT-05-010");
        // Tang 1 co mat: khong co no thi Truong chi khai lai tu dau va moi ma deu lech.
        assertThat(rSong.get(ImportColumn.HO_TEN)).isEqualTo("Nguyễn Thị Sống");
        assertThat(rSong.get(ImportColumn.GIOI)).isEqualTo("Nữ");
        assertThat(rSong.get(ImportColumn.DOI)).isEqualTo("5");
        assertThat(rSong.get(ImportColumn.CON_SONG)).isEqualTo("Có");
        // Va khong gi hon the.
        assertThat(rSong.get(ImportColumn.NAM_SINH)).isNull();
        assertThat(rSong.get(ImportColumn.NGUYEN_QUAN)).isNull();
        assertThat(rSong.get(ImportColumn.MA_NGUYEN_QUAN)).isNull();
        assertThat(rSong.get(ImportColumn.TEN_HUY)).isNull();
        assertThat(rSong.get(ImportColumn.NGAY_MAT_AM)).isNull();
    }

    @Test
    @DisplayName("Mã cha / mã mẹ được điền sẵn, và con nuôi vẫn là con nuôi sau một vòng")
    void maChaMeDuocDienSan() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID cha = nguoi(chi, "AT-02-001", "Nguyễn Văn Đức", "MALE", 2, false, 1915, null, null);
        UUID me = nguoi(chi, "AT-02-014", "Trần Thị Mão", "FEMALE", 2, false, 1918, null, null);
        UUID con = nguoi(chi, "AT-03-005", "Nguyễn Văn Bảo", "MALE", 3, false, 1940, null, null);
        canh(cha, con, "PARENT_ADOPT");
        canh(me, con, "PARENT_BIO");

        RawRow r = dong(doc(chi), "AT-03-005");

        assertThat(r.get(ImportColumn.MA_CHA)).isEqualTo("AT-02-001");
        assertThat(r.get(ImportColumn.MA_ME)).isEqualTo("AT-02-014");
        assertThat(r.get(ImportColumn.QUAN_HE)).isEqualTo("Con nuôi");
    }

    @Test
    @DisplayName("Hôn phối: vai chồng/vợ chia theo giới, không theo chiều lưu của cạnh SPOUSE")
    void vaiChongVoTheoGioi() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID ong = nguoi(chi, "AT-02-001", "Nguyễn Văn Đức", "MALE", 2, false, 1915, null, null);
        UUID ba = nguoi(chi, "AT-02-014", "Trần Thị Mão", "FEMALE", 2, false, 1918, null, null);
        // Co tinh luu NGUOC: from = ba vo. Canh SPOUSE khong co quy uoc chong-truoc-vo-sau.
        honPhoi(ba, ong, 1);

        ParsedWorkbook wb = doc(chi);
        assertThat(wb.marriageRows()).hasSize(1);
        RawRow m = wb.marriageRows().get(0);

        assertThat(m.get(MarriageColumn.MA_CHONG)).isEqualTo("AT-02-001");
        assertThat(m.get(MarriageColumn.MA_VO)).isEqualTo("AT-02-014");
        assertThat(m.get(MarriageColumn.BAC)).isEqualTo("1");
    }

    @Test
    @DisplayName("Người đã xoá mềm và người của chi khác không có mặt trong tệp")
    void khongLayNguoiXoaMemVaChiKhac() {
        UUID chiAt = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", null, "CHI");
        nguoi(chiAt, "AT-02-001", "Nguyễn Văn Đức", "MALE", 2, false, 1915, null, null);
        UUID daXoa = nguoi(chiAt, "AT-02-002", "Nguyễn Văn Xoá", "MALE", 2, false, 1917, null, null);
        jdbc.update("UPDATE person SET is_deleted = TRUE, deleted_at = now() WHERE id = ?", daXoa);
        nguoi(chiGiap, "GI-02-001", "Nguyễn Văn Giáp", "MALE", 2, false, 1916, null, null);

        ParsedWorkbook wb = doc(chiAt);

        List<String> ma = wb.personRows().stream().map(r -> r.get(ImportColumn.MA)).toList();
        assertThat(ma).containsExactly("AT-02-001");
    }

    @Test
    @DisplayName("Danh mục place_division đã nạp: cột Mã nguyên quán có danh sách chọn thật")
    void danhMucDaNapThiCoDanhSach() {
        // V9 gieo 13 ma quoc gia (VN, US, ...). Danh muc 34 tinh thi CO Y chua nap.
        assertThat(roster.danhMucDiaDanh()).isNotEmpty();
        assertThat(roster.danhMucDiaDanh()).anyMatch(d -> "VN".equals(d.ma()));
        // Va tep sinh ra co vung dat ten cho cot ay — day la nhanh "danh muc DA nap", doi lai voi
        // nhanh rong ma ImportTemplateWorkbookStructureTest kiem.
        assertThat(generator.sinh(insertBranch("Chi Kỷ", "goc.chi_ky", null, "CHI"))
                .kichThuoc()).isPositive();
    }

    @Test
    @DisplayName("Chi chưa có ai: mẫu trắng vẫn đọc lại được")
    void chiChuaCoAi() {
        UUID chi = insertBranch("Chi Mậu", "goc.chi_mau", null, "CHI");

        ParsedWorkbook wb = doc(chi);

        assertThat(wb.personRows()).isEmpty();
        assertThat(wb.personHeaders()).hasSize(ImportColumn.values().length);
    }

    @Test
    @DisplayName("Cùng một chi, ba người gọi khác vai nhận ba mẫu khác nhau")
    void haiVaiKhacNhauNhanMauKhacNhau() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID song = nguoi(chi, "AT-05-010", "Nguyễn Thị Sống", "FEMALE", 5, true, 1962,
                "Làng Kim Bảng, phủ Lý Nhân", "VN");
        huy(song, "Tên huý của người sống");
        // Truong chi THAT: co tai khoan va co phan cong dung chi ay. Co phan cong van KHONG mo
        // khoi du lieu ngoai nhom — do la ket luan cua PrivacyTierService, khong phai cua tep nay.
        UUID taiKhoan = insertAppUser("sub-truong-chi", null);
        assignBranchRole(taiKhoan, "BRANCH_HEAD", chi);

        RawRow cuaTruongChi = dong(doc(chi), "AT-05-010");

        authenticateAs("sub-hoi-dong", "COUNCIL");
        RawRow cuaHoiDong = dong(doc(chi), "AT-05-010");

        // Tang 1: hai ben giong het nhau.
        assertThat(cuaTruongChi.get(ImportColumn.HO_TEN))
                .isEqualTo(cuaHoiDong.get(ImportColumn.HO_TEN))
                .isEqualTo("Nguyễn Thị Sống");
        assertThat(cuaTruongChi.get(ImportColumn.CON_SONG)).isEqualTo("Có");

        // Ngoai Tang 1: khac han nhau — va day la toan bo ly do mat tien nhan NGUOI GOI.
        assertThat(cuaTruongChi.get(ImportColumn.NAM_SINH)).isNull();
        assertThat(cuaHoiDong.get(ImportColumn.NAM_SINH)).isEqualTo("1962");
        assertThat(cuaTruongChi.get(ImportColumn.NGUYEN_QUAN)).isNull();
        assertThat(cuaHoiDong.get(ImportColumn.NGUYEN_QUAN))
                .isEqualTo("Làng Kim Bảng, phủ Lý Nhân");
        assertThat(cuaTruongChi.get(ImportColumn.TEN_HUY)).isNull();
        assertThat(cuaHoiDong.get(ImportColumn.TEN_HUY)).isEqualTo("Tên huý của người sống");
        // Ma nguyen quan khong nam trong aggregate cua genealogy, nhung van che theo cung ket luan.
        assertThat(cuaTruongChi.get(ImportColumn.MA_NGUYEN_QUAN)).isNull();
        assertThat(cuaHoiDong.get(ImportColumn.MA_NGUYEN_QUAN)).isEqualTo("VN");

        // Va Khach thi khong thay ca su TON TAI cua nguoi con song — khong dong nao het.
        authenticateAsGuest();
        assertThat(doc(chi).personRows())
                .as("BA v2 §10: Khach khong duoc thay bat ky nguoi con song nao, ke ca ten")
                .isEmpty();
    }

    @Test
    @DisplayName("Quét mọi ô của mọi trang tệp thật: dữ liệu Tầng 2/3 của người sống không lọt")
    void quetCaTepThatKhongThayManhTang2Nao() {
        UUID chi = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID song = nguoi(chi, "AT-05-010", "Nguyễn Thị Sống", "FEMALE", 5, true, 1962,
                "Làng Kim Bảng, phủ Lý Nhân", "VN");
        huy(song, "Tên huý của người sống");
        thuy(song, "Thuỵ hiệu không được có");
        UUID chong = nguoi(chi, "AT-05-011", "Nguyễn Văn Chồng", "MALE", 5, true, 1960, null, null);
        honPhoi(chong, song, 1);
        jdbc.update("UPDATE relationship SET valid_from = DATE '1988-01-01' WHERE rel_type = 'SPOUSE'");

        List<String> oChu = moiOChu(generator.sinh(chi).noiDung());

        for (String mat : List.of("1962", "1960", "Làng Kim Bảng, phủ Lý Nhân",
                "Tên huý của người sống", "Thuỵ hiệu không được có", "1988")) {
            assertThat(oChu)
                    .as("manh du lieu '%s' cua nguoi con song KHONG duoc co mat o bat ky o nao"
                            + " cua tep tai ve — tep roi khoi he thong la khong thu hoi duoc", mat)
                    .noneMatch(o -> o.contains(mat));
        }
        // Tang 1 van phai co, neu khong bai kiem tren xanh vi ly do sai.
        assertThat(oChu).anyMatch(o -> o.contains("Nguyễn Thị Sống"));
        assertThat(oChu).anyMatch(o -> o.contains("AT-05-010"));
    }

    // -------------------------------------------------------------------------------------

    /** Mọi ô chữ của mọi trang, kể cả trang ẩn "Danh mục" và trang Hướng dẫn. */
    private static List<String> moiOChu(byte[] file) {
        List<String> ket = new java.util.ArrayList<>();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(file))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(s);
                ket.add(sheet.getSheetName());
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                            ket.add(cell.getStringCellValue());
                        } else if (cell.getCellType()
                                == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
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

    private ParsedWorkbook doc(UUID chi) {
        byte[] file = generator.sinh(chi).noiDung();
        return reader.read(new ByteArrayInputStream(file), "mau.xlsx");
    }

    private static RawRow dong(ParsedWorkbook wb, String ma) {
        return wb.personRows().stream()
                .filter(r -> ma.equals(r.get(ImportColumn.MA)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong thay dong co ma " + ma));
    }

    /** Gieo một nhân khẩu kèm mã {@code EXCEL_MA} của chi. */
    private UUID nguoi(UUID chi, String ma, String hoTen, String gioi, int doi, boolean conSong,
                       Integer namSinh, String nguyenQuan, String maNguyenQuan) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, birth_solar, death_lunar,"
                        + " is_alive, native_place, native_place_code, primary_branch_id)"
                        + " VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)",
                id, gioi, doi,
                namSinh == null ? null : java.sql.Date.valueOf(java.time.LocalDate.of(namSinh, 3, 2)),
                conSong ? null : "{\"year\":1980,\"month\":5,\"day\":10,\"leap\":false}",
                conSong, nguyenQuan, maNguyenQuan, chi);
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'THUONG_GOI', ?, TRUE)", UUID.randomUUID(), id, hoTen);
        jdbc.update("INSERT INTO person_external_ref (id, code_system, branch_id, external_code,"
                + " person_id) VALUES (?, 'EXCEL_MA', ?, ?, ?)", UUID.randomUUID(), chi, ma, id);
        return id;
    }

    private void huy(UUID personId, String ten) {
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'HUY', ?, FALSE)", UUID.randomUUID(), personId, ten);
    }

    private void thuy(UUID personId, String ten) {
        jdbc.update("INSERT INTO person_name (id, person_id, name_type, full_name, is_primary)"
                + " VALUES (?, ?, 'THUY', ?, FALSE)", UUID.randomUUID(), personId, ten);
    }

    private void canh(UUID cha, UUID con, String loai) {
        jdbc.update("INSERT INTO relationship (id, from_person_id, to_person_id, rel_type)"
                + " VALUES (?, ?, ?, ?)", UUID.randomUUID(), cha, con, loai);
    }

    private void honPhoi(UUID a, UUID b, int bac) {
        jdbc.update("INSERT INTO relationship (id, from_person_id, to_person_id, rel_type,"
                + " spouse_order) VALUES (?, ?, ?, 'SPOUSE', ?)", UUID.randomUUID(), a, b, bac);
    }
}

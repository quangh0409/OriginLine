package vn.giapha.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.dataimport.application.CommitImportBatchService;
import vn.giapha.dataimport.application.RollbackImportBatchService;
import vn.giapha.dataimport.application.StageImportBatchService;
import vn.giapha.dataimport.application.ValidateImportBatchService;
import vn.giapha.dataimport.domain.ImportBatch;
import vn.giapha.dataimport.domain.ImportCommitBlockedException;
import vn.giapha.dataimport.domain.IssueCode;
import vn.giapha.dataimport.domain.RollbackRefusedException;
import vn.giapha.integration.AbstractIntegrationTest;

/**
 * Nửa sau của đường ống: <b>ghi vào phả thật</b>, và <b>gỡ lại được</b>.
 *
 * <h2>Bốn con số, không phải bốn lời hứa</h2>
 * Mọi ca ở đây đo bằng {@code count(person)}, {@code count(relationship)}, số đỉnh AGE và số cạnh
 * AGE — đúng cách {@code GraphRelationalConsistencyIT} canh bất biến của nó. Ghi lệch một bên thì
 * không có ràng buộc CSDL nào bắt được: phả đồ (đọc từ đồ thị) và báo cáo (đọc từ bảng) sẽ nói hai
 * điều khác nhau và không ai biết bên nào đúng.
 *
 * <p><b>Test cố ý không {@code @Transactional}</b>: ranh giới commit thật là thứ đang được đo, nên
 * không được bọc nó trong một transaction khác — xem javadoc {@link AbstractIntegrationTest}.</p>
 */
@DisplayName("Ghi lô vào phả và gỡ lô")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ImportCommitIT extends AbstractIntegrationTest {

    @Autowired
    private StageImportBatchService stage;

    @Autowired
    private ValidateImportBatchService validate;

    @Autowired
    private CommitImportBatchService commit;

    @Autowired
    private RollbackImportBatchService rollback;

    private UUID chiAt;

    /**
     * Tài khoản bấm "đã xem cảnh báo".
     *
     * <p>Phải có thật: xác nhận đã xem cảnh báo là hành vi mở khoá nút ghi vài trăm người vào phả,
     * nên nó <b>bắt buộc có chủ</b> — cả ràng buộc {@code ck_import_batch_ack_pair} lẫn
     * {@code CommitImportBatchService} đều từ chối một cái tick vô danh.</p>
     */
    private UUID nguoiXacNhan;

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        nguoiXacNhan = insertAppUser("sub-admin", null);
        authenticateAs("sub-admin", "ADMIN");
    }

    // =====================================================================================
    // 1. Ghi một lô ba đời: ba con số phải khớp nhau
    // =====================================================================================

    @Test
    @DisplayName("Lô 50 người ba đời vào phả: số person, số đỉnh AGE và số dòng relationship khớp")
    void loBaDoiVaoPhaCanBang() {
        DongHoMau mau = DongHoMau.dung();

        CommitImportBatchService.KetQua ketQua = ghi(mau.tep(), "chi-at-lan-1.xlsx");

        assertThat(ketQua.daTao()).isEqualTo(mau.soNguoi());
        assertThat(ketQua.daCapNhat()).isZero();

        // --- Ba con so phai khop nhau ---
        assertThat(countRows("person")).isEqualTo(mau.soNguoi());
        assertThat(graphNodeCount()).isEqualTo(mau.soNguoi());
        assertThat(countRows("relationship")).isEqualTo(mau.soCanh());
        assertThat(graphEdgeTotal()).isEqualTo(mau.soCanh());

        // --- Va tung dong ban chieu phai co dung mot canh AGE tuong ung ---
        assertThat(soCanhAgeKhongKhopBanChieu()).isEmpty();

        // --- Doi thu phai suy ra duoc den tan doi 3, neu khong thi pha do khong xep duoc hang ---
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM person WHERE generation IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT max(generation) FROM person", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("Vợ cả / vợ lẽ vào đúng cạnh hôn phối, đúng bậc, trên đúng người chồng")
    void bacVoCaVoLeVaoDungCanh() {
        ghi(DongHoMau.dung().tep(), "chi-at-hon-phoi.xlsx");

        UUID chong = personCua("AT-01-001");
        UUID voCa = personCua("AT-01-002");
        UUID voLe = personCua("AT-01-003");

        assertThat(bacHonPhoi(chong, voCa)).isEqualTo(1);
        assertThat(bacHonPhoi(chong, voLe)).isEqualTo(2);
        // Canh AGE phai ton tai cho ca hai ba, dung chieu chong -> vo.
        assertThat(graphEdgeCount("SPOUSE", chong, voCa)).isEqualTo(1);
        assertThat(graphEdgeCount("SPOUSE", chong, voLe)).isEqualTo(1);
    }

    @Test
    @DisplayName("Kế tự / đích tôn thành cạnh HEIR đúng chiều: cụ để lại → cháu nối dõi")
    void keTuThanhCanhHeirDungChieu() {
        ghi(DongHoMau.dung().tep(), "chi-at-ke-tu.xlsx");

        UUID cuTo = personCua("AT-01-001");
        UUID dichTon = personCua("AT-03-001");

        Map<String, Object> canh = jdbc.queryForMap("""
                SELECT from_person_id, to_person_id, heir_type FROM relationship
                 WHERE rel_type = 'HEIR' AND is_deleted = FALSE
                """);
        assertThat(canh.get("from_person_id")).isEqualTo(cuTo);
        assertThat(canh.get("to_person_id")).isEqualTo(dichTon);
        assertThat(canh.get("heir_type")).isEqualTo("DICH_TON");
        assertThat(graphEdgeCount("HEIR", cuTo, dichTon)).isEqualTo(1);
        // Chieu nguoc phai KHONG ton tai — ve nguoc canh nay la dao nguoc thua tu cua ca mot chi.
        assertThat(graphEdgeCount("HEIR", dichTon, cuTo)).isZero();
    }

    @Test
    @DisplayName("Ngày giỗ không rõ năm vẫn vào được death_lunar — nhóm hồ sơ cổ nhất không bị bỏ rơi")
    void gioKhongRoNamVanVaoDuoc() {
        ghi(DongHoMau.dung().tep(), "chi-at-gio.xlsx");

        Map<String, Object> gio = jdbc.queryForMap("""
                SELECT death_lunar->>'day' AS ngay, death_lunar->>'month' AS thang,
                       death_lunar->>'year' AS nam, is_alive
                  FROM person WHERE id = ?
                """, personCua("AT-01-003"));

        assertThat(gio.get("ngay")).isEqualTo("15");
        assertThat(gio.get("thang")).isEqualTo("8");
        assertThat(gio.get("nam")).isNull();
        assertThat(gio.get("is_alive")).isEqualTo(false);
    }

    // =====================================================================================
    // 2. Lỗi giữa chừng: không một dòng nào lọt vào phả
    // =====================================================================================

    @Test
    @DisplayName("Một dòng hỏng ở cuối lô → KHÔNG một người nào vào phả, cả bốn con số lệch 0")
    void loHongGiuaChungKhongMotDongNaoLot() {
        int personTruoc = countRows("person");
        int relTruoc = countRows("relationship");
        int dinhTruoc = graphNodeCount();
        int canhTruoc = graphEdgeTotal();

        // Ma nguyen quan "ZZ" khong co trong danh muc place_division: bo kiem chi CANH BAO
        // (IMP_UNKNOWN_PLACE_CODE) nen lo van duyet duoc, con khoa ngoai
        // person.native_place_code thi tu choi — tuc lenh ghi chet o BUOC CUOI CUNG, sau khi ca
        // ba nguoi va toan bo canh da duoc viet ra. Dung ca dang so nhat: hong o dong gan cuoi.
        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8/1950")
                .nhanKhau("AT-02-001", "Nguyễn Văn Hai", "", "Nam", "2", "AT-01-001", "", "",
                        "Không", "1915", "3/9/1990")
                .nhanKhau("AT-03-999", "Nguyễn Văn Ba", "", "Nam", "3", "AT-02-001", "", "",
                        "Không", "1940", "5/6/2000", "Làng Đông Ngạc", "ZZ")
                .build();
        ImportBatch batch = chuanBi(tep, "chi-at-hong.xlsx");

        assertThatThrownBy(() -> commit.commit(batch.id(), null))
                .isInstanceOf(RuntimeException.class);

        assertThat(countRows("person")).isEqualTo(personTruoc);
        assertThat(countRows("relationship")).isEqualTo(relTruoc);
        assertThat(graphNodeCount()).isEqualTo(dinhTruoc);
        assertThat(graphEdgeTotal()).isEqualTo(canhTruoc);
        // Khoa bat bien cung phai sach: neu con sot thi lan thu lai se ra UPDATE tro toi nguoi
        // khong ton tai.
        assertThat(countRows("person_external_ref")).isZero();
        assertThat(countRows("import_commit_entry")).isZero();
        assertThat(trangThai(batch.id())).isEqualTo("FAILED");
    }

    // =====================================================================================
    // 3. Ghi rồi gỡ: về đúng trạng thái trước
    // =====================================================================================

    @Test
    @DisplayName("Ghi rồi gỡ ngay: người xoá mềm, 0 cạnh còn hiệu lực, person_external_ref sạch")
    void ghiRoiGoVeDungTrangThaiTruoc() {
        DongHoMau mau = DongHoMau.dung();
        CommitImportBatchService.KetQua daGhi = ghi(mau.tep(), "chi-at-se-go.xlsx");
        UUID batchId = daGhi.batchId();

        assertThat(rollback.vuongMac(batchId)).isEmpty();
        RollbackImportBatchService.KetQua ketQua =
                rollback.rollback(batchId, null, "Nhập nhầm sang chi Ất");

        assertThat(ketQua.daXoaMem()).isEqualTo(mau.soNguoi());
        assertThat(ketQua.daTraLaiMa()).isEqualTo(mau.soNguoi());

        // XOA MEM, khong xoa cung: node van con trong do thi de khong dut duong noi cac doi.
        assertThat(countRows("person")).isEqualTo(mau.soNguoi());
        assertThat(graphNodeCount()).isEqualTo(mau.soNguoi());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE is_deleted = FALSE",
                Integer.class)).isZero();

        // Canh phai bien mat o CA HAI noi — de nguyen canh thi nguoi vua go thanh "cha ma".
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM relationship WHERE is_deleted = FALSE", Integer.class))
                .isZero();
        assertThat(graphEdgeTotal()).isZero();

        // Khoa bat bien tra lai cho chi, so cai don sach.
        assertThat(countRows("person_external_ref")).isZero();
        assertThat(countRows("import_commit_entry")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT rolled_back_at IS NOT NULL FROM import_batch WHERE id = ?",
                Boolean.class, batchId)).isTrue();
        // Trang thai VAN la COMMITTED: lo ay DA tung vao pha, va do la su that lich su.
        assertThat(trangThai(batchId)).isEqualTo("COMMITTED");
    }

    @Test
    @DisplayName("Gỡ rồi tải lại đúng tệp ấy: mã đã được trả lại nên lô mới ra toàn dòng CREATE")
    void goRoiTaiLaiThiMaDuocTraLai() {
        DongHoMau mau = DongHoMau.dung();
        CommitImportBatchService.KetQua daGhi = ghi(mau.tep(), "chi-at-go-roi-tai-lai.xlsx");
        rollback.rollback(daGhi.batchId(), null, "Nhập nhầm chi");

        ImportBatch lai = stage.stage(chiAt, null, "chi-at-go-roi-tai-lai.xlsx", mau.tep(), false);
        ValidateImportBatchService.KetQua kiem = validate.validate(lai.id());

        assertThat(kiem.seCapNhat()).isZero();
        assertThat(kiem.seTao()).isEqualTo(mau.soNguoi());
    }

    // =====================================================================================
    // 4. Gỡ bị từ chối khi đã có dữ liệu phụ thuộc mới
    // =====================================================================================

    @Test
    @DisplayName("Ghi → sửa một hồ sơ trong lô → gỡ BỊ TỪ CHỐI, và nói rõ hồ sơ nào")
    void suaHoSoRoiGoBiTuChoi() {
        DongHoMau mau = DongHoMau.dung();
        UUID batchId = ghi(mau.tep(), "chi-at-sua-roi-go.xlsx").batchId();

        // Mot nguoi trong ho bo sung tieu su cho cu to — dung loai "dong vao" ma phep go phai thay.
        UUID cuTo = personCua("AT-01-001");
        jdbc.update("UPDATE person SET version = version + 1 WHERE id = ?", cuTo);

        assertThat(rollback.vuongMac(batchId)).isNotEmpty();
        assertThatThrownBy(() -> rollback.rollback(batchId, null, "thử gỡ"))
                .isInstanceOf(RollbackRefusedException.class)
                .satisfies(ex -> assertThat(((RollbackRefusedException) ex).lyDo())
                        .anyMatch(l -> l.contains("đã bị sửa sau khi ghi")));

        // Va khong mot nguoi nao bi xoa mem — tu choi phai la TU CHOI, khong phai "go mot nua".
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person WHERE is_deleted = TRUE",
                Integer.class)).isZero();
        assertThat(countRows("person_external_ref")).isEqualTo(mau.soNguoi());
    }

    @Test
    @DisplayName("Ghi → treo thêm một đứa con vào người của lô → gỡ BỊ TỪ CHỐI")
    void treoThemConRoiGoBiTuChoi() {
        DongHoMau mau = DongHoMau.dung();
        UUID batchId = ghi(mau.tep(), "chi-at-treo-con.xlsx").batchId();

        UUID cha = personCua("AT-02-001");
        UUID conMoi = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, generation, is_alive, primary_branch_id)"
                + " VALUES (?, 'MALE', 3, TRUE, ?)", conMoi, chiAt);
        jdbc.update("INSERT INTO relationship (from_person_id, to_person_id, rel_type)"
                + " VALUES (?, ?, 'PARENT_BIO')", cha, conMoi);

        assertThatThrownBy(() -> rollback.rollback(batchId, null, "thử gỡ"))
                .isInstanceOf(RollbackRefusedException.class)
                .satisfies(ex -> assertThat(((RollbackRefusedException) ex).lyDo())
                        .anyMatch(l -> l.contains("quan hệ mới")));
    }

    @Test
    @DisplayName("Ghi → một người trong họ nhận hồ sơ qua tài khoản → gỡ BỊ TỪ CHỐI")
    void daNhanHoSoRoiGoBiTuChoi() {
        UUID batchId = ghi(DongHoMau.dung().tep(), "chi-at-nhan-ho-so.xlsx").batchId();

        insertAppUser("sub-chau-dich-ton", personCua("AT-03-001"));

        assertThatThrownBy(() -> rollback.rollback(batchId, null, "thử gỡ"))
                .isInstanceOf(RollbackRefusedException.class)
                .satisfies(ex -> assertThat(((RollbackRefusedException) ex).lyDo())
                        .anyMatch(l -> l.contains("đã nhận hồ sơ")));
    }

    // =====================================================================================
    // 5. Tải lại tệp đã sửa sau khi đã ghi: không sinh người trùng
    // =====================================================================================

    @Test
    @DisplayName("Ghi → sửa một ô trong tệp → ghi lại: không sinh một người nào, chỉ cập nhật")
    void taiLaiTepDaSuaKhongSinhNguoiTrung() {
        DongHoMau mau = DongHoMau.dung();
        ghi(mau.tep(), "chi-at-lan-1.xlsx");

        int personSauLan1 = countRows("person");
        int relSauLan1 = countRows("relationship");
        int dinhSauLan1 = graphNodeCount();
        int canhSauLan1 = graphEdgeTotal();

        CommitImportBatchService.KetQua lan2 =
                ghi(mau.tepDaSuaMotO(), "chi-at-lan-2.xlsx");

        assertThat(lan2.daTao()).isZero();
        assertThat(lan2.daCapNhat()).isEqualTo(mau.soNguoi());

        // Bon con so LECH 0 — day la toan bo co che chong sinh trung.
        assertThat(countRows("person")).isEqualTo(personSauLan1);
        assertThat(countRows("relationship")).isEqualTo(relSauLan1);
        assertThat(graphNodeCount()).isEqualTo(dinhSauLan1);
        assertThat(graphEdgeTotal()).isEqualTo(canhSauLan1);

        // Va o da sua thi phai thay doi that, neu khong thi buoc doi soat cua quy trinh vo nghia.
        assertThat(jdbc.queryForObject("""
                SELECT full_name FROM person_name
                 WHERE person_id = ? AND is_primary = TRUE
                """, String.class, personCua("AT-02-001")))
                .isEqualTo(DongHoMau.TEN_DA_SUA);
    }

    // =====================================================================================
    // 6. Ba ca Hội đồng chưa chốt: dừng có kiểm soát, chưa một dòng nào vào phả
    // =====================================================================================

    @Test
    @DisplayName("Lô chạm ca chưa chốt (không rõ sống/mất, con nuôi trong họ, đời dâu) → dừng sạch")
    void baCaChuaChotThiDungSach() {
        byte[] tep = ImportWorkbooks.builder()
                .nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "", "Nam", "1", "", "", "", "Không",
                        "1890", "15/8/1950")
                .nhanKhau("AT-02-001", "Nguyễn Văn Hai", "", "Nam", "2", "AT-01-001", "", "",
                        "Không", "1915", "3/9/1990")
                // Ca 9: o Con song trong VA khong co ngay gio
                .nhanKhau("AT-02-002", "Nguyễn Văn Tha Hương", "", "Nam", "2", "AT-01-001", "",
                        "", "", "1918", "")
                // Ca 5: con nuoi cua mot nguoi trong ho
                .nhanKhau("AT-03-005", "Nguyễn Văn Nuôi", "", "Nam", "3", "AT-02-001", "",
                        "con nuôi", "Không", "1940", "1/2/2000")
                // Ca 2: dau co khai Doi
                .nhanKhau("AT-02-101", "Trần Thị Dâu", "", "Nữ", "2", "", "", "", "Không",
                        "1920", "7/7/1995")
                .honPhoi("AT-02-001", "AT-02-101", "1")
                .build();
        ImportBatch batch = chuanBi(tep, "chi-at-chua-chot.xlsx");

        assertThatThrownBy(() -> commit.commit(batch.id(), null))
                .isInstanceOf(ImportCommitBlockedException.class)
                .satisfies(ex -> assertThat(((ImportCommitBlockedException) ex).issues())
                        .extracting(i -> i.code().name())
                        .contains(IssueCode.IMP_UNDECIDED_LIFE_STATUS.name(),
                                IssueCode.IMP_UNDECIDED_ADOPTION.name(),
                                IssueCode.IMP_UNDECIDED_INLAW_DOI.name()));

        assertThat(countRows("person")).isZero();
        assertThat(graphNodeCount()).isZero();
        assertThat(trangThai(batch.id())).isEqualTo("FAILED");
        // Danh sach van de phai SONG SOT de nguoi nhap doc — no nam ngoai transaction ghi.
        assertThat(jdbc.queryForList("""
                SELECT code FROM import_issue WHERE batch_id = ? AND severity = 'BLOCKING'
                """, String.class, batch.id()))
                .contains(IssueCode.IMP_UNDECIDED_LIFE_STATUS.name());
    }

    @Test
    @DisplayName("Lô chưa qua bộ kiểm thì không bấm ghi được — ranh giới ghi không có cửa sau")
    void loChuaDuyetThiKhongGhiDuoc() {
        ImportBatch batch = stage.stage(chiAt, null, "chua-kiem.xlsx",
                DongHoMau.dung().tep(), false);

        assertThatThrownBy(() -> commit.commit(batch.id(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PARSED");
        assertThat(countRows("person")).isZero();
    }

    // =====================================================================================
    // 7. Phục hồi sau khi tiến trình chết giữa chừng
    // =====================================================================================

    @Test
    @DisplayName("Lô kẹt ở COMMITTING mà sổ cái rỗng → transaction đã cuộn lại, đưa về VALIDATED")
    void loKetDangGhiDuocDuaVeChoDuyet() {
        ImportBatch batch = chuanBi(DongHoMau.dung().tep(), "chi-at-ket.xlsx");
        // Mo phong tien trinh chet dung giua buoc ghi: trang thai da chuyen COMMITTING nhung
        // transaction cuon lai sach nen khong co mot dong so cai nao.
        jdbc.update("UPDATE import_batch SET status = 'COMMITTING' WHERE id = ?", batch.id());

        assertThat(commit.phucHoiSauSuCo(chiAt)).isEqualTo(1);

        assertThat(trangThai(batch.id())).isEqualTo("VALIDATED");
        assertThat(countRows("person")).isZero();
        // Va sau khi phuc hoi thi bam ghi lai duoc ngay, khong phai tai len lai tep.
        assertThat(commit.commit(batch.id(), null).daTao()).isEqualTo(DongHoMau.dung().soNguoi());
    }

    @Test
    @DisplayName("Lô đã ghi xong thì phép phục hồi KHÔNG đụng tới: sổ cái là bằng chứng nó đã vào")
    void loDaGhiXongThiPhepPhucHoiKhongDungToi() {
        UUID batchId = ghi(DongHoMau.dung().tep(), "chi-at-da-ghi.xlsx").batchId();

        assertThat(commit.phucHoiSauSuCo(chiAt)).isZero();
        assertThat(trangThai(batchId)).isEqualTo("COMMITTED");
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private CommitImportBatchService.KetQua ghi(byte[] tep, String ten) {
        return commit.commit(chuanBi(tep, ten).id(), null);
    }

    /** Tải lên, kiểm, tick "đã xem cảnh báo" — đưa lô về đúng trạng thái được phép bấm ghi. */
    private ImportBatch chuanBi(byte[] tep, String ten) {
        ImportBatch batch = stage.stage(chiAt, null, ten, tep, false);
        ValidateImportBatchService.KetQua kiem = validate.validate(batch.id());
        assertThat(kiem.loiChan())
                .as("lô %s phải sạch lỗi chặn trước khi ghi; lỗi: %s", ten, kiem.issues())
                .isZero();
        commit.xacNhanDaXemCanhBao(batch.id(), nguoiXacNhan);
        return batch;
    }

    private String trangThai(UUID batchId) {
        return jdbc.queryForObject("SELECT status FROM import_batch WHERE id = ?", String.class,
                batchId);
    }

    private UUID personCua(String externalCode) {
        return jdbc.queryForObject("""
                SELECT person_id FROM person_external_ref
                 WHERE code_system = 'EXCEL_MA' AND branch_id = ? AND external_code = ?
                """, UUID.class, chiAt, externalCode);
    }

    private Integer bacHonPhoi(UUID chong, UUID vo) {
        return jdbc.queryForObject("""
                SELECT spouse_order FROM relationship
                 WHERE from_person_id = ? AND to_person_id = ? AND rel_type = 'SPOUSE'
                   AND is_deleted = FALSE
                """, Integer.class, chong, vo);
    }

    /**
     * Số dòng bản chiếu còn hiệu lực <b>không</b> có cạnh AGE tương ứng.
     *
     * <p>Phép khẳng định quan trọng nhất của tệp này. Đếm hai bên rồi so tổng là chưa đủ: hai con
     * số bằng nhau vẫn có thể ứng với hai tập cạnh khác nhau.</p>
     */
    private List<String> soCanhAgeKhongKhopBanChieu() {
        List<Map<String, Object>> canh = jdbc.queryForList("""
                SELECT from_person_id, to_person_id, rel_type FROM relationship
                 WHERE is_deleted = FALSE
                """);
        List<String> lech = new ArrayList<>();
        for (Map<String, Object> c : canh) {
            // Nhan canh AGE cua ca con ruot lan con nuoi deu la PARENT; loai nam o thuoc tinh
            // `type` cua canh chu khong o nhan. Do bang rel_type nguyen van thi moi canh cha-con
            // deu "khong khop" va bai kiem nay se xanh/do vi mot ly do sai.
            String nhan = ((String) c.get("rel_type")).startsWith("PARENT")
                    ? "PARENT" : (String) c.get("rel_type");
            int n = graphEdgeCount(nhan, (UUID) c.get("from_person_id"),
                    (UUID) c.get("to_person_id"));
            if (n != 1) {
                lech.add(c.get("rel_type") + " " + c.get("from_person_id") + "->"
                        + c.get("to_person_id") + " co " + n + " canh AGE");
            }
        }
        return lech;
    }

    /**
     * Một chi ba đời, đủ các ca tiếng Việt mà nửa sau đường ống phải xử đúng.
     *
     * <ul>
     *   <li>thuỷ tổ có <b>vợ cả và vợ lẽ</b> (bậc 1 và 2) — đầu vào của
     *       {@code ux_relationship_spouse_order};</li>
     *   <li>sáu người con đời 2, mỗi người một bà <b>dâu</b> (không cha, không mẹ, <b>để trống ô
     *       Đời</b> đúng theo quy ước tạm thời cho tới khi Hội đồng chốt);</li>
     *   <li>đời 3 là cháu, trong đó cháu trưởng mang quan hệ <b>đích tôn</b> với thuỷ tổ;</li>
     *   <li>vợ lẽ có ngày giỗ <b>không rõ năm</b> — ca rất thường trong sổ cũ.</li>
     * </ul>
     */
    private static final class DongHoMau {

        static final String TEN_DA_SUA = "Nguyễn Văn Hai (đã đối soát với sổ 1998)";

        private static final int SO_CON_DOI_2 = 6;
        private static final int SO_CHAU_MOI_NHA = 5;

        private final byte[] tep;
        private final byte[] tepDaSua;
        private final int soNguoi;
        private final int soCanh;

        private DongHoMau(byte[] tep, byte[] tepDaSua, int soNguoi, int soCanh) {
            this.tep = tep;
            this.tepDaSua = tepDaSua;
            this.soNguoi = soNguoi;
            this.soCanh = soCanh;
        }

        static DongHoMau dung() {
            return new DongHoMau(dungTep(null), dungTep(TEN_DA_SUA),
                    3 + SO_CON_DOI_2 * 2 + SO_CON_DOI_2 * SO_CHAU_MOI_NHA,
                    // hon phoi: 2 ba cua thuy to + 6 ba dau
                    (2 + SO_CON_DOI_2)
                            // cha + me cho 6 nguoi doi 2, va cho 30 chau doi 3
                            + (SO_CON_DOI_2 * 2) + (SO_CON_DOI_2 * SO_CHAU_MOI_NHA * 2)
                            // mot canh ke tu
                            + 1);
        }

        byte[] tep() {
            return tep;
        }

        byte[] tepDaSuaMotO() {
            return tepDaSua;
        }

        int soNguoi() {
            return soNguoi;
        }

        int soCanh() {
            return soCanh;
        }

        private static byte[] dungTep(String tenMoiChoConCa) {
            ImportWorkbooks wb = ImportWorkbooks.builder();
            // --- Doi 1: thuy to + vo ca + vo le ---
            wb.nhanKhau("AT-01-001", "Nguyễn Văn Cẩn", "Cẩn", "Nam", "1", "", "", "", "Không",
                    "1880", "20/11/1945");
            wb.nhanKhau("AT-01-002", "Nguyễn Thị Lựu", "", "Nữ", "1", "", "", "", "Không",
                    "1884", "9/3/1951");
            // Vo le: ngay gio KHONG RO NAM — chuyen rat thuong trong so cu.
            wb.nhanKhau("AT-01-003", "Trần Thị Nhu", "", "Nữ", "1", "", "", "", "Không",
                    "1890", "15/8");
            wb.honPhoi("AT-01-001", "AT-01-002", "1");
            wb.honPhoi("AT-01-001", "AT-01-003", "2");

            List<String> maDoi2 = new ArrayList<>();
            for (int i = 1; i <= SO_CON_DOI_2; i++) {
                String ma = String.format("AT-02-%03d", i);
                maDoi2.add(ma);
                String ten = i == 1 && tenMoiChoConCa != null
                        ? tenMoiChoConCa : "Nguyễn Văn Đời Hai " + i;
                wb.nhanKhau(ma, ten, "", "Nam", "2", "AT-01-001", "AT-01-002", "", "Không",
                        String.valueOf(1905 + i), "12/4/" + (1970 + i));
                // Dau: khong cha, khong me, DE TRONG o Doi.
                String maDau = String.format("AT-02-%03d", 100 + i);
                wb.nhanKhau(maDau, "Lê Thị Dâu " + i, "", "Nữ", "", "", "", "", "Không",
                        String.valueOf(1908 + i), "6/6/" + (1975 + i));
                wb.honPhoi(ma, maDau, "1");
            }

            int stt = 0;
            for (int i = 0; i < SO_CON_DOI_2; i++) {
                for (int j = 1; j <= SO_CHAU_MOI_NHA; j++) {
                    stt++;
                    String ma = String.format("AT-03-%03d", stt);
                    // Chau truong cua nha ca la DICH TON cua thuy to.
                    String keTuCho = stt == 1 ? "AT-01-001" : "";
                    String loaiKeTu = stt == 1 ? "đích tôn" : "";
                    wb.nhanKhau(ma, "Nguyễn Văn Đời Ba " + stt, "", "Nam", "3", maDoi2.get(i),
                            String.format("AT-02-%03d", 101 + i), "", "Không",
                            String.valueOf(1935 + j), "3/10/" + (1995 + j), "", "", "", "",
                            keTuCho, loaiKeTu);
                }
            }
            return wb.build();
        }
    }
}

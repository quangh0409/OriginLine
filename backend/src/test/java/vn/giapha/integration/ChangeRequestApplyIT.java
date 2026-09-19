package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.application.GenealogyConflictException;
import vn.giapha.genealogy.application.SoftDeletePersonService;
import vn.giapha.genealogy.application.UpdatePersonCommands;
import vn.giapha.genealogy.application.UpdatePersonService;
import vn.giapha.membership.application.ChangeRequestService;
import vn.giapha.membership.application.ChangeRequestView;
import vn.giapha.membership.application.command.ReviewChangeRequestCommand;
import vn.giapha.membership.application.command.SubmitChangeRequestCommand;
import vn.giapha.membership.domain.ChangeRequestType;
import vn.giapha.membership.domain.event.CorrectionPayload;

/**
 * <b>Duyệt xong thì gia phả phải đổi thật</b> — bộ áp dụng yêu cầu đính chính, trên hạ tầng thật.
 *
 * <p>Trước bộ này, Trưởng chi bấm Duyệt, hệ thống ghi {@code APPROVED}, và cây không hề đổi:
 * {@code ChangeRequestApprovedEvent} được phát ra nhưng toàn hệ thống không có một
 * {@code @EventListener} nào. Mỗi ca dưới đây canh một mặt của việc bịt lỗ hổng ấy.</p>
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * Ba thứ chỉ quan sát được với CSDL thật:
 * <ol>
 *   <li><b>Ranh giới transaction.</b> Duyệt và áp dụng nằm trong <i>một</i> transaction. Chỉ có
 *       commit/rollback thật mới chứng minh được rằng lệnh ghi hỏng kéo theo trạng thái
 *       {@code APPROVED} rollback — mock không có ranh giới nào để mà rollback.</li>
 *   <li><b>Khoá lạc quan.</b> {@code person.version} do Hibernate quản; ca "ghi đè mù bị chặn" cần
 *       hai lệnh ghi thật, nối tiếp nhau, trên cùng một hàng.</li>
 *   <li><b>Nhật ký.</b> {@code actor_user_id} đi qua chuỗi {@code keycloak_sub → app_user}, và
 *       {@code audit_log} có trigger cấm UPDATE — cả hai chỉ tồn tại trong Postgres.</li>
 * </ol>
 */
@DisplayName("Áp dụng yêu cầu đính chính sau khi duyệt")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class ChangeRequestApplyIT extends AbstractIntegrationTest {

    @Autowired
    private ChangeRequestService changeRequests;

    @Autowired
    private UpdatePersonService updatePerson;

    @Autowired
    private SoftDeletePersonService softDeletePerson;

    /** Ngày giỗ đúng theo bia mộ: rằm tháng Tám âm. Fixture gieo sẵn 10/5 — tức là đang ghi sai. */
    private static final int GIO_THANG = 8;
    private static final int GIO_NGAY = 15;

    private UUID chiGiap;

    /** Cụ ông đã khuất, thuộc Chi Giáp — đối tượng của đề nghị. */
    private UUID cuOng;

    /** Bà Mai — thành viên thường của Chi Giáp, người phát hiện ngày giỗ ghi sai. */
    private UUID maiPersonId;
    private UUID maiUserId;

    /** Trưởng Chi Giáp — người duyệt. */
    private UUID truongChiPersonId;
    private UUID truongChiUserId;

    @BeforeEach
    void dungDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        cuOng = seed(PersonFixtures.deceased("Nguyễn Văn Cả", 1975)
                .branch(chiGiap).generation(3));

        maiPersonId = seed(PersonFixtures.living("Nguyễn Thị Mai").branch(chiGiap).generation(5));
        maiUserId = insertAppUser("sub-mai", maiPersonId);

        truongChiPersonId = seed(PersonFixtures.living("Nguyễn Văn Trưởng")
                .branch(chiGiap).generation(4));
        truongChiUserId = insertAppUser("sub-truong-chi", truongChiPersonId);
        assignBranchRole(truongChiUserId, "BRANCH_HEAD", chiGiap);
    }

    // -------------------------------------------------------------------------------------
    // Tiện ích
    // -------------------------------------------------------------------------------------

    /** Bà Mai gửi đề nghị đính chính <b>ngày giỗ</b> — ca dùng thật nhiều nhất của cả luồng. */
    private ChangeRequestView maiDeNghiSuaNgayGio() {
        authenticateAs("sub-mai", "MEMBER");
        return changeRequests.submit(new SubmitChangeRequestCommand(
                ChangeRequestType.UPDATE_PERSON, cuOng, null,
                Map.of("death", Map.of(
                        "lunar", Map.of("year", 1975, "month", GIO_THANG, "day", GIO_NGAY,
                                "leap", false),
                        "precision", "DAY")),
                "Bia mộ tại từ đường ghi rằm tháng Tám, gia phả chép nhầm sang tháng Năm"));
    }

    private ChangeRequestView maiDeNghiSuaNguyenQuan(String nguyenQuan) {
        authenticateAs("sub-mai", "MEMBER");
        return changeRequests.submit(new SubmitChangeRequestCommand(
                ChangeRequestType.UPDATE_PERSON, cuOng, null,
                Map.of("nativePlace", nguyenQuan), "Nguyên quán chép nhầm"));
    }

    private ChangeRequestView truongChiDuyet(UUID changeRequestId) {
        authenticateAs("sub-truong-chi", "BRANCH_HEAD");
        return changeRequests.review(
                new ReviewChangeRequestCommand(changeRequestId, true, "Đúng với bia mộ"));
    }

    private String trangThai(UUID changeRequestId) {
        return jdbc.queryForObject("SELECT status FROM change_request WHERE id = ?",
                String.class, changeRequestId);
    }

    private String ngayGioAmDangGhi() {
        return jdbc.queryForObject(
                "SELECT (death_lunar->>'day') || '/' || (death_lunar->>'month')"
                        + " FROM person WHERE id = ?", String.class, cuOng);
    }

    private String nguyenQuanDangGhi() {
        return jdbc.queryForObject("SELECT native_place FROM person WHERE id = ?",
                String.class, cuOng);
    }

    private long phienBanCuOng() {
        Long version = jdbc.queryForObject("SELECT version FROM person WHERE id = ?",
                Long.class, cuOng);
        return version == null ? -1L : version;
    }

    // -------------------------------------------------------------------------------------
    // 1 — Duyệt xong thì cây đổi thật
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Trưởng chi duyệt -> person ĐỔI THẬT trong CSDL, không chỉ đổi trạng thái yêu cầu")
    void duyetXongThiCayDoiThat() {
        assertThat(ngayGioAmDangGhi()).as("fixture gieo sai ngay gio").isEqualTo("10/5");
        ChangeRequestView yeuCau = maiDeNghiSuaNgayGio();

        ChangeRequestView sauDuyet = truongChiDuyet(yeuCau.id());

        assertThat(sauDuyet.status()).isEqualTo("APPROVED");
        assertThat(ngayGioAmDangGhi())
                .as("death_lunar la NGUON CHAN LY cua gio - duyet xong ma cot nay khong doi thi "
                        + "ca chi van nhac gio sai, va khong co loi nao duoc nem ra")
                .isEqualTo(GIO_NGAY + "/" + GIO_THANG);
        assertThat(phienBanCuOng())
                .as("lenh ghi that phai nhich khoa lac quan len")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Đề nghị sửa nguyên quán cũng xuống tới cột person.native_place")
    void suaNguyenQuanXuongToiCot() {
        ChangeRequestView yeuCau = maiDeNghiSuaNguyenQuan("Hà Nam");

        truongChiDuyet(yeuCau.id());

        assertThat(nguyenQuanDangGhi()).isEqualTo("Hà Nam");
    }

    @Test
    @DisplayName("Loại OTHER được duyệt nhưng KHÔNG tự sửa gì — Trưởng chi tự thao tác")
    void loaiOtherDuocDuyetNhungKhongTuSua() {
        // OTHER la loi mo ta bang chu ("xin doi quan he cha-con"), khong phai lenh ghi. Gia vo ap
        // dung no la doan bua y dinh cua nguoi gui tren mot thay doi lam bien dang ca phan cay ben duoi.
        authenticateAs("sub-mai", "MEMBER");
        ChangeRequestView yeuCau = changeRequests.submit(new SubmitChangeRequestCommand(
                ChangeRequestType.OTHER, cuOng, null,
                Map.of("moTa", "Xin nối cụ vào chi trên"), "Theo lời cụ bà kể lại"));

        assertThat(truongChiDuyet(yeuCau.id()).status()).isEqualTo("APPROVED");
        assertThat(phienBanCuOng()).as("khong lenh ghi nao duoc phat sinh").isZero();
    }

    // -------------------------------------------------------------------------------------
    // 2 — Nhật ký phân biệt "tự sửa" với "duyệt đề nghị của người khác"
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("audit_log ghi ĐỦ hai vai: người duyệt là actor, người đề nghị nằm trong note")
    void nhatKyGhiDuHaiVai() {
        ChangeRequestView yeuCau = maiDeNghiSuaNgayGio();

        truongChiDuyet(yeuCau.id());

        Map<String, Object> ghiSuaHoSo = jdbc.queryForMap(
                "SELECT actor_user_id, note FROM audit_log"
                        + " WHERE entity_type = 'Person' AND action = 'UPDATE' AND entity_id = ?",
                cuOng.toString());
        assertThat(ghiSuaHoSo.get("actor_user_id"))
                .as("nguoi THUC HIEN lenh ghi la Truong chi duyet - trach nhiem thuoc ve nguoi quyet")
                .isEqualTo(truongChiUserId);
        assertThat((String) ghiSuaHoSo.get("note"))
                .as("audit_log khong co cot 'thay mat ai', nen dau vet nguoi de nghi di vao note")
                .contains(yeuCau.id().toString())
                .contains(maiUserId.toString())
                .contains(truongChiUserId.toString());

        Map<String, Object> ghiDuyet = jdbc.queryForMap(
                "SELECT actor_user_id, after->>'requestedBy' AS requested_by FROM audit_log"
                        + " WHERE entity_type = 'ChangeRequest' AND action = 'APPROVE'"
                        + " AND entity_id = ?", yeuCau.id().toString());
        assertThat(ghiDuyet.get("actor_user_id")).isEqualTo(truongChiUserId);
        assertThat(ghiDuyet.get("requested_by")).isEqualTo(maiUserId.toString());
    }

    @Test
    @DisplayName("Trưởng chi TỰ SỬA để lại vết KHÁC hẳn — không có dấu yêu cầu đính chính")
    void truongChiTuSuaDeLaiVetKhac() {
        // Day la nua con lai cua cau hoi "ghi audit ra sao": phai phan biet duoc "Truong chi tu sua"
        // voi "Truong chi duyet de nghi cua ba Mai". Neu hai viec de lai vet giong het nhau thi
        // doc lai lich su ba nam sau khong con biet ai la nguoi phat hien ra sai sot.
        authenticateAs("sub-truong-chi", "BRANCH_HEAD");
        updatePerson.update(UpdatePersonCommands.cho(cuOng)
                .nguyenQuan("Hà Nam").ghiChu("Theo gia phả chi Giáp bản 1998").build());

        String note = jdbc.queryForObject(
                "SELECT note FROM audit_log"
                        + " WHERE entity_type = 'Person' AND action = 'UPDATE' AND entity_id = ?",
                String.class, cuOng.toString());
        assertThat(note).doesNotContain("Ap dung yeu cau dinh chinh");
        assertThat(countRows("change_request")).isZero();
    }

    // -------------------------------------------------------------------------------------
    // 3 — Áp dụng thất bại: yêu cầu KHÔNG được ở lại trạng thái APPROVED
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Nhân khẩu bị xoá mềm trong lúc chờ -> duyệt hỏng, yêu cầu QUAY LẠI PENDING")
    void apDungThatBaiThiKhongDuocGhiApproved() {
        // Day la cho de de lai du lieu mau thuan nhat: da ghi APPROVED roi ma lenh ghi hong.
        // Chon MOT transaction cho ca duyet lan ap dung chinh la de tra loi cau hoi nay bang cau
        // truc, khong bang ky luat: khong co cua so nao de trang thai kip commit mot minh.
        ChangeRequestView yeuCau = maiDeNghiSuaNgayGio();

        authenticateAs("sub-admin", "ADMIN");
        softDeletePerson.softDelete(cuOng, "Trùng với một bản ghi khác, chờ Hội đồng đối chiếu");

        assertThatThrownBy(() -> truongChiDuyet(yeuCau.id()))
                .isInstanceOf(GenealogyConflictException.class)
                .hasMessageContaining("xoa mem");

        assertThat(trangThai(yeuCau.id()))
                .as("trang thai APPROVED phai rollback theo lenh ghi hong")
                .isEqualTo("PENDING");
        assertThat(ngayGioAmDangGhi()).as("cay khong duoc doi mot nua").isEqualTo("10/5");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'ChangeRequest'"
                        + " AND action = 'APPROVE'", Integer.class))
                .as("dong audit APPROVE cung phai rollback - nhat ky khong duoc ghi mot cuoc duyet "
                        + "chua he co hieu luc")
                .isZero();
    }

    @Test
    @DisplayName("Áp dụng hỏng thì yêu cầu vẫn nằm trong hàng đợi chờ duyệt, không biến mất")
    void apDungHongThiYeuCauVanConTrongHangDoi() {
        // He qua truc tiep cua lua chon "quay lai PENDING": Truong chi khong mat dau vet cong viec,
        // va ba Mai khong phai go lai de nghi tu dau.
        ChangeRequestView yeuCau = maiDeNghiSuaNgayGio();
        authenticateAs("sub-admin", "ADMIN");
        softDeletePerson.softDelete(cuOng, "Nhầm bản ghi, chờ Hội đồng đối chiếu");
        assertThatThrownBy(() -> truongChiDuyet(yeuCau.id()))
                .isInstanceOf(GenealogyConflictException.class);

        authenticateAs("sub-truong-chi", "BRANCH_HEAD");

        assertThat(changeRequests.pendingForReview(0, 20))
                .extracting(ChangeRequestView::id)
                .contains(yeuCau.id());
    }

    // -------------------------------------------------------------------------------------
    // 4 — Ghi đè mù bị chặn nhờ _baseVersion
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Người khác sửa hồ sơ trong lúc chờ -> đề nghị KHÔNG được ghi đè mù")
    void ghiDeMuBiChan() {
        // Mot de nghi nam cho hang tuan. Khong co moc phien ban thi luc duyet no de len moi thay
        // doi da xay ra trong luc cho, va khong ai biet dieu do vua xay ra.
        ChangeRequestView yeuCau = maiDeNghiSuaNguyenQuan("Hà Nam");
        assertThat(CorrectionPayload.baseVersion(yeuCau.payload()))
                .as("moc phien ban duoc dong dau NGAY LUC GUI")
                .isZero();

        authenticateAs("sub-truong-chi", "BRANCH_HEAD");
        updatePerson.update(UpdatePersonCommands.cho(cuOng)
                .nguyenQuan("Thái Bình").ghiChu("Đối chiếu bia mộ").build());
        assertThat(phienBanCuOng()).isEqualTo(1L);

        assertThatThrownBy(() -> truongChiDuyet(yeuCau.id()))
                .isInstanceOf(GenealogyConflictException.class);

        assertThat(nguyenQuanDangGhi())
                .as("thay doi cua nguoi sua trong luc cho phai con nguyen")
                .isEqualTo("Thái Bình");
        assertThat(trangThai(yeuCau.id())).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Mốc phiên bản do người gửi kèm theo được tôn trọng, kể cả khi đã cũ")
    void mocPhienBanCuaNguoiGuiDuocTonTrong() {
        authenticateAs("sub-truong-chi", "BRANCH_HEAD");
        updatePerson.update(UpdatePersonCommands.cho(cuOng).nguyenQuan("Thái Bình").build());

        authenticateAs("sub-mai", "MEMBER");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nativePlace", "Hà Nam");
        // Ba Mai mo ho so tu truoc khi Truong chi sua: ETag cua co ay con la 0.
        payload.put(CorrectionPayload.BASE_VERSION_KEY, 0);
        ChangeRequestView yeuCau = changeRequests.submit(new SubmitChangeRequestCommand(
                ChangeRequestType.UPDATE_PERSON, cuOng, null, payload, "Nguyên quán chép nhầm"));

        assertThatThrownBy(() -> truongChiDuyet(yeuCau.id()))
                .isInstanceOf(GenealogyConflictException.class);
        assertThat(nguyenQuanDangGhi()).isEqualTo("Thái Bình");
    }

    // -------------------------------------------------------------------------------------
    // 5 — Ranh giới phân quyền không bị nới ra vì "đã duyệt rồi"
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Trưởng chi khác nhánh không duyệt được, và cây không đổi")
    void truongChiKhacNhanhKhongDuyetDuoc() {
        UUID chiAt = insertBranch("Chi Ất", "goc.chi_at", null, "CHI");
        UUID nguoiChiAt = seed(PersonFixtures.living("Nguyễn Văn Ất").branch(chiAt).generation(4));
        UUID truongChiAtUserId = insertAppUser("sub-truong-chi-at", nguoiChiAt);
        assignBranchRole(truongChiAtUserId, "BRANCH_HEAD", chiAt);

        ChangeRequestView yeuCau = maiDeNghiSuaNgayGio();

        authenticateAs("sub-truong-chi-at", "BRANCH_HEAD");
        assertThatThrownBy(() -> changeRequests.review(
                new ReviewChangeRequestCommand(yeuCau.id(), true, "duyệt hộ")))
                .isInstanceOf(vn.giapha.shared.exception.ForbiddenException.class);

        assertThat(ngayGioAmDangGhi()).isEqualTo("10/5");
        assertThat(trangThai(yeuCau.id())).isEqualTo("PENDING");

        // Va vi yeu cau van o PENDING, dung nguoi vao duyet thi no di tron duong.
        assertThat(truongChiDuyet(yeuCau.id()).status()).isEqualTo("APPROVED");
        assertThat(ngayGioAmDangGhi()).isEqualTo(GIO_NGAY + "/" + GIO_THANG);
    }
}

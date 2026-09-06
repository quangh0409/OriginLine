package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.audit.domain.AuditAction;
import vn.giapha.audit.domain.AuditActor;
import vn.giapha.audit.domain.AuditEntry;
import vn.giapha.audit.domain.RequestFingerprint;
import vn.giapha.audit.domain.port.AuditLogRepository;
import vn.giapha.membership.domain.ChangeRequest;
import vn.giapha.membership.domain.port.ChangeRequestRepository;
import vn.giapha.shared.vo.BranchPath;

/**
 * Ghim hai câu SQL thuần đã từng chết lúc chạy chứ không phải lúc biên dịch.
 *
 * <p>Cả hai đều là <b>native query</b>, nên trình biên dịch không nói gì và không một test unit nào
 * chạm tới. Chúng chỉ nổ khi có PostgreSQL thật ở đầu bên kia — đúng loại lỗi mà test tích hợp sinh
 * ra để bắt:</p>
 * <ol>
 *   <li>{@code CAST(:paths AS ltree[]) @> b.path} — lọc phạm vi chi/ngành ngay trong CSDL. Nếu
 *       literal mảng sai định dạng hoặc toán tử không phân giải được thì Trưởng chi <b>không thấy
 *       hàng đợi nào</b>, và triệu chứng trông hệt như "chưa có yêu cầu nào" chứ không như lỗi.</li>
 *   <li>{@code CAST(? AS inet)} — cột {@code ip_address} của {@code audit_log}. Một chuỗi không
 *       phải IP hợp lệ bị CSDL từ chối sẽ <b>kéo đổ cả giao dịch phả hệ</b> mà nó đang ghi vết:
 *       dấu vết HTTP là thứ phụ, nó không được phép làm hỏng nghiệp vụ.</li>
 * </ol>
 */
@DisplayName("Ghim SQL thuần: ltree[] @> path và CAST(? AS inet)")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class NativeSqlPinningIT extends AbstractIntegrationTest {

    @Autowired
    private ChangeRequestRepository changeRequests;

    @Autowired
    private AuditLogRepository auditLog;

    private UUID chiGiap;
    private UUID nganhTruong;
    private UUID chiAt;
    private UUID nguoiGui;

    @BeforeEach
    void setUpBranches() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        nganhTruong = insertBranch("Ngành Trưởng", "goc.chi_giap.nganh_truong", chiGiap, "NGANH");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        nguoiGui = insertAppUser("sub-nguoi-gui", null);
    }

    /** Một yêu cầu đính chính đang chờ duyệt, nhắm vào {@code branchId}. */
    private UUID pendingRequest(UUID branchId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO change_request (id, request_type, target_branch_id, payload,"
                        + " requested_by, status) VALUES (?, 'CREATE_PERSON', ?, CAST(? AS jsonb), ?, 'PENDING')",
                id, branchId, "{\"ten\":\"Nguyễn Văn Test\"}", nguoiGui);
        return id;
    }

    // =====================================================================================
    // ltree[] @> path
    // =====================================================================================

    @Test
    @DisplayName("phạm vi chi phủ cả ngành con nhưng KHÔNG chạm sang chi khác")
    void phamViChi_phuNganhCon_khongChamChiKhac() {
        UUID cuaChiGiap = pendingRequest(chiGiap);
        UUID cuaNganhTruong = pendingRequest(nganhTruong);
        UUID cuaChiAt = pendingRequest(chiAt);

        List<ChangeRequest> trongPhamVi = changeRequests.pendingInScope(
                List.of(BranchPath.of("goc.chi_giap")), false, 50, 0);

        assertThat(trongPhamVi).extracting(ChangeRequest::id)
                .as("toán tử @> đọc là 'mảng chứa một tổ tiên của path kia'")
                .containsExactlyInAnyOrder(cuaChiGiap, cuaNganhTruong)
                .doesNotContain(cuaChiAt);
        assertThat(changeRequests.countPendingInScope(List.of(BranchPath.of("goc.chi_giap")), false))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("nhiều chi trong cùng một literal mảng ltree đều được tính")
    void nhieuChiTrongMotMangLtree_deuDuocTinh() {
        pendingRequest(chiGiap);
        pendingRequest(nganhTruong);
        pendingRequest(chiAt);

        long count = changeRequests.countPendingInScope(
                List.of(BranchPath.of("goc.chi_giap"), BranchPath.of("goc.chi_at")), false);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("vai toàn dòng họ thấy cả yêu cầu chưa xác định được chi")
    void vaiToanDongHo_thayCaYeuCauChuaXacDinhChi() {
        pendingRequest(chiGiap);
        pendingRequest(chiAt);
        pendingRequest(null);

        assertThat(changeRequests.countPendingInScope(List.of(), true)).isEqualTo(3);
        // Yêu cầu không xác định được chi phải NẰM NGOÀI phạm vi của Trưởng chi: dữ liệu thiếu
        // phải làm quyền hẹp lại, không bao giờ được nới ra.
        assertThat(changeRequests.countPendingInScope(List.of(BranchPath.of("goc.chi_giap")), false))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("không có phạm vi nào nghĩa là không thấy gì, tuyệt đối không phải thấy tất")
    void khongCoPhamVi_khongThayGi() {
        pendingRequest(chiGiap);
        pendingRequest(chiAt);

        assertThat(changeRequests.pendingInScope(List.of(), false, 50, 0)).isEmpty();
        assertThat(changeRequests.countPendingInScope(List.of(), false)).isZero();
    }

    // =====================================================================================
    // CAST(? AS inet)
    // =====================================================================================

    // Doc gia tri bang host() chu khong phai ip_address::text: phep ep sang text cua PostgreSQL
    // GIU LAI mat na (203.0.113.9/32, 2001:db8::1/128), con thu ta muon doi chieu la chinh dia chi.
    @Test
    @DisplayName("ghi nhật ký với IPv4, IPv6 và không có IP đều thành công")
    void ghiNhatKy_ipv4_ipv6_vaKhongCoIp() {
        append("ip-v4", new RequestFingerprint("203.0.113.9", "Mozilla/5.0", "req-1"));
        append("ip-v6", new RequestFingerprint("2001:db8::1", "Mozilla/5.0", "req-2"));
        append("ip-none", RequestFingerprint.none());

        Map<String, Object> v4 = jdbc.queryForMap(
                "SELECT host(ip_address) AS ip FROM audit_log WHERE entity_id = 'ip-v4'");
        assertThat(v4.get("ip")).isEqualTo("203.0.113.9");

        Map<String, Object> v6 = jdbc.queryForMap(
                "SELECT host(ip_address) AS ip FROM audit_log WHERE entity_id = 'ip-v6'");
        assertThat(v6.get("ip")).isEqualTo("2001:db8::1");

        Map<String, Object> none = jdbc.queryForMap(
                "SELECT host(ip_address) AS ip FROM audit_log WHERE entity_id = 'ip-none'");
        assertThat(none.get("ip")).isNull();
    }

    @Test
    @DisplayName("IPv4 kèm cổng và chuỗi X-Forwarded-For nhiều chặng vẫn ghi được")
    void ipKemCong_vaChuoiXForwardedFor_vanGhiDuoc() {
        append("ip-port", new RequestFingerprint("203.0.113.9:52344", null, null));
        append("ip-chain", new RequestFingerprint("198.51.100.7, 203.0.113.9, 10.0.0.1", null, null));

        assertThat(jdbc.queryForObject(
                "SELECT host(ip_address) FROM audit_log WHERE entity_id = 'ip-port'", String.class))
                .isEqualTo("203.0.113.9");
        assertThat(jdbc.queryForObject(
                "SELECT host(ip_address) FROM audit_log WHERE entity_id = 'ip-chain'", String.class))
                .as("chỉ chặng đầu tiên được giữ lại").isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("header IP rác bị sàng thành NULL thay vì làm cột inet từ chối cả câu INSERT")
    void headerIpRac_biSangThanhNull_khongLamHongGiaoDich() {
        assertThatCode(() -> append("ip-rac",
                new RequestFingerprint("khong-phai-dia-chi-ip", null, null)))
                .as("một dấu vết HTTP hỏng không được phép giết giao dịch nghiệp vụ")
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject(
                "SELECT host(ip_address) FROM audit_log WHERE entity_id = 'ip-rac'", String.class))
                .isNull();
        assertThat(countRows("audit_log")).isEqualTo(1);
    }

    @Test
    @DisplayName("mảng TEXT[] changed_fields đi và về nguyên vẹn")
    void mangChangedFields_diVaVeNguyenVen() {
        auditLog.append(new AuditEntry("Person", "co-truong", AuditAction.UPDATE,
                        Map.of("occupation", "Giáo viên"), Map.of("occupation", "Nhà giáo"),
                        List.of("occupation", "biography"), "sửa theo yêu cầu đính chính"),
                AuditActor.system(), RequestFingerprint.none());

        List<AuditLogRepository.AuditLogRow> rows = auditLog.byEntity("Person", "co-truong", 10, 0);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).changedFields()).containsExactly("occupation", "biography");
        assertThat(rows.get(0).action()).isEqualTo(AuditAction.UPDATE.name());
    }

    private void append(String entityId, RequestFingerprint fingerprint) {
        auditLog.append(AuditEntry.of("Person", entityId, AuditAction.READ_SENSITIVE, null),
                AuditActor.system(), fingerprint);
    }
}

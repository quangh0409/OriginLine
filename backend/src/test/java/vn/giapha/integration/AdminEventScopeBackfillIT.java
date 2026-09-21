package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * <b>Dọn phạm vi cho sự kiện CŨ</b> — {@code POST /api/v1/admin/events/backfill-scope}.
 *
 * <h2>Vì sao những dòng này tồn tại, và vì sao phải ghi thẳng bằng SQL ở đây</h2>
 * {@code ck_event_scope} (V4) chỉ cấm đặt <i>cả hai</i> {@code is_clan_level} và
 * {@code target_branch_id}; nó không bắt buộc phải có <i>một</i>. Lối ghi thủ công đóng cửa ấy cho
 * bản ghi <b>mới</b> — nên một dòng "không phạm vi" <b>không tạo được qua HTTP nữa</b>, và cách duy
 * nhất để dựng lại trạng thái của dữ liệu cũ là ghi thẳng vào bảng. Đây là ngoại lệ có lý do, không
 * phải một lối đi tắt qua cổng phân quyền: chính những dòng ấy là thứ đang được kiểm.
 *
 * <p>Tới giờ nhắc, {@code NotificationDispatchService} mở một dòng như thế ra <b>cả dòng họ</b> —
 * với 1.500 người thì mỗi dòng là 1.500 thông báo không ai yêu cầu.</p>
 */
@DisplayName("Don pham vi su kien cu")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class AdminEventScopeBackfillIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private UUID chiGiap;
    private UUID cuToPersonId;

    @BeforeEach
    void dungDongHo() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");

        cuToPersonId = seed(PersonFixtures.deceased("Nguyễn Phúc Tổ", 1940)
                .branch(chiGiap).generation(1));

        insertAppUser("sub-admin", null);
        assignBranchRole(insertAppUser("sub-hoi-dong", null), "COUNCIL", null);
        UUID bonPersonId = seed(PersonFixtures.living("Nguyễn Văn Bốn")
                .branch(chiGiap).generation(6));
        assignBranchRole(insertAppUser("sub-bon", bonPersonId), "BRANCH_HEAD", chiGiap);
    }

    /** Một dòng cũ: không cấp dòng họ, không gắn chi. Chỉ ghi được bằng SQL — xem javadoc lớp. */
    private UUID gieoSuKienKhongPhamVi(String title, UUID personId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO event (id, person_id, event_type, title, lunar_date,"
                + " is_lunar_based, is_recurring, is_clan_level, target_branch_id)"
                + " VALUES (?, ?, ?, ?, '{\"month\":5,\"day\":10}'::jsonb, TRUE, TRUE, FALSE, NULL)",
                id, personId, personId == null ? "HOP_HO" : "GIO", title);
        return id;
    }

    private String chiCua(UUID eventId) {
        return jdbc.queryForObject("SELECT target_branch_id::text FROM event WHERE id = ?",
                String.class, eventId);
    }

    private boolean capDongHo(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_clan_level FROM event WHERE id = ?", Boolean.class, eventId));
    }

    @Test
    @DisplayName("gan chi theo ho so nhan khau; khong co nhan khau thi danh dau cap dong ho")
    void ganPhamViTheoHaiLuat() throws Exception {
        UUID coNhanKhau = gieoSuKienKhongPhamVi("Gio cu To", cuToPersonId);
        UUID khongNhanKhau = gieoSuKienKhongPhamVi("Hop ho cu", null);

        mockMvc.perform(post("/api/v1/admin/events/backfill-scope").with(quanTriHeThong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(2))
                .andExpect(jsonPath("$.assignedBranch").value(1))
                .andExpect(jsonPath("$.markedClanWide").value(1))
                .andExpect(jsonPath("$.changed").value(2));

        assertThat(chiCua(coNhanKhau)).isEqualTo(chiGiap.toString());
        assertThat(capDongHo(coNhanKhau)).isFalse();

        assertThat(chiCua(khongNhanKhau)).isNull();
        assertThat(capDongHo(khongNhanKhau))
                .as("danh dau tuong minh dung bang hanh vi hom nay cua loi gui, nhung doc duoc")
                .isTrue();
    }

    @Test
    @DisplayName("chay lai khong hong: luot thu hai sua 0 dong")
    void chayLaiDuocNhieuLan() throws Exception {
        gieoSuKienKhongPhamVi("Gio cu To", cuToPersonId);

        mockMvc.perform(post("/api/v1/admin/events/backfill-scope").with(quanTriHeThong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(1));

        mockMvc.perform(post("/api/v1/admin/events/backfill-scope").with(quanTriHeThong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(0))
                .andExpect(jsonPath("$.changed").value(0));
    }

    @Test
    @DisplayName("dryRun dem ma KHONG ghi — phai chay duoc truoc mot lenh cham toi ca ho")
    void dryRunDemMaKhongGhi() throws Exception {
        UUID id = gieoSuKienKhongPhamVi("Gio cu To", cuToPersonId);

        mockMvc.perform(post("/api/v1/admin/events/backfill-scope")
                        .param("dryRun", "true").with(hoiDongTocBieu()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(1))
                .andExpect(jsonPath("$.assignedBranch").value(1))
                .andExpect(jsonPath("$.changed").value(0));

        assertThat(chiCua(id)).isNull();
        assertThat(capDongHo(id)).isFalse();
    }

    @Test
    @DisplayName("Truong chi KHONG chay duoc — hau qua cua no vuot khoi moi pham vi chi")
    void truongChiKhongChayDuoc() throws Exception {
        gieoSuKienKhongPhamVi("Gio cu To", cuToPersonId);

        mockMvc.perform(post("/api/v1/admin/events/backfill-scope").with(truongChiGiap()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/events/backfill-scope"))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor quanTriHeThong() {
        return jwt().jwt(builder -> builder.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor hoiDongTocBieu() {
        return jwt().jwt(builder -> builder.subject("sub-hoi-dong"))
                .authorities(new SimpleGrantedAuthority("ROLE_COUNCIL"));
    }

    private RequestPostProcessor truongChiGiap() {
        return jwt().jwt(builder -> builder.subject("sub-bon"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }
}

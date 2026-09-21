package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.giapha.calendar.application.LunarCalendarService;
import vn.giapha.shared.vo.LunarDate;

/**
 * <b>Cấu hình việc họ</b> trên hạ tầng thật, <b>đi qua HTTP cho mọi luật phân quyền</b>.
 *
 * <h2>Vì sao phải là test tích hợp, không phải test đơn vị</h2>
 * <ol>
 *   <li><b>Phạm vi chi là một phép so {@code ltree} chạy trong SQL.</b> "Trưởng chi Ất không tạo
 *       được sự kiện cho chi Bính" là câu trả lời của toán tử {@code ltree} trong Postgres cộng với
 *       bảng {@code branch_assignment} — không bản mô phỏng trong bộ nhớ nào nói đúng về nó.</li>
 *   <li><b>Khoá lạc quan là một mệnh đề {@code WHERE version = ?}</b>, không phải một câu
 *       {@code if}. Bỏ {@code If-Match} hay gửi phiên bản cũ phải bị chặn bởi cơ sở dữ liệu.</li>
 *   <li><b>"Xoá mềm không làm mất lời nhắc đã phát" chỉ chứng minh được bằng phép đếm trên bảng
 *       thật.</b> Hai bảng, hai trạng thái, một lệnh xoá.</li>
 *   <li><b>Tháng nhuận và tháng thiếu</b> là câu trả lời của bộ quy đổi Hồ Ngọc Đức. Bài test dò
 *       năm nhuận/năm thiếu <i>từ chính bộ quy đổi</i> thay vì chép số liệu thiên văn vào đây —
 *       chép vào là tạo bản sao thứ hai của sự thật, và khi nó sai thì test đỏ ở chỗ không có
 *       lỗi.</li>
 * </ol>
 *
 * <p>Không ca nào ở đây ghi thẳng vào bảng {@code event} để đi tắt qua một cổng phân quyền.</p>
 */
@DisplayName("Tao/sua/xoa su kien dong ho")
@AutoConfigureMockMvc
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class EventCrudFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private LunarCalendarService calendar;

    private UUID goc;
    private UUID chiGiap;
    private UUID chiAt;

    /** Ông Bốn — Trưởng Chi Giáp. Cấu hình được việc của Chi Giáp, không đụng được Chi Ất. */
    private UUID bonPersonId;

    /** Ông Năm — Trưởng Chi Ất. */
    private UUID namPersonId;

    /** Bà Lan — thành viên Chi Giáp, còn sống, có tài khoản ⇒ nhận được nhắc. */
    private UUID lanPersonId;

    /** Ông Tư — thành viên Chi Ất, còn sống, có tài khoản ⇒ KHÔNG được nhắc việc của Chi Giáp. */
    private UUID tuPersonId;

    /** Cụ Tổ — đã khuất, có {@code death_lunar} (tháng 5 ngày 10 theo fixture). */
    private UUID cuToPersonId;

    @BeforeEach
    void dungDongHo() {
        goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        cuToPersonId = seed(PersonFixtures.deceased("Nguyễn Phúc Tổ", 1940).branch(goc).generation(1));

        bonPersonId = seed(PersonFixtures.living("Nguyễn Văn Bốn").branch(chiGiap).generation(6));
        assignBranchRole(insertAppUser("sub-bon", bonPersonId), "BRANCH_HEAD", chiGiap);

        namPersonId = seed(PersonFixtures.living("Nguyễn Văn Năm").branch(chiAt).generation(6));
        assignBranchRole(insertAppUser("sub-nam", namPersonId), "BRANCH_HEAD", chiAt);

        lanPersonId = seed(PersonFixtures.living("Nguyễn Thị Lan").branch(chiGiap).generation(7));
        insertAppUser("sub-lan", lanPersonId);

        tuPersonId = seed(PersonFixtures.living("Nguyễn Văn Tư").branch(chiAt).generation(7));
        insertAppUser("sub-tu", tuPersonId);

        // Hội đồng Tộc biểu: phân công KHÔNG gắn chi = phạm vi toàn dòng họ.
        assignBranchRole(insertAppUser("sub-hoi-dong", null), "COUNCIL", null);
        insertAppUser("sub-admin", null);
    }

    // =========================================================================================
    // Việc 1 — CRUD
    // =========================================================================================

    @Test
    @DisplayName("Truong chi tao duoc hop ho cho chi minh, va no hien ra o lo ghi doc")
    void truongChiTaoDuocViecCuaChiMinh() throws Exception {
        JsonNode created = taoSuKien(truongChiGiap(), Map.of(
                "eventType", "HOP_HO",
                "title", "Hop ho dau xuan Chi Giap",
                "lunarDate", Map.of("month", 1, "day", 6),
                "recurringAnnually", true,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString(),
                "location", "Tu duong Chi Giap"));

        UUID id = UUID.fromString(created.get("id").asText());
        // Mã loại đi về đúng HOP_HO chứ không rơi vào KHAC: đây là khoản nợ V10 vừa trả, và lối ghi
        // mới là chỗ duy nhất có thể làm nó quay lại.
        assertThat(created.get("eventType").asText()).isEqualTo("HOP_HO");
        assertThat(created.get("isClanLevel").asBoolean()).isFalse();
        assertThat(created.get("targetBranch").get("id").asText()).isEqualTo(chiGiap.toString());

        mockMvc.perform(get("/api/v1/events/{id}", id).with(truongChiGiap()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hop ho dau xuan Chi Giap"))
                .andExpect(jsonPath("$.eventType").value("HOP_HO"));
    }

    @Test
    @DisplayName("Du 12 loai cua hop dong deu ghi duoc - khong loai nao roi vao KHAC")
    void duMuoiHaiLoaiDeuGhiDuoc() throws Exception {
        // GIO_THUONG cần nhân khẩu và ngày giỗ lấy theo hồ sơ, nên nó có ca riêng bên dưới.
        List<String> loaiKhongCanNhanKhau = List.of("GIO_TO", "GIO_HO", "TIEU_TUONG", "DAI_TUONG",
                "CHAP_MA", "MUNG_THO", "SINH_NHAT", "KHANH_THANH", "HOP_HO", "CUOI_HOI", "KHAC");

        for (String loai : loaiKhongCanNhanKhau) {
            boolean capDongHo = "GIO_TO".equals(loai) || "GIO_HO".equals(loai);
            JsonNode created = taoSuKien(hoiDongTocBieu(), scopedBody(loai, capDongHo));
            assertThat(created.get("eventType").asText())
                    .as("loai %s phai giu nguyen ma, khong duoc gop vao KHAC", loai)
                    .isEqualTo(loai);
        }

        // GIO_CHI là mã thứ 12: cùng giá trị CSDL với GIO_HO (TE_LE) nhưng khác cờ cấp dòng họ.
        JsonNode gioChi = taoSuKien(truongChiGiap(), scopedBody("GIO_CHI", false));
        assertThat(gioChi.get("eventType").asText()).isEqualTo("GIO_CHI");
        assertThat(gioChi.get("isClanLevel").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Sua doi ngay am, If-Match dung thi qua, sai thi 409, thieu thi 412")
    void suaDoiNgayVoiKhoaLacQuan() throws Exception {
        JsonNode created = taoSuKien(truongChiGiap(), chapMaChiGiap());
        UUID id = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();

        // Thiếu If-Match: từ chối ghi mù.
        mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Chap ma Chi Giap 2026"))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        // If-Match cũ: có người khác vừa sửa.
        mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiGiap())
                        .header("If-Match", "\"" + (version + 5) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Chap ma doi ten"))))
                .andExpect(status().isConflict());

        // If-Match đúng: qua, và version tăng.
        MvcResult ok = mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiGiap())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "Chap ma Chi Giap - doi ngay",
                                "lunarDate", Map.of("month", 12, "day", 25)))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode updated = json.readTree(ok.getResponse().getContentAsString());
        // Phiên bản chỉ tăng khi thật sự có trường đổi giá trị — gửi lại y nguyên dữ liệu cũ thì
        // Hibernate không sinh câu UPDATE nào, và ETag đứng yên là đúng.
        assertThat(updated.get("version").asLong()).isGreaterThan(version);
        assertThat(updated.get("lunarDate").get("day").asInt()).isEqualTo(25);

        // Vắng mặt ≠ null: lượt PATCH trên chỉ gửi title + lunarDate, địa điểm phải còn nguyên.
        assertThat(updated.get("location").asText()).isEqualTo("Nghia trang ho Nguyen");
    }

    @Test
    @DisplayName("Xoa mem giu lai ban ghi va giu lai loi nhac DA PHAT, chi don lich chua ban")
    void xoaMemGiuBanGhiVaLoiNhacDaPhat() throws Exception {
        JsonNode created = taoSuKien(truongChiGiap(), chapMaChiGiap());
        UUID id = UUID.fromString(created.get("id").asText());

        int chuaBan = jdbc.queryForObject(
                "SELECT count(*) FROM reminder_job WHERE event_id = ? AND status = 'PENDING'",
                Integer.class, id);
        assertThat(chuaBan).as("tao su kien phai sinh lich nhac ngay, khong cho job 01:30")
                .isPositive();

        // Một lời nhắc "đã phát" của chính sự kiện này: đây là thứ tuyệt đối không được biến mất.
        UUID jobDaGui = UUID.randomUUID();
        jdbc.update("INSERT INTO reminder_job (id, event_id, occurrence_year, due_solar_date,"
                        + " offset_days, fire_at, status)"
                        + " VALUES (?, ?, ?, ?, ?, now(), 'SENT')",
                jobDaGui, id, LocalDate.now().getYear() - 1, java.sql.Date.valueOf(
                        LocalDate.now().minusDays(30)), 7);

        mockMvc.perform(delete("/api/v1/events/{id}", id).with(truongChiGiap())
                        .param("reason", "Nam nay ho gop chap ma voi gio To"))
                .andExpect(status().isNoContent());

        // Bản ghi ở lại — xoá cứng thì ON DELETE CASCADE kéo theo cả lịch sử gửi.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM event WHERE id = ?", Boolean.class, id))
                .isTrue();

        // Lời nhắc đã phát còn nguyên; lịch chưa bắn đã được dọn.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reminder_job WHERE id = ?", Integer.class, jobDaGui))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reminder_job WHERE event_id = ? AND status = 'PENDING'",
                Integer.class, id)).isZero();

        // Và nó biến mất khỏi lối đọc.
        mockMvc.perform(get("/api/v1/events/{id}", id).with(truongChiGiap()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Moi lan ghi deu de lai vet trong audit_log, khong co du lieu Tang 3")
    void moiLanGhiDeLaiVetKiemToan() throws Exception {
        JsonNode created = taoSuKien(truongChiGiap(), chapMaChiGiap());
        UUID id = UUID.fromString(created.get("id").asText());

        mockMvc.perform(delete("/api/v1/events/{id}", id).with(truongChiGiap()))
                .andExpect(status().isNoContent());

        List<String> hanhDong = jdbc.queryForList(
                "SELECT action FROM audit_log WHERE entity_type = 'event' AND entity_id = ?"
                        + " ORDER BY at", String.class, id.toString());
        assertThat(hanhDong).containsExactly("CREATE", "SOFT_DELETE");

        String noiDung = jdbc.queryForObject(
                "SELECT coalesce(after::text, '') || coalesce(before::text, '')"
                        + " FROM audit_log WHERE entity_type = 'event' AND entity_id = ?"
                        + " ORDER BY at LIMIT 1", String.class, id.toString());
        assertThat(noiDung).doesNotContain("0900000001", "nguoi@example.com");
    }

    // =========================================================================================
    // Việc 2 — lặp hằng năm theo lịch âm: tháng nhuận và tháng thiếu
    // =========================================================================================

    @Test
    @DisplayName("Thang nhuan: nam khong co thang nhuan ay thi roi vao thang THUONG cung so,"
            + " va bo nhac gio tra ve dung ngay ay")
    void thangNhuanRoiVaoThangThuongVaKhopVoiBoNhac() throws Exception {
        int thang = 6;
        int namKhongNhuan = namKhongNhuanThang(thang);

        JsonNode created = taoSuKien(truongChiGiap(), Map.of(
                "eventType", "GIO_CHI",
                "title", "Te le Chi Giap - ngay am chep vao thang nhuan",
                "lunarDate", Map.of("month", thang, "day", 3, "leap", true),
                "recurringAnnually", true,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString()));
        UUID id = UUID.fromString(created.get("id").asText());

        // Bộ sinh lịch nhắc, chạy như thể hôm nay ở đầu năm âm ấy.
        LocalDate moc = calendar.toSolar(new LunarDate(namKhongNhuan, 1, 15, false));
        mockMvc.perform(post("/api/v1/admin/reminders/generate").with(quanTriHeThong())
                        .param("referenceDate", moc.toString()))
                .andExpect(status().isOk());

        LocalDate thangThuong = calendar.toSolar(new LunarDate(namKhongNhuan, thang, 3, false));
        assertThat(calendar.toSolar(new LunarDate(namKhongNhuan, thang, 3, true)))
                .as("gia dinh cua bai test: nam %d khong nhuan thang %d", namKhongNhuan, thang)
                .isNull();

        List<LocalDate> ngayNhac = jdbc.queryForList(
                "SELECT DISTINCT due_solar_date FROM reminder_job WHERE event_id = ?",
                LocalDate.class, id);
        assertThat(ngayNhac)
                .as("bo nhac gio phai giai thang nhuan y het OccurrenceResolver, khong giai lai lan hai")
                .contains(thangThuong);
    }

    @Test
    @DisplayName("Thang thieu: ngay 30 o thang chi co 29 ngay thi lui ve 29, khong bao gio day muon")
    void thangThieuLuiVeNgay29() throws Exception {
        int thang = 12;
        int namThangThieu = namThangThieu(thang);

        JsonNode created = taoSuKien(truongChiGiap(), Map.of(
                "eventType", "GIO_CHI",
                "title", "Te le Chi Giap - ngay 30 thang Chap",
                "lunarDate", Map.of("month", thang, "day", 30),
                "recurringAnnually", true,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString()));
        UUID id = UUID.fromString(created.get("id").asText());

        LocalDate moc = calendar.toSolar(new LunarDate(namThangThieu, 1, 15, false));
        mockMvc.perform(post("/api/v1/admin/reminders/generate").with(quanTriHeThong())
                        .param("referenceDate", moc.toString()))
                .andExpect(status().isOk());

        LocalDate ngay29 = calendar.toSolar(new LunarDate(namThangThieu, thang, 29, false));
        assertThat(calendar.toSolar(new LunarDate(namThangThieu, thang, 30, false)))
                .as("gia dinh cua bai test: thang %d nam %d la thang thieu", thang, namThangThieu)
                .isNull();

        List<LocalDate> ngayNhac = jdbc.queryForList(
                "SELECT DISTINCT due_solar_date FROM reminder_job WHERE event_id = ?",
                LocalDate.class, id);
        assertThat(ngayNhac).contains(ngay29);

        // Chỉ được lùi SỚM, không bao giờ đẩy muộn. Xét riêng lần xảy ra của năm âm đã dò được:
        // tầm nhìn mặc định là 2 năm âm nên danh sách còn mang cả lần của năm kế tiếp.
        LocalDate tetSauDo = calendar.toSolar(new LunarDate(namThangThieu + 1, 1, 1, false));
        List<LocalDate> lanCuaNamAy = ngayNhac.stream().filter(ngay -> ngay.isBefore(tetSauDo)).toList();
        assertThat(lanCuaNamAy).containsExactly(ngay29);
    }

    @Test
    @DisplayName("Su kien MOT LAN theo am lich bat buoc co nam, va chi xay ra dung mot lan")
    void suKienMotLanBatBuocCoNam() throws Exception {
        // Thiếu năm: từ chối ngay, vì "ngày 12 tháng 2 âm" không quy đổi được sang ngày dương nào.
        mockMvc.perform(post("/api/v1/events").with(hoiDongTocBieu())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "eventType", "KHANH_THANH",
                                "title", "Khanh thanh tu duong",
                                "lunarDate", Map.of("month", 2, "day", 12),
                                "recurringAnnually", false,
                                "clanWide", true))))
                .andExpect(status().isBadRequest());

        int namToi = requireNonNullLunarYear(LocalDate.now().plusYears(1));
        JsonNode created = taoSuKien(hoiDongTocBieu(), Map.of(
                "eventType", "KHANH_THANH",
                "title", "Khanh thanh tu duong",
                "lunarDate", Map.of("year", namToi, "month", 2, "day", 12),
                "recurringAnnually", false,
                "clanWide", true));
        UUID id = UUID.fromString(created.get("id").asText());

        assertThat(created.get("recurringAnnually").asBoolean()).isFalse();
        // Đúng MỘT lần xảy ra: mỗi mốc nhắc sinh đúng một job, không lặp qua từng năm trong tầm nhìn.
        List<Integer> namXayRa = jdbc.queryForList(
                "SELECT DISTINCT occurrence_year FROM reminder_job WHERE event_id = ?",
                Integer.class, id);
        assertThat(namXayRa).hasSize(1);
    }

    // =========================================================================================
    // Việc 3 — phạm vi quyết định ai được nhắc, và ai được ghi
    // =========================================================================================

    @Test
    @DisplayName("Truong chi At KHONG tao duoc su kien cho chi Binh (Chi Giap)")
    void truongChiAtKhongTaoDuocChoChiGiap() throws Exception {
        mockMvc.perform(post("/api/v1/events").with(truongChiAt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(chapMaChiGiap())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_SCOPE_VIOLATION"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM event", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Truong chi KHONG sua duoc su kien cua chi khac, va khong chuyen duoc su kien sang chi minh")
    void truongChiKhongSuaDuocSuKienChiKhac() throws Exception {
        JsonNode created = taoSuKien(truongChiGiap(), chapMaChiGiap());
        UUID id = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();

        mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiAt())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Cua Chi At bay gio"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_SCOPE_VIOLATION"));

        // Và cũng không "cướp" được bằng cách đổi phạm vi sang chi mình.
        mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiAt())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scopeBranchId", chiAt.toString()))))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "SELECT target_branch_id FROM event WHERE id = ?", UUID.class, id))
                .isEqualTo(chiGiap);
    }

    @Test
    @DisplayName("Truong chi KHONG dat duoc su kien cap dong ho - Hoi dong thi duoc")
    void chiHoiDongDatDuocSuKienCapDongHo() throws Exception {
        Map<String, Object> gioHo = Map.of(
                "eventType", "GIO_HO",
                "title", "Gio ho Nguyen",
                "lunarDate", Map.of("month", 5, "day", 10),
                "recurringAnnually", true,
                "clanWide", true);

        mockMvc.perform(post("/api/v1/events").with(truongChiGiap())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(gioHo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        JsonNode created = taoSuKien(hoiDongTocBieu(), gioHo);
        assertThat(created.get("isClanLevel").asBoolean()).isTrue();
        // EventDto bỏ trường null khỏi JSON, nên "không gắn chi" nghĩa là khoá vắng mặt.
        assertThat(created.hasNonNull("targetBranch")).isFalse();
    }

    /**
     * Phạm vi phải <b>chảy tới tận bộ sinh lời nhắc</b>, không dừng ở bảng.
     *
     * <p>Ca này kiểm hai chặng quan sát được một cách tất định: phạm vi được ghi đúng, và lối đọc
     * lọc theo {@code ltree} thật của Postgres. Chặng cuối — "ai thật sự nhận thông báo" — nằm sau
     * một consumer bất đồng bộ, nên nó đã được ghim sẵn bằng
     * {@code DispatchDueRemindersServiceTest#chiThanhVienCungChiNhanNhac} (người chi khác không có
     * trong danh sách người nhận) thay vì bằng một vòng chờ trong test tích hợp. Khẳng định phủ
     * định trên một bảng do consumer ghi sẽ xanh cả khi consumer chưa kịp chạy — tức là kiểm đúng
     * cái không cần kiểm.</p>
     */
    @Test
    @DisplayName("Su kien pham vi mot chi khong lot sang chi khac o ca ban ghi lan loi doc")
    void suKienMotChiKhongLotSangChiKhac() throws Exception {
        JsonNode created = taoSuKien(truongChiGiap(), Map.of(
                "eventType", "HOP_HO",
                "title", "Hop Chi Giap",
                "solarDate", LocalDate.now().plusDays(10).toString(),
                "recurringAnnually", false,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString()));
        UUID id = UUID.fromString(created.get("id").asText());

        assertThat(demJob(id)).as("su kien mot lan cung phai co lich nhac").isPositive();
        assertThat(jdbc.queryForObject("SELECT is_clan_level FROM event WHERE id = ?",
                Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT target_branch_id FROM event WHERE id = ?",
                UUID.class, id)).isEqualTo(chiGiap);

        // Lối đọc: phép lọc chi chạy bằng toán tử ltree "<@" trong Postgres.
        mockMvc.perform(get("/api/v1/events").with(truongChiAt())
                        .param("branchId", chiAt.toString())
                        .param("upcomingDays", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/events").with(truongChiGiap())
                        .param("branchId", chiGiap.toString())
                        .param("upcomingDays", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").isNotEmpty());
    }

    @Test
    @DisplayName("Doi ngay thi lich nhac CU bi don di, khong con ai bi nhac theo ngay cu")
    void doiNgayThiDonLichNhacCu() throws Exception {
        LocalDate ngayCu = LocalDate.now().plusDays(20);
        LocalDate ngayMoi = LocalDate.now().plusDays(40);
        JsonNode created = taoSuKien(truongChiGiap(), Map.of(
                "eventType", "HOP_HO",
                "title", "Hop Chi Giap",
                "solarDate", ngayCu.toString(),
                "recurringAnnually", false,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString()));
        UUID id = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();

        assertThat(ngayNhacCua(id)).containsOnly(ngayCu);

        mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiGiap())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("solarDate", ngayMoi.toString()))))
                .andExpect(status().isOk());

        assertThat(ngayNhacCua(id))
                .as("con mot job tro ve ngay cu nghia la ca chi van den nham ngay")
                .containsOnly(ngayMoi);
    }

    /**
     * Một job đã bị <b>huỷ</b> không được chiếm khoá chống trùng của lần xảy ra ấy.
     *
     * <h2>Cuộc đua thật, không phải một trạng thái bịa ra</h2>
     * {@code DispatchDueRemindersService} đánh dấu {@code CANCELLED} những job nó vừa
     * {@code claimDue} (tức đã {@code QUEUED}) khi sự kiện vừa bị xoá mềm hoặc ngày đã trôi qua.
     * Nếu lượt sửa sự kiện rơi vào <i>giữa</i> {@code claimDue} và lượt gửi, thì lệnh dọn chỉ xoá
     * {@code PENDING} sẽ bỏ sót dòng ấy — và dòng ấy vẫn giữ {@code ux_reminder_job_occurrence},
     * nên {@code INSERT ... ON CONFLICT DO NOTHING} của lượt sinh sau <b>lặng lẽ không ghi gì,
     * mãi mãi</b>: đúng kiểu hỏng mà chú thích của V18 đã cảnh báo, chỉ là đến bằng một cuộc đua
     * thay vì bằng thiết kế.
     */
    @Test
    @DisplayName("Job da bi huy khong chiem khoa chong trung cua lan xay ra do")
    void jobDaHuyKhongChiemKhoaChongTrung() throws Exception {
        LocalDate ngayCu = LocalDate.now().plusDays(20);
        LocalDate ngayMoi = LocalDate.now().plusDays(40);
        JsonNode created = taoSuKien(truongChiGiap(), Map.of(
                "eventType", "HOP_HO",
                "title", "Hop Chi Giap",
                "solarDate", ngayCu.toString(),
                "recurringAnnually", false,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString()));
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(ngayNhacCua(id)).containsOnly(ngayCu);

        // Cuoc dua: luot gui vua danh dau cac job nay CANCELLED.
        jdbc.update("UPDATE reminder_job SET status = 'CANCELLED' WHERE event_id = ?", id);

        mockMvc.perform(patch("/api/v1/events/{id}", id).with(truongChiGiap())
                        .header("If-Match", "\"" + created.get("version").asLong() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("solarDate", ngayMoi.toString()))))
                .andExpect(status().isOk());

        assertThat(ngayNhacCua(id))
                .as("khong con job nao cho ngay moi nghia la ca chi khong duoc nhac lan nao nua")
                .containsOnly(ngayMoi);
    }

    @Test
    @DisplayName("Ngay gio lay theo ho so nhan khau: ghi mot ngay khac bi tu choi 409")
    void ngayGioLayTheoHoSoNhanKhau() throws Exception {
        // PersonFixtures.deceased gieo death_lunar = ngay 10 thang 5.
        mockMvc.perform(post("/api/v1/events").with(hoiDongTocBieu())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "eventType", "GIO_THUONG",
                                "title", "Gio cu To",
                                "personId", cuToPersonId.toString(),
                                "lunarDate", Map.of("month", 7, "day", 1),
                                "recurringAnnually", true,
                                "clanWide", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GIO_DATE_FROM_PERSON"));

        JsonNode created = taoSuKien(hoiDongTocBieu(), Map.of(
                "eventType", "GIO_THUONG",
                "title", "Gio cu To",
                "personId", cuToPersonId.toString(),
                "lunarDate", Map.of("month", 5, "day", 10),
                "recurringAnnually", true,
                "clanWide", true));
        assertThat(created.get("eventType").asText()).isEqualTo("GIO_THUONG");
    }

    /**
     * Vế <b>dương lịch</b> của cùng phép canh: gửi {@code solarDate} thay vì {@code lunarDate}
     * không được phép tháo ngày giỗ ra khỏi {@code person.death_lunar}.
     *
     * <p>Không có phép canh này thì lời nhắc giỗ bắn theo một ngày dương cố định và <b>trôi khoảng
     * 11 ngày mỗi năm</b> khỏi ngày giỗ thật — trong khi lượt ghi gây ra chuyện đó trả 201/200 và
     * để lại một dòng nhật ký không có gì bất thường.</p>
     */
    @Test
    @DisplayName("Ngay gio KHONG tach duoc khoi death_lunar bang cach gui solarDate")
    void ngayGioKhongTachDuocBangSolarDate() throws Exception {
        // (a) TAO thang mot cai gio theo duong lich.
        mockMvc.perform(post("/api/v1/events").with(hoiDongTocBieu())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "eventType", "GIO_THUONG",
                                "title", "Gio cu To theo duong lich",
                                "personId", cuToPersonId.toString(),
                                "solarDate", LocalDate.now().plusDays(30).toString(),
                                "recurringAnnually", true,
                                "clanWide", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GIO_DATE_FROM_PERSON"));

        // (b) SUA mot cai gio dung dan thanh duong lich — loi vong, cung phai dong.
        JsonNode created = taoSuKien(hoiDongTocBieu(), Map.of(
                "eventType", "GIO_THUONG",
                "title", "Gio cu To",
                "personId", cuToPersonId.toString(),
                "lunarDate", Map.of("month", 5, "day", 10),
                "recurringAnnually", true,
                "clanWide", true));
        UUID id = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();

        mockMvc.perform(patch("/api/v1/events/{id}", id).with(hoiDongTocBieu())
                        .header("If-Match", "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "solarDate", LocalDate.now().plusDays(30).toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GIO_DATE_FROM_PERSON"));

        // Va ban ghi trong CSDL khong nhuc nhich: van am lich, van ngay 10 thang 5.
        assertThat(jdbc.queryForObject(
                "SELECT is_lunar_based FROM event WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT lunar_date->>'day' FROM event WHERE id = ?", String.class, id))
                .isEqualTo("10");
    }

    /**
     * Giỗ <b>không lặp hằng năm</b> là một sự kiện lặng lẽ không bao giờ sinh lời nhắc.
     *
     * <p>Nó qua được phép canh ngày (ngày âm vẫn khớp hồ sơ), nhưng lúc đọc
     * {@code EffectiveLunarDate.of} thay ngày bằng {@code death_lunar}, mà {@code year()} của nó là
     * <b>năm mất</b> — nên lần xảy ra duy nhất rơi vào quá khứ.</p>
     */
    @Test
    @DisplayName("Gio phai lap hang nam - mot lan la mot lan xay ra trong qua khu")
    void gioPhaiLapHangNam() throws Exception {
        mockMvc.perform(post("/api/v1/events").with(hoiDongTocBieu())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "eventType", "GIO_THUONG",
                                "title", "Gio cu To mot lan",
                                "personId", cuToPersonId.toString(),
                                "lunarDate", Map.of("year", 1940, "month", 5, "day", 10),
                                "recurringAnnually", false,
                                "clanWide", true))))
                .andExpect(status().isBadRequest());

        assertThat(countRows("event")).isZero();
    }

    /**
     * Ràng buộc CSDL là lớp chặn cuối, dưới cả tầng Java.
     *
     * <p>Ghi thẳng bằng SQL ở đây là <b>cố ý</b> và là ngoại lệ duy nhất của lớp test này: câu hỏi
     * đang hỏi chính là "nếu một lối ghi nào đó đi vòng qua {@code EventCommandService} thì cơ sở
     * dữ liệu có còn đỡ không".</p>
     */
    @Test
    @DisplayName("CSDL tu choi mot dong GIO theo duong lich, ke ca khi di vong qua tang Java")
    void csdlTuChoiDongGioTheoDuongLich() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO event (person_id, event_type, title, solar_date,"
                                + " is_lunar_based, is_recurring, is_clan_level)"
                                + " VALUES (?, 'GIO', 'Gio ghi thang bang SQL', ?, FALSE, TRUE, TRUE)",
                        cuToPersonId, LocalDate.now().plusDays(10)))
                .hasMessageContaining("ck_event_gio_lunar_recurring");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO event (person_id, event_type, title, lunar_date,"
                                + " is_lunar_based, is_recurring, is_clan_level)"
                                + " VALUES (?, 'GIO', 'Gio mot lan ghi thang bang SQL',"
                                + " '{\"year\":1940,\"month\":5,\"day\":10}'::jsonb, TRUE, FALSE, TRUE)",
                        cuToPersonId))
                .hasMessageContaining("ck_event_gio_lunar_recurring");
    }

    /**
     * Bật cờ lặp trên một sự kiện âm lịch một-lần phải <b>bỏ năm âm đi</b>.
     *
     * <p>Chuẩn hoá chỉ chạy bên trong {@code applyDateSource}, mà nhánh âm lịch của hàm ấy chỉ chạy
     * khi thân yêu cầu <b>có</b> {@code lunarDate}. Nên một {@code PATCH} chỉ lật cờ lặp để lại
     * đúng cái "sự thật nửa vời" mà javadoc của chính hàm chuẩn hoá nói là không được để xảy ra:
     * một sự kiện lặp hằng năm mang năm 2027 trên giao diện, như thể nó chỉ có một năm.</p>
     */
    @Test
    @DisplayName("Bat co lap tren su kien am lich mot-lan thi nam am bi bo di")
    void batCoLapThiBoNamAm() throws Exception {
        int namToi = requireNonNullLunarYear(LocalDate.now().plusYears(1));
        JsonNode created = taoSuKien(hoiDongTocBieu(), Map.of(
                "eventType", "KHANH_THANH",
                "title", "Khanh thanh tu duong",
                "lunarDate", Map.of("year", namToi, "month", 2, "day", 12),
                "recurringAnnually", false,
                "clanWide", true));
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(jdbc.queryForObject("SELECT lunar_date ->> 'year' FROM event WHERE id = ?",
                String.class, id)).isEqualTo(String.valueOf(namToi));

        mockMvc.perform(patch("/api/v1/events/{id}", id).with(hoiDongTocBieu())
                        .header("If-Match", "\"" + created.get("version").asLong() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("recurringAnnually", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lunarDate.year").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT lunar_date ->> 'year' FROM event WHERE id = ?",
                String.class, id))
                .as("nam am con lai tren mot su kien nay da lap la mot su that nua voi")
                .isNull();
    }

    /**
     * Một dòng {@code lunar_date} hỏng <b>không được làm sập lối đọc của cả dòng họ</b>.
     *
     * <p>{@code ck_event_date_source} (V4) chỉ đòi {@code lunar_date} <i>có khoá</i> {@code day} và
     * {@code month} — không kiểm khoảng giá trị. Nên {@code {"month": 13}} là một dòng hợp lệ với
     * cơ sở dữ liệu mà {@code LunarDate} từ chối. {@code EventMapper.toLunar} đã có chính sách
     * "cảnh báo rồi đi tiếp" cho đúng ca này, nhưng nó trả {@code null} cho một sự kiện âm lịch,
     * và constructor của {@code Event} lại từ chối {@code lunarBased} mà không có ngày — nên chính
     * sách ấy bị vô hiệu hoá: <b>một</b> dòng hỏng làm {@code GET /api/v1/events} ném lỗi với
     * <b>mọi</b> người, và giết luôn bộ sinh lời nhắc ban đêm.</p>
     */
    @Test
    @DisplayName("Mot dong lunar_date hong bi bo qua, khong lam sap ca lo ghi doc")
    void dongLunarDateHongKhongLamSapLoGhiDoc() throws Exception {
        JsonNode lanh = taoSuKien(hoiDongTocBieu(), Map.of(
                "eventType", "HOP_HO",
                "title", "Hop ho lanh lan",
                "lunarDate", Map.of("month", 3, "day", 3),
                "recurringAnnually", true,
                "clanWide", true));

        // Ghi thang bang SQL: cau hoi dang hoi chinh la "neu mot dong nhu the lot vao bang thi sao".
        jdbc.update("INSERT INTO event (event_type, title, lunar_date, is_lunar_based,"
                + " is_recurring, is_clan_level) VALUES ('HOP_HO', 'Dong hong',"
                + " '{\"month\":13,\"day\":40}'::jsonb, TRUE, TRUE, TRUE)");

        mockMvc.perform(get("/api/v1/events").with(hoiDongTocBieu()).param("upcomingDays", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + lanh.get("id").asText() + "')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.title=='Dong hong')]").isEmpty());
    }

    @Test
    @DisplayName("Bo trong ca hai pham vi bi tu choi - vi he thong se mac dinh nhac CA HO")
    void boTrongPhamViBiTuChoi() throws Exception {
        mockMvc.perform(post("/api/v1/events").with(hoiDongTocBieu())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "eventType", "HOP_HO",
                                "title", "Hop ho khong pham vi",
                                "lunarDate", Map.of("month", 3, "day", 3),
                                "recurringAnnually", true,
                                "clanWide", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khach vang lai khong cau hinh duoc viec ho")
    void khachKhongCauHinhDuocViecHo() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(chapMaChiGiap())))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================================
    // Tiện ích
    // =========================================================================================

    private JsonNode taoSuKien(RequestPostProcessor nguoiGoi, Map<String, Object> body)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/events").with(nguoiGoi)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> chapMaChiGiap() {
        return Map.of(
                "eventType", "CHAP_MA",
                "title", "Chap ma Chi Giap",
                "lunarDate", Map.of("month", 12, "day", 20),
                "recurringAnnually", true,
                "clanWide", false,
                "scopeBranchId", chiGiap.toString(),
                "location", "Nghia trang ho Nguyen");
    }

    private Map<String, Object> scopedBody(String loai, boolean capDongHo) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("eventType", loai);
        body.put("title", "Su kien " + loai);
        body.put("lunarDate", Map.of("month", 3, "day", 3));
        body.put("recurringAnnually", true);
        body.put("clanWide", capDongHo);
        if (!capDongHo) {
            body.put("scopeBranchId", chiGiap.toString());
        }
        return body;
    }

    private int demJob(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM reminder_job WHERE event_id = ?",
                Integer.class, eventId);
    }

    private List<LocalDate> ngayNhacCua(UUID eventId) {
        return jdbc.queryForList("SELECT DISTINCT due_solar_date FROM reminder_job WHERE event_id = ?",
                LocalDate.class, eventId);
    }

    /**
     * Năm âm <b>không</b> nhuận tháng {@code thang}, dò từ chính bộ quy đổi.
     *
     * <p>Không hằng số hoá "năm 2028 không nhuận tháng 6": nhúng số liệu thiên văn vào bài test là
     * tạo ra một bản sao thứ hai của sự thật, và khi nó sai thì test đỏ ở chỗ không có lỗi.</p>
     */
    private int namKhongNhuanThang(int thang) {
        int namHienTai = requireNonNullLunarYear(LocalDate.now());
        for (int nam = namHienTai; nam <= namHienTai + 25; nam++) {
            if (calendar.toSolar(new LunarDate(nam, thang, 1, true)) == null
                    && calendar.toSolar(new LunarDate(nam, thang, 1, false)) != null) {
                return nam;
            }
        }
        throw new IllegalStateException("Khong tim thay nam nao khong nhuan thang " + thang);
    }

    /** Năm mà tháng {@code thang} là tháng thiếu (không có ngày 30). */
    private int namThangThieu(int thang) {
        int namHienTai = requireNonNullLunarYear(LocalDate.now());
        for (int nam = namHienTai; nam <= namHienTai + 25; nam++) {
            if (calendar.toSolar(new LunarDate(nam, thang, 30, false)) == null
                    && calendar.toSolar(new LunarDate(nam, thang, 29, false)) != null) {
                return nam;
            }
        }
        throw new IllegalStateException("Khong tim thay nam nao thang " + thang + " thieu ngay 30");
    }

    private int requireNonNullLunarYear(LocalDate solar) {
        LunarDate lunar = calendar.toLunar(solar);
        if (lunar == null) {
            throw new IllegalStateException("Khong quy doi duoc " + solar + " sang am lich");
        }
        return lunar.year();
    }

    private RequestPostProcessor truongChiGiap() {
        return jwt().jwt(builder -> builder.subject("sub-bon"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    private RequestPostProcessor truongChiAt() {
        return jwt().jwt(builder -> builder.subject("sub-nam"))
                .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD"));
    }

    private RequestPostProcessor hoiDongTocBieu() {
        return jwt().jwt(builder -> builder.subject("sub-hoi-dong"))
                .authorities(new SimpleGrantedAuthority("ROLE_COUNCIL"));
    }

    private RequestPostProcessor quanTriHeThong() {
        return jwt().jwt(builder -> builder.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

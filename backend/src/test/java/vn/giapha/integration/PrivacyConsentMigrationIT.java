package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.PrivacyLevel;

/**
 * <b>Di trú {@code privacy_level} → {@code privacy_consent} không được làm lộ thêm gì.</b>
 *
 * <p>Đây là ca test khó bịa nhất và cũng là ca duy nhất trả lời được câu hỏi mà Hội đồng Tộc biểu
 * sẽ hỏi: "đổi mô hình riêng tư thì dữ liệu của tôi có bị mở ra thêm không?". Câu trả lời phải là
 * <b>không, một trường nào cũng không</b> — và phải chứng minh được, không phải hứa.</p>
 *
 * <h2>Cách chứng minh</h2>
 * Lớp này chép lại <b>nguyên luật cũ</b> (trước V8) thành một oracle thuần hàm
 * ({@link #truongThayDuocTheoLuatCu}), rồi với từng {@code PrivacyLevel} cũ × từng loại người xem,
 * so tập trường thực sự đi ra khỏi hệ thống <i>sau</i> di trú với tập trường mà luật cũ cho phép.
 * Bất biến cần giữ là <b>tập con</b>, không phải bằng nhau: di trú được phép siết thêm, không được
 * phép nới.
 *
 * <p>Dữ liệu được gieo qua {@code PersonFixtures.privacy(...)}, vốn tính
 * {@code privacy_consent} bằng chính hàm SQL {@code public.privacy_consent_from_legacy} mà
 * migration {@code V8} dùng — nên cái được kiểm ở đây là phép di trú thật, không phải một bản
 * mô phỏng trong Java.</p>
 *
 * <h2>Một chỗ CỐ Ý nới, và nó không nằm trong ma trận này</h2>
 * Hội đồng Tộc biểu ({@code COUNCIL}) trước V8 bị kẹt ở Tầng 2 và không đọc được khối liên hệ của
 * người còn sống. Mô hình mới định nghĩa mức "Riêng tư" là <b>chỉ chính chủ + Hội đồng</b>, nên
 * {@code COUNCIL} nay đọc được. Đó là hệ quả trực tiếp của định nghĩa đã chốt, không phải một tác
 * dụng phụ của phép di trú — {@code PrivacyTierIT} ghim nó thành một ca riêng, tách bạch.
 */
@DisplayName("Di trú PrivacyLevel → PrivacyConsent: không lộ thêm gì")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PrivacyConsentMigrationIT extends AbstractIntegrationTest {

    /** Nhãn của từng mảnh dữ liệu có thể rời khỏi hệ thống. Đủ thô để so tập, đủ mịn để có nghĩa. */
    private static final String NGHE_NGHIEP = "occupation";
    private static final String TINH = "residenceProvince";
    private static final String DIA_CHI_DAY_DU = "residenceFull";
    private static final String LIEN_HE = "contact";
    private static final String ANH = "avatar";
    private static final String NGAY_SINH_DAY_DU = "birthDay";
    private static final String NAM_SINH = "birthYear";
    private static final String NGUYEN_QUAN = "nativePlace";
    private static final String TIEU_SU = "biography";
    private static final String THUOC_TINH = "attributes";
    private static final String TEN_PHU = "secondaryNames";

    /** Luật cũ: Tầng 2 mở đúng năm mảnh này. */
    private static final Set<String> TANG_2 =
            Set.of(NGUYEN_QUAN, TINH, NGHE_NGHIEP, NAM_SINH, TEN_PHU);

    /** Luật cũ: Tầng 3 là Tầng 2 cộng toàn bộ khối nhạy cảm. */
    private static final Set<String> TANG_3 = Set.of(NGUYEN_QUAN, TINH, NGHE_NGHIEP, NAM_SINH,
            TEN_PHU, DIA_CHI_DAY_DU, LIEN_HE, ANH, NGAY_SINH_DAY_DU, TIEU_SU, THUOC_TINH);

    @Autowired
    private PersonQueryService personQuery;

    private UUID chiGiap;
    private UUID chiAt;

    /** Một chủ thể cho mỗi giá trị {@code privacy_level} cũ. */
    private final Map<PrivacyLevel, UUID> chuThe = new EnumMap<>(PrivacyLevel.class);

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        UUID cungChi = seed(PersonFixtures.living("Nguyễn Văn Cùng Chi")
                .birthYear(1980).branch(chiGiap).generation(4));
        UUID khacChi = seed(PersonFixtures.living("Nguyễn Văn Khác Chi")
                .birthYear(1982).branch(chiAt).generation(4));
        insertAppUser("sub-cung-chi", cungChi);
        insertAppUser("sub-khac-chi", khacChi);

        chuThe.clear();
        for (PrivacyLevel level : PrivacyLevel.values()) {
            UUID id = seed(PersonFixtures.living("Nguyễn Văn " + level.name())
                    .birthYear(1975).branch(chiGiap).generation(3)
                    .tabooName("Nguyễn Văn Huý " + level.name())
                    .privacy(level));
            chuThe.put(level, id);
            insertAppUser("sub-self-" + level.name(), id);
        }
        authenticateAsGuest();
    }

    // =====================================================================================
    // Bất biến chính
    // =====================================================================================

    @Test
    @DisplayName("Mọi ô của ma trận: tập trường sau di trú là TẬP CON của tập trước di trú")
    void khongLoThemGiSoVoiTruoc() {
        List<String> viPham = new ArrayList<>();

        for (PrivacyLevel level : PrivacyLevel.values()) {
            UUID target = chuThe.get(level);

            for (LoaiNguoiXem xem : LoaiNguoiXem.values()) {
                if (xem == LoaiNguoiXem.KHACH) {
                    continue; // luật cũ và luật mới đều giấu hẳn bản ghi; kiểm riêng bên dưới
                }
                dangNhapNhu(xem, level);
                Set<String> sauDiTru = truongThucSuHienRa(personQuery.byId(target));
                Set<String> truocDiTru = truongThayDuocTheoLuatCu(level, xem);

                Set<String> loThem = new LinkedHashSet<>(sauDiTru);
                loThem.removeAll(truocDiTru);
                if (!loThem.isEmpty()) {
                    viPham.add(level + " / " + xem + " lộ thêm " + loThem);
                }
            }
        }

        assertThat(viPham)
                .as("di trú được phép siết thêm, KHÔNG được phép nới — thà hỏi lại còn hơn lộ")
                .isEmpty();
    }

    @Test
    @DisplayName("Khách vẫn không thấy người còn sống sau di trú, kể cả bản ghi từng CLAN_OPT_IN")
    void khachVanKhongThayNguoiConSong() {
        authenticateAsGuest();

        for (PrivacyLevel level : PrivacyLevel.values()) {
            assertThat(personQuery.find(chuThe.get(level)))
                    .as("mức cũ %s", level)
                    .isEmpty();
        }
    }

    // =====================================================================================
    // Từng dòng của bảng ánh xạ
    // =====================================================================================

    @Test
    @DisplayName("DEFAULT là sự vắng mặt của lựa chọn ⇒ di trú về kín hoàn toàn")
    void defaultDiTruVeKinHoanToan() {
        authenticateAs("sub-cung-chi", "MEMBER");

        Set<String> hienRa = truongThucSuHienRa(personQuery.byId(chuThe.get(PrivacyLevel.DEFAULT)));

        assertThat(hienRa)
                .as("nhóm trường do người dùng điều khiển phải đóng hết: chưa ai từng đồng ý mở")
                .doesNotContain(NGHE_NGHIEP, TINH, DIA_CHI_DAY_DU, LIEN_HE, ANH, NGAY_SINH_DAY_DU);
        assertThat(hienRa)
                .as("kể cả ba mảnh ngoài nhóm trường: luật cố định cho chúng là kín nhất có thể, "
                        + "vì người dùng không có công tắc nào để đóng lại")
                .isEmpty();
    }

    @Test
    @DisplayName("BRANCH_OPT_IN giữ đúng ý chí 'cho người cùng chi xem', không rộng hơn")
    void branchOptInGiuDungPhamVi() {
        UUID target = chuThe.get(PrivacyLevel.BRANCH_OPT_IN);

        authenticateAs("sub-cung-chi", "MEMBER");
        assertThat(truongThucSuHienRa(personQuery.byId(target)))
                .contains(NGHE_NGHIEP, TINH, DIA_CHI_DAY_DU, LIEN_HE, ANH, NGAY_SINH_DAY_DU);

        authenticateAs("sub-khac-chi", "MEMBER");
        assertThat(truongThucSuHienRa(personQuery.byId(target)))
                .as("người khác chi chưa bao giờ được xem, và sau di trú vẫn không")
                .doesNotContain(NGHE_NGHIEP, TINH, DIA_CHI_DAY_DU, LIEN_HE, ANH, NGAY_SINH_DAY_DU);
    }

    @Test
    @DisplayName("CLAN_OPT_IN giữ đúng ý chí 'cho cả họ xem'")
    void clanOptInGiuDungYChi() {
        authenticateAs("sub-khac-chi", "MEMBER");

        assertThat(truongThucSuHienRa(personQuery.byId(chuThe.get(PrivacyLevel.CLAN_OPT_IN))))
                .contains(NGHE_NGHIEP, TINH, DIA_CHI_DAY_DU, LIEN_HE, ANH, NGAY_SINH_DAY_DU);
    }

    @Test
    @DisplayName("RESTRICTED vẫn kín với cả người cùng chi")
    void restrictedVanKin() {
        authenticateAs("sub-cung-chi", "MEMBER");

        assertThat(truongThucSuHienRa(personQuery.byId(chuThe.get(PrivacyLevel.RESTRICTED))))
                .doesNotContain(NGHE_NGHIEP, TINH, DIA_CHI_DAY_DU, LIEN_HE, ANH, NGAY_SINH_DAY_DU);
    }

    // =====================================================================================
    // Bản thân phép di trú ở tầng CSDL
    // =====================================================================================

    @Test
    @DisplayName("Hàm SQL di trú khớp từng dòng với PrivacyLevel.toConsent() bên Java")
    void hamSqlKhopVoiJava() {
        for (PrivacyLevel level : PrivacyLevel.values()) {
            String sql = jdbc.queryForObject(
                    "SELECT public.privacy_consent_from_legacy(?)::text", String.class,
                    level.dbValue());

            assertThat(sql).isNotNull();
            level.toConsent().toJson().forEach((khoa, muc) ->
                    assertThat(sql)
                            .as("mức cũ %s, nhóm %s", level, khoa)
                            .contains("\"" + khoa + "\": \"" + muc + "\""));
        }
    }

    @Test
    @DisplayName("Ràng buộc CSDL từ chối khoá lạ và mức lạ trong privacy_consent")
    void rangBuocTuChoiKhoaLaVaMucLa() {
        UUID id = chuThe.get(PrivacyLevel.DEFAULT);

        assertThat(capNhatConsentThatBai(id, "{\"khoaLa\": \"CLAN\"}"))
                .as("khoá gõ sai lọt qua sẽ đọc thành PRIVATE — im lặng và sai hướng, phải chặn sớm")
                .isTrue();
        assertThat(capNhatConsentThatBai(id, "{\"contact\": \"EVERYONE\"}"))
                .as("mức lạ không được phép tồn tại trong cột")
                .isTrue();
        assertThat(capNhatConsentThatBai(id, "\"CLAN\""))
                .as("privacy_consent phải là object, không phải chuỗi")
                .isTrue();
    }

    @Test
    @DisplayName("Hàng mới không khai báo gì thì mặc định KÍN hoàn toàn")
    void hangMoiMacDinhKin() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO person (id, gender, is_alive, primary_branch_id)"
                + " VALUES (?, 'MALE', TRUE, ?)", id, chiGiap);

        String consent = jdbc.queryForObject(
                "SELECT privacy_consent::text FROM person WHERE id = ?", String.class, id);

        assertThat(consent)
                .as("object rỗng đọc ra là cả năm nhóm PRIVATE — không cần migration cho nhóm mới thêm")
                .isEqualTo("{}");
    }

    // =====================================================================================
    // Nội bộ
    // =====================================================================================

    private enum LoaiNguoiXem { CHINH_CHU, CUNG_CHI, KHAC_CHI, KHACH }

    private void dangNhapNhu(LoaiNguoiXem xem, PrivacyLevel level) {
        switch (xem) {
            case CHINH_CHU -> authenticateAs("sub-self-" + level.name(), "MEMBER");
            case CUNG_CHI -> authenticateAs("sub-cung-chi", "MEMBER");
            case KHAC_CHI -> authenticateAs("sub-khac-chi", "MEMBER");
            case KHACH -> authenticateAsGuest();
        }
    }

    /** Những mảnh dữ liệu thực sự rời khỏi hệ thống trong phản hồi này. */
    private static Set<String> truongThucSuHienRa(PersonView view) {
        Set<String> fields = new LinkedHashSet<>();
        if (view.occupation() != null) {
            fields.add(NGHE_NGHIEP);
        }
        if (view.currentPlaceProvince() != null) {
            fields.add(TINH);
        }
        if (view.currentPlaceFull() != null) {
            fields.add(DIA_CHI_DAY_DU);
        }
        if (view.contact() != null) {
            fields.add(LIEN_HE);
        }
        if (view.avatarKey() != null) {
            fields.add(ANH);
        }
        if (view.nativePlace() != null) {
            fields.add(NGUYEN_QUAN);
        }
        if (view.biography() != null) {
            fields.add(TIEU_SU);
        }
        if (view.attributes() != null && !view.attributes().isEmpty()) {
            fields.add(THUOC_TINH);
        }
        if (view.birth() != null) {
            fields.add(NAM_SINH);
            if (view.birth().precision() == DatePrecision.DAY) {
                fields.add(NGAY_SINH_DAY_DU);
            }
        }
        if (view.names() != null && view.names().size() > 1) {
            fields.add(TEN_PHU);
        }
        return fields;
    }

    /**
     * <b>Oracle: luật trước V8, chép lại nguyên văn.</b>
     *
     * <p>Chép tay thay vì giữ code cũ chạy song song là cố ý — code cũ đã bị xoá, và một oracle
     * chép tay còn buộc người đọc test phải nhìn thẳng vào luật cũ thay vì tin một lớp adapter.
     * Nguyên bản: chính chủ và {@code ADMIN} ⇒ T3; {@code RESTRICTED} ⇒ T1; {@code CLAN_OPT_IN}
     * ⇒ T3; {@code BRANCH_OPT_IN} và trong phạm vi ⇒ T3; trong phạm vi ⇒ T2; còn lại ⇒ T1.</p>
     */
    private static Set<String> truongThayDuocTheoLuatCu(PrivacyLevel level, LoaiNguoiXem xem) {
        if (xem == LoaiNguoiXem.KHACH) {
            return Set.of();
        }
        if (xem == LoaiNguoiXem.CHINH_CHU) {
            return TANG_3;
        }
        boolean trongPhamVi = xem == LoaiNguoiXem.CUNG_CHI;
        return switch (level) {
            case RESTRICTED -> Set.of();
            case CLAN_OPT_IN -> TANG_3;
            case BRANCH_OPT_IN -> trongPhamVi ? TANG_3 : Set.of();
            case DEFAULT -> trongPhamVi ? TANG_2 : Set.of();
        };
    }

    /** {@code true} nếu CSDL từ chối giá trị — nghĩa là ràng buộc đang làm việc của nó. */
    private boolean capNhatConsentThatBai(UUID personId, String json) {
        try {
            jdbc.update("UPDATE person SET privacy_consent = CAST(? AS jsonb) WHERE id = ?",
                    json, personId);
            return false;
        } catch (RuntimeException ex) {
            return true;
        }
    }
}

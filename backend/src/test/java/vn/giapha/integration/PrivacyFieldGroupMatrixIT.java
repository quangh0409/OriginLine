package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.DatePrecision;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;

/**
 * <b>Ma trận đầy đủ của mô hình đồng thuận</b>: 5 nhóm trường × 3 mức chia sẻ × 4 loại người xem,
 * chạy trên PostgreSQL + Apache AGE thật.
 *
 * <h2>Vì sao phải là test tích hợp</h2>
 * Hai trong bốn loại người xem chỉ tồn tại khi có CSDL: "cùng chi" và "khác chi" được phân giải
 * qua chuỗi {@code keycloak_sub → app_user → person.primary_branch_id → branch.path} rồi so bằng
 * toán tử {@code ltree}. Test unit dựng {@code CallerContext} bằng tay sẽ xanh kể cả khi chuỗi ấy
 * đứt ở giữa — và đó đúng là chỗ dễ sai nhất của cả tính năng.
 *
 * <h2>Ma trận kỳ vọng</h2>
 * <table>
 *   <caption>Nhóm trường có hiện ra hay không</caption>
 *   <tr><th>Mức</th><th>Chính chủ</th><th>Cùng chi</th><th>Khác chi</th><th>Khách</th></tr>
 *   <tr><td>{@code PRIVATE}</td><td>hiện</td><td>ẩn</td><td>ẩn</td><td>không thấy hồ sơ</td></tr>
 *   <tr><td>{@code BRANCH}</td><td>hiện</td><td>hiện</td><td>ẩn</td><td>không thấy hồ sơ</td></tr>
 *   <tr><td>{@code CLAN}</td><td>hiện</td><td>hiện</td><td>hiện</td><td>không thấy hồ sơ</td></tr>
 * </table>
 *
 * <p>Cột "Khách" <b>không</b> phụ thuộc vào mức: đó là ranh giới pháp lý của Nghị định 13/2023,
 * không nằm trong tay người dùng. Một ô {@code CLAN} mà Khách nhìn thấy được là lỗi nghiêm trọng
 * nhất mà lớp này có thể để lọt, nên nó được kiểm cho cả 15 tổ hợp chứ không chỉ một.</p>
 */
@DisplayName("Ma trận riêng tư: 5 nhóm trường × 3 mức × 4 loại người xem")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PrivacyFieldGroupMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private PersonQueryService personQuery;

    private UUID chiGiap;
    private UUID chiAt;

    /** Người cùng chi Giáp với các chủ thể đang bị xem. */
    private UUID nguoiCungChi;
    /** Người ở chi Ất — trong họ, đã đăng nhập, nhưng khác chi. */
    private UUID nguoiKhacChi;

    /** Chủ thể theo từng mức chia sẻ; mỗi mức một nhân khẩu riêng để không phải sửa dữ liệu giữa ca. */
    private final Map<ShareScope, UUID> chuThe = new EnumMap<>(ShareScope.class);

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");

        nguoiCungChi = seed(PersonFixtures.living("Nguyễn Văn Cùng Chi")
                .birthYear(1980).branch(chiGiap).generation(4));
        nguoiKhacChi = seed(PersonFixtures.living("Nguyễn Văn Khác Chi")
                .birthYear(1982).branch(chiAt).generation(4));
        insertAppUser("sub-cung-chi", nguoiCungChi);
        insertAppUser("sub-khac-chi", nguoiKhacChi);

        chuThe.clear();
        for (ShareScope scope : ShareScope.values()) {
            UUID id = seed(PersonFixtures.living("Nguyễn Văn " + scope.name())
                    .birthYear(1975).branch(chiGiap).generation(3)
                    .consent(moTatCa(scope)));
            chuThe.put(scope, id);
            insertAppUser("sub-self-" + scope.name(), id);
        }
        authenticateAsGuest();
    }

    /** Bản đồng thuận đặt <b>cả năm nhóm</b> về cùng một mức — trục "mức" của ma trận. */
    private static PrivacyConsent moTatCa(ShareScope scope) {
        PrivacyConsent consent = PrivacyConsent.allPrivate();
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            consent = consent.with(group, scope);
        }
        return consent;
    }

    // =====================================================================================
    // Ma trận chính
    // =====================================================================================

    @Test
    @DisplayName("Chính chủ đọc được cả năm nhóm ở mọi mức — kể cả khi tự đặt Riêng tư")
    void chinhChu_docDuocMoiNhomOMoiMuc() {
        List<String> viPham = new ArrayList<>();
        for (ShareScope scope : ShareScope.values()) {
            authenticateAs("sub-self-" + scope.name(), "MEMBER");
            PersonView view = personQuery.byId(chuThe.get(scope));
            assertThat(view.access().isSelf()).isTrue();
            for (Map.Entry<PrivacyFieldGroup, Boolean> o : hienRa(view).entrySet()) {
                if (!o.getValue()) {
                    viPham.add(scope + "/" + o.getKey() + " bi an voi chinh chu");
                }
            }
        }
        assertThat(viPham)
                .as("muc Rieng tu la 'chi chinh chu + Hoi dong', khong phai 'khong ai'")
                .isEmpty();
    }

    @Test
    @DisplayName("Người cùng chi: thấy ở mức Cùng chi và Cả họ, không thấy ở mức Riêng tư")
    void nguoiCungChi_thayTuMucBranchTroLen() {
        authenticateAs("sub-cung-chi", "MEMBER");

        assertThat(hienRa(personQuery.byId(chuThe.get(ShareScope.PRIVATE))))
                .as("Riêng tư là riêng tư, kể cả với người trong chi")
                .allSatisfy((nhom, hien) -> assertThat(hien).as(nhom.name()).isFalse());
        assertThat(hienRa(personQuery.byId(chuThe.get(ShareScope.BRANCH))))
                .allSatisfy((nhom, hien) -> assertThat(hien).as(nhom.name()).isTrue());
        assertThat(hienRa(personQuery.byId(chuThe.get(ShareScope.CLAN))))
                .allSatisfy((nhom, hien) -> assertThat(hien).as(nhom.name()).isTrue());
    }

    @Test
    @DisplayName("Người khác chi: chỉ thấy ở mức Cả họ")
    void nguoiKhacChi_chiThayOMucClan() {
        authenticateAs("sub-khac-chi", "MEMBER");

        assertThat(hienRa(personQuery.byId(chuThe.get(ShareScope.PRIVATE))))
                .allSatisfy((nhom, hien) -> assertThat(hien).as(nhom.name()).isFalse());
        assertThat(hienRa(personQuery.byId(chuThe.get(ShareScope.BRANCH))))
                .as("'Cùng chi' phải được phân giải bằng ltree, không phải bằng 'đã đăng nhập'")
                .allSatisfy((nhom, hien) -> assertThat(hien).as(nhom.name()).isFalse());
        assertThat(hienRa(personQuery.byId(chuThe.get(ShareScope.CLAN))))
                .allSatisfy((nhom, hien) -> assertThat(hien).as(nhom.name()).isTrue());
    }

    @Test
    @DisplayName("Khách không thấy hồ sơ người còn sống ở BẤT KỲ mức nào — 15/15 ô")
    void khach_khongThayOBatKyO() {
        authenticateAsGuest();

        for (ShareScope scope : ShareScope.values()) {
            Optional<PersonView> view = personQuery.find(chuThe.get(scope));
            assertThat(view)
                    .as("mức %s: đồng thuận của chủ thể là với NGƯỜI TRONG HỌ, không phải với Internet",
                            scope)
                    .isEmpty();
        }
    }

    // =====================================================================================
    // Từng nhóm một mức — điều mà mô hình cũ không làm được
    // =====================================================================================

    @Test
    @DisplayName("Năm nhóm năm mức khác nhau trên cùng một hồ sơ")
    void namNhomNamMucKhacNhau() {
        UUID hon = seed(PersonFixtures.living("Nguyễn Văn Hỗn Hợp")
                .birthYear(1978).branch(chiGiap).generation(3)
                .consent(PrivacyConsent.allPrivate()
                        .with(PrivacyFieldGroup.OCCUPATION, ShareScope.CLAN)
                        .with(PrivacyFieldGroup.RESIDENCE_PROVINCE, ShareScope.CLAN)
                        .with(PrivacyFieldGroup.RESIDENCE_FULL, ShareScope.BRANCH)
                        .with(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH)
                        .with(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO, ShareScope.PRIVATE)));

        authenticateAs("sub-khac-chi", "MEMBER");
        Map<PrivacyFieldGroup, Boolean> khacChi = hienRa(personQuery.byId(hon));
        assertThat(khacChi.get(PrivacyFieldGroup.OCCUPATION)).isTrue();
        assertThat(khacChi.get(PrivacyFieldGroup.RESIDENCE_PROVINCE)).isTrue();
        assertThat(khacChi.get(PrivacyFieldGroup.RESIDENCE_FULL)).isFalse();
        assertThat(khacChi.get(PrivacyFieldGroup.CONTACT)).isFalse();
        assertThat(khacChi.get(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO)).isFalse();

        authenticateAs("sub-cung-chi", "MEMBER");
        Map<PrivacyFieldGroup, Boolean> cungChi = hienRa(personQuery.byId(hon));
        assertThat(cungChi.get(PrivacyFieldGroup.RESIDENCE_FULL)).isTrue();
        assertThat(cungChi.get(PrivacyFieldGroup.CONTACT)).isTrue();
        assertThat(cungChi.get(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO))
                .as("nhóm Riêng tư không bị các nhóm khác kéo theo")
                .isFalse();
    }

    @Test
    @DisplayName("Khối liên hệ đi thành một khối: mở là có cả ba, đóng là mất cả ba")
    void khoiLienHeDiTheoKhoi() {
        UUID nguoi = seed(PersonFixtures.living("Nguyễn Văn Liên Hệ")
                .birthYear(1979).branch(chiGiap).generation(3)
                .consent(PrivacyConsent.allPrivate()
                        .with(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH)));

        authenticateAs("sub-cung-chi", "MEMBER");
        PersonView cungChi = personQuery.byId(nguoi);
        assertThat(cungChi.contact()).isNotNull();
        assertThat(cungChi.contact().phone()).isNotBlank();
        assertThat(cungChi.contact().email()).isNotBlank();
        assertThat(cungChi.contact().zaloId()).isNotBlank();

        authenticateAs("sub-khac-chi", "MEMBER");
        assertThat(personQuery.byId(nguoi).contact())
                .as("cả khối vắng mặt, không phải một object rỗng — object rỗng vẫn nói có chỗ cho dữ liệu")
                .isNull();
    }

    @Test
    @DisplayName("Ngày sinh đầy đủ và ảnh đi cùng nhau; năm sinh vẫn là dữ liệu phả hệ")
    void ngaySinhDayDuVaAnhDiCungNhau() {
        UUID nguoi = seed(PersonFixtures.living("Nguyễn Văn Ngày Sinh")
                .birthYear(1977).branch(chiGiap).generation(3)
                .consent(PrivacyConsent.allPrivate()
                        .with(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO, ShareScope.BRANCH)));

        authenticateAs("sub-cung-chi", "MEMBER");
        PersonView cungChi = personQuery.byId(nguoi);
        assertThat(cungChi.birth().precision()).isEqualTo(DatePrecision.DAY);
        assertThat(cungChi.avatarKey()).isNotNull();

        authenticateAs("sub-khac-chi", "MEMBER");
        PersonView khacChi = personQuery.byId(nguoi);
        assertThat(khacChi.avatarKey()).isNull();
        assertThat(khacChi.birth())
                .as("năm sinh là chất liệu của cây phả hệ, nhưng người khác chi vẫn không được đọc")
                .isNull();
    }

    // =====================================================================================
    // Người đã khuất — không áp mô hình đồng thuận
    // =====================================================================================

    @Test
    @DisplayName("Người đã khuất công khai bất kể bản đồng thuận, nhưng khối liên hệ vẫn bị giấu")
    void nguoiDaKhuatCongKhaiTruLienHe() {
        UUID cu = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1960)
                .birthYear(1900).branch(chiGiap).generation(1)
                .consent(PrivacyConsent.allPrivate()));

        authenticateAsGuest();
        PersonView khach = personQuery.byId(cu);

        assertThat(khach.occupation()).isNotNull();
        assertThat(khach.currentPlaceFull()).isNotNull();
        assertThat(khach.avatarKey()).isNotNull();
        assertThat(khach.contact())
                .as("số ghi trong hồ sơ một cụ đã mất trên thực tế là số của người thân đang sống")
                .isNull();
    }

    // =====================================================================================
    // Nội bộ
    // =====================================================================================

    /**
     * Nhóm trường nào <b>thực sự</b> hiện ra trong phản hồi.
     *
     * <p>Đọc từ {@link PersonView} chứ không từ {@code PersonVisibility}: điều cần kiểm là dữ liệu
     * có rời khỏi tiến trình hay không, không phải là một cờ nội bộ nói rằng nó không nên rời đi.
     * Fixture luôn gieo đủ dữ liệu cho cả năm nhóm, nên {@code null} ở đây chỉ có một nghĩa duy
     * nhất: bị lọc.</p>
     */
    private static Map<PrivacyFieldGroup, Boolean> hienRa(PersonView view) {
        Map<PrivacyFieldGroup, Boolean> result = new LinkedHashMap<>();
        result.put(PrivacyFieldGroup.OCCUPATION, view.occupation() != null);
        result.put(PrivacyFieldGroup.RESIDENCE_PROVINCE, view.currentPlaceProvince() != null);
        result.put(PrivacyFieldGroup.RESIDENCE_FULL, view.currentPlaceFull() != null);
        result.put(PrivacyFieldGroup.CONTACT, view.contact() != null);
        result.put(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO,
                view.avatarKey() != null
                        && view.birth() != null
                        && view.birth().precision() == DatePrecision.DAY);
        return result;
    }
}

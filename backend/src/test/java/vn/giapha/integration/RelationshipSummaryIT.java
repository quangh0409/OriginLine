package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.genealogy.api.rest.GenealogyDtoMapper;
import vn.giapha.genealogy.api.rest.RelationshipSummaryLoader;
import vn.giapha.genealogy.api.rest.dto.PersonDto;
import vn.giapha.genealogy.api.rest.dto.PersonSummaryDto;
import vn.giapha.genealogy.api.rest.dto.RelationshipDto;
import vn.giapha.genealogy.application.LinkRelationshipService;
import vn.giapha.genealogy.application.PersonQueryService;
import vn.giapha.genealogy.application.command.LinkRelationshipCommand;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.shared.vo.Gender;

/**
 * Cạnh quan hệ mang theo tóm tắt của đầu kia — và tóm tắt ấy <b>không được là lỗ rò</b>.
 *
 * <h2>Vì sao ca test này phải chạy trên hạ tầng thật</h2>
 * Cái đang được kiểm chứng không phải phép ánh xạ DTO (việc đó test đơn vị làm được), mà là
 * <b>toàn bộ chuỗi</b>: {@code SecurityContext → CurrentUserProvider → app_user → branch_assignment
 * → ltree → PrivacyTierService → PersonQueryService → RelationshipSummaryLoader}. Một mắt xích lệch
 * ở giữa — chẳng hạn tầng api lỡ đọc thẳng kho nhân khẩu cho "nhanh" — vẫn cho ra một bộ test đơn
 * vị xanh mướt, trong khi trên thật thì ai mở hồ sơ một cụ đã khuất cũng thấy tên con cháu còn sống
 * của cụ.
 *
 * <h2>Bất biến bị canh</h2>
 * <ol>
 *   <li><b>Khách</b> mở hồ sơ một cụ đã khuất: cạnh tới người con còn sống <b>biến mất hoàn toàn</b>,
 *       và chuỗi JSON của cả hồ sơ không chứa tên người sống ấy ở bất kỳ đâu.</li>
 *   <li><b>Thành viên khác chi</b> (Tầng 1): thấy tên và đời của người còn sống, nhưng không thấy
 *       năm sinh hay nguyên quán — tức tóm tắt đi qua đúng bộ lọc chứ không phải là một bản sao
 *       nguyên vẹn của bản ghi.</li>
 *   <li><b>Quản trị</b>: thấy năm sinh — chứng minh hai ca trên ẩn dữ liệu vì <i>bộ lọc</i>, không
 *       phải vì dữ liệu rỗng.</li>
 * </ol>
 */
@DisplayName("Tóm tắt đầu kia trong cạnh quan hệ (REST)")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class RelationshipSummaryIT extends AbstractIntegrationTest {

    private static final String TEN_NGUOI_SONG = "Nguyễn Văn Đang Sống";

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private PersonQueryService personQuery;

    @Autowired
    private LinkRelationshipService linkRelationship;

    @Autowired
    private RelationshipSummaryLoader relationshipSummaries;

    private UUID cuTo;
    private UUID conDaKhuat;
    private UUID conConSong;

    @BeforeEach
    void setUpClan() {
        UUID goc = insertBranch("Dòng họ Nguyễn", "goc", null, "DONG_HO");
        UUID chiGiap = insertBranch("Chi Giáp", "goc.chi_giap", goc, "CHI");
        UUID chiAt = insertBranch("Chi Ất", "goc.chi_at", goc, "CHI");
        authenticateAs("sub-admin", "ADMIN");

        cuTo = seed(PersonFixtures.deceased("Nguyễn Phúc Thuỷ Tổ", 1950)
                .birthYear(1880).branch(chiGiap).generation(1));
        conDaKhuat = seed(PersonFixtures.deceased("Nguyễn Văn Đã Khuất", 1990)
                .birthYear(1910).branch(chiGiap).generation(2));
        conConSong = seed(PersonFixtures.living(TEN_NGUOI_SONG)
                .birthYear(1955).branch(chiGiap).generation(2));

        // Nguoi o chi KHAC, co tai khoan — de co mot nguoi goi dung o Tang 1.
        UUID nguoiChiAt = seed(PersonFixtures.living("Nguyễn Văn Ất")
                .gender(Gender.MALE).birthYear(1980).branch(chiAt).generation(4));
        insertAppUser("sub-chi-at", nguoiChiAt);

        link(cuTo, conDaKhuat);
        link(cuTo, conConSong);
    }

    private void link(UUID cha, UUID con) {
        linkRelationship.link(new LinkRelationshipCommand(cha, con, RelType.PARENT_BIO, null, null,
                null, null, null));
    }

    /** Hồ sơ đúng như tầng api dựng ra — qua cùng một lối mà {@code PersonController} đi. */
    private PersonDto hoSoCuTo() {
        PersonView view = personQuery.byId(cuTo);
        return GenealogyDtoMapper.toDto(view, relationshipSummaries.forSubject(view));
    }

    private static PersonSummaryDto tomTatToi(PersonDto hoSo, UUID doiTuong) {
        List<RelationshipDto> canh = hoSo.relationships() == null ? List.of() : hoSo.relationships();
        return canh.stream()
                .filter(rel -> doiTuong.equals(rel.otherEnd(hoSo.id())))
                .map(RelationshipDto::otherPerson)
                .findFirst().orElse(null);
    }

    // =====================================================================================
    // 1. Khách — không một mẩu nào của người còn sống được lọt ra
    // =====================================================================================

    @Test
    @DisplayName("khách: cạnh tới người còn sống biến mất, và tên người ấy KHÔNG có trong JSON")
    void khachKhongRoTenNguoiConSongQuaTomTatQuanHe() throws Exception {
        authenticateAsGuest();

        PersonDto hoSo = hoSoCuTo();

        assertThat(hoSo.relationships())
                .as("Cu to da khuat la du lieu cong khai nen ho so phai hien ra")
                .isNotNull();
        assertThat(hoSo.relationships().stream().map(rel -> rel.otherEnd(hoSo.id())).toList())
                .as("Chi con canh toi nguoi da khuat; canh toi nguoi song phai bien mat CA CANH")
                .containsExactly(conDaKhuat);
        assertThat(tomTatToi(hoSo, conConSong))
                .as("Khong duoc co tom tat nao cho nguoi con song")
                .isNull();

        String json = JSON.writeValueAsString(hoSo);
        assertThat(json)
                .as("Chi can mot cai ten lot ra la du de suy ra ca mot nguoi con song trong ho")
                .doesNotContain(TEN_NGUOI_SONG)
                .doesNotContain(conConSong.toString());
    }

    @Test
    @DisplayName("khách: cạnh tới người đã khuất VẪN mang tóm tắt — không phải cấm sạch")
    void khachVanThayTomTatCuaNguoiDaKhuat() {
        authenticateAsGuest();

        PersonSummaryDto tomTat = tomTatToi(hoSoCuTo(), conDaKhuat);

        assertThat(tomTat).isNotNull();
        assertThat(tomTat.displayName()).isEqualTo("Nguyễn Văn Đã Khuất");
        assertThat(tomTat.generation()).isEqualTo(2);
        assertThat(tomTat.primaryBranch()).isNotNull();
        assertThat(tomTat.primaryBranch().name())
                .as("Man Quan he can ten + doi + chi, khong phai mot cai id")
                .isEqualTo("Chi Giáp");
        assertThat(tomTat.deathYear()).isEqualTo(1990);
    }

    // =====================================================================================
    // 2. Thành viên khác chi — Tầng 1: có tên, không có năm sinh
    // =====================================================================================

    @Test
    @DisplayName("thành viên khác chi: tóm tắt người còn sống dừng ở Tầng 1")
    void thanhVienKhacChiTomTatDungOTang1() {
        authenticateAs("sub-chi-at", "MEMBER");

        PersonSummaryDto tomTat = tomTatToi(hoSoCuTo(), conConSong);

        assertThat(tomTat).as("Thanh vien da dang nhap duoc thay quan he loi").isNotNull();
        assertThat(tomTat.displayName()).isEqualTo(TEN_NGUOI_SONG);
        assertThat(tomTat.generation()).isEqualTo(2);
        assertThat(tomTat.isAlive()).isTrue();
        assertThat(tomTat.birthYear())
                .as("Nam sinh la Tang 2 — nguoi khac chi khong duoc thay")
                .isNull();
        assertThat(tomTat.nativePlace())
                .as("Nguyen quan la Tang 2")
                .isNull();
        assertThat(tomTat.avatarUrl())
                .as("Anh la Tang 3")
                .isNull();
    }

    // =====================================================================================
    // 3. Quản trị — chứng minh hai ca trên ẩn vì bộ lọc, không vì dữ liệu rỗng
    // =====================================================================================

    @Test
    @DisplayName("quản trị: cùng tóm tắt ấy mang đủ Tầng 2 — nên hai ca trên ẩn vì bộ lọc")
    void quanTriThayDuTang2TrongCungTomTatAy() {
        authenticateAs("sub-admin", "ADMIN");

        PersonSummaryDto tomTat = tomTatToi(hoSoCuTo(), conConSong);

        assertThat(tomTat).isNotNull();
        assertThat(tomTat.displayName()).isEqualTo(TEN_NGUOI_SONG);
        assertThat(tomTat.birthYear()).isEqualTo(1955);
    }

    // =====================================================================================
    // 4. Không phải gọi thêm một lượt /tree nữa
    // =====================================================================================

    @Test
    @DisplayName("mọi cạnh hiện ra đều có tóm tắt — giao diện không cần lượt gọi thứ hai")
    void moiCanhHienRaDeuCoTomTat() {
        authenticateAs("sub-admin", "ADMIN");

        PersonDto hoSo = hoSoCuTo();

        assertThat(hoSo.relationships()).hasSize(2);
        assertThat(hoSo.relationships())
                .allSatisfy(rel -> assertThat(rel.otherPerson())
                        .as("Canh %s khong mang tom tat -> man Quan he lai phai goi /tree", rel.id())
                        .isNotNull());
        assertThat(hoSo.relationships())
                .extracting(rel -> rel.otherPerson().displayName())
                .containsExactlyInAnyOrder("Nguyễn Văn Đã Khuất", TEN_NGUOI_SONG);
    }

    @Test
    @DisplayName("loader nạp đúng một lượt theo lô cho mọi đầu kia của hồ sơ")
    void loaderNapTheoLo() {
        authenticateAs("sub-admin", "ADMIN");

        PersonView view = personQuery.byId(conConSong);
        Map<UUID, PersonSummaryDto> tomTat = relationshipSummaries.forSubject(view);

        assertThat(tomTat).hasSize(1).containsKey(cuTo);
    }
}

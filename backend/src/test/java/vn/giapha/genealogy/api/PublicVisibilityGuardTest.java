package vn.giapha.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.api.rest.public_.PublicVisibilityGuard;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPageDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicPersonSummaryDto;
import vn.giapha.genealogy.api.rest.public_.dto.PublicTreeDto;
import vn.giapha.genealogy.application.CallerRole;
import vn.giapha.genealogy.application.VisibleTier;
import vn.giapha.genealogy.application.view.PageView;
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.application.view.PersonSummaryView;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.application.view.TreeDirection;
import vn.giapha.genealogy.application.view.TreeEdgeView;
import vn.giapha.genealogy.application.view.TreeMetaView;
import vn.giapha.genealogy.application.view.TreeNodeView;
import vn.giapha.genealogy.application.view.TreeProjectionView;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.shared.exception.NotFoundException;
import vn.giapha.shared.vo.Gender;

/**
 * Chốt chặn cuối cùng của cổng thông tin công khai, thử <b>không cần CSDL</b>.
 *
 * <h2>Vì sao ca test này tồn tại song song với {@code PublicPortalIT}</h2>
 * {@code PublicPortalIT} kiểm rằng cả chuỗi hoạt động đúng khi mọi lớp phòng thủ còn nguyên. Lớp
 * này kiểm điều khó hơn: <b>khi các lớp trên đã thủng</b> thì guard có tự đứng vững không. Nó nạp
 * thẳng vào guard những view mà tầng ứng dụng lẽ ra không bao giờ tạo ra — một hồ sơ người còn
 * sống đã đi lọt qua {@code canSee} — và đòi guard phải chặn.
 *
 * <p>Đây chính là chỗ để phòng cái bẫy đã biết: {@code PrivacyTierService.tierFor()} trả
 * {@code T1} cho Khách nhìn người còn sống thay vì từ chối, nên toàn hệ thống đang dựa vào một bất
 * biến "phải gọi {@code canSee} trước" mà không kiểu dữ liệu nào bảo vệ. Nếu ai đó thêm một lối
 * vào công khai mới và quên gọi, các ca dưới đây là thứ còn lại giữa dữ liệu người còn sống và
 * Internet.</p>
 */
@DisplayName("Chốt chặn người còn sống ở biên API công khai")
class PublicVisibilityGuardTest {

    private final PublicVisibilityGuard guard = new PublicVisibilityGuard();

    private final UUID daKhuat = UUID.randomUUID();
    private final UUID conSong = UUID.randomUUID();

    // =====================================================================================
    // Hồ sơ đơn lẻ
    // =====================================================================================

    @Test
    @DisplayName("hồ sơ người CÒN SỐNG lọt tới guard vẫn bị biến thành 404")
    void hoSoNguoiConSong_biChan() {
        assertThatThrownBy(() -> guard.person(personView(conSong, true)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("view rỗng cũng là 404, cùng một hình dạng lỗi")
    void viewRong_la404() {
        assertThatThrownBy(() -> guard.person(null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("hồ sơ người đã khuất đi qua, nhưng khối liên hệ và dữ liệu Tầng 3 không theo cùng")
    void hoSoNguoiDaKhuat_khongMangTheoDuLieuNhayCam() {
        var dto = guard.person(personView(daKhuat, false));

        assertThat(dto.isAlive()).isFalse();
        assertThat(dto.displayName()).isEqualTo("Nguyễn Phúc Thuỷ Tổ");
        assertThat(dto.nativePlace()).isEqualTo("Bắc Ninh");
        // Khong con bat ky duong nao de so dien thoai / dia chi day du / nghe nghiep di ra: DTO
        // cong khai khong co truong tuong ung. Xem them PublicDtoShapeTest.
        assertThat(dto.toString())
                .doesNotContain("0900000001")
                .doesNotContain("So 12 ngo 3")
                .doesNotContain("Giao vien");
    }

    // =====================================================================================
    // Danh sách
    // =====================================================================================

    @Test
    @DisplayName("người còn sống trong danh sách bị loại lặng lẽ, không làm hỏng cả trang")
    void danhSach_loaiNguoiConSong() {
        PageView<PersonSummaryView> page = PageView.of(
                List.of(summary(daKhuat, false), summary(conSong, true)), 0, 20, 2, "relevance,desc");

        PublicPageDto<PublicPersonSummaryDto> result = guard.page(page);

        assertThat(result.items()).extracting(PublicPersonSummaryDto::id).containsExactly(daKhuat);
        assertThat(result.items()).allMatch(item -> !item.isAlive());
    }

    // =====================================================================================
    // Phả đồ
    // =====================================================================================

    @Test
    @DisplayName("phả đồ: bỏ node người sống, bỏ cạnh chạm vào họ, và bỏ cả id tham chiếu tới họ")
    void phaDo_loaiNguoiSongVaMoiThamChieuToiHo() {
        UUID chauDaKhuat = UUID.randomUUID();
        TreeNodeView goc = node(daKhuat, false, 0, List.of(), List.of(conSong));
        TreeNodeView matXich = node(conSong, true, 1, List.of(daKhuat), List.of(daKhuat));
        TreeNodeView chau = node(chauDaKhuat, false, 2, List.of(conSong), List.of());

        TreeProjectionView view = new TreeProjectionView(daKhuat,
                List.of(goc, matXich, chau),
                List.of(edge(daKhuat, conSong), edge(conSong, chauDaKhuat), edge(daKhuat, chauDaKhuat)),
                new TreeMetaView(3, TreeDirection.DESCENDANTS, 3, 3, false, List.of(), Instant.now(),
                        false));

        PublicTreeDto tree = guard.tree(view, 3);

        assertThat(tree.nodes()).extracting(PublicTreeDto.PublicTreeNodeDto::id)
                .containsExactly(daKhuat, chauDaKhuat);
        // Canh nao cham vao nguoi con song deu bien mat; canh giua hai nguoi da khuat thi o lai.
        assertThat(tree.edges()).hasSize(1);
        assertThat(tree.edges().get(0).source()).isEqualTo(daKhuat);
        assertThat(tree.edges().get(0).target()).isEqualTo(chauDaKhuat);
        // Day la cho de ro ri tinh vi nhat: giu lai mot parentId tro toi node khong co trong
        // nodes[] chang khac gi noi "cha cua nguoi nay con song, va day la dinh danh cua ong ay".
        assertThat(tree.nodes().get(1).parentIds()).isEmpty();
        assertThat(tree.nodes().get(0).spouseIds()).isEmpty();
        assertThat(tree.meta().guestFiltered()).isTrue();
        assertThat(tree.meta().nodeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("gốc phả đồ là người còn sống thì cả phản hồi biến thành 404")
    void phaDo_gocLaNguoiConSong_la404() {
        TreeProjectionView view = new TreeProjectionView(conSong,
                List.of(node(conSong, true, 0, List.of(), List.of())),
                List.of(),
                new TreeMetaView(3, TreeDirection.DESCENDANTS, 1, 0, false, List.of(), Instant.now(),
                        false));

        assertThatThrownBy(() -> guard.tree(view, 3)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("node ở rìa độ sâu được đánh dấu mở rộng được, suy từ tham số chứ không từ số con thật")
    void phaDo_coMoRongDuoc_maKhongDemSoConThat() {
        TreeNodeView goc = node(daKhuat, false, 0, List.of(), List.of());
        UUID ria = UUID.randomUUID();
        TreeNodeView nodeRia = node(ria, false, 2, List.of(daKhuat), List.of());

        PublicTreeDto tree = guard.tree(new TreeProjectionView(daKhuat, List.of(goc, nodeRia),
                List.of(edge(daKhuat, ria)),
                new TreeMetaView(2, TreeDirection.DESCENDANTS, 2, 1, false, List.of(), Instant.now(),
                        false)), 2);

        assertThat(tree.nodes()).filteredOn(node -> node.id().equals(ria))
                .allMatch(PublicTreeDto.PublicTreeNodeDto::expandable);
        assertThat(tree.nodes()).filteredOn(node -> node.id().equals(daKhuat))
                .noneMatch(PublicTreeDto.PublicTreeNodeDto::expandable);
    }

    // =====================================================================================
    // meta của Khách (Khiếm khuyết 3)
    // =====================================================================================

    @Test
    @DisplayName("hồ sơ công khai mang meta, và meta ấy nói đúng quyền của Khách")
    void hoSoCongKhaiMangMetaCuaKhach() {
        PublicPersonDto dto = guard.person(personView(daKhuat, false));

        assertThat(dto.meta()).as("thiếu meta thì ngăn hồ sơ không nối được vào cổng công khai")
                .isNotNull();
        assertThat(dto.meta().callerRole()).isEqualTo(CallerRole.GUEST);
        assertThat(dto.meta().canEdit()).isFalse();
        assertThat(dto.meta().canDelete()).isFalse();
        assertThat(dto.meta().canRequestCorrection()).isFalse();
        assertThat(dto.meta().isSelf()).isFalse();
        // Người đã khuất luôn PUBLIC. Ghim để không ai sửa thành T1/T2/T3 rồi khiến giao diện
        // tưởng có dữ liệu Tầng 2/3 đang bị giấu ở đâu đó trên bề mặt này.
        assertThat(dto.meta().visibleTier()).isEqualTo(VisibleTier.PUBLIC);
    }

    @Test
    @DisplayName("meta KHÔNG phải của Khách ⇒ từ chối phục vụ, không lặng lẽ sửa lại")
    void metaKhongPhaiCuaKhachThiTuChoi() {
        // Đây là dấu hiệu PublicGuestScope đã không chạy: cả phản hồi được tính theo vai của người
        // mang token, mà controller lại gắn Cache-Control: public lên nó.
        PersonView theoVaiHoiDong = thayQuyen(personView(daKhuat, false),
                new PersonAccessView(VisibleTier.PUBLIC, true, true, false, false,
                        CallerRole.COUNCIL));

        assertThatThrownBy(() -> guard.person(theoVaiHoiDong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Khach");
    }

    @Test
    @DisplayName("thiếu hẳn access ⇒ cũng từ chối, không trả meta null")
    void thieuAccessThiCungTuChoi() {
        PersonView khongCoQuyen = thayQuyen(personView(daKhuat, false), null);

        assertThatThrownBy(() -> guard.person(khongCoQuyen))
                .isInstanceOf(IllegalStateException.class);
    }

    // =====================================================================================
    // Dựng dữ liệu
    // =====================================================================================

    private PersonView personView(UUID id, boolean alive) {
        return new PersonView(id,
                List.of(new PersonName(UUID.randomUUID(), NameType.THUONG_GOI,
                        "Nguyễn Phúc Thuỷ Tổ", null, true, null)),
                "Nguyễn Phúc Thuỷ Tổ", Gender.MALE, 1, alive, null, null, null,
                "Bắc Ninh", "Hà Nội", "So 12 ngo 3 phuong Lang Ha, Ha Noi", "Giao vien",
                "Tieu su", "portraits/1.jpg", null,
                new ContactInfo("0900000001", "nguoi@example.com", "zalo-01"),
                Map.of("ghi_chu", "du lieu test"), PrivacyConsent.allPrivate(),
                Instant.now(), Instant.now(), 1L, List.of(), quyenCuaKhach());
    }

    /**
     * {@code PersonAccessView} đúng như {@code PrivacyTierService.accessOf} tính cho Khách nhìn một
     * người đã khuất: tier {@code PUBLIC} (người đã khuất luôn công khai), ba cờ {@code can*} tắt.
     */
    private PersonAccessView quyenCuaKhach() {
        return new PersonAccessView(VisibleTier.PUBLIC, false, false, false, false,
                CallerRole.GUEST);
    }

    /** Bản sao {@code view} với {@code access} khác — {@code PersonView} không có wither cho nó. */
    private PersonView thayQuyen(PersonView view, PersonAccessView access) {
        return new PersonView(view.id(), view.names(), view.displayName(), view.gender(),
                view.generation(), view.alive(), view.deleted(), view.birth(), view.death(),
                view.nativePlace(), view.currentPlaceProvince(), view.currentPlaceFull(),
                view.occupation(), view.biography(), view.avatarKey(), view.primaryBranch(),
                view.contact(), view.attributes(), view.privacyConsent(), view.createdAt(),
                view.updatedAt(), view.version(), view.relationships(), access);
    }

    private PersonSummaryView summary(UUID id, boolean alive) {
        return new PersonSummaryView(id, "Nguyễn Văn A", null, Gender.MALE, 2, alive, 1900,
                alive ? null : 1970, null, "Bắc Ninh", null, null);
    }

    private TreeNodeView node(UUID id, boolean alive, int depth, List<UUID> parents,
                              List<UUID> spouses) {
        return new TreeNodeView(id, summary(id, alive), depth, parents, spouses, 0, false, List.of());
    }

    private TreeEdgeView edge(UUID from, UUID to) {
        return new TreeEdgeView(from + "->" + to, from, to, RelType.PARENT_BIO, null, null, null);
    }
}

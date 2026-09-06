package vn.giapha.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.domain.event.PersonMovedBranchEvent;
import vn.giapha.shared.vo.LunarDate;

/**
 * Tình trạng sống/mất, đời thứ, thứ tự sinh, chi/ngành và ảnh chụp nhật ký.
 *
 * <p>Hai ràng buộc ghim ở đây trùng với CHECK của V2 nhưng được bắt ở domain để lỗi hiện ra dưới
 * dạng thông điệp nghiệp vụ chứ không phải {@code SQLException} lúc flush: <b>còn sống thì không
 * có ngày mất</b>, và <b>đời thứ bắt đầu từ 1 (Thuỷ tổ)</b>.
 */
class PersonLifeStatusTest {

    @Test
    @DisplayName("Báo mất ghi ngày mất song lịch; phần âm lịch là nguồn chân lý tính giỗ")
    void baoMatGhiNgayMatSongLich() {
        Person person = PersonFixtures.nam("Nguyễn Văn Cả");
        LifeDate ngayMat = LifeDate.of(java.time.LocalDate.of(1975, 4, 20),
                LunarDate.of(1975, 3, 10), DatePrecision.DAY);

        person.markDeceased(ngayMat);

        assertThat(person.isAlive()).isFalse();
        assertThat(person.death()).isEqualTo(ngayMat);
        assertThat(person.death().lunar())
                .as("death_lunar la nguon chan ly de context events sinh nhac gio")
                .isEqualTo(LunarDate.of(1975, 3, 10));
    }

    @Test
    @DisplayName("Gia phả cổ chỉ biết đã mất mà không còn ngày nào — markDeceased(null) hợp lệ")
    void baoMatKhongCoNgayVanHopLe() {
        Person person = PersonFixtures.nam("Cụ Tổ");

        person.markDeceased(null);

        assertThat(person.isAlive()).isFalse();
        assertThat(person.death()).isNull();
    }

    @Test
    @DisplayName("Đính chính còn sống thì ngày mất bị gỡ — không bao giờ tồn tại đồng thời")
    void dinhChinhConSongThiGoNgayMat() {
        Person person = PersonFixtures.nam("Nguyễn Văn Hai");
        person.markDeceased(LifeDate.ofLunar(LunarDate.of(1990, 5, 5)));

        person.markAlive();

        assertThat(person.isAlive()).isTrue();
        assertThat(person.death())
                .as("rang buoc ck_person_alive_vs_death cua V2 duoc bat ngay o domain")
                .isNull();
    }

    @Test
    @DisplayName("markDeceased chỉ khai những trường THỰC SỰ đổi, không khai bừa [isAlive, death]")
    void baoMatChiKhaiTruongThucSuDoi() {
        Person person = PersonFixtures.nam("Nguyễn Văn Tám");
        LifeDate ngayMat = LifeDate.ofLunar(LunarDate.of(1975, 3, 10));

        assertThat(person.markDeceased(null))
                .as("nguoi dang song, khong co ngay mat -> chi moi tinh trang doi")
                .containsExactly("isAlive");
        assertThat(person.markDeceased(ngayMat))
                .as("da mat san roi, gio moi biet ngay gio -> chi moi 'death' doi")
                .containsExactly("death");
        assertThat(person.markDeceased(ngayMat))
                .as("gui lai dung ngay cu la thao tac rong; khai no da doi la lam audit_log noi doi")
                .isEmpty();
    }

    @Test
    @DisplayName("Thao tác rỗng không phát sự kiện — consumer nhắc giỗ không bị đánh thức vô cớ")
    void thaoTacRongKhongPhatSuKien() {
        Person person = PersonFixtures.nam("Nguyễn Văn Chín");
        person.markDeceased(LifeDate.ofLunar(LunarDate.of(1975, 3, 10)));
        person.clearDomainEvents();

        person.markDeceased(LifeDate.ofLunar(LunarDate.of(1975, 3, 10)));

        assertThat(person.domainEvents()).isEmpty();
    }

    @Test
    @DisplayName("markAlive trên người vốn đang sống là thao tác rỗng, không khai gì")
    void dinhChinhTrenNguoiVonDangSongLaThaoTacRong() {
        Person person = PersonFixtures.nam("Nguyễn Văn Mười");
        person.clearDomainEvents();

        assertThat(person.markAlive()).isEmpty();
        assertThat(person.domainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Đời thứ phải >= 1 (Thuỷ tổ = 1); null hợp lệ khi chưa nối vào cây")
    void doiThuPhaiTuMotTroLen() {
        Person person = PersonFixtures.nam("Nguyễn Văn Ba");

        assertThatThrownBy(() -> person.placeInGeneration(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thuy to");
        assertThatThrownBy(() -> person.placeInGeneration(-3))
                .isInstanceOf(IllegalArgumentException.class);

        person.placeInGeneration(null);
        assertThat(person.generation())
                .as("nhan khau bi go khoi cay co doi thu null va khong xuat hien tren pha do")
                .isNull();

        person.placeInGeneration(1);
        assertThat(person.generation()).isEqualTo(1);
    }

    @Test
    @DisplayName("Thứ tự sinh phải >= 1 (con cả = 1) — đầu vào của suy luận danh xưng")
    void thuTuSinhPhaiTuMotTroLen() {
        Person person = PersonFixtures.nam("Nguyễn Văn Tư");

        assertThatThrownBy(() -> person.placeInBirthOrder(0))
                .isInstanceOf(IllegalArgumentException.class);

        person.placeInBirthOrder(1);
        assertThat(person.birthOrder()).isEqualTo(1);
        person.placeInBirthOrder(null);
        assertThat(person.birthOrder()).isNull();
    }

    @Test
    @DisplayName("Chuyển chi phát sự kiện kèm cả chi cũ lẫn chi mới")
    void chuyenChiPhatSuKienKemCaHaiChi() {
        Person person = PersonFixtures.nam("Nguyễn Văn Năm");
        UUID chiCu = UUID.randomUUID();
        UUID chiMoi = UUID.randomUUID();
        person.moveToBranch(chiCu);
        person.clearDomainEvents();

        person.moveToBranch(chiMoi);

        assertThat(person.primaryBranchId()).isEqualTo(chiMoi);
        assertThat(person.domainEvents()).singleElement()
                .isInstanceOfSatisfying(PersonMovedBranchEvent.class, event -> {
                    assertThat(event.fromBranchId()).isEqualTo(chiCu);
                    assertThat(event.toBranchId()).isEqualTo(chiMoi);
                });
    }

    @Test
    @DisplayName("Chuyển sang đúng chi đang ở là thao tác rỗng, không phát sự kiện")
    void chuyenSangChinhChiDangOLaThaoTacRong() {
        Person person = PersonFixtures.nam("Nguyễn Văn Sáu");
        UUID chi = UUID.randomUUID();
        person.moveToBranch(chi);
        person.clearDomainEvents();

        person.moveToBranch(chi);

        assertThat(person.domainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Tình trạng nối dõi: tuyệt tự và kế tự đều ghi được, null quy về NORMAL")
    void tinhTrangNoiDoiGhiDuoc() {
        Person person = PersonFixtures.nam("Nguyễn Văn Bảy");

        person.changeLineageStatus(LineageStatus.TUYET_TU);
        assertThat(person.lineageStatus()).isEqualTo(LineageStatus.TUYET_TU);

        person.changeLineageStatus(LineageStatus.KE_TU);
        assertThat(person.lineageStatus()).isEqualTo(LineageStatus.KE_TU);

        person.changeLineageStatus(null);
        assertThat(person.lineageStatus()).isEqualTo(LineageStatus.NORMAL);
    }

    @Test
    @DisplayName("Ảnh chụp nhật ký KHÔNG chứa bất kỳ trường Tầng 3 nào")
    void anhChupNhatKyKhongChuaTang3() {
        Person person = PersonFixtures.nu("Nguyễn Thị Lan");
        person.applyProfileEdit(ProfileEdit.builder()
                .contact(FieldChange.set(PersonFixtures.lienHe()))
                .currentPlaceFull(FieldChange.set("số 12 ngõ 30 phố Hàng Bột, Hà Nội"))
                .biography(FieldChange.set("tiểu sử riêng tư"))
                .avatarKey(FieldChange.set("portraits/lan.jpg"))
                .birth(FieldChange.set(PersonFixtures.ngaySinh(1988, 4, 17)))
                .attributes(FieldChange.set(Map.of("soCMND", "001188000123")))
                .build());

        Map<String, Object> snapshot = person.auditSnapshot();

        assertThat(snapshot).doesNotContainKeys("contact", "currentPlaceFull", "currentPlaceProvince",
                "occupation", "biography", "avatarKey", "attributes", "birth", "death");
        assertThat(snapshot)
                .as("nhat ky chi duoc giu NAM sinh, khong duoc giu ngay sinh day du")
                .containsEntry("birthYear", 1988);
        assertThat(snapshot.toString())
                .doesNotContain("0912345678")
                .doesNotContain("Hàng Bột")
                .doesNotContain("001188000123")
                .doesNotContain("portraits/lan.jpg");
    }

    @Test
    @DisplayName("Ảnh chụp nhật ký vẫn giữ đủ dữ liệu phả hệ để đối chiếu lịch sử sửa đổi")
    void anhChupNhatKyGiuDuDuLieuPhaHe() {
        Person person = PersonFixtures.nam("Nguyễn Văn Cả");
        person.placeInGeneration(3);
        person.placeInBirthOrder(1);

        Map<String, Object> snapshot = person.auditSnapshot();

        assertThat(snapshot)
                .containsEntry("displayName", "Nguyễn Văn Cả")
                .containsEntry("generation", 3)
                .containsEntry("birthOrder", 1)
                .containsEntry("isAlive", true)
                .containsEntry("isDeleted", false)
                .containsEntry("lineageStatus", "NORMAL");
    }
}

package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeCallerIdentity;
import vn.giapha.genealogy.application.GenealogyTestDoubles.InMemoryBranchRepository;
import vn.giapha.genealogy.application.GenealogyTestDoubles.InMemoryPersonRepository;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.BranchKind;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;

/**
 * Mặt tiền {@link PersonDisclosureService} — <b>cửa duy nhất</b> để context khác lấy hồ sơ nhân
 * khẩu đã lọc.
 *
 * <h2>Bộ này kiểm đúng hai điều, và cố ý không kiểm điều thứ ba</h2>
 * <ol>
 *   <li><b>Mặt tiền lọc theo NGƯỜI GỌI</b>: cùng một hồ sơ, ba vai khác nhau nhận về ba kết quả
 *       khác nhau. Đây là lý do tồn tại của {@link DisclosureAudience}; một mặt tiền lọc theo một
 *       mức cố định chính là bản chép luật thứ hai đội lốt.</li>
 *   <li><b>Mặt tiền không tự quyết định gì</b>: nó chỉ dịch kiểu từ {@code PersonView}. Vì thế các
 *       ca dưới đây khẳng định kết quả của nó <b>trùng khít</b> với thứ {@code PrivacyTierService}
 *       trả ra, chứ không khẳng định một danh sách trường riêng.</li>
 * </ol>
 *
 * <p>Điều thứ ba — bản thân luật riêng tư (ai thấy nhóm trường nào ở mức nào) — là việc của
 * {@code PrivacyTierServiceTest} và {@code PrivacyFieldGroupMatrixIT}. Kiểm lại nó ở đây là dựng
 * bản sao thứ hai của cùng một luật, đúng thứ mà mặt tiền này sinh ra để dẹp.</p>
 */
class PersonDisclosureServiceTest {

    private static final BranchPath P_CHI_AT = BranchPath.of("goc.chi_at");

    private final InMemoryPersonRepository persons = new InMemoryPersonRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final FakeCallerIdentity identity = new FakeCallerIdentity();
    private final PrivacyTierService privacy = new PrivacyTierService(identity);
    private final PersonDisclosureService disclosure =
            new PersonDisclosureService(persons, branches, privacy);

    private final Branch chiAt = branches.seed(
            Branch.create(UUID.randomUUID(), "Chi Ất", P_CHI_AT, null, BranchKind.CHI));

    @AfterEach
    void dongPhien() {
        GenealogyTestDoubles.dangXuat();
    }

    @Test
    @DisplayName("Cùng một người còn sống, ba vai nhận ba hồ sơ khác nhau")
    void baVaiBaKetQua() {
        Person song = conSongDayDuLieu();

        DisclosedPerson thanhVien = hoSo(song, "sub-thanh-vien", "MEMBER");
        assertThat(thanhVien.thuongGoi()).isEqualTo("Nguyễn Thị Sống");
        assertThat(thanhVien.doi()).isEqualTo(5);
        assertThat(thanhVien.gender()).isEqualTo(Gender.FEMALE);
        assertThat(thanhVien.namSinh()).as("nam sinh khong thuoc Tang 1").isNull();
        assertThat(thanhVien.nguyenQuan()).isNull();
        assertThat(thanhVien.huy()).as("ten huy la du lieu le nghi nhay cam").isNull();
        assertThat(thanhVien.thuy()).isNull();
        assertThat(thanhVien.duLieuNgoaiNhomHienDuoc()).isFalse();
        // Chu Han-Nom cua TEN CHINH thi CO — no di theo ten chinh, khong theo cac lop ten phu, va
        // PersonSummaryView van dua dung chu ay len node pha do cho moi thanh vien dang nhap.
        // Ghim o day vi day chinh la cho ban chep cu trong dataimport da lech: no giau chu nay
        // trong tep Excel trong khi man hinh van hien. Neu Hoi dong Toc bieu muon giau, cho sua la
        // PrivacyTierService.visibleNames() — mot cho, va ca man hinh lan tep cung doi theo.
        assertThat(thanhVien.hanNom()).isEqualTo("阮氏生");

        DisclosedPerson hoiDong = hoSo(song, "sub-hoi-dong", "COUNCIL");
        assertThat(hoiDong.namSinh()).isEqualTo(1962);
        assertThat(hoiDong.nguyenQuan()).isEqualTo("Làng Kim Bảng");
        assertThat(hoiDong.huy()).isEqualTo("Huý của người sống");
        assertThat(hoiDong.duLieuNgoaiNhomHienDuoc()).isTrue();

        // Khach: khong duoc biet nguoi con song nay TON TAI — va "khong duoc biet" phai giong het
        // "khong co", nen id bien mat khoi ket qua chu khong tra ve mot ho so rong.
        assertThat(disclosure.hoSo(List.of(song.rawId()), DisclosureAudience.khach()))
                .as("BA v2 §10: Khach khong thay bat ky nguoi con song nao, ke ca ten")
                .isEmpty();
    }

    @Test
    @DisplayName("Người đã khuất là dữ liệu công khai — Khách cũng nhận đủ")
    void nguoiDaKhuatCongKhaiVoiCaKhach() {
        Person cu = PersonFixtures.daKhuat("Nguyễn Văn Đức", Gender.MALE);
        cu.moveToBranch(chiAt.id());
        cu.addName(PersonName.of(NameType.HUY, "Đức", false));
        cu.placeInGeneration(2);
        persons.seed(cu);

        Map<UUID, DisclosedPerson> ket =
                disclosure.hoSo(List.of(cu.rawId()), DisclosureAudience.khach());

        assertThat(ket).containsOnlyKeys(cu.rawId());
        DisclosedPerson hoSo = ket.get(cu.rawId());
        assertThat(hoSo.conSong()).isFalse();
        assertThat(hoSo.huy()).isEqualTo("Đức");
        assertThat(hoSo.duLieuNgoaiNhomHienDuoc()).isTrue();
        // Ngay gio la nguon chan ly de nhac gio — mat no la ca ho cung nham ngay.
        assertThat(hoSo.ngayGio()).isNotNull();
        assertThat(hoSo.ngayGio().month()).isEqualTo(3);
        assertThat(hoSo.ngayGio().day()).isEqualTo(10);
    }

    @Test
    @DisplayName("Bản ghi đã xoá mềm biến mất với vai không có phạm vi toàn dòng họ")
    void xoaMemThiBienMatVoiVaiHep() {
        Person cu = PersonFixtures.daKhuat("Nguyễn Văn Xoá", Gender.MALE);
        cu.moveToBranch(chiAt.id());
        cu.softDelete("trung ban ghi");
        persons.seed(cu);

        assertThat(hoSoMap(cu, "sub-thanh-vien", "MEMBER")).isEmpty();
        assertThat(hoSoMap(cu, "sub-admin", "ADMIN")).containsOnlyKeys(cu.rawId());
    }

    @Test
    @DisplayName("Không truyền người gọi thì hạ về Khách, không mở toang")
    void thieuNguoiGoiThiFailClosed() {
        Person song = conSongDayDuLieu();

        assertThat(disclosure.hoSo(List.of(song.rawId()), null))
                .as("mot cho quen truyen ngu canh phai ra THIEU du lieu, khong bao gio ra thua")
                .isEmpty();
    }

    @Test
    @DisplayName("Id lạ, id null và id trùng không làm hỏng lô — chỉ đơn giản vắng mặt")
    void idLaThiVangMat() {
        Person song = conSongDayDuLieu();
        GenealogyTestDoubles.dangNhap("sub-thanh-vien", "MEMBER");
        DisclosureAudience ai = disclosure.nguoiGoiHienTai();

        Map<UUID, DisclosedPerson> ket = disclosure.hoSo(
                java.util.Arrays.asList(song.rawId(), null, UUID.randomUUID(), song.rawId()), ai);

        assertThat(ket).containsOnlyKeys(song.rawId());
    }

    @Test
    @DisplayName("Ngữ cảnh dựng ngoài genealogy chỉ có thể là Khách — không tự nâng quyền được")
    void ngoaiContextChiDungDuocKhach() {
        // Constructor that la package-private; loi dung public duy nhat la khach(). Day la ly do
        // DisclosureAudience khong phai mot record.
        assertThat(DisclosureAudience.khach().vai()).isEqualTo(CallerRole.GUEST.name());

        GenealogyTestDoubles.dangNhap("sub-hoi-dong", "COUNCIL");
        assertThat(disclosure.nguoiGoiHienTai().vai()).isEqualTo(CallerRole.COUNCIL.name());
    }

    // -------------------------------------------------------------------------------------

    /** Một người còn sống khai đủ mọi thứ mà mẫu Excel có cột để chở. */
    private Person conSongDayDuLieu() {
        Person song = PersonFixtures.nguoiSong("Nguyễn Thị Sống", Gender.FEMALE);
        song.moveToBranch(chiAt.id());
        song.placeInGeneration(5);
        song.replaceNames(List.of(
                new PersonName(null, NameType.THUONG_GOI, "Nguyễn Thị Sống", "阮氏生", true, null),
                PersonName.of(NameType.HUY, "Huý của người sống", false)));
        song.applyProfileEdit(ProfileEdit.builder()
                .birth(FieldChange.set(PersonFixtures.sinhNam(1962)))
                .nativePlace(FieldChange.set("Làng Kim Bảng"))
                .build());
        persons.seed(song);
        return song;
    }

    private DisclosedPerson hoSo(Person person, String sub, String role) {
        return hoSoMap(person, sub, role).get(person.rawId());
    }

    private Map<UUID, DisclosedPerson> hoSoMap(Person person, String sub, String role) {
        GenealogyTestDoubles.dangNhap(sub, role);
        return disclosure.hoSo(List.of(person.rawId()), disclosure.nguoiGoiHienTai());
    }
}

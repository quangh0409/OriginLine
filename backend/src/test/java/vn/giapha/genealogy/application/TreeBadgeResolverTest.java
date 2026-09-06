package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.view.PersonBadge;
import vn.giapha.genealogy.domain.Branch;
import vn.giapha.genealogy.domain.BranchKind;
import vn.giapha.genealogy.domain.HeirKind;
import vn.giapha.genealogy.domain.LineageStatus;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.Relationship;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;

/**
 * Nhãn nghiệp vụ vẽ lên node phả đồ — <b>tính ở backend</b>, không để giao diện tự suy.
 *
 * <p>Lý do: mấy nhãn này không đọc được từ một cột nào cả. <b>Dâu và rể không phải một loại
 * cạnh</b> — chúng suy ra từ {@code SPOUSE} cộng huyết thống (vợ của một người con trai trong dòng
 * họ là con dâu). Bắt frontend tự luận là bảo nó cài lại một phần rule engine danh xưng bằng
 * JavaScript, và bản đó chắc chắn sẽ lệch với bản chính.
 */
class TreeBadgeResolverTest {

    private final Branch chi = Branch.create(UUID.randomUUID(), "Chi Giáp",
            BranchPath.of("goc.chi_giap"), null, BranchKind.CHI);
    private final BranchDirectory danhBa = BranchDirectory.of(chi);

    private Person trongChi(Person person) {
        person.moveToBranch(chi.id());
        return person;
    }

    @Test
    @DisplayName("Người đã khuất được gắn nhãn DECEASED để canvas phân biệt thị giác")
    void nguoiDaKhuatDuocGanNhanDeceased() {
        Person cu = trongChi(PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE));

        List<PersonBadge> badges = TreeBadgeResolver.badgesOf(cu, List.of(), danhBa, true);

        assertThat(badges).containsExactly(PersonBadge.DECEASED);
    }

    @Test
    @DisplayName("Người còn sống không có nhãn nào nếu không có gì đặc biệt")
    void nguoiConSongKhongCoNhanNao() {
        Person nguoi = trongChi(PersonFixtures.nam("Nguyễn Văn A"));

        assertThat(TreeBadgeResolver.badgesOf(nguoi, List.of(), danhBa, true)).isEmpty();
    }

    @Test
    @DisplayName("Chi tuyệt tự được gắn nhãn TUYET_TU")
    void chiTuyetTuDuocGanNhan() {
        Person nguoi = trongChi(PersonFixtures.nam("Nguyễn Văn Đoạn"));
        nguoi.changeLineageStatus(LineageStatus.TUYET_TU);

        assertThat(TreeBadgeResolver.badgesOf(nguoi, List.of(), danhBa, true))
                .contains(PersonBadge.TUYET_TU);
    }

    @Test
    @DisplayName("Người đang giữ chức Trưởng chi được gắn nhãn TRUONG_CHI")
    void truongChiDuocGanNhan() {
        Person nguoi = trongChi(PersonFixtures.nam("Nguyễn Văn Trưởng"));
        chi.appointHead(nguoi.rawId());

        assertThat(TreeBadgeResolver.badgesOf(nguoi, List.of(), danhBa, true))
                .contains(PersonBadge.TRUONG_CHI);
    }

    @Test
    @DisplayName("Con nuôi đến từ cạnh PARENT_ADOPT, không phải một cờ trên cạnh ruột")
    void conNuoiDenTuCanhAdopt() {
        Person con = trongChi(PersonFixtures.nam("Người Con"));
        UUID cha = UUID.randomUUID();
        Relationship nuoi = Relationship.parent(UUID.randomUUID(), cha, con.rawId(), true);
        Relationship ruot = Relationship.parent(UUID.randomUUID(), cha, con.rawId(), false);

        assertThat(TreeBadgeResolver.badgesOf(con, List.of(nuoi), danhBa, true))
                .contains(PersonBadge.CON_NUOI);
        assertThat(TreeBadgeResolver.badgesOf(con, List.of(ruot), danhBa, true))
                .doesNotContain(PersonBadge.CON_NUOI);
    }

    @Test
    @DisplayName("Cạnh con nuôi của NGƯỜI KHÁC không gắn nhãn nhầm sang người này")
    void canhConNuoiCuaNguoiKhacKhongGanNham() {
        Person cha = trongChi(PersonFixtures.nam("Người Cha"));
        Relationship nuoi = Relationship.parent(UUID.randomUUID(), cha.rawId(),
                UUID.randomUUID(), true);

        assertThat(TreeBadgeResolver.badgesOf(cha, List.of(nuoi), danhBa, true))
                .as("nguoi CHA nhan con nuoi khong phai la con nuoi")
                .doesNotContain(PersonBadge.CON_NUOI);
    }

    @Test
    @DisplayName("Đích tôn, thừa tự, kế tự ánh xạ đúng từ heirKind của cạnh HEIR")
    void baLoaiThuaKeAnhXaDung() {
        Person nguoi = trongChi(PersonFixtures.nam("Người Nối Dõi"));
        UUID cu = UUID.randomUUID();

        assertThat(TreeBadgeResolver.badgesOf(nguoi,
                List.of(Relationship.heir(UUID.randomUUID(), cu, nguoi.rawId(), HeirKind.DICH_TON)),
                danhBa, true)).contains(PersonBadge.DICH_TON);
        assertThat(TreeBadgeResolver.badgesOf(nguoi,
                List.of(Relationship.heir(UUID.randomUUID(), cu, nguoi.rawId(), HeirKind.THUA_TU)),
                danhBa, true)).contains(PersonBadge.THUA_TU);
        assertThat(TreeBadgeResolver.badgesOf(nguoi,
                List.of(Relationship.heir(UUID.randomUUID(), cu, nguoi.rawId(), HeirKind.KE_TU)),
                danhBa, true)).contains(PersonBadge.KE_TU);
    }

    @Test
    @DisplayName("Người để lại hương hoả KHÔNG bị gắn nhãn của người nối dõi")
    void nguoiDeLaiHuongHoaKhongBiGanNhanNoiDoi() {
        Person cu = trongChi(PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE));
        Relationship canh = Relationship.heir(UUID.randomUUID(), cu.rawId(), UUID.randomUUID(),
                HeirKind.DICH_TON);

        assertThat(TreeBadgeResolver.badgesOf(cu, List.of(canh), danhBa, true))
                .doesNotContain(PersonBadge.DICH_TON);
    }

    @Test
    @DisplayName("Vợ của một người con trai trong họ là con DÂU — suy từ SPOUSE cộng huyết thống")
    void voCuaConTraiTrongHoLaConDau() {
        Person dau = trongChi(PersonFixtures.nu("Con Dâu"));
        Relationship honNhan = Relationship.spouse(UUID.randomUUID(), UUID.randomUUID(),
                dau.rawId(), 1, null, null);

        assertThat(TreeBadgeResolver.badgesOf(dau, List.of(honNhan), danhBa, false))
                .contains(PersonBadge.DAU);
    }

    @Test
    @DisplayName("Chồng của một người con gái trong họ là con RỂ")
    void chongCuaConGaiTrongHoLaConRe() {
        Person re = trongChi(PersonFixtures.nam("Con Rể"));
        Relationship honNhan = Relationship.spouse(UUID.randomUUID(), re.rawId(),
                UUID.randomUUID(), 1, null, null);

        assertThat(TreeBadgeResolver.badgesOf(re, List.of(honNhan), danhBa, false))
                .contains(PersonBadge.RE);
    }

    @Test
    @DisplayName("Người TRONG huyết thống không bao giờ là dâu/rể của chính dòng họ mình")
    void nguoiTrongHuyetThongKhongPhaiDauRe() {
        Person conGai = trongChi(PersonFixtures.nu("Con Gái Trong Họ"));
        Relationship honNhan = Relationship.spouse(UUID.randomUUID(), UUID.randomUUID(),
                conGai.rawId(), 1, null, null);

        assertThat(TreeBadgeResolver.badgesOf(conGai, List.of(honNhan), danhBa, true))
                .as("con gai trong ho lay chong van la con gai cua ho, khong phai con dau")
                .doesNotContain(PersonBadge.DAU, PersonBadge.RE);
    }

    @Test
    @DisplayName("Người ngoài huyết thống nhưng KHÔNG có cạnh hôn nhân thì không gắn dâu/rể")
    void ngoaiHuyetThongMaKhongCoHonNhanThiKhongGanNhan() {
        Person nguoi = trongChi(PersonFixtures.nu("Người Lạ"));

        assertThat(TreeBadgeResolver.badgesOf(nguoi, List.of(), danhBa, false))
                .doesNotContain(PersonBadge.DAU, PersonBadge.RE);
    }

    @Test
    @DisplayName("Giới tính chưa rõ thì không đoán bừa là dâu hay rể")
    void gioiTinhChuaRoThiKhongDoanBua() {
        Person nguoi = trongChi(PersonFixtures.nguoiSong("Chưa Rõ", Gender.UNKNOWN));
        Relationship honNhan = Relationship.spouse(UUID.randomUUID(), UUID.randomUUID(),
                nguoi.rawId(), 1, null, null);

        assertThat(TreeBadgeResolver.badgesOf(nguoi, List.of(honNhan), danhBa, false))
                .doesNotContain(PersonBadge.DAU, PersonBadge.RE);
    }

    @Test
    @DisplayName("Nhiều nhãn cùng lúc đều được trả về")
    void nhieuNhanCungLuc() {
        Person nguoi = trongChi(PersonFixtures.daKhuat("Cụ Đích Tôn", Gender.MALE));
        nguoi.changeLineageStatus(LineageStatus.TUYET_TU);
        chi.appointHead(nguoi.rawId());
        Relationship keThua = Relationship.heir(UUID.randomUUID(), UUID.randomUUID(),
                nguoi.rawId(), HeirKind.DICH_TON);
        Relationship laConNuoi = Relationship.parent(UUID.randomUUID(), UUID.randomUUID(),
                nguoi.rawId(), true);

        List<PersonBadge> badges = TreeBadgeResolver.badgesOf(nguoi,
                List.of(keThua, laConNuoi), danhBa, true);

        assertThat(badges).contains(PersonBadge.DECEASED, PersonBadge.TUYET_TU,
                PersonBadge.TRUONG_CHI, PersonBadge.DICH_TON, PersonBadge.CON_NUOI);
    }

    @Test
    @DisplayName("Danh sách nhãn trả ra là bản bất biến")
    void danhSachNhanLaBanBatBien() {
        Person cu = trongChi(PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE));

        assertThat(TreeBadgeResolver.badgesOf(cu, List.of(), danhBa, true)).isUnmodifiable();
    }

    @Test
    @DisplayName("Nhãn dâu/rể KHÔNG được suy từ một loại cạnh riêng — RelType không có DAU/RE")
    void khongCoLoaiCanhRiengChoDauRe() {
        assertThat(RelType.values()).extracting(Enum::name)
                .doesNotContain("DAU", "RE", "IN_LAW");
    }
}

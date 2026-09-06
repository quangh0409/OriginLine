package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeCallerIdentity;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PrivacyLevel;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Phân tầng riêng tư</b> (BA v2 §10, Nghị định 13/2023) — lớp chịu trách nhiệm pháp lý của
 * context {@code genealogy}.
 *
 * <p>Bốn luật được ghim ở đây: người đã khuất là dữ liệu công khai; <b>Khách không thấy bất kỳ
 * người còn sống nào</b>; Tầng 3 chỉ cho chính chủ, Admin và người được chủ thể opt-in; và
 * <b>trẻ vị thành niên ẩn tối đa</b>, chặn trước cả opt-in.
 */
class PrivacyTierServiceTest {

    private static final BranchPath CHI_GIAP = BranchPath.of("goc.chi_giap");
    private static final BranchPath CHI_AT = BranchPath.of("goc.chi_at");

    private final FakeCallerIdentity identity = new FakeCallerIdentity();
    private final PrivacyTierService privacy = new PrivacyTierService(identity);

    @AfterEach
    void dongPhien() {
        GenealogyTestDoubles.dangXuat();
    }

    private CallerContext vai(CallerRole role, BranchPath chiNha, BranchPath... quanTri) {
        return new CallerContext(role, UUID.randomUUID(), List.of(quanTri), chiNha);
    }

    @Nested
    @DisplayName("Ai được biết nhân khẩu này tồn tại")
    class KhaNangNhinThay {

        @Test
        @DisplayName("Khách KHÔNG thấy bất kỳ người còn sống nào")
        void khachKhongThayNguoiConSong() {
            Person conSong = PersonFixtures.nam("Nguyễn Văn Sống");

            assertThat(privacy.canSee(conSong, CallerContext.guest()))
                    .as("khong phai 'thay moi cai ten' — la khong ton tai trong phan hoi")
                    .isFalse();
        }

        @Test
        @DisplayName("Khách thấy người đã khuất — đúng mục đích của gia phả")
        void khachThayNguoiDaKhuat() {
            Person daKhuat = PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE);

            assertThat(privacy.canSee(daKhuat, CallerContext.guest())).isTrue();
        }

        @Test
        @DisplayName("Thành viên đã đăng nhập thấy người còn sống ở mức tối thiểu")
        void thanhVienThayNguoiConSong() {
            Person conSong = PersonFixtures.nu("Nguyễn Thị Sống");

            assertThat(privacy.canSee(conSong, vai(CallerRole.MEMBER, CHI_AT))).isTrue();
        }

        @Test
        @DisplayName("Bản ghi đã xoá mềm chỉ Hội đồng và Quản trị hệ thống được thấy")
        void banGhiDaXoaMemChiClanWideThay() {
            Person daXoa = PersonFixtures.daKhuat("Cụ Trùng Bản Ghi", Gender.MALE);
            daXoa.softDelete("gộp bản ghi");

            assertThat(privacy.canSee(daXoa, CallerContext.guest())).isFalse();
            assertThat(privacy.canSee(daXoa, vai(CallerRole.MEMBER, CHI_GIAP))).isFalse();
            assertThat(privacy.canSee(daXoa, vai(CallerRole.BRANCH_HEAD, CHI_GIAP, CHI_GIAP))).isFalse();
            assertThat(privacy.canSee(daXoa, vai(CallerRole.COUNCIL, null))).isTrue();
            assertThat(privacy.canSee(daXoa, vai(CallerRole.ADMIN, null))).isTrue();
        }

        @Test
        @DisplayName("Nhân khẩu null không bao giờ nhìn thấy được")
        void nhanKhauNullKhongNhinThayDuoc() {
            assertThat(privacy.canSee(null, vai(CallerRole.ADMIN, null))).isFalse();
        }
    }

    @Nested
    @DisplayName("Tầng dữ liệu cho từng vai")
    class TangDuLieu {

        @Test
        @DisplayName("Người đã khuất luôn ở tầng PUBLIC, kể cả với Khách")
        void nguoiDaKhuatLuonPublic() {
            Person daKhuat = PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE);

            assertThat(privacy.tierFor(daKhuat, CallerContext.guest(), CHI_GIAP))
                    .isEqualTo(VisibleTier.PUBLIC);
            assertThat(privacy.tierFor(daKhuat, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP))
                    .isEqualTo(VisibleTier.PUBLIC);
        }

        @Test
        @DisplayName("Chính chủ và Quản trị hệ thống nhận Tầng 3")
        void chinhChuVaAdminNhanTang3() {
            Person toi = PersonFixtures.nam("Nguyễn Văn Tôi");
            CallerContext chinhChu = new CallerContext(CallerRole.MEMBER, toi.rawId(),
                    List.of(), CHI_AT);

            assertThat(privacy.tierFor(toi, chinhChu, CHI_GIAP)).isEqualTo(VisibleTier.T3);
            assertThat(privacy.tierFor(toi, vai(CallerRole.ADMIN, null), CHI_GIAP))
                    .isEqualTo(VisibleTier.T3);
        }

        @Test
        @DisplayName("Thành viên khác chi chỉ nhận Tầng 1")
        void thanhVienKhacChiChiNhanTang1() {
            Person nguoiKhac = PersonFixtures.nam("Nguyễn Văn Khác");

            assertThat(privacy.tierFor(nguoiKhac, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP))
                    .isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("Thành viên cùng chi nhận Tầng 2")
        void thanhVienCungChiNhanTang2() {
            Person nguoiCungChi = PersonFixtures.nam("Nguyễn Văn Cùng Chi");

            assertThat(privacy.tierFor(nguoiCungChi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .isEqualTo(VisibleTier.T2);
        }

        @Test
        @DisplayName("Trưởng chi có phạm vi trên nhân khẩu nhận Tầng 2, ngoài phạm vi chỉ Tầng 1")
        void truongChiNhanTang2TrongPhamVi() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn A");

            assertThat(privacy.tierFor(nguoi, vai(CallerRole.BRANCH_HEAD, null, CHI_GIAP), CHI_GIAP))
                    .isEqualTo(VisibleTier.T2);
            assertThat(privacy.tierFor(nguoi, vai(CallerRole.BRANCH_HEAD, null, CHI_GIAP), CHI_AT))
                    .isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("Hội đồng Tộc biểu nhận Tầng 2 trên toàn dòng họ, KHÔNG tự động lên Tầng 3")
        void hoiDongNhanTang2ToanDongHo() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn A");

            assertThat(privacy.tierFor(nguoi, vai(CallerRole.COUNCIL, null), CHI_GIAP))
                    .as("SDT va dia chi day du khong phai du lieu quan tri pha he")
                    .isEqualTo(VisibleTier.T2);
        }

        @Test
        @DisplayName("Opt-in toàn dòng họ mở Tầng 3 cho mọi thành viên đã đăng nhập")
        void optInToanDongHoMoTang3() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Mở");
            nguoi.choosePrivacyLevel(PrivacyLevel.CLAN_OPT_IN);

            assertThat(privacy.tierFor(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP))
                    .isEqualTo(VisibleTier.T3);
        }

        @Test
        @DisplayName("Opt-in theo chi chỉ mở Tầng 3 cho người trong phạm vi")
        void optInTheoChiChiMoChoNguoiTrongPhamVi() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Mở Hẹp");
            nguoi.choosePrivacyLevel(PrivacyLevel.BRANCH_OPT_IN);

            assertThat(privacy.tierFor(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .isEqualTo(VisibleTier.T3);
            assertThat(privacy.tierFor(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP))
                    .isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("RESTRICTED siết về Tầng 1 kể cả với người cùng chi")
        void restrictedSietVeTang1() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Kín");
            nguoi.choosePrivacyLevel(PrivacyLevel.RESTRICTED);

            assertThat(privacy.tierFor(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .isEqualTo(VisibleTier.T1);
            assertThat(privacy.tierFor(nguoi, vai(CallerRole.COUNCIL, null), CHI_GIAP))
                    .isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("RESTRICTED không che được chính chủ và Quản trị hệ thống")
        void restrictedKhongCheChinhChuVaAdmin() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Kín");
            nguoi.choosePrivacyLevel(PrivacyLevel.RESTRICTED);
            CallerContext chinhChu = new CallerContext(CallerRole.MEMBER, nguoi.rawId(),
                    List.of(), CHI_AT);

            assertThat(privacy.tierFor(nguoi, chinhChu, CHI_GIAP)).isEqualTo(VisibleTier.T3);
            assertThat(privacy.tierFor(nguoi, vai(CallerRole.ADMIN, null), CHI_GIAP))
                    .isEqualTo(VisibleTier.T3);
        }
    }

    @Nested
    @DisplayName("Trẻ vị thành niên ẩn tối đa")
    class TreViThanhNien {

        private Person treEm() {
            Person tre = PersonFixtures.nam("Nguyễn Văn Bé");
            tre.applyProfileEdit(ProfileEdit.builder()
                    .birth(FieldChange.set(PersonFixtures.sinhNam(Year.now().getValue() - 10)))
                    .build());
            return tre;
        }

        @Test
        @DisplayName("Trẻ vị thành niên bị trần Tầng 1 kể cả với người cùng chi")
        void treViThanhNienBiTranTang1() {
            assertThat(privacy.tierFor(treEm(), vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("Opt-in KHÔNG nới được trần của trẻ vị thành niên")
        void optInKhongNoiDuocTranCuaTre() {
            Person tre = treEm();
            tre.choosePrivacyLevel(PrivacyLevel.CLAN_OPT_IN);

            assertThat(privacy.tierFor(tre, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .as("mot dua tre khong tu quyet duoc viec cong khai du lieu cua chinh minh")
                    .isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("Vừa đủ 18 tuổi thì không còn bị trần vị thành niên")
        void duMuoiTamTuoiThiHetTran() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Vừa Lớn");
            nguoi.applyProfileEdit(ProfileEdit.builder()
                    .birth(FieldChange.set(PersonFixtures.sinhNam(Year.now().getValue() - 18)))
                    .build());

            assertThat(privacy.tierFor(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .isEqualTo(VisibleTier.T2);
        }

        @Test
        @DisplayName("Không rõ năm sinh thì KHÔNG suy đoán là trẻ em")
        void khongRoNamSinhThiKhongSuyDoan() {
            Person khongRo = PersonFixtures.nam("Nguyễn Văn Chưa Rõ");

            assertThat(privacy.tierFor(khongRo, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP))
                    .as("gia pha giay thuong thieu nam sinh cac doi xa; coi ho la tre em thi vo ly")
                    .isEqualTo(VisibleTier.T2);
        }
    }

    @Nested
    @DisplayName("Dựng ngữ cảnh người gọi từ SecurityContext")
    class DungNguCanhNguoiGoi {

        @Test
        @DisplayName("Không có token thì người gọi là Khách vãng lai")
        void khongCoTokenThiLaKhach() {
            GenealogyTestDoubles.dangXuat();

            CallerContext caller = privacy.caller();

            assertThat(caller.isGuest()).isTrue();
            assertThat(caller.personId()).isNull();
            assertThat(caller.managedBranches()).isEmpty();
        }

        @Test
        @DisplayName("Token có vai ADMIN cho ra vai ADMIN kèm nhân khẩu và phạm vi đã phân giải")
        void tokenAdminChoRaVaiAdmin() {
            UUID toi = UUID.randomUUID();
            identity.la(toi).quanTri(CHI_GIAP).chiNha(CHI_GIAP);
            GenealogyTestDoubles.dangNhap("sub-admin", "ADMIN", "MEMBER");

            CallerContext caller = privacy.caller();

            assertThat(caller.role()).isEqualTo(CallerRole.ADMIN);
            assertThat(caller.personId()).isEqualTo(toi);
            assertThat(caller.managedBranches()).containsExactly(CHI_GIAP);
            assertThat(caller.homeBranch()).isEqualTo(CHI_GIAP);
        }

        @Test
        @DisplayName("Tài khoản chưa map sang nhân khẩu vẫn là thành viên, không phải Khách")
        void taiKhoanChuaMapVanLaThanhVien() {
            GenealogyTestDoubles.dangNhap("sub-moi", "MEMBER");

            CallerContext caller = privacy.caller();

            assertThat(caller.isGuest()).isFalse();
            assertThat(caller.role()).isEqualTo(CallerRole.MEMBER);
            assertThat(caller.personId()).isNull();
        }
    }
}

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
import vn.giapha.genealogy.application.view.PersonAccessView;
import vn.giapha.genealogy.domain.FieldChange;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ProfileEdit;
import vn.giapha.genealogy.domain.ShareScope;
import vn.giapha.shared.vo.BranchPath;
import vn.giapha.shared.vo.Gender;

/**
 * <b>Mô hình riêng tư theo từng nhóm trường</b> (BA v2 §10, Nghị định 13/2023) — lớp chịu trách
 * nhiệm pháp lý của context {@code genealogy}.
 *
 * <p>Bốn luật nằm ngoài tay người dùng được ghim ở đây: <b>Khách không thấy bất kỳ người còn sống
 * nào</b>; người đã khuất công khai (trừ khối liên hệ); <b>trẻ vị thành niên ẩn tối đa</b>, chặn
 * trước cả đồng thuận; và chính chủ + Hội đồng luôn xem được.</p>
 *
 * <p>Ma trận đầy đủ 5 nhóm × 3 mức × 4 loại người xem chạy trên CSDL thật ở
 * {@code PrivacyFieldGroupMatrixIT} — phân giải phạm vi chi/ngành từ {@code branch_assignment} và
 * {@code ltree} là chỗ dễ sai nhất và test unit không chạm tới được.</p>
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

    /** Quyết định hiển thị; ném nếu người gọi lẽ ra không được biết bản ghi tồn tại. */
    private PersonVisibility nhinThay(Person person, CallerContext caller, BranchPath chi) {
        return privacy.visibility(person, caller, chi).orElseThrow();
    }

    private static PrivacyConsent moTatCa(ShareScope scope) {
        PrivacyConsent consent = PrivacyConsent.allPrivate();
        for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
            consent = consent.with(group, scope);
        }
        return consent;
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
        @DisplayName("Khách vẫn không thấy dù chủ thể đã mở HẾT cho cả họ")
        void khachKhongThayDuMoHet() {
            Person conSong = PersonFixtures.nam("Nguyễn Văn Cởi Mở");
            conSong.choosePrivacyConsent(moTatCa(ShareScope.CLAN));

            assertThat(privacy.visibility(conSong, CallerContext.guest(), CHI_GIAP))
                    .as("ranh gioi phap ly cua Nghi dinh 13/2023, khong nam trong tay nguoi dung")
                    .isEmpty();
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
            assertThat(privacy.visibility(null, vai(CallerRole.ADMIN, null), CHI_GIAP)).isEmpty();
        }

        @Test
        @DisplayName("canSee và visibility không bao giờ trả lời khác nhau")
        void canSeeVaVisibilityDongBo() {
            Person conSong = PersonFixtures.nam("Nguyễn Văn Sống");
            Person daKhuat = PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE);
            List<CallerContext> nguoiGoi = List.of(CallerContext.guest(),
                    vai(CallerRole.MEMBER, CHI_AT), vai(CallerRole.COUNCIL, null),
                    vai(CallerRole.ADMIN, null), vai(CallerRole.BRANCH_HEAD, CHI_GIAP, CHI_GIAP));

            for (Person person : List.of(conSong, daKhuat)) {
                for (CallerContext caller : nguoiGoi) {
                    assertThat(privacy.visibility(person, caller, CHI_GIAP).isPresent())
                            .as("hai loi hoi cung mot cau thi phai cung mot cau tra loi")
                            .isEqualTo(privacy.canSee(person, caller));
                }
            }
        }
    }

    @Nested
    @DisplayName("Mặc định là KÍN")
    class MacDinhLaKin {

        @Test
        @DisplayName("Nhân khẩu mới bắt đầu với cả năm nhóm ở mức Riêng tư")
        void nhanKhauMoiKinHoanToan() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Mới");

            assertThat(nguoi.privacyConsent().isAllPrivate()).isTrue();
            PersonVisibility vis = nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP);
            for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
                assertThat(vis.allows(group))
                        .as("nhom %s phai bat dau o muc kin, he thong khong tu mo ho", group)
                        .isFalse();
            }
            assertThat(vis.tier()).isEqualTo(VisibleTier.T1);
        }

        @Test
        @DisplayName("Người cùng chi vẫn không thấy gì khi chủ thể chưa chọn mở nhóm nào")
        void cungChiVanKhongThayKhiChuaChon() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Chưa Chọn");

            PersonVisibility vis = nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP);

            assertThat(vis.allows(PrivacyFieldGroup.OCCUPATION)).isFalse();
            assertThat(vis.allows(PrivacyFieldGroup.CONTACT)).isFalse();
        }
    }

    @Nested
    @DisplayName("Từng nhóm trường một mức độc lập")
    class TungNhomMotMuc {

        @Test
        @DisplayName("Nghề nghiệp cả họ, liên hệ cùng chi, ngày sinh riêng tư — ba mức cùng lúc")
        void baMucCungLuc() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Ba Mức");
            nguoi.choosePrivacyConsent(PrivacyConsent.allPrivate()
                    .with(PrivacyFieldGroup.OCCUPATION, ShareScope.CLAN)
                    .with(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH)
                    .with(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO, ShareScope.PRIVATE));

            PersonVisibility khacChi = nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP);
            PersonVisibility cungChi = nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP);

            assertThat(khacChi.allows(PrivacyFieldGroup.OCCUPATION)).isTrue();
            assertThat(khacChi.allows(PrivacyFieldGroup.CONTACT)).isFalse();
            assertThat(cungChi.allows(PrivacyFieldGroup.CONTACT)).isTrue();
            assertThat(cungChi.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO)).isFalse();
        }

        @Test
        @DisplayName("Mở một nhóm KHÔNG kéo theo nhóm nào khác")
        void moMotNhomKhongKeoTheoNhomKhac() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Một Nhóm");
            nguoi.choosePrivacyScope(PrivacyFieldGroup.OCCUPATION, ShareScope.CLAN);

            PersonVisibility vis = nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP);

            assertThat(vis.allows(PrivacyFieldGroup.OCCUPATION)).isTrue();
            assertThat(vis.allows(PrivacyFieldGroup.RESIDENCE_PROVINCE)).isFalse();
            assertThat(vis.allows(PrivacyFieldGroup.RESIDENCE_FULL)).isFalse();
            assertThat(vis.allows(PrivacyFieldGroup.CONTACT)).isFalse();
            assertThat(vis.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO)).isFalse();
        }

        @Test
        @DisplayName("Mức BRANCH đọc theo phạm vi ltree hai chiều, không phải so chuỗi")
        void mucBranchTheoLtree() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Cành");
            nguoi.choosePrivacyScope(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH);
            BranchPath canh = BranchPath.of("goc.chi_giap.canh_mot");

            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), canh)
                    .allows(PrivacyFieldGroup.CONTACT))
                    .as("nguoi dung o goc chi van la 'cung chi' voi nguoi o canh nho")
                    .isTrue();
            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, canh), CHI_GIAP)
                    .allows(PrivacyFieldGroup.CONTACT))
                    .as("va nguoc lai")
                    .isTrue();
            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP)
                    .allows(PrivacyFieldGroup.CONTACT)).isFalse();
        }

        @Test
        @DisplayName("Trưởng chi được giao phạm vi tính là cùng chi; ngoài phạm vi thì không")
        void truongChiTinhLaCungChi() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn A");
            nguoi.choosePrivacyScope(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH);

            assertThat(nhinThay(nguoi, vai(CallerRole.BRANCH_HEAD, null, CHI_GIAP), CHI_GIAP)
                    .allows(PrivacyFieldGroup.CONTACT)).isTrue();
            assertThat(nhinThay(nguoi, vai(CallerRole.BRANCH_HEAD, null, CHI_GIAP), CHI_AT)
                    .allows(PrivacyFieldGroup.CONTACT)).isFalse();
        }
    }

    @Nested
    @DisplayName("Luật nằm ngoài tay người dùng")
    class LuatNgoaiTamNguoiDung {

        @Test
        @DisplayName("Người đã khuất công khai, nhưng khối liên hệ thì KHÔNG")
        void nguoiDaKhuatCongKhaiTruLienHe() {
            Person daKhuat = PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE);

            PersonVisibility khach = nhinThay(daKhuat, CallerContext.guest(), CHI_GIAP);

            assertThat(khach.tier()).isEqualTo(VisibleTier.PUBLIC);
            assertThat(khach.allows(PrivacyFieldGroup.OCCUPATION)).isTrue();
            assertThat(khach.allows(PrivacyFieldGroup.RESIDENCE_FULL)).isTrue();
            assertThat(khach.allows(PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO)).isTrue();
            assertThat(khach.allows(PrivacyFieldGroup.CONTACT))
                    .as("so ghi trong ho so mot cu da mat thuc chat la so cua nguoi than dang song")
                    .isFalse();
        }

        @Test
        @DisplayName("Hội đồng đọc được khối liên hệ của người đã khuất, Khách thì không")
        void hoiDongDocDuocLienHeCuaNguoiDaKhuat() {
            Person daKhuat = PersonFixtures.daKhuat("Cụ Tổ", Gender.MALE);

            assertThat(nhinThay(daKhuat, vai(CallerRole.COUNCIL, null), CHI_GIAP)
                    .allows(PrivacyFieldGroup.CONTACT)).isTrue();
            assertThat(nhinThay(daKhuat, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP)
                    .allows(PrivacyFieldGroup.CONTACT)).isFalse();
        }

        @Test
        @DisplayName("Chủ thể không áp được mô hình đồng thuận lên chính mình sau khi mất")
        void dongThuanKhongApDungChoNguoiDaKhuat() {
            Person daKhuat = PersonFixtures.daKhuat("Cụ Kín", Gender.MALE);
            daKhuat.choosePrivacyConsent(PrivacyConsent.allPrivate());

            assertThat(nhinThay(daKhuat, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP)
                    .allows(PrivacyFieldGroup.OCCUPATION))
                    .as("BA v2 §10: nguoi da khuat la du lieu cong khai")
                    .isTrue();
        }

        @Test
        @DisplayName("Chính chủ và Quản trị hệ thống xem được dù mọi nhóm ở Riêng tư")
        void chinhChuVaAdminLuonXemDuoc() {
            Person toi = PersonFixtures.nam("Nguyễn Văn Tôi");
            CallerContext chinhChu = new CallerContext(CallerRole.MEMBER, toi.rawId(),
                    List.of(), CHI_AT);

            assertThat(nhinThay(toi, chinhChu, CHI_GIAP).tier()).isEqualTo(VisibleTier.T3);
            assertThat(nhinThay(toi, vai(CallerRole.ADMIN, null), CHI_GIAP).tier())
                    .isEqualTo(VisibleTier.T3);
            assertThat(nhinThay(toi, vai(CallerRole.COUNCIL, null), CHI_GIAP).tier())
                    .as("muc Rieng tu = chinh chu + Hoi dong, dung dinh nghia da chot")
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
        @DisplayName("Đồng thuận mở hết KHÔNG nới được trần của trẻ vị thành niên")
        void dongThuanKhongNoiDuocTranCuaTre() {
            Person tre = treEm();
            tre.choosePrivacyConsent(moTatCa(ShareScope.CLAN));

            PersonVisibility vis = nhinThay(tre, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP);

            for (PrivacyFieldGroup group : PrivacyFieldGroup.values()) {
                assertThat(vis.allows(group))
                        .as("mot dua tre khong tu quyet duoc viec cong khai du lieu cua chinh minh")
                        .isFalse();
            }
            assertThat(vis.tier()).isEqualTo(VisibleTier.T1);
            assertThat(vis.ungroupedFieldsVisible())
                    .as("cung chi cung khong doc duoc nguyen quan / nam sinh cua tre")
                    .isFalse();
        }

        @Test
        @DisplayName("Vừa đủ 18 tuổi thì không còn bị trần vị thành niên")
        void duMuoiTamTuoiThiHetTran() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Vừa Lớn");
            nguoi.applyProfileEdit(ProfileEdit.builder()
                    .birth(FieldChange.set(PersonFixtures.sinhNam(Year.now().getValue() - 18)))
                    .build());
            nguoi.choosePrivacyScope(PrivacyFieldGroup.OCCUPATION, ShareScope.BRANCH);

            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP)
                    .allows(PrivacyFieldGroup.OCCUPATION)).isTrue();
        }

        @Test
        @DisplayName("Không rõ năm sinh thì KHÔNG suy đoán là trẻ em")
        void khongRoNamSinhThiKhongSuyDoan() {
            Person khongRo = PersonFixtures.nam("Nguyễn Văn Chưa Rõ");
            khongRo.choosePrivacyScope(PrivacyFieldGroup.OCCUPATION, ShareScope.BRANCH);

            assertThat(nhinThay(khongRo, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP)
                    .allows(PrivacyFieldGroup.OCCUPATION))
                    .as("gia pha giay thuong thieu nam sinh cac doi xa; coi ho la tre em thi vo ly")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Tóm tắt tầng cho meta.visibleTier")
    class TomTatTang {

        @Test
        @DisplayName("Chỉ mở nghề nghiệp / tỉnh thì tóm tắt là T2")
        void moNhomNheThiT2() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Nhẹ");
            nguoi.choosePrivacyScope(PrivacyFieldGroup.OCCUPATION, ShareScope.CLAN);

            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP).tier())
                    .isEqualTo(VisibleTier.T2);
        }

        @Test
        @DisplayName("Mở một nhóm nhạy cảm thì tóm tắt lên T3")
        void moNhomNhayCamThiT3() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Nhạy");
            nguoi.choosePrivacyScope(PrivacyFieldGroup.CONTACT, ShareScope.CLAN);

            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP).tier())
                    .isEqualTo(VisibleTier.T3);
        }

        @Test
        @DisplayName("Tóm tắt nói đúng thứ thực sự chạm tới được, không phải thứ chủ thể đã chọn")
        void tomTatNoiDungThucTe() {
            Person nguoi = PersonFixtures.nam("Nguyễn Văn Hẹp");
            nguoi.choosePrivacyScope(PrivacyFieldGroup.CONTACT, ShareScope.BRANCH);

            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_AT), CHI_GIAP).tier())
                    .as("chu the da mo, nhung mo cho NGUOI KHAC — nguoi goi nay van o T1")
                    .isEqualTo(VisibleTier.T1);
            assertThat(nhinThay(nguoi, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP).tier())
                    .isEqualTo(VisibleTier.T3);
        }
    }

    @Nested
    @DisplayName("Ba cờ quyền của meta — nói về NGƯỜI GỌI, không về dữ liệu bị giấu")
    class BaCoQuyen {

        /** Người gọi là chính chủ của {@code person}. */
        private CallerContext chinhChu(Person person, BranchPath chiNha) {
            return new CallerContext(CallerRole.MEMBER, person.rawId(), List.of(), chiNha);
        }

        private PersonAccessView quyenCua(Person person, CallerContext caller, BranchPath chi) {
            return privacy.accessOf(person, caller, nhinThay(person, caller, chi), chi);
        }

        /**
         * Khiếm khuyết đã đo: cờ này từng là hằng số {@code false}, và
         * {@code CorrectionRequestEntry} của giao diện tự ẩn khi nó sai — nên luồng đính chính
         * <b>không có điểm bắt đầu nào</b>, trong khi
         * {@code POST /api/v1/change-requests} vẫn nhận đơn và trả {@code 201}.
         */
        @Test
        @DisplayName("Thành viên thường ĐỀ NGHỊ ĐÍNH CHÍNH được trên hồ sơ người khác")
        void thanhVienDeNghiDinhChinhDuoc() {
            Person nguoiKhac = PersonFixtures.nu("Nguyễn Thị Người Khác");

            PersonAccessView quyen = quyenCua(nguoiKhac, vai(CallerRole.MEMBER, CHI_GIAP), CHI_GIAP);

            assertThat(quyen.canEdit())
                    .as("thanh vien thuong khong ghi thang len ho so nguoi khac")
                    .isFalse();
            assertThat(quyen.canRequestCorrection())
                    .as("khong co co nay thi ho so nguoi khac khong con nut 'De nghi dinh chinh'")
                    .isTrue();
        }

        @Test
        @DisplayName("Chính chủ KHÔNG cần đề nghị đính chính — họ sửa thẳng")
        void chinhChuSuaThang() {
            Person toi = PersonFixtures.nu("Nguyễn Thị Chính Chủ");

            PersonAccessView quyen = quyenCua(toi, chinhChu(toi, CHI_GIAP), CHI_GIAP);

            assertThat(quyen.isSelf()).isTrue();
            assertThat(quyen.canEdit()).isTrue();
            assertThat(quyen.canRequestCorrection())
                    .as("bat chinh chu di vong qua hang doi duyet la vo nghia")
                    .isFalse();
        }

        @Test
        @DisplayName("Trưởng chi TRONG phạm vi sửa thẳng; NGOÀI phạm vi thì đề nghị đính chính")
        void truongChiTheoPhamVi() {
            Person trongChi = PersonFixtures.nu("Nguyễn Thị Trong Chi");
            Person ngoaiChi = PersonFixtures.nu("Nguyễn Thị Ngoài Chi");
            CallerContext truongChiGiap = vai(CallerRole.BRANCH_HEAD, CHI_GIAP, CHI_GIAP);

            PersonAccessView trong = quyenCua(trongChi, truongChiGiap, CHI_GIAP);
            PersonAccessView ngoai = quyenCua(ngoaiChi, truongChiGiap, CHI_AT);

            assertThat(trong.canEdit()).isTrue();
            assertThat(trong.canRequestCorrection()).isFalse();
            assertThat(ngoai.canEdit())
                    .as("ranh gioi ltree: Truong chi Giap khong ghi len Chi At")
                    .isFalse();
            assertThat(ngoai.canRequestCorrection())
                    .as("khong ghi duoc thi phai de nghi duoc, neu khong ho khong con loi nao")
                    .isTrue();
        }

        /**
         * <b>Ca mà {@link #truongChiTheoPhamVi} không bao giờ chạm tới.</b>
         *
         * <p>Ca kia dựng vai và nhân khẩu ở hai chi khác hẳn nhau, nên {@code managesBranch} và
         * {@code sharesBranchWith} <i>cùng</i> sai — hai vế của một phép {@code ||} không bao giờ
         * tách ra được. Ca này tách đúng hai vế ấy: Trưởng chi được giao
         * {@code chi_giap.nganh_truong}, nhưng nhân khẩu của <b>chính ông</b> ở {@code chi_giap},
         * và ông mở hồ sơ một người thuộc {@code chi_giap.nganh_hai}.</p>
         *
         * <ul>
         *   <li>{@code managesBranch(nganh_hai)} = sai — {@code nganh_truong} không phải tổ tiên
         *       {@code ltree} của {@code nganh_hai};</li>
         *   <li>{@code sharesBranchWith(nganh_hai)} = đúng — chi nhà {@code chi_giap} là tổ tiên
         *       của nó. Đó là Tầng 2 "cùng chi", một khái niệm <b>hiển thị</b>.</li>
         * </ul>
         *
         * <p>Khi {@code branchHead} còn tính theo {@code inBranchScope} thì hai cờ cùng sai một
         * lượt: {@code canEdit} bật (giao diện mời bấm "Sửa" → {@code PATCH} trả {@code 403}) và
         * {@code canRequestCorrection} tắt (đường vòng đúng thì biến mất). Đổi một nút mất tích
         * lấy một nút dẫn tới lỗi.</p>
         */
        @Test
        @DisplayName("Trưởng chi CÙNG CHI NHÀ nhưng NGOÀI phạm vi được giao: không sửa thẳng, "
                + "nhưng phải đề nghị được")
        void cungChiNhaKhongPhaiQuyenGhi() {
            BranchPath nganhTruong = BranchPath.of("goc.chi_giap.nganh_truong");
            BranchPath nganhHai = BranchPath.of("goc.chi_giap.nganh_hai");
            Person nguoiNganhHai = PersonFixtures.nu("Nguyễn Thị Ngành Hai");
            // Chi nha = chi_giap (cha cua ca hai nganh); pham vi duoc giao = nganh_truong.
            CallerContext truongNganhTruong =
                    vai(CallerRole.BRANCH_HEAD, CHI_GIAP, nganhTruong);

            PersonAccessView quyen = quyenCua(nguoiNganhHai, truongNganhTruong, nganhHai);

            assertThat(quyen.canEdit())
                    .as("GenealogyAccessGuard chi cho ghi khi managesBranch dung;"
                            + " cung chi nha khong phai quyen ghi — bam Sua se an 403")
                    .isFalse();
            assertThat(quyen.canDelete())
                    .as("xoa mem di theo dung luat voi ghi — cung mot phep tinh branchHead")
                    .isFalse();
            assertThat(quyen.canRequestCorrection())
                    .as("khong ghi duoc thi duong vong phai mo, neu khong ho khong con loi nao")
                    .isTrue();
        }

        /** Chiều ngược lại, để phép sửa không biến thành "Trưởng chi không bao giờ ghi được". */
        @Test
        @DisplayName("Trưởng chi ĐÚNG phạm vi được giao vẫn sửa thẳng ở một ngành con sâu hơn")
        void dungPhamViThiVanGhiDuoc() {
            BranchPath nganhTruong = BranchPath.of("goc.chi_giap.nganh_truong");
            BranchPath canhSau = BranchPath.of("goc.chi_giap.nganh_truong.canh_mot");
            Person nguoiCanhSau = PersonFixtures.nu("Nguyễn Thị Cành Một");
            CallerContext truongNganhTruong =
                    vai(CallerRole.BRANCH_HEAD, CHI_GIAP, nganhTruong);

            PersonAccessView quyen = quyenCua(nguoiCanhSau, truongNganhTruong, canhSau);

            assertThat(quyen.canEdit())
                    .as("ngu nghia @> cua ltree: pham vi duoc giao trum len moi cay con")
                    .isTrue();
            assertThat(quyen.canRequestCorrection()).isFalse();
        }

        @Test
        @DisplayName("Hội đồng sửa thẳng nên không có cờ đề nghị đính chính")
        void hoiDongSuaThang() {
            Person ai = PersonFixtures.nu("Nguyễn Thị Bất Kỳ");

            PersonAccessView quyen = quyenCua(ai, vai(CallerRole.COUNCIL, CHI_GIAP), CHI_AT);

            assertThat(quyen.canEdit()).isTrue();
            assertThat(quyen.canRequestCorrection()).isFalse();
        }

        /**
         * Tài khoản vừa gõ xong mã mời: <b>có</b> {@code app_user} nhưng chưa được duyệt vào phả.
         *
         * <p>Đây là ca mà một cờ tính theo "có token" sẽ sai: giao diện sẽ mời họ gửi đính chính,
         * rồi máy chủ đóng cửa bằng {@code ACCOUNT_NOT_ACTIVE}
         * ({@code BranchScopeGuard.requireActiveMember}). Hai bên phải nói cùng một câu.</p>
         */
        @Test
        @DisplayName("Tài khoản CHƯA được duyệt vào phả thì chưa đề nghị đính chính được")
        void chuaDuyetVaoPhaThiChuaDeNghiDuoc() {
            Person nguoiKhac = PersonFixtures.nu("Nguyễn Thị Người Khác");
            // personId = null: dung trang thai cua tai khoan vua dang ky bang ma moi dong ho.
            CallerContext chuaDuyet =
                    new CallerContext(CallerRole.MEMBER, null, List.of(), CHI_GIAP);

            PersonAccessView quyen = quyenCua(nguoiKhac, chuaDuyet, CHI_GIAP);

            assertThat(quyen.canEdit()).isFalse();
            assertThat(quyen.canRequestCorrection())
                    .as("chua duyet thi chua gui duoc gi vao hang cho cua Truong chi")
                    .isFalse();
        }

        /**
         * Bề mặt công khai đòi cả ba cờ phải tắt — {@code PublicVisibilityGuard} ném
         * {@code IllegalStateException} nếu không, nên ca này canh đúng bất biến ấy tại nguồn.
         */
        @Test
        @DisplayName("Khách KHÔNG đề nghị đính chính được — bề mặt công khai đòi cả ba cờ tắt")
        void khachKhongCoCoNao() {
            Person daKhuat = PersonFixtures.daKhuat("Cụ Tổ Công Khai", Gender.MALE);
            CallerContext khach = CallerContext.guest();

            PersonAccessView quyen = quyenCua(daKhuat, khach, CHI_GIAP);

            assertThat(quyen.canEdit()).isFalse();
            assertThat(quyen.canDelete()).isFalse();
            assertThat(quyen.canRequestCorrection()).isFalse();
            assertThat(quyen.isSelf()).isFalse();
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

package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.GenealogyTestDoubles.FakeTabooNamePort;
import vn.giapha.genealogy.application.command.AddPersonCommand;
import vn.giapha.genealogy.application.command.RelationshipLinkCommand;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.ContactInfo;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.NameType;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.genealogy.domain.PersonName;
import vn.giapha.genealogy.domain.PrivacyConsent;
import vn.giapha.genealogy.domain.PrivacyFieldGroup;
import vn.giapha.genealogy.domain.ShareScope;
import vn.giapha.genealogy.domain.RelType;
import vn.giapha.genealogy.domain.port.AuditPort;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.exception.ForbiddenException;
import vn.giapha.shared.vo.Gender;

/**
 * Use case <b>thêm một nhân khẩu vào gia phả</b>.
 *
 * <p>Ba điều quan trọng nhất được ghim ở đây: đời thứ và chi/ngành được <b>suy ra từ quan hệ</b>
 * chứ không nhận từ client (nên không bao giờ lệch với đồ thị); cảnh báo kỵ húy chạy <b>trước mọi
 * lệnh ghi</b> (có va chạm mà chưa xác nhận thì không bản ghi nào được tạo); và đỉnh AGE cùng dòng
 * {@code person} cùng sống hoặc cùng biến mất.
 */
class AddPersonServiceTest {

    /** Mọi nhóm trường mở cho cả họ — mức rộng nhất mà một client có thể xin. */
    private static final PrivacyConsent CA_HO_XEM_HET = PrivacyConsent.of(java.util.Map.of(
            PrivacyFieldGroup.OCCUPATION, ShareScope.CLAN,
            PrivacyFieldGroup.RESIDENCE_PROVINCE, ShareScope.CLAN,
            PrivacyFieldGroup.RESIDENCE_FULL, ShareScope.CLAN,
            PrivacyFieldGroup.CONTACT, ShareScope.CLAN,
            PrivacyFieldGroup.BIRTH_DETAIL_AND_PHOTO, ShareScope.CLAN));

    private GenealogyServiceFixture fx;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    private static AddPersonCommand lenh(String ten, Gender gioi, UUID chiId,
                                         List<RelationshipLinkCommand> links) {
        return new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, ten, true)),
                gioi, true, null, null, null, null, null, null, null,
                chiId, null, null, null, links, false, false, null);
    }

    @Test
    @DisplayName("Thêm nhân khẩu ghi cả dòng person lẫn đỉnh đồ thị, rồi dọn cache cây")
    void themNhanKhauGhiCaBangLanDoThi() {
        PersonView view = fx.addPerson.add(lenh("Nguyễn Văn Cả", Gender.MALE, fx.chiGiap.id(), List.of()));

        assertThat(fx.persons.exists(vn.giapha.shared.vo.PersonId.of(view.id()))).isTrue();
        assertThat(fx.graph.nodeDaTao)
                .as("mot nhan khau co dong ma khong co dinh do thi la hong du lieu khong ai bao")
                .containsExactly(view.id());
        assertThat(fx.treeCache.soLanEvictAll).isEqualTo(1);
        assertThat(fx.audit.hanhDong()).containsExactly(AuditPort.Action.CREATE);
    }

    @Test
    @DisplayName("Lệnh tạo phát PersonAddedEvent, kèm một sự kiện phụ sinh ra lúc dựng aggregate")
    void lenhTaoPhatSuKienNao() {
        fx.addPerson.add(lenh("Nguyễn Văn Cả", Gender.MALE, fx.chiGiap.id(), List.of()));

        assertThat(fx.suKien).extracting(vn.giapha.shared.domain.DomainEvent::eventType)
                .as("PersonMovedBranchEvent la SAN PHAM PHU cua viec dung aggregate (moveToBranch) "
                        + "chu khong phai mot lan chuyen chi that. Consumer nhac gio/thong bao phai "
                        + "idempotent va khong duoc coi no la thao tac cua nguoi dung. "
                        + "PersonUpdatedEvent KHONG con xuat hien: choosePrivacyConsent() bo qua khi "
                        + "ban dong thuan khong doi, ma nhan khau moi von da kin hoan toan — "
                        + "khong co thay doi nao de bao.")
                .containsExactly("PersonAddedEvent", "PersonMovedBranchEvent");
    }

    @Test
    @DisplayName("Đời thứ và chi được suy ra từ người cha, không nhận từ client")
    void doiThuVaChiSuyRaTuNguoiCha() {
        Person cha = fx.seed(PersonFixtures.nam("Nguyễn Văn Cha"), fx.chiGiap);
        cha.placeInGeneration(4);
        fx.persons.seed(cha);

        PersonView con = fx.addPerson.add(lenh("Nguyễn Văn Con", Gender.MALE, null,
                List.of(new RelationshipLinkCommand(RelType.PARENT_BIO, cha.rawId(), true,
                        null, null, null, null, null))));

        assertThat(con.generation()).isEqualTo(5);
        assertThat(con.primaryBranch().id()).isEqualTo(fx.chiGiap.id());
    }

    @Test
    @DisplayName("Thứ tự sinh = số con hiện có của người cha + 1")
    void thuTuSinhLaSoConHienCoCongMot() {
        Person cha = fx.seed(PersonFixtures.nam("Nguyễn Văn Cha"), fx.chiGiap);
        cha.placeInGeneration(4);
        fx.persons.seed(cha);
        Person conCa = fx.seed(PersonFixtures.nam("Con Cả"), fx.chiGiap);
        Person conThu = fx.seed(PersonFixtures.nu("Con Thứ"), fx.chiGiap);
        fx.seedParent(cha, conCa, false);
        fx.seedParent(cha, conThu, false);

        PersonView conBa = fx.addPerson.add(lenh("Con Ba", Gender.FEMALE, null,
                List.of(new RelationshipLinkCommand(RelType.PARENT_BIO, cha.rawId(), true,
                        null, null, null, null, null))));

        Person daLuu = fx.persons.byId(vn.giapha.shared.vo.PersonId.of(conBa.id())).orElseThrow();
        assertThat(daLuu.birthOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("Thêm CHA cho một người đã có: đời thứ suy ngược = đời con trừ 1")
    void themChaThiDoiThuSuyNguoc() {
        Person con = fx.seed(PersonFixtures.nam("Nguyễn Văn Con"), fx.chiGiap);
        con.placeInGeneration(5);
        fx.persons.seed(con);

        PersonView cha = fx.addPerson.add(lenh("Nguyễn Văn Cha", Gender.MALE, null,
                List.of(new RelationshipLinkCommand(RelType.PARENT_BIO, con.rawId(), false,
                        null, null, null, null, null))));

        assertThat(cha.generation()).isEqualTo(4);
        assertThat(cha.primaryBranch().id()).isEqualTo(fx.chiGiap.id());
    }

    @Test
    @DisplayName("Con của Thuỷ tổ thì không suy ngược được nữa — đời thứ để trống, không đặt 0")
    void khongSuyNguocQuaThuyTo() {
        Person thuyTo = fx.seed(PersonFixtures.nam("Thuỷ Tổ"), fx.chiGiap);
        thuyTo.placeInGeneration(1);
        fx.persons.seed(thuyTo);

        PersonView truocThuyTo = fx.addPerson.add(lenh("Vô Danh", Gender.MALE, null,
                List.of(new RelationshipLinkCommand(RelType.PARENT_BIO, thuyTo.rawId(), false,
                        null, null, null, null, null))));

        assertThat(truocThuyTo.generation())
                .as("doi thu 0 hoac am se pha vo rang buoc doi thu >= 1 cua domain")
                .isNull();
    }

    @Test
    @DisplayName("Thêm vợ: đời thứ ước lượng bằng đời của chồng, chi kế thừa theo chồng")
    void themVoThiDoiThuBangDoiChong() {
        Person chong = fx.seed(PersonFixtures.nam("Nguyễn Văn Chồng"), fx.chiGiap);
        chong.placeInGeneration(6);
        fx.persons.seed(chong);

        PersonView vo = fx.addPerson.add(lenh("Trần Thị Vợ", Gender.FEMALE, null,
                List.of(new RelationshipLinkCommand(RelType.SPOUSE, chong.rawId(), true,
                        null, 2, null, null, null))));

        assertThat(vo.generation()).isEqualTo(6);
        assertThat(fx.relationships.all()).singleElement()
                .satisfies(edge -> {
                    assertThat(edge.relType()).isEqualTo(RelType.SPOUSE);
                    assertThat(edge.spouseOrder()).as("vo hai — spouse_order = 2").isEqualTo(2);
                });
    }

    @Test
    @DisplayName("Chi client gửi được ưu tiên hơn chi kế thừa từ cha")
    void chiClientGuiDuocUuTien() {
        Person cha = fx.seed(PersonFixtures.nam("Nguyễn Văn Cha"), fx.chiGiap);
        cha.placeInGeneration(4);
        fx.persons.seed(cha);

        PersonView con = fx.addPerson.add(lenh("Nguyễn Văn Con", Gender.MALE, fx.chiAt.id(),
                List.of(new RelationshipLinkCommand(RelType.PARENT_BIO, cha.rawId(), true,
                        null, null, null, null, null))));

        assertThat(con.primaryBranch().id()).isEqualTo(fx.chiAt.id());
    }

    @Test
    @DisplayName("Kỵ húy chưa xác nhận: KHÔNG bản ghi nào được tạo, không đỉnh, không nhật ký")
    void kyHuyChuaXacNhanThiKhongGhiGiCa() {
        fx.tabooPort.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 3));
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)),
                Gender.MALE, true, null, null, null, null, null, null, null,
                fx.chiGiap.id(), null, null, null, List.of(), false, false, null);

        assertThatThrownBy(() -> fx.addPerson.add(cmd))
                .isInstanceOf(TabooNameConflictException.class);

        assertThat(fx.persons.soLanSave).as("ngoai le phai duoc nem TRUOC moi lenh ghi").isZero();
        assertThat(fx.graph.nodeDaTao).isEmpty();
        assertThat(fx.audit.dong).isEmpty();
        assertThat(fx.treeCache.soLanEvictAll).isZero();
    }

    @Test
    @DisplayName("Kỵ húy đã xác nhận: ghi bình thường và lưu lý do ghi đè vào nhật ký")
    void kyHuyDaXacNhanThiGhiVaLuuLyDo() {
        fx.tabooPort.vaChamVoi("Nguyễn Văn Tuân", FakeTabooNamePort.cuTo("Nguyễn Văn Tuân", 3));
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.HUY, "Nguyễn Văn Tuân", true)),
                Gender.MALE, true, null, null, null, null, null, null, null,
                fx.chiGiap.id(), null, null, null, List.of(), true, false, "chép từ gia phả giấy 1998");

        PersonView view = fx.addPerson.add(cmd);

        assertThat(view.id()).isNotNull();
        assertThat(fx.audit.cuoiCung().note())
                .as("ghi de canh bao ky huy phai de lai dau vet trong audit_log")
                .contains("chép từ gia phả giấy 1998")
                .contains("Ghi de canh bao ky huy");
        assertThat(fx.audit.cuoiCung().note())
                .as("dau vet ay mang KHOA chu khong mang ten doc tu pha — xem TabooNameChecker")
                .doesNotContain("Nguyễn Văn Tuân");
    }

    @Test
    @DisplayName("Trẻ vị thành niên bị ép kín hoàn toàn bất kể client gửi mức nào")
    void treViThanhNienBiEpKinHoanToan() {
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Bé", true)),
                Gender.MALE, true, PersonFixtures.sinhNam(Year.now().getValue() - 8), null,
                null, null, null, null, null, fx.chiGiap.id(), null, null,
                CA_HO_XEM_HET, List.of(), false, false, null);

        PersonView view = fx.addPerson.add(cmd);

        Person daLuu = fx.persons.byId(vn.giapha.shared.vo.PersonId.of(view.id())).orElseThrow();
        assertThat(daLuu.privacyConsent().isAllPrivate())
                .as("mot dua tre khong tu quyet duoc, va nguoi nhap lieu cung khong quyet thay duoc")
                .isTrue();
    }

    @Test
    @DisplayName("Người lớn giữ đúng mức riêng tư mình chọn")
    void nguoiLonGiuDungMucRiengTuDaChon() {
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Văn Lớn", true)),
                Gender.MALE, true, PersonFixtures.sinhNam(1980), null,
                null, null, null, null, null, fx.chiGiap.id(), null, null,
                CA_HO_XEM_HET, List.of(), false, false, null);

        PersonView view = fx.addPerson.add(cmd);

        Person daLuu = fx.persons.byId(vn.giapha.shared.vo.PersonId.of(view.id())).orElseThrow();
        assertThat(daLuu.privacyConsent()).isEqualTo(CA_HO_XEM_HET);
    }

    @Test
    @DisplayName("Trưởng Chi KHÔNG thêm được nhân khẩu vào chi khác — 403 BRANCH_SCOPE_VIOLATION")
    void truongChiKhongThemDuocVaoChiKhac() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_AT);

        assertThatThrownBy(() -> fx.addPerson.add(
                lenh("Nguyễn Văn Lạ", Gender.MALE, fx.chiGiap.id(), List.of())))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.BRANCH_SCOPE_VIOLATION);

        assertThat(fx.persons.soLanSave).isZero();
        assertThat(fx.graph.nodeDaTao).isEmpty();
    }

    @Test
    @DisplayName("Trưởng Chi thêm được nhân khẩu trong chi mình")
    void truongChiThemDuocTrongChiMinh() {
        fx.dangXuat();
        fx.dangNhapTruongChi(GenealogyServiceFixture.P_CHI_GIAP);

        PersonView view = fx.addPerson.add(lenh("Nguyễn Văn Nhà", Gender.MALE,
                fx.chiGiap.id(), List.of()));

        assertThat(view.id()).isNotNull();
    }

    @Test
    @DisplayName("Nhân khẩu bắt buộc có ít nhất một lớp tên — 422 VALIDATION_FAILED")
    void batBuocCoItNhatMotLopTen() {
        AddPersonCommand cmd = new AddPersonCommand(List.of(), Gender.MALE, true, null, null,
                null, null, null, null, null, fx.chiGiap.id(), null, null, null,
                List.of(), false, false, null);

        assertThatThrownBy(() -> fx.addPerson.add(cmd))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(GenealogyProblemCodes.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Con gái được thêm y hệt con trai: cùng đời, cùng chi, cùng thứ tự sinh")
    void conGaiDuocThemYHetConTrai() {
        Person cha = fx.seed(PersonFixtures.nam("Nguyễn Văn Cha"), fx.chiGiap);
        cha.placeInGeneration(4);
        fx.persons.seed(cha);
        RelationshipLinkCommand laConCuaCha = new RelationshipLinkCommand(
                RelType.PARENT_BIO, cha.rawId(), true, null, null, null, null, null);

        PersonView conTrai = fx.addPerson.add(lenh("Nguyễn Văn Trai", Gender.MALE, null,
                List.of(laConCuaCha)));
        PersonView conGai = fx.addPerson.add(lenh("Nguyễn Thị Gái", Gender.FEMALE, null,
                List.of(laConCuaCha)));

        assertThat(conGai.generation()).isEqualTo(conTrai.generation());
        assertThat(conGai.primaryBranch().id()).isEqualTo(conTrai.primaryBranch().id());

        Person traiDaLuu = fx.persons.byId(vn.giapha.shared.vo.PersonId.of(conTrai.id())).orElseThrow();
        Person gaiDaLuu = fx.persons.byId(vn.giapha.shared.vo.PersonId.of(conGai.id())).orElseThrow();
        assertThat(traiDaLuu.birthOrder()).isEqualTo(1);
        assertThat(gaiDaLuu.birthOrder())
                .as("con gai KHONG bi bo qua khi danh thu tu sinh (BA v2 §12)")
                .isEqualTo(2);
        assertThat(fx.graph.nodeDaTao).contains(conGai.id());
    }

    @Test
    @DisplayName("Người đã khuất được thêm kèm ngày mất; ngày âm là nguồn chân lý tính giỗ")
    void nguoiDaKhuatDuocThemKemNgayMat() {
        LifeDate ngayMat = LifeDate.ofLunar(vn.giapha.shared.vo.LunarDate.of(1975, 3, 10));
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, "Cụ Tổ", true)),
                Gender.MALE, false, null, ngayMat, null, null, null, null, null,
                fx.chiGiap.id(), null, null, null, List.of(), false, false, null);

        PersonView view = fx.addPerson.add(cmd);

        assertThat(view.alive()).isFalse();
        assertThat(view.death().lunar().day()).isEqualTo(10);
        assertThat(view.death().lunar().month()).isEqualTo(3);
    }

    @Test
    @DisplayName("Hồ sơ đầy đủ được ghi trọn vẹn ngay ở lệnh tạo")
    void hoSoDayDuDuocGhiTronVenNgayOLenhTao() {
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Thị Lan", true)),
                Gender.FEMALE, true, PersonFixtures.sinhNam(1960), null,
                "Bắc Ninh", "Hà Nội", "số 12 phố Hàng Bột", "Giáo viên", "tiểu sử",
                fx.chiGiap.id(), new ContactInfo("0912345678", null, null),
                Map.of("hocVi", "Cử nhân"), null, List.of(), false, false, null);

        PersonView view = fx.addPerson.add(cmd);

        Person daLuu = fx.persons.byId(vn.giapha.shared.vo.PersonId.of(view.id())).orElseThrow();
        assertThat(daLuu.nativePlace()).isEqualTo("Bắc Ninh");
        assertThat(daLuu.currentPlaceProvince()).isEqualTo("Hà Nội");
        assertThat(daLuu.occupation()).isEqualTo("Giáo viên");
        assertThat(daLuu.contact().phone()).isEqualTo("0912345678");
        assertThat(daLuu.attributes()).containsEntry("hocVi", "Cử nhân");
    }

    @Test
    @DisplayName("Nhật ký của lệnh tạo không chứa dữ liệu Tầng 3")
    void nhatKyLenhTaoKhongChuaTang3() {
        AddPersonCommand cmd = new AddPersonCommand(
                List.of(PersonName.of(NameType.THUONG_GOI, "Nguyễn Thị Lan", true)),
                Gender.FEMALE, true, PersonFixtures.sinhNam(1960), null,
                null, null, "số 12 phố Hàng Bột", null, null,
                fx.chiGiap.id(), new ContactInfo("0912345678", "lan@example.com", null),
                null, null, List.of(), false, false, null);

        fx.addPerson.add(cmd);

        assertThat(String.valueOf(fx.audit.cuoiCung().after()))
                .doesNotContain("0912345678")
                .doesNotContain("lan@example.com")
                .doesNotContain("Hàng Bột");
    }
}

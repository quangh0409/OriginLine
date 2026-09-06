package vn.giapha.genealogy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.giapha.genealogy.application.view.PersonView;
import vn.giapha.genealogy.domain.LifeDate;
import vn.giapha.genealogy.domain.Person;
import vn.giapha.genealogy.domain.PersonFixtures;
import vn.giapha.shared.exception.DomainException;
import vn.giapha.shared.vo.LunarDate;
import vn.giapha.shared.vo.PersonId;

/**
 * <b>Báo mất qua {@code PATCH} — Phương án B: suy diễn.</b>
 *
 * <h2>Lỗi mà bộ test này chốt lại</h2>
 * Bản cũ của {@code UpdatePersonService.applyLifeStatus} rơi vào nhánh {@code markAlive()} khi
 * client gửi {@code {"death": …}} mà quên {@code "isAlive": false}: <b>ngày mất bị âm thầm vứt
 * đi</b>, API vẫn trả {@code 200 OK}, và nhật ký vẫn khai {@code changed = [isAlive, death]} — tức
 * là <b>audit_log nói dối</b> về một thay đổi chưa hề xảy ra. Nửa thứ hai mới là chỗ nguy hiểm: một
 * gia phả sống bằng khả năng truy vết ai sửa gì, mà nhật ký sai thì không còn gì để đối chiếu.
 *
 * <p>Hội đồng Tộc biểu chốt: <b>có ngày mất tức là đã mất</b>. Backend suy ra
 * {@code isAlive = false} rồi lưu bình thường; hộp thoại xác nhận ở giao diện lo phần rủi ro gõ
 * nhầm. Mâu thuẫn <i>tường minh</i> ({@code isAlive=true} kèm ngày mất) vẫn bị từ chối.</p>
 */
@DisplayName("Tình trạng sống/mất qua PATCH (Phương án B — suy diễn)")
class UpdatePersonLifeStatusTest {

    /** Ngày giỗ âm lịch — 15 tháng 8 năm Giáp Thìn. */
    private static final LifeDate NGAY_MAT = LifeDate.ofLunar(LunarDate.of(2024, 8, 15));
    private static final LifeDate NGAY_MAT_KHAC = LifeDate.ofLunar(LunarDate.of(2024, 9, 20));

    private GenealogyServiceFixture fx;
    private Person nguoi;

    @BeforeEach
    void dungHo() {
        fx = new GenealogyServiceFixture();
        fx.dangNhapAdmin();
        nguoi = fx.seed(PersonFixtures.nam("Nguyễn Văn Cả"), fx.chiGiap);
        nguoi.placeInGeneration(4);
        fx.persons.seed(nguoi);
    }

    @AfterEach
    void dongPhien() {
        fx.dangXuat();
    }

    private Person tuKho() {
        return fx.persons.byId(PersonId.of(nguoi.rawId())).orElseThrow();
    }

    // -----------------------------------------------------------------------------------------
    // Suy diễn
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("HỒI QUY — gửi ngày mất mà quên isAlive: suy ra đã mất, KHÔNG mất ngày mất")
    void guiNgayMatMaQuenIsAliveThiSuyRaDaMat() {
        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ngayMat(NGAY_MAT).build());

        assertThat(view.alive())
                .as("Phuong an B: co ngay mat tuc la da mat")
                .isFalse();
        assertThat(view.death())
                .as("ban cu vut ngay mat di roi van tra 200 OK - day chinh la cho mat du lieu")
                .isEqualTo(NGAY_MAT);
        assertThat(tuKho().death().lunar())
                .as("death_lunar la nguon chan ly de context events sinh nhac gio")
                .isEqualTo(LunarDate.of(2024, 8, 15));
    }

    @Test
    @DisplayName("HỒI QUY — suy diễn ghi đúng cả hai trường vào nhật ký, before/after khớp thực tế")
    void suyDienGhiDungNhatKy() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).ngayMat(NGAY_MAT).build());

        var ban = fx.audit.cuoiCung();
        assertThat(ban.changedFields()).containsExactlyInAnyOrder("isAlive", "death");
        assertThat(ban.before()).containsEntry("isAlive", true).containsEntry("deathYear", null);
        assertThat(ban.after()).containsEntry("isAlive", false).containsEntry("deathYear", 2024);
        assertThat(ban.note())
                .as("nhat ky phai noi ro viec chuyen sang 'da mat' la do HE THONG suy ra, "
                        + "khong phai do nguoi nhap lieu tuyen bo")
                .contains("Suy dien");
    }

    @Test
    @DisplayName("Suy diễn giữ nguyên ghi chú của người dùng, chỉ nối thêm phần giải thích")
    void suyDienGiuNguyenGhiChuCuaNguoiDung() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .ngayMat(NGAY_MAT).ghiChu("Theo bia mộ tại từ đường chi Giáp").build());

        assertThat(fx.audit.cuoiCung().note())
                .contains("Theo bia mộ tại từ đường chi Giáp")
                .contains("Suy dien");
    }

    @Test
    @DisplayName("Khai isAlive=false tường minh thì KHÔNG bị ghi là suy diễn")
    void khaiTuongMinhThiKhongPhaiSuyDien() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .conSong(false).ngayMat(NGAY_MAT).ghiChu("Gia đình báo").build());

        assertThat(fx.audit.cuoiCung().note()).isEqualTo("Gia đình báo");
    }

    @Test
    @DisplayName("Báo mất không kèm ngày (gia phả cổ chỉ biết đã mất) chỉ đổi mỗi isAlive")
    void baoMatKhongKemNgayChiDoiIsAlive() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).conSong(false).build());

        assertThat(tuKho().isAlive()).isFalse();
        assertThat(tuKho().death()).isNull();
        assertThat(fx.audit.cuoiCung().changedFields())
                .as("ngay mat von da null va van null - khai no 'da doi' la noi doi")
                .containsExactly("isAlive");
    }

    // -----------------------------------------------------------------------------------------
    // Mâu thuẫn tường minh
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Mâu thuẫn tường minh trong cùng một body")
    class MauThuanTuongMinh {

        @Test
        @DisplayName("isAlive=true kèm ngày mất bị từ chối VALIDATION_FAILED, không ghi gì")
        void vuaKhaiConSongVuaGuiNgayMatThiTuChoi() {
            int truoc = fx.persons.soLanSave;

            assertThatThrownBy(() -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(true).ngayMat(NGAY_MAT).build()))
                    .as("client noi ro HAI dieu trai nguoc nhau - khac han viec chi quen tick")
                    .isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo(GenealogyProblemCodes.VALIDATION_FAILED);

            assertThat(fx.persons.soLanSave).isEqualTo(truoc);
            assertThat(fx.audit.dong).isEmpty();
            assertThat(fx.treeCache.soLanEvictAll).isZero();
        }

        @Test
        @DisplayName("Mâu thuẫn nổ ra trước khi aggregate bị đụng vào — tên không bị thay dở dang")
        void mauThuanNoTruocKhiDungVaoAggregate() {
            assertThatThrownBy(() -> fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(true).ngayMat(NGAY_MAT).nguyenQuan("Bắc Ninh").build()))
                    .isInstanceOf(DomainException.class);

            assertThat(tuKho().nativePlace())
                    .as("khong duoc de lai mot ho so sua do dang khi lenh bi tu choi")
                    .isNull();
        }

        @Test
        @DisplayName("isAlive=true kèm clearFields:[death] là NHẤT QUÁN, không phải mâu thuẫn")
        void conSongKemXoaTrangNgayMatLaNhatQuan() {
            fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(false).ngayMat(NGAY_MAT).build());

            PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(true).xoaTrangNgayMat().build());

            assertThat(view.alive()).isTrue();
            assertThat(view.death()).isNull();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Chiều ngược lại và các ca biên
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Đính chính và các ca biên")
    class DinhChinhVaCaBien {

        @BeforeEach
        void baoMatTruoc() {
            fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(false).ngayMat(NGAY_MAT).build());
            fx.audit.dong.clear();
        }

        @Test
        @DisplayName("isAlive=true trên người đã mất vẫn gỡ ngày mất (ck_person_alive_vs_death)")
        void dinhChinhConSongThiGoNgayMat() {
            PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(true).build());

            assertThat(view.alive()).isTrue();
            assertThat(view.death()).isNull();
            assertThat(fx.audit.cuoiCung().changedFields())
                    .containsExactlyInAnyOrder("isAlive", "death");
        }

        @Test
        @DisplayName("clearFields:[death] trên người đã mất KHÔNG hồi sinh họ, chỉ gỡ ngày giỗ")
        void xoaTrangNgayMatKhongHoiSinhNguoiDaMat() {
            PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .xoaTrangNgayMat().build());

            assertThat(view.alive())
                    .as("gia pha co day nguoi chi biet da mat ma khong con ngay gio nao; "
                            + "go ngay ghi sai khong phai la lam cho cu song lai")
                    .isFalse();
            assertThat(view.death()).isNull();
            assertThat(fx.audit.cuoiCung().changedFields()).containsExactly("death");
        }

        @Test
        @DisplayName("Sửa ngày giỗ của người vốn đã mất chỉ ghi 'death', không ghi 'isAlive'")
        void suaNgayGioNguoiVonDaMatChiGhiDeath() {
            PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .ngayMat(NGAY_MAT_KHAC).build());

            assertThat(view.death()).isEqualTo(NGAY_MAT_KHAC);
            assertThat(fx.audit.cuoiCung().changedFields())
                    .as("nguoi nay von da mat - khai 'isAlive da doi' la noi doi")
                    .containsExactly("death");
            assertThat(fx.audit.cuoiCung().note())
                    .as("khong phai suy dien: khong co buoc chuyen tinh trang nao ca")
                    .isNull();
        }

        @Test
        @DisplayName("Gửi lại đúng ngày mất cũ là thao tác rỗng: không ghi, không nhật ký, không dọn cache")
        void guiLaiDungNgayMatCuLaThaoTacRong() {
            int truoc = fx.persons.soLanSave;
            int evictTruoc = fx.treeCache.soLanEvictAll;

            fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                    .conSong(false).ngayMat(NGAY_MAT).build());

            assertThat(fx.persons.soLanSave).isEqualTo(truoc);
            assertThat(fx.audit.dong).isEmpty();
            assertThat(fx.treeCache.soLanEvictAll).isEqualTo(evictTruoc);
        }
    }

    @Test
    @DisplayName("isAlive=true trên người vốn đang sống là thao tác rỗng, không sinh dòng nhật ký ma")
    void conSongTrenNguoiVonDangSongLaThaoTacRong() {
        int truoc = fx.persons.soLanSave;

        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).conSong(true).build());

        assertThat(fx.persons.soLanSave).isEqualTo(truoc);
        assertThat(fx.audit.dong).isEmpty();
    }

    @Test
    @DisplayName("clearFields:[death] trên người đang sống là thao tác rỗng")
    void xoaTrangNgayMatTrenNguoiDangSongLaThaoTacRong() {
        int truoc = fx.persons.soLanSave;

        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).xoaTrangNgayMat().build());

        assertThat(tuKho().isAlive()).isTrue();
        assertThat(fx.persons.soLanSave).isEqualTo(truoc);
        assertThat(fx.audit.dong).isEmpty();
    }

    // -----------------------------------------------------------------------------------------
    // Hệ quả dây chuyền — BA v2 §10
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("HỆ QUẢ — suy diễn ra 'đã mất' chuyển hồ sơ sang tầng PUBLIC, Khách thấy được")
    void suyDienDaMatThiChuyenSangTangPublic() {
        fx.dangXuat();
        fx.dangNhapThanhVien(java.util.UUID.randomUUID(), GenealogyServiceFixture.P_CHI_AT);
        assertThat(fx.query.find(nguoi.rawId()).orElseThrow().access().visibleTier())
                .as("nguoi con song, khac chi, mac dinh chi den Tang 1")
                .isEqualTo(VisibleTier.T1);
        fx.dangXuat();
        fx.dangNhapAdmin();

        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).ngayMat(NGAY_MAT).build());

        fx.dangXuat();
        assertThat(fx.query.find(nguoi.rawId()))
                .as("Khach khong thay NGUOI SONG nao; nguoi da khuat thi cong khai (BA v2 §10). "
                        + "Day chinh la he qua nang ne cua Phuong an B - doi hoi hop thoai xac nhan "
                        + "o giao dien truoc khi gui.")
                .isPresent();
        assertThat(fx.query.find(nguoi.rawId()).orElseThrow().access().visibleTier())
                .isEqualTo(VisibleTier.PUBLIC);
    }

    @Test
    @DisplayName("HỆ QUẢ — đính chính 'thật ra còn sống' kéo hồ sơ khỏi tầm mắt Khách trở lại")
    void dinhChinhConSongThiKhachKhongThayNua() {
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).ngayMat(NGAY_MAT).build());
        fx.dangXuat();
        assertThat(fx.query.find(nguoi.rawId())).isPresent();

        fx.dangNhapAdmin();
        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).conSong(true).build());
        fx.dangXuat();

        assertThat(fx.query.find(nguoi.rawId()))
                .as("dinh chinh phai dong lai canh cua rieng tu vua mo ra, khong chi doi mot co")
                .isEmpty();
    }

    @Test
    @DisplayName("HỆ QUẢ — suy diễn phát PersonUpdatedEvent đúng hai trường isAlive + death")
    void suyDienPhatSuKienDungHaiTruong() {
        fx.suKien.clear();

        fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId()).ngayMat(NGAY_MAT).build());

        assertThat(fx.suKien)
                .as("consumer nhac gio cua context events bam vao su kien nay; liet ke sai truong "
                        + "thi ho hoac bo qua mot cai gio hoac sinh nhac cho nguoi con song")
                .singleElement()
                .isInstanceOfSatisfying(vn.giapha.genealogy.domain.event.PersonUpdatedEvent.class,
                        event -> assertThat(event.changedFields())
                                .containsExactlyInAnyOrder("isAlive", "death"));
    }

    @Test
    @DisplayName("Suy diễn vẫn tôn trọng khoá lạc quan và phiên bản trả về là bản đã ghi")
    void suyDienVanTonTrongKhoaLacQuan() {
        long phienBanTruoc = fx.persons.versionOf(nguoi.rawId());

        PersonView view = fx.updatePerson.update(UpdatePersonCommands.cho(nguoi.rawId())
                .phienBan(phienBanTruoc).ngayMat(NGAY_MAT).build());

        assertThat(view.version()).isEqualTo(fx.persons.versionOf(nguoi.rawId()));
        assertThat(List.of(view.alive())).containsExactly(false);
    }
}

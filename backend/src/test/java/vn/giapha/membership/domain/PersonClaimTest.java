package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.shared.vo.Gender;

/**
 * Bất biến của một lá đơn tự nhận.
 *
 * <p>Nặng nhất là <b>ràng buộc 1 của design 07 §1.5</b>: đơn chỉ là đơn, nó không tạo nhân khẩu.
 * Ở tầng domain điều đó phát biểu thành "{@code createdPersonId} chỉ được ghi trong
 * {@link PersonClaim#approve}", và {@code ck_person_claim_created} của V16 canh cùng một luật ở
 * CSDL.</p>
 */
@DisplayName("Đơn tự nhận mình trong phả")
class PersonClaimTest {

    private static final Instant NOW = Instant.parse("2026-03-14T01:00:00Z");

    private static final UUID NGUOI_GUI = UUID.randomUUID();
    private static final UUID NGUOI_DUYET = UUID.randomUUID();
    private static final UUID NHAN_KHAU = UUID.randomUUID();
    private static final UUID NGUOI_THAN = UUID.randomUUID();
    private static final UUID CHI = UUID.randomUUID();

    private PersonClaim donNhanMinh() {
        return PersonClaim.nhanMinh(UUID.randomUUID(), NGUOI_GUI, NHAN_KHAU, CHI, "0912345678",
                "Chau la con thu hai cua ong Bon");
    }

    private PersonClaim donChuaCoTrongPha() {
        return PersonClaim.chuaCoTrongPha(UUID.randomUUID(), NGUOI_GUI, "Trần Thị Mai", 1998,
                Gender.FEMALE, NGUOI_THAN, RelativeKind.SPOUSE, CHI, "0912345678",
                "Chau la con dau moi", List.of());
    }

    // -------------------------------------------------------------------------------------
    // Hình dạng hai loại đơn
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("đơn nhận mình phải trỏ tới một nhân khẩu có sẵn")
    void donNhanMinhPhaiTroToiNhanKhau() {
        assertThatThrownBy(() -> PersonClaim.nhanMinh(UUID.randomUUID(), NGUOI_GUI, null, CHI,
                "0912345678", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nhan khau co san");
    }

    @Test
    @DisplayName("RÀNG BUỘC 2: đơn chưa-có-trong-phả phải chỉ ra người thân đã có")
    void donChuaCoTrongPhaPhaiCoNguoiThan() {
        assertThatThrownBy(() -> PersonClaim.chuaCoTrongPha(UUID.randomUUID(), NGUOI_GUI,
                "Trần Thị Mai", 1998, Gender.FEMALE, null, null, CHI, "0912345678", null,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                // Khong co nguoi than thi nhan khau moi thanh node mo coi: khong tinh duoc doi,
                // khong tra duoc danh xung, va khong biet AI DUYET.
                .hasMessageContaining("nguoi than");
    }

    @Test
    @DisplayName("RÀNG BUỘC 1: đơn chưa-có-trong-phả KHÔNG được trỏ tới nhân khẩu nào lúc gửi")
    void donChuaCoTrongPhaKhongTroToiNhanKhauNao() {
        assertThatThrownBy(() -> new PersonClaim(UUID.randomUUID(), PersonClaimKind.NEW_PERSON,
                NGUOI_GUI, NHAN_KHAU, NGUOI_THAN, RelativeKind.FATHER, "Trần Thị Mai", null, null,
                CHI, "0912345678", null, List.of(), PersonClaimStatus.PENDING, null, null, null,
                null, NOW, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rang buoc 1");
    }

    @Test
    @DisplayName("đơn phải kèm số điện thoại — không gọi kiểm chứng được thì không đối chiếu được")
    void donPhaiKemSoDienThoai() {
        assertThatThrownBy(() -> PersonClaim.nhanMinh(UUID.randomUUID(), NGUOI_GUI, NHAN_KHAU, CHI,
                "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("so dien thoai");
    }

    // -------------------------------------------------------------------------------------
    // Máy trạng thái
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("duyệt đơn nhận mình KHÔNG sinh nhân khẩu nào")
    void duyetDonNhanMinhKhongSinhNhanKhau() {
        PersonClaim don = donNhanMinh();
        don.approve(NGUOI_DUYET, "Da goi kiem chung", NOW, null);

        assertThat(don.status()).isEqualTo(PersonClaimStatus.APPROVED);
        assertThat(don.createdPersonId()).isNull();
        assertThat(don.resolvedPersonId()).isEqualTo(NHAN_KHAU);
    }

    @Test
    @DisplayName("duyệt đơn nhận mình mà kèm nhân khẩu mới là một lỗi lập trình, không phải tuỳ chọn")
    void donNhanMinhKhongNhanNhanKhauMoi() {
        assertThatThrownBy(() -> donNhanMinh().approve(NGUOI_DUYET, null, NOW, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duyệt đơn chưa-có-trong-phả BẮT BUỘC kèm nhân khẩu vừa tạo")
    void duyetDonChuaCoTrongPhaPhaiKemNhanKhau() {
        assertThatThrownBy(() -> donChuaCoTrongPha().approve(NGUOI_DUYET, null, NOW, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nhan khau vua tao");
    }

    @Test
    @DisplayName("duyệt đơn chưa-có-trong-phả ghi lại đúng nhân khẩu vừa tạo")
    void duyetDonChuaCoTrongPhaGhiNhanKhauMoi() {
        PersonClaim don = donChuaCoTrongPha();
        UUID nguoiMoi = UUID.randomUUID();
        don.approve(NGUOI_DUYET, "Da doi chieu so giay", NOW, nguoiMoi);

        assertThat(don.createdPersonId()).isEqualTo(nguoiMoi);
        assertThat(don.resolvedPersonId()).isEqualTo(nguoiMoi);
    }

    @Test
    @DisplayName("từ chối BẮT BUỘC kèm lý do — người gửi có quyền biết vì sao")
    void tuChoiPhaiKemLyDo() {
        assertThatThrownBy(() -> donNhanMinh().reject(NGUOI_DUYET, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ly do");
    }

    @Test
    @DisplayName("từ chối KHÔNG để lại nhân khẩu nào — bằng chứng của ràng buộc 1")
    void tuChoiKhongDeLaiNhanKhau() {
        PersonClaim don = donChuaCoTrongPha();
        don.reject(NGUOI_DUYET, "Khong doi chieu duoc voi so giay", NOW);

        assertThat(don.status()).isEqualTo(PersonClaimStatus.REJECTED);
        assertThat(don.createdPersonId()).isNull();
        assertThat(don.resolvedPersonId()).isNull();
    }

    @Test
    @DisplayName("KHÔNG ai được tự duyệt đơn của chính mình")
    void khongTuDuyetDonCuaMinh() {
        assertThatThrownBy(() -> donNhanMinh().approve(NGUOI_GUI, "Toi tu duyet", NOW, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tu duyet");
        // Va tu choi cung the: mot nguoi tu dong don cua minh phai dung loi RUT, de nhat ky noi
        // dung chuyen gi da xay ra.
        assertThatThrownBy(() -> donNhanMinh().reject(NGUOI_GUI, "Toi tu tu choi", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("đơn đã đóng thì không xử lại được")
    void donDaDongKhongXuLai() {
        PersonClaim don = donNhanMinh();
        don.approve(NGUOI_DUYET, null, NOW, null);
        assertThatThrownBy(() -> don.reject(NGUOI_DUYET, "Doi y", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trang thai cuoi");
    }

    @Test
    @DisplayName("chỉ người gửi mới rút được đơn của mình, và rút thì không cần người thứ hai")
    void chiNguoiGuiMoiRutDuoc() {
        PersonClaim don = donNhanMinh();
        assertThatThrownBy(() -> don.cancel(NGUOI_DUYET, NOW))
                .isInstanceOf(IllegalStateException.class);

        don.cancel(NGUOI_GUI, NOW);
        assertThat(don.status()).isEqualTo(PersonClaimStatus.CANCELLED);
    }

    // -------------------------------------------------------------------------------------
    // Nhật ký
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("ảnh chụp kiểm toán KHÔNG mang số điện thoại lẫn lời tự giới thiệu")
    void anhChupKhongMangDuLieuCaNhan() {
        PersonClaim don = donNhanMinh();
        assertThat(don.auditSnapshot())
                // audit_log la bang CHI GHI THEM: mot so dien thoai lot vao do la lot vinh vien.
                .doesNotContainKeys("phone", "introduction")
                .containsKeys("id", "kind", "status", "requestedBy", "targetBranchId");
        assertThat(don.auditSnapshot().values().stream().map(String::valueOf))
                .noneMatch(v -> v.contains("0912345678"));
    }
}

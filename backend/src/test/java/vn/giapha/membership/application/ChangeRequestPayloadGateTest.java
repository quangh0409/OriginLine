package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.application.command.SubmitChangeRequestCommand;
import vn.giapha.membership.domain.ChangeRequestType;
import vn.giapha.membership.domain.event.CorrectionPayload;
import vn.giapha.membership.support.ClanFixture;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.shared.exception.DomainException;

/**
 * Cửa kiểm nội dung đề nghị đính chính, đặt ở bước <b>GỬI</b>.
 *
 * <h2>Vì sao kiểm lúc gửi chứ không lúc duyệt</h2>
 * Người gửi là thành viên dùng ứng dụng thưa, mở lên vì vừa nhận ra một chỗ ghi sai. Nếu sai khoá
 * mà phải chờ tới lúc Trưởng chi bấm Duyệt mới lộ ra thì: người gửi biết mình gõ nhầm sau một tuần,
 * khi đã quên gõ gì; còn Trưởng chi nhận một đề nghị không dùng được và không có cách nào sửa hộ.
 *
 * <h2>Và vì sao mốc phiên bản được đóng dấu ngay lúc gửi</h2>
 * Một đề nghị chờ hàng tuần rồi mới áp dụng. Không có mốc thì lệnh ghi lúc duyệt là một cú
 * <b>ghi đè mù</b> lên mọi thay đổi đã xảy ra trong lúc chờ.
 */
@DisplayName("Cửa kiểm nội dung đề nghị đính chính (lúc gửi)")
class ChangeRequestPayloadGateTest {

    private final ClanFixture clan = new ClanFixture();
    private final ChangeRequestService service = new ChangeRequestService(
            clan.requests, clan.scopes, clan.guard, clan.branches, clan.auditTrail, clan.events);

    private UUID nguoiChiGiap;

    @BeforeEach
    void dangNhapThanhVien() {
        clan.account("sub-nguoi-gui", clan.chiGiapId);
        TestSecurity.loginAs("sub-nguoi-gui", "MEMBER");
        nguoiChiGiap = clan.personIn(clan.chiGiapId);
    }

    @AfterEach
    void donSecurityContext() {
        TestSecurity.logout();
    }

    private ChangeRequestView gui(Map<String, Object> payload) {
        return service.submit(new SubmitChangeRequestCommand(ChangeRequestType.UPDATE_PERSON,
                nguoiChiGiap, null, payload, "Ngày giỗ cụ ghi sai"));
    }

    private static Map<String, Object> ngayGio() {
        return Map.of("death", Map.of(
                "lunar", Map.of("year", 1975, "month", 7, "day", 15, "leap", false),
                "precision", "DAY"));
    }

    // -------------------------------------------------------------------------------------
    // Hợp đồng đóng
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Khoá ngoài danh mục bị từ chối ngay, KHÔNG lưu vào hàng đợi")
    void khoaNgoaiDanhMucBiTuChoiNgay() {
        assertThatThrownBy(() -> gui(Map.of("deathLunar", "15/07")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);

        assertThat(clan.requests.countPendingInScope(java.util.List.of(), true))
                .as("de nghi hong khong duoc nam trong hang doi cho Truong chi doc")
                .isZero();
    }

    @Test
    @DisplayName("Thông điệp lỗi nêu đúng danh mục trường, để người gửi sửa được ngay")
    void thongDiepLoiNeuDanhMuc() {
        assertThatThrownBy(() -> gui(Map.of("tenHuy", "Nguyễn Văn Cả")))
                .hasMessageContaining("nativePlace")
                .hasMessageContaining("OTHER");
    }

    @Test
    @DisplayName("gender = OTHER bị chặn ở cửa gửi, không để chết ở ck_person_gender")
    void gioiTinhOtherBiChanSom() {
        assertThatThrownBy(() -> gui(Map.of("gender", "OTHER")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Loại OTHER không bị siết — đó là lời mô tả cho Trưởng chi đọc")
    void loaiOtherKhongBiSiet() {
        ChangeRequestView view = service.submit(new SubmitChangeRequestCommand(
                ChangeRequestType.OTHER, nguoiChiGiap, null,
                Map.of("moTa", "Xin đổi quan hệ cha–con của cụ"), "Chi tiết trong lời nhắn"));

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.payload()).doesNotContainKey(CorrectionPayload.BASE_VERSION_KEY);
    }

    // -------------------------------------------------------------------------------------
    // Mốc phiên bản
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Backend tự đóng dấu _baseVersion từ person.version lúc gửi")
    void tuDongDauMocPhienBan() {
        clan.branches.personVersion(nguoiChiGiap, 4L);

        ChangeRequestView view = gui(ngayGio());

        assertThat(CorrectionPayload.baseVersion(view.payload()))
                .as("moc chup LUC GUI - moi thay doi trong luc cho duyet se lam no lech")
                .isEqualTo(4L);
    }

    @Test
    @DisplayName("Mốc do người gửi kèm theo thắng mốc backend tự đọc")
    void mocCuaNguoiGuiThang() {
        // Con so client gui la phien ban ho THAT SU nhin thay tren man hinh (ETag cua lan GET);
        // con so backend doc chi la xap xi tai thoi diem bam Gui.
        clan.branches.personVersion(nguoiChiGiap, 9L);

        ChangeRequestView view = gui(Map.of("nativePlace", "Bắc Ninh",
                CorrectionPayload.BASE_VERSION_KEY, 2));

        assertThat(CorrectionPayload.baseVersion(view.payload())).isEqualTo(2L);
    }

    @Test
    @DisplayName("_baseVersion không lọt vào payloadFields của giao diện")
    void mocKhongLotVaoDanhSachTruong() {
        ChangeRequestView view = gui(ngayGio());

        assertThat(view.payloadFields()).containsExactly("death");
        assertThat(view.payload()).containsKey(CorrectionPayload.BASE_VERSION_KEY);
    }

    @Test
    @DisplayName("Không đọc được phiên bản và người gửi cũng không kèm -> từ chối, không bỏ trống")
    void khongCoMocThiTuChoi() {
        // Bo trong moc phien ban la mo lai dung lo hong ghi de mu ma ca bo nay sinh ra de bit.
        UUID nguoiLa = UUID.randomUUID();

        assertThatThrownBy(() -> service.submit(new SubmitChangeRequestCommand(
                ChangeRequestType.UPDATE_PERSON, nguoiLa, clan.chiGiapId,
                Map.of("nativePlace", "Bắc Ninh"), "sửa")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(CorrectionPayload.BASE_VERSION_KEY);
    }

    @Test
    @DisplayName("Đề nghị XOÁ một trường (giá trị null) vẫn đóng dấu được mốc phiên bản")
    void deNghiXoaTruongVanDongDauDuoc() {
        // Map.copyOf nem NPE khi map chua null - va "bo ngay mat ghi nham" chinh la mot gia tri null.
        Map<String, Object> xoaNgayMat = new LinkedHashMap<>();
        xoaNgayMat.put("death", null);
        clan.branches.personVersion(nguoiChiGiap, 1L);

        ChangeRequestView view = gui(xoaNgayMat);

        assertThat(view.payload()).containsEntry("death", null);
        assertThat(CorrectionPayload.baseVersion(view.payload())).isEqualTo(1L);
    }
}

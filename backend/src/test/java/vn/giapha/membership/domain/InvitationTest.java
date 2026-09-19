package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bất biến của một lời mời: dùng một lần · có hạn · thu hồi được. */
@DisplayName("Lời mời")
class InvitationTest {

    private static final Instant NOW = Instant.parse("2026-03-14T01:00:00Z");

    private Invitation loiMoi() {
        return Invitation.issue(UUID.randomUUID(), hash("a"), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), NOW.plus(Duration.ofDays(7)), null);
    }

    private static String hash(String seed) {
        return InvitationCode.sha256Hex(seed);
    }

    @Test
    @DisplayName("mới phát thì dùng được")
    void moiPhatThiDungDuoc() {
        assertThat(loiMoi().usabilityAt(NOW)).isEqualTo(InvitationUsability.USABLE);
    }

    @Test
    @DisplayName("quá hạn thì hết dùng được — hết hạn là phép so sánh, không phải một trạng thái lưu trữ")
    void quaHanThiHetDungDuoc() {
        Invitation invitation = loiMoi();
        // Trang thai luu tru van la PENDING: khong co job nao di lat co, va do la chu y.
        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.usabilityAt(NOW.plus(Duration.ofDays(8))))
                .isEqualTo(InvitationUsability.EXPIRED);
    }

    @Test
    @DisplayName("đúng giây hết hạn đã là hết hạn")
    void dungGiayHetHanDaLaHetHan() {
        Invitation invitation = loiMoi();
        assertThat(invitation.usabilityAt(invitation.expiresAt()))
                .isEqualTo(InvitationUsability.EXPIRED);
        assertThat(invitation.usabilityAt(invitation.expiresAt().minusMillis(1)))
                .isEqualTo(InvitationUsability.USABLE);
    }

    @Test
    @DisplayName("nhận xong thì mã chết — dùng lại lần hai ném ngay ở domain")
    void nhanXongThiMaChet() {
        Invitation invitation = loiMoi();
        UUID nguoiNhan = UUID.randomUUID();
        invitation.accept(nguoiNhan, NOW);

        assertThat(invitation.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.acceptedBy()).isEqualTo(nguoiNhan);
        assertThat(invitation.acceptedAt()).isEqualTo(NOW);
        assertThat(invitation.usabilityAt(NOW)).isEqualTo(InvitationUsability.ALREADY_USED);

        // Bat bien "mot lan" nam o domain, khong chi o tang tren: neu chi tang tren canh thi no se
        // mat ngay khi co loi goi thu hai.
        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALREADY_USED");
    }

    @Test
    @DisplayName("mã quá hạn không nhận được, kể cả khi gọi thẳng domain")
    void maQuaHanKhongNhanDuoc() {
        Invitation invitation = loiMoi();
        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), NOW.plus(Duration.ofDays(8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRED");
    }

    @Test
    @DisplayName("thu hồi rồi thì không nhận được nữa — nền của nút \"Không phải tôi\"")
    void thuHoiRoiThiKhongNhanDuoc() {
        Invitation invitation = loiMoi();
        invitation.revoke("Nguoi nhan bam: Khong phai toi", NOW);

        assertThat(invitation.status()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(invitation.revokedAt()).isEqualTo(NOW);
        assertThat(invitation.usabilityAt(NOW)).isEqualTo(InvitationUsability.REVOKED);
        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("thu hồi một mã đã quá hạn là vô hại — Trưởng chi dọn danh sách")
    void thuHoiMaQuaHanLaVoHai() {
        Invitation invitation = loiMoi();
        invitation.revoke("Don danh sach", NOW.plus(Duration.ofDays(9)));
        assertThat(invitation.status()).isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    @DisplayName("đã nhận rồi thì KHÔNG thu hồi được — gỡ mối gắn là một nghiệp vụ khác hẳn")
    void daNhanRoiThiKhongThuHoiDuoc() {
        Invitation invitation = loiMoi();
        invitation.accept(UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> invitation.revoke("doi y", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("da duoc nhan");
    }

    @Test
    @DisplayName("băm sai độ dài bị từ chối ngay ở constructor")
    void bamSaiDoDaiBiTuChoi() {
        UUID id = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        Instant expires = NOW.plus(Duration.ofDays(7));
        assertThatThrownBy(() -> Invitation.issue(id, "khong-phai-sha256", person, null,
                inviter, expires, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");
    }

    @Test
    @DisplayName("lời mời KHÔNG trỏ vào nhân khẩu nào là không dựng được")
    void loiMoiPhaiTroVaoNhanKhau() {
        // Day la diem mau chot cua ca luong: loi moi khong mang san person_id se tao ra dung cai
        // hang doi "cho duyet" ma thiet ke nay sinh ra de xoa.
        UUID id = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        Instant expires = NOW.plus(Duration.ofDays(7));
        assertThatThrownBy(() -> Invitation.issue(id, hash("a"), null, null, inviter, expires, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("personId");
    }
}

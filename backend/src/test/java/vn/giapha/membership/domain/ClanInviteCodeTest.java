package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bất biến của một mã mời dòng họ: <b>có hạn · thu hồi được · đếm lượt dùng</b>.
 *
 * <p>Ba trong bốn chốt của design 07 §1.2 nằm ở đây; chốt thứ tư (giới hạn tần suất) là việc của
 * {@code InviteThrottle}.</p>
 */
@DisplayName("Mã mời dòng họ")
class ClanInviteCodeTest {

    private static final Instant NOW = Instant.parse("2026-03-14T01:00:00Z");

    private ClanInviteCode ma(Integer maxUses, int useCount) {
        return new ClanInviteCode(UUID.randomUUID(), InvitationCode.sha256Hex("a"),
                "Nhóm Zalo họ Nguyễn", UUID.randomUUID(), ClanInviteStatus.ACTIVE,
                NOW.plus(Duration.ofDays(30)), maxUses, useCount, null, null, null, NOW, 0L);
    }

    private ClanInviteCode maMoiPhat() {
        return ClanInviteCode.issue(UUID.randomUUID(), InvitationCode.sha256Hex("a"), "Zalo",
                UUID.randomUUID(), NOW.plus(Duration.ofDays(30)), null, null);
    }

    // -------------------------------------------------------------------------------------
    // CHỐT 1 — có hạn dùng
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("mới phát thì dùng được, và bộ đếm bắt đầu từ 0")
    void moiPhatThiDungDuoc() {
        ClanInviteCode ma = maMoiPhat();
        assertThat(ma.usabilityAt(NOW)).isEqualTo(ClanInviteUsability.USABLE);
        assertThat(ma.useCount()).isZero();
        assertThat(ma.remainingUses()).as("khong dat tran thi khong co so luot con lai").isNull();
    }

    @Test
    @DisplayName("quá hạn thì hết dùng được — hết hạn là phép so sánh, KHÔNG phải trạng thái lưu trữ")
    void quaHanThiHetDungDuoc() {
        ClanInviteCode ma = maMoiPhat();
        // Trang thai luu tru van la ACTIVE: khong co job nao di lat co, va do la chu y — mot
        // EXPIRED luu tru se tao ra mot lo hong CO LICH CHAY, vi moi ma qua han van dung duoc cho
        // toi khi job ay chay.
        assertThat(ma.status()).isEqualTo(ClanInviteStatus.ACTIVE);
        assertThat(ma.usabilityAt(NOW.plus(Duration.ofDays(31))))
                .isEqualTo(ClanInviteUsability.EXPIRED);
    }

    @Test
    @DisplayName("đúng giây hết hạn đã là hết hạn")
    void dungGiayHetHanDaLaHetHan() {
        assertThat(maMoiPhat().usabilityAt(NOW.plus(Duration.ofDays(30))))
                .isEqualTo(ClanInviteUsability.EXPIRED);
    }

    @Test
    @DisplayName("KHÔNG dựng được mã không hạn — một mã không hạn là một mã vĩnh viễn")
    void khongCoMaVoHan() {
        assertThatThrownBy(() -> ClanInviteCode.issue(UUID.randomUUID(),
                InvitationCode.sha256Hex("a"), null, UUID.randomUUID(), null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt");
    }

    // -------------------------------------------------------------------------------------
    // CHỐT 2 — thu hồi được
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("thu hồi đóng cửa ngay, và bộ đếm GIỮ NGUYÊN con số cũ")
    void thuHoiGiuLaiBoDem() {
        ClanInviteCode ma = ma(null, 37);
        ma.revoke("Ma bi dan len nhom Zalo cong khai", NOW);

        assertThat(ma.usabilityAt(NOW)).isEqualTo(ClanInviteUsability.REVOKED);
        assertThat(ma.revokedReason()).isEqualTo("Ma bi dan len nhom Zalo cong khai");
        // Chinh con so nay la thu dang giu sau khi thu hoi: no la bang chung ma da ro toi dau.
        assertThat(ma.useCount()).isEqualTo(37);
    }

    @Test
    @DisplayName("thu hồi một mã ĐÃ QUÁ HẠN là hợp lệ — đó là việc dọn danh sách")
    void thuHoiMaQuaHanLaHopLe() {
        ClanInviteCode ma = maMoiPhat();
        Instant sauKhiHetHan = NOW.plus(Duration.ofDays(40));
        ma.revoke("Don danh sach", sauKhiHetHan);
        assertThat(ma.status()).isEqualTo(ClanInviteStatus.REVOKED);
    }

    @Test
    @DisplayName("thu hồi thắng hết hạn khi xét lý do — nó là hành động của Hội đồng")
    void thuHoiThangHetHan() {
        ClanInviteCode ma = maMoiPhat();
        ma.revoke("Thu hoi", NOW);
        // Ma vua bi thu hoi vua qua han: ly do dang noi voi nguoi dung la THU HOI, vi no dan toi
        // mot cau hoi khac han ("vi sao Hoi dong dong ma nay?").
        assertThat(ma.usabilityAt(NOW.plus(Duration.ofDays(40))))
                .isEqualTo(ClanInviteUsability.REVOKED);
    }

    // -------------------------------------------------------------------------------------
    // CHỐT 3 — đếm lượt dùng, và trần lượt
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("chạm trần thì hết dùng được, và phân biệt được với hết hạn")
    void chamTranThiHetDungDuoc() {
        assertThat(ma(3, 3).usabilityAt(NOW)).isEqualTo(ClanInviteUsability.EXHAUSTED);
        assertThat(ma(3, 2).usabilityAt(NOW)).isEqualTo(ClanInviteUsability.USABLE);
        assertThat(ma(3, 2).remainingUses()).isEqualTo(1);
    }

    @Test
    @DisplayName("vượt trần (dữ liệu cũ, trần bị hạ sau) vẫn ra EXHAUSTED, không ra số âm")
    void vuotTranVanRaExhausted() {
        ClanInviteCode ma = ma(3, 9);
        assertThat(ma.usabilityAt(NOW)).isEqualTo(ClanInviteUsability.EXHAUSTED);
        assertThat(ma.remainingUses()).isZero();
    }

    @Test
    @DisplayName("không đặt trần thì bộ đếm vẫn chạy — đếm mới là thứ đáng giá, chặn chỉ là hệ quả")
    void khongDatTranVanDem() {
        ClanInviteCode ma = ma(null, 400);
        assertThat(ma.usabilityAt(NOW)).isEqualTo(ClanInviteUsability.USABLE);
        // 400 luot tren mot dong ho 600 nguoi: he thong khong chan, nhung Hoi dong NHIN THAY.
        assertThat(ma.useCount()).isEqualTo(400);
    }

    // -------------------------------------------------------------------------------------
    // Bí mật
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("chỉ nhận băm SHA-256 hex 64 ký tự — mã thô không lọt vào đối tượng này được")
    void chiNhanBam() {
        assertThatThrownBy(() -> ClanInviteCode.issue(UUID.randomUUID(), "K7M2Q-D9HFX", null,
                UUID.randomUUID(), NOW.plus(Duration.ofDays(1)), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");
    }
}

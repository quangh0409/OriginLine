package vn.giapha.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.domain.port.IdentityAccount;
import vn.giapha.membership.domain.port.IdentityProviderException;
import vn.giapha.membership.domain.port.SetPasswordLink;
import vn.giapha.membership.domain.port.SetPasswordNotAllowedException;
import vn.giapha.membership.support.FakeIdentityProvider;
import vn.giapha.shared.exception.DomainException;

/**
 * Đuôi của luồng mời: người được mời bấm liên kết một lần và tự đặt mật khẩu.
 *
 * <p>Ca quan trọng nhất ở đây là <b>dùng lại lần hai</b>. Tính "một lần" của liên kết không được
 * canh bằng một cờ trong CSDL mà bằng chính trạng thái ở nhà cung cấp danh tính — "tài khoản này đã
 * có mật khẩu chưa". Một cờ song song có thể sót lại và biến liên kết thành lối đổi mật khẩu của
 * người khác; trạng thái thật thì không sót được.</p>
 */
@DisplayName("Đặt mật khẩu qua liên kết một lần")
class SetPasswordServiceTest {

    private FakeIdentityProvider identityProvider;
    private SetPasswordService setPasswords;

    @BeforeEach
    void dung() {
        identityProvider = new FakeIdentityProvider();
        setPasswords = new SetPasswordService(identityProvider);
    }

    private String lienKetChoTaiKhoanMoi() {
        IdentityAccount account = identityProvider.seed("ba.lan@example.com", false);
        SetPasswordLink link = identityProvider.issueSetPasswordLink(account).orElseThrow();
        return link.url().substring(link.url().indexOf("token=") + "token=".length());
    }

    @Test
    @DisplayName("đặt được mật khẩu, và sau đó tài khoản có mật khẩu thật")
    void datDuocMatKhau() {
        String token = lienKetChoTaiKhoanMoi();

        setPasswords.setPassword(token, "mat-khau-cua-rieng-toi");

        assertThat(identityProvider.findByEmail("ba.lan@example.com").orElseThrow().hasPassword())
                .isTrue();
    }

    @Test
    @DisplayName("dùng lại liên kết lần hai THẤT BẠI — một lần là một lần")
    void dungLaiLanHaiThatBai() {
        String token = lienKetChoTaiKhoanMoi();
        setPasswords.setPassword(token, "mat-khau-cua-rieng-toi");

        assertThatThrownBy(() -> setPasswords.setPassword(token, "mat-khau-cua-ke-khac"))
                .isInstanceOf(SetPasswordNotAllowedException.class);
    }

    @Test
    @DisplayName("token không khớp liên kết nào thì từ chối")
    void tokenLaThiTuChoi() {
        identityProvider.seed("ba.lan@example.com", false);

        assertThatThrownBy(() -> setPasswords.setPassword("token-bia-ra", "mat-khau-nao-do"))
                .isInstanceOf(SetPasswordNotAllowedException.class);
    }

    @Test
    @DisplayName("mật khẩu rỗng bị chặn trước khi gửi đi — không phí một lượt HTTP chắc chắn hỏng")
    void matKhauRongBiChan() {
        String token = lienKetChoTaiKhoanMoi();

        assertThatThrownBy(() -> setPasswords.setPassword(token, "   "))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("thiếu token bị chặn")
    void thieuTokenBiChan() {
        assertThatThrownBy(() -> setPasswords.setPassword(null, "mat-khau-nao-do"))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo(MembershipProblemCodes.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("chưa cấu hình cổng danh tính thì báo 503 chứ không im lặng thành công")
    void chuaCauHinhThiBao() {
        String token = lienKetChoTaiKhoanMoi();
        identityProvider.notConfigured();

        assertThatThrownBy(() -> setPasswords.setPassword(token, "mat-khau-cua-rieng-toi"))
                .isInstanceOf(IdentityProviderException.class);
    }
}

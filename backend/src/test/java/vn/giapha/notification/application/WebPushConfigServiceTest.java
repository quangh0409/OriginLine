package vn.giapha.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.notification.application.view.WebPushConfigView;
import vn.giapha.notification.domain.WebPushConfigStatus;
import vn.giapha.notification.domain.port.VapidKeyProvider;

/**
 * Màn hình chẩn đoán của quản trị viên phải trả lời được câu "thiếu <b>cái gì</b>", không chỉ
 * "chưa cấu hình".
 *
 * <p>Ba tình huống dưới đây trước kia ra cùng một phản hồi 422: kênh bị tắt tay, thiếu cả hai khoá,
 * và thiếu đúng một nửa cặp khoá. Người quản trị nhận lời phàn nàn của thành viên rồi không có gì
 * để phân biệt chúng.</p>
 */
class WebPushConfigServiceTest {

    private static final String KHOA_CONG_KHAI = "BHPRzYgxaK_Xm_bxolJqJTuCwKDfsvFIy5SIPno";

    @Test
    @DisplayName("Da cau hinh du -> ready=true va KHONG con viec gi phai lam")
    void daSanSang() {
        WebPushConfigView view = service(new WebPushConfigStatus(
                true, true, true, KHOA_CONG_KHAI, "mailto:admin@giapha.vn", 86_400)).status();

        assertThat(view.ready()).isTrue();
        assertThat(view.publicKey()).isEqualTo(KHOA_CONG_KHAI);
        assertThat(view.remediation()).isEmpty();
        assertThat(view.summary()).contains("san sang");
    }

    @Test
    @DisplayName("Chua co khoa nao -> ready=false kem cac buoc sinh khoa, nap bien, KHOI DONG LAI")
    void chuaCoKhoa() {
        WebPushConfigView view = service(new WebPushConfigStatus(
                true, false, false, null, "mailto:admin@giapha.vn", 86_400)).status();

        assertThat(view.ready()).isFalse();
        assertThat(view.remediation()).isNotEmpty();
        assertThat(String.join(" ", view.remediation()))
                .contains("VapidKeyGenerator")
                .contains("GIAPHA_WEBPUSH_PUBLIC_KEY")
                .contains("GIAPHA_WEBPUSH_PRIVATE_KEY")
                // Không nhắc khởi động lại thì người vận hành đặt biến, gọi lại, thấy vẫn đỏ, và
                // kết luận nhầm rằng khoá sai.
                .contains("Khoi dong lai");
    }

    @Test
    @DisplayName("Chi co khoa cong khai -> noi ro dang thieu KHOA RIENG, khong noi chung chung")
    void thieuKhoaRieng() {
        WebPushConfigView view = service(new WebPushConfigStatus(
                true, true, false, null, "mailto:admin@giapha.vn", 86_400)).status();

        assertThat(view.ready()).isFalse();
        assertThat(view.publicKeySet()).isTrue();
        assertThat(view.privateKeySet()).isFalse();
        assertThat(view.summary()).contains("thieu khoa rieng");
    }

    @Test
    @DisplayName("Bi tat bang tay -> khong bao 'thieu khoa', vi khoa co the van con day du")
    void biTatBangTay() {
        WebPushConfigView view = service(new WebPushConfigStatus(
                false, true, true, null, "mailto:admin@giapha.vn", 86_400)).status();

        assertThat(view.ready()).isFalse();
        assertThat(view.summary()).contains("TAT bang tay");
        assertThat(String.join(" ", view.remediation())).contains("giapha.webpush.enabled");
    }

    @Test
    @DisplayName("KHONG bao gio lo khoa rieng - chi mot co boolean")
    void khongLoKhoaRieng() {
        WebPushConfigStatus status = new WebPushConfigStatus(
                true, true, true, KHOA_CONG_KHAI, "mailto:admin@giapha.vn", 86_400);

        // Bản ghi trạng thái không có chỗ nào để đặt khoá riêng vào: đó là ràng buộc của kiểu, không
        // phải một quy ước phải nhớ.
        assertThat(status.toString()).doesNotContain("privateKey=");
        assertThat(service(status).status().toString()).doesNotContain("privateKey=");
    }

    private static WebPushConfigService service(WebPushConfigStatus status) {
        return new WebPushConfigService(new VapidKeyProvider() {
            @Override
            public Optional<String> publicKeyBase64Url() {
                return Optional.ofNullable(status.publicKey());
            }

            @Override
            public WebPushConfigStatus configStatus() {
                return status;
            }
        });
    }
}

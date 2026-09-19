package vn.giapha.notification.infrastructure.webpush;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Khoá VAPID <b>chỉ</b> đến từ biến môi trường.
 *
 * <p>Khoá riêng Web Push cho phép đẩy thông báo tới điện thoại thật của người trong họ. Một khoá lọt
 * vào repo là lọt vĩnh viễn — lịch sử Git không quên. Vì vậy ba điều được kiểm ở đây: không có giá
 * trị mặc định nào trong mã, tên biến môi trường đúng như tài liệu vận hành, và không tệp cấu hình
 * nào trong repo mang khoá.</p>
 */
class WebPushPropertiesTest {

    private static final String BIEN_KHOA_CONG_KHAI = "GIAPHA_WEBPUSH_PUBLIC_KEY";
    private static final String BIEN_KHOA_RIENG = "GIAPHA_WEBPUSH_PRIVATE_KEY";

    @Test
    @DisplayName("Mac dinh KHONG co khoa nao trong ma nguon")
    void macDinhKhongCoKhoa() {
        WebPushProperties properties = new WebPushProperties();

        assertThat(properties.getPublicKey()).isNull();
        assertThat(properties.getPrivateKey()).isNull();
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Chua cau hinh thi kenh Web Push tu tat, KHONG lam hong ung dung")
    void chuaCauHinhThiTuTat() {
        WebPushProperties properties = new WebPushProperties();
        properties.setEnabled(true);

        // In-app là nguồn chân lý của thông báo; một môi trường chưa có VAPID vẫn phải chạy được.
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Doc dung tu GIAPHA_WEBPUSH_PUBLIC_KEY / GIAPHA_WEBPUSH_PRIVATE_KEY")
    void docTuBienMoiTruong() {
        Map<String, Object> bienMoiTruong = new HashMap<>();
        bienMoiTruong.put(BIEN_KHOA_CONG_KHAI, "khoa-cong-khai-gia-lap");
        bienMoiTruong.put(BIEN_KHOA_RIENG, "khoa-rieng-gia-lap");

        WebPushProperties properties = bind(bienMoiTruong);

        assertThat(properties.getPublicKey()).isEqualTo("khoa-cong-khai-gia-lap");
        assertThat(properties.getPrivateKey()).isEqualTo("khoa-rieng-gia-lap");
        assertThat(properties.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("Chi co khoa cong khai thi van coi la chua cau hinh - khong ky duoc JWT VAPID")
    void thieuMotNuaThiVanChuaCauHinh() {
        WebPushProperties properties = bind(Map.of(BIEN_KHOA_CONG_KHAI, "chi-co-mot-nua"));

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("toString KHONG bao gio lo khoa rieng - Spring in cau hinh ra log o che do debug")
    void toStringKhongLoKhoaRieng() {
        WebPushProperties properties = bind(Map.of(
                BIEN_KHOA_CONG_KHAI, "khoa-cong-khai-gia-lap",
                BIEN_KHOA_RIENG, "KHOA-RIENG-KHONG-DUOC-LO"));

        assertThat(properties.toString())
                .doesNotContain("KHOA-RIENG-KHONG-DUOC-LO")
                .contains("da dat (an)");
    }

    @Test
    @DisplayName("Khong tep cau hinh nao trong repo mang khoa VAPID")
    void khongTepCauHinhNaoMangKhoa() {
        for (String tep : new String[]{"/application.yml", "/application-dev.yml"}) {
            String noiDung = docTaiNguyen(tep);
            if (noiDung == null) {
                continue;
            }
            String thuong = noiDung.toLowerCase(Locale.ROOT);
            assertThat(thuong)
                    .as("%s khong duoc chua khoa rieng VAPID", tep)
                    .doesNotContain("private-key")
                    .doesNotContain("privatekey");
        }
    }

    @Test
    @DisplayName("Tep mau infra/webpush-dev.example.txt chi chua cho danh san, khong chua khoa that")
    void tepMauKhongMangKhoaThat() throws IOException {
        // Tệp mẫu là thứ duy nhất của Web Push nằm trong kho mã. Nếu một ngày có người "tiện tay"
        // dán cặp khoá dev thật vào đó cho đỡ phải sinh lại, khoá sẽ vào lịch sử Git và ở đó vĩnh
        // viễn. Đường dẫn tương đối hợp lệ vì Surefire chạy với thư mục làm việc là `backend/`.
        java.nio.file.Path tepMau = java.nio.file.Path.of("..", "infra", "webpush-dev.example.txt");
        assertThat(tepMau).as("README tro toi tep nay - xoa no la tai lieu gay").exists();

        for (String dong : java.nio.file.Files.readAllLines(tepMau, StandardCharsets.UTF_8)) {
            String sach = dong.trim();
            if (sach.startsWith("#") || !sach.startsWith("GIAPHA_WEBPUSH_")) {
                continue;
            }
            String giaTri = sach.substring(sach.indexOf('=') + 1).trim();
            assertThat(giaTri)
                    .as("dong '%s' trong tep mau trong nhu mot khoa THAT", sach)
                    .doesNotMatch("^[A-Za-z0-9_-]{40,}$");
        }
    }

    private static WebPushProperties bind(Map<String, ?> bienMoiTruong) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> nguon = new HashMap<>(bienMoiTruong);
        // Đặt vào đúng loại property source của biến môi trường: chỉ nguồn này mới cho phép Spring
        // ánh xạ lỏng GIAPHA_WEBPUSH_PUBLIC_KEY -> giapha.webpush.public-key.
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, nguon));
        return Binder.get(environment).bind("giapha.webpush", WebPushProperties.class)
                .orElseGet(WebPushProperties::new);
    }

    private static String docTaiNguyen(String ten) {
        try (InputStream in = WebPushPropertiesTest.class.getResourceAsStream(ten)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Khong doc duoc " + ten, ex);
        }
    }
}

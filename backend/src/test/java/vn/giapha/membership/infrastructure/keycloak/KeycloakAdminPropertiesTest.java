package vn.giapha.membership.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Bí mật của client dịch vụ Keycloak <b>chỉ</b> đến từ biến môi trường.
 *
 * <p>Bí mật này mở quyền <b>tạo người dùng trong realm</b>. Một chuỗi như thế lọt vào kho mã là lọt
 * vĩnh viễn — lịch sử Git không quên, và mọi bản clone đều đã có. Bốn điều được kiểm ở đây: không
 * có giá trị mặc định nào trong mã, tên biến môi trường đúng như tài liệu vận hành, không tệp cấu
 * hình nào trong repo mang bí mật, và tệp mẫu chỉ chứa chỗ dành sẵn.</p>
 *
 * <p>Cùng khuôn mẫu với {@code WebPushPropertiesTest} — cố ý, vì đây là bí mật thứ hai của hệ thống
 * và hai bí mật nên được canh bằng cùng một cách.</p>
 */
class KeycloakAdminPropertiesTest {

    private static final String BIEN_BI_MAT = "GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET";

    @Test
    @DisplayName("Mac dinh KHONG co bi mat nao trong ma nguon")
    void macDinhKhongCoBiMat() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();

        assertThat(properties.getClientSecret()).isNull();
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Chua cau hinh thi cong danh tinh tu tat, KHONG lam hong ung dung")
    void chuaCauHinhThiTuTat() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setEnabled(true);

        // Loi nhan loi moi bang token san co van chay; chi loi "nguoi chua co tai khoan" la 503.
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Doc dung tu GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET")
    void docTuBienMoiTruong() {
        KeycloakAdminProperties properties = bind(Map.of(BIEN_BI_MAT, "bi-mat-gia-lap"));

        assertThat(properties.getClientSecret()).isEqualTo("bi-mat-gia-lap");
        assertThat(properties.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("toString KHONG bao gio lo bi mat - Spring in cau hinh ra log o che do debug")
    void toStringKhongLoBiMat() {
        KeycloakAdminProperties properties = bind(Map.of(BIEN_BI_MAT, "BI-MAT-KHONG-DUOC-LO"));

        assertThat(properties.toString())
                .doesNotContain("BI-MAT-KHONG-DUOC-LO")
                .contains("da dat (an)");
    }

    @Test
    @DisplayName("Client dich vu MAC DINH khong phai giapha-backend")
    void clientDichVuTachRieng() {
        // giapha-backend la audience cua moi token nguoi dung. Gan them quyen quan tri realm vao
        // dung client ay la gop hai muc rui ro vao mot bi mat.
        assertThat(new KeycloakAdminProperties().getClientId())
                .isEqualTo("giapha-provisioner")
                .isNotEqualTo("giapha-backend");
    }

    @Test
    @DisplayName("Khong tep cau hinh nao trong repo mang bi mat client dich vu")
    void khongTepCauHinhNaoMangBiMat() {
        for (String tep : new String[]{"/application.yml", "/application-dev.yml"}) {
            String noiDung = docTaiNguyen(tep);
            if (noiDung == null) {
                continue;
            }
            String thuong = noiDung.toLowerCase(Locale.ROOT);
            // Chi chan DONG KHAI BAO gia tri. Ten bien moi truong trong chu thich thi duoc — do la
            // tai lieu van hanh, va giau no di khong bao mat them gi ma chi lam kho nguoi van hanh.
            assertThat(thuong)
                    .as("%s khong duoc chua bi mat client dich vu", tep)
                    .doesNotContain("client-secret:")
                    .doesNotContain("clientsecret:");
        }
    }

    @Test
    @DisplayName("Tep mau infra/keycloak-admin-dev.example.txt chi chua cho danh san")
    void tepMauKhongMangBiMatThat() throws IOException {
        // Duong dan tuong doi hop le vi Surefire chay voi thu muc lam viec la `backend/`.
        Path tepMau = Path.of("..", "infra", "keycloak-admin-dev.example.txt");
        assertThat(tepMau).as("README tro toi tep nay - xoa no la tai lieu gay").exists();

        for (String dong : Files.readAllLines(tepMau, StandardCharsets.UTF_8)) {
            String sach = dong.trim();
            if (sach.startsWith("#") || !sach.startsWith("GIAPHA_KEYCLOAK_")) {
                continue;
            }
            String giaTri = sach.substring(sach.indexOf('=') + 1).trim();
            assertThat(giaTri)
                    .as("dong '%s' trong tep mau trong nhu mot bi mat THAT", sach)
                    .doesNotMatch("^[A-Za-z0-9]{24,}$");
        }
    }

    @Test
    @DisplayName("Realm mau trong repo KHONG mang bi mat that cua client dich vu")
    void realmMauKhongMangBiMatThat() throws IOException {
        Path realm = Path.of("..", "infra", "keycloak", "realm-giapha.json");
        String noiDung = Files.readString(realm, StandardCharsets.UTF_8);

        // Realm mau nay chi de dung moi truong phat trien tu con so khong. Bi mat that phai den tu
        // bien moi truong; trong tep chi duoc co cho danh san noi ro la cua dev.
        assertThat(noiDung).contains("giapha-provisioner");
        assertThat(noiDung).doesNotContainPattern("\"secret\"\\s*:\\s*\"[A-Za-z0-9]{24,}\"");
    }

    private static KeycloakAdminProperties bind(Map<String, ?> bienMoiTruong) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> nguon = new HashMap<>(bienMoiTruong);
        // Dat vao dung loai property source cua bien moi truong: chi nguon nay moi cho phep Spring
        // anh xa long GIAPHA_KEYCLOAK_ADMIN_CLIENT_SECRET -> giapha.keycloak.admin.client-secret.
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, nguon));
        return Binder.get(environment).bind("giapha.keycloak.admin", KeycloakAdminProperties.class)
                .orElseGet(KeycloakAdminProperties::new);
    }

    private static String docTaiNguyen(String ten) {
        try (InputStream in = KeycloakAdminPropertiesTest.class.getResourceAsStream(ten)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Khong doc duoc " + ten, ex);
        }
    }
}

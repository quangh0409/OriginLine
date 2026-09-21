package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Định danh đăng nhập: email <b>hoặc</b> số điện thoại.
 *
 * <p>Lớp này tồn tại để sửa một mâu thuẫn trong chính sản phẩm — ô đăng nhập của realm nhận cả
 * hai, nhưng hai lối lập tài khoản từng chỉ nhận email, nên đúng nhóm người mà luồng mời sinh ra
 * để phục vụ (các cụ không có email) lại là nhóm duy nhất không dùng được nó.</p>
 */
@DisplayName("Định danh đăng nhập")
class LoginIdentifierTest {

    @Test
    @DisplayName("email được hạ chữ thường và giữ nguyên làm tên đăng nhập")
    void emailHaChuThuong() {
        LoginIdentifier id = LoginIdentifier.of("  Ba.Lan@Example.COM ");
        assertThat(id.kind()).isEqualTo(LoginIdentifier.Kind.EMAIL);
        assertThat(id.value()).isEqualTo("ba.lan@example.com");
        assertThat(id.emailOrNull()).isEqualTo("ba.lan@example.com");
    }

    @ParameterizedTest(name = "{0} → 0912345678")
    @ValueSource(strings = {
            "0912345678",
            "0912 345 678",
            "0912.345.678",
            "0912-345-678",
            "+84912345678",
            "84912345678",
            " (0912) 345 678 "})
    @DisplayName("bốn cách viết một số máy là MỘT số")
    void chuanHoaSoDienThoai(String raw) {
        LoginIdentifier id = LoginIdentifier.of(raw);
        assertThat(id.kind()).isEqualTo(LoginIdentifier.Kind.PHONE);
        // Khong quy ve mot dang thi cung mot nguoi dang ky hai lan se ra HAI tai khoan — roi mot
        // trong hai duoc Truong chi ghep vao pha con cai kia thi khong, va khong ai hieu vi sao
        // dang nhap duoc ma khong thay gi.
        assertThat(id.value()).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("số điện thoại KHÔNG đi vào ô email — realm sẽ từ chối, và thư sẽ gửi vào hư không")
    void soDienThoaiKhongDiVaoOEmail() {
        LoginIdentifier id = LoginIdentifier.of("0912345678");
        assertThat(id.emailOrNull()).isNull();
        assertThat(id.isEmail()).isFalse();
    }

    @Test
    @DisplayName("có @ thì luôn là email, kể cả khi phần trước toàn số")
    void coAThiLuonLaEmail() {
        LoginIdentifier id = LoginIdentifier.of("0912345678@example.com");
        assertThat(id.kind()).isEqualTo(LoginIdentifier.Kind.EMAIL);
    }

    @Test
    @DisplayName("số bàn ngắn hơn di động vẫn nhận — một cụ ở quê có thể chỉ có số bàn")
    void soBanVanNhan() {
        assertThat(LoginIdentifier.of("0243825999").kind())
                .isEqualTo(LoginIdentifier.Kind.PHONE);
        assertThat(LoginIdentifier.of("024382599").kind())
                .isEqualTo(LoginIdentifier.Kind.PHONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "khong-phai-gi-ca",
            "0912",
            "912345678",
            "091234567890123",
            "@example.com",
            "ba.lan@",
            "ba.lan@example"})
    @DisplayName("đọc không ra thì từ chối, không đoán")
    void docKhongRaThiTuChoi(String raw) {
        assertThatThrownBy(() -> LoginIdentifier.of(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rỗng thì nói rõ là phải cho biết email hoặc số điện thoại")
    void rongThiNoiRo() {
        assertThatThrownBy(() -> LoginIdentifier.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("so dien thoai");
    }

    @Test
    @DisplayName("toString KHÔNG in giá trị — cả email lẫn số máy đều là dữ liệu cá nhân")
    void toStringKhongInGiaTri() {
        assertThat(LoginIdentifier.of("0912345678").toString())
                .doesNotContain("0912345678")
                .contains("PHONE");
    }
}

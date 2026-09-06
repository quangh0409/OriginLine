package vn.giapha.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mắt xích giữa của chuỗi {@code keycloak_sub → app_user → person}.
 *
 * <p>Khoá nối là claim {@code sub}, <b>không phải email</b>: email đổi được, và một người có thể
 * đăng nhập bằng Google rồi bằng Zalo với cùng địa chỉ.</p>
 */
class AppUserTest {

    @Nested
    @DisplayName("Đăng ký lần đầu")
    class DangKyLanDau {

        @Test
        @DisplayName("Tài khoản mới ở trạng thái PENDING và chưa ghép vào cây")
        void taiKhoanMoi() {
            AppUser fresh = AppUser.register(UUID.randomUUID(), "sub-google-001",
                    "nguyenvana@example.test", "Nguyễn Văn A");

            assertThat(fresh.status()).isEqualTo(AppUserStatus.PENDING);
            assertThat(fresh.personId()).isNull();
            assertThat(fresh.isLinkedToTree()).isFalse();
            // Chua ghep thi khong co "chi nha", nen moi phep xet cung chi cua Tang 2 deu truot.
            assertThat(fresh.status().canOperate()).isFalse();
            assertThat(fresh.locale()).isEqualTo("vi");
        }

        @Test
        @DisplayName("keycloak_sub rỗng bị chặn — không có sub thì không có khoá nối nào")
        void subRongBiChan() {
            assertThatThrownBy(() -> AppUser.register(UUID.randomUUID(), "  ", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keycloak_sub");
            assertThatThrownBy(() -> AppUser.register(UUID.randomUUID(), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("locale chỉ nhận vi hoặc en, khớp ck_app_user_locale")
        void chuanHoaLocale() {
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", null, null);

            user.changeLocale("EN");
            assertThat(user.locale()).isEqualTo("en");
            user.changeLocale("fr");
            assertThat(user.locale()).isEqualTo("vi");
            user.changeLocale(null);
            assertThat(user.locale()).isEqualTo("vi");
        }
    }

    @Nested
    @DisplayName("Ghép tài khoản với nhân khẩu")
    class GhepVaoCay {

        @Test
        @DisplayName("Ghép lần đầu chuyển PENDING sang ACTIVE")
        void ghepLanDau() {
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", null, null);
            UUID person = UUID.randomUUID();

            user.linkPerson(person);

            assertThat(user.personId()).isEqualTo(person);
            assertThat(user.status()).isEqualTo(AppUserStatus.ACTIVE);
            assertThat(user.isLinkedToTree()).isTrue();
        }

        @Test
        @DisplayName("Ghép lại đúng người cũ là thao tác không đổi gì (idempotent)")
        void ghepLaiCungNguoi() {
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", null, null);
            UUID person = UUID.randomUUID();
            user.linkPerson(person);

            user.linkPerson(person);

            assertThat(user.personId()).isEqualTo(person);
        }

        @Test
        @DisplayName("KHÔNG tự đổi sang nhân khẩu khác")
        void khongTuDoiNguoi() {
            // Ghep lai sang nguoi khac la nghiep vu khac han: no phai de lai vet UPDATE co ly do,
            // khong duoc lan vao loi "ghep lan dau". Va ghep sai la trao cho mot nguoi quyen xem
            // Tang 3 cua nguoi khac duoi danh nghia "ho so cua minh".
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", null, null);
            user.linkPerson(UUID.randomUUID());

            assertThatThrownBy(() -> user.linkPerson(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("da duoc ghep");
        }

        @Test
        @DisplayName("Ghép với null bị chặn")
        void ghepNull() {
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", null, null);

            assertThatThrownBy(() -> user.linkPerson(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Tài khoản đã bị khoá thì ghép KHÔNG tự mở khoá")
        void ghepKhongMoKhoaTaiKhoan() {
            AppUser suspended = new AppUser(UUID.randomUUID(), "sub-1", null, null, null,
                    AppUserStatus.SUSPENDED, "vi", null, 0L);

            suspended.linkPerson(UUID.randomUUID());

            assertThat(suspended.status()).isEqualTo(AppUserStatus.SUSPENDED);
            assertThat(suspended.status().canOperate()).isFalse();
        }
    }

    @Nested
    @DisplayName("Làm mới hồ sơ ở mỗi lần đăng nhập")
    class LamMoiHoSo {

        @Test
        @DisplayName("Cập nhật email, tên hiển thị và thời điểm đăng nhập")
        void capNhatTuToken() {
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", "cu@example.test", "Cũ");
            Instant luc = Instant.parse("2026-09-05T00:00:00Z");

            user.refreshProfile("moi@example.test", "Nguyễn Văn A", luc);

            assertThat(user.email()).isEqualTo("moi@example.test");
            assertThat(user.displayName()).isEqualTo("Nguyễn Văn A");
            assertThat(user.lastLoginAt()).isEqualTo(luc);
        }

        @Test
        @DisplayName("Giá trị rỗng trong token KHÔNG xoá dữ liệu đang có")
        void rongThiGiuNguyen() {
            // Zalo khong tra email. Neu de nó ghi de thi mot lan dang nhap bang Zalo se xoa email
            // ma nguoi dung da khai bang Google.
            AppUser user = AppUser.register(UUID.randomUUID(), "sub-1", "cu@example.test", "Cũ");

            user.refreshProfile(null, "   ", null);

            assertThat(user.email()).isEqualTo("cu@example.test");
            assertThat(user.displayName()).isEqualTo("Cũ");
            assertThat(user.lastLoginAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Trạng thái tài khoản")
    class TrangThai {

        @Test
        @DisplayName("Chỉ ACTIVE mới được thao tác")
        void chiActiveMoiThaoTac() {
            assertThat(AppUserStatus.ACTIVE.canOperate()).isTrue();
            assertThat(AppUserStatus.PENDING.canOperate()).isFalse();
            assertThat(AppUserStatus.SUSPENDED.canOperate()).isFalse();
            assertThat(AppUserStatus.DISABLED.canOperate()).isFalse();
        }

        @Test
        @DisplayName("Không có trạng thái nào nghĩa là xoá dòng app_user")
        void khongCoTrangThaiXoa() {
            // audit_log.actor_user_id tro toi app_user; mot nhat ky mat nguoi thuc hien la nhat ky
            // vo dung. Yeu cau xoa du lieu theo Nghi dinh 13/2023 duoc phuc vu bang DISABLED
            // (va an danh hoa ben genealogy), khong bang DELETE.
            assertThat(AppUserStatus.values()).containsExactlyInAnyOrder(
                    AppUserStatus.PENDING, AppUserStatus.ACTIVE,
                    AppUserStatus.SUSPENDED, AppUserStatus.DISABLED);
        }

        @Test
        @DisplayName("Chuỗi rỗng phân giải thành PENDING, chuỗi lạ thì ném")
        void phanGiaiChuoi() {
            assertThat(AppUserStatus.of(null)).isEqualTo(AppUserStatus.PENDING);
            assertThat(AppUserStatus.of("  ")).isEqualTo(AppUserStatus.PENDING);
            assertThat(AppUserStatus.of(" active ")).isEqualTo(AppUserStatus.ACTIVE);
            assertThatThrownBy(() -> AppUserStatus.of("DELETED"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Định danh so theo id, không so theo sub hay email")
    void soSanhTheoId() {
        UUID id = UUID.randomUUID();
        AppUser mot = new AppUser(id, "sub-1", null, "a@example.test", null, null, null, null, 0L);
        AppUser hai = new AppUser(id, "sub-2", null, "b@example.test", null, null, null, null, 3L);

        assertThat(mot).isEqualTo(hai).hasSameHashCodeAs(hai);
    }
}

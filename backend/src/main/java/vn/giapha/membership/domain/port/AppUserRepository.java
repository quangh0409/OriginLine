package vn.giapha.membership.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.AppUser;

/** Lưu trữ tài khoản. Khoá tra cứu chính là {@code keycloak_sub}, không phải email. */
public interface AppUserRepository {

    Optional<AppUser> byId(UUID id);

    /** Khoá nối duy nhất từ JWT sang dữ liệu ứng dụng ({@code ux_app_user_keycloak_sub}). */
    Optional<AppUser> byKeycloakSub(String keycloakSub);

    /** Tài khoản gắn với một nhân khẩu; nhiều nhất một ({@code ux_app_user_person}). */
    Optional<AppUser> byPersonId(UUID personId);

    /** Tài khoản của các thành viên thuộc một chi — dùng khi cần biết ai được nhắc giỗ. */
    List<AppUser> byPersonIds(List<UUID> personIds);

    AppUser save(AppUser user);
}

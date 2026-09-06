package vn.giapha.membership.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.membership.domain.AppUser;
import vn.giapha.membership.domain.port.AppUserRepository;

/**
 * Bản trong bộ nhớ của {@code app_user}, mô phỏng cả hai ràng buộc duy nhất của V5:
 * {@code ux_app_user_keycloak_sub} và {@code ux_app_user_person}.
 *
 * <p>Mô phỏng ràng buộc chứ không chỉ lưu map: nếu bản giả cho phép hai tài khoản cùng trỏ một
 * nhân khẩu thì test về việc ghép tài khoản sẽ xanh trên một trạng thái mà CSDL thật từ chối.</p>
 */
public final class InMemoryAppUserRepository implements AppUserRepository {

    private final Map<UUID, AppUser> byId = new LinkedHashMap<>();

    /** Đếm số lần {@link #save} bị gọi — dùng để soi lối tự khởi tạo ở lần đăng nhập đầu. */
    private int saveCount;

    /** Nếu khác {@code null}, lần {@link #save} kế tiếp ném ngoại lệ này rồi tự xoá bẫy. */
    private RuntimeException failNextSave;

    /**
     * Bản ghi mà "luồng kia" đã chèn trước nhưng luồng này chưa nhìn thấy — xem
     * {@link #simulateRaceWith(AppUser)}.
     */
    private AppUser raceWinner;

    public AppUser seed(AppUser user) {
        byId.put(user.id(), user);
        return user;
    }

    @Override
    public Optional<AppUser> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<AppUser> byKeycloakSub(String keycloakSub) {
        if (keycloakSub == null) {
            return Optional.empty();
        }
        return byId.values().stream()
                .filter(user -> keycloakSub.equals(user.keycloakSub()))
                .findFirst();
    }

    @Override
    public Optional<AppUser> byPersonId(UUID personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return byId.values().stream()
                .filter(user -> personId.equals(user.personId()))
                .findFirst();
    }

    @Override
    public List<AppUser> byPersonIds(List<UUID> personIds) {
        List<AppUser> found = new ArrayList<>();
        if (personIds == null) {
            return found;
        }
        for (UUID personId : personIds) {
            byPersonId(personId).ifPresent(found::add);
        }
        return found;
    }

    @Override
    public AppUser save(AppUser user) {
        saveCount++;
        if (failNextSave != null) {
            RuntimeException boom = failNextSave;
            failNextSave = null;
            if (raceWinner != null) {
                // Luong kia da chen xong trong lúc luong nay dang chay: tu day tro di ban ghi hien
                // ra voi moi truy van, dung nhu sau khi INSERT cua no commit.
                byId.put(raceWinner.id(), raceWinner);
                raceWinner = null;
            }
            throw boom;
        }
        // ux_app_user_keycloak_sub
        byKeycloakSub(user.keycloakSub())
                .filter(other -> !other.id().equals(user.id()))
                .ifPresent(other -> {
                    throw new IllegalStateException(
                            "Vi pham ux_app_user_keycloak_sub: " + user.keycloakSub());
                });
        // ux_app_user_person
        if (user.personId() != null) {
            byPersonId(user.personId())
                    .filter(other -> !other.id().equals(user.id()))
                    .ifPresent(other -> {
                        throw new IllegalStateException(
                                "Vi pham ux_app_user_person: " + user.personId());
                    });
        }
        byId.put(user.id(), user);
        return user;
    }

    public int saveCount() {
        return saveCount;
    }

    public void failNextSaveWith(RuntimeException error) {
        this.failNextSave = error;
    }

    /**
     * Dựng đúng hình dạng của cuộc đua ở lần đăng nhập đầu.
     *
     * <p>Hai luồng cùng thấy "chưa có tài khoản" rồi cùng INSERT. {@code winner} <b>chưa</b> hiện ra
     * với {@link #byKeycloakSub} — vì luồng này đã đọc trước khi luồng kia commit — nhưng lần
     * {@link #save} tiếp theo sẽ vỡ vì {@code ux_app_user_keycloak_sub}, và ngay sau đó bản ghi của
     * luồng kia trở nên nhìn thấy được. Nếu bản giả chỉ ném ngoại lệ mà không làm bản ghi hiện ra
     * thì bước "đọc lại" của service sẽ không có gì để đọc, và test sẽ kiểm nhầm một hành vi khác.</p>
     */
    public void simulateRaceWith(AppUser winner) {
        this.raceWinner = winner;
        this.failNextSave = new org.springframework.dao.DataIntegrityViolationException(
                "ux_app_user_keycloak_sub");
    }
}

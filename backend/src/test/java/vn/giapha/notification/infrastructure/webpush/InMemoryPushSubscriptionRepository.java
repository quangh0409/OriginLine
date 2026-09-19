package vn.giapha.notification.infrastructure.webpush;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;

/**
 * Kho đăng ký thiết bị trong bộ nhớ — bản sao <b>hành vi</b> của
 * {@code PushSubscriptionJdbcRepository} đủ cho các ca test của {@link WebPushAdapter}.
 *
 * <p>Không phải mock: nó thật sự xoá bản ghi khi {@code deleteByEndpoint} được gọi, nên câu hỏi
 * "sau khi máy chủ đẩy trả 410 thì lượt gửi kế tiếp còn thấy thiết bị đó không" trả lời được ngay
 * trong test đơn vị. Việc dòng SQL {@code DELETE} có thật sự xoá trong PostgreSQL là chuyện của
 * {@code WebPushRevokedSubscriptionIT}.</p>
 */
final class InMemoryPushSubscriptionRepository implements PushSubscriptionRepository {

    /** Giữ thứ tự chèn để phản ánh {@code ORDER BY created_at} của bản JDBC. */
    private final Map<String, PushSubscription> theoEndpoint = new LinkedHashMap<>();

    private final List<String> endpointDaXoa = new ArrayList<>();
    private final List<UUID> daGhiNhanLoi = new ArrayList<>();
    private final Map<UUID, Instant> lanDungCuoi = new LinkedHashMap<>();

    PushSubscription them(UUID appUserId, String endpoint, String p256dh, String auth) {
        return them(appUserId, endpoint, p256dh, auth, null);
    }

    PushSubscription them(UUID appUserId, String endpoint, String p256dh, String auth,
                          Instant expiresAt) {
        PushSubscription subscription = new PushSubscription(UUID.randomUUID(), appUserId, endpoint,
                p256dh, auth, "test-agent", true, 0, null, expiresAt, Instant.now());
        theoEndpoint.put(endpoint, subscription);
        return subscription;
    }

    /** Bản ghi hiện tại theo endpoint — để test soi {@code is_active} và {@code failure_count}. */
    PushSubscription theoEndpoint(String endpoint) {
        return theoEndpoint.get(endpoint);
    }

    List<String> endpointDaXoa() {
        return List.copyOf(endpointDaXoa);
    }

    List<UUID> daGhiNhanLoi() {
        return List.copyOf(daGhiNhanLoi);
    }

    Map<UUID, Instant> lanDungCuoi() {
        return Map.copyOf(lanDungCuoi);
    }

    int soThietBiConLai() {
        return theoEndpoint.size();
    }

    @Override
    public Upsert save(UUID appUserId, String endpoint, String p256dh, String auth, String userAgent,
                       Instant expiresAt) {
        boolean moi = !theoEndpoint.containsKey(endpoint);
        return new Upsert(them(appUserId, endpoint, p256dh, auth, expiresAt), moi);
    }

    @Override
    public List<PushSubscription> activeByAppUser(UUID appUserId) {
        Instant bayGio = Instant.now();
        return theoEndpoint.values().stream()
                .filter(subscription -> subscription.active()
                        && subscription.appUserId().equals(appUserId)
                        // Cung dieu kien voi ban JDBC: het han thi khong gui nua.
                        && (subscription.expiresAt() == null
                                || subscription.expiresAt().isAfter(bayGio)))
                .toList();
    }

    @Override
    public Optional<PushSubscription> findOwned(UUID id, UUID appUserId) {
        return theoEndpoint.values().stream()
                .filter(subscription -> subscription.id().equals(id)
                        && subscription.appUserId().equals(appUserId))
                .findFirst();
    }

    @Override
    public boolean deleteOwned(UUID id, UUID appUserId) {
        return findOwned(id, appUserId)
                .map(subscription -> theoEndpoint.remove(subscription.endpoint()) != null)
                .orElse(false);
    }

    @Override
    public boolean deleteByEndpoint(String endpoint) {
        endpointDaXoa.add(endpoint);
        return theoEndpoint.remove(endpoint) != null;
    }

    @Override
    public void touchLastUsed(UUID id, Instant when) {
        lanDungCuoi.put(id, when);
    }

    @Override
    public boolean recordFailure(UUID id) {
        daGhiNhanLoi.add(id);
        PushSubscription hienTai = theoEndpoint.values().stream()
                .filter(subscription -> subscription.id().equals(id))
                .findFirst().orElse(null);
        if (hienTai == null || !hienTai.active()) {
            return false;
        }
        int soLoi = hienTai.failureCount() + 1;
        boolean conSong = soLoi < PushSubscriptionRepository.NGUONG_LOI_LIEN_TIEP;
        theoEndpoint.put(hienTai.endpoint(), new PushSubscription(hienTai.id(), hienTai.appUserId(),
                hienTai.endpoint(), hienTai.p256dh(), hienTai.auth(), hienTai.userAgent(), conSong,
                soLoi, hienTai.lastUsedAt(), hienTai.expiresAt(), hienTai.createdAt()));
        return !conSong;
    }
}

package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryOutcome;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;
import vn.giapha.notification.infrastructure.webpush.FakePushService;
import vn.giapha.notification.infrastructure.webpush.VapidSigner;
import vn.giapha.notification.infrastructure.webpush.WebPushAdapter;
import vn.giapha.notification.infrastructure.webpush.WebPushCipher;
import vn.giapha.notification.infrastructure.webpush.WebPushDecryptor;
import vn.giapha.notification.infrastructure.webpush.WebPushProperties;
import vn.giapha.notification.infrastructure.webpush.WebPushTestKeys;

/**
 * Nửa sau của tiêu chí ra số 7: <b>đăng ký bị thu hồi phải biến mất khỏi PostgreSQL</b>.
 *
 * <p>{@code WebPushAdapterTest} đã chứng minh adapter <i>gọi</i> {@code deleteByEndpoint}. Điều đó
 * chưa đủ: câu {@code DELETE} thật viết {@code WHERE md5(endpoint) = md5(:endpoint)} — đúng biểu
 * thức của chỉ mục {@code ux_push_subscription_endpoint} — và một câu SQL viết đúng cú pháp vẫn có
 * thể xoá không dòng nào. Ca test này chạy trên PostgreSQL thật với schema Flyway thật, và đếm số
 * dòng còn lại trong bảng.</p>
 *
 * <p>Toàn bộ đường đi là mã sản xuất: {@link WebPushAdapter} → HTTP thật → máy chủ đẩy giả trả
 * {@code 410} → {@code PushSubscriptionJdbcRepository} → bảng {@code push_subscription}.</p>
 */
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class WebPushRevokedSubscriptionIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PushSubscriptionRepository subscriptions;

    private FakePushService mayChuDay;
    private WebPushAdapter adapter;
    private UUID appUserId;
    private UUID personId;

    @BeforeEach
    void dungHaTangWebPush() throws Exception {
        mayChuDay = FakePushService.khoiDong();

        WebPushProperties cauHinh = WebPushTestKeys.cauHinhVapidMoi();
        cauHinh.setJwtValidity(Duration.ofHours(12));
        adapter = new WebPushAdapter(cauHinh, new VapidSigner(), new WebPushCipher(),
                subscriptions, JSON);

        // Khong can gieo nhan khau: push_subscription chi rang buoc toi app_user, va `personId` o
        // day chi di vao Recipient de ghi nhat ky.
        personId = UUID.randomUUID();
        appUserId = insertAppUser("nguoi-trong-ho", null);
    }

    @AfterEach
    void dongHaTangWebPush() {
        mayChuDay.close();
    }

    @Test
    @DisplayName("HTTP 410 -> dong push_subscription bi XOA THAT khoi PostgreSQL")
    void ma410ThiDongBiXoaKhoiCsdl() throws Exception {
        WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
        String endpoint = mayChuDay.endpoint("da-go-app");
        subscriptions.save(appUserId, endpoint, thietBi.p256dh(), thietBi.auth(), "Chrome/131", null);
        assertThat(countRows("push_subscription")).isEqualTo(1);

        mayChuDay.traVe(410);
        DeliveryResult ketQua = adapter.send(tinNhacGio());

        assertThat(countRows("push_subscription"))
                .as("Giu lai ban ghi chet nghia la moi mua gio ton them mot luot HTTP cho moi may")
                .isZero();
        assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SKIPPED);
    }

    @Test
    @DisplayName("HTTP 404 -> dong bi XOA THAT (Firefox tra 404 thay vi 410)")
    void ma404ThiDongBiXoaKhoiCsdl() throws Exception {
        WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
        subscriptions.save(appUserId, mayChuDay.endpoint("khong-ton-tai"), thietBi.p256dh(),
                thietBi.auth(), "Firefox/133", null);

        mayChuDay.traVe(404);
        adapter.send(tinNhacGio());

        assertThat(countRows("push_subscription")).isZero();
    }

    @Test
    @DisplayName("Chi may bi thu hoi bi xoa; may con song van nam nguyen trong bang")
    void chiXoaDungMayBiThuHoi() throws Exception {
        WebPushDecryptor song = WebPushDecryptor.thietBiMoi();
        WebPushDecryptor chet = WebPushDecryptor.thietBiMoi();
        String endpointSong = mayChuDay.endpoint("con-song");
        String endpointChet = mayChuDay.endpoint("da-chet");
        subscriptions.save(appUserId, endpointSong, song.p256dh(), song.auth(), "Chrome/131", null);
        subscriptions.save(appUserId, endpointChet, chet.p256dh(), chet.auth(), "Chrome/120", null);

        mayChuDay.traVe(201);
        mayChuDay.traVeChoEndpoint(endpointChet, 410);
        DeliveryResult ketQua = adapter.send(tinNhacGio());

        assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.SENT);
        List<String> conLai = jdbc.queryForList(
                "SELECT endpoint FROM push_subscription", String.class);
        assertThat(conLai).containsExactly(endpointSong);

        // Va may con song van doc duoc noi dung: chuoi day du di het tu adapter toi ban ro.
        byte[] goi = mayChuDay.daNhan().stream()
                .filter(luot -> luot.duongDan().endsWith("con-song"))
                .findFirst()
                .orElseThrow()
                .than();
        JsonNode payload = JSON.readTree(song.giaiMa(goi));
        assertThat(payload.get("title").asText()).isEqualTo("Giỗ cụ Nguyễn Văn Đệ");
    }

    @Test
    @DisplayName("HTTP 503 -> KHONG xoa, chi tang failure_count trong bang")
    void loiTamThoiThiKhongXoaMaDemLoi() throws Exception {
        WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
        subscriptions.save(appUserId, mayChuDay.endpoint("may-chu-hong"), thietBi.p256dh(),
                thietBi.auth(), "Chrome/131", null);

        mayChuDay.traVe(503);
        DeliveryResult ketQua = adapter.send(tinNhacGio());

        assertThat(ketQua.outcome()).isEqualTo(DeliveryOutcome.RETRYABLE);
        assertThat(countRows("push_subscription")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT failure_count FROM push_subscription", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Gui thanh cong -> last_used_at duoc ghi va failure_count ve 0")
    void guiThanhCongThiGhiLanDungCuoi() throws Exception {
        WebPushDecryptor thietBi = WebPushDecryptor.thietBiMoi();
        subscriptions.save(appUserId, mayChuDay.endpoint("dien-thoai"), thietBi.p256dh(),
                thietBi.auth(), "Chrome/131", null);
        jdbc.update("UPDATE push_subscription SET failure_count = 3");

        mayChuDay.traVe(201);
        assertThat(adapter.send(tinNhacGio()).outcome()).isEqualTo(DeliveryOutcome.SENT);

        assertThat(jdbc.queryForObject(
                "SELECT last_used_at IS NOT NULL FROM push_subscription", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT failure_count FROM push_subscription", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Dang ky lai cung endpoint la CAP NHAT, khong sinh dong thu hai")
    void dangKyLaiCungEndpointThiCapNhat() throws Exception {
        WebPushDecryptor cu = WebPushDecryptor.thietBiMoi();
        WebPushDecryptor moi = WebPushDecryptor.thietBiMoi();
        String endpoint = mayChuDay.endpoint("dien-thoai");

        assertThat(subscriptions.save(appUserId, endpoint, cu.p256dh(), cu.auth(), "Chrome/131", null)
                .created()).isTrue();
        // Trinh duyet xoay vong khoa client sau khi cap nhat service worker.
        assertThat(subscriptions.save(appUserId, endpoint, moi.p256dh(), moi.auth(), "Chrome/132", null)
                .created()).isFalse();

        assertThat(countRows("push_subscription")).isEqualTo(1);

        mayChuDay.traVe(201);
        adapter.send(tinNhacGio());

        // Khoa moi phai duoc dung, khong phai khoa cu.
        assertThat(JSON.readTree(moi.giaiMa(mayChuDay.luotDuyNhat().than())).get("title").asText())
                .isEqualTo("Giỗ cụ Nguyễn Văn Đệ");
    }

    private NotificationMessage tinNhacGio() {
        return new NotificationMessage(UUID.randomUUID(), UUID.randomUUID(), personId,
                new Recipient(personId, appUserId, "vi"), Channel.WEBPUSH,
                NotificationCategory.REMINDER, "Giỗ cụ Nguyễn Văn Đệ", "Còn 3 ngày",
                "/persons/" + personId, 3, java.time.LocalDate.of(2026, 3, 27));
    }
}

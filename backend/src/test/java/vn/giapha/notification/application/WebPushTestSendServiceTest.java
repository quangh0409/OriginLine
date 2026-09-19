package vn.giapha.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.giapha.membership.support.TestSecurity;
import vn.giapha.notification.application.view.WebPushTestSendView;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;

/**
 * Nút "gửi thử" của quản trị viên.
 *
 * <p>Hai điều được ghim ở đây, và cả hai đều là điều kiện để nút này có ích: nó gửi cho
 * <b>chính người bấm</b> chứ không cho ai khác, và nó <b>trả kết quả thật</b> — kể cả kết quả xấu.
 * Một nút thử luôn báo "đã gửi" thì tệ hơn là không có nút nào: nó xác nhận sai rằng kênh đang
 * sống.</p>
 */
class WebPushTestSendServiceTest {

    private static final String SUB = "keycloak-sub-admin";
    private static final UUID PERSON = UUID.randomUUID();
    private static final UUID APP_USER = UUID.randomUUID();
    private static final Recipient TOI = new Recipient(PERSON, APP_USER, "vi");

    private FakeRecipientDirectory danhBa;
    private StubPushSubscriptions kho;

    @BeforeEach
    void dungBoiCanh() {
        danhBa = new FakeRecipientDirectory().linkAccount(SUB, TOI);
        kho = new StubPushSubscriptions();
        TestSecurity.loginAs(SUB, "ADMIN");
    }

    @AfterEach
    void dongPhien() {
        TestSecurity.logout();
    }

    @Test
    @DisplayName("Gui dung cho CHINH NGUOI BAM, kenh WEBPUSH, loai SYSTEM")
    void guiChoChinhMinh() {
        kho.them(1);
        RecordingProvider webPush = RecordingProvider.alwaysSent(Channel.WEBPUSH);

        WebPushTestSendView ketQua = service(webPush).sendToSelf();

        assertThat(ketQua.sent()).isTrue();
        assertThat(webPush.sendCount()).isEqualTo(1);
        NotificationMessage daGui = webPush.sent().get(0);
        assertThat(daGui.recipient()).isEqualTo(TOI);
        assertThat(daGui.channel()).isEqualTo(Channel.WEBPUSH);
        // SYSTEM chứ không REMINDER: đây là phép đo hạ tầng, không phải một lời nhắc giỗ.
        assertThat(daGui.category()).isEqualTo(NotificationCategory.SYSTEM);
        // Không có khoá chống trùng: hai lần bấm thử phải là hai lần gửi thật.
        assertThat(daGui.reminderJobId()).isNull();
        assertThat(daGui.hasIdempotencyKey()).isFalse();
    }

    @Test
    @DisplayName("Push service tu choi -> bao PERMANENT nguyen ven, KHONG nuot thanh 'da gui'")
    void loiVinhVienPhaiKeuTo() {
        kho.them(1);
        RecordingProvider webPush = RecordingProvider.always(Channel.WEBPUSH,
                DeliveryResult.permanent("Push service tu choi (HTTP 401)"));

        WebPushTestSendView ketQua = service(webPush).sendToSelf();

        assertThat(ketQua.sent()).isFalse();
        assertThat(ketQua.outcome()).isEqualTo("PERMANENT");
        // Mã HTTP phải đi tới tận người bấm nút: 401 là dấu hiệu gần như chắc chắn của VAPID sai.
        assertThat(ketQua.detail()).contains("401");
    }

    @Test
    @DisplayName("Chua co thiet bi nao -> deviceCount=0, ly do hien ra thay vi im lang")
    void khongCoThietBiNao() {
        RecordingProvider webPush = RecordingProvider.always(Channel.WEBPUSH,
                DeliveryResult.skipped("Nguoi nhan chua dang ky thiet bi nao"));

        WebPushTestSendView ketQua = service(webPush).sendToSelf();

        assertThat(ketQua.deviceCount()).isZero();
        assertThat(ketQua.sent()).isFalse();
        assertThat(ketQua.outcome()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("Dem dung so thiet bi dang hoat dong cua nguoi goi")
    void demThietBi() {
        kho.them(3);

        assertThat(service(RecordingProvider.alwaysSent(Channel.WEBPUSH)).sendToSelf().deviceCount())
                .isEqualTo(3);
    }

    private WebPushTestSendService service(RecordingProvider webPush) {
        return new WebPushTestSendService(new CurrentAccountService(danhBa), kho,
                List.of(RecordingProvider.alwaysSent(Channel.INAPP), webPush));
    }

    /** Kho đăng ký tối giản: bài test này chỉ quan tâm "người gọi có mấy thiết bị". */
    private static final class StubPushSubscriptions implements PushSubscriptionRepository {

        private final List<PushSubscription> thietBi = new ArrayList<>();

        void them(int soLuong) {
            for (int i = 0; i < soLuong; i++) {
                thietBi.add(new PushSubscription(UUID.randomUUID(), APP_USER,
                        "https://fcm.googleapis.com/fcm/send/thiet-bi-" + i, "p256dh", "auth",
                        "test", true, 0, null, null, Instant.now()));
            }
        }

        @Override
        public Upsert save(UUID appUserId, String endpoint, String p256dh, String auth,
                           String userAgent, Instant expiresAt) {
            throw new UnsupportedOperationException("Khong dung trong bai test nay");
        }

        @Override
        public List<PushSubscription> activeByAppUser(UUID appUserId) {
            return APP_USER.equals(appUserId) ? List.copyOf(thietBi) : List.of();
        }

        @Override
        public Optional<PushSubscription> findOwned(UUID id, UUID appUserId) {
            return Optional.empty();
        }

        @Override
        public boolean deleteOwned(UUID id, UUID appUserId) {
            return false;
        }

        @Override
        public boolean deleteByEndpoint(String endpoint) {
            return false;
        }

        @Override
        public void touchLastUsed(UUID id, Instant when) {
            // không cần cho bài test này
        }

        @Override
        public boolean recordFailure(UUID id) {
            return false;
        }
    }
}

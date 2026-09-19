package vn.giapha.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import vn.giapha.notification.domain.PushSubscription;
import vn.giapha.notification.domain.port.PushSubscriptionRepository;

/**
 * Hai lỗ hổng của Web Push, đo trên PostgreSQL thật: <b>ngưỡng lỗi</b> và <b>hạn dùng</b>.
 *
 * <h2>Vì sao không test bằng mock</h2>
 * Cả hai khoản nợ đều nằm <b>trong chuỗi SQL</b>, không nằm trong logic Java. Ngưỡng lỗi là biểu
 * thức {@code is_active = (failure_count + 1 < :nguong)} — nó phụ thuộc vào việc vế phải của
 * {@code SET} đọc giá trị <i>cũ</i> của dòng, một quy tắc của PostgreSQL mà không mock nào mô phỏng
 * được. Hạn dùng là mệnh đề {@code expires_at > now()} trong câu {@code SELECT}. Mock hai thứ này
 * chỉ chứng minh rằng mock hoạt động.
 *
 * <h2>Cả hai chiều đều phải xanh</h2>
 * Test "đủ ngưỡng thì tắt" một mình là chưa đủ: một câu SQL tắt đăng ký ngay từ lần lỗi đầu tiên
 * cũng làm nó xanh, mà hành vi ấy sẽ khiến cả họ mất thông báo giỗ vì một phút chập chờn của push
 * service. Vì vậy luôn có ca đối chứng "chưa đủ ngưỡng thì KHÔNG tắt".
 */
@DisplayName("Vòng đời đăng ký Web Push: ngưỡng lỗi + hạn dùng")
@EnabledIf("vn.giapha.integration.AbstractIntegrationTest#dockerAvailable")
class PushSubscriptionLifecycleIT extends AbstractIntegrationTest {

    private static final String P256DH = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";
    private static final String AUTH = "FPssNDTKnInHVndSTdbKFw";

    @Autowired
    private PushSubscriptionRepository subscriptions;

    private UUID appUserId;

    @BeforeEach
    void taoTaiKhoan() {
        appUserId = insertAppUser("nguoi-trong-ho", null);
    }

    private PushSubscription dangKy(String endpoint, Instant expiresAt) {
        return subscriptions.save(appUserId, endpoint, P256DH, AUTH, "Chrome/131", expiresAt)
                .subscription();
    }

    private boolean conHoatDong(UUID id) {
        Boolean active = jdbc.queryForObject(
                "SELECT is_active FROM push_subscription WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(active);
    }

    private int soLoi(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT failure_count FROM push_subscription WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    // =====================================================================================
    // Chiều 1 — CHƯA đủ ngưỡng thì KHÔNG được tắt
    // =====================================================================================

    @Test
    @DisplayName("dưới ngưỡng: đếm lỗi tăng nhưng đăng ký vẫn hoạt động và vẫn được gửi")
    void duoiNguongThiKhongTat() {
        PushSubscription dangKy = dangKy("https://fcm.example.test/chap-chon", null);

        for (int lan = 1; lan < PushSubscriptionRepository.NGUONG_LOI_LIEN_TIEP; lan++) {
            boolean vuaTat = subscriptions.recordFailure(dangKy.id());
            assertThat(vuaTat)
                    .as("Lan loi thu %d chua duoc phep tat dang ky", lan)
                    .isFalse();
        }

        assertThat(soLoi(dangKy.id()))
                .isEqualTo(PushSubscriptionRepository.NGUONG_LOI_LIEN_TIEP - 1);
        assertThat(conHoatDong(dangKy.id()))
                .as("Tat ngay tu vai lan 5xx la cach chac chan nhat de ca ho mat nhac gio")
                .isTrue();
        assertThat(subscriptions.activeByAppUser(appUserId))
                .extracting(PushSubscription::id)
                .contains(dangKy.id());
    }

    // =====================================================================================
    // Chiều 2 — ĐỦ ngưỡng thì tắt
    // =====================================================================================

    @Test
    @DisplayName("đủ ngưỡng: is_active = FALSE, biến khỏi danh sách gửi, và chỉ báo tắt ĐÚNG một lần")
    void duNguongThiTat() {
        PushSubscription dangKy = dangKy("https://fcm.example.test/chet-han", null);

        boolean tatOLanCuoi = false;
        for (int lan = 1; lan <= PushSubscriptionRepository.NGUONG_LOI_LIEN_TIEP; lan++) {
            tatOLanCuoi = subscriptions.recordFailure(dangKy.id());
        }

        assertThat(tatOLanCuoi)
                .as("Dung lan cham nguong phai bao true de co cho ma ghi log/canh bao")
                .isTrue();
        assertThat(conHoatDong(dangKy.id())).isFalse();
        assertThat(subscriptions.activeByAppUser(appUserId))
                .as("Mot endpoint chet ma khong ai tat se bi goi lai vao moi mua gio, mai mai")
                .isEmpty();

        assertThat(subscriptions.recordFailure(dangKy.id()))
                .as("Da tat roi thi khong duoc bao 'vua tat' them lan nua")
                .isFalse();
        assertThat(countRows("push_subscription"))
                .as("Tat co chu y khac xoa: ban ghi phai con de man quan ly thiet bi giai thich duoc")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("đăng ký lại cùng endpoint thì bật lại và đặt bộ đếm về 0")
    void dangKyLaiThiBatLai() {
        PushSubscription dangKy = dangKy("https://fcm.example.test/dang-ky-lai", null);
        for (int lan = 1; lan <= PushSubscriptionRepository.NGUONG_LOI_LIEN_TIEP; lan++) {
            subscriptions.recordFailure(dangKy.id());
        }
        assertThat(conHoatDong(dangKy.id())).isFalse();

        PushSubscription lai = dangKy("https://fcm.example.test/dang-ky-lai", null);

        assertThat(lai.id())
                .as("endpoint la khoa dinh danh chuan Web Push nen phai la CAP NHAT, khong tao dong moi")
                .isEqualTo(dangKy.id());
        assertThat(conHoatDong(dangKy.id())).isTrue();
        assertThat(soLoi(dangKy.id())).isZero();
        assertThat(subscriptions.activeByAppUser(appUserId)).hasSize(1);
    }

    @Test
    @DisplayName("gửi thành công đặt lại bộ đếm — nên ngưỡng đếm lỗi LIÊN TIẾP")
    void guiThanhCongDatLaiBoDem() {
        PushSubscription dangKy = dangKy("https://fcm.example.test/chap-chon-roi-khoi", null);

        for (int lan = 1; lan < PushSubscriptionRepository.NGUONG_LOI_LIEN_TIEP; lan++) {
            subscriptions.recordFailure(dangKy.id());
        }
        subscriptions.touchLastUsed(dangKy.id(), Instant.now());
        assertThat(soLoi(dangKy.id())).isZero();

        assertThat(subscriptions.recordFailure(dangKy.id()))
                .as("Sau mot lan thanh cong thi chuoi loi bat dau lai tu dau")
                .isFalse();
        assertThat(conHoatDong(dangKy.id())).isTrue();
    }

    // =====================================================================================
    // expires_at — trước đây không câu truy vấn nào đọc tới
    // =====================================================================================

    @Test
    @DisplayName("đăng ký đã quá hạn không còn nằm trong danh sách gửi")
    void quaHanThiKhongGuiNua() {
        PushSubscription hetHan = dangKy("https://fcm.example.test/het-han",
                Instant.now().minus(1, ChronoUnit.DAYS));
        PushSubscription conHan = dangKy("https://fcm.example.test/con-han",
                Instant.now().plus(30, ChronoUnit.DAYS));
        PushSubscription khongHan = dangKy("https://fcm.example.test/khong-han", null);

        assertThat(conHoatDong(hetHan.id()))
                .as("Qua han khong phai la loi cua thiet bi nen co is_active van bat")
                .isTrue();
        assertThat(subscriptions.activeByAppUser(appUserId))
                .extracting(PushSubscription::id)
                .as("Bo qua expires_at nghia la cu gui vao endpoint da het han cho toi khi nhan 410 —"
                        + " ma co push service khong bao gio tra 410 cho truong hop nay")
                .containsExactlyInAnyOrder(conHan.id(), khongHan.id())
                .doesNotContain(hetHan.id());
    }

    @Test
    @DisplayName("đăng ký lại một endpoint đã quá hạn thì hạn mới có hiệu lực ngay")
    void dangKyLaiThiHanMoiCoHieuLuc() {
        String endpoint = "https://fcm.example.test/gia-han";
        dangKy(endpoint, Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(subscriptions.activeByAppUser(appUserId)).isEmpty();

        PushSubscription giaHan = dangKy(endpoint, Instant.now().plus(90, ChronoUnit.DAYS));

        assertThat(subscriptions.activeByAppUser(appUserId))
                .extracting(PushSubscription::id)
                .containsExactly(giaHan.id());
    }
}

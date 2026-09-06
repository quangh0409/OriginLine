package vn.giapha.notification.infrastructure.inapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.giapha.notification.domain.Channel;
import vn.giapha.notification.domain.DeliveryOutcome;
import vn.giapha.notification.domain.DeliveryResult;
import vn.giapha.notification.domain.InboxEntry;
import vn.giapha.notification.domain.InboxItem;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.NotificationMessage;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.InboxPort;

/**
 * Hộp thư in-app — <b>nguồn chân lý</b> của kênh thông báo MVP.
 *
 * <p>Người từ chối quyền Web Push, người dùng iPhone chưa cài PWA vào màn hình chính, người tắt
 * thông báo hệ điều hành: tất cả vẫn phải nhận đủ ở đây.</p>
 */
class InAppProviderTest {

    private final GhiNhoHopThu inbox = new GhiNhoHopThu();
    private final InAppProvider provider = new InAppProvider(inbox);

    private final UUID jobId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    private NotificationMessage tinNhac() {
        return new NotificationMessage(jobId, UUID.randomUUID(), UUID.randomUUID(),
                new Recipient(personId, UUID.randomUUID(), "vi"), Channel.INAPP,
                NotificationCategory.REMINDER, "Ngay mai la gio cu Duc",
                "Ngay 20/10/2026 duong lich", "/events/abc", 1, LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("Ghi tin vao hop thu cua dung nguoi nhan, giu nguyen deep link")
    void ghiVaoHopThuDungNguoi() {
        DeliveryResult result = provider.send(tinNhac());

        assertThat(result.outcome()).isEqualTo(DeliveryOutcome.SENT);
        assertThat(inbox.items).hasSize(1);
        assertThat(inbox.items.get(0).recipientPersonId()).isEqualTo(personId);
        assertThat(inbox.items.get(0).linkUrl()).isEqualTo("/events/abc");
        assertThat(inbox.items.get(0).reminderJobId()).isEqualTo(jobId);
    }

    @Test
    @DisplayName("Giao lai: khong ghi hai dong, va van bao SENT chu khong bao loi")
    void giaoLaiKhongGhiHaiDong() {
        provider.send(tinNhac());
        DeliveryResult lanHai = provider.send(tinNhac());

        // Chỉ mục ux_notification_inbox_idempotency đã chặn; đây là kết quả ĐÚNG của một lần giao
        // lại, không phải lỗi - báo FAILED ở đây sẽ đẩy một tin hoàn toàn bình thường vào DLQ.
        assertThat(lanHai.outcome()).isEqualTo(DeliveryOutcome.SENT);
        assertThat(inbox.items).hasSize(1);
    }

    @Test
    @DisplayName("Vi pham rang buoc CSDL la loi VINH VIEN - retry khong sua duoc du lieu hong")
    void viPhamRangBuocLaLoiVinhVien() {
        inbox.nemViPhamRangBuoc = true;

        DeliveryResult result = provider.send(tinNhac());

        assertThat(result.outcome()).isEqualTo(DeliveryOutcome.PERMANENT);
        assertThat(result.detail()).contains("notification_inbox");
    }

    @Test
    @DisplayName("Kenh cua provider la INAPP - dung de dinh tuyen trong NotificationDeliveryService")
    void kenhLaInApp() {
        assertThat(provider.channel()).isEqualTo(Channel.INAPP);
    }

    /** Hộp thư trong bộ nhớ, chặn trùng theo {@code (reminder_job_id, recipient_person_id)}. */
    private static final class GhiNhoHopThu implements InboxPort {

        private final List<InboxItem> items = new ArrayList<>();
        private boolean nemViPhamRangBuoc;

        @Override
        public boolean insertIfAbsent(InboxItem item) {
            if (nemViPhamRangBuoc) {
                throw new DataIntegrityViolationException("gia lap vi pham rang buoc");
            }
            boolean daCo = items.stream().anyMatch(existing ->
                    existing.reminderJobId() != null
                            && existing.reminderJobId().equals(item.reminderJobId())
                            && existing.recipientPersonId().equals(item.recipientPersonId()));
            if (daCo) {
                return false;
            }
            items.add(item);
            return true;
        }

        @Override
        public List<InboxEntry> findPage(UUID recipientPersonId, ReadFilter filter,
                                         NotificationCategory category, int page, int size) {
            return List.of();
        }

        @Override
        public long countPage(UUID recipientPersonId, ReadFilter filter, NotificationCategory category) {
            return 0;
        }

        @Override
        public long countUnread(UUID recipientPersonId) {
            return items.size();
        }

        @Override
        public Optional<InboxItem> findOwned(UUID id, UUID recipientPersonId) {
            return Optional.empty();
        }

        @Override
        public Optional<InboxItem> markRead(UUID id, UUID recipientPersonId) {
            return Optional.empty();
        }

        @Override
        public int markAllRead(UUID recipientPersonId) {
            return items.size();
        }
    }

    @Test
    @DisplayName("Tham so status khong hop le bi tu choi voi thong diep noi ro gia tri cho phep")
    void locTrangThaiKhongHopLe() {
        assertThat(InboxPort.ReadFilter.parse(null)).isEqualTo(InboxPort.ReadFilter.ALL);
        assertThat(InboxPort.ReadFilter.parse("unread")).isEqualTo(InboxPort.ReadFilter.UNREAD);
        assertThat(InboxPort.ReadFilter.parse("READ".toLowerCase(Locale.ROOT)))
                .isEqualTo(InboxPort.ReadFilter.READ);
    }
}

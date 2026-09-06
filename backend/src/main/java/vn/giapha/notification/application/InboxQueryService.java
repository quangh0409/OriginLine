package vn.giapha.notification.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.notification.application.view.NotificationPageView;
import vn.giapha.notification.application.view.NotificationReadAllResultView;
import vn.giapha.notification.application.view.NotificationReadResultView;
import vn.giapha.notification.application.view.NotificationView;
import vn.giapha.notification.application.view.PageMetaView;
import vn.giapha.notification.domain.InboxEntry;
import vn.giapha.notification.domain.InboxItem;
import vn.giapha.notification.domain.NotificationCategory;
import vn.giapha.notification.domain.Recipient;
import vn.giapha.notification.domain.port.InboxPort;
import vn.giapha.shared.exception.NotFoundException;

/**
 * Hộp thư in-app của <b>chính người đang đăng nhập</b> — {@code GET /api/v1/notifications} và
 * {@code POST /api/v1/notifications/{id}/read}.
 *
 * <p>In-app là <b>nguồn chân lý</b> của kênh thông báo MVP: người từ chối quyền Web Push vẫn nhận
 * đủ ở đây. Vì thế đây cũng là màn hình phải luôn đúng — badge sai một con số thì người dùng thôi
 * không tin cái chuông nữa.</p>
 */
@Service
public class InboxQueryService {

    private static final Logger log = LoggerFactory.getLogger(InboxQueryService.class);

    /** Chặn trên kích thước trang, khớp tham số {@code size} của OpenAPI. */
    private static final int MAX_PAGE_SIZE = 100;

    private final InboxPort inbox;
    private final CurrentAccountService currentAccount;

    public InboxQueryService(InboxPort inbox, CurrentAccountService currentAccount) {
        this.inbox = inbox;
        this.currentAccount = currentAccount;
    }

    /**
     * @param status   {@code ALL} / {@code UNREAD} / {@code READ}
     * @param category lọc theo loại; {@code null} = tất cả
     */
    @Transactional(readOnly = true)
    public NotificationPageView list(String status, NotificationCategory category, int page, int size,
                                     String sort) {
        Recipient me = currentAccount.require();
        InboxPort.ReadFilter filter = InboxPort.ReadFilter.parse(status);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);

        List<InboxEntry> entries = inbox.findPage(me.personId(), filter, category, safePage, safeSize);
        long total = inbox.countPage(me.personId(), filter, category);
        // Số chưa đọc của TOÀN hộp thư, cố ý không theo bộ lọc: badge chuông hiện trên mọi màn hình
        // nên nó không được nhảy theo tab mà người dùng đang mở.
        long unread = inbox.countUnread(me.personId());

        List<NotificationView> items = new ArrayList<>(entries.size());
        for (InboxEntry entry : entries) {
            items.add(toView(entry));
        }
        return new NotificationPageView(List.copyOf(items),
                PageMetaView.of(safePage, safeSize, total, sort), unread);
    }

    /**
     * Đánh dấu đã đọc — <b>idempotent</b>: gọi lại trên tin đã đọc vẫn trả 200 với {@code readAt}
     * giữ nguyên lần đầu. Giao diện cứ gọi thoải mái, không cần kiểm tra trạng thái trước.
     *
     * <p>Tin của người khác trả <b>404 chứ không 403</b>, theo đúng hợp đồng: 403 xác nhận rằng id
     * đó có tồn tại, và đó là một rò rỉ nhỏ nhưng thật.</p>
     */
    @Transactional
    public NotificationReadResultView markRead(UUID notificationId) {
        Recipient me = currentAccount.require();
        Optional<InboxItem> updated = inbox.markRead(notificationId, me.personId());
        InboxItem item = updated.orElseThrow(() -> {
            log.debug("Danh dau da doc that bai: tin {} khong ton tai hoac khong thuoc nguoi {}",
                    notificationId, me.personId());
            return NotFoundException.of("Notification", notificationId);
        });
        return new NotificationReadResultView(item.id(), item.isRead(), item.readAt(),
                inbox.countUnread(me.personId()));
    }

    /**
     * Đánh dấu đã đọc <b>tất cả</b> tin của chính mình — nút "Đọc hết" của trung tâm thông báo.
     *
     * <p><b>Idempotent</b>: gọi lại khi hộp thư đã sạch trả {@code markedCount = 0} chứ không lỗi.
     * Không có tham số phạm vi (danh mục, khoảng thời gian) và cố ý như vậy: một nút "đọc hết" chỉ
     * lọc một phần là nút mà không ai đoán được nó vừa làm gì.</p>
     */
    @Transactional
    public NotificationReadAllResultView markAllRead() {
        Recipient me = currentAccount.require();
        int marked = inbox.markAllRead(me.personId());
        long unread = inbox.countUnread(me.personId());
        log.debug("Danh dau da doc tat ca cho {}: {} tin, con lai {} chua doc",
                me.personId(), marked, unread);
        return new NotificationReadAllResultView(marked, unread);
    }

    private static NotificationView toView(InboxEntry entry) {
        InboxItem item = entry.item();
        return new NotificationView(
                item.id(),
                item.category(),
                item.title(),
                item.body(),
                item.eventId(),
                entry.subjectPersonId(),
                item.linkUrl(),
                item.createdAt(),
                item.isRead(),
                item.readAt(),
                entry.reminderOffsetDays());
    }
}

package vn.giapha.notification.application.view;

import java.time.Instant;
import java.util.UUID;
import vn.giapha.notification.domain.NotificationCategory;

/**
 * Một dòng hộp thư nhìn từ phía API.
 *
 * @param subjectPersonId    nhân khẩu liên quan (người được giỗ), suy từ sự kiện
 * @param reminderOffsetDays mốc nhắc đã sinh ra tin này (7 / 3 / 1); {@code null} với tin không đến
 *                           từ lịch nhắc
 */
public record NotificationView(UUID id,
                               NotificationCategory category,
                               String title,
                               String body,
                               UUID eventId,
                               UUID subjectPersonId,
                               String deepLink,
                               Instant createdAt,
                               boolean read,
                               Instant readAt,
                               Integer reminderOffsetDays) {
}

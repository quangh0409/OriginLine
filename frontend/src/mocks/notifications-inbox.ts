import { getMockEvents } from "./events-calendar";
import { NOTIFICATIONS_MOCK } from "./data";
import type { NotificationDto } from "@/types/api";

/**
 * Builds a plausible in-app inbox out of the generated giỗ calendar, so F7's
 * notification centre has more than two rows and the unread badge, the
 * status/category filters and paging are all exercisable by hand.
 *
 * Reminders are derived from the same events the /events endpoint serves, at
 * the FR-2.2 offsets (7 / 3 / 1 days before), which is what the real
 * ReminderPlan will do. `deepLink` uses the contract's documented
 * `/events/{id}` shape on purpose — that is the form the frontend's
 * `resolveNotificationHref` adapter has to cope with.
 *
 * `body` never contains Tier-3 data: a push notification renders on a locked
 * screen, so the same text must be safe there.
 */

const OFFSETS = [7, 3, 1];

function isoDaysAgo(days: number): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() - days);
  date.setUTCHours(0, 0, 0, 0);
  return date.toISOString();
}

let cached: NotificationDto[] | null = null;

export function getMockNotifications(): NotificationDto[] {
  if (cached) return cached;

  const soon = getMockEvents()
    .filter((e) => (e.daysUntil ?? 999) >= 0 && (e.daysUntil ?? 999) <= 60)
    .slice(0, 4);

  const generated: NotificationDto[] = [];
  for (const event of soon) {
    for (const offset of OFFSETS) {
      const daysUntil = event.daysUntil ?? 0;
      // Only reminders that would already have fired belong in the inbox.
      if (daysUntil > offset) continue;
      generated.push({
        id: `nt-${event.id}-${offset}`,
        category: "GIO_REMINDER",
        title: `Còn ${offset} ngày tới ${event.title ?? "ngày giỗ"}`,
        body: `${event.title ?? "Ngày giỗ"} — ngày ${event.lunarDate.day} tháng ${event.lunarDate.month} âm lịch${event.lunarDate.leap ? " (nhuận)" : ""}.`,
        eventId: event.id,
        personId: event.person?.id ?? null,
        deepLink: `/events/${event.id}`,
        createdAt: isoDaysAgo(Math.max(0, offset - daysUntil)),
        isRead: offset === 7,
        readAt: offset === 7 ? isoDaysAgo(offset - 1) : null,
        reminderOffsetDays: offset,
      });
    }
  }

  // A couple of non-reminder rows so the category filter has something to do.
  generated.push({
    id: "nt-sys-welcome",
    category: "SYSTEM",
    title: "Hộp thư thông báo đã sẵn sàng",
    body: "Mọi lời nhắc giỗ đều hiện ở đây, kể cả khi bạn không bật thông báo đẩy.",
    createdAt: isoDaysAgo(9),
    isRead: false,
    readAt: null,
  });

  const byId = new Map<string, NotificationDto>();
  for (const item of [...generated, ...NOTIFICATIONS_MOCK]) byId.set(item.id, item);

  cached = [...byId.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return cached;
}

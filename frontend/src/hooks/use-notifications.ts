import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationsApi, type ListNotificationsParams } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";
import type { NotificationPage } from "@/types/api";

/**
 * Backs the header bell + unread badge AND the full notification centre (F7).
 *
 * This is the MVP's PRIMARY giỗ-reminder channel and the one guaranteed to
 * work: Web Push is an optional extra layer on top, so a member who denied
 * the browser permission, or whose iPhone PWA was never installed, still sees
 * every reminder here the moment they open the app. Three things make that
 * true and none of them are optional:
 *
 *  - `refetchInterval` — the badge updates while the app sits open, so a
 *    reminder generated at 07:00 doesn't wait for a navigation (plan §7
 *    pitfall #7: "in-app notifications look done but nobody sees the badge").
 *  - `refetchOnWindowFocus` — returning to a backgrounded tab, or reopening
 *    the installed PWA, pulls anything that arrived meanwhile. This is
 *    exactly the "mở app là thấy đủ" path for users without push.
 *  - `refetchOnMount` — a cold start never renders a stale cached inbox.
 *
 * `unreadCount` is the WHOLE-inbox count, filter- and page-independent —
 * never derive the badge by counting `items`, which is one page of a possibly
 * filtered list.
 */
export function useNotifications(params: ListNotificationsParams = {}) {
  return useQuery({
    queryKey: [...queryKeys.notifications(), params],
    queryFn: () => notificationsApi.list(params),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
    refetchOnMount: true,
  });
}

/**
 * Page size for the "is there anything new?" surfaces (header bell, mobile
 * tab badge). Both read the SAME params so they share one cached query and
 * one poll timer instead of quietly running two.
 */
export const NOTIFICATION_PREVIEW_SIZE = 5;

/**
 * Whole-inbox unread count for badges. Comes from the response's
 * `unreadCount`, never from `items.length` — the preview is one short page.
 */
export function useUnreadCount(): number {
  const { data } = useNotifications({ size: NOTIFICATION_PREVIEW_SIZE });
  return data?.unreadCount ?? 0;
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSuccess: (result) => {
      // The bell and the notification centre are separate queries with
      // different filter params. Patch EVERY cached inbox variant, otherwise
      // marking an item read on the page leaves the header badge stale — the
      // exact bug the plan calls out.
      queryClient.setQueriesData<NotificationPage>(
        { queryKey: queryKeys.notifications() },
        (prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            unreadCount: result.unreadCount,
            items: prev.items.map((item) =>
              item.id === result.id ? { ...item, isRead: true, readAt: result.readAt } : item
            ),
          };
        }
      );
      // A list filtered to UNREAD must drop the item, not just restyle it;
      // that needs a refetch rather than a local patch.
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications() });
    },
  });
}

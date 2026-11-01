import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationsApi, type ListNotificationsParams } from "@/lib/api";
import { queryKeys } from "@/lib/query/keys";
import { ApiError } from "@/lib/api/http";
import { MOCKING_ENABLED, getDevRole } from "@/lib/api/dev-role";
import { useAuth } from "@/lib/auth/auth-context";
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
/**
 * Ai đang hỏi hộp thư: `"loading"` khi phiên chưa ngã ngũ, `"guest"` khi chưa đăng nhập.
 *
 * <h2>Vì sao hộp thư phải hỏi câu này TRƯỚC khi gọi mạng (đo 24/09 trên hệ thật)</h2>
 * Chuông ở thanh đầu trang và huy hiệu ở thanh tab đáy dựng cho MỌI người, kể cả khách. Trước đây
 * chúng gọi `GET /notifications?size=5` bất kể ai đang xem: mỗi trang khách mở là một `401` đỏ
 * trong bảng điều khiển trình duyệt và trong nhật ký máy chủ — rồi lặp lại mỗi 60 giây
 * (`refetchInterval`) và mỗi lần quay lại tab, suốt thời gian khách còn mở trang. Hộp thư là của
 * riêng người đăng nhập (không có tham số `userId`), nên với khách câu trả lời đã biết trước.
 *
 * Cùng một quy tắc ba nhánh với `useTreeAudience`: dưới MSW thì vai giả lập quyết định.
 */
export type InboxAccess = "loading" | "guest" | "member";

export function useInboxAccess(): InboxAccess {
  const { status } = useAuth();
  if (MOCKING_ENABLED) return getDevRole() === "guest" ? "guest" : "member";
  if (status === "loading") return "loading";
  return status === "authenticated" ? "member" : "guest";
}

export function useNotifications(params: ListNotificationsParams = {}) {
  const access = useInboxAccess();
  return useQuery({
    queryKey: [...queryKeys.notifications(), params],
    queryFn: () => notificationsApi.list(params),
    enabled: access === "member",
    // Máy chủ đã TỪ CHỐI dứt khoát (4xx: chưa ghép nhân khẩu — `ACCOUNT_NOT_LINKED` — hay phiên
    // hết hạn) thì thôi hỏi lại mỗi phút: câu trả lời sẽ y hệt. Đo 24/09: một tài khoản chưa ghép
    // phả để mở trang sinh ra một `403` mỗi 60 giây trong nhật ký máy chủ, không dừng. Lỗi 5xx
    // vẫn hỏi lại như cũ — đó mới là trục trặc tạm thời mà "mở app là thấy đủ" cần vượt qua.
    refetchInterval: (query) => inboxRefetchInterval(query.state.error),
    refetchOnWindowFocus: (query) => !isFinalRefusal(query.state.error),
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
/** Lỗi mà hỏi lại cũng không đổi được câu trả lời: 4xx từ máy chủ. */
export function isFinalRefusal(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 400 && error.status < 500;
}

/** Nhịp hỏi lại hộp thư: 60 giây, trừ khi máy chủ đã từ chối dứt khoát (xem `isFinalRefusal`). */
export function inboxRefetchInterval(error: unknown): number | false {
  return isFinalRefusal(error) ? false : 60_000;
}

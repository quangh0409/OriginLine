import { http, HttpResponse } from "msw";
import { API_BASE_URL } from "@/lib/api/http";
import { getMockNotifications } from "@/mocks/notifications-inbox";
import { resolveMockRole } from "./role";
import type {
  NotificationCategory,
  NotificationDto,
  NotificationPage,
  NotificationReadResult,
  Problem,
} from "@/types/api";

// Mutable copy so POST /:id/read has an observable effect within a session.
let notifications: NotificationDto[] | null = null;

function inbox(): NotificationDto[] {
  if (!notifications) notifications = getMockNotifications().map((n) => ({ ...n }));
  return notifications;
}

export const notificationHandlers = [
  http.get(`${API_BASE_URL}/api/v1/notifications`, ({ request }) => {
    // Hộp thư là của riêng một người — khách không có danh tính để sở hữu hộp
    // thư nào. Máy thật trả `401 UNAUTHENTICATED` (đo 24/09 trên 8088). Bản
    // trước của mock tự ghi đúng điều ấy rồi vẫn trả một hộp thư RỖNG `200` —
    // "dễ hơn thật": giao diện được kiểm với "Chưa có thông báo nào" trong khi
    // khách trên máy thật thấy dải đỏ "Không tải được". Nay trả đúng `401`.
    const role = resolveMockRole(request);
    if (role === "guest") {
      const body: Problem = {
        type: "https://giapha.vn/problems/unauthorized",
        title: "Chưa đăng nhập",
        status: 401,
        detail: "Yêu cầu cần một JWT hợp lệ do Keycloak cấp",
        instance: "/api/v1/notifications",
        code: "UNAUTHENTICATED",
      };
      return HttpResponse.json(body, {
        status: 401,
        headers: { "Content-Type": "application/problem+json" },
      });
    }
    const all = inbox();

    const url = new URL(request.url);
    const status = url.searchParams.get("status") ?? "ALL";
    const category = url.searchParams.get("category") as NotificationCategory | null;
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");

    let items = all;
    if (status === "UNREAD") items = items.filter((n) => !n.isRead);
    if (status === "READ") items = items.filter((n) => n.isRead);
    if (category) items = items.filter((n) => n.category === category);

    const [, sortDir] = (url.searchParams.get("sort") ?? "createdAt,desc").split(",");
    items = [...items].sort((a, b) =>
      sortDir === "asc"
        ? a.createdAt.localeCompare(b.createdAt)
        : b.createdAt.localeCompare(a.createdAt)
    );

    const start = page * size;

    const response: NotificationPage = {
      items: items.slice(start, start + size),
      page: {
        page,
        size,
        totalElements: items.length,
        totalPages: Math.max(1, Math.ceil(items.length / size)),
        hasNext: start + size < items.length,
        sort: `createdAt,${sortDir === "asc" ? "asc" : "desc"}`,
      },
      // Whole-inbox count, deliberately computed from `all` and NOT from the
      // filtered/paged `items` — the bell badge must not change just because
      // the page happens to be filtered to READ (contracts/openapi.yaml).
      unreadCount: all.filter((n) => !n.isRead).length,
    };
    return HttpResponse.json(response);
  }),

  http.post(`${API_BASE_URL}/api/v1/notifications/:id/read`, ({ params }) => {
    const id = params.id as string;
    const target = inbox().find((n) => n.id === id);
    if (!target) {
      const problem: Problem = {
        type: "about:blank",
        title: "Không tìm thấy thông báo",
        status: 404,
        code: "NOT_FOUND",
        instance: `/api/v1/notifications/${id}/read`,
      };
      return HttpResponse.json(problem, { status: 404 });
    }
    // Idempotent: readAt is set only on first read.
    if (!target.isRead) {
      target.isRead = true;
      target.readAt = new Date().toISOString();
    }
    const result: NotificationReadResult = {
      id: target.id,
      isRead: true,
      readAt: target.readAt as string,
      unreadCount: inbox().filter((n) => !n.isRead).length,
    };
    return HttpResponse.json(result);
  }),
];

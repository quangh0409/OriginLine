/// <reference lib="webworker" />
export {};
declare const self: ServiceWorkerGlobalScope;

/**
 * Custom service worker additions, merged into the @ducanh2912/next-pwa
 * generated public/sw.js at build time (see next.config.js
 * `customWorkerDir`).
 *
 * HỢP ĐỒNG PAYLOAD — đối chiếu với `WebPushAdapter.buildPayload()` ở backend.
 * Backend phát đúng năm khoá: `title`, `body`, `url`, `tag`, `eventId`. Kiểu
 * bên dưới phải là bản sao của đúng năm khoá ấy; lệch một khoá là im lặng mất
 * một tính năng, không phải lỗi biên dịch.
 */

interface GiaPhaPushPayload {
  title: string;
  body: string;
  /** Deep link to open on click, e.g. an event or person profile. */
  url?: string;
  /**
   * Khoá gộp thông báo của HỆ ĐIỀU HÀNH.
   *
   * <p>Backend phát khoá này (`"gio-" + reminderJobId`) để các lượt nhắc của cùng
   * một lịch giỗ gộp lại thành MỘT thông báo thay vì chồng đống trên màn hình
   * khoá. Trước đợt sửa này worker không đọc nó, nên ý định ấy chưa bao giờ xảy
   * ra: người dùng nhận ba thông báo riêng cho cùng một ngày giỗ.</p>
   *
   * <p>Không đặt giá trị dự phòng. `tag` là tuỳ chọn ở backend (chỉ có khi
   * `reminderJobId != null`), và bịa ra một tag chung sẽ gộp NHẦM hai giỗ khác
   * nhau thành một — thông báo sau đè mất thông báo trước. Thiếu tag thì để hệ
   * điều hành xử lý như thông báo độc lập, đúng như hôm nay.</p>
   */
  tag?: string;
  /**
   * Định danh sự kiện, để mã trong trang biết thông báo vừa mở là của giỗ nào.
   *
   * <p>Trước đây chỗ này khai `notificationId` — một khoá backend KHÔNG BAO GIỜ
   * phát, nên `notification.data.notificationId` luôn là `undefined`. Sửa ở phía
   * worker chứ không bắt backend phát thêm: backend hiện không có định danh nào
   * đúng nghĩa "notification id" để mà phát, còn `eventId` thì có thật.</p>
   */
  eventId?: string;
}

self.addEventListener("push", (event: PushEvent) => {
  let payload: GiaPhaPushPayload = {
    title: "Nhắc giỗ",
    body: "Bạn có một thông báo mới từ dòng họ.",
  };

  if (event.data) {
    try {
      payload = { ...payload, ...event.data.json() };
    } catch {
      // Fall back to a plain-text body if the payload isn't JSON.
      payload = { ...payload, body: event.data.text() };
    }
  }

  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: "/icons/icon-192.png",
      badge: "/icons/badge-72.png",
      // `tag` chỉ được truyền khi backend có gửi: `undefined` là "không gộp",
      // còn một chuỗi rỗng sẽ gộp MỌI thông báo lại thành một.
      ...(payload.tag ? { tag: payload.tag } : {}),
      data: { url: payload.url ?? "/", eventId: payload.eventId },
    })
  );
});

self.addEventListener("notificationclick", (event: NotificationEvent) => {
  event.notification.close();
  const targetUrl = (event.notification.data as { url?: string } | undefined)?.url ?? "/";

  event.waitUntil(
    self.clients
      .matchAll({ type: "window", includeUncontrolled: true })
      .then((clientList) => {
        for (const client of clientList) {
          if (client.url === targetUrl && "focus" in client) {
            return client.focus();
          }
        }
        if (self.clients.openWindow) {
          return self.clients.openWindow(targetUrl);
        }
      })
  );
});

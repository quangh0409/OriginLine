import { describe, expect, it } from "vitest";
import { resolveNotificationHref } from "@/lib/format/notification-link";
import type { NotificationDto } from "@/types/api";

/**
 * F7 — chỗ nối giữa `deepLink` do máy chủ soạn và các tuyến đường mà ứng
 * dụng này thật sự có.
 *
 * Hợp đồng ghi ví dụ `/events/{id}`, nhưng frontend không có trang riêng cho
 * một sự kiện (hợp đồng cũng không có `GET /events/{id}` để dựng trang đó),
 * nên liên kết được viết lại thành màn hình sự kiện có chọn sẵn. Nếu không có
 * lớp chuyển đổi này, mỗi lời nhắc giỗ sẽ dẫn người dùng tới một trang 404.
 *
 * Điểm bảo mật: chỉ đi theo đường dẫn tương đối cùng nguồn. Một thông báo
 * không phải là nơi để nhận URL tuyệt đối từ bên ngoài.
 */

function thongBao(overrides: Partial<NotificationDto> = {}): NotificationDto {
  return {
    id: "nt-1",
    category: "GIO_REMINDER",
    title: "Còn 3 ngày tới giỗ Thủy Tổ",
    createdAt: "2026-08-30T00:00:00.000Z",
    isRead: false,
    ...overrides,
  };
}

describe("liên kết sâu từ một lời nhắc", () => {
  it("đổi /events/{id} thành màn hình sự kiện có chọn sẵn sự kiện đó", () => {
    const href = resolveNotificationHref(thongBao({ deepLink: "/events/ev-42" }));

    expect(href).toBe("/events?event=ev-42");
  });

  it("mã hoá id sự kiện để ký tự lạ không phá vỡ chuỗi truy vấn", () => {
    const href = resolveNotificationHref(thongBao({ deepLink: "/events/ev 42&x=1" }));

    expect(href).toBe("/events?event=ev%2042%26x%3D1");
  });

  it("giữ nguyên các đường dẫn tương đối khác", () => {
    expect(resolveNotificationHref(thongBao({ deepLink: "/persons/p-001" }))).toBe(
      "/persons/p-001"
    );
    expect(resolveNotificationHref(thongBao({ deepLink: "/notifications" }))).toBe(
      "/notifications"
    );
  });

  it("từ chối URL tuyệt đối — không mở trang ngoài từ một thông báo", () => {
    const href = resolveNotificationHref(
      thongBao({ deepLink: "https://ke-xau.example/phishing", eventId: null, personId: null })
    );

    expect(href).toBeUndefined();
  });

  it("từ chối cả URL tuyệt đối trỏ về chính miền của mình", () => {
    const href = resolveNotificationHref(
      thongBao({ deepLink: "https://giapha.example.vn/events/ev-9" })
    );

    // Không có eventId/personId để lùi về, nên không có liên kết nào.
    expect(href).toBeUndefined();
  });
});

describe("khi không có deepLink", () => {
  it("dùng eventId để dựng liên kết tới lịch giỗ", () => {
    expect(resolveNotificationHref(thongBao({ eventId: "ev-7" }))).toBe("/events?event=ev-7");
  });

  it("dùng personId khi thông báo nói về một nhân khẩu", () => {
    expect(resolveNotificationHref(thongBao({ personId: "p-010" }))).toBe("/persons/p-010");
  });

  it("ưu tiên eventId hơn personId — lời nhắc giỗ nói về NGÀY, không phải người", () => {
    const href = resolveNotificationHref(thongBao({ eventId: "ev-7", personId: "p-010" }));

    expect(href).toBe("/events?event=ev-7");
  });

  it("thông báo hệ thống không có đích đến thì không bọc thẻ liên kết", () => {
    const href = resolveNotificationHref(
      thongBao({ category: "SYSTEM", title: "Hộp thư đã sẵn sàng" })
    );

    expect(href).toBeUndefined();
  });

  it("chuỗi rỗng cũng bị coi là vắng mặt, không tạo ra liên kết '/'", () => {
    const href = resolveNotificationHref(thongBao({ deepLink: "  ", eventId: "" }));

    expect(href).toBeUndefined();
  });
});

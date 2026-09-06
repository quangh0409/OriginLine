import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import type { NotificationDto } from "@/types/api";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/notifications",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/notifications",
}));

const { NotificationCenter } = await import("@/components/notifications/notification-center");
const { NotificationItem } = await import("@/components/notifications/notification-item");

/**
 * F7 — trung tâm thông báo.
 *
 * Đây là kênh nhắc giỗ CHÍNH và là kênh duy nhất chắc chắn hoạt động của bản
 * MVP: Web Push chỉ là lớp phụ. Một thành viên đã từ chối quyền thông báo,
 * hoặc dùng iPhone chưa cài ứng dụng ra màn hình chính, vẫn phải thấy đủ mọi
 * lời nhắc ở đây ngay khi mở ứng dụng. Vì vậy không có gì trên màn hình này
 * được phụ thuộc vào push.
 *
 * `unreadCount` đến từ phản hồi của máy chủ, tính trên TOÀN hộp thư — không
 * bao giờ đếm từ `items`, vì `items` chỉ là một trang đã lọc.
 */

function thongBao(overrides: Partial<NotificationDto> = {}): NotificationDto {
  return {
    id: "nt-test",
    category: "GIO_REMINDER",
    title: "Còn 3 ngày tới giỗ Thủy tổ",
    body: "Giỗ Thủy tổ Nguyễn Văn Thủy Tổ (22/9 âm lịch) còn 3 ngày nữa.",
    eventId: "ev-001",
    deepLink: "/events/ev-001",
    createdAt: "2026-08-29T01:00:00+07:00",
    isRead: false,
    readAt: null,
    reminderOffsetDays: 3,
    ...overrides,
  };
}

beforeEach(() => {
  resetRouterMock();
});

describe("một dòng thông báo", () => {
  it("hiện tiêu đề và nội dung máy chủ gửi, không tự thêm gì", () => {
    const notification = thongBao();
    renderWithProviders(
      <ul>
        <NotificationItem notification={notification} onMarkRead={vi.fn()} />
      </ul>,
      { role: "member" }
    );

    expect(screen.getByText(notification.title)).toBeInTheDocument();
    expect(screen.getByText(notification.body!)).toBeInTheDocument();
  });

  it("gắn nhãn mốc nhắc — 'Nhắc trước 3 ngày' (FR-2.2)", () => {
    renderWithProviders(
      <ul>
        <NotificationItem notification={thongBao({ reminderOffsetDays: 3 })} onMarkRead={vi.fn()} />
      </ul>,
      { role: "member" }
    );

    expect(screen.getByText("Nhắc trước 3 ngày")).toBeInTheDocument();
  });

  it("nhận đủ cả ba mốc 7 / 3 / 1 ngày", () => {
    for (const offset of [7, 3, 1]) {
      const { unmount } = renderWithProviders(
        <ul>
          <NotificationItem
            notification={thongBao({ id: `nt-${offset}`, reminderOffsetDays: offset })}
            onMarkRead={vi.fn()}
          />
        </ul>,
        { role: "member" }
      );
      expect(screen.getByText(`Nhắc trước ${offset} ngày`)).toBeInTheDocument();
      unmount();
    }
  });

  it("hiện giờ theo múi giờ Việt Nam, không theo múi giờ máy người đọc", () => {
    // 01:00 ngày 29/08 giờ Hà Nội. Một kiều bào ở California mở lên vẫn phải
    // đọc thấy ngày 29/08 — ngày của dòng họ là ngày ở Việt Nam.
    renderWithProviders(
      <ul>
        <NotificationItem notification={thongBao()} onMarkRead={vi.fn()} />
      </ul>,
      { role: "member" }
    );

    expect(screen.getByText(/29 thg 8, 2026/)).toBeInTheDocument();
  });

  it("dòng chưa đọc có chấm đánh dấu và nút 'Đánh dấu đã đọc'", () => {
    renderWithProviders(
      <ul>
        <NotificationItem notification={thongBao({ isRead: false })} onMarkRead={vi.fn()} />
      </ul>,
      { role: "member" }
    );

    expect(screen.getByLabelText("Chưa đọc")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đánh dấu đã đọc" })).toBeInTheDocument();
  });

  it("dòng đã đọc thì không còn nút đánh dấu nữa", () => {
    renderWithProviders(
      <ul>
        <NotificationItem
          notification={thongBao({ isRead: true, readAt: "2026-08-29T02:00:00+07:00" })}
          onMarkRead={vi.fn()}
        />
      </ul>,
      { role: "member" }
    );

    expect(screen.queryByRole("button", { name: "Đánh dấu đã đọc" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Chưa đọc")).not.toBeInTheDocument();
  });

  it("bấm 'Đánh dấu đã đọc' báo đúng id lên trên", async () => {
    const onMarkRead = vi.fn();
    const { user } = renderWithProviders(
      <ul>
        <NotificationItem notification={thongBao({ id: "nt-42" })} onMarkRead={onMarkRead} />
      </ul>,
      { role: "member" }
    );

    await user.click(screen.getByRole("button", { name: "Đánh dấu đã đọc" }));

    expect(onMarkRead).toHaveBeenCalledWith("nt-42");
  });

  it("dẫn tới lịch giỗ có chọn sẵn sự kiện, thay vì một tuyến /events/{id} không tồn tại", () => {
    renderWithProviders(
      <ul>
        <NotificationItem notification={thongBao()} onMarkRead={vi.fn()} />
      </ul>,
      { role: "member" }
    );

    expect(screen.getByRole("link")).toHaveAttribute("href", "/events?event=ev-001");
  });

  it("mở liên kết cũng là đã đọc — không bắt người dùng làm hai thao tác", async () => {
    const onMarkRead = vi.fn();
    const { user } = renderWithProviders(
      <ul>
        <NotificationItem notification={thongBao({ id: "nt-7" })} onMarkRead={onMarkRead} />
      </ul>,
      { role: "member" }
    );

    await user.click(screen.getByRole("link"));

    expect(onMarkRead).toHaveBeenCalledWith("nt-7");
  });
});

describe("hộp thư đầy đủ", () => {
  it("tải được danh sách và hiện số chưa đọc của TOÀN hộp thư", async () => {
    renderWithProviders(<NotificationCenter />, { role: "member" });

    const badge = await screen.findByText(/\d+ chưa đọc/, undefined, { timeout: 15_000 });
    const unreadFromBadge = Number(badge.textContent!.replace(/\D+/g, ""));
    expect(unreadFromBadge).toBeGreaterThan(0);
  });

  it("mọi lời nhắc giỗ đều ở đây, không phụ thuộc quyền thông báo đẩy", async () => {
    renderWithProviders(<NotificationCenter />, { role: "member" });

    const items = await screen.findAllByRole("listitem", undefined, { timeout: 15_000 });
    expect(items.length).toBeGreaterThan(0);
    // Không có chữ nào bắt người dùng bật push mới xem được.
    expect(document.body.textContent).not.toMatch(/bật thông báo đẩy để xem/i);
  });

  it("lọc 'Chưa đọc' chỉ còn các dòng chưa đọc", async () => {
    const { user } = renderWithProviders(<NotificationCenter />, { role: "member" });

    await screen.findAllByRole("listitem", undefined, { timeout: 15_000 });
    // <Segmented> của antd giấu radio thật và vẽ ra một nhãn — bấm vào nhãn,
    // đúng chỗ người dùng bấm. Nhãn "Chưa đọc" trên bộ lọc phải phân biệt với
    // aria-label "Chưa đọc" của chấm tròn trên từng dòng.
    const filterLabels = screen.getAllByText("Chưa đọc", {
      selector: ".ant-segmented-item-label",
    });
    await user.click(filterLabels[0]!);

    await waitFor(
      () => {
        const items = screen.getAllByRole("listitem");
        for (const item of items) {
          expect(
            within(item).queryByRole("button", { name: "Đánh dấu đã đọc" })
          ).toBeInTheDocument();
        }
      },
      { timeout: 15_000 }
    );
  });

  it("đánh dấu đã đọc làm giảm số chưa đọc và bỏ nút khỏi dòng đó", async () => {
    const { user } = renderWithProviders(<NotificationCenter />, { role: "member" });

    const badge = await screen.findByText(/\d+ chưa đọc/, undefined, { timeout: 15_000 });
    const before = Number(badge.textContent!.replace(/\D+/g, ""));

    const buttons = await screen.findAllByRole("button", { name: "Đánh dấu đã đọc" });
    const row = buttons[0]!.closest("li")!;
    const title = within(row).getAllByText(/./)[0]!.textContent;
    await user.click(buttons[0]!);

    await waitFor(
      () => {
        const now = screen.queryByText(/\d+ chưa đọc/);
        const after = now ? Number(now.textContent!.replace(/\D+/g, "")) : 0;
        expect(after).toBe(before - 1);
      },
      { timeout: 15_000 }
    );
    expect(title).toBeTruthy();
  });

  it("khách vãng lai có hộp thư rỗng — chưa đăng nhập thì không sở hữu hộp thư nào", async () => {
    renderWithProviders(<NotificationCenter />, { role: "guest" });

    expect(
      await screen.findByText("Chưa có thông báo nào", undefined, { timeout: 15_000 })
    ).toBeInTheDocument();
    expect(screen.queryByText(/chưa đọc/)).not.toBeInTheDocument();
  });

  /**
   * Tài khoản đã đăng nhập nhưng chưa được ghép với nhân khẩu nào (`ACCOUNT_NOT_LINKED`).
   *
   * Đây là một trạng thái CÓ THẬT và giải quyết được, không phải sự cố hệ thống: người dùng cần
   * biết phải hỏi ai để được ghép. Nếu chỉ báo "Không tải được thông báo" thì họ sẽ ngồi đợi
   * nhắc giỗ không bao giờ tới, và tưởng hệ thống nuốt mất.
   */
  it("nói rõ khi tài khoản chưa được ghép với nhân khẩu, thay vì báo lỗi tải chung chung", async () => {
    server.use(
      http.get(`${API_BASE_URL}/api/v1/notifications`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Tài khoản chưa được ghép với nhân khẩu",
            status: 403,
            code: "ACCOUNT_NOT_LINKED",
          },
          { status: 403 }
        )
      )
    );

    renderWithProviders(<NotificationCenter />, { role: "member" });

    expect(
      await screen.findByText(
        "Tài khoản của bạn chưa được ghép với nhân khẩu nào trong gia phả.",
        undefined,
        { timeout: 15_000 }
      )
    ).toBeInTheDocument();
    // Và chỉ đúng người cần hỏi.
    expect(screen.getByText(/Hội đồng Tộc biểu/)).toBeInTheDocument();
    expect(screen.queryByText("Không tải được thông báo")).not.toBeInTheDocument();
  });
});

import { afterEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { server } from "@/mocks/server";
import { renderWithProviders } from "../setup/render";
import { TestLink } from "../setup/next-navigation-mock";

/**
 * **Khách không hỏi hộp thư** — đo 24/09 trên hệ thật (bản production, không MSW).
 *
 * Chuông ở thanh đầu trang dựng cho mọi người. Trước bản vá, mỗi trang khách mở bắn
 * `GET /api/v1/notifications?size=5` → `401` (đỏ trong console, và trong nhật ký máy chủ), rồi
 * lặp lại mỗi 60 giây theo `refetchInterval`. Bộ test này chạy ở CHẾ ĐỘ THẬT (tắt
 * `MOCKING_ENABLED`) để quyết định đi qua đúng nhánh `useAuth().status` mà bản production đi.
 */
vi.mock("@/lib/api/dev-role", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/dev-role")>("@/lib/api/dev-role");
  return { ...actual, MOCKING_ENABLED: false };
});
vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/notifications",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  redirect: vi.fn(),
  getPathname: () => "/notifications",
}));

const { NotificationBell } = await import("@/components/notifications/notification-bell");
const { NotificationCenter } = await import("@/components/notifications/notification-center");

function recordInboxCalls(): string[] {
  const seen: string[] = [];
  server.events.on("request:start", ({ request }) => {
    if (new URL(request.url).pathname.endsWith("/notifications")) seen.push(request.url);
  });
  return seen;
}

afterEach(() => server.events.removeAllListeners());

describe("hộp thư theo trạng thái phiên THẬT", () => {
  it("khách: chuông không gọi mạng, trung tâm nói cần đăng nhập", async () => {
    const calls = recordInboxCalls();
    renderWithProviders(
      <>
        <NotificationBell />
        <NotificationCenter />
      </>,
      { auth: { status: "guest", isAuthenticated: false } }
    );
    expect(await screen.findByText("Cần đăng nhập để xem thông báo")).toBeInTheDocument();
    // Chờ một nhịp để một lượt gọi (nếu có) kịp phát đi.
    await new Promise((r) => setTimeout(r, 300));
    expect(calls).toEqual([]);
    expect(screen.queryByText("Chưa có thông báo nào")).not.toBeInTheDocument();
  });

  it("phiên chưa ngã ngũ: chưa gọi, và không vội kết luận 'hộp thư rỗng'", async () => {
    const calls = recordInboxCalls();
    renderWithProviders(<NotificationCenter />, {
      auth: { status: "loading", isAuthenticated: false },
    });
    await new Promise((r) => setTimeout(r, 300));
    expect(calls).toEqual([]);
    expect(screen.queryByText("Chưa có thông báo nào")).not.toBeInTheDocument();
    expect(screen.queryByText("Cần đăng nhập để xem thông báo")).not.toBeInTheDocument();
  });

  it("đã đăng nhập: chuông hỏi hộp thư như cũ", async () => {
    const calls = recordInboxCalls();
    renderWithProviders(<NotificationBell />, { role: "member" });
    await waitFor(() => expect(calls.length).toBeGreaterThan(0));
  });
});

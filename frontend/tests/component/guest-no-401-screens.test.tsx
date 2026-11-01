import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { server } from "@/mocks/server";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";

let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams,
  usePathname: () => "/",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/",
}));

const { PostListScreen } = await import("@/components/posts/post-list-screen");
const { DirectoryScreen } = await import("@/components/directory/directory-screen");
const { EventsScreen } = await import("@/components/events/events-screen");
const { HonoursListScreen } = await import("@/components/honours/honours-list-screen");

/**
 * **Khách không bắn lượt gọi nào mà chắc chắn nhận `401`** (26/09/2026).
 *
 * Bấm thật bản mới: khách mở `/bai-viet` → `401 GET /posts?status=PUBLISHED`
 * và màn báo "Chưa tải được danh sách bài viết"; mở `/danh-ba` → `401 GET
 * /directory`. Phiên là thứ trình duyệt đã biết (`useSignedIn`), nên câu "cần
 * đăng nhập" phải đến TRƯỚC lượt gọi, không phải được dịch ra SAU một `401`.
 *
 * Mỗi ca khách có một ca thành viên đi kèm: "không gọi" chỉ có nghĩa khi cùng
 * màn ấy, với người đã đăng nhập, THẬT SỰ gọi.
 */
let paths: string[] = [];
beforeEach(() => {
  paths = [];
  searchParams = new URLSearchParams();
  resetRouterMock();
  server.events.on("request:start", ({ request }) => {
    paths.push(new URL(request.url).pathname);
  });
});
afterEach(() => {
  server.events.removeAllListeners("request:start");
});

/** Một nhịp cho lượt gọi muộn (nếu có) kịp lộ ra trước khi khẳng định "không gọi". */
const settle = () => new Promise((r) => setTimeout(r, 250));

describe("/bai-viet", () => {
  it("khách: không gọi /posts, hiện lời mời đăng nhập, không in 'chưa tải được'", async () => {
    renderWithProviders(<PostListScreen />, { role: "guest" });

    expect(await screen.findByTestId("members-only-notice")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Đăng nhập để xem/ })).toBeInTheDocument();
    await settle();
    expect(paths.filter((p) => p.startsWith("/api/v1/posts"))).toEqual([]);
    expect(screen.queryByText(/Chưa tải được/)).not.toBeInTheDocument();
    // Lối vào "Viết bài mới" vẫn ở đó — cửa thật nằm ở màn soạn bài, nơi nói vì sao.
    expect(screen.getByRole("link", { name: /Viết bài mới/ })).toBeInTheDocument();
  });

  it("thành viên: gọi /posts như thường và không thấy lời mời đăng nhập", async () => {
    renderWithProviders(<PostListScreen />, { role: "member" });

    await waitFor(() => expect(paths).toContain("/api/v1/posts"));
    expect(screen.queryByTestId("members-only-notice")).not.toBeInTheDocument();
  });
});

describe("/danh-ba", () => {
  it("khách: không gọi /directory, vẫn là khối mời đăng nhập của danh bạ", async () => {
    const { container } = renderWithProviders(<DirectoryScreen />, { role: "guest" });

    await waitFor(() =>
      expect(container.querySelector('[data-directory-state="guest"]')).not.toBeNull()
    );
    await settle();
    expect(paths.filter((p) => p.startsWith("/api/v1/directory"))).toEqual([]);
  });

  it("thành viên: gọi /directory", async () => {
    renderWithProviders(<DirectoryScreen />, { role: "member" });

    await waitFor(() => expect(paths).toContain("/api/v1/directory"));
  });
});

/**
 * `useBranches` bị chặn ở chính hook (không ở từng nơi gọi): `/events` và
 * `/vinh-danh` vừa mở API cho khách hôm nay, và cả hai màn đều hỏi danh sách
 * chi qua GraphQL — resolver đòi token, nên thiếu chốt này thì mỗi vị khách
 * mở hai màn ấy lại nhận `401 POST /graphql`.
 */
describe("danh sách chi (GraphQL) chỉ hỏi khi đã đăng nhập", () => {
  it("khách mở /events: có gọi /events (đã mở cho khách), không gọi /graphql", async () => {
    renderWithProviders(<EventsScreen />, { role: "guest" });

    await waitFor(() => expect(paths).toContain("/api/v1/events"));
    await settle();
    expect(paths).not.toContain("/api/v1/graphql");
  });

  it("khách mở /vinh-danh: có gọi /honours, không gọi /graphql", async () => {
    renderWithProviders(<HonoursListScreen />, { role: "guest" });

    await waitFor(() => expect(paths).toContain("/api/v1/honours"));
    await settle();
    expect(paths).not.toContain("/api/v1/graphql");
  });

  it("thành viên mở /events: có hỏi danh sách chi", async () => {
    renderWithProviders(<EventsScreen />, { role: "member" });

    await waitFor(() => expect(paths).toContain("/api/v1/graphql"));
  });
});

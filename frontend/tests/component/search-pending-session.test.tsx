import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { server } from "@/mocks/server";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("q=thuy to"),
  usePathname: () => "/search",
  useRouter: () => routerMock,
}));

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/search",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/search",
}));

// Giữ phiên ở trạng thái "CHƯA NGÃ NGŨ" — `check-sso` của Keycloak đang chạy. Dưới MSW trạng
// thái này không bao giờ xảy ra tự nhiên (vai đến ngay từ bộ chuyển vai dev), và đó chính là lý
// do lỗi dưới đây sống sót qua `person-search.test.tsx`: mọi ca ở đó đã biết ngay khách hay
// thành viên. `useSignedIn` dựa trên chính hook này nên cũng trả `null`.
vi.mock("@/hooks/use-tree-audience", () => ({
  useTreeAudience: () => null,
}));

const { PersonSearchScreen } = await import("@/components/search/person-search-screen");

/**
 * `/search` khi phiên chưa ngã ngũ — lượt `401 POST /graphql` cuối cùng của khách (26/09/2026).
 *
 * Bản trước ẩn ô lọc chi khi `audience === "public"`. Trong nhịp `check-sso` (`audience ===
 * null`) phép so ấy ra `false`, ô lọc chi được dựng, `useBranches` bắn `POST /graphql`, và
 * `getAuthToken()` — vốn CHỜ `check-sso` xong — trả `null` cho khách: `401`. Nay ô lọc ẩn khi
 * `audience !== "member"` và `useBranches` tự tắt khi chưa đăng nhập; ca này ghim cả hai.
 */
let paths: string[] = [];
beforeEach(() => {
  paths = [];
  resetRouterMock();
  server.events.on("request:start", ({ request }) => {
    paths.push(new URL(request.url).pathname);
  });
});
afterEach(() => {
  server.events.removeAllListeners("request:start");
});

describe("tìm kiếm khi phiên chưa ngã ngũ", () => {
  it("không dựng ô lọc chi, không gọi /graphql, không gọi lối tìm kiếm nào", async () => {
    renderWithProviders(<PersonSearchScreen />, { role: "guest" });

    expect(await screen.findByLabelText("Tên người cần tìm")).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 400));

    expect(document.getElementById("search-filter-branch")).toBeNull();
    expect(paths).not.toContain("/api/v1/graphql");
    expect(paths.filter((p) => p.includes("/persons/search"))).toEqual([]);
  });
});

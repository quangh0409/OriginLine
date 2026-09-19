import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { renderHook, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { setDevRole } from "@/lib/api/dev-role";
import { useTreeCanvas } from "@/hooks/use-tree-canvas";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";

/**
 * **MỞ `/tree` KHÔNG THAM SỐ THÌ RA CÂY.**
 *
 * Hai bên từng giải cùng một bài toán theo hai hướng. Giao diện dựng một chuỗi
 * bốn bước chọn gốc ở phía client (URL → biến môi trường → `localStorage` →
 * hỏi người dùng); máy chủ làm `rootId` thành **tuỳ chọn** và tự chọn gốc theo
 * **vai + phạm vi chi**. Câu trả lời của máy chủ hay hơn hẳn — nó biết vai,
 * biết phạm vi `ltree`, biết ai là thuỷ tổ; client không biết gì trong ba thứ
 * đó. Bộ test này ghim lại việc client đã thôi đoán.
 *
 * Bốn tính chất được giữ ở đây:
 *  1. khách mở `/tree` trần là **thấy cây ngay**, không qua màn chọn gốc;
 *  2. thành viên cũng vậy, và vẫn đi đường thành viên;
 *  3. `?rootId=` trên URL **vẫn thắng tuyệt đối** — đó là liên kết dán vào nhóm Zalo;
 *  4. yêu cầu gửi đi khi không có gốc **thật sự không mang `rootId`**, chứ
 *     không phải mang một chuỗi rỗng hay chữ `"null"`.
 */

const SHARED_ROOT_ID = "p-010";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/tree",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/tree",
}));

vi.mock("next/navigation", async () => {
  const actual = await vi.importActual<typeof import("next/navigation")>("next/navigation");
  return { ...actual, useSearchParams: () => new URLSearchParams() };
});

const { TreeCanvas } = await import("@/components/tree/tree-canvas");

function hookWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

/**
 * Ghi lại URL của mọi lượt gọi `/tree` (cả hai bản) trong một bài test.
 *
 * Dùng `server.events` chứ không chặn bằng một handler riêng: câu hỏi ở đây là
 * "giao diện gửi đi **cái gì**", và nó phải trả lời được mà không thay bộ giả
 * lập thật bằng một bộ khác.
 */
function recordTreeRequests(): URL[] {
  const seen: URL[] = [];
  server.events.on("request:start", ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith("/tree")) seen.push(url);
  });
  return seen;
}

// `server.resetHandlers()` ở setup chung KHÔNG gỡ listener; thiếu dòng này thì
// bài test sau nhặt luôn lượt gọi của bài trước và khẳng định sai.
afterEach(() => {
  server.events.removeAllListeners();
});

describe("không có `?rootId=` — máy chủ chọn gốc", () => {
  it("khách thấy cây ngay, và gốc do máy chủ chọn về trong phản hồi", async () => {
    setDevRole("guest");

    const { result } = renderHook(() => useTreeCanvas({}), { wrapper: hookWrapper() });

    await waitFor(() => expect(result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    expect(result.current.audience).toBe("public");
    // Gốc thật sự đang mở CHỈ biết được từ trường `rootId` của phản hồi — hợp
    // đồng nói rõ thế, và đây là thứ cho phép giao diện tô đậm node gốc.
    expect(result.current.rootId).not.toBe("");
    // Và nó phải là một gốc MỞ RA ĐƯỢC CÂY. Ở đời 1 có cả cụ ông lẫn cụ bà mà
    // mọi cạnh cha/mẹ chỉ xuất phát từ một người; chọn nhầm thì phả đồ ra đúng
    // hai node — vẫn "có cây", vẫn xanh, và vẫn vô dụng.
    expect(result.current.nodes.length).toBeGreaterThan(2);
    expect(result.current.nodes.some((node) => node.id === result.current.rootId)).toBe(true);
    // Bản công khai chỉ gồm người đã khuất — phép chọn gốc không được phá luật ấy.
    expect(result.current.nodes.every((node) => node.person.isAlive === false)).toBe(true);
    expect(result.current.error).toBeNull();
    expect(result.current.rootRejected).toBe(false);
    expect(result.current.rootNotFound).toBe(false);
  });

  it("thành viên cũng thấy cây ngay, trên đường thành viên", async () => {
    setDevRole("member");

    const { result } = renderHook(() => useTreeCanvas({}), { wrapper: hookWrapper() });

    await waitFor(() => expect(result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    expect(result.current.audience).toBe("member");
    expect(result.current.rootId).not.toBe("");
    expect(result.current.nodes.length).toBeGreaterThan(2);
    expect(result.current.error).toBeNull();
  });

  it("gốc mặc định của cổng công khai KHÔNG phụ thuộc vai — nếu không `Cache-Control: public` là lỗ rò", async () => {
    setDevRole("guest");
    const asGuest = renderHook(() => useTreeCanvas({ audience: "public" }), {
      wrapper: hookWrapper(),
    });
    await waitFor(() => expect(asGuest.result.current.rootId).not.toBe(""), { timeout: 10000 });

    // Một thành viên lỡ mang phiên tới cổng công khai vẫn phải nhận đúng cây
    // của Khách, từ đúng cái gốc của Khách.
    setDevRole("admin");
    const asAdmin = renderHook(() => useTreeCanvas({ audience: "public" }), {
      wrapper: hookWrapper(),
    });
    await waitFor(() => expect(asAdmin.result.current.rootId).not.toBe(""), { timeout: 10000 });

    expect(asAdmin.result.current.rootId).toBe(asGuest.result.current.rootId);
  });

  it("gốc mặc định KHÁC nhau theo vai — đó là cả điểm của việc để máy chủ chọn", async () => {
    setDevRole("admin");
    const admin = renderHook(() => useTreeCanvas({}), { wrapper: hookWrapper() });
    await waitFor(() => expect(admin.result.current.rootId).not.toBe(""), { timeout: 10000 });

    setDevRole("branch-head");
    const branchHead = renderHook(() => useTreeCanvas({}), { wrapper: hookWrapper() });
    await waitFor(() => expect(branchHead.result.current.rootId).not.toBe(""), {
      timeout: 10000,
    });

    // Quản trị mở ra thuỷ tổ (đời 1); trưởng chi mở ra ông tổ CHI ĐƯỢC GIAO.
    // Nếu hai giá trị này bằng nhau thì phép chọn theo vai đã không chạy, và
    // giao diện vừa xoá mất phần cá nhân hoá mà máy chủ làm ra.
    expect(branchHead.result.current.rootId).not.toBe(admin.result.current.rootId);
  });

  it("yêu cầu gửi đi KHÔNG mang `rootId` — không phải chuỗi rỗng, không phải chữ `null`", async () => {
    setDevRole("guest");
    const seen = recordTreeRequests();

    const { result } = renderHook(() => useTreeCanvas({}), { wrapper: hookWrapper() });
    await waitFor(() => expect(result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    expect(seen.length).toBeGreaterThan(0);
    // `buildUrl` chỉ bỏ qua `undefined`; một `null` lọt xuống sẽ thành
    // `?rootId=null` và đổi một phép chọn gốc thành `400`.
    expect(seen.every((url) => url.searchParams.has("rootId"))).toBe(false);
    expect(seen.map((url) => url.searchParams.get("rootId"))).not.toContain("null");
  });
});

describe("`?rootId=` trên URL vẫn thắng tuyệt đối", () => {
  it("gốc được chia sẻ được gửi lên nguyên vẹn và trở thành gốc đang mở", async () => {
    setDevRole("guest");
    const seen = recordTreeRequests();

    const { result } = renderHook(() => useTreeCanvas({ rootId: SHARED_ROOT_ID }), {
      wrapper: hookWrapper(),
    });

    await waitFor(() => expect(result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    expect(result.current.rootId).toBe(SHARED_ROOT_ID);
    expect(seen.some((url) => url.searchParams.get("rootId") === SHARED_ROOT_ID)).toBe(true);
  });

  it("gốc trên URL không bị 'gốc mặc định của máy chủ' đè lên", async () => {
    // Vai quản trị: gốc mặc định của họ là THUỶ TỔ, khác hẳn `p-010` (ông tổ
    // chi Nhất) mà liên kết chia sẻ trỏ tới. Chọn một vai mà hai giá trị ấy
    // tình cờ trùng nhau thì bài test không còn chứng minh được gì.
    setDevRole("admin");

    const withRoot = renderHook(() => useTreeCanvas({ rootId: SHARED_ROOT_ID }), {
      wrapper: hookWrapper(),
    });
    await waitFor(() => expect(withRoot.result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    const serverDefault = renderHook(() => useTreeCanvas({}), { wrapper: hookWrapper() });
    await waitFor(() => expect(serverDefault.result.current.rootId).not.toBe(""), {
      timeout: 10000,
    });

    expect(withRoot.result.current.rootId).toBe(SHARED_ROOT_ID);
    expect(withRoot.result.current.rootId).not.toBe(serverDefault.result.current.rootId);
  });
});

describe("màn chọn gốc thôi chắn đường", () => {
  it("mở `/tree` trần: KHÔNG hiện màn chọn gốc, cho cả khách lẫn thành viên", async () => {
    for (const role of ["guest", "member"] as const) {
      setDevRole(role);
      resetRouterMock();

      const { unmount } = renderWithProviders(<TreeCanvas />, { role });

      // Không được nhấp nháy một màn hỏi gốc trong lúc chờ: gốc bây giờ đến từ
      // phản hồi, nên trạng thái trung gian phải là "đang tải", không phải "hỏi".
      expect(screen.queryByTestId("tree-root-picker")).toBeNull();

      await waitFor(
        () => expect(screen.getByTestId("tree-node-count")).toBeInTheDocument(),
        { timeout: 15000 }
      );
      expect(screen.queryByTestId("tree-root-picker")).toBeNull();

      unmount();
    }
  });

  it("còn tới được khi người dùng CHỦ Ý bấm đổi gốc", async () => {
    setDevRole("member");
    resetRouterMock();

    const { user } = renderWithProviders(<TreeCanvas />, { role: "member" });

    await waitFor(() => expect(screen.getByTestId("tree-change-root")).toBeInTheDocument(), {
      timeout: 15000,
    });
    await user.click(screen.getByTestId("tree-change-root"));

    expect(await screen.findByTestId("tree-root-picker")).toBeInTheDocument();
    // Và nó không còn là ngõ cụt: có lối quay về cây mặc định mà không phải gõ tên ai.
    expect(
      screen.getByRole("button", { name: "Mở phả đồ mặc định của dòng họ" })
    ).toBeInTheDocument();
  });

  it("một `?rootId=` máy chủ từ chối dẫn tới màn chọn gốc, KHÔNG dẫn tới dải đỏ", async () => {
    setDevRole("guest");
    server.use(
      http.get(`${API_BASE_URL}/api/v1/public/tree`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "rootId không hợp lệ",
            status: 400,
            code: "VALIDATION_FAILED",
          },
          { status: 400 }
        )
      )
    );

    const { result } = renderHook(() => useTreeCanvas({ rootId: "lien-ket-cu" }), {
      wrapper: hookWrapper(),
    });

    await waitFor(() => expect(result.current.rootRejected).toBe(true), { timeout: 10000 });
    expect(result.current.error).toBeNull();
  });
});

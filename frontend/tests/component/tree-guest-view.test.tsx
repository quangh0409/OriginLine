import { describe, expect, it, vi } from "vitest";
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

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/tree",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/tree",
}));

const { TreePublicNotice } = await import("@/components/tree/tree-public-notice");
const { TreeRootPicker } = await import("@/components/tree/tree-root-picker");

/** Thủy tổ của bộ dữ liệu giả lập — đã khuất, nên công khai với mọi người. */
const DECEASED_ROOT_ID = "p-001";

/**
 * KHÁCH XEM PHẢ ĐỒ.
 *
 * BA v2 §10: người đã khuất là **công khai**. Trước bản vá này giao diện chỉ
 * biết đúng một đường — `GET /api/v1/tree` — mà đường ấy nằm sau
 * `authenticated()`, nên khách nhận `401` và màn hình dịch nó thành "Không tải
 * được cây phả đồ": vừa sai nguyên nhân, vừa không có lối đi tiếp.
 *
 * Hai điều bộ test này giữ:
 *  1. khách **xem được** phả đồ, và những gì họ thấy đều là người đã khuất;
 *  2. sự thưa của cây ấy được giải thích **mà không đếm** — không câu chữ nào
 *     nói có bao nhiêu người đang bị ẩn, vì đếm cũng là tiết lộ.
 */

function hookWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("khách mở phả đồ", () => {
  it("đi đường công khai và thấy các cụ đã khuất, không phải một dải báo lỗi", async () => {
    setDevRole("guest");

    const { result } = renderHook(() => useTreeCanvas({ rootId: DECEASED_ROOT_ID }), {
      wrapper: hookWrapper(),
    });

    await waitFor(() => expect(result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    expect(result.current.audience).toBe("public");
    expect(result.current.error).toBeNull();
    expect(result.current.unauthorized).toBe(false);
    expect(result.current.rootNotFound).toBe(false);
    expect(result.current.nodes.every((node) => node.person.isAlive === false)).toBe(true);
  });

  it("thành viên vẫn đi đường thành viên — không ai bị hạ xuống bản công khai", async () => {
    setDevRole("member");

    const { result } = renderHook(() => useTreeCanvas({ rootId: DECEASED_ROOT_ID }), {
      wrapper: hookWrapper(),
    });

    await waitFor(() => expect(result.current.nodes.length).toBeGreaterThan(0), {
      timeout: 10000,
    });

    expect(result.current.audience).toBe("member");
    expect(result.current.error).toBeNull();
  });

  it("phiên chết giữa chừng đọc ra 'cần đăng nhập', không phải 'không tải được'", async () => {
    setDevRole("member");
    server.use(
      http.get(`${API_BASE_URL}/api/v1/tree`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Cần đăng nhập",
            status: 401,
            code: "UNAUTHENTICATED",
          },
          { status: 401 }
        )
      )
    );

    const { result } = renderHook(() => useTreeCanvas({ rootId: DECEASED_ROOT_ID }), {
      wrapper: hookWrapper(),
    });

    await waitFor(() => expect(result.current.unauthorized).toBe(true), { timeout: 10000 });
    // `error` phải là null: đó là thứ quyết định màn hình hiện lời mời đăng
    // nhập thay vì dải đỏ.
    expect(result.current.error).toBeNull();
  });

  it("gốc không phải UUID không rơi vào dải đỏ mà dẫn tới bước chọn gốc", async () => {
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

    const { result } = renderHook(() => useTreeCanvas({ rootId: "p-001" }), {
      wrapper: hookWrapper(),
    });

    await waitFor(() => expect(result.current.rootRejected).toBe(true), { timeout: 10000 });
    expect(result.current.error).toBeNull();
  });
});

describe("câu giải thích cho bản công khai", () => {
  it("nói bản đang xem gồm những ai, và mời đăng nhập", () => {
    renderWithProviders(<TreePublicNotice onLogin={() => undefined} />);

    const notice = screen.getByRole("note", { name: "Ghi chú về bản phả đồ đang xem" });
    expect(notice).toBeInTheDocument();
    expect(notice.textContent).toMatch(/đã khuất/);
    expect(screen.getByRole("button", { name: "Đăng nhập" })).toBeInTheDocument();
  });

  it("KHÔNG chứa một con số nào — đếm người bị ẩn cũng là tiết lộ", () => {
    renderWithProviders(<TreePublicNotice onLogin={() => undefined} />);

    const notice = screen.getByRole("note", { name: "Ghi chú về bản phả đồ đang xem" });
    // Bất kỳ chữ số nào ở đây đều đáng ngờ: câu này chỉ được phụ thuộc vào
    // "đang xem bản công khai", một hằng số, chứ không vào dữ liệu của dòng họ.
    expect(notice.textContent ?? "").not.toMatch(/\d/);
  });

  it("không dùng từ ngữ gợi ý 'có dữ liệu bạn không được xem'", () => {
    const { container } = renderWithProviders(<TreePublicNotice onLogin={() => undefined} />);

    const text = container.textContent ?? "";
    expect(text).not.toMatch(/bị ẩn|đã ẩn|riêng tư|không có quyền/i);
    expect(text).not.toMatch(/🔒|🔓/);
  });
});

describe("bước chọn gốc", () => {
  it("tìm được một cụ đã khuất và trả id cho người gọi", async () => {
    setDevRole("guest");
    resetRouterMock();
    const onSelect = vi.fn();

    const { user } = renderWithProviders(
      <TreeRootPicker audience="public" onSelect={onSelect} />
    );

    expect(
      screen.getByRole("heading", { name: "Phả đồ bắt đầu từ ai?" })
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Tìm người làm gốc cây/), "Thủy");

    const option = await screen.findByRole("button", { name: /Thủy Tổ/ }, { timeout: 10000 });
    await user.click(option);

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect.mock.calls[0]![0]).toBe(DECEASED_ROOT_ID);
  });

  it("một ký tự thì chưa gọi máy chủ — nó sẽ là 400, và người dùng chưa làm gì sai", async () => {
    setDevRole("guest");
    const { user, container } = renderWithProviders(
      <TreeRootPicker audience="public" onSelect={() => undefined} />
    );

    await user.type(screen.getByLabelText(/Tìm người làm gốc cây/), "N");

    expect(screen.getByText("Xin gõ ít nhất 2 ký tự.")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/lỗi|thất bại/i);
  });
});

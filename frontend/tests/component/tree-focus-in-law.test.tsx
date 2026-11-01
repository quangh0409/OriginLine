import { afterEach, describe, expect, it } from "vitest";
import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import { setDevRole } from "@/lib/api/dev-role";
import { useTreeCanvas } from "@/hooks/use-tree-canvas";
import { edge, node, projection } from "../setup/tree-fixtures";

/**
 * **"Về chỗ tôi" cho một người DÂU** — đo trên hệ thật 24/09 (NT-TV-02).
 *
 * Tài khoản demo `thanhvien` là một người dâu đời 7. Bấm "Về chỗ tôi" thì canvas đổi gốc sang
 * chính cô và còn "2 nhân khẩu đang hiển thị": máy chủ trả `ANCESTORS` từ người dâu đúng hai node
 * (cô và chồng, một cạnh `SPOUSE`) — không có cạnh cha/mẹ nào để đi ngược về gốc.
 *
 * Bộ giả lập ở đây trả **đúng hình dạng máy chủ thật** cho từng lượt gọi, và ghim rằng hook
 * hỏi thêm chuỗi tổ tiên của NGƯỜI CHỒNG rồi mở đường `gốc → … → chồng → cô`, không đổi gốc.
 *
 *   R ─ A ─ B ─ H ══ W (dâu)
 */
const R = node("R", 0, { childCount: 1 });
const A = node("A", 1, { parentIds: ["R"], childCount: 1 });
const B = node("B", 2, { parentIds: ["A"], childCount: 1, hasMoreDescendants: true });
const H = node("H", 3, { parentIds: ["B"], spouseIds: ["W"] });
const W = node("W", 3, { spouseIds: ["H"], person: { ...node("W", 3).person, isAlive: true } });
const eRA = edge("e-RA", "R", "A");
const eAB = edge("e-AB", "A", "B");
const eBH = edge("e-BH", "B", "H");
const eHW = edge("e-HW", "H", "W", "SPOUSE", { spouseOrder: 1 });

function wrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

afterEach(() => {
  server.events.removeAllListeners();
});

describe("nhắm tới một người dâu", () => {
  it("hỏi thêm tổ tiên của chồng, mở đường qua chồng, và KHÔNG đổi gốc", async () => {
    setDevRole("member");
    const seen: string[] = [];
    server.use(
      http.get(`${API_BASE_URL}/api/v1/tree`, ({ request }) => {
        const url = new URL(request.url);
        const rootId = url.searchParams.get("rootId");
        const dir = url.searchParams.get("direction");
        seen.push(`${rootId ?? "-"}:${dir}`);
        // Gốc do máy chủ chọn, sâu 2 đời: người dâu ở đời 4 chưa có mặt.
        if (rootId === null) return HttpResponse.json(projection("R", [R, A, B], [eRA, eAB]));
        // Đúng hình dạng đã đo: ANCESTORS từ người dâu = cô + chồng, một cạnh SPOUSE.
        if (rootId === "W") return HttpResponse.json(projection("W", [W, H], [eHW]));
        if (rootId === "H" && dir === "ANCESTORS")
          return HttpResponse.json(projection("H", [H, B, A, R, W], [eRA, eAB, eBH, eHW]));
        return HttpResponse.json(projection(rootId, [], []), { status: 404 });
      })
    );

    const { result } = renderHook(() => useTreeCanvas({ focusId: "W", fetchFocus: true }), {
      wrapper: wrapper(),
    });

    await waitFor(() => expect(result.current.focusPath).toEqual(["R", "A", "B", "H", "W"]), {
      timeout: 10000,
    });
    expect(seen).toContain("H:ANCESTORS");
    expect(result.current.focusUnreachable).toBe(false);
    expect(result.current.rootId).toBe("R");
    expect(result.current.nodes.map((n) => n.id)).toEqual(expect.arrayContaining(["H", "W"]));
  });

  it("người trong họ: không tốn thêm lượt gọi cầu nối nào", async () => {
    setDevRole("member");
    const seen: string[] = [];
    server.use(
      http.get(`${API_BASE_URL}/api/v1/tree`, ({ request }) => {
        const url = new URL(request.url);
        const rootId = url.searchParams.get("rootId");
        seen.push(`${rootId ?? "-"}:${url.searchParams.get("direction")}`);
        if (rootId === null) return HttpResponse.json(projection("R", [R, A, B], [eRA, eAB]));
        return HttpResponse.json(projection("H", [H, B, A, R, W], [eRA, eAB, eBH, eHW]));
      })
    );

    const { result } = renderHook(() => useTreeCanvas({ focusId: "H", fetchFocus: true }), {
      wrapper: wrapper(),
    });
    await waitFor(() => expect(result.current.focusPath).toEqual(["R", "A", "B", "H"]), {
      timeout: 10000,
    });
    // Hai lượt cho người được nhắm (tổ tiên + một đời hậu duệ), không có lượt từ người vợ.
    expect(seen.filter((s) => s.startsWith("W:"))).toEqual([]);
  });
});

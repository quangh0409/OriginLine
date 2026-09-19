import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { findChangeRequestMock, resetChangeRequestsMock } from "@/mocks/change-requests";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/correction-requests",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/correction-requests",
}));

const { CorrectionScreen } = await import("@/components/correction/correction-screen");

/**
 * **Phía người duyệt** của luồng đính chính.
 *
 * Ba luật mà hàng đợi này sống chết theo, và cả ba đều được khoá ở đây:
 *
 *  1. **phạm vi `ltree`** — Trưởng chi `root.chi_nhat` không thấy yêu cầu của
 *     `root.chi_nhi`, chứ không phải "thấy rồi bấm vào mới bị chặn";
 *  2. **không tự duyệt** — đề nghị do chính Trưởng chi gửi thì họ không quyết
 *     được; thiếu luật này thì luồng đính chính chỉ là một cách viết thẳng vào
 *     cây có thêm hai cú bấm;
 *  3. **từ chối phải nêu lý do** — người gửi bỏ công tra gia phả cũ; trả lại
 *     một chữ "không" là cách chắc chắn để họ không gửi lần thứ hai.
 *
 * Dữ liệu mồi (`src/mocks/change-requests.ts`):
 *   `cr-001` do `u-member` gửi, nhắm `p-010` (chi_nhat)  → Trưởng chi duyệt được
 *   `cr-002` do `u-branch-head` gửi, nhắm `p-020` (chi_nhat) → KHÔNG tự duyệt được
 *   `cr-003` do `u-member` gửi, nhắm `p-011` (chi_nhi)   → ngoài phạm vi
 *   `cr-004` đã APPROVED                                  → không nằm trong hàng đợi
 */

interface CapturedRequest {
  method: string;
  url: string;
  body: unknown;
}

function captureWrites(): CapturedRequest[] {
  const captured: CapturedRequest[] = [];
  server.events.on("request:start", async ({ request }) => {
    if (request.method === "GET") return;
    let body: unknown;
    try {
      body = await request.clone().json();
    } catch {
      body = undefined;
    }
    captured.push({ method: request.method, url: request.url, body });
  });
  return captured;
}

/** Bộ test này chờ MSW + antd nhiều lần; 45s là mức an toàn trên máy chậm. */
const SLOW = 45_000;

const reviews = (captured: CapturedRequest[]) =>
  captured.filter((r) => r.url.includes("/review"));

/** Thẻ yêu cầu chứa đoạn lý do đã cho. */
function cardContaining(text: string | RegExp): HTMLElement {
  const node = screen.getByText(text);
  const card = node.closest("article");
  if (!card) throw new Error(`Không tìm thấy thẻ yêu cầu chứa: ${String(text)}`);
  return card as HTMLElement;
}

async function openQueue(role: "member" | "branch-head" | "admin") {
  const rendered = renderWithProviders(<CorrectionScreen />, { role });
  return rendered;
}

beforeEach(() => {
  resetRouterMock();
  resetChangeRequestsMock();
  server.events.removeAllListeners("request:start");
});

describe("Trưởng chi nhìn hàng đợi trong phạm vi được giao", () => {
  it("thấy đề nghị của thành viên trong chi mình, kèm trước/sau cạnh nhau", async () => {
    await openQueue("branch-head");

    const card = await waitFor(() => cardContaining(/18 tháng Chạp/), { timeout: 15_000 });

    // Trước/sau là cả điểm của màn hình này: Trưởng chi quyết trong vài giây,
    // trên điện thoại; bắt họ nhớ giá trị cũ là cách để một con số gõ nhầm lọt.
    // Cột "đang ghi" phải chờ hồ sơ nhân khẩu tải xong — thẻ hiện trước, giá
    // trị đối chiếu đến sau, và đó là hành vi đúng: người duyệt thấy ngay có
    // gì trong hàng đợi thay vì nhìn màn hình trắng.
    expect(await within(card).findByText("Đang ghi", {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(within(card).getByText("Đề nghị sửa thành")).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: /Duyệt/ })).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: /Từ chối/ })).toBeInTheDocument();
  }, SLOW);

  it("KHÔNG thấy yêu cầu của Chi Nhị — ngoài phạm vi thì vô hình, không phải bấm vào mới 403", async () => {
    await openQueue("branch-head");
    await waitFor(() => cardContaining(/18 tháng Chạp/), { timeout: 15_000 });

    // cr-003 nhắm p-011 thuộc `root.chi_nhi`.
    expect(screen.queryByText(/Cụ dạy học ở làng/)).not.toBeInTheDocument();
  }, SLOW);

  it("không thấy yêu cầu đã chốt trong hàng đợi", async () => {
    await openQueue("branch-head");
    await waitFor(() => cardContaining(/18 tháng Chạp/), { timeout: 15_000 });
    expect(screen.queryByText(/bia đá ngoài lăng/)).not.toBeInTheDocument();
  }, SLOW);
});

describe("không được tự duyệt yêu cầu của chính mình", () => {
  it("Trưởng chi thấy đề nghị của chính mình nhưng không có nút Duyệt/Từ chối", async () => {
    await openQueue("branch-head");

    // cr-002 do chính `u-branch-head` gửi.
    const ownCard = await waitFor(() => cardContaining(/văn bia ngoài từ đường/), {
      timeout: 15_000,
    });

    expect(within(ownCard).getByText("Đề nghị này do chính bạn gửi")).toBeInTheDocument();
    expect(within(ownCard).queryByRole("button", { name: /Duyệt/ })).not.toBeInTheDocument();
    expect(within(ownCard).queryByRole("button", { name: /Từ chối/ })).not.toBeInTheDocument();
  }, SLOW);

  it("máy chủ vẫn là chốt thật: gọi thẳng review trả 403 SELF_REVIEW_FORBIDDEN", async () => {
    // Ẩn nút là phép lịch sự với người dùng, KHÔNG phải biện pháp bảo mật.
    // Bài này đi vòng qua giao diện để chứng minh cửa chặn nằm ở máy chủ.
    const { changeRequestsApi } = await import("@/lib/api/change-requests");
    const { ApiError } = await import("@/lib/api/http");
    const { setDevRole } = await import("@/lib/api/dev-role");
    setDevRole("branch-head");

    await expect(
      changeRequestsApi.review("cr-002", { approve: true, note: null })
    ).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as InstanceType<typeof ApiError>).code).toBe("SELF_REVIEW_FORBIDDEN");
      expect((error as InstanceType<typeof ApiError>).status).toBe(403);
      return true;
    });

    // Và trạng thái không hề nhúc nhích.
    expect(findChangeRequestMock("cr-002")?.status).toBe("PENDING");
  }, SLOW);

  it("Hội đồng / Quản trị mới là người quyết đề nghị của Trưởng chi", async () => {
    const captured = captureWrites();
    const { user } = await openQueue("admin");

    const ownCard = await waitFor(() => cardContaining(/văn bia ngoài từ đường/), {
      timeout: 15_000,
    });
    await user.click(within(ownCard).getByRole("button", { name: /Duyệt/ }));

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /Duyệt/ }));

    await waitFor(() => expect(reviews(captured)).toHaveLength(1), { timeout: 10_000 });
    expect((reviews(captured)[0]?.body as { approve: boolean }).approve).toBe(true);
    await waitFor(() => expect(findChangeRequestMock("cr-002")?.status).toBe("APPROVED"));
  }, SLOW);
});

describe("từ chối phải nêu lý do", () => {
  it("chặn ngay trên giao diện khi ghi chú để trống, không gửi gì lên máy chủ", async () => {
    const captured = captureWrites();
    const { user } = await openQueue("branch-head");

    const card = await waitFor(() => cardContaining(/18 tháng Chạp/), { timeout: 15_000 });
    await user.click(within(card).getByRole("button", { name: /Từ chối/ }));

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /Từ chối/ }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(/Xin nêu lý do từ chối/);
    expect(reviews(captured)).toHaveLength(0);
    expect(findChangeRequestMock("cr-001")?.status).toBe("PENDING");
  }, SLOW);

  it("gửi lý do kèm quyết định để người gửi đọc được", async () => {
    const captured = captureWrites();
    const { user } = await openQueue("branch-head");

    const card = await waitFor(() => cardContaining(/18 tháng Chạp/), { timeout: 15_000 });
    await user.click(within(card).getByRole("button", { name: /Từ chối/ }));

    const dialog = await screen.findByRole("dialog");
    await user.type(
      within(dialog).getByLabelText("Lý do từ chối"),
      "Đã đối chiếu văn bia, ngày mất trong gia phả là đúng."
    );
    await user.click(within(dialog).getByRole("button", { name: /Từ chối/ }));

    await waitFor(() => expect(reviews(captured)).toHaveLength(1), { timeout: 10_000 });
    const body = reviews(captured)[0]?.body as { approve: boolean; note: string };
    expect(body.approve).toBe(false);
    expect(body.note).toContain("văn bia");
    await waitFor(() => expect(findChangeRequestMock("cr-001")?.status).toBe("REJECTED"));
  }, SLOW);
});

describe("thành viên thường", () => {
  it("không thấy tab hàng đợi duyệt, chỉ thấy đề nghị của mình", async () => {
    await openQueue("member");

    expect(
      await screen.findByRole("tab", { name: "Đề nghị của tôi" }, { timeout: 15_000 })
    ).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /Chờ tôi duyệt/ })).not.toBeInTheDocument();
  }, SLOW);

  it("đọc lại được kết quả và lý do của người duyệt — im lặng là cách để họ gửi trùng", async () => {
    await openQueue("member");
    await screen.findByRole("tab", { name: "Đề nghị của tôi" }, { timeout: 15_000 });

    // cr-004 đã được Quản trị duyệt kèm ghi chú.
    expect(await screen.findByText("Đã duyệt", {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(
      screen.getByText(/Ghi chú của người duyệt: Đã nhận ảnh/)
    ).toBeInTheDocument();
  }, SLOW);
});

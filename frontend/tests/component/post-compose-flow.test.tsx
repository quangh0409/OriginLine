import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, resetRouterMock, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import type { PostWriteBody } from "@/lib/api/posts";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/bai-viet/moi",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/bai-viet/moi",
}));

const { PostComposeScreen } = await import("@/components/posts/post-compose-screen");
const { MyPostsScreen } = await import("@/components/posts/my-posts-screen");
const { PostReviewQueueScreen } = await import("@/components/posts/post-review-queue-screen");

/**
 * Ghim bốn lỗi của luồng đăng bài mà chỉ một trình duyệt đã đăng nhập bấm thật mới thấy
 * (nghiệm thu 24/09/2026, `e2e/real-auth/nt-d-*.mjs`). Mỗi ca dưới đây ĐỎ trên mã trước bản vá:
 *
 * 1. gõ tiêu đề rồi rời ô ⇒ `POST /posts {body:""}` ⇒ 400 ⇒ "Chưa lưu được nháp";
 * 2. ảnh tải lên + xác nhận + gắn (200 cả ba) rồi BIẾN MẤT khỏi `/bai-viet/moi`;
 * 3. bấm "Gửi duyệt" trả 200 mà màn hình không đổi gì; và với nháp mở lại thì còn tệ hơn —
 *    "Bài đã được sửa ở nơi khác" hiện ra và không gì được gửi;
 * 4. "Sửa nhẹ rồi đăng" luôn 403 trên máy chủ thật.
 */

function tinyPng(name: string): File {
  const base64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return new File([bytes], name, { type: "image/png" });
}

describe("màn soạn bài — luồng thật từ gõ tiêu đề tới gửi duyệt", () => {
  beforeEach(() => {
    resetRouterMock();
    window.history.replaceState(null, "", "/bai-viet/moi");
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("chỉ gõ tiêu đề rồi rời ô: nháp được lưu với thân RỖNG, báo 'Đã lưu nháp', mở bộ chọn ảnh, URL thành /bai-viet/{id}/sua", async () => {
    let createdBody: PostWriteBody | null = null;
    server.events.on("request:start", async ({ request }) => {
      if (request.method === "POST" && request.url.endsWith("/api/v1/posts")) {
        createdBody = (await request.clone().json()) as PostWriteBody;
      }
    });

    const { user } = renderWithProviders(<PostComposeScreen />, { role: "member" });
    const titleBox = await screen.findByLabelText("Tiêu đề");
    await user.type(titleBox, "Họp họ cuối năm");
    await user.tab(); // rời ô tiêu đề — đúng lời gợi ý ở bộ chọn ảnh

    await screen.findByText(/Đã lưu nháp lúc/, undefined, { timeout: 10000 });
    server.events.removeAllListeners();

    expect(createdBody).toEqual({ title: "Họp họ cuối năm", body: "" });
    expect(screen.queryByText(/Chưa lưu được nháp/)).not.toBeInTheDocument();
    // Bộ chọn ảnh không còn khoá, và lời nhắc "lưu nháp trước" đã biến mất.
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await waitFor(() => expect(input).not.toBeDisabled());
    expect(screen.queryByText(/lưu nháp trước/)).not.toBeInTheDocument();
    // F5 từ giờ mở lại đúng bài này — URL đổi mà KHÔNG điều hướng (không router.push/replace).
    expect(window.location.pathname).toMatch(/^\/bai-viet\/post-\d+\/sua$/);
    expect(routerMock.replace).not.toHaveBeenCalled();
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("bài mới: ảnh vừa gắn vẫn HIỆN trên màn soạn (không biến mất sau khi gắn thành công)", async () => {
    // jsdom gửi thân `File` qua XHR KHÔNG kèm byte thật, nên phép dò chữ ký byte của kho giả luôn
    // từ chối — ép hai bước cuối (xác nhận + gắn) trả đúng hình dạng hợp đồng. Điều đang kiểm là
    // màn soạn ĐỌC kết quả gắn ở đâu, không phải kho giả.
    server.use(
      http.post(`${API_BASE_URL}/api/v1/media/:mediaId/confirm`, ({ params }) =>
        HttpResponse.json({
          id: String(params.mediaId),
          kind: "IMAGE",
          contentType: "image/png",
          sizeBytes: 67,
          durationMs: null,
          alt: "Mái ngói nhà thờ họ",
          position: 0,
          url: null,
        })
      ),
      http.put(`${API_BASE_URL}/api/v1/posts/:id/media`, async ({ request, params }) => {
        const { mediaIds } = (await request.json()) as { mediaIds: string[] };
        return HttpResponse.json({
          id: String(params.id),
          version: 1,
          media: mediaIds.map((id, position) => ({
            id,
            kind: "IMAGE",
            contentType: "image/png",
            sizeBytes: 67,
            durationMs: null,
            alt: "Mái ngói nhà thờ họ",
            position,
            url: "data:image/png;base64,AAAA",
          })),
        });
      })
    );

    const { user } = renderWithProviders(<PostComposeScreen />, { role: "member" });
    const titleBox = await screen.findByLabelText("Tiêu đề");
    await user.type(titleBox, "Bài có ảnh");
    await user.tab();
    await screen.findByText(/Đã lưu nháp lúc/, undefined, { timeout: 10000 });

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await waitFor(() => expect(input).not.toBeDisabled());
    fireEvent.change(input, { target: { files: [tinyPng("nha-tho.png")] } });

    const altBox = await screen.findByLabelText("Chữ thay ảnh (alt)", undefined, { timeout: 10000 });
    await user.type(altBox, "Mái ngói nhà thờ họ");
    await user.click(screen.getByRole("button", { name: "Xong, gắn ảnh này vào bài" }));

    await waitFor(
      () => {
        expect(screen.getByRole("img", { name: "Mái ngói nhà thờ họ" })).toBeInTheDocument();
      },
      { timeout: 10000 }
    );
  });

  it("bài mới: gõ xong bấm THẲNG 'Gửi duyệt' (không rời ô trước) ⇒ gửi đi và dẫn sang 'Bài của tôi' kèm xác nhận", async () => {
    const submitted: string[] = [];
    server.events.on("request:start", ({ request }) => {
      if (request.method === "POST" && request.url.endsWith("/submit")) submitted.push(request.url);
    });

    const { user } = renderWithProviders(<PostComposeScreen />, { role: "member" });
    await user.type(await screen.findByLabelText("Tiêu đề"), "Xin ý kiến ngày họp");
    await user.click(screen.getByLabelText("Nội dung"));
    await user.type(screen.getByLabelText("Nội dung"), "Đề xuất họp vào 25 tháng Chạp.");
    // Bấm ngay khi con trỏ còn trong ô nội dung: `onBlur` khởi một lượt lưu (tạo nháp) đúng lúc
    // nút được bấm. Bản trước khoá nút vì đang lưu ⇒ cú bấm mất trắng; hoặc nhận `false` rồi dừng.
    await user.click(screen.getByRole("button", { name: /Gửi duyệt/ }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledTimes(1), { timeout: 10000 });
    server.events.removeAllListeners();
    expect(submitted).toHaveLength(1);
    expect(routerMock.push.mock.calls[0]?.[0]).toMatch(/^\/bai-viet\/cua-toi\?daGui=post-\d+$/);
  });

  it("nháp MỞ LẠI từ 'Bài của tôi': bấm 'Gửi duyệt' gửi thật, KHÔNG báo nhầm 'sửa ở nơi khác'", async () => {
    let submitCalls = 0;
    server.use(
      // Không trả gì ⇒ MSW rơi sang handler thật của mock (đếm lượt gọi mà không thay hành vi).
      http.post(`${API_BASE_URL}/api/v1/posts/post-1/submit`, () => {
        submitCalls += 1;
      })
    );
    window.history.replaceState(null, "", "/bai-viet/post-1/sua");

    const { user } = renderWithProviders(<PostComposeScreen postId="post-1" />, { role: "member" });
    const titleBox = await screen.findByLabelText("Tiêu đề");
    await waitFor(() => expect(titleBox).toHaveValue("Sửa lại đường vào từ đường trước giỗ Tổ"));

    await user.click(screen.getByRole("button", { name: /Gửi duyệt/ }));

    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith("/bai-viet/cua-toi?daGui=post-1"), {
      timeout: 10000,
    });
    expect(submitCalls).toBe(1);
    expect(screen.queryByText("Bài đã được sửa ở nơi khác")).not.toBeInTheDocument();
  });
});

describe("'Bài của tôi' sau khi gửi duyệt", () => {
  it("?daGui={id} ⇒ khối xác nhận nói rõ bài đang chờ ai duyệt", async () => {
    renderWithProviders(<MyPostsScreen justSubmittedId="post-2" />, { role: "member" });
    expect(await screen.findByText("Đã gửi bài đi duyệt")).toBeInTheDocument();
    await waitFor(() => {
      expect(
        screen.getByText(/«Xin ý kiến cả họ về ngày họp mặt cuối năm» đang chờ Trưởng chi hoặc Hội đồng Tộc biểu/)
      ).toBeInTheDocument();
    });
  });

  it("không có ?daGui ⇒ không có khối xác nhận", async () => {
    renderWithProviders(<MyPostsScreen />, { role: "member" });
    await screen.findByText(/Nháp đang viết dở \(/);
    expect(screen.queryByText("Đã gửi bài đi duyệt")).not.toBeInTheDocument();
  });
});

describe("hàng chờ duyệt bài", () => {
  it("không còn nút 'Sửa nhẹ rồi đăng' — máy chủ thật không có lối cho người duyệt sửa bài đang chờ", async () => {
    renderWithProviders(<PostReviewQueueScreen />, { role: "branch-head" });
    await screen.findAllByRole("button", { name: "Duyệt" }, { timeout: 10000 });
    expect(screen.queryByRole("button", { name: /Sửa nhẹ rồi đăng/ })).not.toBeInTheDocument();
  });
});

import { describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { TestLink, routerMock } from "../setup/next-navigation-mock";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import type { Problem } from "@/types/api";

vi.mock("@/i18n/navigation", () => ({
  Link: TestLink,
  usePathname: () => "/bai-viet/post-1/sua",
  useRouter: () => routerMock,
  redirect: vi.fn(),
  getPathname: () => "/bai-viet/post-1/sua",
}));

const { PostComposeScreen } = await import("@/components/posts/post-compose-screen");

function conflictProblem(): Problem {
  return {
    type: "about:blank",
    title: "Bài viết đã bị sửa ở nơi khác",
    status: 409,
    code: "OPTIMISTIC_LOCK_CONFLICT",
  };
}

/**
 * **Lưu nháp gặp `409` (ETag lệch) — nói được điều gì đã xảy ra, và KHÔNG
 * ĐƯỢC mất nội dung người dùng đang gõ** (checklist §2, "mất một bài viết dở
 * là cách chắc chắn nhất để người ta không viết bài thứ hai").
 *
 * `post-1` (mồi ở `src/mocks/handlers/posts.ts`) là nháp của `p-102`. Khoá
 * PATCH của handler thật để luôn trả `409`, mô phỏng đúng ca "một tab khác đã
 * lưu trước" — không đoán hành vi máy chủ, ép nó bằng `server.use`.
 */
describe("màn soạn bài — xung đột khi lưu nháp", () => {
  it("409 khi lưu: hiện rõ lý do, KHÔNG xoá chữ đang gõ trên màn hình", async () => {
    const user = renderWithProviders(<PostComposeScreen postId="post-1" />, { role: "member" }).user;

    const titleBox = await screen.findByLabelText("Tiêu đề");
    await waitFor(() => expect(titleBox).toHaveValue("Sửa lại đường vào từ đường trước giỗ Tổ"));

    server.use(
      http.patch(`${API_BASE_URL}/api/v1/posts/post-1`, () =>
        HttpResponse.json(conflictProblem(), { status: 409 })
      )
    );

    const bodyBox = screen.getByLabelText("Nội dung");
    const typed = "Đoạn vừa gõ thêm, chưa kịp lưu — không được mất.";
    await user.click(bodyBox);
    await user.type(bodyBox, typed);
    await user.tab(); // rời khỏi ô để kích hoạt lưu ngay (onBlur)

    await waitFor(() => {
      expect(screen.getByText("Bài đã được sửa ở nơi khác")).toBeInTheDocument();
    });

    // Chữ vừa gõ vẫn còn nguyên trên màn hình — hook không đụng vào state hiển thị khi lưu hỏng.
    expect((bodyBox as HTMLTextAreaElement).value).toContain(typed);
    expect(
      screen.getByRole("button", { name: "Tải bản mới nhất (bỏ nội dung đang gõ ở đây)" })
    ).toBeInTheDocument();
  });

  /**
   * Việc 3 của nhiệm vụ bài-viết-có-ảnh: soát lại đúng ca `412` (thiếu
   * `If-Match`) — KHÁC `409` (ETag lệch) nhưng phải dẫn tới cùng một kết cục:
   * không mất chữ đang gõ. Máy chủ thật trả `412` khi header vắng mặt hoàn
   * toàn; ép ca đó bằng `server.use` giống hệt cách bài kiểm `409` ở trên làm.
   */
  it("412 (thiếu If-Match) khi lưu: cũng KHÔNG xoá chữ đang gõ, và nói đúng lý do", async () => {
    const user = renderWithProviders(<PostComposeScreen postId="post-1" />, { role: "member" }).user;

    const titleBox = await screen.findByLabelText("Tiêu đề");
    await waitFor(() => expect(titleBox).toHaveValue("Sửa lại đường vào từ đường trước giỗ Tổ"));

    server.use(
      http.patch(`${API_BASE_URL}/api/v1/posts/post-1`, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Thiếu header If-Match",
            status: 412,
            code: "PRECONDITION_REQUIRED",
          } satisfies Problem,
          { status: 412 }
        )
      )
    );

    const bodyBox = screen.getByLabelText("Nội dung");
    const typed = "Câu vừa gõ khi máy chủ báo thiếu If-Match — vẫn phải còn nguyên.";
    await user.click(bodyBox);
    await user.type(bodyBox, typed);
    await user.tab();

    await waitFor(() => {
      expect(screen.getByText("Bài đã được sửa ở nơi khác")).toBeInTheDocument();
    });

    expect((bodyBox as HTMLTextAreaElement).value).toContain(typed);
  });
});

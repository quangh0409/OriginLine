import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import type { Problem } from "@/types/api";

const { MediaPicker } = await import("@/components/posts/media/media-picker");

/**
 * Máy chủ từ chối DỨT KHOÁT một tệp ở bước xác nhận (tệp đã bị xoá khỏi kho).
 *
 * Nghiệm thu bằng trình duyệt thật 24/09/2026 (`e2e/real-auth/nt-d-tv11-12.mjs`): một MP4 dài 200
 * giây mà trình duyệt không đọc được thời lượng trước khi tải ⇒ máy chủ trả `422 MEDIA_TOO_LONG`
 * ⇒ thẻ hiện NGUYÊN VĂN `detail` không dấu ("Doan video dai 200 giay…") kèm nút "Thử lại" — thử
 * lại đúng tệp ấy chắc chắn bị từ chối lần nữa. Nay: câu thiết kế sẵn (NT-TV-12), không có nút.
 */
function problem(code: Problem["code"], detail: string): Problem {
  return { type: "about:blank", title: "Tệp không hợp lệ", status: 422, code, detail };
}

function findFileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]');
  if (!input) throw new Error("Không tìm thấy input[type=file]");
  return input as HTMLInputElement;
}

describe("MediaPicker — máy chủ từ chối dứt khoát ở bước xác nhận", () => {
  it("MEDIA_TOO_LONG ⇒ câu thiết kế 'Video dài quá 120 giây…', KHÔNG hiện detail không dấu, KHÔNG có nút Thử lại", async () => {
    server.use(
      http.post(`${API_BASE_URL}/api/v1/media/:mediaId/confirm`, () =>
        HttpResponse.json(
          problem("MEDIA_TOO_LONG", "Doan video dai 200 giay, vuot gioi han 120 giay cua trang dong ho."),
          { status: 422 }
        )
      )
    );
    renderWithProviders(<MediaPicker postId="post-1" media={[]} editable />, { role: "member" });
    const input = findFileInput();
    await waitFor(() => expect(input).not.toBeDisabled());

    const video = new File([new Uint8Array(4096)], "video-200-giay.mp4", { type: "video/mp4" });
    fireEvent.change(input, { target: { files: [video] } });

    const card = await screen.findByText(
      "Video dài quá 120 giây. Cắt ngắn lại hoặc chia thành nhiều đoạn trước khi tải lên.",
      undefined,
      { timeout: 10000 }
    );
    const li = card.closest("li");
    expect(li?.textContent ?? "").not.toMatch(/Doan video dai/);
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });

  it("MEDIA_BAD_SIGNATURE ⇒ hiện lời máy chủ (chỉ nó biết tệp thật là gì), KHÔNG có nút Thử lại", async () => {
    const detail = "Tệp vừa tải lên là ảnh HEIC của iPhone — Trên iPhone mở Cài đặt › Máy ảnh › Định dạng.";
    server.use(
      http.post(`${API_BASE_URL}/api/v1/media/:mediaId/confirm`, () =>
        HttpResponse.json(problem("MEDIA_BAD_SIGNATURE", detail), { status: 422 })
      )
    );
    const { user } = renderWithProviders(<MediaPicker postId="post-1" media={[]} editable />, {
      role: "member",
    });
    const input = findFileInput();
    await waitFor(() => expect(input).not.toBeDisabled());

    // Nội dung HEIC mang đuôi .jpg — phép kiểm theo tên ở client không bắt được.
    fireEvent.change(input, {
      target: { files: [new File([new Uint8Array(64)], "anh-iphone.jpg", { type: "image/jpeg" })] },
    });
    const altBox = await screen.findByLabelText("Chữ thay ảnh (alt)", undefined, { timeout: 10000 });
    await user.type(altBox, "Ảnh iPhone");
    await user.click(screen.getByRole("button", { name: "Xong, gắn ảnh này vào bài" }));

    expect(await screen.findByText(detail, undefined, { timeout: 10000 })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
});

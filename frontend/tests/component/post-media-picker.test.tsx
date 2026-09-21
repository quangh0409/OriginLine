import { afterEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { renderWithProviders } from "../setup/render";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/lib/api/http";
import type { PostMediaItem } from "@/lib/api/media";

const { MediaPicker } = await import("@/components/posts/media/media-picker");

/**
 * Việc 1 — chọn và tải tệp (checklist ẢNH/VIDEO). Bốn ca bắt buộc của nhiệm
 * vụ, cả bốn đều dựng trên `post-1` — nháp `DRAFT` của tài khoản `member`
 * trong `src/mocks/handlers/posts.ts`, nên `MediaPicker` được mở khoá thật
 * (không phải trạng thái "chưa có nháp").
 */

function findFileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]');
  if (!input) throw new Error("Không tìm thấy input[type=file]");
  return input as HTMLInputElement;
}

/**
 * Chọn NHIỀU tệp cùng lúc, đúng như một lượt chọn thật trong hộp thoại của hệ
 * điều hành (MỘT sự kiện `change`, `input.files` chứa cả lô) — không dùng
 * `userEvent.upload(input, [a, b])` ở đây vì nó không tái tạo đúng ca này
 * trên `<input>` ẩn bằng `sr-only`, nên tệp thứ hai không bao giờ tới tay
 * `onChange`.
 */
function selectFiles(input: HTMLInputElement, files: File[]): void {
  // `fireEvent.change(input, { target: { files } })` — cách chuẩn của Testing
  // Library cho `<input type="file">`: nó tự lo việc gán `files` đúng cách mà
  // jsdom chấp nhận. Tự gọi `Object.defineProperty(input, "files", ...)` rồi
  // `fireEvent.change(input)` KHÔNG tương đương — component còn tự đặt
  // `e.target.value = ""` ngay trong `onChange` để cho phép chọn lại cùng một
  // tệp, và việc đó xoá luôn `files` đã gán tay theo cách không chuẩn.
  fireEvent.change(input, { target: { files } });
}

function tinyPng(name: string): File {
  // 1x1 PNG hợp lệ — đủ để trình duyệt/jsdom không báo lỗi giải mã sớm.
  const base64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return new File([bytes], name, { type: "image/png" });
}

function oversizedJpeg(name: string, sizeBytes: number): File {
  return new File([new Uint8Array(sizeBytes)], name, { type: "image/jpeg" });
}

/**
 * Chờ chính sách (`GET /media/policy`) tải xong — bộ chọn tệp bị khoá cho tới
 * lúc đó (xem `useMediaUploadQueue.disabled`), nên mọi lượt chọn tệp trong
 * các bài kiểm dưới đây phải chờ đúng mốc này trước, không phải chờ vu vơ.
 */
async function waitForPolicyLoaded(input: HTMLInputElement): Promise<void> {
  await waitFor(() => expect(input).not.toBeDisabled());
}

describe("MediaPicker — chọn tệp, kiểm trước khi gọi mạng, alt bắt buộc", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("tệp vượt trần dung lượng bị chặn NGAY, không một fetch nào được gọi cho nó", async () => {
    const { user } = renderWithProviders(
      <MediaPicker postId="post-1" media={[]} editable />,
      { role: "member" }
    );

    const input = findFileInput();
    await waitForPolicyLoaded(input);

    // Gắn gián điệp SAU KHI chính sách đã tải — lượt gọi `GET /media/policy`
    // lúc dựng màn không phải là thứ bài kiểm này đang canh.
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const big = oversizedJpeg("anh-qua-to.jpg", 9 * 1024 * 1024); // 9MiB > trần 8MiB
    await user.upload(input, big);

    // Câu báo lỗi nêu đúng tên tệp và nói rõ vượt trần — không phải "tải lên thất bại" chung chung.
    const card = await screen.findByText("anh-qua-to.jpg");
    expect(card.closest("li")?.textContent ?? "").toMatch(/MiB/);

    // Không một fetch nào được gọi cho lượt chọn này — kiểm chặn hoàn toàn ở
    // client, trước khi có bất kỳ lời gọi mạng nào.
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("một tệp sai định dạng giữa lô không chặn tệp hợp lệ còn lại", async () => {
    renderWithProviders(<MediaPicker postId="post-1" media={[]} editable />, { role: "member" });

    const input = findFileInput();
    await waitForPolicyLoaded(input);
    const bad = new File(["not-a-real-doc"], "tai-lieu.pdf", { type: "application/pdf" });
    const good = tinyPng("anh-hop-le.png");
    selectFiles(input, [bad, good]);

    // Tệp hỏng: có thẻ báo lỗi mang đúng tên, không phải một câu chung chung.
    const badCard = (await screen.findByText("tai-lieu.pdf")).closest("li");
    expect(badCard?.textContent ?? "").toMatch(/ảnh|video|jpg|png|webp|mp4|webm/i);

    // Tệp hợp lệ vẫn đi tiếp — cuối cùng dừng ở bước chờ nhập alt (ảnh không có
    // alt). Hạn chờ nới hơn mặc định — xem chú thích ở bài kiểm "alt bắt buộc".
    await waitFor(
      () => {
        expect(screen.getByText("Chữ thay ảnh (alt)")).toBeInTheDocument();
      },
      { timeout: 10000 }
    );
  });

  it("ảnh thiếu alt thì không gắn được vào bài — nút xác nhận khoá cho tới khi có alt", async () => {
    const { user } = renderWithProviders(
      <MediaPicker postId="post-1" media={[]} editable />,
      { role: "member" }
    );

    const input = findFileInput();
    await waitForPolicyLoaded(input);
    await user.upload(input, tinyPng("chan-dung.png"));

    // Chờ tải lên xong — tới bước cần alt. Hạn chờ nới hơn mặc định: đọc kích
    // thước tệp có một hạn 1200ms tự thân trước khi rơi về `null` (jsdom
    // không giải mã được `blob:`, xem `readMediaDimensions`), cộng độ trễ
    // thật 300ms của "kho" giả — mặc định 1000ms của `findBy*` không đủ.
    const confirmButton = await screen.findByRole(
      "button",
      { name: "Xong, gắn ảnh này vào bài" },
      { timeout: 10000 }
    );
    expect(confirmButton).toBeDisabled();

    const altBox = screen.getByLabelText("Chữ thay ảnh (alt)");
    expect(altBox).toBeInTheDocument();

    // Gõ alt — nút mở khoá.
    await user.type(altBox, "Chân dung cụ tổ chi Nhất");
    expect(confirmButton).toBeEnabled();

    await user.click(confirmButton);

    // Xác nhận xong — thẻ tạm biến mất khỏi hàng đợi cục bộ (đã thành một phần
    // thật của `post.media`, không còn là "đang chờ" nữa).
    await waitFor(() => {
      expect(screen.queryByText("Chữ thay ảnh (alt)")).not.toBeInTheDocument();
    });
  });

  it("HEIC bị từ chối kèm cách sửa cụ thể, không phải một câu 'định dạng không hợp lệ' trống rỗng", async () => {
    renderWithProviders(<MediaPicker postId="post-1" media={[]} editable />, { role: "member" });

    const input = findFileInput();
    await waitForPolicyLoaded(input);
    // Trình duyệt thường báo MIME rỗng cho HEIC — mô phỏng đúng ca xấu nhất
    // (client CHỈ còn đuôi tệp để nhận diện, không có gì ở `file.type`).
    const heic = new File(["fake-heic-bytes"], "IMG_4821.HEIC", { type: "" });
    selectFiles(input, [heic]);

    const card = await screen.findByText("IMG_4821.HEIC");
    const text = card.closest("li")?.textContent ?? "";
    // Câu cụ thể: nói đúng HEIC và một cách sửa thật (Cài đặt/Chia sẻ), không
    // phải "định dạng không hỗ trợ" chung chung.
    expect(text).toMatch(/HEIC/);
    expect(text).toMatch(/Cài đặt|Chia sẻ/);
  });

  it("sắp lại thứ tự dùng được HOÀN TOÀN bằng bàn phím, không chỉ kéo-thả", async () => {
    const media: PostMediaItem[] = [
      {
        id: "m1",
        kind: "IMAGE",
        url: "data:image/png;base64,AAAA",
        alt: "Ảnh một",
        order: 0,
        createdAt: "2026-01-01T00:00:00.000Z",
      },
      {
        id: "m2",
        kind: "IMAGE",
        url: "data:image/png;base64,BBBB",
        alt: "Ảnh hai",
        order: 1,
        createdAt: "2026-01-01T00:00:00.000Z",
      },
    ];

    let capturedOrder: string[] | null = null;
    server.use(
      http.patch(`${API_BASE_URL}/api/v1/posts/test-reorder-post/media/order`, async ({ request }) => {
        const body = (await request.json()) as { order: string[] };
        capturedOrder = body.order;
        return HttpResponse.json(
          {
            id: "test-reorder-post",
            media: capturedOrder.map((id, i) => ({ ...media.find((m) => m.id === id)!, order: i })),
            version: 2,
          },
          { headers: { ETag: '"v2"' } }
        );
      })
    );

    const { user } = renderWithProviders(
      <MediaPicker postId="test-reorder-post" media={media} editable />,
      { role: "member" }
    );

    const grid = await screen.findByTestId("media-grid");
    const moveRightButtons = within(grid).getAllByRole("button", { name: "Chuyển ảnh này ra sau" });
    // Nút của Ảnh hai (phần tử cuối) phải bị khoá — không có gì để chuyển ra sau nữa.
    expect(moveRightButtons[moveRightButtons.length - 1]).toBeDisabled();

    const firstMoveRight = moveRightButtons[0];
    expect(firstMoveRight).toBeDefined();
    firstMoveRight!.focus();
    expect(firstMoveRight).toHaveFocus();
    await user.keyboard("{Enter}");

    await waitFor(() => expect(capturedOrder).toEqual(["m2", "m1"]));
  });
});

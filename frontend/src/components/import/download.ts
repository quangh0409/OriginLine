import type { DownloadedFile } from "@/lib/api/data-import";

/**
 * Đưa một tệp đã tải về xuống máy người dùng.
 *
 * <h2>Vì sao không dùng một thẻ `<a href>` trỏ thẳng vào API</h2>
 * Hai endpoint tệp đều cần header: `Authorization` với bản chạy thật, và
 * `x-mock-role` dưới MSW. Trình duyệt **không** gắn header vào một lần điều
 * hướng thường, nên một liên kết trần sẽ nhận 401 ở môi trường thật trong khi
 * vẫn chạy ngon dưới bộ giả lập — đúng loại lỗi chỉ lộ ra sau khi nối backend.
 * Vậy nên: `fetch` có header, rồi `URL.createObjectURL`.
 *
 * `revokeObjectURL` gọi ngay sau khi bấm: đối tượng URL giữ nguyên cả Blob
 * trong bộ nhớ tab cho tới khi được thu hồi, và một buổi nhập liệu là hàng chục
 * lượt tải bản lỗi về.
 */
export function saveDownloadedFile({ blob, fileName }: DownloadedFile): void {
  if (typeof document === "undefined") return;
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = "noopener";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

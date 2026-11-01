"use client";

import { useCallback, useRef, useState } from "react";
import { ApiError } from "@/lib/api/http";
import {
  mediaApi,
  readVideoDurationSeconds,
  validateMediaFile,
  type MediaKind,
  type MediaPolicy,
  type MediaRejectReason,
} from "@/lib/api/media";
import { useConfirmAndAttachMedia } from "../queries";

export type PendingMediaStatus =
  | "uploading"
  | "needsAlt"
  | "confirming"
  | "error"
  | "rejected";

export interface PendingMediaItem {
  localId: string;
  kind: MediaKind | null;
  file: File;
  /** `URL.createObjectURL` — thu hồi khi tháo phần tử khỏi hàng đợi. */
  previewUrl: string | null;
  status: PendingMediaStatus;
  /** 0–100. Chỉ có ý nghĩa khi `status === "uploading"`. */
  progress: number;
  alt: string;
  errorMessage?: string;
  rejectReason?: MediaRejectReason;
  /** Chỉ có khi `rejectReason === "NAMED_FORMAT"` — khoá dịch `heic`/`gif`/`mov`/`mkv`. */
  namedFormatKey?: string;
  /**
   * Khoá của phiếu/tệp trên kho media (`UploadTicket.mediaId`) — gắn sau khi
   * `requestUploadTicket` trả lời. Cần cho bước xác nhận VÀ bước gắn vào bài
   * (cùng một khoá cho cả hai, khác bản trước dùng `uploadId` riêng).
   */
  mediaId?: string;
}

let seq = 0;
function nextLocalId(): string {
  seq += 1;
  return `local-media-${Date.now()}-${seq}`;
}

/**
 * Hàng đợi tải lên phía **client** cho màn soạn bài — Việc 1.
 *
 * <h2>Cần {@link MediaPolicy} — không còn hằng số FE</h2>
 * `policy` là `undefined` cho tới khi `GET /media/policy` trả lời; `addFiles`
 * từ chối thêm gì trong lúc đó (xem `disabled` ở giá trị trả về, và
 * `MediaPicker` khoá luôn bộ chọn tệp).
 *
 * <h2>Máy trạng thái của MỘT tệp</h2>
 * ```
 * (chọn tệp)
 *    │
 *    ├─ kiểm ĐỒNG BỘ trước khi gọi mạng thất bại (số lượng/tên/MIME/dung lượng)
 *    │     ──────────────────────────────────────────────► "rejected"
 *    │
 *    ▼
 * (VIDEO) đọc thời lượng cục bộ — vượt trần?
 *    │                                    │
 *   có ─────────────────────────► "rejected" (reason "DURATION", KHÔNG gọi mạng)
 *    │ không / không đọc được
 *    ▼
 * "uploading" (xin phiếu, PUT thẳng lên kho, có tiến trình %)
 *    │
 *    ├─ lỗi mạng/máy chủ (kể cả máy chủ từ chối vì đọc thời lượng thật ra khác)
 *    │     ──────────────────────────────────────────────► "error" (có nút thử lại)
 *    ▼
 * kind === IMAGE && chưa có alt ?
 *    │                                    │
 *   có                                  không
 *    ▼                                    ▼
 * "needsAlt" (chờ người dùng gõ)      "confirming" ──► xác nhận + gắn xong: BIẾN MẤT khỏi hàng
 *    │ (gõ xong, gọi confirmItem)                        đợi này — item giờ sống trong `post.media`
 *    ▼
 * "confirming" ──────────────────────────────────────► như trên
 * ```
 * "confirming" gộp HAI lời gọi thật (`POST /media/{id}/confirm` rồi
 * `PUT /posts/{id}/media`) — xem {@link useConfirmAndAttachMedia}. Hợp đồng
 * thật tách hai bước này (xác nhận trả một tệp trần trụi, chưa gắn vào bài),
 * khác bản trước tưởng một lượt xác nhận là xong.
 *
 * <h2>Một tệp hỏng KHÔNG được chặn các tệp còn lại</h2>
 * Mỗi tệp có `localId`, tiến trình và lỗi riêng; `addFiles` xử từng tệp độc
 * lập, không có `Promise.all` nào khiến một `reject` kéo cả lô xuống.
 *
 * <h2>Vì sao hook này không tự giữ "đã xong"</h2>
 * Ngay khi xác nhận + gắn thành công, tệp đã là một phần tử thật của
 * `post.media` trong cache React Query (xem `useConfirmAndAttachMedia`) — giữ
 * thêm một bản sao "đã xong" ở đây là hai nguồn sự thật cho cùng một tệp.
 */
export function useMediaUploadQueue(
  postId: string | null,
  currentAttachedCount: number,
  policy: MediaPolicy | undefined
) {
  const [items, setItems] = useState<PendingMediaItem[]>([]);
  const confirmAndAttach = useConfirmAndAttachMedia(postId ?? "");
  const abortControllers = useRef<Map<string, AbortController>>(new Map());

  const patch = useCallback((localId: string, changes: Partial<PendingMediaItem>) => {
    setItems((prev) => prev.map((it) => (it.localId === localId ? { ...it, ...changes } : it)));
  }, []);

  const drop = useCallback((localId: string) => {
    setItems((prev) => {
      const found = prev.find((it) => it.localId === localId);
      if (found?.previewUrl) URL.revokeObjectURL(found.previewUrl);
      return prev.filter((it) => it.localId !== localId);
    });
    abortControllers.current.get(localId)?.abort();
    abortControllers.current.delete(localId);
  }, []);

  // Giữ một bản tham chiếu luôn mới để các callback bên dưới đọc được
  // `mediaId`/`alt` mới nhất mà không phải đưa toàn bộ `items` vào dependency
  // array (thứ đổi liên tục khi tiến trình % cập nhật).
  const itemsRef = useRef<PendingMediaItem[]>([]);
  itemsRef.current = items;

  const runConfirm = useCallback(
    async (localId: string, kind: MediaKind, alt: string) => {
      patch(localId, { status: "confirming", errorMessage: undefined });
      try {
        const item = itemsRef.current.find((it) => it.localId === localId);
        if (!item?.mediaId) throw new Error("Chưa có mã tệp trên kho.");
        await confirmAndAttach.mutateAsync({
          mediaId: item.mediaId,
          alt: kind === "IMAGE" ? alt.trim() : undefined,
        });
        // Thành công — bỏ khỏi hàng đợi cục bộ, `post.media` giờ là nguồn thật.
        setItems((prev) => {
          const found = prev.find((it) => it.localId === localId);
          if (found?.previewUrl) URL.revokeObjectURL(found.previewUrl);
          return prev.filter((it) => it.localId !== localId);
        });
      } catch (caught) {
        const terminal = terminalRejection(caught);
        if (terminal) {
          patch(localId, terminal);
          return;
        }
        patch(localId, {
          status: kind === "IMAGE" && !alt.trim() ? "needsAlt" : "error",
          errorMessage: describeError(caught),
        });
      }
    },
    [confirmAndAttach, patch]
  );

  const uploadOne = useCallback(
    async (localId: string, file: File, kind: MediaKind) => {
      const controller = new AbortController();
      abortControllers.current.set(localId, controller);
      try {
        // Trần thời lượng video — CHẶN TRƯỚC KHI GỌI MẠNG. Đọc không được thì
        // để máy chủ quyết (nó đọc thật từ phần đầu tệp và từ chối nếu hỏng).
        // Không có ý nghĩa gì với ẢNH — `MediaAssetDto` không mang kích
        // thước, nên không còn lý do đọc chúng trước ở client.
        if (kind === "VIDEO" && policy) {
          const durationSeconds = await readVideoDurationSeconds(file);
          if (durationSeconds !== null && durationSeconds > policy.maxVideoDurationSeconds) {
            patch(localId, { status: "rejected", rejectReason: "DURATION" });
            return;
          }
        }
        if (!postId) throw new Error("Chưa có bản nháp để đính kèm tệp.");
        const ticket = await mediaApi.requestUploadTicket({ kind, sizeBytes: file.size });
        patch(localId, { mediaId: ticket.mediaId });
        await mediaApi.putToStorage(
          ticket.uploadUrl,
          file,
          (event) => patch(localId, { progress: Math.round((event.loaded / event.total) * 100) }),
          controller.signal
        );
        const current = itemsRef.current.find((it) => it.localId === localId);
        const alt = current?.alt ?? "";
        if (kind === "IMAGE" && !alt.trim()) {
          patch(localId, { status: "needsAlt", progress: 100 });
          return;
        }
        await runConfirm(localId, kind, alt);
      } catch (caught) {
        if (caught instanceof DOMException && caught.name === "AbortError") return;
        const terminal = terminalRejection(caught);
        if (terminal) {
          patch(localId, terminal);
          return;
        }
        patch(localId, { status: "error", errorMessage: describeError(caught) });
      } finally {
        abortControllers.current.delete(localId);
      }
    },
    [postId, policy, patch, runConfirm]
  );

  /**
   * Thêm một lô tệp. Kiểm TRƯỚC KHI GỌI MẠNG cho từng tệp — tệp vượt trần
   * hoặc sai định dạng dừng lại ngay tại đây, không một `fetch` nào được gọi
   * cho nó. Không làm gì nếu {@link MediaPolicy} chưa tải xong.
   *
   * <h2>Tính TRƯỚC, ghi state SAU — không tính bên trong updater của `setState`</h2>
   * Bản trước xây `toUpload` bên trong hàm cập nhật truyền cho `setItems`, rồi
   * đọc `toUpload` NGAY SAU lệnh gọi `setItems` đó, giả định React gọi hàm cập
   * nhật một cách đồng bộ. Giả định đó SAI: React không cam kết gọi hàm cập
   * nhật ngay tại chỗ (nó có thể hoãn sang khâu render), nên đoạn mã sau
   * `setItems` chạy trước khi hàm cập nhật kịp điền `toUpload` — hậu quả là
   * `uploadOne` không bao giờ được gọi, và tệp mãi kẹt ở "uploading 0%" mà
   * không một `console.error` nào tố cáo, vì không có gì thật sự ném lỗi.
   * Toàn bộ phép tính (kiểm từng tệp, gộp danh sách) giờ chạy trước, bằng dữ
   * liệu hiện có (`policy`, `currentCount`) — không đọc/ghi qua `setState`.
   */
  const addFiles = useCallback(
    (fileList: FileList | File[]) => {
      if (!policy) return;
      const files = Array.from(fileList);
      // Tệp đã bị từ chối không chiếm một chỗ trong trần — chỉ đếm tệp đang
      // thật sự trên đường tải lên hoặc đã gắn vào bài.
      let runningCount =
        currentAttachedCount + itemsRef.current.filter((it) => it.status !== "rejected").length;
      const additions: PendingMediaItem[] = [];
      const toUpload: Array<{ localId: string; file: File; kind: MediaKind }> = [];

      for (const file of files) {
        const localId = nextLocalId();
        const validation = validateMediaFile(file, runningCount, policy);
        if (!validation.ok) {
          additions.push({
            localId,
            kind: validation.kind,
            file,
            previewUrl: null,
            status: "rejected",
            progress: 0,
            alt: "",
            rejectReason: validation.reason,
            namedFormatKey: validation.namedFormatKey,
          });
          continue;
        }
        runningCount += 1;
        const kind = validation.kind;
        additions.push({
          localId,
          kind,
          file,
          previewUrl: URL.createObjectURL(file),
          status: "uploading",
          progress: 0,
          alt: "",
        });
        toUpload.push({ localId, file, kind });
      }

      setItems((prev) => [...prev, ...additions]);
      for (const job of toUpload) {
        void uploadOne(job.localId, job.file, job.kind);
      }
    },
    [currentAttachedCount, policy, uploadOne]
  );

  const setAlt = useCallback((localId: string, alt: string) => {
    patch(localId, { alt });
  }, [patch]);

  const confirmWithAlt = useCallback(
    (localId: string) => {
      const item = itemsRef.current.find((it) => it.localId === localId);
      if (!item || item.kind !== "IMAGE") return;
      if (!item.alt.trim()) return; // nút gọi hàm này đã bị khoá khi alt rỗng — chốt lần hai.
      void runConfirm(localId, "IMAGE", item.alt);
    },
    [runConfirm]
  );

  const retry = useCallback(
    (localId: string) => {
      const item = itemsRef.current.find((it) => it.localId === localId);
      if (!item || !item.kind) return;
      patch(localId, { status: "uploading", progress: 0, errorMessage: undefined });
      void uploadOne(localId, item.file, item.kind);
    },
    [patch, uploadOne]
  );

  return {
    items,
    addFiles,
    removeItem: drop,
    setAlt,
    confirmWithAlt,
    retry,
    /** Bài chưa có bản nháp, hoặc chính sách chưa tải xong — khoá bộ chọn tệp thay vì để người dùng chọn rồi mất tệp. */
    disabled: !postId || !policy,
  };
}

/**
 * Máy chủ từ chối DỨT KHOÁT (tệp đã bị xoá khỏi kho — xem `/media/{id}/confirm` trong hợp đồng):
 * chuyển thẻ sang "rejected", KHÔNG phải "error".
 *
 * <h2>Vì sao không để "error" như trước</h2>
 * "error" hiện nút "Thử lại", và thử lại đúng tệp ấy chắc chắn nhận lại đúng lời từ chối ấy —
 * một nút hứa điều không làm được. Nghiệm thu bằng trình duyệt thật (24/09/2026) còn thấy lỗi thứ
 * hai: với `MEDIA_TOO_LONG` thẻ hiện nguyên `detail` của máy chủ, vốn là câu KHÔNG DẤU ("Doan video
 * dai 200 giay…") và không có bản tiếng Anh. Trần thời lượng/dung lượng đã có câu thiết kế sẵn
 * trong `posts.media.rejected.*` (kèm cách chữa), nên hai mã ấy đi về đúng lý do `DURATION`/`SIZE`
 * — cùng câu mà phép kiểm phía client hiện khi nó tự bắt được trước khi tải lên.
 *
 * `MEDIA_BAD_SIGNATURE` giữ `detail` của máy chủ: chỉ máy chủ biết tệp THẬT là gì (HEIC mang đuôi
 * `.jpg`, GIF đổi tên…), và câu ấy đã kèm cách sửa cho từng định dạng.
 */
function terminalRejection(caught: unknown): Partial<PendingMediaItem> | null {
  if (!(caught instanceof ApiError)) return null;
  switch (caught.code) {
    case "MEDIA_TOO_LONG":
      return { status: "rejected", rejectReason: "DURATION", errorMessage: undefined };
    case "MEDIA_TOO_LARGE":
      return { status: "rejected", rejectReason: "SIZE", errorMessage: undefined };
    case "MEDIA_BAD_SIGNATURE":
      return {
        status: "rejected",
        rejectReason: "SERVER",
        errorMessage: caught.problem?.detail ?? undefined,
      };
    default:
      return null;
  }
}

function describeError(caught: unknown): string {
  if (caught instanceof ApiError) {
    switch (caught.code) {
      case "MEDIA_TOO_LARGE":
        return caught.problem?.detail ?? "Tệp vượt quá dung lượng cho phép.";
      case "VALIDATION_FAILED":
        return caught.problem?.detail ?? "Máy chủ từ chối tệp này.";
      case "MEDIA_BAD_SIGNATURE":
        return caught.problem?.detail ?? "Định dạng tệp không đúng như đã khai — thử chọn lại tệp.";
      case "MEDIA_TOO_LONG":
        return caught.problem?.detail ?? "Video dài quá trần cho phép.";
      case "MEDIA_DURATION_UNKNOWN":
        return caught.problem?.detail ?? "Không đọc được thời lượng video — tệp có thể đã hỏng, thử một tệp khác.";
      case "MEDIA_NOT_UPLOADED":
      case "MEDIA_TICKET_CLOSED":
        return "Phiếu tải lên đã hết hạn hoặc tệp chưa thật sự tới kho. Xin thử lại từ đầu.";
      case "MEDIA_NOT_OWNED":
        return "Phiếu tải lên này không phải của bạn.";
      case "MEDIA_STORAGE_UNAVAILABLE":
        return "Kho tệp đang không phản hồi. Xin thử lại sau ít phút.";
      default:
        return caught.problem?.title ?? "Không tải lên được. Xin thử lại.";
    }
  }
  if (caught instanceof Error) return caught.message;
  return "Không tải lên được. Xin thử lại.";
}

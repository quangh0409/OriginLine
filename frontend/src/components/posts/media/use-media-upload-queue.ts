"use client";

import { useCallback, useRef, useState } from "react";
import { ApiError } from "@/lib/api/http";
import {
  mediaApi,
  readMediaMeta,
  validateMediaFile,
  type MediaKind,
  type MediaPolicy,
  type MediaRejectReason,
} from "@/lib/api/media";
import { useConfirmMediaUpload } from "../queries";

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
  /** Gắn sau khi `requestUpload` trả lời — cần cho bước xác nhận. */
  uploadId?: string;
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
 * "uploading" (xin URL đã ký rồi PUT thẳng lên kho, có tiến trình %)
 *    │
 *    ├─ lỗi mạng/máy chủ (kể cả máy chủ từ chối vì đọc thời lượng thật ra khác)
 *    │     ──────────────────────────────────────────────► "error" (có nút thử lại)
 *    ▼
 * kind === IMAGE && chưa có alt ?
 *    │                                    │
 *   có                                  không
 *    ▼                                    ▼
 * "needsAlt" (chờ người dùng gõ)      "confirming" ──► xác nhận xong: BIẾN MẤT khỏi hàng đợi này —
 *    │ (gõ xong, gọi confirmItem)                        item giờ sống trong `post.media` (nguồn thật)
 *    ▼
 * "confirming" ──────────────────────────────────────► như trên
 * ```
 *
 * <h2>Một tệp hỏng KHÔNG được chặn các tệp còn lại</h2>
 * Mỗi tệp có `localId`, tiến trình và lỗi riêng; `addFiles` xử từng tệp độc
 * lập, không có `Promise.all` nào khiến một `reject` kéo cả lô xuống.
 *
 * <h2>Vì sao hook này không tự giữ "đã xong"</h2>
 * Ngay khi `confirmUpload` thành công, tệp đã là một phần tử thật của
 * `post.media` trong cache React Query (xem `useConfirmMediaUpload`) — giữ
 * thêm một bản sao "đã xong" ở đây là hai nguồn sự thật cho cùng một tệp.
 */
export function useMediaUploadQueue(
  postId: string | null,
  currentAttachedCount: number,
  policy: MediaPolicy | undefined
) {
  const [items, setItems] = useState<PendingMediaItem[]>([]);
  const confirmMutation = useConfirmMediaUpload(postId ?? "");
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

  const runConfirm = useCallback(
    async (
      localId: string,
      kind: MediaKind,
      alt: string,
      width?: number,
      height?: number,
      durationSeconds?: number
    ) => {
      patch(localId, { status: "confirming", errorMessage: undefined });
      try {
        const item = itemsRef.current.find((it) => it.localId === localId);
        if (!item?.uploadId) throw new Error("Chưa có mã lượt tải lên.");
        await confirmMutation.mutateAsync({
          uploadId: item.uploadId,
          alt: kind === "IMAGE" ? alt.trim() : undefined,
          width,
          height,
          durationSeconds: kind === "VIDEO" ? durationSeconds : undefined,
        });
        // Thành công — bỏ khỏi hàng đợi cục bộ, `post.media` giờ là nguồn thật.
        setItems((prev) => {
          const found = prev.find((it) => it.localId === localId);
          if (found?.previewUrl) URL.revokeObjectURL(found.previewUrl);
          return prev.filter((it) => it.localId !== localId);
        });
      } catch (caught) {
        patch(localId, {
          status: kind === "IMAGE" && !alt.trim() ? "needsAlt" : "error",
          errorMessage: describeError(caught),
        });
      }
    },
    [confirmMutation, patch]
  );

  // Giữ một bản tham chiếu luôn mới để các callback bên dưới đọc được
  // `uploadId`/`alt` mới nhất mà không phải đưa toàn bộ `items` vào
  // dependency array (thứ đổi liên tục khi tiến trình % cập nhật).
  const itemsRef = useRef<PendingMediaItem[]>([]);
  itemsRef.current = items;

  const uploadOne = useCallback(
    async (localId: string, file: File, kind: MediaKind) => {
      const controller = new AbortController();
      abortControllers.current.set(localId, controller);
      try {
        const meta = await readMediaMeta(file, kind);
        // Trần thời lượng video — CHẶN TRƯỚC KHI GỌI MẠNG. Đọc không được thì
        // để máy chủ quyết (nó đọc thật từ phần đầu tệp và từ chối nếu hỏng).
        if (
          kind === "VIDEO" &&
          policy &&
          meta?.durationSeconds !== undefined &&
          meta.durationSeconds > policy.video.maxDurationSeconds
        ) {
          patch(localId, { status: "rejected", rejectReason: "DURATION" });
          return;
        }
        if (!postId) throw new Error("Chưa có bản nháp để đính kèm tệp.");
        const upload = await mediaApi.requestUpload(postId, {
          kind,
          fileName: file.name,
          contentType: file.type,
          sizeBytes: file.size,
        });
        patch(localId, { uploadId: upload.uploadId });
        await mediaApi.putToStorage(
          upload.uploadUrl,
          file,
          upload.uploadHeaders,
          (event) => patch(localId, { progress: Math.round((event.loaded / event.total) * 100) }),
          controller.signal
        );
        const current = itemsRef.current.find((it) => it.localId === localId);
        const alt = current?.alt ?? "";
        if (kind === "IMAGE" && !alt.trim()) {
          patch(localId, { status: "needsAlt", progress: 100 });
          return;
        }
        await runConfirm(localId, kind, alt, meta?.width, meta?.height, meta?.durationSeconds);
      } catch (caught) {
        if (caught instanceof DOMException && caught.name === "AbortError") return;
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

function describeError(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.code === "VALIDATION_FAILED") {
      return caught.problem?.detail ?? "Máy chủ từ chối tệp này.";
    }
    if (caught.status === 413) return "Tệp vượt quá dung lượng cho phép.";
    return caught.problem?.title ?? "Không tải lên được. Xin thử lại.";
  }
  if (caught instanceof Error) return caught.message;
  return "Không tải lên được. Xin thử lại.";
}

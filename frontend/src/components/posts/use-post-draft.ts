"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/http";
import { postsApi, type PostDto } from "@/lib/api/posts";
import { postKeys, type PostSnapshot } from "./queries";

export type DraftStatus =
  | "idle"
  | "saving"
  | "saved"
  | "conflict"
  | "sessionExpired"
  | "forbidden"
  | "error";

export interface PostDraftValues {
  title: string;
  body: string;
}

export interface UsePostDraftOptions {
  /** `null` khi bài chưa từng được tạo — lượt lưu đầu tiên `POST`, không `PATCH`. */
  postId: string | null;
  etag: string | null;
  /** Tắt hẳn autosave — dùng khi không còn được sửa nữa (đã đăng/đã gỡ, hoặc chưa đủ quyền). */
  enabled: boolean;
  /** Gọi đúng một lần, ngay sau lượt tạo nháp đầu tiên thành công. */
  onCreated?: (post: PostDto) => void;
  /** Chu kỳ lưu tự động khi có thay đổi (mili-giây). */
  intervalMs?: number;
}

const DEFAULT_INTERVAL_MS = 8_000;

function sameValues(a: PostDraftValues | null, b: PostDraftValues | null): boolean {
  if (a === b) return true;
  if (!a || !b) return false;
  return a.title === b.title && a.body === b.body;
}

/**
 * Lưu nháp tự động cho màn soạn bài — **không phải trang trí** (checklist §2,
 * design 07 §2 "mất một bài viết dở là cách chắc chắn nhất để người ta không
 * viết bài thứ hai").
 *
 * <h2>Vì sao một bộ đếm giờ cố định, không phải debounce theo phím gõ</h2>
 * Người viết là bác 60 tuổi, gõ chậm và hay dừng tay để nghĩ câu tiếp theo.
 * Debounce ngắn theo phím gõ sẽ lưu ngay giữa một câu dở dang mỗi lần ông/bà
 * dừng gõ; debounce dài thì không lưu được nếu ông/bà gõ liên tục nhiều phút.
 * Một chu kỳ cố định (mặc định 8 giây) lưu bất cứ khi nào nội dung đã đổi kể
 * từ lần lưu trước, không phụ thuộc nhịp gõ.
 *
 * <h2>Ba ca hỏng — và luật chung là KHÔNG BAO GIỜ đụng vào chữ đang gõ</h2>
 * <ul>
 *   <li><b>412/409</b> — thiếu `If-Match` hoặc ETag lệch (tab khác đã lưu đè).
 *       Dừng đếm giờ, chuyển {@code "conflict"}. {@link reloadFromServer} là
 *       lối ra DUY NHẤT và luôn cần người dùng bấm xác nhận — không bao giờ tự
 *       động lấy bản trên máy chủ đè lên chữ đang gõ.</li>
 *   <li><b>401 sau khi làm mới token thất bại</b> ({@code UNAUTHENTICATED}) —
 *       phiên đã hết hạn thật (2 giờ không thao tác là chuyện có thật ở sản
 *       phẩm này, xem CLAUDE.md). Chuyển {@code "sessionExpired"}; nội dung vẫn
 *       nguyên trong state của màn hình vì hook không xoá {@code values} khi
 *       lưu hỏng — nó chỉ đọc, chưa từng viết vào state hiển thị.</li>
 *   <li><b>Lỗi khác</b> (mất mạng, 5xx) — {@code "error"}, thử lại ở lượt đếm
 *       giờ kế tiếp.</li>
 * </ul>
 *
 * Hook này không giữ `title`/`body` — màn hình gọi nó vẫn là chủ của state
 * hiển thị (React input state), tránh việc một lỗi lưu kéo theo mất luôn nội
 * dung đang hiện trên màn hình.
 */
export function usePostDraft({
  postId: initialPostId,
  etag: initialEtag,
  enabled,
  onCreated,
  intervalMs = DEFAULT_INTERVAL_MS,
}: UsePostDraftOptions) {
  const queryClient = useQueryClient();
  const [postId, setPostId] = useState(initialPostId);
  const [etag, setEtag] = useState(initialEtag);
  const [status, setStatus] = useState<DraftStatus>("idle");
  const [error, setError] = useState<ApiError | null>(null);
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null);

  const latestValues = useRef<PostDraftValues | null>(null);
  const lastSavedValues = useRef<PostDraftValues | null>(null);
  const savingRef = useRef(false);
  const blockedRef = useRef(false);
  const postIdRef = useRef(postId);
  const etagRef = useRef(etag);

  postIdRef.current = postId;
  etagRef.current = etag;
  blockedRef.current = status === "conflict" || status === "sessionExpired" || status === "forbidden";

  const isDirty = useCallback((values: PostDraftValues) => {
    if (!lastSavedValues.current) return Boolean(values.title.trim() || values.body.trim());
    return !sameValues(values, lastSavedValues.current);
  }, []);

  const saveNow = useCallback(
    async (values: PostDraftValues): Promise<boolean> => {
      latestValues.current = values;
      if (!enabled) return false;
      if (savingRef.current) return false;
      if (!isDirty(values)) return true;

      savingRef.current = true;
      setStatus("saving");
      setError(null);
      try {
        const currentId = postIdRef.current;
        if (!currentId) {
          const { data, etag: newEtag } = await postsApi.create({
            title: values.title,
            body: values.body,
          });
          setPostId(data.id);
          setEtag(newEtag);
          lastSavedValues.current = values;
          setLastSavedAt(data.updatedAt);
          setStatus("saved");
          queryClient.setQueryData<PostSnapshot>(postKeys.detail(data.id), {
            post: data,
            etag: newEtag,
          });
          onCreated?.(data);
          return true;
        }

        const currentEtag = etagRef.current;
        if (!currentEtag) {
          // Không có ETag để làm `If-Match` — coi như xung đột, không đoán liều.
          setStatus("conflict");
          return false;
        }

        const { data, etag: newEtag } = await postsApi.update(
          currentId,
          {
            title: values.title,
            body: values.body,
          },
          currentEtag
        );
        setEtag(newEtag);
        lastSavedValues.current = values;
        setLastSavedAt(data.updatedAt);
        setStatus("saved");
        queryClient.setQueryData<PostSnapshot>(postKeys.detail(currentId), {
          post: data,
          etag: newEtag,
        });
        return true;
      } catch (caught) {
        if (caught instanceof ApiError) {
          setError(caught);
          if (caught.code === "OPTIMISTIC_LOCK_CONFLICT" || caught.code === "PRECONDITION_REQUIRED") {
            setStatus("conflict");
          } else if (caught.code === "UNAUTHENTICATED") {
            setStatus("sessionExpired");
          } else if (caught.code === "FORBIDDEN" || caught.code === "BRANCH_SCOPE_VIOLATION") {
            setStatus("forbidden");
          } else {
            setStatus("error");
          }
        } else {
          setStatus("error");
        }
        return false;
      } finally {
        savingRef.current = false;
      }
    },
    [enabled, isDirty, onCreated, queryClient]
  );

  /** Gọi ở mỗi lần người dùng gõ — chỉ cập nhật giá trị mới nhất cho lượt đếm giờ kế tiếp, không lưu ngay. */
  const notifyChange = useCallback((values: PostDraftValues) => {
    latestValues.current = values;
  }, []);

  useEffect(() => {
    if (!enabled) return undefined;
    const timer = window.setInterval(() => {
      if (blockedRef.current || savingRef.current) return;
      const values = latestValues.current;
      if (!values || !isDirty(values)) return;
      void saveNow(values);
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [enabled, intervalMs, isDirty, saveNow]);

  /**
   * Người dùng đã bấm xác nhận muốn xem bản trên máy chủ, chấp nhận bỏ thay
   * đổi tại chỗ của mình. Không nơi nào khác trong hook này được gọi hàm này.
   */
  const reloadFromServer = useCallback(async (): Promise<PostDto | null> => {
    const currentId = postIdRef.current;
    if (!currentId) return null;
    const { data, etag: newEtag } = await postsApi.getById(currentId);
    setEtag(newEtag);
    lastSavedValues.current = { title: data.title, body: data.body };
    setStatus("idle");
    setError(null);
    queryClient.setQueryData<PostSnapshot>(postKeys.detail(currentId), { post: data, etag: newEtag });
    return data;
  }, [queryClient]);

  /** Sau khi người dùng đăng nhập lại — thử lưu lại đúng nội dung đang có trên màn hình. */
  const retryAfterSessionRestored = useCallback(() => {
    setStatus("idle");
    setError(null);
    const values = latestValues.current;
    if (values) void saveNow(values);
  }, [saveNow]);

  return {
    postId,
    etag,
    status,
    error,
    lastSavedAt,
    saveNow,
    notifyChange,
    reloadFromServer,
    retryAfterSessionRestored,
  };
}

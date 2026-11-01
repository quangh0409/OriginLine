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

/**
 * Giờ hiện ở dòng "Đã lưu nháp lúc …". Ưu tiên `updatedAt` của máy chủ, nhưng rơi về đồng hồ máy
 * này khi phản hồi không mang nó: backend trước 24/09/2026 không đọc lại cột do CSDL đặt sau
 * INSERT, và `PostDto` là NON_NULL — nên phản hồi `POST /posts` thiếu hẳn `updatedAt` và dòng xác
 * nhận không bao giờ hiện. "Đã lưu" là điều người viết cần biết; giờ của máy họ là đủ đúng cho
 * câu ấy, còn im lặng thì không.
 */
function savedAtOf(post: PostDto): string {
  return post.updatedAt || new Date().toISOString();
}

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
  /** Lượt lưu đang bay — để lượt gọi sau CHỜ nó xong rồi lưu tiếp, không bỏ ngang. */
  const inflightRef = useRef<Promise<boolean> | null>(null);
  const blockedRef = useRef(false);
  const postIdRef = useRef(postId);
  const etagRef = useRef(etag);

  postIdRef.current = postId;
  etagRef.current = etag;

  /*
   * ETag của một nháp MỞ LẠI tới SAU lượt render đầu — `usePost(id)` còn đang tải khi hook này
   * dựng, nên `useState(initialEtag)` chụp đúng `null` và không bao giờ đổi nữa. Hậu quả đo được
   * bằng trình duyệt thật (24/09/2026): mở một nháp từ "Bài của tôi", bấm "Gửi duyệt" ⇒
   * `saveNow` thấy không có ETag ⇒ coi là xung đột ⇒ hiện "Bài đã được sửa ở nơi khác" và KHÔNG
   * gửi gì cả. Mọi nháp mở lại đều không gửi duyệt được. Nhận ETag khi nó tới — nhưng chỉ khi hook
   * chưa tự có ETag từ một lượt lưu của chính nó (ETag ấy mới hơn bản đọc về).
   */
  useEffect(() => {
    if (initialEtag && !etagRef.current) setEtag(initialEtag);
  }, [initialEtag]);
  blockedRef.current = status === "conflict" || status === "sessionExpired" || status === "forbidden";

  const isDirty = useCallback((values: PostDraftValues) => {
    if (!lastSavedValues.current) return Boolean(values.title.trim() || values.body.trim());
    return !sameValues(values, lastSavedValues.current);
  }, []);

  /**
   * Lưu ngay nội dung cho trước.
   *
   * <h2>Đang có một lượt lưu bay thì CHỜ, không trả `false`</h2>
   * Bản trước trả `false` khi đang lưu — và lượt đang lưu gần như luôn có mặt đúng lúc người dùng
   * bấm "Gửi duyệt": nhấn chuột vào nút làm ô nội dung mất tiêu điểm, `onBlur` khởi một lượt lưu,
   * rồi `onClick` gọi lại hàm này, nhận `false`, và `handleSubmitForReview` lặng lẽ dừng. Cú bấm
   * đầu tiên mất trắng. Nay lượt gọi sau chờ lượt trước xong rồi lưu nốt phần còn đổi (nếu có).
   *
   * <p>Vì các lượt lưu nối đuôi nhau có thể chạy TRƯỚC khi React kịp render lại, `postIdRef`/
   * `etagRef` được ghi thẳng ngay khi có phản hồi — nếu chỉ chờ render thì lượt thứ hai thấy
   * `postId` còn rỗng và TẠO THÊM một bản nháp trùng.</p>
   */
  const saveNow = useCallback(
    async (values: PostDraftValues): Promise<boolean> => {
      latestValues.current = values;
      if (!enabled) return false;
      while (inflightRef.current) {
        await inflightRef.current.catch(() => false);
      }
      if (!isDirty(values)) return true;

      // Chưa có bản ghi mà tiêu đề còn trống: KHÔNG gọi mạng. Tiêu đề là trường duy nhất hợp đồng
      // bắt buộc ở lượt tạo nháp (`CreatePostRequest.required: [title]`) — gửi đi chỉ để nhận 400
      // rồi hiện "Chưa lưu được nháp" cho một người mới gõ chữ đầu tiên vào ô nội dung.
      if (!postIdRef.current && !values.title.trim()) return false;

      savingRef.current = true;
      setStatus("saving");
      setError(null);
      const run = async (): Promise<boolean> => {
        try {
          const currentId = postIdRef.current;
          if (!currentId) {
            const { data, etag: newEtag } = await postsApi.create({
              title: values.title,
              body: values.body,
            });
            postIdRef.current = data.id;
            etagRef.current = newEtag;
            setPostId(data.id);
            setEtag(newEtag);
            lastSavedValues.current = values;
            setLastSavedAt(savedAtOf(data));
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
          etagRef.current = newEtag;
          setEtag(newEtag);
          lastSavedValues.current = values;
          setLastSavedAt(savedAtOf(data));
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
      };
      const pending = run();
      inflightRef.current = pending;
      // Dọn SAU khi đã gán — `run()` có nhánh trả về đồng bộ (thiếu ETag); dọn trong `finally` của
      // nó sẽ chạy TRƯỚC phép gán trên và để lại một promise đã xong mãi trong ref, khiến vòng
      // `while` ở đầu hàm quay vô tận.
      void pending.finally(() => {
        if (inflightRef.current === pending) inflightRef.current = null;
      });
      return pending;
    },
    [enabled, isDirty, onCreated, queryClient]
  );

  /** Khoá bài hiện tại, đọc thẳng từ ref — đúng ngay sau `await saveNow(...)`, trước lượt render. */
  const currentPostId = useCallback((): string | null => postIdRef.current, []);

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
    currentPostId,
    notifyChange,
    reloadFromServer,
    retryAfterSessionRestored,
  };
}

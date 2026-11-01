"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/http";
import {
  postsApi,
  type FeedPostsParams,
  type ListPostsParams,
  type PostDto,
  type PostPage,
  type PostWriteBody,
  type ReviewPostBody,
} from "@/lib/api/posts";
import { mediaApi } from "@/lib/api/media";

/**
 * Khoá React Query của nhóm bài viết.
 *
 * Cùng quy ước với `src/components/claim/use-claims.ts`: gom cạnh hook dùng nó
 * vì đây là một mảnh còn mới, chưa đủ ổn định để chuyển vào
 * `src/lib/query/keys.ts` (tệp dùng chung, đang có nhóm khác sửa song song).
 */
export const postKeys = {
  feed: (limit: number) => ["posts-feed", limit] as const,
  list: (params: ListPostsParams) => ["posts-list", params] as const,
  detail: (id: string) => ["post", id] as const,
};

/** Khối "Bài mới" ở trang chủ — chỉ bài đã đăng, ai xem cũng như nhau. */
export function usePostsFeed(params: FeedPostsParams = {}) {
  const limit = params.limit ?? 6;
  return useQuery<PostDto[]>({
    queryKey: postKeys.feed(limit),
    queryFn: () => postsApi.feed({ limit }),
    staleTime: 30_000,
  });
}

/**
 * Hàng chờ duyệt / danh sách theo trạng thái. Phạm vi do máy chủ cắt — xem
 * `PostReviewQueueScreen` (dùng `items` thôi) và `PostListScreen`/
 * `MyPostsScreen` (dùng cả `page` để phân trang / gộp nhiều trạng thái).
 */
export function usePostsList(params: ListPostsParams) {
  return useQuery<PostPage>({
    queryKey: postKeys.list(params),
    queryFn: () => postsApi.list(params),
    staleTime: 0,
  });
}

export interface PostSnapshot {
  post: PostDto;
  etag: string | null;
}

export function usePost(id: string | undefined) {
  return useQuery<PostSnapshot>({
    queryKey: postKeys.detail(id ?? ""),
    queryFn: async () => {
      const { data, etag } = await postsApi.getById(id as string);
      return { post: data, etag };
    },
    enabled: Boolean(id),
    staleTime: 0,
  });
}

export function useCreatePost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: PostWriteBody) => postsApi.create(body),
    onSuccess: ({ data, etag }) => {
      queryClient.setQueryData<PostSnapshot>(postKeys.detail(data.id), { post: data, etag });
    },
  });
}

function invalidatePostLists(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["posts-feed"] });
  void queryClient.invalidateQueries({ queryKey: ["posts-list"] });
}

/**
 * Nhận `id` ở LƯỢT GỌI (`mutate(id)`), không ở lúc tạo hook.
 *
 * Cố ý khác `useUpdatePerson(id)` — ở màn soạn bài, `id` có thể còn là `null`
 * lúc component dựng lần đầu (bài chưa từng được tạo) và chỉ có giá trị thật
 * SAU một lượt lưu nháp bất đồng bộ. Khoá `id` vào lúc tạo hook sẽ đóng lại
 * một closure với `id=""` từ lượt render trước lượt lưu ấy — đúng loại lỗi mà
 * `usePersonSubmit`/`ChangeRequest` không gặp vì `id` của chúng luôn có sẵn
 * từ đầu.
 */
export function useSubmitPost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => postsApi.submit(id),
    onSuccess: (data) => {
      queryClient.setQueryData<PostSnapshot>(postKeys.detail(data.id), (prev) => ({
        post: data,
        etag: prev?.etag ?? null,
      }));
      invalidatePostLists(queryClient);
    },
  });
}

export function useReviewPost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReviewPostBody }) => postsApi.review(id, body),
    onSuccess: (data) => {
      queryClient.setQueryData<PostSnapshot>(postKeys.detail(data.id), (prev) => ({
        post: data,
        etag: prev?.etag ?? null,
      }));
      invalidatePostLists(queryClient);
    },
  });
}

export function useWithdrawPost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => postsApi.withdraw(id),
    onSuccess: (data) => {
      queryClient.setQueryData<PostSnapshot>(postKeys.detail(data.id), (prev) => ({
        post: data,
        etag: prev?.etag ?? null,
      }));
      invalidatePostLists(queryClient);
    },
  });
}

/**
 * Ghi thẳng kết quả một thao tác media (gắn tệp mới / sắp lại thứ tự / gỡ —
 * cả ba đều đi qua `postsApi.setMedia`, xem `currentMediaIds` bên dưới) vào
 * `postKeys.detail(postId)` — **cùng một cách** bốn hook `usePost*` phía trên
 * đã làm với thân bài. Không dùng `invalidateQueries` rồi refetch: refetch là
 * một lượt gọi mạng nữa mà kết quả này đã có đủ, và trong lúc chờ nó có một
 * khoảng trắng mà lưới ảnh nhấp nháy hoặc rơi về rỗng.
 *
 * <p>Không còn "sửa `alt`" trong danh sách thao tác — hợp đồng thật
 * (`AttachMediaRequest`) chỉ nhận `mediaIds`, không có trường nào mang `alt`;
 * `alt` chỉ đặt được đúng một lần, ở bước xác nhận. Xem javadoc
 * `src/lib/api/media.ts`.</p>
 */
function patchPostMedia(
  queryClient: ReturnType<typeof useQueryClient>,
  postId: string,
  data: { media: PostDto["media"]; version: number },
  etag: string | null
) {
  queryClient.setQueryData<PostSnapshot>(postKeys.detail(postId), (prev) => {
    if (!prev) return prev;
    return {
      post: { ...prev.post, media: data.media, version: data.version },
      etag: etag ?? prev.etag,
    };
  });
}

/**
 * Đọc danh sách `mediaId` ĐANG gắn vào bài, thẳng từ cache React Query — cả
 * ba mutation dưới đây (gắn tệp mới/sắp lại/gỡ) đều cần nó để dựng lại đúng
 * `mediaIds` gửi cho `postsApi.setMedia`, vì hợp đồng thật không có lệnh
 * "thêm một tệp"/"gỡ một tệp" riêng — chỉ có MỘT lệnh "đặt cả danh sách".
 * Không đọc từ tham số hook (đóng gói lúc tạo hook) vì `post.media` có thể đã
 * đổi giữa hai lượt gọi liên tiếp (vd. gắn hai tệp gần nhau).
 */
function currentMediaIds(queryClient: ReturnType<typeof useQueryClient>, postId: string): string[] {
  const snapshot = queryClient.getQueryData<PostSnapshot>(postKeys.detail(postId));
  return snapshot?.post.media.map((m) => m.id) ?? [];
}

/**
 * Xác nhận một lượt tải lên (bước 3/3, xem `src/lib/api/media.ts`) RỒI gắn
 * luôn tệp vừa xác nhận vào CUỐI danh sách hiện tại của bài.
 *
 * <h2>Vì sao đây là MỘT mutation, không phải hai bước tách rời ở nơi gọi</h2>
 * Hợp đồng thật tách "xác nhận" (`POST /media/{id}/confirm`, trả một tệp trần
 * trụi, chưa gắn vào đâu) khỏi "gắn vào bài" (`PUT /posts/{id}/media`, đặt cả
 * danh sách) — khác hẳn suy đoán trước đây (một lượt xác nhận trả về cả bài
 * đã cập nhật). Gộp hai lời gọi vào một mutation ở đây để phần còn lại của
 * giao diện (`useMediaUploadQueue`) không phải tự nhớ thứ tự hai bước này.
 */
export function useConfirmAndAttachMedia(postId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ mediaId, alt }: { mediaId: string; alt?: string }) => {
      await mediaApi.confirmUpload(mediaId, alt);
      const nextIds = [...currentMediaIds(queryClient, postId), mediaId];
      return postsApi.setMedia(postId, nextIds);
    },
    onSuccess: ({ data, etag }) => patchPostMedia(queryClient, postId, data, etag),
  });
}

/**
 * Sắp lại thứ tự hiển thị của cả album — gửi lại TOÀN BỘ danh sách `mediaId`
 * theo thứ tự mới qua `postsApi.setMedia` (không có lệnh "sắp lại" riêng).
 */
export function useReorderMedia(postId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderedMediaIds: string[]) => postsApi.setMedia(postId, orderedMediaIds),
    onSuccess: ({ data, etag }) => patchPostMedia(queryClient, postId, data, etag),
  });
}

/**
 * Gỡ một tệp khỏi bài đang soạn (tác giả, chỉ khi còn `DRAFT`) — gửi lại danh
 * sách hiện tại đã BỚT một phần tử qua `postsApi.setMedia` (không có lệnh
 * "gỡ một tệp" riêng; tệp bị bỏ ra không xoá ngay, xem javadoc
 * `AttachMediaRequest` phía backend — nó mồ côi và đường dọn mang đi sau 24
 * giờ).
 */
export function useRemoveMedia(postId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mediaId: string) =>
      postsApi.setMedia(postId, currentMediaIds(queryClient, postId).filter((id) => id !== mediaId)),
    onSuccess: ({ data, etag }) => patchPostMedia(queryClient, postId, data, etag),
  });
}

/**
 * Ngưỡng dung lượng/định dạng/thời lượng — công bố bởi máy chủ
 * (`GET /media/policy`). `staleTime` dài vì chính sách gần như không đổi
 * trong một phiên làm việc, cùng tinh thần với cách `dataimport` cache
 * `duplicate-policy`; không cần refetch mỗi lần mở màn soạn bài.
 */
export function useMediaPolicy() {
  return useQuery({
    queryKey: ["media-policy"] as const,
    queryFn: () => mediaApi.policy(),
    staleTime: 5 * 60_000,
  });
}

/**
 * "Báo gỡ" — bất kỳ ai xem một bài đã đăng đều báo được một tệp cần xem lại.
 * Không đổi `post.media` của bài (gỡ thật sự đi qua {@link useTakedownReport}
 * ở màn duyệt, không phải ở đây). Chú ý: gọi `mediaId` trực tiếp, KHÔNG còn
 * cần `postId` — báo cáo gắn với TỆP, không gắn với bài chứa nó.
 */
export function useReportMedia(mediaId: string) {
  return useMutation({
    mutationFn: (input: Parameters<typeof mediaApi.report>[1]) => mediaApi.report(mediaId, input),
  });
}

/** Hàng chờ duyệt báo cáo, phạm vi chi của người gọi — xem `MediaReportQueueScreen`. */
export function useMediaReports(limit = 50) {
  return useQuery({
    queryKey: ["media-reports", limit] as const,
    queryFn: () => mediaApi.listReports(limit),
    staleTime: 0,
  });
}

function invalidateMediaReports(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["media-reports"] });
}

/**
 * Gỡ vĩnh viễn. **Xoá byte ngay trong cùng transaction, không có thời gian ân
 * hạn, không có nút hoàn tác** — màn gọi hook này PHẢI xác nhận rõ điều đó
 * trước khi gọi `mutate`, không phải sau.
 */
export function useTakedownReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reportId, note }: { reportId: string; note?: string }) =>
      mediaApi.takedownReport(reportId, { note }),
    onSuccess: () => invalidateMediaReports(queryClient),
  });
}

/** Bỏ qua báo cáo — tệp ở lại nguyên trạng. */
export function useDismissReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reportId, note }: { reportId: string; note?: string }) =>
      mediaApi.dismissReport(reportId, { note }),
    onSuccess: () => invalidateMediaReports(queryClient),
  });
}

/**
 * Nhánh câu chữ theo `code` (contracts/README §3), dùng ở mọi nơi hiển thị lỗi
 * ghi/gửi bài viết. Trả về khoá dịch trong `posts.errors.*`.
 */
export function postErrorKey(error: unknown): string {
  if (!(error instanceof ApiError)) return "unknown";
  switch (error.code) {
    case "OPTIMISTIC_LOCK_CONFLICT":
      return "staleRecord";
    case "PRECONDITION_REQUIRED":
      return "missingPrecondition";
    case "UNAUTHENTICATED":
      return "sessionExpired";
    // Tách khỏi FORBIDDEN thường — tiền lệ ChangeRequest: câu chữ phải nói
    // đúng "chính bạn không tự duyệt được", không phải "không có quyền".
    case "SELF_REVIEW_FORBIDDEN":
      return "selfReview";
    case "FORBIDDEN":
    case "BRANCH_SCOPE_VIOLATION":
      return "forbidden";
    case "VALIDATION_FAILED":
      return "validation";
    case "NOT_FOUND":
      return "notFound";
    default:
      return "unknown";
  }
}

import { apiFetch, apiFetchWithMeta, type ApiResult } from "./http";
import type { PageMeta } from "@/types/api";
import type { PostMediaItem } from "./media";

export type { PostMediaItem, MediaKind } from "./media";

/**
 * Lớp API cho **bài viết dòng họ** (soạn → gửi duyệt → đăng → gỡ).
 *
 * <h2>Trạng thái hợp đồng: chưa ở trong `contracts/openapi.yaml`</h2>
 * Bản `1.0.0-sprint1` **chưa** có nhóm `/api/v1/posts`. Hình dạng dưới đây là
 * hợp đồng do PO chốt cho Đợt 2 (không phải do giao diện tự suy đoán) và agent
 * backend đang dựng đúng nó. Khi `contracts/` được cập nhật, đối chiếu lại tệp
 * này trước khi tin một chỗ lệch là lỗi ở đây — cùng quy ước với
 * `src/lib/api/change-requests.ts`.
 *
 * <h2>`PATCH` đòi `If-Match` — coi bài viết như một `PersonDto`</h2>
 * Máy chủ trả `412 PRECONDITION_REQUIRED` khi thiếu header, `409
 * OPTIMISTIC_LOCK_CONFLICT` khi ETag không khớp (ai đó đã lưu nháp ở tab
 * khác). Cả hai đều là mã đã có sẵn trong `ProblemCode` — không cần mã riêng
 * cho bài viết. Xem `src/components/posts/use-post-draft.ts` cho cách xử tử
 * tế cả hai ca mà không làm mất chữ người dùng đang gõ.
 *
 * <h2>Vì sao "gửi duyệt" là một hành động riêng, không phải một giá trị của `status`</h2>
 * `PATCH` không nhận `status` — chuyển nháp thành "chờ duyệt" đi qua
 * `POST /{id}/submit` (và ngược lại qua `/withdraw`) để máy chủ khoá được nội
 * dung tại đúng thời điểm gửi (không cho lưu nháp đè lên một bài đang chờ
 * duyệt bằng một `PATCH` vô tình).
 *
 * <h2>Ai viết được: `linkedToTree`, không phải vai</h2>
 * Quyết định đã chốt: "viết bài là tiếng nói của người trong họ" — người chưa
 * được duyệt vào phả (`MeView.linkedToTree === false`) không viết được, bất kể
 * vai kỹ thuật. Cổng thật nằm ở máy chủ; giao diện chỉ ẩn biểu mẫu và nói rõ vì
 * sao (xem `PostWriteGate`) để không giấu một nút rồi để người dùng tự đoán.
 *
 * <h2>Cập nhật từ backend (đã chốt, không còn là suy đoán của FE)</h2>
 * <ul>
 *   <li><b>Không có `coverImageKey` đơn lẻ, và sẽ không bao giờ có.</b> Cột ấy
 *       từng bị gỡ khỏi cả CSDL lẫn DTO vì lúc đó backend chưa có SDK MinIO/S3
 *       nào. Đợt này PO chốt lại hình dạng khác hẳn "ảnh bìa": một danh sách
 *       {@link PostDto.media} có thứ tự, nhiều ảnh/video, không có khái niệm
 *       "ảnh đại diện". Xem `src/lib/api/media.ts` cho luồng ba bước (xin URL
 *       đã ký → `PUT` thẳng lên kho → xác nhận) — một agent backend khác đang
 *       dựng phần MinIO cùng lúc với tệp này, nên hình dạng dưới đây vẫn là đề
 *       xuất của FE cho tới khi `contracts/openapi.yaml` có nhóm này.</li>
 *   <li><b>`reviewedByDisplayName`</b> — tên người duyệt, tra qua ĐÚNG
 *       `PersonDisclosureService` dùng cho `authorDisplayName`. `null` khi
 *       người đọc không được thấy người duyệt (vd. khách không thấy một
 *       Trưởng chi còn sống dù bài đã đăng công khai) HOẶC người duyệt không
 *       gắn với nhân khẩu nào (System Admin kỹ thuật) — cả hai ca đều là
 *       "không có gì để hiện", không phải lỗi. Xem `post-review-meta.tsx`.</li>
 * </ul>
 */

export type PostStatus = "DRAFT" | "PENDING" | "PUBLISHED" | "WITHDRAWN";

export interface PostDto {
  id: string;
  title: string;
  body: string;
  status: PostStatus;
  authorPersonId: string;
  authorDisplayName: string;
  branchId?: string | null;
  branchName?: string | null;
  publishedAt?: string | null;
  /** Định danh nội bộ — không hiển thị trực tiếp. Dùng `reviewedByDisplayName` để vẽ giao diện. */
  reviewedBy?: string | null;
  /** Tên hiển thị của người duyệt, đã qua lọc riêng tư. `null` ⇒ không vẽ tên, chỉ vẽ ngày. */
  reviewedByDisplayName?: string | null;
  reviewedAt?: string | null;
  rejectReason?: string | null;
  /**
   * Ảnh/video đính kèm, **đã sắp theo thứ tự hiển thị**. Rỗng ⇒ bài không có
   * tệp nào — không suy diễn gì thêm từ mảng rỗng (không phải "đang tải",
   * không phải lỗi). Xem `src/lib/api/media.ts` cho luồng ghi.
   */
  media: PostMediaItem[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface PostPage {
  items: PostDto[];
  page: PageMeta;
}

export interface ListPostsParams {
  status?: PostStatus;
  /**
   * Chỉ bài của CHÍNH người gọi, lọc **trong SQL** (không phải ở client) —
   * dùng cho "bài của tôi / nháp của tôi". Ghép được với `status`. Cũ hơn,
   * đã bỏ: bắn bốn lượt gọi song song theo từng `status` rồi lọc lại ở
   * client theo `authorPersonId` — cách đó cắt trang TRƯỚC KHI lọc, nên một
   * người viết nhiều mất bài cũ mà không có gì báo (đúng lỗi backend viết
   * bài kiểm ghim lại). Xem `src/components/posts/use-my-posts.ts`.
   */
  mine?: boolean;
  page?: number;
  size?: number;
}

export interface FeedPostsParams {
  /** Mặc định máy chủ áp dụng nếu bỏ trống — giao diện không đoán trần này. */
  limit?: number;
}

export interface PostWriteBody {
  title: string;
  body: string;
}

/** `PATCH` — mọi trường đều tuỳ chọn, chỉ gửi trường đã đổi. */
export type PostPatchBody = Partial<PostWriteBody>;

export interface ReviewPostBody {
  approve: boolean;
  /** Nên có khi từ chối — người viết cần biết vì sao để sửa lại. */
  note?: string | null;
}

const BASE = "/api/v1/posts";

export const postsApi = {
  /** Hàng chờ duyệt (`status=PENDING`) và danh sách theo trạng thái khác. Phạm vi do máy chủ cắt. */
  list: ({ status, mine, page = 0, size = 20 }: ListPostsParams = {}) =>
    apiFetch<PostPage>(BASE, { query: { status, mine, page, size } }),

  /** Nguồn cho khối "Bài mới" ở trang chủ — chỉ bài đã đăng, do chính máy chủ lọc theo trạng thái. */
  feed: ({ limit = 6 }: FeedPostsParams = {}) =>
    apiFetch<PostDto[]>(`${BASE}/feed`, { query: { limit } }),

  getById: (id: string): Promise<ApiResult<PostDto>> => apiFetchWithMeta<PostDto>(`${BASE}/${id}`),

  /** Tạo nháp. Trả kèm `ETag` để lượt lưu nháp tự động đầu tiên có `If-Match` ngay. */
  create: (body: PostWriteBody): Promise<ApiResult<PostDto>> =>
    apiFetchWithMeta<PostDto>(BASE, { method: "POST", body }),

  /** Lưu nháp / sửa nhẹ trước khi đăng. `ifMatch` bắt buộc. */
  update: (id: string, body: PostPatchBody, ifMatch: string): Promise<ApiResult<PostDto>> =>
    apiFetchWithMeta<PostDto>(`${BASE}/${id}`, { method: "PATCH", body, ifMatch }),

  submit: (id: string) => apiFetch<PostDto>(`${BASE}/${id}/submit`, { method: "POST" }),

  review: (id: string, body: ReviewPostBody) =>
    apiFetch<PostDto>(`${BASE}/${id}/review`, { method: "POST", body }),

  withdraw: (id: string) => apiFetch<PostDto>(`${BASE}/${id}/withdraw`, { method: "POST" }),
};

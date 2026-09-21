import { apiFetch, apiFetchWithMeta, type ApiResult } from "./http";

/**
 * Lớp API cho **ảnh và video đính kèm bài viết**, cộng luồng **báo gỡ**
 * (`/api/v1/media/**`, một nhóm riêng — KHÔNG lồng dưới `/posts/{id}` như bản
 * nháp đầu của tệp này từng đoán) — `postsApi` gọi `PostDto.media` để đọc,
 * tệp này lo phần **ghi** (tải lên/xoá) và phần **báo cáo/duyệt gỡ**.
 *
 * <h2>Đồng bộ đợt hai — backend media đã xong, bốn chỗ đã sửa lại</h2>
 * <ol>
 *   <li><b>Trần dung lượng/định dạng đọc từ {@link mediaApi.policy}</b>, không
 *       còn là hằng số phía FE. Xem javadoc {@link MediaPolicy}.</li>
 *   <li><b>`GET /posts?mine=true`</b> lọc trong SQL — xem
 *       `src/components/posts/use-my-posts.ts`.</li>
 *   <li><b>Báo gỡ chuyển hẳn sang `/api/v1/media/{mediaId}/reports`</b>, không
 *       còn qua `postId`: một tệp chỉ có một khoá thật (`mediaId`), và người
 *       báo cáo không cần biết/không quan tâm bài nào — họ đang báo TỆP.</li>
 *   <li><b>Có màn duyệt báo cáo</b> — {@link mediaApi.listReports},
 *       {@link mediaApi.takedownReport}, {@link mediaApi.dismissReport}; xem
 *       `src/components/posts/media/media-report-queue-screen.tsx`.</li>
 * </ol>
 *
 * <h2>Ba bước tải lên vẫn giữ nguyên</h2>
 * Xin URL đã ký → `PUT` thẳng lên kho (KHÔNG qua backend) → gọi backend xác
 * nhận. `PostDto.media` vẫn là một danh sách có thứ tự, không "ảnh bìa" riêng.
 *
 * <h2>Vẫn còn phần CHƯA có trong `contracts/openapi.yaml`</h2>
 * Tên endpoint/trường ở tệp này khớp với những gì backend media vừa công bố
 * (không còn là suy đoán của FE cho phần chính sách/báo gỡ), nhưng hợp đồng
 * OpenAPI chính thức vẫn chưa cập nhật — đối chiếu lại khi nó xuất hiện, đừng
 * coi một chỗ lệch nhỏ là lỗi ở đây (cùng quy ước với `src/lib/api/posts.ts`).
 */

export type MediaKind = "IMAGE" | "VIDEO";

/** Một tệp đã gắn vào bài — phần tử của `PostDto.media`, có thứ tự. */
export interface PostMediaItem {
  id: string;
  kind: MediaKind;
  /**
   * URL đã ký để **đọc** (khác URL đã ký để tải lên) — hạn dùng theo
   * {@link MediaPolicy.readUrlExpiresInSeconds}. Nếu hết hạn khi đang xem
   * (video dài, hoặc tab bỏ mở qua đêm), tải lại bài để lấy URL mới; đừng lưu
   * URL này lâu hơn phiên xem hiện tại.
   */
  url: string;
  /** Bắt buộc có với `IMAGE`. Luôn `null` với `VIDEO` — video không có "chữ thay thế". */
  alt: string | null;
  /** Vị trí 0-based trong album — trùng thứ tự trong mảng nhưng tường minh để sắp xếp lại. */
  order: number;
  /** Để đặt trước khung, tránh trang nhảy khi tệp tải xong. Vắng mặt nếu trình duyệt không đọc được. */
  width?: number | null;
  height?: number | null;
  createdAt: string;
}

// ============================================================================
// Chính sách — GET /api/v1/media/policy
// ============================================================================

export interface MediaKindPolicy {
  maxBytes: number;
  mimeTypes: string[];
}

/**
 * `GET /api/v1/media/policy` — cùng tiền lệ `GET /import/duplicate-policy`:
 * ngưỡng do MÁY CHỦ công bố, client không ghi cứng con số nào.
 *
 * <h2>`video.maxBytes` là ngân sách TẢI VỀ, không phải ngân sách tải lên</h2>
 * Không chuyển mã nghĩa là mỗi byte tải lên sẽ được **mọi người đọc bài tải
 * xuống** — kể cả bà con ở xa đang dùng dữ liệu di động. Trần thấp hơn trực
 * giác "video thì phải nặng hơn ảnh nhiều" là có chủ đích.
 *
 * <h2>`video.maxDurationSeconds` chặn THỂ LOẠI, không chặn băng thông</h2>
 * Thiếu trần này thì một bản ghi hình lễ giỗ 40 phút nén mạnh vẫn lọt qua
 * trần dung lượng, và trang chủ dòng họ biến thành chỗ lưu video — thứ đợt
 * này cố ý không dựng. Máy chủ đọc thời lượng thật từ phần đầu tệp và **từ
 * chối khi không đọc được** (tệp hỏng/tráo đổi) — client kiểm trước bằng
 * {@link readMediaMeta} để không bắt người dùng chờ hết thanh tiến trình rồi
 * mới bị từ chối.
 */
export interface MediaPolicy {
  image: MediaKindPolicy;
  video: MediaKindPolicy & { maxDurationSeconds: number };
  /** Số tệp tối đa một bài mang theo. */
  maxAttachmentsPerPost: number;
  /** Hạn dùng của URL `PUT` (xin ở bước 1) — hết hạn thì phải xin URL mới, không thử `PUT` lại URL cũ. */
  uploadUrlExpiresInSeconds: number;
  /** Hạn dùng của URL đọc gắn trong {@link PostMediaItem.url}. */
  readUrlExpiresInSeconds: number;
}

// ============================================================================
// Từ chối theo TÊN — HEIC/GIF/MOV/MKV, kèm cách sửa
// ============================================================================

export type MediaRejectReason = "TYPE" | "SIZE" | "COUNT" | "DURATION" | "NAMED_FORMAT";

/**
 * Bốn định dạng máy chủ từ chối THEO TÊN tệp (đuôi/MIME), mỗi cái kèm một
 * cách sửa cụ thể — không phải "định dạng không hỗ trợ" chung chung.
 *
 * <h2>HEIC đứng đầu vì lý do thật</h2>
 * Mọi tấm ảnh chụp mặc định trên iPhone là HEIC. Một câu báo lỗi trống rỗng ở
 * đây khiến người dùng thử lại thêm năm lần với cùng một tấm ảnh trước khi bỏ
 * cuộc — cách sửa (đổi định dạng lưu trong Cài đặt, hoặc "Lưu ảnh gốc dạng
 * tương thích nhất" khi chia sẻ) phải hiện NGAY, không phải sau khi họ tự tìm
 * ra vấn đề.
 *
 * Nhận diện qua ĐUÔI TỆP (không phải MIME): trình duyệt thường báo MIME rỗng
 * hoặc sai cho các định dạng này (`.heic` hay về `""` hoặc
 * `"application/octet-stream"` tuỳ hệ điều hành), nên đuôi tệp là tín hiệu
 * đáng tin duy nhất phía client.
 */
const NAMED_REJECTIONS: Array<{ extensions: string[]; kind: MediaKind; messageKey: string }> = [
  { extensions: [".heic", ".heif"], kind: "IMAGE", messageKey: "heic" },
  { extensions: [".gif"], kind: "IMAGE", messageKey: "gif" },
  { extensions: [".mov"], kind: "VIDEO", messageKey: "mov" },
  { extensions: [".mkv"], kind: "VIDEO", messageKey: "mkv" },
];

/** Khoá dịch trong `posts.media.rejected.named.*` cho tệp bị từ chối theo tên — `undefined` nếu tệp không thuộc bốn định dạng này. */
export function namedRejectionKey(fileName: string): string | undefined {
  const lower = fileName.toLowerCase();
  const hit = NAMED_REJECTIONS.find((r) => r.extensions.some((ext) => lower.endsWith(ext)));
  return hit?.messageKey;
}

/**
 * Hợp nhất có phân biệt qua `ok` — `ok: true` đảm bảo `kind` khác `null` mà
 * KHÔNG CẦN kiểm lại ở nơi gọi (xem lịch sử: một phiên bản `kind: MediaKind |
 * null` đứng ngoài hợp nhất từng buộc nơi gọi tự ép kiểu).
 */
export type MediaValidationResult =
  | { ok: true; kind: MediaKind }
  | {
      ok: false;
      kind: MediaKind | null;
      reason: MediaRejectReason;
      /** Chỉ có khi `reason === "NAMED_FORMAT"` — khoá dịch cụ thể (heic/gif/mov/mkv), không phải câu chung. */
      namedFormatKey?: string;
    };

/** Suy loại tệp từ MIME theo chính sách máy chủ — không suy từ đuôi tệp (trừ bốn định dạng bị từ chối theo tên ở trên). */
export function inferMediaKind(file: File, policy: MediaPolicy): MediaKind | null {
  if (policy.image.mimeTypes.includes(file.type)) return "IMAGE";
  if (policy.video.mimeTypes.includes(file.type)) return "VIDEO";
  return null;
}

/**
 * Kiểm **trước khi gọi mạng** — đúng yêu cầu "vượt trần bị chặn trước khi gọi
 * mạng, không phải sau". Gọi hàm này ngay khi người dùng chọn tệp, trước bất
 * kỳ `fetch` nào. Đòi {@link MediaPolicy} làm tham số — không có chính sách
 * thì không kiểm được, xem `useMediaPolicy`.
 *
 * <h2>Thứ tự kiểm CỐ Ý</h2>
 * Đếm số lượng trước (rẻ nhất), rồi kiểu-theo-tên (HEIC/GIF/MOV/MKV — câu báo
 * lỗi cụ thể nhất, đáng ưu tiên hơn một câu "định dạng không hỗ trợ" chung
 * chung nếu tệp rơi vào cả hai), rồi MIME, rồi dung lượng. Thời lượng video
 * KHÔNG kiểm ở đây — nó cần đọc tệp (bất đồng bộ), xem {@link readMediaMeta}
 * và gọi riêng sau khi hàm này trả `ok`.
 */
export function validateMediaFile(
  file: File,
  currentCount: number,
  policy: MediaPolicy
): MediaValidationResult {
  if (currentCount >= policy.maxAttachmentsPerPost) {
    return { ok: false, kind: null, reason: "COUNT" };
  }
  const namedKey = namedRejectionKey(file.name);
  if (namedKey) {
    const hit = NAMED_REJECTIONS.find((r) => r.messageKey === namedKey);
    return { ok: false, kind: hit?.kind ?? null, reason: "NAMED_FORMAT", namedFormatKey: namedKey };
  }
  const kind = inferMediaKind(file, policy);
  if (!kind) {
    return { ok: false, kind: null, reason: "TYPE" };
  }
  if (file.size > policy[kind === "IMAGE" ? "image" : "video"].maxBytes) {
    return { ok: false, kind, reason: "SIZE" };
  }
  return { ok: true, kind };
}

export interface MediaMeta {
  width: number;
  height: number;
  /** Chỉ có với `VIDEO`. */
  durationSeconds?: number;
}

/**
 * Đọc kích thước thật (và, cho video, thời lượng) trước khi tải lên — để
 * {@link mediaApi.confirmUpload} gửi kèm kích thước, và để chặn video quá
 * {@link MediaPolicy.video.maxDurationSeconds} TRƯỚC KHI gọi mạng.
 *
 * <h2>Vì sao phải kiểm thời lượng ở client</h2>
 * Máy chủ đọc thời lượng thật từ phần đầu tệp khi xác nhận, và **từ chối khi
 * không đọc được** — không kiểm trước ở đây thì người dùng chờ hết thanh
 * tiến trình của một video 200MB rồi mới biết nó bị từ chối vì dài 3 phút.
 *
 * <h2>Có hạn chờ — KHÔNG bao giờ treo cả lượt tải lên vì một tệp</h2>
 * `img.onload`/`video.onloadedmetadata` không đảm bảo luôn bắn (tệp ảnh hỏng
 * phần đầu nhưng đọc dung lượng vẫn đúng, hoặc môi trường không giải mã được
 * — kể cả jsdom trong bộ kiểm, nơi không có gì giải mã `blob:` cả). Hết hạn
 * thì trả `null` — với ẢNH nghĩa là bỏ qua kích thước (tuỳ chọn); với VIDEO
 * nghĩa là **không kiểm được thời lượng ở client**, và lượt tải lên vẫn đi
 * tiếp — máy chủ là chốt chặn thật sự, client chỉ tránh một lượt chờ vô ích
 * khi có thể.
 */
export function readMediaMeta(
  file: File,
  kind: MediaKind,
  timeoutMs = 1200
): Promise<MediaMeta | null> {
  return new Promise((resolve) => {
    let settled = false;
    const objectUrl = URL.createObjectURL(file);
    const finish = (value: MediaMeta | null) => {
      if (settled) return;
      settled = true;
      URL.revokeObjectURL(objectUrl);
      resolve(value);
    };
    const timer = setTimeout(() => finish(null), timeoutMs);

    if (kind === "IMAGE") {
      const img = new Image();
      img.onload = () => {
        clearTimeout(timer);
        finish({ width: img.naturalWidth, height: img.naturalHeight });
      };
      img.onerror = () => {
        clearTimeout(timer);
        finish(null);
      };
      img.src = objectUrl;
      return;
    }
    const video = document.createElement("video");
    video.preload = "metadata";
    video.onloadedmetadata = () => {
      clearTimeout(timer);
      finish({
        width: video.videoWidth,
        height: video.videoHeight,
        durationSeconds: Number.isFinite(video.duration) ? video.duration : undefined,
      });
    };
    video.onerror = () => {
      clearTimeout(timer);
      finish(null);
    };
    video.src = objectUrl;
  });
}

// ============================================================================
// Vận chuyển — tải lên
// ============================================================================

export interface RequestUploadResult {
  uploadId: string;
  /** Khoá đối tượng trên kho — chỉ để ghi log/đối soát, giao diện không tự dựng URL từ nó. */
  key: string;
  /** URL đã ký, `PUT` thẳng lên đây — KHÔNG đi qua backend. */
  uploadUrl: string;
  /** Header bắt buộc phải gửi kèm lượt `PUT` (tối thiểu `Content-Type`). */
  uploadHeaders: Record<string, string>;
  expiresAt: string;
}

export interface UploadProgressEvent {
  loaded: number;
  total: number;
}

/**
 * `PUT` thân tệp thẳng lên kho, bằng `XMLHttpRequest` — không phải `fetch` —
 * vì đây là cách DUY NHẤT có sự kiện tiến trình tải **lên** trên trình duyệt.
 * `fetch` chỉ báo được tiến trình tải VỀ.
 */
export function putToStorage(
  uploadUrl: string,
  file: File,
  headers: Record<string, string>,
  onProgress?: (event: UploadProgressEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", uploadUrl, true);
    for (const [key, value] of Object.entries(headers)) {
      xhr.setRequestHeader(key, value);
    }
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) return;
      onProgress?.({ loaded: event.loaded, total: event.total });
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress?.({ loaded: file.size, total: file.size });
        resolve();
      } else {
        reject(new Error(`Tải tệp lên kho thất bại (HTTP ${xhr.status}).`));
      }
    };
    xhr.onerror = () => reject(new Error("Mất kết nối khi đang tải tệp lên."));
    xhr.onabort = () => reject(new DOMException("Đã huỷ tải lên", "AbortError"));
    if (signal) {
      if (signal.aborted) {
        xhr.abort();
        return;
      }
      signal.addEventListener("abort", () => xhr.abort());
    }
    xhr.send(file);
  });
}

export interface ConfirmUploadInput {
  uploadId: string;
  /** Bắt buộc khi `kind === "IMAGE"`. Bỏ qua với `VIDEO`. */
  alt?: string;
  width?: number;
  height?: number;
  /**
   * Chỉ có ý nghĩa với `VIDEO` — thời lượng đọc được ở client, gửi kèm để
   * server đối chiếu nhanh. KHÔNG thay thế phép đọc thật của server (từ phần
   * đầu tệp) — server vẫn là chốt chặn cuối, đây chỉ giúp trả lỗi sớm hơn khi
   * hai bên đã thống nhất; vắng mặt nghĩa là client không đọc được (jsdom,
   * tệp hỏng…), server tự đọc lấy.
   */
  durationSeconds?: number;
}

const postMediaBase = (postId: string) => `/api/v1/posts/${postId}/media`;

// ============================================================================
// Báo gỡ — /api/v1/media/{mediaId}/reports (KHÔNG lồng dưới /posts/{id})
// ============================================================================

/**
 * Lý do báo cáo — hợp nhất đóng do backend chốt. Chọn `KHAC` thì `note` **bắt
 * buộc**; bốn giá trị còn lại `note` vẫn tuỳ chọn nhưng nên khuyến khích.
 */
export type MediaReportReason = "RIENG_TU" | "SAI_NGUOI" | "KHONG_PHU_HOP" | "BAN_QUYEN" | "KHAC";

export interface CreateMediaReportInput {
  reason: MediaReportReason;
  /** Bắt buộc khi `reason === "KHAC"`. */
  note?: string;
}

export interface MediaReportDto {
  id: string;
  mediaId: string;
  /** URL đã ký để ĐỌC, cho người duyệt xem đúng thứ đang bị báo — không phải khoá kho. */
  mediaUrl: string;
  mediaKind: MediaKind;
  postId: string;
  postTitle: string;
  reason: MediaReportReason;
  note: string | null;
  /** `null` khi người báo cáo không được thấy (đã xoá mềm) hoặc báo cáo ẩn danh theo chính sách. */
  reporterDisplayName: string | null;
  createdAt: string;
}

export interface TakedownOrDismissInput {
  note?: string;
}

export const mediaApi = {
  /** Ngưỡng dung lượng/định dạng/thời lượng — máy chủ công bố, xem {@link MediaPolicy}. */
  policy: () => apiFetch<MediaPolicy>(`/api/v1/media/policy`),

  /** Bước 1 — xin URL đã ký. Máy chủ kiểm lại `kind`/`contentType`/`sizeBytes` — client chặn trước không thay thế phép kiểm này. */
  requestUpload: (postId: string, input: { kind: MediaKind; fileName: string; contentType: string; sizeBytes: number }) =>
    apiFetch<RequestUploadResult>(`${postMediaBase(postId)}/uploads`, { method: "POST", body: input }),

  /** Bước 2 — xem {@link putToStorage}. Cố ý tách khỏi object này vì nó KHÔNG gọi `API_BASE_URL`. */
  putToStorage,

  /**
   * Bước 3 — xác nhận. Trả cả bài (danh sách `media` đã có phần tử mới, đã
   * sắp thứ tự) **cộng `ETag` mới** — cùng quy ước với `postsApi.update`, vì
   * gắn một tệp cũng đổi `version` của bài, nên lượt `PATCH` thân bài kế tiếp
   * cần `If-Match` mới, không phải cái đã lấy trước khi có tệp này.
   *
   * Máy chủ đọc thời lượng thật từ phần đầu tệp video ở bước này và **từ chối
   * khi không đọc được** (`422`) — client đã kiểm trước bằng
   * {@link readMediaMeta}, nhưng đây mới là chốt chặn thật.
   */
  confirmUpload: (postId: string, input: ConfirmUploadInput): Promise<ApiResult<PostDtoWithMedia>> =>
    apiFetchWithMeta<PostDtoWithMedia>(`${postMediaBase(postId)}/uploads/${input.uploadId}/confirm`, {
      method: "POST",
      body: {
        alt: input.alt,
        width: input.width,
        height: input.height,
        durationSeconds: input.durationSeconds,
      },
    }),

  /** Sắp lại thứ tự — gửi toàn bộ danh sách id theo thứ tự mong muốn. Cũng đổi `version`, xem {@link confirmUpload}. */
  reorder: (postId: string, orderedMediaIds: string[]): Promise<ApiResult<PostDtoWithMedia>> =>
    apiFetchWithMeta<PostDtoWithMedia>(`${postMediaBase(postId)}/order`, {
      method: "PATCH",
      body: { order: orderedMediaIds },
    }),

  /** Sửa `alt` sau khi đã gắn — ca "gõ vội, quên chưa tả đúng". */
  updateAlt: (postId: string, mediaId: string, alt: string): Promise<ApiResult<PostDtoWithMedia>> =>
    apiFetchWithMeta<PostDtoWithMedia>(`${postMediaBase(postId)}/${mediaId}`, {
      method: "PATCH",
      body: { alt },
    }),

  /**
   * Gỡ một tệp khỏi bài đang soạn. Cùng cổng quyền với sửa thân bài — chỉ tác
   * giả, chỉ khi bài còn `DRAFT`. Đây là "xoá lúc soạn", KHÁC với
   * {@link mediaApi.report} ("báo gỡ" trên một bài đã đăng).
   */
  remove: (postId: string, mediaId: string): Promise<ApiResult<PostDtoWithMedia>> =>
    apiFetchWithMeta<PostDtoWithMedia>(`${postMediaBase(postId)}/${mediaId}`, { method: "DELETE" }),

  /**
   * Báo một tệp đính kèm cần được gỡ (ảnh đăng nhầm, ảnh của người khác, v.v).
   * KHÔNG xoá ngay — 201 nghĩa là "đã ghi nhận, chờ người duyệt trong phạm vi
   * chi xem". Đúng tinh thần "ảnh đi theo quyền của bài": một người xem bất kỳ
   * không tự xoá được ảnh của người khác, nhưng phải có cách khiến ai đó
   * *biết* để xử lý.
   *
   * <b>Một người chỉ mở được MỘT báo cáo đang treo cho mỗi tệp</b> — gọi lại
   * trong lúc báo cáo trước còn `PENDING` nhận lỗi (client hiện thông điệp
   * chung qua {@link describeError}, không có mã riêng vì đây là ràng buộc dữ
   * liệu, không phải một loại lỗi nghiệp vụ mới).
   */
  report: (mediaId: string, input: CreateMediaReportInput) =>
    apiFetch<{ id: string }>(`/api/v1/media/${mediaId}/reports`, { method: "POST", body: input }),

  /**
   * Hàng chờ duyệt báo cáo — phạm vi chi của người gọi, mới nhất trước. Mỗi
   * mục mang sẵn {@link MediaReportDto.mediaUrl} đã ký, để người duyệt nhìn
   * thấy ĐÚNG thứ mình đang quyết mà không phải mở bài gốc.
   */
  listReports: (limit = 50) => apiFetch<MediaReportDto[]>(`/api/v1/media/reports`, { query: { limit } }),

  /**
   * Gỡ vĩnh viễn. **Xoá byte ngay trong cùng transaction — không có thời gian
   * ân hạn, không có nút hoàn tác.** Giao diện PHẢI nói thẳng điều đó trước
   * khi người duyệt bấm, không phải sau.
   */
  takedownReport: (reportId: string, input: TakedownOrDismissInput = {}) =>
    apiFetch<{ id: string }>(`/api/v1/media/reports/${reportId}/takedown`, { method: "POST", body: input }),

  /** Bỏ qua báo cáo — tệp ở lại nguyên trạng. */
  dismissReport: (reportId: string, input: TakedownOrDismissInput = {}) =>
    apiFetch<{ id: string }>(`/api/v1/media/reports/${reportId}/dismiss`, { method: "POST", body: input }),
};

/**
 * `PostDto` mở rộng — khai lại tối thiểu ở đây để tránh vòng import với
 * `posts.ts` (nơi `PostDto` thật được định nghĩa và đã có trường `media`).
 */
interface PostDtoWithMedia {
  id: string;
  media: PostMediaItem[];
  version: number;
  [key: string]: unknown;
}

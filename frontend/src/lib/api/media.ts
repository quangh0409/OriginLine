import { apiFetch } from "./http";

/**
 * Lớp API cho **ảnh và video đính kèm bài viết**, cộng luồng **báo gỡ**
 * (`/api/v1/media/**`, một nhóm riêng — KHÔNG lồng dưới `/posts/{id}`) —
 * `postsApi` gọi `PostDto.media` để đọc, tệp này lo phần **ghi** (tải
 * lên/gắn/gỡ) và phần **báo cáo/duyệt gỡ**.
 *
 * <h2>Đồng bộ đợt ba — hình dạng THẬT của backend media, không còn suy đoán</h2>
 * Bản trước của tệp này (và của mock) tưởng tượng ra một hợp đồng lồng
 * (`{image:{maxBytes,mimeTypes}, video:{...}}`) và bốn điểm cuối
 * (`requestUpload`/`confirmUpload`/`reorder`/`updateAlt` dưới
 * `/posts/{id}/media/**`) trong lúc backend còn đang dựng song song — đúng
 * cái bẫy đã lặp lại **sáu lần** ở dự án này ("mock giả lập một API dễ hơn/
 * khác hình dạng hơn API thật"). Backend thật và
 * `contracts/openapi.yaml` (`MediaPolicyDto` v.v.) đã chốt một hợp đồng
 * **PHẲNG**, và chỉ có BỐN điểm cuối ghi, không tám:
 * <ol>
 *   <li><b>`GET /media/policy`</b> — trần phẳng, không còn `image`/`video` lồng.</li>
 *   <li><b>`POST /media/upload-tickets` {@code {kind, sizeBytes}}</b> — KHÔNG
 *       còn nhận `fileName`/`contentType` (loại thật do chữ ký byte quyết ở
 *       bước xác nhận), KHÔNG còn cần `postId` (một tệp tồn tại độc lập với
 *       bài cho tới khi được gắn — xem mục 4).</li>
 *   <li><b>`POST /media/{mediaId}/confirm` {@code {alt}}</b> — trả một
 *       {@link MediaAsset} trần trụi (không phải cả bài viết); tệp CHƯA
 *       thuộc về bài nào ở bước này.</li>
 *   <li><b>`PUT /posts/{postId}/media` {@code {mediaIds}}</b> — của
 *       `content`, không phải `media`. Đặt <b>cả danh sách</b>, đúng thứ tự;
 *       không có lệnh "thêm một tệp"/"sắp lại"/"sửa alt" riêng — ba việc đó
 *       (gắn tệp mới, sắp lại, gỡ) đều đi qua CHÍNH điểm cuối này với một
 *       danh sách `mediaIds` khác nhau. Xem `postsApi.setMedia` ở
 *       `src/lib/api/posts.ts`.</li>
 * </ol>
 *
 * <h2>Hệ quả: KHÔNG có lối sửa `alt` sau khi đã gắn</h2>
 * `AttachMediaRequest` chỉ nhận `mediaIds` — không có trường nào mang `alt`.
 * `alt` chỉ đặt được đúng MỘT LẦN, ở bước xác nhận (bước 3), và gọi lại
 * `confirm` cho cùng một `mediaId` để "sửa" alt bị chặn bởi
 * `409 MEDIA_TICKET_CLOSED` (phiếu đã xử lý rồi). Bản trước của
 * `MediaPicker` cho sửa `alt` của một ảnh ĐÃ gắn — tính năng đó không có chỗ
 * đứng thật ở backend nên đã bị bỏ, không phải bị quên.
 *
 * <h2>Ba bước tải lên vẫn giữ đúng HÌNH DẠNG, khác về chi tiết</h2>
 * Xin phiếu (không cần `postId`/`contentType`) → `PUT` thẳng lên kho (không
 * cần header nào — xem {@link putToStorage}) → xác nhận (trả một tệp, không
 * phải cả bài) → **gắn vào bài** bằng danh sách đầy đủ qua `postsApi.setMedia`
 * (bước MỚI, trước đây bị nhầm là một phần của "xác nhận").
 */

export type MediaKind = "IMAGE" | "VIDEO";

/**
 * Chính sách tải tệp — `GET /api/v1/media/policy`. Hình dạng **phẳng**, đúng
 * `MediaPolicyDto` (`contracts/openapi.yaml` ~12076): không có `image`/`video`
 * lồng nhau như bản đoán trước đây. Đọc chính sách này thay vì giữ hằng số
 * riêng — cùng tiền lệ `GET /import/duplicate-policy`.
 */
export interface MediaPolicy {
  /** 8 MiB (8388608) — trần dung lượng ẢNH. */
  maxImageBytes: number;
  /**
   * 100 MiB (104857600) — trần dung lượng VIDEO. Đây là một **ngân sách tải
   * xuống**, không phải ngân sách tải lên: không chuyển mã, nên mỗi byte tải
   * lên sẽ được tải xuống nguyên vẹn bởi mọi người đọc bài.
   */
  maxVideoBytes: number;
  /** 120 giây — trần THỂ LOẠI, khác trần dung lượng (bắt băng thông). */
  maxVideoDurationSeconds: number;
  /** 12 — một bài viết, không phải một an-bum. */
  maxMediaPerPost: number;
  /** 300 ký tự — trần độ dài chữ thay ảnh (`alt`), ép tới `ck_media_alt_required` ở CSDL. */
  maxAltLength: number;
  /** Gợi ý cho ô chọn tệp (`accept`) — KHÔNG phải phép kiểm; phép kiểm thật là chữ ký byte. */
  imageContentTypes: string[];
  videoContentTypes: string[];
  /** 900 (15 phút) — hạn của `uploadUrl`. */
  uploadUrlTtlSeconds: number;
  /** 600 (10 phút) — hạn của URL đọc. */
  viewUrlTtlSeconds: number;
}

// ============================================================================
// Từ chối theo TÊN — HEIC/GIF/MOV/MKV, kèm cách sửa
// ============================================================================

/**
 * `SERVER` — máy chủ từ chối dứt khoát sau khi đọc chữ ký byte (`MEDIA_BAD_SIGNATURE`); câu hiện ra
 * là `detail` của máy chủ, vì chỉ nó biết tệp thật là gì. Xem `terminalRejection` ở
 * `use-media-upload-queue.ts`.
 */
export type MediaRejectReason = "TYPE" | "SIZE" | "COUNT" | "DURATION" | "NAMED_FORMAT" | "SERVER";

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
 * đáng tin duy nhất phía client. Cùng bốn định dạng backend từ chối bằng chữ
 * ký byte ở `MediaKind` — hai nơi khớp nhau vì cùng một quyết định sản phẩm.
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
  if (policy.imageContentTypes.includes(file.type)) return "IMAGE";
  if (policy.videoContentTypes.includes(file.type)) return "VIDEO";
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
 * KHÔNG kiểm ở đây — nó cần đọc tệp (bất đồng bộ), xem
 * {@link readVideoDurationSeconds} và gọi riêng sau khi hàm này trả `ok`.
 */
export function validateMediaFile(
  file: File,
  currentCount: number,
  policy: MediaPolicy
): MediaValidationResult {
  if (currentCount >= policy.maxMediaPerPost) {
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
  const maxBytes = kind === "IMAGE" ? policy.maxImageBytes : policy.maxVideoBytes;
  if (file.size > maxBytes) {
    return { ok: false, kind, reason: "SIZE" };
  }
  return { ok: true, kind };
}

/**
 * Đọc thời lượng THẬT của một video từ chính trình duyệt, trước khi tải lên
 * — để chặn video quá {@link MediaPolicy.maxVideoDurationSeconds} TRƯỚC KHI
 * gọi mạng. Chỉ có ý nghĩa với VIDEO: ảnh không có gì để đọc trước (kích
 * thước ảnh không còn là một trường máy chủ chấp nhận — `MediaAssetDto`
 * không mang `width`/`height`, nên không còn lý do đọc chúng ở client).
 *
 * <h2>Vì sao phải kiểm thời lượng ở client</h2>
 * Máy chủ đọc thời lượng thật từ phần đầu tệp khi xác nhận, và **từ chối khi
 * không đọc được** — không kiểm trước ở đây thì người dùng chờ hết thanh
 * tiến trình của một video 200MB rồi mới biết nó bị từ chối vì dài 3 phút.
 *
 * <h2>Có hạn chờ — KHÔNG bao giờ treo cả lượt tải lên vì một tệp</h2>
 * `video.onloadedmetadata` không đảm bảo luôn bắn (tệp hỏng phần đầu, hoặc
 * môi trường không giải mã được — kể cả jsdom trong bộ kiểm, nơi không có gì
 * giải mã `blob:` cả). Hết hạn hoặc lỗi thì trả `null` — nghĩa là "không kiểm
 * được thời lượng ở client", và lượt tải lên vẫn đi tiếp: máy chủ là chốt
 * chặn thật sự, client chỉ tránh một lượt chờ vô ích khi có thể.
 */
export function readVideoDurationSeconds(file: File, timeoutMs = 1200): Promise<number | null> {
  return new Promise((resolve) => {
    let settled = false;
    const objectUrl = URL.createObjectURL(file);
    const finish = (value: number | null) => {
      if (settled) return;
      settled = true;
      URL.revokeObjectURL(objectUrl);
      resolve(value);
    };
    const timer = setTimeout(() => finish(null), timeoutMs);

    const video = document.createElement("video");
    video.preload = "metadata";
    video.onloadedmetadata = () => {
      clearTimeout(timer);
      finish(Number.isFinite(video.duration) ? video.duration : null);
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

/** Trả lời của `POST /api/v1/media/upload-tickets` — bước 1/3. */
export interface UploadTicket {
  /** URL `PUT` đã ký, hạn 15 phút. Client tải tệp THẲNG lên kho, không qua backend. */
  uploadUrl: string;
  /** Khoá của phiếu — dùng cho bước xác nhận và (sau khi xác nhận) để gắn vào bài. */
  mediaId: string;
  /** Khoá đối tượng trên kho — chỉ để đối chiếu khi truy sự cố, luồng thường không cần. */
  mediaKey: string;
  expiresAt: string;
  /** Trần dung lượng áp cho loại tệp này — 8 MiB ảnh, 100 MiB video. */
  maxBytes: number;
  /** 120 giây với video, `null` với ảnh. */
  maxDurationSeconds: number | null;
  /** Gợi ý `accept` — không phải phép kiểm. */
  acceptedContentTypes: string[];
}

export interface UploadProgressEvent {
  loaded: number;
  total: number;
}

/**
 * `PUT` thân tệp thẳng lên kho, bằng `XMLHttpRequest` — không phải `fetch` —
 * vì đây là cách DUY NHẤT có sự kiện tiến trình tải **lên** trên trình duyệt.
 * `fetch` chỉ báo được tiến trình tải VỀ.
 *
 * <h2>KHÔNG gửi header nào</h2>
 * URL đã ký của MinIO ở đây không đưa header nào vào chữ ký (xem
 * `MinioObjectStorageAdapter.presign` — không có `extraSignedHeaders`), và
 * hợp đồng nói rõ: "Gửi đúng một yêu cầu PUT với thân là nội dung tệp".
 * Trình duyệt tự thêm `Content-Type` từ `file.type` khi gửi một `Blob`/`File`
 * làm thân — đó không phải điều bắt buộc, chỉ là hành vi mặc định của XHR, và
 * kho không kiểm nó (loại thật được backend dò lại bằng chữ ký byte ở bước
 * xác nhận).
 */
export function putToStorage(
  uploadUrl: string,
  file: File,
  onProgress?: (event: UploadProgressEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", uploadUrl, true);
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

/**
 * Một tệp media trần trụi — trả lời của `POST /api/v1/media/{id}/confirm`
 * (`MediaAssetDto`). **Chưa** thuộc về bài nào ở bước này: `position` luôn
 * `0` và `url` luôn `null` (tệp chưa gắn vào bản ghi nào để mà ký URL đọc).
 * Gắn vào bài là một lệnh RIÊNG — xem `postsApi.setMedia`.
 */
export interface MediaAsset {
  id: string;
  kind: MediaKind;
  /** Kiểu MIME do backend dò bằng chữ ký byte — không phải `file.type` client gửi. */
  contentType: string;
  sizeBytes: number;
  /** Chỉ có với VIDEO, đọc từ header container. `null` với ảnh. */
  durationMs: number | null;
  /** Bắt buộc khác `null`/rỗng với ẢNH — ép tới tận CSDL, không bao giờ cần nhánh dự phòng ở đây. */
  alt: string | null;
  position: number;
  url: string | null;
}

/**
 * Một tệp đã GẮN vào bài — phần tử của `PostDto.media`, có thứ tự
 * (`PostMediaDto`, `allOf: [MediaAssetDto]` cộng `url` bắt buộc). Cùng hình
 * dạng với {@link MediaAsset}, chỉ khác `url` LUÔN có mặt: mỗi lần đọc bài là
 * một lần máy chủ ký lại URL đọc.
 */
export interface PostMediaItem extends Omit<MediaAsset, "url"> {
  url: string;
}

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

export type MediaReportStatus = "OPEN" | "ACTIONED" | "DISMISSED";

/**
 * `MediaReportDto` trên dây — ĐÚNG từng trường của `contracts/openapi.yaml`
 * (`MediaReportDto`) và `MediaDtos.MediaReportDto` phía backend.
 *
 * <h2>Không có `postId`/`postTitle`/`reporterDisplayName` — và đó là sửa, không phải thiếu</h2>
 * Bản trước của kiểu này (và của mock) bịa ba trường ấy; máy chủ thật không bao giờ gửi chúng,
 * nên trên hệ thống thật màn duyệt báo cáo hiện một đường dẫn `/bai-viet/undefined` với nhãn
 * rỗng. Một báo cáo gắn với TỆP (một tệp có thể là ảnh chân dung, không chỉ ảnh bài viết), và tên
 * người báo là dữ liệu của một người còn sống — hợp đồng cố ý chỉ đưa khoá tài khoản `reportedBy`.
 * Người duyệt quyết dựa trên chính tấm ảnh (`mediaUrl`), lý do và lời mô tả.
 */
export interface MediaReportDto {
  id: string;
  mediaId: string;
  mediaKind: MediaKind;
  /** URL đã ký để ĐỌC, cho người duyệt xem đúng thứ đang bị báo. `null` sau khi tệp đã bị gỡ. */
  mediaUrl: string | null;
  reason: MediaReportReason;
  note: string | null;
  /** Khoá tài khoản (`app_user.id`) của người báo — không phải tên, không để hiển thị. */
  reportedBy: string;
  status: MediaReportStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  resolutionNote: string | null;
  createdAt: string;
  version: number;
}

export interface TakedownOrDismissInput {
  note?: string;
}

export const mediaApi = {
  /** Ngưỡng dung lượng/định dạng/thời lượng — máy chủ công bố, xem {@link MediaPolicy}. */
  policy: () => apiFetch<MediaPolicy>(`/api/v1/media/policy`),

  /**
   * Bước 1/3 — xin một URL `PUT` đã ký. KHÔNG cần `postId`: một tệp tồn tại
   * độc lập với bài cho tới khi được gắn bằng `postsApi.setMedia`; tệp xin
   * xong mà không bao giờ gắn vào đâu sẽ bị dọn bởi đường gom rác mồ côi sau
   * (`POST /admin/media/gc`), không phải trách nhiệm của client.
   */
  requestUploadTicket: (input: { kind: MediaKind; sizeBytes: number }) =>
    apiFetch<UploadTicket>(`/api/v1/media/upload-tickets`, { method: "POST", body: input }),

  /** Bước 2/3 — xem {@link putToStorage}. Tách khỏi object này vì nó KHÔNG gọi `API_BASE_URL`. */
  putToStorage,

  /**
   * Bước 3/3 — xác nhận. Trả một {@link MediaAsset} TRẦN TRỤI, không phải cả
   * bài viết (khác hẳn suy đoán trước đây) — tệp chưa gắn vào đâu ở bước
   * này. `alt` bắt buộc với ảnh; thiếu thì máy chủ trả `422 VALIDATION_FAILED`
   * và **không xoá tệp** — gọi lại đúng điểm cuối này kèm `alt` là đủ, không
   * phải tải lại từ đầu.
   */
  confirmUpload: (mediaId: string, alt?: string) =>
    apiFetch<MediaAsset>(`/api/v1/media/${mediaId}/confirm`, { method: "POST", body: { alt } }),

  /**
   * Báo một tệp đính kèm cần được gỡ (ảnh đăng nhầm, ảnh của người khác, v.v).
   * KHÔNG xoá ngay — 201 nghĩa là "đã ghi nhận, chờ người duyệt trong phạm vi
   * chi xem". Đúng tinh thần "ảnh đi theo quyền của bài": một người xem bất kỳ
   * không tự xoá được ảnh của người khác, nhưng phải có cách khiến ai đó
   * *biết* để xử lý.
   *
   * <b>Một người chỉ mở được MỘT báo cáo đang treo cho mỗi tệp</b> — gọi lại
   * trong lúc báo cáo trước còn `OPEN` nhận `409 REPORT_DUPLICATE`.
   */
  report: (mediaId: string, input: CreateMediaReportInput) =>
    apiFetch<MediaReportDto>(`/api/v1/media/${mediaId}/reports`, { method: "POST", body: input }),

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
    apiFetch<MediaReportDto>(`/api/v1/media/reports/${reportId}/takedown`, { method: "POST", body: input }),

  /** Bỏ qua báo cáo — tệp ở lại nguyên trạng. */
  dismissReport: (reportId: string, input: TakedownOrDismissInput = {}) =>
    apiFetch<MediaReportDto>(`/api/v1/media/reports/${reportId}/dismiss`, { method: "POST", body: input }),
};

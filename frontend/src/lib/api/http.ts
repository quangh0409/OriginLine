import { MOCKING_ENABLED, getDevRole } from "./dev-role";
import { getAuthToken, refreshAuthToken } from "@/lib/auth/token-bridge";
import type { Problem, ProblemCode } from "@/types/api";

/**
 * Ngôn ngữ đang hoạt động của giao diện, gửi kèm mọi yêu cầu dưới dạng
 * `Accept-Language`.
 *
 * Backend soạn sẵn phần chữ hiển thị cho người dùng theo header này — tiêu đề
 * sự kiện ("Giỗ ..."), `Problem.title`/`detail` (RFC 7807) và nhãn thông báo.
 * Nếu không gửi, backend mặc định `vi` và một người đang xem bản tiếng Anh sẽ
 * nhận lỗi tiếng Việt lẫn vào giữa màn hình tiếng Anh.
 *
 * Lưu ý: `danh xưng` KHÔNG bị header này chi phối — đó là dữ liệu nghiệp vụ,
 * luôn là tiếng Việt (contracts/README §3).
 *
 * Đây là biến ở phạm vi module chứ không phải React state, bởi vì `apiFetch`
 * được gọi từ hàm `queryFn` của React Query, ngoài cây React nên không đọc
 * được context. Chỉ ghi khi ở trình duyệt: trên server, một biến module dùng
 * chung cho mọi request sẽ lẫn ngôn ngữ giữa hai người dùng đồng thời.
 */
export type ApiLocale = "vi" | "en";

const DEFAULT_API_LOCALE: ApiLocale = "vi";

let activeLocale: ApiLocale = DEFAULT_API_LOCALE;

export function setApiLocale(locale: ApiLocale): void {
  if (typeof window === "undefined") return;
  activeLocale = locale;
}

export function getApiLocale(): ApiLocale {
  return typeof window === "undefined" ? DEFAULT_API_LOCALE : activeLocale;
}

/**
 * Backend dev origin. Overridable via NEXT_PUBLIC_API_BASE_URL for other
 * environments; defaults to the value fixed in CLAUDE.md for local dev.
 * Note the OpenAPI `servers[0].url` already includes `/api/v1` — this base
 * does NOT, so every call site includes the `/api/v1/...` prefix explicitly
 * (kept consistent with plan §5's endpoint list).
 */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/**
 * Thrown for any non-2xx response. Branch on `.code` (contracts/README §3:
 * "FE phân nhánh theo code, không theo detail") — `code` is a closed,
 * machine-readable enum; `detail`/`problem.title` are localized human text
 * and MUST NOT be used for control flow.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ProblemCode | "UNKNOWN";
  readonly problem?: Problem;

  constructor(status: number, problem?: Problem) {
    super(problem?.title ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = problem?.code ?? "UNKNOWN";
    this.problem = problem;
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  query?: Record<string, string | number | boolean | string[] | undefined>;
  /** `ETag` from a prior GET — required by the backend for PATCH (optimistic locking). */
  ifMatch?: string;
  /** Client-generated ETag for conditional GET; server replies 304 if unchanged. */
  ifNoneMatch?: string;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const url = new URL(
    path.startsWith("http") ? path : `${API_BASE_URL}${path}`
  );
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined) continue;
      if (Array.isArray(value)) {
        // OpenAPI style: form + explode=true -> repeated query params.
        for (const v of value) url.searchParams.append(key, String(v));
      } else {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

/** Result envelope exposing response headers (ETag) alongside the parsed body. */
export interface ApiResult<T> {
  data: T;
  etag: string | null;
  response: Response;
}

/**
 * Fetch wrapper returning both the parsed body and response headers. Use
 * this when the caller needs `ETag` (e.g. before a subsequent PATCH's
 * `If-Match`) — see contracts/openapi.yaml `/persons/{id}` GET/PATCH.
 */
export async function apiFetchWithMeta<TResponse>(
  path: string,
  options: RequestOptions = {}
): Promise<ApiResult<TResponse>> {
  const { body, query, headers, ifMatch, ifNoneMatch, ...rest } = options;

  const url = buildUrl(path, query);
  const serializedBody = body !== undefined ? JSON.stringify(body) : undefined;

  /**
   * Token của phiên hiện tại, hoặc `null` với khách.
   *
   * Lời gọi này CHỜ Keycloak khởi tạo xong (xem `lib/auth/token-bridge.ts`),
   * nên yêu cầu đầu tiên của một trang không bao giờ lỡ mất header chỉ vì
   * `check-sso` chưa kịp trả lời. Ở chế độ MSW nó trả `null` ngay lập tức.
   */
  let token = await getAuthToken();

  const send = (bearer: string | null) =>
    fetch(url, {
      ...rest,
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json, application/problem+json",
        // Ngôn ngữ hiện tại của giao diện (vi | en). Xem setApiLocale ở trên.
        "Accept-Language": getApiLocale(),
        ...(ifMatch ? { "If-Match": ifMatch } : {}),
        ...(ifNoneMatch ? { "If-None-Match": ifNoneMatch } : {}),
        // DEV ONLY, và CHỈ dưới `npm run dev:mock`: đóng vai claim vai trò của
        // JWT thật để phân tầng riêng tư và RBAC thử được trên MSW. Cờ
        // MOCKING_ENABLED loại trừ lẫn nhau với AUTH_ENABLED (xem
        // lib/auth/keycloak.ts), nên header này KHÔNG BAO GIỜ đi cùng một
        // bản chạy nói chuyện với backend thật — backend không đọc nó, và
        // đọc mới là sai.
        ...(MOCKING_ENABLED ? { "x-mock-role": getDevRole() } : {}),
        // Đặt TRƯỚC `...headers` để người gọi vẫn ghi đè được cho một yêu cầu
        // riêng lẻ. Không có token thì không gắn gì cả: khách phải gọi được
        // các điểm cuối công khai (BA v2 §10).
        ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
        ...headers,
      },
      body: serializedBody,
    });

  let response = await send(token);

  /**
   * 401 với một token mà phía client vẫn tưởng còn hạn: lệch đồng hồ, phiên
   * bị thu hồi, hoặc Keycloak vừa khởi động lại. Thử làm mới đúng MỘT lần rồi
   * gọi lại; thất bại thì rơi về chế độ khách và ném lỗi như thường — không
   * bao giờ lặp vô hạn, và không bao giờ treo giao diện.
   *
   * Khách (token = null) không được thử lại: 401 của họ là câu trả lời đúng
   * theo luật, không phải sự cố.
   */
  if (response.status === 401 && token !== null) {
    const refreshed = await refreshAuthToken();
    if (refreshed) {
      token = refreshed;
      response = await send(refreshed);
    }
  }

  const etag = response.headers.get("ETag");

  if (response.status === 204 || response.status === 304) {
    return { data: undefined as TResponse, etag, response };
  }

  const contentType = response.headers.get("content-type") ?? "";
  const isJson = contentType.includes("json");
  const payload = isJson ? await response.json() : undefined;

  if (!response.ok) {
    const problem = isJson ? (payload as Problem) : undefined;
    throw new ApiError(response.status, problem);
  }

  return { data: payload as TResponse, etag, response };
}

/** Convenience wrapper for the common case of not needing response headers. */
export async function apiFetch<TResponse>(
  path: string,
  options: RequestOptions = {}
): Promise<TResponse> {
  const { data } = await apiFetchWithMeta<TResponse>(path, options);
  return data;
}

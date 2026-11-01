import { ApiError, apiFetch, apiFetchWithMeta, type ApiResult } from "./http";

/**
 * Lớp API cho **khởi tạo dòng họ** — `/api/v1/public/clan-profile` (Khách gọi
 * được) và `/api/v1/admin/clan-profile/**` (chỉ `ADMIN`).
 *
 * <h2>Vì sao một tệp cho cả hai nửa, khác tiền lệ `clan-invite.ts`/`membership-admin.ts`</h2>
 * `clan-invite.ts` tách nửa người cầm mã khỏi nửa Hội đồng vì hai nửa ấy đọc
 * hai *endpoint khác nhau trên cùng một khái niệm* (mã mời). Ở đây chỉ có
 * MỘT khái niệm — hồ sơ dòng họ — và bản công khai
 * ({@link ClanProfilePublicDto}) là tập con đúng nghĩa của bản quản trị
 * ({@link ClanProfileDto}, `= public + createdAt/updatedAt/version`). Tách
 * đôi sẽ ép hai kiểu dữ liệu phải định nghĩa lại y hệt nhau, và một ngày nào
 * đó trường mới sẽ chỉ được thêm vào một bên.
 *
 * <h2>Vì sao có màn hình này</h2>
 * Quyết định 26/09/2026 của chủ dự án: không có nơi nào để dựng một dòng họ
 * mới — mọi thứ làm tay dưới CSDL. Đây là bước 1: `/quan-tri`, chỉ `ADMIN`.
 *
 * <h2>`GET /admin/clan-profile` → `404 CLAN_NOT_INITIALIZED` là ca BÌNH THƯỜNG</h2>
 * Một cổng vừa dựng xong chưa từng chạy `PUT` lần nào. Đây không phải lỗi —
 * xem javadoc `ProblemCode.CLAN_NOT_INITIALIZED` (`src/types/api.ts`). Nơi gọi
 * (`useClanProfileAdmin`) phải đọc mã này thành "hiện form trống, bước 1",
 * không phải một dải lỗi đỏ — cùng khuôn với cách `use-tree-canvas.ts` đọc một
 * `404` của `/persons/{rootId}` thành "chưa có gốc" thay vì một lỗi tải trang.
 *
 * <h2>`rootBranchPath` khoá vĩnh viễn sau nhân khẩu đầu tiên</h2>
 * `PUT` trả `409 CLAN_ROOT_LOCKED` nếu sửa `rootBranchPath` khi
 * `readiness.persons.count > 0`. Đây là một quyết định đã CHỐT trong
 * CLAUDE.md ("Current state"): đổi gốc `ltree` sau khi 1.500 người đã vào phả
 * không còn là một lượt sửa mã. Form bước 1 phải khoá ô này ngay khi biết
 * `persons.count > 0` — đọc từ `readiness`, không đợi máy chủ từ chối.
 *
 * <h2>`PUT` như `PATCH /persons/{id}`: cùng luật `If-Match`</h2>
 * Tạo mới → `201`, không cần `If-Match` (chưa có gì để khoá lạc quan). Cập
 * nhật → `200`, đòi `If-Match` đúng `version` vừa đọc; lệch thì `409`
 * `OPTIMISTIC_LOCK_CONFLICT`, thiếu thì `412 PRECONDITION_REQUIRED` — HỆT
 * `persons.ts`, không phải một luật riêng phải học lại.
 */

// ============================================================================
// DTO — theo hợp đồng `clan-profile` (chưa có trong `contracts/openapi.yaml`
// lúc viết tệp này; xem mục "Chỗ hợp đồng còn thiếu" ở báo cáo bàn giao)
// ============================================================================

export type ClanRegion = "BAC" | "TRUNG" | "NAM";

export interface AncestralHallDto {
  name: string;
  address?: string;
}

/** Ngày giỗ tổ — chỉ ngày/tháng ÂM, không năm: giỗ lặp hằng năm. */
export interface GioToDto {
  lunarDay: number;
  lunarMonth: number;
}

/**
 * Bản công khai — Khách gọi được, `GET /api/v1/public/clan-profile`.
 *
 * `initialized: false` thì MỌI trường khác đều vắng — không suy diễn, không
 * bịa một tên mặc định vào chỗ trống. Nơi gọi phải kiểm `initialized` TRƯỚC
 * khi đọc bất kỳ trường nào khác.
 */
export interface ClanProfilePublicDto {
  initialized: boolean;
  name?: string;
  shortName?: string;
  rootBranchPath?: string;
  region?: ClanRegion;
  ancestralHall?: AncestralHallDto;
  ancestorName?: string;
  foundedNote?: string;
  gioTo?: GioToDto;
}

/** Bản quản trị — `ADMIN`, `GET /api/v1/admin/clan-profile`. */
export interface ClanProfileDto extends ClanProfilePublicDto {
  createdAt?: string;
  updatedAt?: string;
  /** ETag của bản ghi cũng chính là số này (header `ETag`, dùng cho `If-Match`). */
  version: number;
}

/** Thân `PUT /api/v1/admin/clan-profile`. */
export interface ClanProfileWriteBody {
  /** ≤ 120 ký tự. */
  name: string;
  shortName?: string;
  region: ClanRegion;
  /** Một nhãn `ltree`, vd `ho_nguyen_dinh`. Khoá vĩnh viễn sau nhân khẩu đầu tiên. */
  rootBranchPath: string;
  ancestralHall?: AncestralHallDto;
  ancestorName?: string;
  /** ≤ 2000 ký tự. */
  foundedNote?: string;
  gioTo?: GioToDto;
}

export interface ClanReadinessCountDto {
  done: boolean;
  name?: string;
}

export interface ClanReadinessRootBranchDto {
  done: boolean;
  path?: string;
}

export interface ClanReadinessKinshipDto {
  done: boolean;
  region?: ClanRegion;
  customized: boolean;
}

export interface ClanReadinessCouncilDto {
  done: boolean;
  count: number;
}

export type ClanReadinessStepKey = "profile" | "council" | "import" | "invite" | "done";

export interface ClanReadinessStepDto {
  key: ClanReadinessStepKey;
  done: boolean;
  /**
   * `null` khi bước đã xong — máy chủ (`ClanReadinessDto.Step`) không khai
   * `@JsonInclude(NON_NULL)` cho bản ghi này, nên trường vắng nội dung tới
   * dưới dạng `null` tường minh, không phải bị lược khỏi JSON như các khối
   * khác của cùng hợp đồng (`profile`, `rootBranch`, `kinshipRuleSet`).
   */
  hint?: string | null;
}

/** `GET /api/v1/admin/clan-profile/readiness` — điều khiển thanh tiến độ của cả 5 bước. */
export interface ClanReadinessDto {
  profile: ClanReadinessCountDto;
  rootBranch: ClanReadinessRootBranchDto;
  kinshipRuleSet: ClanReadinessKinshipDto;
  council: ClanReadinessCouncilDto;
  persons: { count: number };
  branches: { count: number };
  inviteCodes: { active: number };
  steps: ClanReadinessStepDto[];
}

/** Thân `POST /api/v1/admin/clan-profile/council-accounts`. */
export interface CreateCouncilAccountBody {
  displayName: string;
  /** Số điện thoại VN hoặc email — cùng luật với `loginIdentifierKind` (`clan-invite.ts`). */
  loginId: string;
  personId?: string;
}

/**
 * Phản hồi `201` — mang liên kết đặt mật khẩu **một lần**, cùng khuôn với
 * `IssuedPasswordResetDto`/`IssuedInvitationDto`: KHÔNG đưa vào cache React
 * Query (nó sống lâu hơn một lượt xem), giữ trong `mutation.data` và để mất
 * khi component gọi `reset()`.
 */
export interface CouncilAccountCreatedDto {
  appUserId: string;
  keycloakSub: string;
  role: "COUNCIL";
  setPasswordUrl: string;
  setPasswordExpiresAt: string;
}

// ============================================================================
// GỌI API
// ============================================================================

const ADMIN_BASE = "/api/v1/admin/clan-profile";

export const clanProfileApi = {
  /** Khách gọi được — không có nhánh 401/403 nào phải xử lý ở nơi gọi. */
  getPublic: () => apiFetch<ClanProfilePublicDto>("/api/v1/public/clan-profile"),

  /**
   * `404 CLAN_NOT_INITIALIZED` là một CA BÌNH THƯỜNG (xem javadoc đầu tệp) —
   * hàm này KHÔNG nuốt nó, để nguyên cho `ApiError` ném ra như mọi lỗi khác.
   * Nơi gọi đọc `error.code` để phân biệt "chưa khởi tạo" khỏi trục trặc thật.
   */
  getAdmin: (): Promise<ApiResult<ClanProfileDto>> =>
    apiFetchWithMeta<ClanProfileDto>(ADMIN_BASE),

  /**
   * `ifMatch` vắng khi TẠO MỚI (chưa có phiên bản nào để khoá lạc quan); bắt
   * buộc khi CẬP NHẬT — hệt `persons.ts#update`.
   */
  save: (body: ClanProfileWriteBody, ifMatch?: string): Promise<ApiResult<ClanProfileDto>> =>
    apiFetchWithMeta<ClanProfileDto>(ADMIN_BASE, {
      method: "PUT",
      body,
      ...(ifMatch ? { ifMatch } : {}),
    }),

  readiness: () => apiFetch<ClanReadinessDto>(`${ADMIN_BASE}/readiness`),

  createCouncilAccount: (body: CreateCouncilAccountBody) =>
    apiFetch<CouncilAccountCreatedDto>(`${ADMIN_BASE}/council-accounts`, {
      method: "POST",
      body,
    }),
};

// ============================================================================
// NHÁNH HỎNG
// ============================================================================

/** Bốn lối đi khác nhau cho một lượt lưu hồ sơ hỏng, cộng ca hạ tầng thường lệ. */
export type ClanProfileSaveFailure =
  | "ROOT_LOCKED"
  | "VERSION_CONFLICT"
  | "MISSING_ETAG"
  | "VALIDATION"
  | "FORBIDDEN"
  | "UNAVAILABLE";

export function clanProfileSaveFailureOf(error: unknown): ClanProfileSaveFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";
  switch (error.code) {
    case "CLAN_ROOT_LOCKED":
      return "ROOT_LOCKED";
    case "OPTIMISTIC_LOCK_CONFLICT":
      return "VERSION_CONFLICT";
    case "PRECONDITION_REQUIRED":
      return "MISSING_ETAG";
    case "VALIDATION_FAILED":
      return "VALIDATION";
    case "FORBIDDEN":
      return "FORBIDDEN";
    default:
      return "UNAVAILABLE";
  }
}

/** Trường nào máy chủ từ chối, cho `400 VALIDATION_FAILED` (`ValidationProblem.errors[]`). */
export function clanProfileValidationErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || error.code !== "VALIDATION_FAILED") return {};
  const errors = (error.problem as { errors?: Array<{ field: string; message: string }> })?.errors;
  if (!errors) return {};
  const byField: Record<string, string> = {};
  for (const e of errors) byField[e.field] = e.message;
  return byField;
}

export type CouncilAccountFailure =
  | "IDENTITY_TAKEN"
  /** `personId` gắn kèm (ô "Gắn với một nhân khẩu đã có") đã có tài khoản khác — chọn lại người. */
  | "PERSON_TAKEN"
  /** `personId` không còn tìm thấy — hiếm, vì `ScopedPersonPicker` chỉ cho chọn người có thật. */
  | "PERSON_NOT_FOUND"
  | "VALIDATION"
  | "FORBIDDEN"
  | "UNAVAILABLE";

/**
 * Đọc theo javadoc `CouncilAccountService.create`/`requireLinkablePerson`
 * (backend `vn.giapha.membership.application`): `IDENTITY_ALREADY_REGISTERED`
 * là định danh đăng nhập (`loginId`) đã có chủ; `PERSON_ALREADY_LINKED` là
 * NHÂN KHẨU gắn kèm đã có tài khoản khác — hai lỗi khác hẳn nhau (đổi số điện
 * thoại/email so với đổi người được chọn), gộp chung sẽ khiến người dùng sửa
 * nhầm ô.
 */
export function councilAccountFailureOf(error: unknown): CouncilAccountFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";
  switch (error.code) {
    case "IDENTITY_ALREADY_REGISTERED":
      return "IDENTITY_TAKEN";
    case "PERSON_ALREADY_LINKED":
      return "PERSON_TAKEN";
    case "NOT_FOUND":
      return "PERSON_NOT_FOUND";
    case "VALIDATION_FAILED":
      return "VALIDATION";
    case "FORBIDDEN":
      return "FORBIDDEN";
    default:
      return "UNAVAILABLE";
  }
}

/**
 * Gợi ý `rootBranchPath` từ tên dòng họ — bỏ dấu, `ho_` + snake_case.
 *
 * CHỈ là gợi ý ban đầu cho ô nhập, KHÔNG tự động điền lại sau khi người dùng
 * đã tự gõ (xem cách dùng ở `clan-profile-step.tsx`): một cụ gõ "Nguyễn Đình"
 * rồi tự sửa gốc thành `nguyen_dinh_ha_noi` không được để mỗi ký tự gõ vào ô
 * TÊN kéo ngược ô GỐC về gợi ý mặc định.
 */
export function suggestRootBranchPath(name: string): string {
  const noDiacritics = name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D");
  const snake = noDiacritics
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return snake ? `ho_${snake}` : "";
}

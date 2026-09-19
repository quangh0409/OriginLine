import { apiFetch } from "./http";
import type { DateDual, Gender, Role } from "@/types/api";

/**
 * Lớp API cho luồng **đính chính** (`membership.ChangeRequest`) và hồ sơ phiên
 * làm việc (`/api/v1/me`).
 *
 * <h2>Vì sao các kiểu ở đây chứ không ở `src/types/api.ts`</h2>
 * `contracts/openapi.yaml` (bản `1.0.0-sprint1`) **chưa** mô tả nhóm
 * `/api/v1/change-requests` lẫn `/api/v1/me` — hai nhóm ấy do W6 dựng ở backend
 * và chưa được chép ngược vào contract. Các kiểu dưới đây được chép tay từ
 * nguồn thật:
 *
 *   backend/src/main/java/vn/giapha/membership/api/rest/ChangeRequestController.java
 *   backend/src/main/java/vn/giapha/membership/application/ChangeRequestView.java
 *   backend/src/main/java/vn/giapha/membership/api/rest/dto/SubmitChangeRequestDto.java
 *   backend/src/main/java/vn/giapha/membership/api/rest/dto/ReviewChangeRequestDto.java
 *   backend/src/main/java/vn/giapha/membership/api/rest/dto/MeDto.java
 *
 * Khi contract được cập nhật, đối chiếu lại tệp này trước khi tin rằng một chỗ
 * lệch là lỗi ở đây.
 *
 * <h2>`payload` là đề nghị, không phải lệnh ghi</h2>
 * Backend hiện <b>chưa</b> có người nhận {@code ChangeRequestApprovedEvent}
 * (xem javadoc của chính lớp sự kiện đó: "Ở W6 chưa có người nhận"). Nghĩa là
 * duyệt một yêu cầu mới chỉ ghi nhận quyết định; cây phả hệ chưa tự đổi. Vì
 * vậy `payload` ở đây được đặt khoá **trùng tên trường của `UpdatePersonRequest`**
 * — đó là quy ước duy nhất hợp lý để bước ghép W2×W6 sau này áp dụng được mà
 * không phải dịch khoá, và giao diện người duyệt cũng đọc được ngay.
 */

/** Khớp {@code membership.domain.ChangeRequestType}. */
export type ChangeRequestType =
  | "CREATE_PERSON"
  | "UPDATE_PERSON"
  | "SOFT_DELETE_PERSON"
  | "ADD_RELATIONSHIP"
  | "REMOVE_RELATIONSHIP"
  | "ADD_NAME"
  | "MOVE_BRANCH"
  | "OTHER";

/**
 * Khớp {@code membership.domain.ChangeRequestStatus}.
 *
 * Chỉ `PENDING` là trạng thái mở; ba trạng thái còn lại là **cuối** — muốn đổi
 * ý thì gửi yêu cầu mới, để nhật ký duyệt vẫn đọc được.
 */
export type ChangeRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

/**
 * Khớp {@code ChangeRequestView}.
 *
 * `payload` **rỗng** ở bản đã cắt (`redacted`) mà người không có quyền xem nội
 * dung nhận được — `payloadFields` vẫn còn để giao diện tóm tắt được yêu cầu
 * động tới trường nào mà không lộ giá trị Tầng 3.
 */
export interface ChangeRequestView {
  id: string;
  type: ChangeRequestType;
  status: ChangeRequestStatus;
  personId?: string | null;
  targetBranchId?: string | null;
  payload: Record<string, unknown>;
  payloadFields: string[];
  reason?: string | null;
  requestedBy: string;
  reviewerId?: string | null;
  reviewNote?: string | null;
  reviewedAt?: string | null;
  createdAt?: string | null;
  version: number;
}

/** Thân `POST /api/v1/change-requests`. */
export interface SubmitChangeRequestBody {
  requestType: ChangeRequestType;
  /** Bỏ trống chỉ hợp lệ với `CREATE_PERSON`. */
  personId?: string | null;
  /** Bỏ trống thì backend suy từ chi của `personId`. */
  targetBranchId?: string | null;
  payload?: Record<string, unknown>;
  /** Tối đa 2000 ký tự. Nên có — người duyệt cần bối cảnh. */
  reason?: string | null;
}

/** Thân `POST /api/v1/change-requests/{id}/review`. */
export interface ReviewChangeRequestBody {
  approve: boolean;
  /** **Bắt buộc khi từ chối** — người gửi có quyền biết vì sao. */
  note?: string | null;
}

/**
 * Khớp {@code MeDto}. Đây **không phải** quyết định phân quyền, chỉ là dữ liệu
 * để vẽ giao diện: `managedBranches` cho biết nên hiện nút "Duyệt" ở đâu. Mọi
 * phép kiểm thật nằm ở backend, và client sửa được phản hồi này cũng không ghi
 * được gì.
 */
export interface MeView {
  /** `null` nếu tài khoản chưa được khởi tạo (`ACCOUNT_NOT_PROVISIONED`). */
  appUserId?: string | null;
  /** `null` nếu chưa được ghép vào cây phả hệ (`ACCOUNT_NOT_LINKED`). */
  personId?: string | null;
  role: Role;
  clanWide: boolean;
  /** Các chi/ngành được giao, dạng `ltree`. **Rỗng nghĩa là không có phạm vi nào.** */
  managedBranches: string[];
  homeBranch?: string | null;
  linkedToTree: boolean;
}

export interface PageParams {
  page?: number;
  size?: number;
}

const BASE = "/api/v1/change-requests";

export const changeRequestsApi = {
  /**
   * Gửi đề nghị. Mọi thành viên đã có tài khoản đều gửi được — backend cố ý
   * **không** kiểm phạm vi ở bước này (một người con gái lấy chồng xa vẫn phải
   * báo được rằng ngày mất của cụ ghi sai). Cửa kiểm chặt nằm ở bước duyệt.
   */
  submit: (body: SubmitChangeRequestBody) =>
    apiFetch<ChangeRequestView>(BASE, { method: "POST", body }),

  /** Đề nghị của chính người gọi. */
  mine: ({ page = 0, size = 20 }: PageParams = {}) =>
    apiFetch<ChangeRequestView[]>(`${BASE}/mine`, { query: { page, size } }),

  /**
   * Hàng đợi chờ duyệt **trong phạm vi được giao**.
   *
   * Danh sách rỗng nghĩa là "không có phạm vi nào" hoặc "không còn gì chờ" —
   * tuyệt đối không được hiểu ngược thành "xem được tất". Giao diện phải phân
   * biệt hai lý do ấy bằng `MeView.managedBranches`, không bằng độ dài mảng.
   */
  pending: ({ page = 0, size = 20 }: PageParams = {}) =>
    apiFetch<ChangeRequestView[]>(`${BASE}/pending`, { query: { page, size } }),

  /** Số yêu cầu chờ duyệt trong phạm vi — cho badge. */
  pendingCount: () => apiFetch<{ count: number }>(`${BASE}/pending/count`),

  byId: (id: string) => apiFetch<ChangeRequestView>(`${BASE}/${id}`),

  /**
   * Duyệt hoặc từ chối.
   *
   * Có thể ném `403 SELF_REVIEW_FORBIDDEN` (tự duyệt đề nghị của mình),
   * `403 BRANCH_SCOPE_VIOLATION` (đúng vai, sai chi) hoặc
   * `422 CHANGE_REQUEST_CLOSED` (yêu cầu đã chốt). Phân nhánh theo `code`.
   */
  review: (id: string, body: ReviewChangeRequestBody) =>
    apiFetch<ChangeRequestView>(`${BASE}/${id}/review`, { method: "POST", body }),

  /** Người gửi tự rút lại. Không xoá — bản ghi chuyển sang `CANCELLED`. */
  cancel: (id: string) => apiFetch<ChangeRequestView>(`${BASE}/${id}`, { method: "DELETE" }),
};

export const meApi = {
  /**
   * Hồ sơ phiên làm việc. Lần gọi đầu tiên cũng là lúc backend khởi tạo dòng
   * `app_user` ở trạng thái `PENDING` — không có bước "đăng ký" riêng.
   */
  get: () => apiFetch<MeView>("/api/v1/me"),
};

/** Giá trị một đề nghị có thể mang cho một trường — khớp `UpdatePersonRequest`. */
export type CorrectionValue = string | Gender | DateDual | null;

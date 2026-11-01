import { ApiError, apiFetch } from "./http";

/**
 * Lớp API cho **cấp lại liên kết đặt mật khẩu** — nhóm `password-resets` của
 * `contracts/openapi.yaml`. Backend (`PasswordResetController`,
 * `PasswordResetGrantService`, migration `V21`) xong từ lâu và có 10 test tích
 * hợp xanh; tới bản này thì **không một dòng frontend nào gọi tới nó** — đúng
 * mẫu hình checklist gọi tên: "một tính năng làm xong mà không ai tìm thấy
 * thì bằng chưa làm".
 *
 * <h2>Vì sao đường này tồn tại</h2>
 * Realm bật `resetPasswordAllowed: true`, nhưng đường phục hồi duy nhất
 * Keycloak biết làm là gửi thư — trong khi sản phẩm cho đăng ký bằng email
 * *hoặc* số điện thoại, và lối số điện thoại sinh ra chính vì các cụ không có
 * email. Một cụ đăng ký bằng số máy mà quên mật khẩu thì không có đường nào
 * tự lấy lại; Trưởng chi cấp hộ một liên kết đặt mật khẩu, dùng đúng cơ chế
 * "một lần, 30 phút" của luồng mời (`invitation.ts` → `setInvitationPassword`),
 * KHÔNG phải một mật khẩu tạm đọc qua điện thoại.
 *
 * <h2>Không có `GET` liệt kê — cố ý, đọc kỹ trước khi "thêm cho đủ bộ"</h2>
 * Một danh sách "ai đã cấp lại cho ai" là đúng thứ dùng để CHỌN MỤC TIÊU: nó
 * nói thẳng ra tài khoản nào không có email, tức tài khoản nào cấp lại được.
 * Câu hỏi tra soát thật thì `audit_log` trả lời đầy đủ hơn. Layer này vì thế
 * chỉ có đúng một hàm.
 *
 * <h2>Bốn ca bị chặn, và tại sao chỉ có BA mã lỗi cho bốn ca ấy</h2>
 * Dịch vụ có bốn phép kiểm theo đúng thứ tự (`PasswordResetGrantService`
 * javadoc): phạm vi `ltree` của chi người nhận · không tự cấp cho mình ·
 * tài khoản đích phải `ACTIVE` · tài khoản đích không được có địa chỉ thư.
 * Nhưng contract chỉ định nghĩa BA mã: `BRANCH_SCOPE_VIOLATION` (403),
 * `SELF_PASSWORD_RESET_FORBIDDEN` (403), và `PASSWORD_RESET_NOT_ALLOWED`
 * (422) — "một mã cho bốn lý do" (chưa có tài khoản · không còn `ACTIVE` ·
 * không còn trong realm · **có địa chỉ thư**), phân biệt bằng `detail`.
 *
 * Trong bốn lý do ấy, đúng MỘT lý do cần một lối đi tiếp khác hẳn ba lý do
 * kia: "có địa chỉ thư" nghĩa là người ấy tự bấm "Quên mật khẩu" được, còn ba
 * lý do còn lại đều dẫn tới cùng một việc — không cấp lại được lúc này, hỏi
 * Trưởng chi/Hội đồng hoặc phát lời mời trước. Vì vậy {@link passwordResetFailureOf}
 * tách "có địa chỉ thư" ra làm nhánh riêng bằng cách đọc một mẩu `detail`
 * — không phải vi phạm luật "phân nhánh theo `code`, không theo `detail`"
 * của `contracts/README §3`, vì đây là do CHÍNH contract bó bốn lý do vào một
 * mã và giao cho `detail` việc phân biệt (xem javadoc `PasswordResetController`:
 * "`detail` phân biệt; nó dành cho người cấp"). Ba lý do còn lại gộp vào
 * `NOT_READY` — không có lối đi tiếp nào khác nhau đáng để tách bốn nhánh
 * riêng, và tách ra sẽ buộc giao diện đoán ý một câu tiếng Việt không dấu.
 */

// ============================================================================
// DTO — theo contracts/openapi.yaml, schema IssuePasswordResetRequest /
// IssuedPasswordReset / PasswordResetGrant
// ============================================================================

/** Người được cấp lại — đúng đủ để người cấp nhận ra mình vừa làm cho ai. */
export interface PasswordResetTargetDto {
  displayName?: string;
  /** Thuỷ tổ = 1. */
  generation?: number;
  branchName?: string;
}

/**
 * Một lượt cấp lại, ở dạng đọc. **Không chứa liên kết** và không chứa mảnh nào
 * của token — liên kết chỉ có ở {@link IssuedPasswordResetDto}, đúng một lần.
 */
export interface PasswordResetGrantDto {
  id: string;
  targetPersonId: string;
  target?: PasswordResetTargetDto;
  branchId?: string;
  issuedBy: string;
  issuedAt?: string;
  expiresAt: string;
  /** `undefined` nghĩa là người ấy CHƯA đặt lại mật khẩu. */
  usedAt?: string;
  revokedAt?: string;
  note?: string;
}

/**
 * Phản hồi `201` của lệnh cấp — **lần duy nhất liên kết rời máy chủ**. Không
 * endpoint nào đọc lại được `url`; CSDL không lưu token, kể cả băm.
 */
export interface IssuedPasswordResetDto {
  /** Trao tận tay người quên mật khẩu, hoặc nhắn qua Zalo. Dùng một lần. */
  url: string;
  expiresAt: string;
  grant: PasswordResetGrantDto;
}

export interface IssuePasswordResetBody {
  /** Nhân khẩu của người quên mật khẩu — khoá vào là NHÂN KHẨU, không phải tài khoản. */
  personId: string;
  /** Tối đa 500 ký tự. KHÔNG ghi số điện thoại/địa chỉ vào đây — vào `audit_log` vĩnh viễn. */
  note?: string;
}

// ============================================================================
// NHÁNH HỎNG
// ============================================================================

/**
 * Bốn ca bị chặn (đọc javadoc đầu tệp) cộng ba ca hạ tầng thường lệ.
 *
 * `NOT_READY` gộp ba trong bốn lý do của `PASSWORD_RESET_NOT_ALLOWED` (chưa có
 * tài khoản · không còn `ACTIVE` · không còn trong realm): cả ba đều dẫn tới
 * cùng một lối đi ("không cấp lại được lúc này"), khác hẳn `HAS_EMAIL` — lối
 * đi của nó là bảo người kia tự bấm "Quên mật khẩu".
 */
export type PasswordResetFailure =
  | "BRANCH_SCOPE"
  | "SELF"
  | "HAS_EMAIL"
  | "NOT_READY"
  /** Nhân khẩu không còn tồn tại — hiếm, vì bộ chọn chỉ cho chọn người có thật. */
  | "NOT_FOUND"
  | "RATE_LIMITED"
  | "PROVIDER_DOWN"
  | "UNAVAILABLE";

/**
 * `unknown` bắt được từ React Query → nhánh giao diện.
 *
 * Mặc định `UNAVAILABLE`, không phải một trong bốn ca nghiệp vụ: một trục
 * trặc hạ tầng đọc thành "không cấp lại được cho người này" sẽ khiến Trưởng
 * chi đi tìm một người khác thay vì thử lại đúng người đang cần.
 */
export function passwordResetFailureOf(error: unknown): PasswordResetFailure {
  if (!(error instanceof ApiError)) return "UNAVAILABLE";

  switch (error.code) {
    case "BRANCH_SCOPE_VIOLATION":
      return "BRANCH_SCOPE";
    case "SELF_PASSWORD_RESET_FORBIDDEN":
      return "SELF";
    case "PASSWORD_RESET_NOT_ALLOWED": {
      // Xem javadoc đầu tệp: đây là chỗ DUY NHẤT trong lớp API này đọc `detail`
      // để rẽ nhánh, và nó được phép — chính contract giao việc phân biệt cho
      // `detail` thay vì cấp thêm mã. Câu máy chủ (`kiemTaiKhoanDich`, phía
      // Java) luôn chứa cụm này khi lý do là "có địa chỉ thư".
      // So KHÔNG DẤU: từ 26/09/2026 máy chủ viết câu này có dấu ("địa chỉ thư"), trước đó
      // không dấu. Bỏ dấu cả hai phía để một bản máy chủ cũ hay mới đều rẽ đúng nhánh — phép so
      // theo chữ này sẽ im lặng rơi sang "NOT_READY" chỉ vì đổi dấu, và người dùng nhận lời
      // khuyên sai ("tài khoản chưa sẵn sàng") thay vì "tự bấm Quên mật khẩu".
      const detail = (error.problem?.detail ?? "")
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "")
        .replace(/đ/g, "d")
        .replace(/Đ/g, "D")
        .toLowerCase();
      if (detail.includes("dia chi thu")) return "HAS_EMAIL";
      return "NOT_READY";
    }
    case "NOT_FOUND":
      return "NOT_FOUND";
    case "RATE_LIMITED":
      return "RATE_LIMITED";
    case "IDENTITY_PROVIDER_UNAVAILABLE":
      return "PROVIDER_DOWN";
    default:
      break;
  }

  if (error.status === 404) return "NOT_FOUND";
  if (error.status === 429) return "RATE_LIMITED";
  if (error.status === 503) return "PROVIDER_DOWN";
  return "UNAVAILABLE";
}

// ============================================================================
// GỌI API
// ============================================================================

const BASE = "/api/v1/password-resets";

export const passwordResetApi = {
  /**
   * Cấp lại liên kết đặt mật khẩu. `201`, thân phản hồi chứa liên kết —
   * đừng đưa `IssuedPasswordResetDto` vào cache của React Query (nó sống qua
   * các lần chuyển màn); giữ nó trong `mutation.data` và để mất khi component
   * gọi `reset()`, đúng khuôn `useIssueInvitation`.
   */
  issue: (body: IssuePasswordResetBody) =>
    apiFetch<IssuedPasswordResetDto>(BASE, { method: "POST", body }),
};
